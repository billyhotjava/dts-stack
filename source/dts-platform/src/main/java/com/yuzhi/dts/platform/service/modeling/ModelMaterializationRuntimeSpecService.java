package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileException;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseRequest;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumes one dispatch-bound runtime token and returns only non-sensitive execution metadata. */
@Service
public class ModelMaterializationRuntimeSpecService {

    private final ModelMaterializationDispatchRepository dispatches;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final ModelMaterializationSourceAvailabilityGuard sourceAvailability;
    private final ModelMaterializationAvailabilityAuditService availabilityAudit;
    private final DbtRuntimeProfileLeaseService leases;
    private final AuditService auditService;
    private final ModelMaterializationRuntimeFailureRecorder failures;
    private final Clock clock;

    @Autowired
    public ModelMaterializationRuntimeSpecService(
        ModelMaterializationDispatchRepository dispatches,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        ModelMaterializationAvailabilityAuditService availabilityAudit,
        DbtRuntimeProfileLeaseService leases,
        AuditService auditService,
        ModelMaterializationRuntimeFailureRecorder failures
    ) {
        this(
            dispatches,
            tokens,
            sourceAvailability,
            availabilityAudit,
            leases,
            auditService,
            failures,
            Clock.systemUTC()
        );
    }

    ModelMaterializationRuntimeSpecService(
        ModelMaterializationDispatchRepository dispatches,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        ModelMaterializationAvailabilityAuditService availabilityAudit,
        DbtRuntimeProfileLeaseService leases,
        AuditService auditService,
        ModelMaterializationRuntimeFailureRecorder failures,
        Clock clock
    ) {
        this.dispatches = Objects.requireNonNull(
            dispatches,
            "dispatches is required"
        );
        this.tokens = Objects.requireNonNull(
            tokens,
            "tokens is required"
        );
        this.sourceAvailability = Objects.requireNonNull(sourceAvailability, "sourceAvailability is required");
        this.availabilityAudit = Objects.requireNonNull(availabilityAudit, "availabilityAudit is required");
        this.leases = Objects.requireNonNull(
            leases,
            "leases is required"
        );
        this.auditService = Objects.requireNonNull(
            auditService,
            "auditService is required"
        );
        this.failures = Objects.requireNonNull(failures, "failures is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public RuntimeSpecView consume(String suppliedToken) {
        String tokenDigest;
        try {
            tokenDigest = tokens.tokenDigest(suppliedToken);
        } catch (RuntimeException invalid) {
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                "Runtime spec token is invalid"
            );
        }
        RuntimeSpecRecord runtime = dispatches
            .lockRuntimeSpec(tokenDigest)
            .orElseThrow(() ->
                failure(
                    "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                    "Runtime spec token is invalid"
                )
            );
        if (
            !tokens.matches(
                runtime.dispatchId(),
                suppliedToken,
                runtime.runtimeTokenDigest()
            )
        ) {
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                "Runtime spec token is invalid"
            );
        }
        Instant now = clock.instant();
        if (
            runtime.runtimeTokenExpiresAt() == null ||
            !now.isBefore(runtime.runtimeTokenExpiresAt())
        ) {
            throw recorded(
                runtime,
                "MODEL_RUNTIME_SPEC_TOKEN_EXPIRED",
                "Runtime spec token has expired"
            );
        }
        if (runtime.planId() == null) {
            // Fail fast before opening a system scope: without the dispatch's own plan
            // the scope could never satisfy the source guard below and the caller would
            // otherwise see a misleading fence denial instead of an incomplete spec.
            throw recorded(
                runtime,
                "MODEL_RUNTIME_SPEC_INCOMPLETE",
                "Runtime spec is incomplete"
            );
        }
        // The Airflow service-token callback carries no user identity, so the source-scope
        // guard below would fail closed without an explicitly opened system scope. The HMAC
        // runtime token already authenticated this dispatch, therefore the scope is bound to
        // the dispatch's own plan only.
        try (
            var executionScope = ModelingSystemExecution.open(
                runtime.tenantId(),
                runtime.planId()
            )
        ) {
            return consumeWithinSystemScope(runtime, now);
        }
    }

    private RuntimeSpecView consumeWithinSystemScope(
        RuntimeSpecRecord runtime,
        Instant now
    ) {
        try {
            sourceAvailability.requireDispatchCurrent(runtime.dispatchId());
        } catch (ModelReleaseCandidateException unavailable) {
            availabilityAudit.recordRuntimeDenied(runtime, unavailable.code(), now);
            throw recorded(runtime, unavailable.code(), unavailable.getMessage());
        }
        requireRuntime(runtime);
        LeaseView lease;
        boolean issuedNow = runtime.profileLeaseId() == null;
        try {
            lease = issuedNow
                ? leases.issue(
                    new LeaseRequest(
                        runtime.tenantId(),
                        runtime.pipelineRunId(),
                        runtime.airflowRunId(),
                        runtime.environment(),
                        runtime.executionTargetKey()
                    )
                )
                : leases.viewActive(runtime.profileLeaseId());
        } catch (DbtRuntimeProfileException unavailable) {
            String code = unavailable.code() == null || unavailable.code().isBlank()
                ? "DBT_EXECUTION_TARGET_SECRET_UNAVAILABLE"
                : unavailable.code();
            availabilityAudit.recordRuntimeDenied(runtime, code, now);
            throw recorded(runtime, code, unavailable.getMessage());
        }
        if (issuedNow) {
            if (
                !dispatches.attachRuntimeLease(
                    runtime.dispatchId(),
                    lease.profileLeaseId(),
                    now
                )
            ) {
                leases.release(lease.profileLeaseId());
                throw failure(
                    "MODEL_RUNTIME_SPEC_CONCURRENT_CONSUME",
                    "Runtime spec could not be consumed"
                );
            }
        }
        try {
            sourceAvailability.pinDispatchCurrent(runtime.dispatchId(), now);
        } catch (ModelReleaseCandidateException unavailable) {
            if (issuedNow) {
                try {
                    leases.release(lease.profileLeaseId());
                } catch (RuntimeException compensationFailure) {
                    unavailable.addSuppressed(compensationFailure);
                }
            }
            availabilityAudit.recordRuntimeDenied(runtime, unavailable.code(), now);
            throw recorded(runtime, unavailable.code(), unavailable.getMessage());
        }
        RuntimeSpecView view = new RuntimeSpecView(
            runtime.dispatchId(),
            "RELEASE_BUILD",
            runtime.scopedBundleChecksum(),
            includeAncestors(runtime.selector()),
            lease.targetName(),
            lease.profileLeaseId(),
            lease.expiresAt(),
            lease.credentialVersionRef()
        );
        try {
            auditRuntimeSpecConsumed(
                runtime,
                lease,
                runtime.runtimeConsumedAt() == null
                    ? now
                    : runtime.runtimeConsumedAt()
            );
        } catch (RuntimeException failure) {
            if (issuedNow) {
                try {
                    leases.release(lease.profileLeaseId());
                } catch (RuntimeException compensationFailure) {
                    failure.addSuppressed(compensationFailure);
                }
            }
            throw failure;
        }
        return view;
    }

    private static String includeAncestors(String selector) {
        return java.util.Arrays
            .stream(selector.trim().split("[,\\s]+"))
            .filter(value -> !value.isBlank())
            .map(value -> value.startsWith("+") ? value : "+" + value)
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private void auditRuntimeSpecConsumed(
        RuntimeSpecRecord runtime,
        LeaseView lease,
        Instant occurredAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant", runtime.tenantId());
        payload.put("candidate", runtime.candidateId());
        payload.put("version", runtime.candidateVersion());
        payload.put("attempt", runtime.attempt());
        payload.put("dispatch", runtime.dispatchId());
        payload.put("run", runtime.airflowRunId());
        payload.put("status", "CONSUMED");
        payload.put("leaseId", lease.profileLeaseId());
        payload.put("expiresAt", lease.expiresAt());
        auditService.auditActionAsStrict(
            "airflow",
            runtimeSpecEventIdentity(runtime),
            occurredAt,
            "MODEL_MATERIALIZATION_RUNTIME_SPEC_CONSUMED",
            AuditStage.SUCCESS,
            runtime.dispatchId().toString(),
            payload
        );
    }

    private static String runtimeSpecEventIdentity(
        RuntimeSpecRecord runtime
    ) {
        return (
            "model-materialization-runtime-spec:" +
            runtime.dispatchId() +
            ":attempt:" +
            runtime.attempt() +
            ":consumed"
        );
    }

    private static void requireRuntime(RuntimeSpecRecord runtime) {
        if (
            runtime.pipelineRunId() == null ||
            runtime.scopedBundleChecksum() == null ||
            !runtime
                .scopedBundleChecksum()
                .matches("^[0-9a-f]{64}$") ||
            runtime.selector() == null ||
            runtime.selector().isBlank() ||
            runtime.targetName() == null ||
            runtime.targetName().isBlank()
        ) {
            throw failure(
                "MODEL_RUNTIME_SPEC_INCOMPLETE",
                "Runtime spec is incomplete"
            );
        }
    }

    /** Authenticated refusal of this dispatch: persisted so finalize reports the root cause. */
    private ModelMaterializationRuntimeException recorded(
        RuntimeSpecRecord runtime,
        String code,
        String message
    ) {
        failures.recordAfterRollback(runtime.dispatchId(), code);
        return failure(code, message);
    }

    private static ModelMaterializationRuntimeException failure(
        String code,
        String message
    ) {
        return new ModelMaterializationRuntimeException(code, message);
    }

    public record RuntimeSpecView(
        java.util.UUID pipelineRunGroupId,
        String runPurpose,
        String projectBundleChecksum,
        String selector,
        String targetName,
        java.util.UUID profileLeaseId,
        Instant expiresAt,
        String credentialVersionRef,
        String runtimeProfileId,
        String candidateProfileId,
        String dbtCoreVersion,
        String dbtPostgresVersion,
        String adapter,
        String databaseType,
        String requirementsLockSha256,
        String candidateImageDigest,
        String imageRef,
        String evidenceManifestSha256
    ) {
        public RuntimeSpecView(
            java.util.UUID pipelineRunGroupId,
            String runPurpose,
            String projectBundleChecksum,
            String selector,
            String targetName,
            java.util.UUID profileLeaseId,
            Instant expiresAt,
            String credentialVersionRef
        ) {
            this(
                pipelineRunGroupId,
                runPurpose,
                projectBundleChecksum,
                selector,
                targetName,
                profileLeaseId,
                expiresAt,
                credentialVersionRef,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }
}
