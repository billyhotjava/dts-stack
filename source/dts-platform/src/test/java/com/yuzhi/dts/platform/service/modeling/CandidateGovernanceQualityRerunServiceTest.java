package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.GovernanceQualityRerunPort.QualityRunRef;
import com.yuzhi.dts.platform.service.modeling.GovernanceQualityRerunPort.RerunReceipt;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateGovernanceQualityRerunServiceTest {

    private static final String TENANT = "default";
    private static final String ACTOR = "xiezm";
    private static final String ACTIVE_DEPT = "INST";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-17T08:00:00Z");

    @Mock
    private ModelReleaseCandidateRepository candidates;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    @Mock
    private ReleaseDutyResolver duties;

    @Mock
    private CandidateGovernanceQualityEvidenceService evidence;

    @Mock
    private GovernanceQualityRerunPort reruns;

    private CandidateGovernanceQualityRerunService service;

    @BeforeEach
    void setUp() {
        service = new CandidateGovernanceQualityRerunService(candidates, planAccess, duties, evidence, reruns);
        when(duties.currentDuties()).thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(candidates.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
    }

    @Test
    void startsNewPinnedRunsWithoutMutatingTheCandidateSnapshot() {
        QualityEvidence failed = failedEvidence("FAILED");
        when(reruns.findReplay(CANDIDATE_ID, "retry-1")).thenReturn(Optional.empty());
        when(evidence.evaluateLive(candidate())).thenReturn(summary(failed));
        when(reruns.rerun(CANDIDATE_ID, "retry-1", ACTOR, ACTIVE_DEPT, List.of(failed)))
            .thenReturn(new RerunReceipt(false, List.of(new QualityRunRef(RULE_ID, VERSION_ID, BINDING_ID, RUN_ID, "QUEUED"))));

        CandidateGovernanceQualityRerunService.RerunResult result = service.rerun(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "retry-1",
            ACTIVE_DEPT
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.runs()).singleElement().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(RUN_ID);
            assertThat(run.ruleVersionId()).isEqualTo(VERSION_ID);
        });
        verify(candidates).lockPlanForCandidate(TENANT, PLAN_ID);
        verify(reruns).rerun(CANDIDATE_ID, "retry-1", ACTOR, ACTIVE_DEPT, List.of(failed));
    }

    @Test
    void replaysTheOriginalRunsBeforeReevaluatingMutableEvidence() {
        RerunReceipt replay = new RerunReceipt(
            true,
            List.of(new QualityRunRef(RULE_ID, VERSION_ID, BINDING_ID, RUN_ID, "RUNNING"))
        );
        when(reruns.findReplay(CANDIDATE_ID, "retry-1")).thenReturn(Optional.of(replay));

        CandidateGovernanceQualityRerunService.RerunResult result = service.rerun(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            1,
            "retry-1",
            ACTIVE_DEPT
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.runs()).singleElement().extracting(QualityRunRef::runId).isEqualTo(RUN_ID);
        verify(evidence, never()).evaluateLive(candidate());
        verify(reruns, never()).rerun(CANDIDATE_ID, "retry-1", ACTOR, ACTIVE_DEPT, List.of());
    }

    @Test
    void refusesMissingBindingsInsteadOfInventingARuleRelationship() {
        QualityEvidence unbound = new QualityEvidence(
            "source:lake/schema:dwd/table:budget",
            null,
            null,
            null,
            null,
            "MISSING",
            null,
            "a".repeat(64),
            List.of("MISSING")
        );
        when(reruns.findReplay(CANDIDATE_ID, "retry-2")).thenReturn(Optional.empty());
        when(evidence.evaluateLive(candidate())).thenReturn(summary(unbound));

        assertThatThrownBy(() -> service.rerun(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "retry-2", ACTIVE_DEPT))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_BINDING_REQUIRED");

        verify(reruns, never()).rerun(CANDIDATE_ID, "retry-2", ACTOR, ACTIVE_DEPT, List.of(unbound));
    }

    private static CandidateView candidate() {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "dev",
            DeliveryStatus.QUALITY_RUNNING,
            4,
            "create-key",
            "b".repeat(64),
            new DeliveryAuditView(ACTOR, NOW, null, null, null, null, null, null),
            ACTOR,
            NOW,
            List.of()
        );
    }

    private static GovernanceQualitySummaryView summary(QualityEvidence item) {
        return new GovernanceQualitySummaryView(
            true,
            EvidenceState.FAILED,
            "MODEL_SPEC_GOVERNANCE_QUALITY_FAILED",
            "治理质量未通过",
            3600,
            List.of(item)
        );
    }

    private static QualityEvidence failedEvidence(String violation) {
        return new QualityEvidence(
            "source:lake/schema:dwd/table:budget",
            RULE_ID,
            VERSION_ID,
            BINDING_ID,
            UUID.randomUUID(),
            "FAILED",
            NOW.minusSeconds(10),
            "c".repeat(64),
            List.of(violation)
        );
    }
}
