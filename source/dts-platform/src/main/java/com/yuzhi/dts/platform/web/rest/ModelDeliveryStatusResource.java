package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelDeliveryStatusQueryService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only delivery aggregation; command endpoints remain owned by their existing resources. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelDeliveryStatusResource {
    private final ModelDeliveryStatusQueryService service;
    private final WarehousePlanActorProvider actors;
    private final String tenantId;
    public ModelDeliveryStatusResource(ModelDeliveryStatusQueryService service, WarehousePlanActorProvider actors, @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId) { this.service = service; this.actors = actors; this.tenantId = tenantId; }
    @GetMapping("/{id}/delivery-status")
    public ApiResponse<ModelDeliveryStatusQueryService.DeliveryStatusView> get(@PathVariable UUID id, @RequestParam(required = false) String environment, @RequestParam(required = false) UUID candidateId) {
        WarehousePlanActorProvider.WarehousePlanActor actor = actors.currentActor();
        return ApiResponses.ok(service.get(tenantId, actor == null ? null : actor.ownerId(), id, environment, candidateId));
    }
    public record WorkbenchSummaryRequest(List<UUID> modelSpecIds, String environment) {}

    /** F15 K1: one bounded call per workbench page; reads no quality or serving state. */
    @PostMapping("/workbench-summaries")
    public ApiResponse<List<ModelDeliveryStatusQueryService.WorkbenchSummaryView>> workbenchSummaries(@RequestBody WorkbenchSummaryRequest request) {
        WarehousePlanActorProvider.WarehousePlanActor actor = actors.currentActor();
        List<UUID> ids = request == null || request.modelSpecIds() == null ? List.of() : request.modelSpecIds();
        return ApiResponses.ok(service.workbenchSummaries(tenantId, actor == null ? null : actor.ownerId(), ids, request == null ? null : request.environment()));
    }
    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> candidateError(ModelReleaseCandidateException exception) {
        HttpStatus status = switch (exception.kind()) { case BAD_REQUEST -> HttpStatus.BAD_REQUEST; case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY; case FORBIDDEN -> HttpStatus.FORBIDDEN; case NOT_FOUND -> HttpStatus.NOT_FOUND; case CONFLICT -> HttpStatus.CONFLICT; case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED; };
        return ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }
    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> modelError(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) { case BAD_REQUEST -> HttpStatus.BAD_REQUEST; case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY; case FORBIDDEN -> HttpStatus.FORBIDDEN; case NOT_FOUND -> HttpStatus.NOT_FOUND; case CONFLICT -> HttpStatus.CONFLICT; case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED; };
        return ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }
}
