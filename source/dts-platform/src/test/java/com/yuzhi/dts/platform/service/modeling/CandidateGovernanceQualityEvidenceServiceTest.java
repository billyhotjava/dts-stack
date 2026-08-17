package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.Policy;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.StandardCoverage;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateGovernanceQualityEvidenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private CandidatePublicationEvidenceRepository publicationEvidence;
    private ModelExecutionTargetCatalogResolver targetResolver;
    private QualityEvidencePort qualityEvidence;
    private ModelGovernancePolicyPort governancePolicy;

    @BeforeEach
    void setUp() {
        publicationEvidence = mock(CandidatePublicationEvidenceRepository.class);
        targetResolver = mock(ModelExecutionTargetCatalogResolver.class);
        qualityEvidence = mock(QualityEvidencePort.class);
        governancePolicy = mock(ModelGovernancePolicyPort.class);
        when(targetResolver.resolve(any())).thenReturn(
            new ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget("postgres-primary", SOURCE_ID, "postgres")
        );
        when(publicationEvidence.requireCurrent(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(
            List.of(observation())
        );
    }

    @Test
    void blockingPolicyRejectsPublicationWhenGovernanceEvidenceIsMissing() {
        when(governancePolicy.resolve()).thenReturn(Policy.available(StandardCoverage.KEY_AND_MEASURE, QualityGate.BLOCKING, 300));
        when(qualityEvidence.read(any())).thenReturn(List.of(evidence("MISSING", List.of("MISSING"))));
        CandidateGovernanceQualityEvidenceService service = service();

        var summary = service.evaluate(candidate());

        assertThat(summary.required()).isTrue();
        assertThat(summary.state()).isEqualTo(EvidenceState.FAILED);
        assertThat(summary.code()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_MISSING");
        assertThatThrownBy(() -> service.requirePublishable(candidate()))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_MISSING");
    }

    @Test
    void exactPassingRunOpensTheBlockingGovernanceGate() {
        when(governancePolicy.resolve()).thenReturn(Policy.available(StandardCoverage.KEY_AND_MEASURE, QualityGate.BLOCKING, 300));
        when(qualityEvidence.read(any())).thenReturn(List.of(evidence("SUCCEEDED", List.of())));
        CandidateGovernanceQualityEvidenceService service = service();

        var summary = service.requirePublishable(candidate());

        assertThat(summary.required()).isTrue();
        assertThat(summary.state()).isEqualTo(EvidenceState.PASSED);
        assertThat(summary.passed()).isTrue();
        org.mockito.Mockito.verify(qualityEvidence).read(
            org.mockito.ArgumentMatchers.argThat(requests ->
                requests.size() == 1 &&
                CatalogAssetKey
                    .dataset(SOURCE_ID, "biadmin", "dwd", "project_detail", "project_detail")
                    .equals(requests.getFirst().assetKey())
            )
        );
    }

    @Test
    void advisoryPolicyStillReportsMissingEvidenceWithoutBlockingPublication() {
        when(governancePolicy.resolve()).thenReturn(Policy.available(StandardCoverage.NONE, QualityGate.ADVISORY, 300));
        when(qualityEvidence.read(any())).thenReturn(List.of(evidence("MISSING", List.of("MISSING"))));
        CandidateGovernanceQualityEvidenceService service = service();

        var summary = service.evaluate(candidate());

        assertThat(summary.required()).isFalse();
        assertThat(summary.state()).isEqualTo(EvidenceState.FAILED);
        assertThatCode(() -> service.requirePublishable(candidate())).doesNotThrowAnyException();
    }

    @Test
    void unavailablePolicyFailsClosedBeforeAnyEvidenceCanBeTrusted() {
        when(governancePolicy.resolve()).thenReturn(Policy.unavailable("PLATFORM_MODEL_GOVERNANCE_POLICY_UNREADABLE"));
        CandidateGovernanceQualityEvidenceService service = service();

        var summary = service.evaluate(candidate());

        assertThat(summary.required()).isTrue();
        assertThat(summary.state()).isEqualTo(EvidenceState.UNAVAILABLE);
        assertThat(summary.code()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_POLICY_UNAVAILABLE");
    }

    @Test
    void pinnedCandidateEvidenceIsStableWhenTheLatestGovernanceRunChanges() throws Exception {
        CandidateView candidate = candidate();
        var passing = new ModelReleaseCandidateContract.GovernanceQualitySummaryView(
            true,
            EvidenceState.PASSED,
            null,
            null,
            300,
            List.of(evidence("SUCCEEDED", List.of()))
        );
        CandidateQualityEvidenceSnapshot snapshot = CandidateQualityEvidenceSnapshot.captureLegacy(candidate, passing);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var response = mapper.createObjectNode();
        snapshot.appendTo(response, mapper);
        CommandEventView event = new CommandEventView(
            UUID.randomUUID(),
            candidate.tenantId(),
            candidate.id(),
            candidate.planId(),
            candidate.version(),
            CommandEventType.STATUS_CHANGED,
            DeliveryStatus.QUALITY_RUNNING,
            DeliveryStatus.QUALITY_PASSED,
            "service:dts-platform-quality",
            NOW,
            "quality passed",
            "quality-pin",
            "f".repeat(64),
            mapper.writeValueAsString(response)
        );
        ModelReleaseCandidateRepository repository = mock(ModelReleaseCandidateRepository.class);
        when(repository.findLatestQualityEvidenceSnapshot(candidate.tenantId(), candidate.id()))
            .thenReturn(Optional.of(event));
        CandidateGovernanceQualityEvidenceService service = new CandidateGovernanceQualityEvidenceService(
            publicationEvidence,
            targetResolver,
            qualityEvidence,
            governancePolicy,
            Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC),
            repository,
            mapper
        );

        var summary = service.evaluate(candidate);

        assertThat(summary.passed()).isTrue();
        assertThat(summary.evidence().getFirst().runId()).isEqualTo(RUN_ID);
        verifyNoInteractions(qualityEvidence, governancePolicy);
    }

    private CandidateGovernanceQualityEvidenceService service() {
        return new CandidateGovernanceQualityEvidenceService(
            publicationEvidence,
            targetResolver,
            qualityEvidence,
            governancePolicy,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static QualityEvidence evidence(String status, List<String> violations) {
        return new QualityEvidence(
            CatalogAssetKey.dataset(SOURCE_ID, "biadmin", "dwd", "project_detail", "project_detail"),
            RULE_ID,
            VERSION_ID,
            BINDING_ID,
            "MISSING".equals(status) ? null : RUN_ID,
            status,
            "MISSING".equals(status) ? null : NOW.minusSeconds(60),
            "a".repeat(64),
            violations
        );
    }

    private static PublicationEntryEvidence observation() {
        return new PublicationEntryEvidence(
            UUID.randomUUID(),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            2,
            "b".repeat(64),
            2,
            "c".repeat(64),
            "model.pjm.project_detail",
            "dwd.project_detail",
            "d".repeat(64),
            "e".repeat(64),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "postgres",
            "biadmin",
            "dwd",
            "project_detail",
            ExpectedRelationType.TABLE,
            "f".repeat(64),
            NOW.minusSeconds(30)
        );
    }

    private static CandidateView candidate() {
        UUID candidateId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID planId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        UUID modelId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        EntryView entry = new EntryView(
            UUID.randomUUID(),
            "server",
            candidateId,
            planId,
            modelId,
            2,
            "b".repeat(64),
            null,
            ImplementationMode.DBT_MANAGED,
            DeliveryStatus.QUALITY_PASSED,
            0,
            "release scope"
        );
        return new CandidateView(
            candidateId,
            "server",
            planId,
            "dev",
            DeliveryStatus.QUALITY_PASSED,
            4,
            "candidate-key",
            "c".repeat(64),
            new DeliveryAuditView("xiezm", NOW.minusSeconds(600), null, null, null, null, null, null),
            "xiezm",
            NOW,
            List.of(entry),
            CandidateOrigin.BATCH_WORKBENCH,
            "postgres-primary",
            "postgres",
            "default",
            "dev"
        );
    }
}
