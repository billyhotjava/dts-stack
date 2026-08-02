package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private static final Duration STALE_CLAIM_TTL =
        Duration.ofMinutes(2);
    private static final Duration RETRY_DELAY =
        Duration.ofSeconds(30);

    private final ModelMaterializationDispatchRepository dispatches;
    private final ModelMaterializationBuildRepository builds;
    private final DbtScopedProjectService scopedProjects;
    private final DbtDagService dags;
    private final DbtExecutionGateway gateway;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final DbtRuntimeCertificationService runtimeCertification;
    private final ModelMaterializationSourceAvailabilityGuard sourceAvailability;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeCertificationService runtimeCertification,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        AuditService auditService,
        PlatformTransactionManager transactionManager
    ) {
        this(
            dispatches,
            builds,
            scopedProjects,
            dags,
            gateway,
            tokens,
            runtimeCertification,
            sourceAvailability,
            auditService,
            Clock.systemUTC(),
            new TransactionTemplate(transactionManager)
        );
    }

    ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeCertificationService runtimeCertification,
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
        this.scopedProjects = Objects.requireNonNull(
            scopedProjects,
            "scopedProjects is required"
        );
        this.dags = Objects.requireNonNull(dags, "dags is required");
        this.gateway = Objects.requireNonNull(
            gateway,
            "gateway is required"
        );
        this.tokens = Objects.requireNonNull(
            tokens,
            "tokens is required"
        );
        this.runtimeCertification = Objects.requireNonNull(
            runtimeCertification,
            "runtimeCertification is required"
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

    public Optional<DispatchResult> dispatchNext() {
        Instant now = clock.instant();
        Optional<DispatchRecord> claimed = dispatches.claimNext(
            now,
            STALE_CLAIM_TTL
        );
        if (claimed.isEmpty()) return Optional.empty();
        DispatchRecord dispatch = claimed.orElseThrow();
        boolean externalBoundaryCrossed = false;
        try {
            CandidateBuildScope scope =
                builds.loadCandidateBuildScope(
                    dispatch.tenantId(),
                    dispatch.id()
            );
            validateIdentity(dispatch, scope);
            runtimeCertification.requireCertified();

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

            ScopedCandidateProject project =
                scopedProjects.prepareCandidate(toArtifacts(scope));
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
                    markUnknown(dispatch, code, now);
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
        if (submitted.status() == SubmissionStatus.SUBMITTED) {
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
            markUnknown(dispatch, submitted.errorCode(), now);
            return new DispatchResult(
                dispatch.id(),
                "UNKNOWN",
                dispatch.airflowRunId(),
                false,
                submitted.errorCode()
            );
        }
        return block(dispatch, submitted.errorCode(), now);
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

    private void markUnknown(
        DispatchRecord dispatch,
        String errorCode,
        Instant now
    ) {
        String code =
            errorCode == null || errorCode.isBlank()
                ? "MODEL_DISPATCH_PERSISTENCE_UNKNOWN"
                : errorCode.trim();
        transactions.executeWithoutResult(status -> {
            dispatches.markUnknown(
                dispatch.id(),
                code,
                now.plus(RETRY_DELAY),
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
        CandidateBuildScope scope
    ) {
        return scope
            .entries()
            .stream()
            .map(entry ->
                new CandidateArtifactEntry(
                    entry.modelSpecId(),
                    entry.modelRevision(),
                    entry.modelChecksum(),
                    entry.implementationRevision(),
                    entry.implementationChecksum(),
                    entry.dbtUniqueId(),
                    entry
                        .artifacts()
                        .stream()
                        .map(artifact ->
                            new CandidateArtifact(
                                artifact.path(),
                                artifact.contentChecksum(),
                                artifact.content()
                            )
                        )
                        .toList()
                )
            )
            .toList();
    }

    public record DispatchResult(
        java.util.UUID pipelineRunGroupId,
        String status,
        String dagRunId,
        boolean recovered,
        String errorCode
    ) {}
}
