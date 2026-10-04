package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.RuntimeRecord;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileException;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseRequest;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService.RuntimeSpecView;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumes a binding-owned runtime token without exposing paths or credentials. */
@Service
public class PlanOperationalRuntimeSpecService {

    private final PlanOperationalRunRepository runs;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final DbtRuntimeProfileLeaseService leases;
    private final Clock clock;

    @Autowired
    public PlanOperationalRuntimeSpecService(
        PlanOperationalRunRepository runs,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeProfileLeaseService leases
    ) {
        this(runs, tokens, leases, Clock.systemUTC());
    }

    PlanOperationalRuntimeSpecService(
        PlanOperationalRunRepository runs,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeProfileLeaseService leases,
        Clock clock
    ) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.tokens = Objects.requireNonNull(tokens, "tokens is required");
        this.leases = Objects.requireNonNull(leases, "leases is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public RuntimeSpecView consume(String suppliedToken) {
        String digest;
        try {
            digest = tokens.tokenDigest(suppliedToken);
        } catch (RuntimeException invalid) {
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                "Runtime spec token is invalid",
                Kind.FORBIDDEN
            );
        }
        RuntimeRecord runtime = runs
            .lockRuntimeSpec(digest)
            .orElseThrow(() ->
                failure(
                    "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                    "Runtime spec token is invalid",
                    Kind.FORBIDDEN
                )
            );
        if (
            !tokens.matches(
                runtime.groupId(),
                suppliedToken,
                runtime.runtimeTokenDigest()
            )
        ) {
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                "Runtime spec token is invalid",
                Kind.FORBIDDEN
            );
        }
        Instant now = clock.instant();
        if (
            runtime.runtimeTokenExpiresAt() == null ||
            !now.isBefore(runtime.runtimeTokenExpiresAt())
        ) {
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_EXPIRED",
                "Runtime spec token has expired",
                Kind.CONFLICT
            );
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
            throw failure(
                unavailable.code() == null || unavailable.code().isBlank()
                    ? "DBT_EXECUTION_TARGET_SECRET_UNAVAILABLE"
                    : unavailable.code(),
                unavailable.getMessage(),
                Kind.UNAVAILABLE
            );
        }
        if (issuedNow) {
            if (
                !runs.attachRuntimeLease(
                    runtime.groupId(),
                    lease.profileLeaseId(),
                    now
                )
            ) {
                leases.release(lease.profileLeaseId());
                throw failure(
                    "MODEL_RUNTIME_SPEC_CONCURRENT_CONSUME",
                    "Runtime spec could not be consumed",
                    Kind.CONFLICT
                );
            }
        }
        return new RuntimeSpecView(
            runtime.groupId(),
            "OPERATIONAL_RUN",
            runtime.projectBundleChecksum(),
            runtime.selector(),
            lease.targetName(),
            lease.profileLeaseId(),
            lease.expiresAt(),
            lease.credentialVersionRef()
        );
    }

    private static void requireRuntime(RuntimeRecord runtime) {
        if (
            runtime.pipelineRunId() == null ||
            runtime.projectBundleChecksum() == null ||
            !runtime
                .projectBundleChecksum()
                .matches("^[0-9a-f]{64}$") ||
            runtime.selector() == null ||
            runtime.selector().isBlank()
        ) {
            throw failure(
                "MODEL_RUNTIME_SPEC_INCOMPLETE",
                "Runtime spec is incomplete",
                Kind.CONFLICT
            );
        }
    }

    private static PlanExecutionException failure(
        String code,
        String message,
        Kind kind
    ) {
        return new PlanExecutionException(code, message, kind);
    }
}
