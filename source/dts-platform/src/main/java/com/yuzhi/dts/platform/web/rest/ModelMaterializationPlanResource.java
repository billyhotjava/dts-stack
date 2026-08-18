package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Preview;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.PreviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, plan-scoped preview boundary for dependency-aware materialization. */
@RestController
@RequestMapping("/api/modeling/plans/{planId}/materialization-plans")
public class ModelMaterializationPlanResource {

    private static final String RELEASE_DUTY_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_DUTIES)";

    private final ModelReleaseCandidateApplicationService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelMaterializationPlanResource(
        ModelReleaseCandidateApplicationService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping("/preview")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<Preview>> preview(
        @PathVariable UUID planId,
        @RequestBody(required = false) PreviewRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("materialization preview request is required");
        }
        Preview preview = service.previewMaterializationPlan(
            serverTenantId,
            actorId(),
            planId,
            new PreviewCommand(
                planId,
                request.environment(),
                request.requestedModelSpecIds(),
                request.strategy()
            )
        );
        return ResponseEntity.ok(ApiResponses.ok(preview));
    }

    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> handleCandidateError(ModelReleaseCandidateException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity
            .status(status)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
    }

    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequest(Exception exception) {
        return ResponseEntity
            .badRequest()
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    "Materialization preview request is invalid",
                    "MODEL_MATERIALIZATION_PLAN_REQUEST_INVALID",
                    null
                )
            );
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    public record PreviewRequest(
        String environment,
        List<UUID> requestedModelSpecIds,
        Strategy strategy
    ) {}
}
