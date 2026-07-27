package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.FinalizeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.RunArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRuntimeException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pairwise-authenticated Airflow callback for durable dbt run artifacts and final state. */
@RestController
@RequestMapping(
    "/api/internal/modeling/materialization/run-groups"
)
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and authentication.name == 'service:dts-airflow'"
)
public class ModelMaterializationRunInternalResource {

    private final ModelMaterializationRunArtifactService artifacts;

    public ModelMaterializationRunInternalResource(
        ModelMaterializationRunArtifactService artifacts
    ) {
        this.artifacts = artifacts;
    }

    @PostMapping("/{groupId}/sync-probe")
    public RunArtifactView syncAndProbe(
        @PathVariable UUID groupId,
        @RequestBody SyncProbeCommand command
    ) {
        return artifacts.syncAndProbe(groupId, command);
    }

    @PostMapping("/{groupId}/finalize")
    public RunArtifactView finalizeRun(
        @PathVariable UUID groupId,
        @RequestBody FinalizeCommand command
    ) {
        return artifacts.finalizeRun(groupId, command);
    }

    @ExceptionHandler(ModelMaterializationRuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(
        ModelMaterializationRuntimeException failure
    ) {
        HttpStatus status = switch (failure.code()) {
            case
                "MODEL_DBT_RUN_GROUP_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case
                "MODEL_DBT_RUN_IDENTITY_MISMATCH",
                "MODEL_DBT_FINALIZE_PRECONDITION_FAILED" ->
                HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
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
}
