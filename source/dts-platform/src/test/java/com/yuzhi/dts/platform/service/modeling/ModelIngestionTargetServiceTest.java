package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelIngestionTargetServiceTest {
    @Test
    void executionCannotResolveAcrossEnvironmentsWhenTheBindingOmitsItsEnvironment() {
        var models = mock(ModelSpecApplicationService.class);
        var candidates = mock(ModelReleaseCandidateRepository.class);
        var service = new ModelIngestionTargetService(models, mock(ModelLifecycleRepository.class), candidates,
            mock(CandidatePublicationEvidenceRepository.class), mock(ModelExecutionTargetCatalogResolver.class), mock(ModelSpecPlanWriteAccessPort.class));
        var target = new ModelIngestionTargetService.Target(1, UUID.randomUUID(), 1, "model", 1, "implementation", null,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "warehouse", "ods", "orders", List.of());
        assertThatThrownBy(() -> service.validateForExecution("tenant", target))
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class,
                error -> assertThat(error.code()).isEqualTo("MODEL_INGESTION_TARGET_INVALID"));
        verifyNoInteractions(models, candidates);
    }
    @Test
    void resolvesExactModelCandidateWithoutThePlanWorkbenchTwoRowLimit() {
        var models = mock(ModelSpecApplicationService.class);
        var model = mock(ModelSpecContract.ModelSpecView.class);
        var implementation = mock(ModelLifecycleContract.ImplementationView.class);
        var candidate = mock(ModelReleaseCandidateContract.CandidateView.class);
        var entry = mock(ModelReleaseCandidateContract.EntryView.class);
        var physical = mock(CandidatePublicationEvidenceRepository.PublicationEntryEvidence.class);
        var implementations = mock(ModelLifecycleRepository.class);
        var candidates = mock(ModelReleaseCandidateRepository.class);
        var evidence = mock(CandidatePublicationEvidenceRepository.class);
        var targets = mock(ModelExecutionTargetCatalogResolver.class);
        var target = mock(ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget.class);
        var access = mock(ModelSpecPlanWriteAccessPort.class);
        var id = UUID.randomUUID(); var plan = UUID.randomUUID(); var implementationId = UUID.randomUUID();
        when(models.get("tenant", id)).thenReturn(model);
        when(model.planId()).thenReturn(plan); when(model.modelType()).thenReturn(ModelSpecContract.ModelType.SOURCE);
        when(model.layer()).thenReturn(ModelSpecContract.Layer.ODS); when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn("model-checksum"); when(model.fields()).thenReturn(List.of());
        when(access.canMaintain("tenant", plan, "writer")).thenReturn(true);
        when(implementations.findImplementation("tenant", id)).thenReturn(java.util.Optional.of(implementation));
        when(implementation.id()).thenReturn(implementationId); when(implementation.revision()).thenReturn(3);
        when(implementation.modelChecksum()).thenReturn("model-checksum");
        when(implementation.implementationRevision()).thenReturn(2); when(implementation.implementationChecksum()).thenReturn("implementation-checksum");
        when(candidates.findLatestForModelCurrentRevision("tenant", plan, id, 3, "model-checksum", "dev"))
            .thenReturn(java.util.Optional.of(candidate));
        when(candidate.entries()).thenReturn(List.of(entry)); when(entry.modelSpecId()).thenReturn(id);
        when(entry.revision()).thenReturn(3); when(entry.checksum()).thenReturn("model-checksum");
        when(entry.implementationId()).thenReturn(implementationId);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(physical));
        when(physical.modelSpecId()).thenReturn(id); when(physical.implementationRevision()).thenReturn(2);
        when(physical.implementationChecksum()).thenReturn("implementation-checksum");
        when(physical.relationType()).thenReturn(PhysicalRelationInspector.ExpectedRelationType.TABLE);
        when(physical.identifier()).thenReturn("orders"); when(targets.resolve(candidate)).thenReturn(target);
        var service = new ModelIngestionTargetService(models, implementations, candidates, evidence, targets, access);
        assertThat(service.resolveForUser("tenant", "writer", id, "dev").tableName()).isEqualTo("orders");
        verify(candidates, never()).listForWorkbench(anyString(), any());
        when(physical.implementationRevision()).thenReturn(1);
        assertThatThrownBy(() -> service.resolveForUser("tenant", "writer", id, "dev"))
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class,
                error -> assertThat(error.code()).isEqualTo("MODEL_INGESTION_TARGET_STALE"));
    }
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
