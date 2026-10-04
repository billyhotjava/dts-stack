package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.TaskView;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asset writes and task completion share one transaction, fenced to the claimed build and attempt. */
@Service
public class ModelAssetRegistrationAttemptService {
    private final ModelAssetRegistrationTaskRepository tasks;
    private final ModelReleaseCandidateRepository candidates;
    private final CandidateQualityAssetRegistrationService registration;
    private final ModelingExecutionAuthorization authorization;

    public ModelAssetRegistrationAttemptService(ModelAssetRegistrationTaskRepository tasks,
        ModelReleaseCandidateRepository candidates, CandidateQualityAssetRegistrationService registration,
        ModelingExecutionAuthorization authorization) {
        this.tasks = tasks;
        this.candidates = candidates;
        this.registration = registration;
        this.authorization = authorization;
    }

    @Transactional
    public void register(TaskView task, int attempt) {
        candidates.lockPlanForCandidate(task.tenantId(), task.planId());
        if (!tasks.lockAttempt(task, attempt)) return;
        var candidate = candidates.find(task.tenantId(), task.candidateId()).orElseThrow(() -> failure("MODEL_ASSET_REGISTRATION_CANDIDATE_NOT_BUILT"));
        if (!CandidateGovernanceQualityEvidenceService.supportsQualityEvidence(candidate.status())) {
            throw failure("MODEL_ASSET_REGISTRATION_CANDIDATE_NOT_BUILT");
        }
        int initiatorVersion = tasks.findBuildInitiatorVersion(task.tenantId(), task.candidateId(), task.builtCandidateVersion())
            .orElseThrow(() -> failure("MODELING_EXECUTION_INITIATOR_MISSING"));
        try (var identity = authorization.candidate(task.tenantId(), task.candidateId(), initiatorVersion, "BUILDING")) {
            registration.ensureRegistered(candidate);
            if (!tasks.markSucceeded(task, attempt, Instant.now())) throw failure("MODEL_ASSET_REGISTRATION_STALE");
        }
    }

    private static ModelReleaseCandidateException failure(String code) {
        return new ModelReleaseCandidateException(code, "登记任务的构建批次或执行身份已变化", ModelReleaseCandidateException.Kind.CONFLICT);
    }
}
