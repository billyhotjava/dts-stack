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
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueActionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
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
            datasetReadGuard
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

    private IssueTicketUpsertRequest request() {
        IssueTicketUpsertRequest request = new IssueTicketUpsertRequest();
        request.setTitle("质量检测失败");
        request.setSummary("受控摘要");
        request.setSeverity("HIGH");
        return request;
    }
}
