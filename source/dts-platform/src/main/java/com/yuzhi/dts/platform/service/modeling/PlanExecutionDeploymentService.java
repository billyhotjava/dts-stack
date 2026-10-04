package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionDeploymentRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionDeploymentRepository.Binding;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanExecutionDeploymentService {
    private final PlanExecutionDeploymentRepository deployments;
    private final ModelReleaseCandidateRepository candidates;
    private final CandidatePublicationRepository publications;
    private final ModelSpecApplicationService models;
    private final ModelSpecPlanWriteAccessPort access;
    private final ReleaseDutyResolver duties;
    private final PlanExecutionHealthService health;
    private final AirflowClient airflow;

    public PlanExecutionDeploymentService(PlanExecutionDeploymentRepository deployments, ModelReleaseCandidateRepository candidates,
        CandidatePublicationRepository publications, ModelSpecApplicationService models, ModelSpecPlanWriteAccessPort access,
        ReleaseDutyResolver duties, PlanExecutionHealthService health, AirflowClient airflow) {
        this.deployments=deployments; this.candidates=candidates; this.publications=publications; this.models=models;
        this.access=access; this.duties=duties; this.health=health; this.airflow=airflow;
    }

    @Transactional(readOnly=true)
    public List<Publication> publications(String tenant, String actor, UUID plan) {
        // Reuse the existing plan read gate before exposing model names or release facts.
        health.workspace(tenant, actor, plan);
        var bindings = deployments.bindings(tenant, plan, false);
        List<Publication> result = new ArrayList<>();
        for (UUID id : deployments.publishedTargets(tenant, plan)) {
            var candidate = candidates.find(tenant, id).orElseThrow();
            var scope = publications.loadPublishedScope(candidate);
            if (scope.isEmpty()) continue;
            try {
                var names = scope.stream().map(entry -> models.get(tenant, entry.modelSpecId()).name() + " · r" + entry.modelRevision()).toList();
                var binding = matching(bindings, candidate);
                result.add(new Publication(id, candidate.version(), candidate.environment(), names, releaseIds(scope),
                    binding == null ? 0 : binding.version(), canOperate(tenant, actor, plan) && (binding == null || !binding.running() && !"DEPLOYING".equals(binding.status()))));
            } catch (ModelSpecException denied) {
                if (denied.kind() != ModelSpecException.Kind.FORBIDDEN && denied.kind() != ModelSpecException.Kind.NOT_FOUND) throw denied;
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public void deploy(String tenant, String actor, UUID plan, DeployRequest request) {
        requireOperator(tenant, actor, plan);
        if (request == null || request.candidateId() == null || request.releaseIds() == null) throw conflict();
        candidates.lockPlanForCandidate(tenant, plan);
        var candidate = candidates.find(tenant, request.candidateId()).orElseThrow(PlanExecutionDeploymentService::conflict);
        if (!plan.equals(candidate.planId()) || !"PUBLISHED".equals(candidate.status().name()) || candidate.version() != request.candidateVersion()) throw conflict();
        var scope = publications.loadPublishedScope(candidate);
        if (scope.isEmpty() || !releaseIds(scope).equals(request.releaseIds().stream().sorted().toList())) throw conflict();
        access.requireOperation(tenant, scope.stream().map(PublishedModelBinding::modelSpecId).toList(), actor);
        var binding = matching(deployments.bindings(tenant, plan, true), candidate);
        if ((binding == null ? 0 : binding.version()) != request.bindingVersion()
            || binding != null && (binding.running() || "DEPLOYING".equals(binding.status()))) throw conflict();
        // Explicit replacement requires a paused runtime; failed remote calls leave the prior binding intact.
        if (binding != null && airflow.setDagPaused(binding.dagId(), true).isEmpty()) throw unavailable();
        publications.rebuildManualBinding(candidate, scope, actor, Instant.now());
    }

    @Transactional
    public void enable(String tenant, String actor, UUID plan, UUID id, int version) {
        requireOperator(tenant, actor, plan);
        candidates.lockPlanForCandidate(tenant, plan);
        var binding = deployments.bindings(tenant, plan, true).stream().filter(value -> value.id().equals(id)).findFirst()
            .orElseThrow(PlanExecutionDeploymentService::conflict);
        if (binding.version() != version || binding.running()) throw conflict();
        access.requireBindingOperation(tenant, plan, id, actor);
        var current = health.workspace(tenant, actor, plan).bindings().stream().filter(value -> value.id().equals(id)).findFirst()
            .orElseThrow(PlanExecutionDeploymentService::conflict);
        if (!current.allowedActions().contains("ENABLE")) throw conflict();
        var actual = airflow.setDagPaused(binding.dagId(), false);
        if (actual.isEmpty() || !Boolean.FALSE.equals(actual.orElseThrow().get("is_paused"))) throw unavailable();
        if (!deployments.enable(tenant, binding, actor)) throw conflict();
    }

    private boolean canOperate(String tenant, String actor, UUID plan) {
        var roles = duties.currentDuties();
        return roles != null && roles.contains(DeliveryActorRole.RELEASE_OPERATOR) && access.canMaintain(tenant, plan, actor);
    }
    private void requireOperator(String tenant, String actor, UUID plan) {
        if (!canOperate(tenant, actor, plan)) throw new PlanExecutionException("MODEL_PLAN_DEPLOY_FORBIDDEN", "需要运行维护权限及建模规划操作权限", Kind.FORBIDDEN);
    }
    private static Binding matching(List<Binding> bindings, CandidateView candidate) {
        return bindings.stream().filter(binding -> Objects.equals(binding.environment(), candidate.environment())
            && Objects.equals(binding.target(), candidate.executionTargetKey())).findFirst().orElse(null);
    }
    private static List<UUID> releaseIds(List<PublishedModelBinding> scope) { return scope.stream().map(PublishedModelBinding::releaseId).sorted().toList(); }
    private static PlanExecutionException conflict() { return new PlanExecutionException("MODEL_PLAN_DEPLOY_CONFLICT", "发布范围或运行计划已变化，请刷新后重试", Kind.CONFLICT); }
    private static PlanExecutionException unavailable() { return new PlanExecutionException("MODEL_PLAN_DEPLOY_UNAVAILABLE", "暂时无法确认运行服务状态，请稍后重试", Kind.UNAVAILABLE); }
    public record Publication(UUID candidateId, int candidateVersion, String environment, List<String> models, List<UUID> releaseIds, int bindingVersion, boolean canDeploy) {}
    public record DeployRequest(UUID candidateId, int candidateVersion, List<UUID> releaseIds, int bindingVersion) {}
}
