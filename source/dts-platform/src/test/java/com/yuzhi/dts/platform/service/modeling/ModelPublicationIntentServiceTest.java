package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.NextHumanAction;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.PublicationOutcome;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPublicationIntentServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "maintainer-a";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T03:00:00Z");

    @Mock
    private ModelReleaseCandidateRepository repository;

    @Mock
    private ModelReleaseCandidateService candidateCommands;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    @Mock
    private ReleaseDutyResolver dutyResolver;

    private ModelPublicationIntentService service;

    @BeforeEach
    void setUp() {
        service = new ModelPublicationIntentService(
            repository,
            candidateCommands,
            planAccess,
            dutyResolver
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
    }

    @Test
    void builtIntentAtomicallyRecordsPublicationRequestAndStartsQualityOnly() {
        CandidateView built = candidate(DeliveryStatus.BUILT, 7, CandidateOrigin.SINGLE_MODEL_INTENT);
        CandidateView quality = candidate(DeliveryStatus.QUALITY_RUNNING, 8, CandidateOrigin.SINGLE_MODEL_INTENT);
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(candidateCommands.detectDrift(TENANT, built)).thenReturn(List.of());
        when(candidateCommands.publicationRequested(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(quality, false, List.of()));

        var result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            CANDIDATE_ID,
            7,
            "publish-intent-1",
            "submit current model"
        );

        assertThat(result.candidateStatus()).isEqualTo(DeliveryStatus.QUALITY_RUNNING);
        assertThat(result.outcome()).isEqualTo(PublicationOutcome.QUALITY_RUNNING);
        assertThat(result.nextHumanAction()).isEqualTo(NextHumanAction.NONE);
        assertThat(result.replayed()).isFalse();
        ArgumentCaptor<TransitionCommand> command = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(candidateCommands).publicationRequested(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().expectedVersion()).isEqualTo(7);
        assertThat(command.getValue().targetStatus()).isEqualTo(DeliveryStatus.QUALITY_RUNNING);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("publish-intent-1");
        assertThat(command.getValue().reason()).isEqualTo("submit current model");
        verify(candidateCommands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void qualityPassedStopsAtTheAsynchronousReviewBoundary() {
        CandidateView passed = candidate(DeliveryStatus.QUALITY_PASSED, 9, CandidateOrigin.SINGLE_MODEL_INTENT);
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(passed));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(candidateCommands.detectDrift(TENANT, passed)).thenReturn(List.of());

        var result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            CANDIDATE_ID,
            9,
            "publish-intent-2",
            "submit current model"
        );

        assertThat(result.outcome()).isEqualTo(PublicationOutcome.REVIEW_SUBMISSION_PENDING);
        assertThat(result.nextHumanAction()).isEqualTo(NextHumanAction.NONE);
        verify(candidateCommands, never()).publicationRequested(any(), any(), any(), any());
        verify(candidateCommands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void dataAdministratorCanPublishQualityPassedCandidateWithoutReview() {
        when(dutyResolver.currentDuties())
            .thenReturn(Set.of(
                DeliveryActorRole.MODEL_MAINTAINER,
                DeliveryActorRole.RELEASE_OPERATOR
            ));
        CandidateView passed = candidate(DeliveryStatus.QUALITY_PASSED, 9, CandidateOrigin.SINGLE_MODEL_INTENT);
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(passed));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        when(candidateCommands.detectDrift(TENANT, passed)).thenReturn(List.of());

        var result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            CANDIDATE_ID,
            9,
            "publish-intent-self-service",
            "publish current model"
        );

        assertThat(result.outcome()).isEqualTo(PublicationOutcome.PUBLICATION_READY);
        assertThat(result.nextHumanAction()).isEqualTo(NextHumanAction.PUBLISH);
        verify(candidateCommands, never()).publicationRequested(any(), any(), any(), any());
        verify(candidateCommands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void replayReturnsTheCurrentProjectionInsteadOfTheOriginalQualitySnapshot() {
        when(dutyResolver.currentDuties())
            .thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
        CandidateView current = candidate(
            DeliveryStatus.REVIEW_PENDING,
            10,
            CandidateOrigin.SINGLE_MODEL_INTENT,
            ACTOR
        );
        CandidateView original = candidate(
            DeliveryStatus.QUALITY_RUNNING,
            8,
            CandidateOrigin.SINGLE_MODEL_INTENT
        );
        var receipt = new ModelReleaseCandidateContract.CommandEventView(
            UUID.randomUUID(),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            8,
            ModelReleaseCandidateContract.CommandEventType.PUBLICATION_REQUESTED,
            DeliveryStatus.BUILT,
            DeliveryStatus.QUALITY_RUNNING,
            ACTOR,
            NOW.minusSeconds(60),
            "submit current model",
            "publish-intent-replay",
            "c".repeat(64),
            "{}"
        );
        when(repository.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(current));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR))
            .thenReturn(true);
        when(
            repository.findCommandByIdempotencyKey(
                TENANT,
                "publish-intent-replay"
            )
        )
            .thenReturn(Optional.of(receipt));
        when(
            candidateCommands.publicationRequested(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any()
            )
        )
            .thenReturn(
                new CommandResult(original, true, List.of())
            );

        var result = service.start(
            TENANT,
            ACTOR,
            MODEL_ID,
            CANDIDATE_ID,
            7,
            "publish-intent-replay",
            "submit current model"
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.candidateStatus())
            .isEqualTo(DeliveryStatus.REVIEW_PENDING);
        assertThat(result.outcome())
            .isEqualTo(PublicationOutcome.REVIEW_PENDING);
    }

    @Test
    void singleModelFacadeNeverMutatesAnActiveBatchCandidate() {
        CandidateView batch = candidate(DeliveryStatus.BUILT, 7, CandidateOrigin.BATCH_WORKBENCH);
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(batch));
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);

        assertThatThrownBy(() ->
            service.start(
                TENANT,
                ACTOR,
                MODEL_ID,
                CANDIDATE_ID,
                7,
                "publish-intent-3",
                "attempt batch publish"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT")
            );

        verify(candidateCommands, never()).publicationRequested(any(), any(), any(), any());
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        int version,
        CandidateOrigin origin
    ) {
        return candidate(status, version, origin, null);
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        int version,
        CandidateOrigin origin,
        String submittedBy
    ) {
        Instant submittedAt = submittedBy == null
            ? null
            : NOW.minusSeconds(30);
        DeliveryAuditView audit = new DeliveryAuditView(
            ACTOR,
            NOW.minusSeconds(120),
            submittedBy,
            submittedAt,
            null,
            null,
            null,
            null
        );
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            version,
            "create-key",
            "a".repeat(64),
            audit,
            ACTOR,
            NOW,
            List.of(
                new EntryView(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    TENANT,
                    CANDIDATE_ID,
                    PLAN_ID,
                    MODEL_ID,
                    3,
                    "b".repeat(64),
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    ModelSpecContract.ImplementationMode.DBT_MANAGED,
                    status,
                    0,
                    "single model"
                )
            ),
            origin,
            "postgres-primary",
            "postgres",
            "runtime",
            "prod"
        );
    }
}
