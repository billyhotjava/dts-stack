package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.*;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ModelAssetRegistrationAttemptServiceTest {
    final ModelAssetRegistrationTaskRepository tasks = mock(ModelAssetRegistrationTaskRepository.class);
    final ModelReleaseCandidateRepository candidates = mock(ModelReleaseCandidateRepository.class);
    final CandidateQualityAssetRegistrationService registration = mock(CandidateQualityAssetRegistrationService.class);
    final ModelingExecutionAuthorization authorization = mock(ModelingExecutionAuthorization.class);
    final ModelAssetRegistrationAttemptService service = new ModelAssetRegistrationAttemptService(tasks, candidates, registration, authorization);
    final TaskView task = new TaskView(UUID.randomUUID(), "tenant", UUID.randomUUID(), 6, UUID.randomUUID(), "prod", State.PENDING, 0, null, null, Instant.now(), null, Instant.now());

    @Test void staleAttemptCannotReadOrWriteAssets() {
        service.register(task, 1);
        var order = inOrder(candidates, tasks);
        order.verify(candidates).lockPlanForCandidate("tenant", task.planId());
        order.verify(tasks).lockAttempt(task, 1);
        verifyNoInteractions(registration, authorization);
    }
    @Test void successfulRegistrationUsesTheBuildInitiatorAndClosesTheSameAttempt() {
        var candidate = mock(CandidateView.class);
        var identity = mock(ModelingIdentityService.Scope.class);
        when(tasks.lockAttempt(task, 1)).thenReturn(true);
        when(tasks.findBuildInitiatorVersion("tenant", task.candidateId(), 6)).thenReturn(Optional.of(5));
        when(candidates.find("tenant", task.candidateId())).thenReturn(Optional.of(candidate));
        when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
        when(authorization.candidate("tenant", task.candidateId(), 5, "BUILDING")).thenReturn(identity);
        when(tasks.markSucceeded(eq(task), eq(1), any())).thenReturn(true);
        service.register(task, 1);
        var order = inOrder(registration, tasks, identity);
        order.verify(tasks).lockAttempt(task, 1);
        order.verify(tasks).findBuildInitiatorVersion("tenant", task.candidateId(), 6);
        order.verify(registration).ensureRegistered(candidate);
        order.verify(tasks).markSucceeded(eq(task), eq(1), any());
        order.verify(identity).close();
    }
    @Test void missingInitiatorCannotRegister() {
        var candidate = mock(CandidateView.class);
        when(tasks.lockAttempt(task, 1)).thenReturn(true);
        when(candidates.find("tenant", task.candidateId())).thenReturn(Optional.of(candidate));
        when(candidate.status()).thenReturn(DeliveryStatus.BUILT);
        assertThatThrownBy(() -> service.register(task, 1)).isInstanceOf(ModelReleaseCandidateException.class);
        verifyNoInteractions(registration, authorization);
    }
}
