package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.repository.modeling.*;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionDeploymentRepository.Binding;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.*;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionDeploymentService.DeployRequest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanExecutionDeploymentServiceTest {
    final PlanExecutionDeploymentRepository deployments = mock(PlanExecutionDeploymentRepository.class);
    final ModelReleaseCandidateRepository candidates = mock(ModelReleaseCandidateRepository.class);
    final CandidatePublicationRepository publications = mock(CandidatePublicationRepository.class);
    final ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
    final ModelSpecPlanWriteAccessPort access = mock(ModelSpecPlanWriteAccessPort.class);
    final ReleaseDutyResolver duties = mock(ReleaseDutyResolver.class);
    final PlanExecutionHealthService health = mock(PlanExecutionHealthService.class);
    final AirflowClient airflow = mock(AirflowClient.class);
    final PlanExecutionDeploymentService service = new PlanExecutionDeploymentService(deployments, candidates, publications, models, access, duties, health, airflow);
    final UUID plan = UUID.randomUUID(), id = UUID.randomUUID(), release = UUID.randomUUID(), model = UUID.randomUUID();
    final CandidateView candidate = mock(CandidateView.class);
    final PublishedModelBinding entry = new PublishedModelBinding(model, release, 2, "model.a", "a", "checksum", "dependencies", UUID.randomUUID());
    @BeforeEach void setup() {
        when(duties.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(access.canMaintain("t", plan, "a")).thenReturn(true);
        when(candidates.find("t", id)).thenReturn(Optional.of(candidate));
        when(candidate.planId()).thenReturn(plan); when(candidate.id()).thenReturn(id);
        when(candidate.status()).thenReturn(DeliveryStatus.PUBLISHED); when(candidate.version()).thenReturn(9);
        when(candidate.environment()).thenReturn("prod"); when(candidate.executionTargetKey()).thenReturn("target");
        when(publications.loadPublishedScope(candidate)).thenReturn(List.of(entry));
    }
    DeployRequest request(int version) { return new DeployRequest(id, 9, List.of(release), version); }
    @Test void deploymentRequiresAnExplicitFrozenSelection() {
        service.deploy("t", "a", plan, request(0));
        verify(access).requireOperation("t", List.of(model), "a");
        verify(publications).rebuildManualBinding(eq(candidate), eq(List.of(entry)), eq("a"), any());
        verifyNoInteractions(airflow);
    }
    @Test void changedReleaseScopeCannotSilentlyDeploy() {
        assertThatThrownBy(() -> service.deploy("t", "a", plan, new DeployRequest(id, 9, List.of(UUID.randomUUID()), 0)))
            .isInstanceOf(PlanExecutionException.class);
        verify(publications, never()).rebuildManualBinding(any(), any(), any(), any());
    }
    @Test void activeRunOrBindingDriftPreventsReplacement() {
        when(deployments.bindings("t", plan, true)).thenReturn(List.of(new Binding(UUID.randomUUID(), 3, "prod", "target", "dag", "ACTIVE", true)));
        assertThatThrownBy(() -> service.deploy("t", "a", plan, request(3))).isInstanceOf(PlanExecutionException.class);
        verifyNoInteractions(airflow);
        verify(publications, never()).rebuildManualBinding(any(), any(), any(), any());
    }
    @Test void publisherWithoutOperatorDutyCannotDeploy() {
        when(duties.currentDuties()).thenReturn(Set.of());
        assertThatThrownBy(() -> service.deploy("t", "a", plan, request(0))).isInstanceOf(PlanExecutionException.class);
        verifyNoInteractions(candidates, publications, airflow);
    }
    @Test void enableRequiresVersionAndServerReadyAction() {
        UUID bindingId = UUID.randomUUID();
        var binding = new Binding(bindingId, 3, "prod", "target", "dag", "ACTIVE", false);
        when(deployments.bindings("t", plan, true)).thenReturn(List.of(binding));
        var view = mock(PlanExecutionHealthService.BindingView.class);
        when(view.id()).thenReturn(bindingId); when(view.allowedActions()).thenReturn(List.of("ENABLE"));
        when(health.workspace("t", "a", plan)).thenReturn(new PlanExecutionHealthService.WorkspaceView(plan, "READY", List.of(view)));
        when(airflow.setDagPaused("dag", false)).thenReturn(Optional.of(Map.of("is_paused", false)));
        when(deployments.enable("t", binding, "a")).thenReturn(true);
        assertThatThrownBy(() -> service.enable("t", "a", plan, bindingId, 2)).isInstanceOf(PlanExecutionException.class);
        verifyNoInteractions(airflow);
        service.enable("t", "a", plan, bindingId, 3);
        verify(access).requireBindingOperation("t", plan, bindingId, "a");
        verify(deployments).enable("t", binding, "a");
    }
}
