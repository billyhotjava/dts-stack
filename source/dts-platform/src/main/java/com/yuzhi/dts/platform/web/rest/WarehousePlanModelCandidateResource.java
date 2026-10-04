package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanModelCandidateService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanModelCandidateService.CandidatePreview;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanModelCandidateService.ConfirmCandidateCommand;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asset-first acceleration that never persists a second candidate ledger. */
@RestController
@RequestMapping("/api/modeling/warehouse-plans/{planId}/model-candidates")
public class WarehousePlanModelCandidateResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final WarehousePlanModelCandidateService service;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanApplicationService planService;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final String serverTenantId;

    public WarehousePlanModelCandidateResource(
        WarehousePlanModelCandidateService service,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanApplicationService planService,
        WarehousePlanAuthorizationGuard authorizationGuard,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.planService = planService;
        this.authorizationGuard = authorizationGuard;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping("/preview")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<CandidatePreview> preview(@PathVariable UUID planId) {
        WarehousePlanActor actor = requirePlanRead(planId);
        return ApiResponses.ok(service.preview(serverTenantId, planId, accessContext(actor)));
    }

    @PostMapping("/confirm")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<ModelSpecView>> confirm(
        @PathVariable UUID planId,
        @RequestBody ConfirmCandidateCommand command
    ) {
        WarehousePlanActor actor = requirePlanMaintenance(planId);
        CreateResult result = service.confirm(
            serverTenantId,
            actor == null ? null : actor.ownerId(),
            planId,
            accessContext(actor),
            command
        );
        return ResponseEntity.created(URI.create("/api/modeling/model-specs/" + result.modelSpec().id()))
            .body(ApiResponses.ok(result.modelSpec()));
    }

    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> handleModelSpecError(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details())
        );
    }

    @ExceptionHandler(WarehousePlanException.class)
    public ResponseEntity<ApiResponse<Object>> handleWarehousePlanError(WarehousePlanException exception) {
        HttpStatus status = switch (exception.code()) {
            case "WAREHOUSE_PLAN_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "WAREHOUSE_PLAN_OWNER_FORBIDDEN", "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    private WarehousePlanActor requirePlanRead(UUID planId) {
        WarehousePlanActor actor = actorProvider.currentActor();
        authorizationGuard.requirePlanRead(planService.get(serverTenantId, planId), actor);
        return actor;
    }

    private WarehousePlanActor requirePlanMaintenance(UUID planId) {
        WarehousePlanActor actor = actorProvider.currentActor();
        authorizationGuard.requirePlanMaintenance(planService.get(serverTenantId, planId), actor);
        return actor;
    }

    private AccessContext accessContext(WarehousePlanActor actor) {
        return new AccessContext(
            serverTenantId,
            actor == null ? null : actor.ownerId(),
            actor == null ? null : actor.ownerDepartmentId()
        );
    }
}
