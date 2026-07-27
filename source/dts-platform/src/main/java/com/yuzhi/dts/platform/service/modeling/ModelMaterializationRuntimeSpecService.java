package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseRequest;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumes one dispatch-bound runtime token and returns only non-sensitive execution metadata. */
@Service
public class ModelMaterializationRuntimeSpecService {

    private final ModelMaterializationDispatchRepository dispatches;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final DbtRuntimeProfileLeaseService leases;
    private final Clock clock;

    @Autowired
    public ModelMaterializationRuntimeSpecService(
        ModelMaterializationDispatchRepository dispatches,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeProfileLeaseService leases
    ) {
        this(dispatches, tokens, leases, Clock.systemUTC());
    }

    ModelMaterializationRuntimeSpecService(
        ModelMaterializationDispatchRepository dispatches,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeProfileLeaseService leases,
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
        this.leases = Objects.requireNonNull(
            leases,
            "leases is required"
        );
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
            throw failure(
                "MODEL_RUNTIME_SPEC_TOKEN_EXPIRED",
                "Runtime spec token has expired"
            );
        }
        requireRuntime(runtime);
        LeaseView lease;
        if (runtime.profileLeaseId() != null) {
            lease = leases.viewActive(runtime.profileLeaseId());
        } else {
            lease = leases.issue(
                new LeaseRequest(
                    runtime.tenantId(),
                    runtime.pipelineRunId(),
                    runtime.airflowRunId(),
                    runtime.environment(),
                    runtime.executionTargetKey()
                )
            );
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
        return new RuntimeSpecView(
            runtime.dispatchId(),
            "RELEASE_BUILD",
            runtime.scopedBundleChecksum(),
            runtime.selector(),
            lease.targetName(),
            lease.profileLeaseId(),
            lease.expiresAt(),
            lease.credentialVersionRef()
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
        String credentialVersionRef
    ) {}
}
