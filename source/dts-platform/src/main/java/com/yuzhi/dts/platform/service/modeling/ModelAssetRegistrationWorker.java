package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.TaskView;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * F15 K3: registers the outputs of confirmed builds, one task at a time. A failure is recorded on its own
 * task with a backoff and never touches the build or other tasks; after the last automatic attempt the task
 * stays FAILED until someone retries registration from the data asset catalog.
 */
@Service
public class ModelAssetRegistrationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ModelAssetRegistrationWorker.class);
    static final int MAX_ATTEMPTS = 5;
    static final int BATCH_SIZE = 20;
    static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration[] BACKOFF = {
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Duration.ofHours(1),
        Duration.ofHours(3),
    };

    private final ModelAssetRegistrationTaskRepository tasks;
    private final CandidateQualityAssetRegistrationService registration;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelingExecutionAuthorization executionAuthorization;
    private final Clock clock;

    @Autowired
    public ModelAssetRegistrationWorker(
        ModelAssetRegistrationTaskRepository tasks,
        CandidateQualityAssetRegistrationService registration,
        ModelReleaseCandidateRepository candidates,
        ModelingExecutionAuthorization executionAuthorization
    ) {
        this(tasks, registration, candidates, executionAuthorization, Clock.systemUTC());
    }

    ModelAssetRegistrationWorker(
        ModelAssetRegistrationTaskRepository tasks,
        CandidateQualityAssetRegistrationService registration,
        ModelReleaseCandidateRepository candidates,
        ModelingExecutionAuthorization executionAuthorization,
        Clock clock
    ) {
        this.tasks = Objects.requireNonNull(tasks, "tasks is required");
        this.registration = Objects.requireNonNull(registration, "registration is required");
        this.candidates = Objects.requireNonNull(candidates, "candidates is required");
        this.executionAuthorization = Objects.requireNonNull(executionAuthorization, "executionAuthorization is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Scheduled(fixedDelayString = "${dts.modeling.asset-registration.delay-ms:10000}")
    public void registerDue() {
        Instant now = clock.instant();
        List<TaskView> due = tasks.findDue(now, MAX_ATTEMPTS, BATCH_SIZE);
        for (TaskView task : due) {
            if (!tasks.claim(task.id(), now, now.plus(LEASE))) continue;
            attempt(task, task.attempts() + 1);
        }
    }

    void attempt(TaskView task, int attempt) {
        Instant now = clock.instant();
        try {
            CandidateView candidate = candidates.find(task.tenantId(), task.candidateId()).orElse(null);
            if (candidate == null || !CandidateGovernanceQualityEvidenceService.supportsQualityEvidence(candidate.status())) {
                fail(task, attempt, "MODEL_ASSET_REGISTRATION_CANDIDATE_NOT_BUILT", "发布单已不处于可登记的构建状态", now);
                return;
            }
            Integer initiatorVersion = tasks
                .findBuildInitiatorVersion(task.tenantId(), task.candidateId(), task.builtCandidateVersion())
                .orElse(null);
            if (initiatorVersion == null) {
                fail(task, attempt, "MODELING_EXECUTION_INITIATOR_MISSING", "找不到发起本次构建的用户", now);
                return;
            }
            try (var identity = executionAuthorization.candidate(
                task.tenantId(), task.candidateId(), initiatorVersion, "BUILDING"
            )) {
                registration.ensureRegistered(candidate);
            }
            tasks.markSucceeded(task.tenantId(), task.candidateId(), clock.instant());
        } catch (ModelReleaseCandidateException failure) {
            fail(task, attempt, failure.code(), failure.getMessage(), now);
        } catch (ModelingIdentityException denied) {
            fail(task, attempt, denied.code(), "登记执行身份不可用", now);
        } catch (RuntimeException failure) {
            fail(task, attempt, "MODEL_ASSET_REGISTRATION_FAILED", failure.getClass().getSimpleName(), now);
        }
    }

    private void fail(TaskView task, int attempt, String code, String message, Instant now) {
        Duration wait = BACKOFF[Math.min(Math.max(attempt, 1), BACKOFF.length) - 1];
        tasks.markFailed(task.id(), code, message, now.plus(wait), now);
        LOG.warn(
            "event=model_asset_registration_failed candidateId={} attempt={} maxAttempts={} code={}",
            task.candidateId(), attempt, MAX_ATTEMPTS, code
        );
    }
}
