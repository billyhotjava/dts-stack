package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelPublicationQualityEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelPublicationQualityEvidenceRepository.QualityWorkItem;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationQualityReconciler.QualityReconcileOutcome;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPublicationQualityReconcilerTest {

    private static final String TENANT = "tenant-a";
    private static final UUID PLAN_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000001"
    );
    private static final UUID CANDIDATE_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000002"
    );
    private static final UUID MODEL_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000003"
    );
    private static final UUID QUALITY_COMMAND_EVENT_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000004"
    );
    private static final UUID RUN_GROUP_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000005"
    );

    @Mock
    private ModelPublicationQualityEvidenceRepository evidence;

    @Mock
    private ModelReleaseCandidateRepository candidates;

    @Mock
    private ModelReleaseCandidateService commands;

    @Mock
    private CandidateGovernanceQualityEvidenceService governanceQuality;

    private ModelPublicationQualityReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new ModelPublicationQualityReconciler(
            evidence,
            candidates,
            commands,
            governanceQuality
        );
    }

    @Test
    void consumesTheCompletedCanonicalDbtBuildInsteadOfStartingASecondJob() {
        CandidateView running = candidate(
            DeliveryStatus.QUALITY_RUNNING,
            8
        );
        CandidateView passed = candidate(
            DeliveryStatus.QUALITY_PASSED,
            9
        );
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(running));
        when(governanceQuality.evaluateLive(running)).thenReturn(passingGovernance());
        when(
            commands.transitionWithQualityEvidence(
                eq(TENANT),
                eq("service:dts-platform-quality"),
                eq(CANDIDATE_ID),
                any(),
                any()
            )
        )
            .thenReturn(
                new CommandResult(
                    passed,
                    false,
                    List.of(),
                    List.of()
                )
            );

        var result = reconciler.reconcile(
            work("COMPLETED", null, 1, 1, 1)
        );

        assertThat(result.outcome())
            .isEqualTo(QualityReconcileOutcome.PASSED);
        ArgumentCaptor<TransitionCommand> command =
            ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transitionWithQualityEvidence(
            eq(TENANT),
            eq("service:dts-platform-quality"),
            eq(CANDIDATE_ID),
            command.capture(),
            any()
        );
        assertThat(command.getValue().targetStatus())
            .isEqualTo(DeliveryStatus.QUALITY_PASSED);
        assertThat(command.getValue().idempotencyKey())
            .isEqualTo(
                "candidate-quality-result:" + QUALITY_COMMAND_EVENT_ID
            );
    }

    @Test
    void waitsUntilAirflowAndEveryCandidateRunAreTerminal() {
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(
                Optional.of(
                    candidate(DeliveryStatus.QUALITY_RUNNING, 8)
                )
            );

        var result = reconciler.reconcile(
            work("SUBMITTED", null, 1, 1, 0)
        );

        assertThat(result.outcome())
            .isEqualTo(QualityReconcileOutcome.WAITING);
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void mapsARealTerminalExecutionFailureToQualityFailed() {
        CandidateView running = candidate(
            DeliveryStatus.QUALITY_RUNNING,
            8
        );
        CandidateView failed = candidate(
            DeliveryStatus.QUALITY_FAILED,
            9
        );
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(running));
        when(
            commands.transition(
                eq(TENANT),
                eq("service:dts-platform-quality"),
                eq(CANDIDATE_ID),
                any()
            )
        )
            .thenReturn(
                new CommandResult(
                    failed,
                    false,
                    List.of(),
                    List.of()
                )
            );

        var result = reconciler.reconcile(
            work(
                "FAILED",
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                1,
                1,
                0
            )
        );

        assertThat(result.outcome())
            .isEqualTo(QualityReconcileOutcome.FAILED);
    }

    @Test
    void keepsCandidateRunningWhenBlockingGovernanceEvidenceIsMissing() {
        CandidateView running = candidate(DeliveryStatus.QUALITY_RUNNING, 8);
        when(candidates.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(running));
        when(governanceQuality.evaluateLive(running))
            .thenReturn(
                new GovernanceQualitySummaryView(
                    true,
                    EvidenceState.FAILED,
                    "MODEL_SPEC_GOVERNANCE_QUALITY_MISSING",
                    "missing governance evidence",
                    300,
                    List.of()
                )
            );

        var result = reconciler.reconcile(work("COMPLETED", null, 1, 1, 1));

        assertThat(result.outcome()).isEqualTo(QualityReconcileOutcome.BLOCKED);
        assertThat(result.blockerCode()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_MISSING");
        verify(commands, never()).transitionWithQualityEvidence(any(), any(), any(), any(), any());
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    private static QualityWorkItem work(
        String dispatchStatus,
        String errorCode,
        int candidateCount,
        int runCount,
        int verifiedCount
    ) {
        return new QualityWorkItem(
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            8,
            QUALITY_COMMAND_EVENT_ID,
            RUN_GROUP_ID,
            dispatchStatus,
            errorCode,
            candidateCount,
            runCount,
            verifiedCount
        );
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        int version
    ) {
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        var audit = new DeliveryAuditView(
            "creator",
            now,
            null,
            null,
            null,
            null,
            null,
            null
        );
        var entry = new EntryView(
            UUID.fromString("30000000-0000-0000-0000-000000000006"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            MODEL_ID,
            3,
            "a".repeat(64),
            UUID.fromString("30000000-0000-0000-0000-000000000007"),
            ImplementationMode.DBT_MANAGED,
            status,
            0,
            null
        );
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "PROD",
            status,
            version,
            "candidate-create",
            "b".repeat(64),
            audit,
            "service:dts-platform-quality",
            now.plusSeconds(60),
            List.of(entry),
            CandidateOrigin.SINGLE_MODEL_INTENT,
            "target",
            "postgres",
            "profile",
            "prod"
        );
    }

    private static GovernanceQualitySummaryView passingGovernance() {
        return new GovernanceQualitySummaryView(
            true,
            EvidenceState.PASSED,
            null,
            null,
            300,
            List.of(
                new QualityEvidence(
                    "40000000-0000-0000-0000-000000000001",
                    UUID.fromString("40000000-0000-0000-0000-000000000002"),
                    UUID.fromString("40000000-0000-0000-0000-000000000003"),
                    UUID.fromString("40000000-0000-0000-0000-000000000004"),
                    UUID.fromString("40000000-0000-0000-0000-000000000005"),
                    "SUCCEEDED",
                    Instant.parse("2026-07-28T00:00:30Z"),
                    "c".repeat(64),
                    List.of()
                )
            )
        );
    }
}
