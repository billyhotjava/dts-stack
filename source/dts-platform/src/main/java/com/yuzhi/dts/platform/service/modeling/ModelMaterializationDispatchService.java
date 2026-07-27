package com.yuzhi.dts.platform.service.modeling;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final Clock clock;

    @Autowired
    public ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelRuntimeSpecTokenCodec tokens
    ) {
        this(
            dispatches,
            builds,
            scopedProjects,
            dags,
            gateway,
            tokens,
            Clock.systemUTC()
        );
    }

    ModelMaterializationDispatchService(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        DbtScopedProjectService scopedProjects,
        DbtDagService dags,
        DbtExecutionGateway gateway,
        ModelRuntimeSpecTokenCodec tokens,
        Clock clock
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
        this.clock = Objects.requireNonNull(clock, "clock is required");
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
            if (submitted.status() == SubmissionStatus.SUBMITTED) {
                dispatches.markSubmitted(
                    dispatch.id(),
                    submitted.recovered(),
                    now
                );
                return Optional.of(
                    new DispatchResult(
                        dispatch.id(),
                        "SUBMITTED",
                        submitted.dagRunId(),
                        submitted.recovered(),
                        null
                    )
                );
            }
            if (
                submitted.status() ==
                SubmissionStatus.RETRYABLE_UNKNOWN
            ) {
                dispatches.markUnknown(
                    dispatch.id(),
                    submitted.errorCode(),
                    now.plus(RETRY_DELAY),
                    now
                );
                return Optional.of(
                    new DispatchResult(
                        dispatch.id(),
                        "UNKNOWN",
                        dispatch.airflowRunId(),
                        false,
                        submitted.errorCode()
                    )
                );
            }
            return Optional.of(
                block(dispatch, submitted.errorCode(), now)
            );
        } catch (
            DbtScopedProjectService.ScopedProjectException failure
        ) {
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (ModelReleaseCandidateException failure) {
            return Optional.of(block(dispatch, failure.code(), now));
        } catch (RuntimeException failure) {
            if (externalBoundaryCrossed) {
                String code = "MODEL_DISPATCH_PERSISTENCE_UNKNOWN";
                try {
                    dispatches.markUnknown(
                        dispatch.id(),
                        code,
                        now.plus(RETRY_DELAY),
                        now
                    );
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
        dispatches.markBlocked(dispatch.id(), code, now);
        return new DispatchResult(
            dispatch.id(),
            "BLOCKED",
            dispatch.airflowRunId(),
            false,
            code
        );
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
