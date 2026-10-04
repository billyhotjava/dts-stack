package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelPublicationReconciliationRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelPublicationReconciliationRepository.PendingReviewSubmission;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationReviewReconciler.ReconcileOutcome;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
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
class ModelPublicationReviewReconcilerTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID CANDIDATE_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000002"
    );
    private static final UUID MODEL_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000003"
    );
    private static final UUID PUBLICATION_EVENT_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000004"
    );

    @Mock
    private ModelPublicationReconciliationRepository pending;

    @Mock
    private ModelReleaseCandidateRepository candidates;

    @Mock
    private ModelReleaseCandidateService commands;

    @Mock
    private ReleaseDutyDirectoryPort dutyDirectory;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    private ModelPublicationReviewReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new ModelPublicationReviewReconciler(
            pending,
            candidates,
            commands,
            dutyDirectory,
            planAccess
        );
    }

    @Test
    void submitsReviewOnceAfterCurrentDutyPlanAndSnapshotRevalidation() {
        CandidateView passed = candidate(
            DeliveryStatus.QUALITY_PASSED,
            9,
            null
        );
        CandidateView pendingReview = candidate(
            DeliveryStatus.REVIEW_PENDING,
            10,
            ACTOR
        );
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(passed));
        when(
            dutyDirectory.hasDuty(
                ACTOR,
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .thenReturn(true);
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR))
            .thenReturn(true);
        when(
            commands.transition(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any()
            )
        )
            .thenReturn(
                new CommandResult(
                    pendingReview,
                    false,
                    List.of(),
                    List.of()
                )
            );

        var result = reconciler.reconcile(work());

        assertThat(result.outcome()).isEqualTo(ReconcileOutcome.ADVANCED);
        ArgumentCaptor<TransitionCommand> command =
            ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transition(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().targetStatus())
            .isEqualTo(DeliveryStatus.REVIEW_PENDING);
        assertThat(command.getValue().idempotencyKey())
            .isEqualTo("publication-review:" + PUBLICATION_EVENT_ID);
    }

    @Test
    void revokedDutyStopsBeforeAnyCandidateMutation() {
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(
                Optional.of(
                    candidate(DeliveryStatus.QUALITY_PASSED, 9, null)
                )
            );
        when(
            dutyDirectory.hasDuty(
                ACTOR,
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .thenReturn(false);

        var result = reconciler.reconcile(work());

        assertThat(result.outcome()).isEqualTo(ReconcileOutcome.BLOCKED);
        assertThat(result.blockerCode())
            .isEqualTo("MODEL_RELEASE_MAINTAINER_DUTY_REVOKED");
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void selfServicePublisherDoesNotEnterLegacyReviewReconciliation() {
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(candidate(DeliveryStatus.QUALITY_PASSED, 9, null)));
        when(dutyDirectory.hasDuty(ACTOR, DeliveryActorRole.MODEL_MAINTAINER))
            .thenReturn(true);
        when(dutyDirectory.hasDuty(ACTOR, DeliveryActorRole.RELEASE_OPERATOR))
            .thenReturn(true);

        var result = reconciler.reconcile(work());

        assertThat(result.outcome()).isEqualTo(ReconcileOutcome.IGNORED);
        verify(planAccess, never()).canMaintain(any(), any(), any());
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void directoryFailureStopsWithoutFallingBackToTheRequestTimeRole() {
        when(candidates.find(TENANT, CANDIDATE_ID))
            .thenReturn(
                Optional.of(
                    candidate(DeliveryStatus.QUALITY_PASSED, 9, null)
                )
            );
        when(
            dutyDirectory.hasDuty(
                ACTOR,
                DeliveryActorRole.MODEL_MAINTAINER
            )
        )
            .thenThrow(
                new ReleaseDutyDirectoryUnavailableException("timeout")
            );

        var result = reconciler.reconcile(work());

        assertThat(result.outcome()).isEqualTo(ReconcileOutcome.BLOCKED);
        assertThat(result.blockerCode())
            .isEqualTo("MODEL_RELEASE_DUTY_DIRECTORY_UNAVAILABLE");
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    private static PendingReviewSubmission work() {
        return new PendingReviewSubmission(
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            9,
            PUBLICATION_EVENT_ID,
            ACTOR
        );
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        int version,
        String submittedBy
    ) {
        Instant createdAt = Instant.parse("2026-07-28T00:00:00Z");
        Instant submittedAt = submittedBy == null
            ? null
            : createdAt.plusSeconds(60);
        var audit = new DeliveryAuditView(
            "creator",
            createdAt,
            submittedBy,
            submittedAt,
            null,
            null,
            null,
            null
        );
        var entry = new EntryView(
            UUID.fromString("20000000-0000-0000-0000-000000000005"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            MODEL_ID,
            3,
            "a".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000006"),
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
            submittedBy == null ? "builder" : submittedBy,
            submittedBy == null ? createdAt.plusSeconds(30) : submittedAt,
            List.of(entry),
            CandidateOrigin.SINGLE_MODEL_INTENT,
            "target",
            "postgres",
            "profile",
            "prod"
        );
    }
}
