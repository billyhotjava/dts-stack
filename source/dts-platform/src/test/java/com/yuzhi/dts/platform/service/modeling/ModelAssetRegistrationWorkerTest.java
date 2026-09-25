package com.yuzhi.dts.platform.service.modeling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.State;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.TaskView;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelAssetRegistrationWorkerTest {

    private static final String TENANT = "tenant-a";
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");

    private final ModelAssetRegistrationTaskRepository tasks = mock(ModelAssetRegistrationTaskRepository.class);
    private final CandidateQualityAssetRegistrationService registration = mock(CandidateQualityAssetRegistrationService.class);
    private final ModelReleaseCandidateRepository candidates = mock(ModelReleaseCandidateRepository.class);
    private final ModelingExecutionAuthorization authorization = mock(ModelingExecutionAuthorization.class);
    private final ModelingIdentityService.Scope scope = mock(ModelingIdentityService.Scope.class);
    private final CandidateView built = mock(CandidateView.class);
    private ModelAssetRegistrationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ModelAssetRegistrationWorker(tasks, registration, candidates, authorization, Clock.fixed(NOW, ZoneOffset.UTC));
        when(built.status()).thenReturn(DeliveryStatus.BUILT);
        when(candidates.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(tasks.findBuildInitiatorVersion(TENANT, CANDIDATE_ID, 6)).thenReturn(Optional.of(5));
        when(authorization.candidate(TENANT, CANDIDATE_ID, 5, "BUILDING")).thenReturn(scope);
    }

    @Test
    void registersUnderTheBuildInitiatorAndRecordsSuccess() {
        TaskView task = task(0);
        when(tasks.findDue(NOW, ModelAssetRegistrationWorker.MAX_ATTEMPTS, ModelAssetRegistrationWorker.BATCH_SIZE)).thenReturn(List.of(task));
        when(tasks.claim(task.id(), NOW, NOW.plus(ModelAssetRegistrationWorker.LEASE))).thenReturn(true);

        worker.registerDue();

        verify(registration).ensureRegistered(built);
        verify(scope).close();
        verify(tasks).markSucceeded(TENANT, CANDIDATE_ID, NOW);
    }

    @Test
    void skipsATaskAnotherWorkerAlreadyClaimed() {
        TaskView task = task(0);
        when(tasks.findDue(any(), anyInt(), anyInt())).thenReturn(List.of(task));
        when(tasks.claim(any(), any(), any())).thenReturn(false);

        worker.registerDue();

        verify(registration, never()).ensureRegistered(any());
    }

    @Test
    void recordsTheRegistrationFailureWithBackoffWithoutTouchingTheBuild() {
        when(registration.ensureRegistered(built)).thenThrow(new ModelReleaseCandidateException(
            "CATALOG_UNAVAILABLE", "目录服务不可用", ModelReleaseCandidateException.Kind.UNPROCESSABLE));

        worker.attempt(task(1), 2);

        verify(tasks).markFailed(any(), eq("CATALOG_UNAVAILABLE"), eq("目录服务不可用"), eq(NOW.plus(Duration.ofMinutes(5))), eq(NOW));
        verify(tasks, never()).markSucceeded(anyString(), any(), any());
        verify(scope).close();
    }

    @Test
    void failsWhenTheBuildInitiatorCannotBeFound() {
        when(tasks.findBuildInitiatorVersion(TENANT, CANDIDATE_ID, 6)).thenReturn(Optional.empty());

        worker.attempt(task(0), 1);

        verify(tasks).markFailed(any(), eq("MODELING_EXECUTION_INITIATOR_MISSING"), anyString(), eq(NOW.plus(Duration.ofMinutes(1))), eq(NOW));
        verify(registration, never()).ensureRegistered(any());
    }

    @Test
    void failsWhenTheInitiatorNoLongerHasModelingAccess() {
        when(authorization.candidate(TENANT, CANDIDATE_ID, 5, "BUILDING"))
            .thenThrow(new ModelingIdentityException(403, "MODELING_ACCESS_DENIED", "denied"));

        worker.attempt(task(0), 1);

        verify(tasks).markFailed(any(), eq("MODELING_ACCESS_DENIED"), anyString(), any(), eq(NOW));
        verify(registration, never()).ensureRegistered(any());
    }

    @Test
    void failsWhenTheCandidateLeftTheBuiltStates() {
        when(built.status()).thenReturn(DeliveryStatus.DRAFT);

        worker.attempt(task(0), 5);

        verify(tasks).markFailed(any(), eq("MODEL_ASSET_REGISTRATION_CANDIDATE_NOT_BUILT"), anyString(), eq(NOW.plus(Duration.ofHours(3))), eq(NOW));
    }

    private static TaskView task(int attempts) {
        return new TaskView(UUID.randomUUID(), TENANT, CANDIDATE_ID, 6, null, "prod", State.PENDING, attempts, null, null, NOW, null, NOW);
    }
}
