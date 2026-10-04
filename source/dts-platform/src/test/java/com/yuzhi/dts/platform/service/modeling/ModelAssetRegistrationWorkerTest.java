package com.yuzhi.dts.platform.service.modeling;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ModelAssetRegistrationWorkerTest {
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");
    private final ModelAssetRegistrationTaskRepository tasks = mock(ModelAssetRegistrationTaskRepository.class);
    private final ModelAssetRegistrationAttemptService registration = mock(ModelAssetRegistrationAttemptService.class);
    private final ModelAssetRegistrationWorker worker = new ModelAssetRegistrationWorker(tasks, registration, Clock.fixed(NOW, ZoneOffset.UTC));
    private TaskView task(int attempts) { return new TaskView(UUID.randomUUID(), "tenant", UUID.randomUUID(), 6, UUID.randomUUID(), "prod", State.PENDING, attempts, null, null, NOW, null, NOW); }

    @Test void executesOnlyTheClaimedGenerationAndAttempt() {
        var task = task(0);
        when(tasks.findDue(any(), anyInt(), anyInt())).thenReturn(List.of(task));
        when(tasks.claim(task, NOW, NOW.plus(ModelAssetRegistrationWorker.LEASE))).thenReturn(true);
        worker.registerDue();
        verify(registration).register(task, 1);
    }
    @Test void losingAClaimDoesNotWriteAssets() {
        when(tasks.findDue(any(), anyInt(), anyInt())).thenReturn(List.of(task(0)));
        worker.registerDue();
        verifyNoInteractions(registration);
    }
    @Test void failureIsRecordedAfterTheTransactionalAttemptReturns() {
        var task = task(1);
        doThrow(new ModelReleaseCandidateException("CATALOG_UNAVAILABLE", "目录服务不可用", ModelReleaseCandidateException.Kind.UNPROCESSABLE))
            .when(registration).register(task, 2);
        worker.attempt(task, 2);
        var order = inOrder(registration, tasks);
        order.verify(registration).register(task, 2);
        order.verify(tasks).markFailed(task, 2, "CATALOG_UNAVAILABLE", "目录服务不可用", NOW.plus(Duration.ofMinutes(5)), NOW);
    }
}
