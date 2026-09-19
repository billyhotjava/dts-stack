package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.DispatchRecord;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway.ReleaseBuildRequest;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway.SubmissionResult;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway.SubmissionStatus;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifact;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifactEntry;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.ScopedCandidateProject;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims durable candidate outbox rows and performs the external Airflow side effect outside the
 * START_BUILD transaction.
 */
@Service
public class ModelMaterializationDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(
        ModelMaterializationDispatchService.class
    );
    private static final Duration STALE_CLAIM_TTL =
        Duration.ofMinutes(2);
    private static final Duration SUBMITTED_RECONCILE_GRACE =
        Duration.ofMinutes(2);
    private static final int RECONCILE_BATCH_SIZE = 20;
    private static final Duration RETRY_DELAY =
        Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY =
        Duration.ofMinutes(5);
    /**
     * Claims (including the first one) after which an outcome that still cannot be confirmed fails
     * the build. With exponential backoff this is roughly 20 minutes of Airflow uncertainty.
     */
    static final int MAX_UNCONFIRMED_DISPATCH_ATTEMPTS = 8;
    /** A dispatch without progress for this long may be abandoned by a model maintainer. */
    static final Duration ABANDON_GRACE = Duration.ofMinutes(10);
    static final String RETRY_EXHAUSTED_CODE =
        "MODEL_MATERIALIZATION_DISPATCH_RETRY_EXHAUSTED";
    static final String ABANDONED_CODE =
        "MODEL_MATERIALIZATION_BUILD_ABANDONED";
    static final String RUN_TIMEOUT_CODE =
        "MODEL_MATERIALIZATION_RUN_TIMEOUT";
    static final String RUN_NOT_FOUND_CODE =
        "MODEL_AIRFLOW_RUN_NOT_FOUND";

    private final ModelMaterializationDispatchRepository dispatches;
    private final ModelMaterializationBuildRepository builds;
    private final ModelReleaseCandidateService candidates;
    private final DbtScopedProjectService scopedProjects;
    private final DbtDagService dags;
    private final DbtExecutionGateway gateway;
    private final ModelMaterializationRunArtifactService runArtifacts;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final ModelMaterializationSourceAvailabilityGuard sourceAvailability;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        ModelReleaseCandidateService candidates,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelMaterializationRunArtifactService runArtifacts,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        AuditService auditService,
        PlatformTransactionManager transactionManager
    ) {
        this(
            dispatches,
            builds,
            candidates,
            scopedProjects,
            dags,
            gateway,
            runArtifacts,
            tokens,
            sourceAvailability,
            auditService,
            Clock.systemUTC(),
            new TransactionTemplate(transactionManager)
        );
    }

    ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        ModelReleaseCandidateService candidates,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelMaterializationRunArtifactService runArtifacts,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        AuditService auditService,
        Clock clock,
        TransactionOperations transactions
    ) {
        this.dispatches = Objects.requireNonNull(
            dispatches,
            "dispatches is required"
        );
        this.builds = Objects.requireNonNull(
            builds,
            "builds is required"
        );
        this.candidates = Objects.requireNonNull(
            candidates,
            "candidates is required"
        );
        this.scopedProjects = Objects.requireNonNull(
            scopedProjects,
            "scopedProjects is required"
        );
        this.dags = Objects.requireNonNull(dags, "dags is required");
        this.gateway = Objects.requireNonNull(
            gateway,
            "gateway is required"
        );
        this.runArtifacts = Objects.requireNonNull(
            runArtifacts,
            "runArtifacts is required"
        );
        this.tokens = Objects.requireNonNull(
            tokens,
            "tokens is required"
        );
        this.sourceAvailability = Objects.requireNonNull(sourceAvailability, "sourceAvailability is required");
        this.auditService = Objects.requireNonNull(
            auditService,
            "auditService is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.transactions = Objects.requireNonNull(
            transactions,
            "transactions is required"
        );
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.materialization.dispatch-delay-ms:2000}"
    )
    public void dispatchQueued() {
        for (int index = 0; index < 20; index++) {
            if (dispatchNext().isEmpty()) return;
        }
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.materialization.reconcile-delay-ms:30000}",
        initialDelayString = "${dts.modeling.materialization.reconcile-delay-ms:30000}"
    )
    public void reconcileSubmitted() {
        Instant now = clock.instant();
        List<DispatchRecord> submitted = dispatches.findSubmittedBefore(
            now.minus(SUBMITTED_RECONCILE_GRACE),
            RECONCILE_BATCH_SIZE
        );
        for (DispatchRecord dispatch : submitted) {
            try {
                reconcileOne(dispatch, now);
            } catch (MachineAuditPersistenceException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                // One poisoned dispatch must not starve the rest of the batch on every pass.
                LOG.warn(
                    "event=model_materialization_reconcile_failed dispatch={} reason={}",
                    dispatch.id(),
                    failure.toString(),
                    failure
                );
            }
        }
    }

    private void reconcileOne(DispatchRecord dispatch, Instant now) {
        Optional<ReleaseBuildRequest> request = recoveryRequest(dispatch);
        if (request.isEmpty()) return;
        Optional<SubmissionResult> reconciled =
            gateway.reconcileReleaseBuild(request.orElseThrow());
        if (reconciled.isEmpty()) {
            // Airflow authoritatively has no run for an accepted submission: nothing will ever
            // finalize it, so fail it instead of showing "building" forever.
            expire(dispatch, RUN_NOT_FOUND_CODE, now);
            return;
        }
        SubmissionResult result = reconciled.orElseThrow();
        if (result.status() == SubmissionStatus.TERMINAL_FAILED) {
            finalizeAirflowFailure(dispatch, result);
            return;
        }
        if (result.status() == SubmissionStatus.TERMINAL_SUCCEEDED) {
            // The finalize callback was lost. The platform-side success path still requires every
            // model to carry sync-probe evidence and fails the build closed when it does not.
            runArtifacts.finalizeRun(
                dispatch.id(),
                new ModelMaterializationRunArtifactService.FinalizeCommand(
                    "SUCCEEDED"
                )
            );
            return;
        }
        if (
            result.status() == SubmissionStatus.SUBMITTED &&
            submittedTimedOut(dispatch, now)
        ) {
            expire(dispatch, RUN_TIMEOUT_CODE, now);
        }
    }

    private boolean submittedTimedOut(DispatchRecord dispatch, Instant now) {
        return dispatch.lastModifiedAt() != null &&
            !dispatch.lastModifiedAt().plus(submittedTimeout).isAfter(now);
    }

    private void expire(DispatchRecord dispatch, String code, Instant now) {
        transactions.executeWithoutResult(status -> {
            if (
                !dispatches.abandonActive(
                    dispatch.id(),
                    code,
                    now,
                    now.minus(STALE_CLAIM_TTL)
                )
            ) {
                return;
            }
            candidates.transition(
                dispatch.tenantId(),
                "service:dts-platform",
                dispatch.candidateId(),
                new TransitionCommand(
                    dispatch.candidateVersion(),
                    DeliveryStatus.BUILD_FAILED,
                    "materialization-dispatch-expired-" + dispatch.id(),
                    code
                )
            );
            auditDispatch(dispatch, "BLOCKED", code, AuditStage.FAIL, now);
        });
        LOG.warn(
            "event=model_materialization_dispatch_expired dispatch={} errorCode={}",
            dispatch.id(),
            code
        );
    }

    /**
     * Whether a model maintainer may abandon the current build of a BUILDING candidate: its
     * dispatch outcome is unconfirmed, it has made no progress within {@link #ABANDON_GRACE}, or
     * no live dispatch backs the candidate at all.
     */
    public boolean canAbandonBuild(
        String tenantId,
        UUID candidateId,
        int candidateVersion
    ) {
        return dispatches
            .findLatestForCandidate(tenantId, candidateId, candidateVersion)
            .map(dispatch -> abandonable(dispatch, clock.instant()))
            .orElse(true);
    }

    /**
     * Fences the current build attempt and moves the candidate to BUILD_FAILED so the maintainer
     * can retry or cancel. Late Airflow callbacks of the fenced attempt are rejected.
     */
    public CommandResult abandonBuild(
        String tenantId,
        String actorId,
        UUID candidateId,
        int candidateVersion,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        Instant now = clock.instant();
        return transactions.execute(status -> {
            Optional<DispatchRecord> latest = dispatches.lockLatestForCandidate(
                tenantId,
                candidateId,
                candidateVersion
            );
            if (latest.isPresent() && ACTIVE_STATUSES.contains(latest.orElseThrow().status())) {
                DispatchRecord dispatch = latest.orElseThrow();
                if (
                    !abandonable(dispatch, now) ||
                    !dispatches.abandonActive(
                        dispatch.id(),
                        ABANDONED_CODE,
                        now,
                        now.minus(STALE_CLAIM_TTL)
                    )
                ) {
                    throw new ModelReleaseCandidateException(
                        "MODEL_MATERIALIZATION_BUILD_IN_PROGRESS",
                        "The build is still progressing and cannot be abandoned yet",
                        ModelReleaseCandidateException.Kind.CONFLICT
                    );
                }
                auditDispatch(dispatch, "BLOCKED", ABANDONED_CODE, AuditStage.FAIL, now);
            }
            return candidates.transition(
                tenantId,
                actorId,
                candidateId,
                new TransitionCommand(
                    expectedVersion,
                    DeliveryStatus.BUILD_FAILED,
                    idempotencyKey,
                    reason == null || reason.isBlank() ? ABANDONED_CODE : reason
                )
            );
        });
    }

    private static final java.util.Set<String> ACTIVE_STATUSES =
        java.util.Set.of("PENDING", "CLAIMED", "SUBMITTED", "UNKNOWN");

    private static boolean abandonable(DispatchRecord dispatch, Instant now) {
        if (!ACTIVE_STATUSES.contains(dispatch.status())) return true;
        if ("UNKNOWN".equals(dispatch.status())) return true;
        Duration quietFor = "CLAIMED".equals(dispatch.status())
            ? STALE_CLAIM_TTL
            : ABANDON_GRACE;
        return dispatch.lastModifiedAt() == null ||
            !dispatch.lastModifiedAt().plus(quietFor).isAfter(now);
    }

    @org.springframework.beans.factory.annotation.Value(
        "${dts.modeling.materialization.submitted-timeout:PT12H}"
    )
    private Duration submittedTimeout = Duration.ofHours(12);

    @org.springframework.beans.factory.annotation.Autowired
    private ModelingExecutionAuthorization executionAuthorization;

    public Optional<DispatchResult> dispatchNext() {
        Instant now = clock.instant();
        Optional<DispatchRecord> claimed = dispatches.claimNext(
            now,
            STALE_CLAIM_TTL
        );
        if (claimed.isEmpty()) return Optional.empty();
        DispatchRecord dispatch = claimed.orElseThrow();
        boolean externalBoundaryCrossed = dispatch.runtimeTokenDigest() != null;
        try (var identity = executionAuthorization.candidate(dispatch.tenantId(), dispatch.candidateId(), dispatch.candidateVersion(), "BUILDING")) {
            CandidateBuildScope scope =
                builds.loadCandidateBuildScope(
                    dispatch.tenantId(),
                    dispatch.id()
            );
            validateIdentity(dispatch, scope);

            Optional<ReleaseBuildRequest> recoveryRequest =
                recoveryRequest(dispatch);
            if (recoveryRequest.isPresent()) {
                Optional<SubmissionResult> reconciled =
                    gateway.reconcileReleaseBuild(
                        recoveryRequest.orElseThrow()
                    );
                if (reconciled.isPresent()) {
                    externalBoundaryCrossed = true;
                    return Optional.of(
                        applySubmission(
                            dispatch,
                            reconciled.orElseThrow(),
                            now
                        )
                    );
                }
            }

            if (
                !dispatch
                    .artifactBundleChecksum()
                    .equals(scope.artifactBundleChecksum())
            ) {
                return Optional.of(
                    block(
                        dispatch,
                        "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT",
                        now
                    )
                );
            }

            sourceAvailability.requireDispatchCurrent(dispatch.id());
            List<PinnedSourceDefinition> pinnedSources = sourceAvailability.pinnedDispatchSources(dispatch.id());

            List<ModelMaterializationBuildRepository.BuildArtifact> pinnedDependencies =
                builds.loadPinnedDependencyArtifacts(scope);
            var artifacts = toArtifacts(scope, pinnedDependencies, pinnedSources);
            ModelingSqlReadSetGuard.requireArtifacts(toArtifacts(scope, List.of(), pinnedSources), pinnedSources);
            ScopedCandidateProject project = scopedProjects.prepareCandidate(artifacts);
            ModelRuntimeSpecTokenCodec.IssuedToken runtimeToken =
                runtimeToken(dispatch, now);
            if (!runtimeToken.expiresAt().isAfter(now)) {
                return Optional.of(
                    block(
                        dispatch,
                        "MODEL_RUNTIME_SPEC_TOKEN_EXPIRED",
                        now
                    )
                );
            }
            if (
                dispatch.scopedBundleChecksum() != null &&
                !dispatch
                    .scopedBundleChecksum()
                    .equals(project.bundleChecksum())
            ) {
                return Optional.of(
                    block(
                        dispatch,
                        "MATERIALIZATION_SCOPED_BUNDLE_DRIFT",
                        now
                    )
                );
            }
            dispatches.markPrepared(
                dispatch.id(),
                project.bundleChecksum(),
                runtimeToken.digest(),
                runtimeToken.expiresAt(),
                now
            );
            dags.ensureReleaseBuildDag(dispatch.airflowDagId());
            externalBoundaryCrossed = true;
            SubmissionResult submitted = gateway.submitReleaseBuild(
                new ReleaseBuildRequest(
                    dispatch.id(),
                    dispatch.candidateId(),
                    dispatch.candidateVersion(),
                    dispatch.attempt(),
                    dispatch.airflowDagId(),
                    dispatch.airflowRunId(),
                    runtimeToken.token(),
                    project.bundleChecksum()
                )
            );
            return Optional.of(
                applySubmission(dispatch, submitted, now)
            );
        } catch (com.yuzhi.dts.platform.security.modeling.ModelingIdentityException failure) {
            // An unavailable directory is retryable. A sealed runtime token may already have crossed the external boundary.
            if (failure.status() == 503 || externalBoundaryCrossed) {
                return Optional.of(markUnknown(dispatch, failure.code(), now));
            }
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (ModelSpecException failure) {
            if (externalBoundaryCrossed) {
                return Optional.of(markUnknown(dispatch, failure.code(), now));
            }
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (
            DbtScopedProjectService.ScopedProjectException failure
        ) {
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (ModelReleaseCandidateException failure) {
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (MachineAuditPersistenceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (externalBoundaryCrossed) {
                String code = "MODEL_DISPATCH_PERSISTENCE_UNKNOWN";
                try {
                    return Optional.of(markUnknown(dispatch, code, now));
                } catch (MachineAuditPersistenceException auditFailure) {
                    auditFailure.addSuppressed(failure);
                    throw auditFailure;
                } catch (RuntimeException ignored) {
                    // The stale-claim recovery path retries the same deterministic DagRun id.
                }
                return Optional.of(
                    new DispatchResult(
                        dispatch.id(),
                        "UNKNOWN",
                        dispatch.airflowRunId(),
                        false,
                        code
                    )
                );
            }
            return Optional.of(
                block(
                    dispatch,
                    "MATERIALIZATION_DISPATCH_FAILED",
                    now
                )
            );
        }
    }

    private Optional<ReleaseBuildRequest> recoveryRequest(
        DispatchRecord dispatch
    ) {
        if (
            dispatch.scopedBundleChecksum() == null &&
            dispatch.runtimeTokenDigest() == null &&
            dispatch.runtimeTokenExpiresAt() == null
        ) {
            return Optional.empty();
        }
        if (
            dispatch.scopedBundleChecksum() == null ||
            dispatch.runtimeTokenDigest() == null ||
            dispatch.runtimeTokenExpiresAt() == null
        ) {
            throw new IllegalStateException(
                "Prepared dispatch recovery metadata is incomplete"
            );
        }
        ModelRuntimeSpecTokenCodec.IssuedToken restored =
            tokens.restore(
                dispatch.id(),
                dispatch.runtimeTokenExpiresAt()
            );
        if (!restored.digest().equals(dispatch.runtimeTokenDigest())) {
            throw new IllegalStateException(
                "Prepared dispatch runtime token has drifted"
            );
        }
        return Optional.of(
            new ReleaseBuildRequest(
                dispatch.id(),
                dispatch.candidateId(),
                dispatch.candidateVersion(),
                dispatch.attempt(),
                dispatch.airflowDagId(),
                dispatch.airflowRunId(),
                restored.token(),
                dispatch.scopedBundleChecksum()
            )
        );
    }

    private DispatchResult applySubmission(
        DispatchRecord dispatch,
        SubmissionResult submitted,
        Instant now
    ) {
        if (
            submitted.status() == SubmissionStatus.TERMINAL_FAILED
        ) {
            return finalizeAirflowFailure(dispatch, submitted);
        }
        if (
            submitted.status() == SubmissionStatus.SUBMITTED ||
            submitted.status() == SubmissionStatus.TERMINAL_SUCCEEDED
        ) {
            // A finished successful run is recorded as submitted; reconciliation finalizes it.
            transactions.executeWithoutResult(status -> {
                dispatches.markSubmitted(
                    dispatch.id(),
                    submitted.recovered(),
                    now
                );
                auditDispatch(
                    dispatch,
                    "SUBMITTED",
                    null,
                    AuditStage.SUCCESS,
                    now
                );
            });
            return new DispatchResult(
                dispatch.id(),
                "SUBMITTED",
                submitted.dagRunId(),
                submitted.recovered(),
                null
            );
        }
        if (
            submitted.status() ==
            SubmissionStatus.RETRYABLE_UNKNOWN
        ) {
            return markUnknown(dispatch, submitted.errorCode(), now);
        }
        return block(dispatch, submitted.errorCode(), now);
    }

    private DispatchResult finalizeAirflowFailure(
        DispatchRecord dispatch,
        SubmissionResult result
    ) {
        runArtifacts.finalizeRun(
            dispatch.id(),
            new ModelMaterializationRunArtifactService.FinalizeCommand(
                "FAILED"
            )
        );
        return new DispatchResult(
            dispatch.id(),
            "FAILED",
            result.dagRunId(),
            true,
            result.errorCode()
        );
    }

    private ModelRuntimeSpecTokenCodec.IssuedToken runtimeToken(
        DispatchRecord dispatch,
        Instant now
    ) {
        if (
            dispatch.runtimeTokenDigest() == null &&
            dispatch.runtimeTokenExpiresAt() == null
        ) {
            return tokens.issue(dispatch.id(), now);
        }
        if (
            dispatch.runtimeTokenDigest() == null ||
            dispatch.runtimeTokenExpiresAt() == null
        ) {
            throw new IllegalStateException(
                "Runtime token metadata is incomplete"
            );
        }
        ModelRuntimeSpecTokenCodec.IssuedToken restored =
            tokens.restore(
                dispatch.id(),
                dispatch.runtimeTokenExpiresAt()
            );
        if (!restored.digest().equals(dispatch.runtimeTokenDigest())) {
            throw new IllegalStateException(
                "Runtime token metadata has drifted"
            );
        }
        return restored;
    }

    private DispatchResult block(
        DispatchRecord dispatch,
        String errorCode,
        Instant now
    ) {
        String code =
            errorCode == null || errorCode.isBlank()
                ? "MATERIALIZATION_DISPATCH_FAILED"
                : errorCode.trim();
        transactions.executeWithoutResult(status -> {
            dispatches.markBlocked(dispatch.id(), code, now);
            candidates.transition(
                dispatch.tenantId(),
                "service:dts-platform",
                dispatch.candidateId(),
                new TransitionCommand(
                    dispatch.candidateVersion(),
                    DeliveryStatus.BUILD_FAILED,
                    "materialization-dispatch-blocked-" + dispatch.id(),
                    code
                )
            );
            auditDispatch(
                dispatch,
                "BLOCKED",
                code,
                AuditStage.FAIL,
                now
            );
        });
        return new DispatchResult(
            dispatch.id(),
            "BLOCKED",
            dispatch.airflowRunId(),
            false,
            code
        );
    }

    private DispatchResult markUnknown(
        DispatchRecord dispatch,
        String errorCode,
        Instant now
    ) {
        String code =
            errorCode == null || errorCode.isBlank()
                ? "MODEL_DISPATCH_PERSISTENCE_UNKNOWN"
                : errorCode.trim();
        if (dispatch.dispatchAttempts() >= MAX_UNCONFIRMED_DISPATCH_ATTEMPTS) {
            // The run id is deterministic and the dispatch is fenced by BLOCKED, so a DagRun that
            // Airflow accepted without telling us can no longer consume the runtime spec.
            LOG.warn(
                "event=model_materialization_dispatch_retry_exhausted dispatch={} attempts={} lastErrorCode={}",
                dispatch.id(),
                dispatch.dispatchAttempts(),
                code
            );
            return block(dispatch, RETRY_EXHAUSTED_CODE, now);
        }
        transactions.executeWithoutResult(status -> {
            dispatches.markUnknown(
                dispatch.id(),
                code,
                now.plus(retryDelay(dispatch.dispatchAttempts())),
                now
            );
            auditDispatch(
                dispatch,
                "UNKNOWN",
                code,
                AuditStage.FAIL,
                now
            );
        });
        return new DispatchResult(
            dispatch.id(),
            "UNKNOWN",
            dispatch.airflowRunId(),
            false,
            code
        );
    }

    static Duration retryDelay(int dispatchAttempts) {
        int doublings = Math.max(0, Math.min(dispatchAttempts - 1, 10));
        Duration delay = RETRY_DELAY.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private void auditDispatch(
        DispatchRecord dispatch,
        String status,
        String errorCode,
        AuditStage stage,
        Instant occurredAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant", dispatch.tenantId());
        payload.put("candidate", dispatch.candidateId());
        payload.put("version", dispatch.candidateVersion());
        payload.put("attempt", dispatch.attempt());
        payload.put("dispatch", dispatch.id());
        payload.put("run", dispatch.airflowRunId());
        payload.put("status", status);
        if (errorCode != null) payload.put("errorCode", errorCode);
        try {
            auditService.auditActionAsStrict(
                "scheduler",
                dispatchEventIdentity(dispatch, status),
                occurredAt,
                "MODEL_MATERIALIZATION_DISPATCH_" + status,
                stage,
                dispatch.id().toString(),
                payload
            );
        } catch (RuntimeException failure) {
            if (isDuplicateDispatchAudit(failure)) {
                // The outbox keeps first-write-wins per deterministic event identity while a
                // retry carries a fresh occurredAt/errorCode. The logical event is already
                // recorded, so treat the retry as a successful no-op instead of poisoning the
                // dispatch loop with a rollback on every attempt.
                LOG.info(
                    "event=model_materialization_dispatch_audit_duplicate_suppressed dispatch={} status={} errorCode={}",
                    dispatch.id(),
                    status,
                    errorCode
                );
                return;
            }
            throw new MachineAuditPersistenceException(failure);
        }
    }

    private static String dispatchEventIdentity(
        DispatchRecord dispatch,
        String status
    ) {
        return (
            "model-materialization-dispatch:" +
            dispatch.id() +
            ":attempt:" +
            dispatch.attempt() +
            ":" +
            status.toLowerCase(java.util.Locale.ROOT)
        );
    }

    private static boolean isDuplicateDispatchAudit(
        Throwable failure
    ) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized =
                    message.toLowerCase(java.util.Locale.ROOT);
                if (
                    normalized.contains("already exists with") &&
                    normalized.contains("different payload")
                ) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class MachineAuditPersistenceException
        extends RuntimeException {

        private MachineAuditPersistenceException(RuntimeException cause) {
            super("Machine audit persistence failed", cause);
        }
    }

    private static void validateIdentity(
        DispatchRecord dispatch,
        CandidateBuildScope scope
    ) {
        if (
            !dispatch.tenantId().equals(scope.tenantId()) ||
            !dispatch.candidateId().equals(scope.candidateId()) ||
            dispatch.candidateVersion() != scope.candidateVersion() ||
            !dispatch.id().equals(scope.pipelineRunGroupId()) ||
            !dispatch
                .executionTargetKey()
                .equals(scope.executionTargetKey())
        ) {
            throw new IllegalStateException(
                "Candidate build scope does not match its dispatch"
            );
        }
    }

    private static List<CandidateArtifactEntry> toArtifacts(
        CandidateBuildScope scope,
        List<ModelMaterializationBuildRepository.BuildArtifact> pinnedDependencies,
        List<PinnedSourceDefinition> pinnedSources
    ) {
        List<ModelMaterializationBuildRepository.BuildArtifact> dependencies =
            pinnedDependencies == null ? List.of() : List.copyOf(pinnedDependencies);
        String sourceYaml = PinnedDbtSourceArtifacts.renderPinnedSources(pinnedSources);
        List<CandidateArtifactEntry> entries = new ArrayList<>();
        for (int index = 0; index < scope.entries().size(); index++) {
            CandidateBuildEntry entry = scope.entries().get(index);
            List<CandidateArtifact> artifacts = new ArrayList<>();
            entry.artifacts().forEach(artifact -> artifacts.add(
                new CandidateArtifact(
                    artifact.path(),
                    artifact.contentChecksum(),
                    artifact.content()
                )
            ));
            if (index == 0) {
                dependencies.forEach(artifact -> artifacts.add(
                    new CandidateArtifact(
                        artifact.path(),
                        artifact.contentChecksum(),
                        artifact.content()
                    )
                ));
                if (sourceYaml != null) {
                    artifacts.add(new CandidateArtifact(
                        "models/_dts_pinned_sources.yml",
                        sha256(sourceYaml),
                        sourceYaml
                    ));
                }
            }
            entries.add(
                new CandidateArtifactEntry(
                    entry.modelSpecId(),
                    entry.modelRevision(),
                    entry.modelChecksum(),
                    entry.implementationRevision(),
                    entry.implementationChecksum(),
                    entry.dbtUniqueId(),
                    List.copyOf(artifacts)
                )
            );
        }
        return List.copyOf(entries);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }


    public record DispatchResult(
        java.util.UUID pipelineRunGroupId,
        String status,
        String dagRunId,
        boolean recovered,
        String errorCode
    ) {}
}
