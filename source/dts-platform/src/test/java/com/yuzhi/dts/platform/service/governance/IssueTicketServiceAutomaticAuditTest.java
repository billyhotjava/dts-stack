package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueActionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class IssueTicketServiceAutomaticAuditTest {

    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000081");
    private static final UUID TICKET_ID = UUID.fromString("20000000-0000-0000-0000-000000000081");

    @Mock private GovIssueTicketRepository ticketRepository;
    @Mock private GovIssueActionRepository actionRepository;
    @Mock private GovComplianceBatchRepository batchRepository;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private AccessChecker accessChecker;
    @Mock private OrganizationVisibilityService organizationVisibilityService;
    @Mock private QualityAuditRecorder qualityAuditRecorder;
    @Mock private QualityEffectiveDepartmentResolver departmentResolver;
    @Mock private QualityDatasetReadGuard datasetReadGuard;
    @Mock private IssueSourcePolicy issueSourcePolicy;

    private IssueTicketService service;

    @BeforeEach
    void setUp() {
        service = new IssueTicketService(
            ticketRepository,
            actionRepository,
            batchRepository,
            datasetRepository,
            accessChecker,
            organizationVisibilityService,
            new GovernanceProperties(),
            qualityAuditRecorder,
            departmentResolver,
            datasetReadGuard,
            issueSourcePolicy
        );
    }

    @Test
    void automaticCreateReturnsDispositionWithoutDuplicatingBuiltInAudit() {
        when(ticketRepository.findFirstBySourceTypeIgnoreCaseAndSourceRefIdAndStatusInOrderByCreatedDateDesc(
            "QUALITY_RUN",
            SOURCE_ID,
            List.of("OPEN", "IN_PROGRESS", "RESOLVED")
        )).thenReturn(Optional.empty());
        when(ticketRepository.save(any(GovIssueTicket.class))).thenAnswer(invocation -> {
            GovIssueTicket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            return ticket;
        });

        IssueTicketService.CreateOrTouchResult result = service.createOrTouchWithDisposition(
            "QUALITY_RUN",
            SOURCE_ID,
            request(),
            "system",
            null
        );

        assertThat(result.disposition()).isEqualTo(IssueTicketService.CreateOrTouchDisposition.CREATED);
        assertThat(result.ticket().getId()).isEqualTo(TICKET_ID);
        verify(qualityAuditRecorder, never()).recordAction(eq("GOV_ISSUE_CREATE"), any(AuditStage.class), any(), any());
    }

    @Test
    void repeatedQualityFailureReopensAndTouchesTheSameProblem() {
        String problemKey = "quality:dataset:binding:EXECUTION_ERROR";
        GovIssueTicket existing = new GovIssueTicket();
        existing.setId(TICKET_ID);
        existing.setStatus("RESOLVED");
        existing.setResolution("previous recovery");
        existing.setProblemKey(problemKey);
        when(ticketRepository.findFirstByProblemKeyAndStatusInOrderByCreatedDateDesc(
            problemKey,
            List.of("OPEN", "IN_PROGRESS", "RESOLVED")
        )).thenReturn(Optional.of(existing));

        IssueTicketService.CreateOrTouchResult result = service.createOrTouchQualityProblem(
            problemKey,
            SOURCE_ID,
            request(),
            "quality-workflow",
            "new failure evidence"
        );

        assertThat(result.disposition()).isEqualTo(IssueTicketService.CreateOrTouchDisposition.TOUCHED);
        assertThat(existing.getStatus()).isEqualTo("OPEN");
        assertThat(existing.getResolution()).isNull();
        verify(actionRepository).save(any(GovIssueAction.class));
    }

    @Test
    void successfulRunResolvesAllOpenProblemsForTheSameBinding() {
        UUID datasetId = UUID.fromString("30000000-0000-0000-0000-000000000081");
        UUID bindingId = UUID.fromString("40000000-0000-0000-0000-000000000081");
        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(bindingId);
        GovQualityRun run = new GovQualityRun();
        run.setId(SOURCE_ID);
        run.setDatasetId(datasetId);
        run.setBinding(binding);
        GovIssueTicket open = new GovIssueTicket();
        open.setId(TICKET_ID);
        open.setStatus("OPEN");
        when(ticketRepository.findByProblemKeyStartingWithAndStatusInOrderByCreatedDateDesc(
            QualityIssueIdentity.problemPrefix(run),
            List.of("OPEN", "IN_PROGRESS")
        )).thenReturn(List.of(open));

        List<UUID> resolved = service.resolveQualityProblems(run, "quality-workflow");

        assertThat(resolved).containsExactly(TICKET_ID);
        assertThat(open.getStatus()).isEqualTo("RESOLVED");
        assertThat(open.getResolution()).contains("自动转为已恢复");
        verify(actionRepository).save(any(GovIssueAction.class));
    }

    @Test
    void legacyCreateOrTouchKeepsItsBuiltInAudit() {
        when(ticketRepository.findFirstBySourceTypeIgnoreCaseAndSourceRefIdAndStatusInOrderByCreatedDateDesc(
            "QUALITY_RUN",
            SOURCE_ID,
            List.of("OPEN", "IN_PROGRESS", "RESOLVED")
        )).thenReturn(Optional.empty());
        when(ticketRepository.save(any(GovIssueTicket.class))).thenAnswer(invocation -> {
            GovIssueTicket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            return ticket;
        });

        service.createOrTouch("QUALITY_RUN", SOURCE_ID, request(), "alice", null);

        verify(qualityAuditRecorder).recordAction(
            eq("GOV_ISSUE_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(TICKET_ID.toString()),
            any()
        );
    }

    @Test
    void strictSuccessAuditFailurePropagatesToRollbackTheOwningTransaction() {
        when(ticketRepository.save(any(GovIssueTicket.class))).thenAnswer(invocation -> {
            GovIssueTicket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            return ticket;
        });
        IllegalStateException auditFailure = new IllegalStateException("strict audit unavailable");
        doThrow(auditFailure).when(qualityAuditRecorder).recordAction(
            eq("GOV_ISSUE_CREATE"), eq(AuditStage.SUCCESS), eq(TICKET_ID.toString()), any()
        );

        assertThatThrownBy(() -> service.create(request(), "alice", null)).isSameAs(auditFailure);
        assertThat(IssueTicketService.class.getAnnotation(Transactional.class)).isNotNull();
        verify(qualityAuditRecorder).recordFailureAction(eq("GOV_ISSUE_CREATE"), eq("UNASSIGNED"), any());
    }

    @Test
    void failureAuditFailureNeverMasksTheOriginalBusinessException() {
        IllegalStateException businessFailure = new IllegalStateException("ticket repository unavailable");
        when(ticketRepository.save(any(GovIssueTicket.class))).thenThrow(businessFailure);
        doThrow(new IllegalStateException("failure audit unavailable"))
            .when(qualityAuditRecorder)
            .recordFailureAction(eq("GOV_ISSUE_CREATE"), eq("UNASSIGNED"), any());

        assertThatThrownBy(() -> service.create(request(), "alice", null)).isSameAs(businessFailure);
    }

    @Test
    void rejectsHumanIssueCreationForAnUnreadableDatasetBeforePersistence() {
        UUID datasetId = UUID.fromString("40000000-0000-0000-0000-000000000081");
        IssueTicketUpsertRequest request = request();
        request.setDatasetId(datasetId);
        doThrow(new AccessDeniedException("denied"))
            .when(datasetReadGuard)
            .requireReadable(datasetId, "dept-a");

        assertThatThrownBy(() -> service.create(request, "alice", "dept-a"))
            .isInstanceOf(AccessDeniedException.class);
        verify(ticketRepository, never()).save(any(GovIssueTicket.class));
    }

    @Test
    void boundDatasetClassificationCannotBeDowngradedByTheRequest() {
        UUID datasetId = UUID.fromString("40000000-0000-0000-0000-000000000082");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setClassification("CONFIDENTIAL");
        IssueTicketUpsertRequest request = request();
        request.setDatasetId(datasetId);
        request.setDataLevel("DATA_INTERNAL");
        when(datasetReadGuard.requireReadable(datasetId, "dept-a")).thenReturn(dataset);
        when(ticketRepository.save(any(GovIssueTicket.class))).thenAnswer(invocation -> {
            GovIssueTicket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            return ticket;
        });

        var created = service.create(request, "alice", "dept-a");

        assertThat(created.getDataLevel()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void lowClearanceUserCannotReadAConfidentialDatasetIssue() {
        GovIssueTicket ticket = new GovIssueTicket();
        ticket.setId(TICKET_ID);
        ticket.setTitle("高密级数据质量问题");
        ticket.setDataLevel("CONFIDENTIAL");
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_INTERNAL);

        assertThatThrownBy(() -> service.get(TICKET_ID, "bob", "dept-a"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void exactSourceLookupReturnsOnlyTheAuthorizedIssue() {
        GovIssueTicket ticket = new GovIssueTicket();
        ticket.setId(TICKET_ID);
        ticket.setSourceType("QUALITY_RUN");
        ticket.setSourceRefId(SOURCE_ID);
        ticket.setTitle("质量运行问题");
        ticket.setDataLevel("INTERNAL");
        ticket.setOwner("alice");
        when(ticketRepository.findFirstBySourceTypeIgnoreCaseAndSourceRefIdOrderByCreatedDateDesc("QUALITY_RUN", SOURCE_ID))
            .thenReturn(Optional.of(ticket));
        when(accessChecker.resolveHighestDataLevel()).thenReturn(DataLevel.DATA_CONFIDENTIAL);

        assertThat(service.findBySource("QUALITY_RUN", SOURCE_ID, "alice", "dept-a"))
            .get()
            .extracting(result -> result.getId())
            .isEqualTo(TICKET_ID);
    }

    @Test
    void exactSourceLookupReportsAnAbsentIssueWithoutEnumeratingTickets() {
        when(ticketRepository.findFirstBySourceTypeIgnoreCaseAndSourceRefIdOrderByCreatedDateDesc("QUALITY_RUN", SOURCE_ID))
            .thenReturn(Optional.empty());

        assertThat(service.findBySource("QUALITY_RUN", SOURCE_ID, "alice", "dept-a")).isEmpty();
    }

    private IssueTicketUpsertRequest request() {
        IssueTicketUpsertRequest request = new IssueTicketUpsertRequest();
        request.setTitle("质量检测失败");
        request.setSummary("受控摘要");
        request.setSeverity("HIGH");
        return request;
    }
}
