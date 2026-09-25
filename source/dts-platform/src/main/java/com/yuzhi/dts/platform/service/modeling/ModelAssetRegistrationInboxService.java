package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned discovery and retry, including outputs that have no asset ID yet. */
@Service
public class ModelAssetRegistrationInboxService {
    private final ModelAssetRegistrationTaskRepository tasks;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelSpecApplicationService models;
    private final ModelSpecPlanWriteAccessPort access;

    public ModelAssetRegistrationInboxService(ModelAssetRegistrationTaskRepository tasks, ModelReleaseCandidateRepository candidates,
        ModelSpecApplicationService models, ModelSpecPlanWriteAccessPort access) {
        this.tasks = tasks; this.candidates = candidates; this.models = models; this.access = access;
    }

    @Transactional(readOnly = true)
    public Page list(String tenant, String actor, int offset) {
        if (offset < 0 || offset > 100000) throw failure("MODEL_REGISTRATION_PAGE_INVALID");
        var page = tasks.pendingPage(tenant, offset, 21);
        List<Item> visible = new ArrayList<>();
        for (var task : page.stream().limit(20).toList()) {
            var candidate = candidates.find(tenant, task.candidateId()).orElse(null);
            if (candidate == null) continue;
            try {
                var names = candidate.entries().stream().map(entry -> models.get(tenant, entry.modelSpecId()).name() + " · r" + entry.revision()).toList();
                visible.add(new Item(task.id(), task.candidateId(), task.builtCandidateVersion(), task.environment(), names,
                    task.state().name(), task.attempts(), task.lastErrorMessage(), task.lastModifiedDate(), access.canMaintain(tenant, task.planId(), actor)));
            } catch (ModelSpecException denied) {
                if (denied.kind() != ModelSpecException.Kind.FORBIDDEN && denied.kind() != ModelSpecException.Kind.NOT_FOUND) throw denied;
            }
        }
        return new Page(List.copyOf(visible), page.size() > 20 ? offset + 20 : null);
    }

    @Transactional
    public void retry(String tenant, String actor, UUID taskId, int buildVersion) {
        var task = tasks.findById(tenant, taskId).orElseThrow(() -> failure("MODEL_REGISTRATION_TASK_CHANGED"));
        if (!access.canMaintain(tenant, task.planId(), actor)) throw new ModelReleaseCandidateException(
            "MODEL_REGISTRATION_FORBIDDEN", "当前账号不能重试此登记任务", ModelReleaseCandidateException.Kind.FORBIDDEN);
        candidates.lockPlanForCandidate(tenant, task.planId());
        var candidate = candidates.find(tenant, task.candidateId()).orElseThrow(() -> failure("MODEL_REGISTRATION_TASK_CHANGED"));
        access.requireOperation(tenant, candidate.entries().stream().map(entry -> entry.modelSpecId()).toList(), actor);
        if (task.builtCandidateVersion() != buildVersion || !tasks.retry(task, Instant.now())) throw failure("MODEL_REGISTRATION_TASK_CHANGED");
    }

    private static ModelReleaseCandidateException failure(String code) {
        return new ModelReleaseCandidateException(code, "登记任务已变化，请刷新后重试", ModelReleaseCandidateException.Kind.CONFLICT);
    }
    public record Item(UUID id, UUID candidateId, int buildVersion, String environment, List<String> models,
        String state, int attempts, String errorMessage, Instant updatedAt, boolean canRetry) {}
    public record Page(List<Item> items, Integer nextOffset) {}
}
