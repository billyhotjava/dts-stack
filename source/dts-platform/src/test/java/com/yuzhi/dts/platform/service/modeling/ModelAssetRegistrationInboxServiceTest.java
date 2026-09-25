package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.*;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.*;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ModelAssetRegistrationInboxServiceTest {
    final ModelAssetRegistrationTaskRepository tasks = mock(ModelAssetRegistrationTaskRepository.class);
    final ModelReleaseCandidateRepository candidates = mock(ModelReleaseCandidateRepository.class);
    final ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
    final ModelSpecPlanWriteAccessPort access = mock(ModelSpecPlanWriteAccessPort.class);
    final ModelAssetRegistrationInboxService service = new ModelAssetRegistrationInboxService(tasks, candidates, models, access);
    final TaskView task = new TaskView(UUID.randomUUID(), "t", UUID.randomUUID(), 6, UUID.randomUUID(), "prod", State.FAILED, 5, "ERROR", "目录不可用", Instant.now(), null, Instant.now());
    final UUID modelId = UUID.randomUUID();
    void candidate() {
        var candidate = mock(CandidateView.class); var entry = mock(EntryView.class);
        when(candidate.entries()).thenReturn(List.of(entry)); when(entry.modelSpecId()).thenReturn(modelId); when(entry.revision()).thenReturn(3);
        when(candidates.find("t", task.candidateId())).thenReturn(Optional.of(candidate));
    }
    @Test void ordinaryInboxIncludesAnOutputThatHasNoAssetId() {
        candidate(); var model = mock(ModelSpecView.class); when(model.name()).thenReturn("预算明细");
        when(models.get("t", modelId)).thenReturn(model); when(tasks.pendingPage("t", 0, 21)).thenReturn(List.of(task));
        when(access.canMaintain("t", task.planId(), "a")).thenReturn(true);
        var page = service.list("t", "a", 0);
        assertThat(page.items()).hasSize(1); assertThat(page.items().getFirst().models()).containsExactly("预算明细 · r3");
        assertThat(page.items().getFirst().canRetry()).isTrue();
    }
    @Test void inaccessibleModelsDoNotLeakIntoTheInbox() {
        candidate(); when(tasks.pendingPage("t", 0, 21)).thenReturn(List.of(task));
        when(models.get("t", modelId)).thenThrow(new ModelSpecException("FORBIDDEN", "denied", ModelSpecException.Kind.FORBIDDEN));
        assertThat(service.list("t", "a", 0).items()).isEmpty();
    }
    @Test void retryRechecksModelScopeAndBuildGeneration() {
        candidate(); when(tasks.findById("t", task.id())).thenReturn(Optional.of(task));
        when(access.canMaintain("t", task.planId(), "a")).thenReturn(true);
        assertThatThrownBy(() -> service.retry("t", "a", task.id(), 5)).isInstanceOf(ModelReleaseCandidateException.class);
        verify(tasks, never()).retry(any(), any());
        when(tasks.retry(eq(task), any())).thenReturn(true);
        service.retry("t", "a", task.id(), 6);
        verify(access, times(2)).requireOperation("t", List.of(modelId), "a");
        verify(tasks).retry(eq(task), any());
    }
}
