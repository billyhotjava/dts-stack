package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelIngestionTargetServiceTest {
    @Test
    void checksPlanPermissionBeforeResolvingAnIngestionTarget() {
        var models = mock(ModelSpecApplicationService.class);
        var model = mock(ModelSpecContract.ModelSpecView.class);
        var access = mock(ModelSpecPlanWriteAccessPort.class);
        var candidates = mock(ModelReleaseCandidateRepository.class);
        var id = UUID.randomUUID(); var plan = UUID.randomUUID();
        when(models.get("tenant", id)).thenReturn(model); when(model.planId()).thenReturn(plan);
        var service = new ModelIngestionTargetService(models, mock(ModelLifecycleRepository.class), candidates,
            mock(CandidatePublicationEvidenceRepository.class), mock(ModelExecutionTargetCatalogResolver.class), access);
        assertThatThrownBy(() -> service.resolveForUser("tenant", "reader", id, "dev"))
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, error -> assertThat(error.code()).isEqualTo("MODEL_INGESTION_TARGET_FORBIDDEN"));
        verifyNoInteractions(candidates);
    }
    @Test
    void refusesNonOdsModelsAndNeverRewritesTheirTarget() {
        var models = mock(ModelSpecApplicationService.class);
        var model = mock(ModelSpecContract.ModelSpecView.class); var id = UUID.randomUUID();
        when(models.get("tenant", id)).thenReturn(model); when(model.modelType()).thenReturn(ModelSpecContract.ModelType.FACT);
        var candidates = mock(ModelReleaseCandidateRepository.class);
        var service = new ModelIngestionTargetService(models, mock(ModelLifecycleRepository.class), candidates,
            mock(CandidatePublicationEvidenceRepository.class), mock(ModelExecutionTargetCatalogResolver.class), mock(ModelSpecPlanWriteAccessPort.class));
        var target = new ModelIngestionTargetService.Target(1, id, 1, "a".repeat(64), 1, "b".repeat(64), "dev", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "warehouse", "ods", "orders", List.of());
        assertThatThrownBy(() -> service.validateForExecution("tenant", target))
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, error -> assertThat(error.code()).isEqualTo("MODEL_INGESTION_TARGET_REQUIRES_ODS"));
        verifyNoInteractions(candidates);
    }
}
