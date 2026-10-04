package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeSpecService.RuntimeSpecView;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRunService;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRunService.ScheduledOpenView;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRunArtifactService;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRuntimeSpecService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.FinalizeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.RunArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pairwise-authenticated Airflow boundary for scheduled OPERATIONAL_RUN preparation. */
@RestController
@RequestMapping(
    "/api/internal/modeling/execution-bindings"
)
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and authentication.name == 'service:dts-airflow'"
)
public class PlanExecutionInternalResource {

    private final PlanOperationalRunService runs;
    private final PlanOperationalRuntimeSpecService runtimeSpecs;
    private final PlanOperationalRunArtifactService artifacts;

    public PlanExecutionInternalResource(
        PlanOperationalRunService runs,
        PlanOperationalRuntimeSpecService runtimeSpecs,
        PlanOperationalRunArtifactService artifacts
    ) {
        this.runs = runs;
        this.runtimeSpecs = runtimeSpecs;
        this.artifacts = artifacts;
    }

    @PostMapping("/{bindingId}/scheduled-runs/open")
    public ScheduledOpenView openScheduled(
        @PathVariable UUID bindingId,
        @RequestBody ScheduledOpenRequest request
    ) {
        if (request == null) {
            throw new PlanExecutionException(
                "MODEL_OPERATIONAL_RUN_REQUEST_INVALID",
                "Scheduled run request is required",
                PlanExecutionException.Kind.INVALID
            );
        }
        return runs.openScheduled(
            bindingId,
            request.dagRunId(),
            request.logicalDate(),
            request.deploymentChecksum()
        );
    }

    @PostMapping("/runtime-specs/consume")
    public RuntimeSpecView consumeRuntime(
        @RequestHeader(
            "X-DTS-Runtime-Spec-Token"
        ) String token
    ) {
        return runtimeSpecs.consume(token);
    }

    @PostMapping("/run-groups/{groupId}/sync-probe")
    public RunArtifactView syncAndProbe(
        @PathVariable UUID groupId,
        @RequestBody SyncProbeCommand command
    ) {
        return artifacts.syncAndProbe(groupId, command);
    }

    @PostMapping("/run-groups/{groupId}/finalize")
    public RunArtifactView finalizeRun(
        @PathVariable UUID groupId,
        @RequestBody FinalizeCommand command
    ) {
        return artifacts.finalizeRun(groupId, command);
    }

    @ExceptionHandler(PlanExecutionException.class)
    public ResponseEntity<Map<String, String>> handle(
        PlanExecutionException failure
    ) {
        HttpStatus status = switch (failure.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity
            .status(status)
            .body(
                Map.of(
                    "code",
                    failure.code(),
                    "message",
                    failure.getMessage()
                )
            );
    }

    public record ScheduledOpenRequest(
        String dagRunId,
        Instant logicalDate,
        String deploymentChecksum
    ) {}
}
