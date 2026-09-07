package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelIngestionTargetService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ModelIngestionTargetResource {
    private final ModelIngestionTargetService service;
    private final WarehousePlanActorProvider actors;
    private final String tenant;
    public ModelIngestionTargetResource(ModelIngestionTargetService service, WarehousePlanActorProvider actors,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) {
        this.service = service; this.actors = actors; this.tenant = tenant;
    }
    @GetMapping("/modeling/model-specs/{id}/ingestion-target")
    public ApiResponse<ModelIngestionTargetService.Target> get(@PathVariable UUID id, @RequestParam String environment) {
        var actor = actors.currentActor();
        return ApiResponses.ok(service.resolveForUser(tenant, actor == null ? null : actor.ownerId(), id, environment));
    }
    @PostMapping("/internal/modeling/ingestion-targets/validate")
    @PreAuthorize("hasAuthority('ROLE_SERVICE_INTERNAL')")
    public ApiResponse<ModelIngestionTargetService.Target> validate(@RequestBody ModelIngestionTargetService.Target target) {
        return ApiResponses.ok(service.validateForExecution(tenant, target));
    }
    @ExceptionHandler(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Object>> error(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException ex) {
        var status = switch (ex.kind()) {
            case BAD_REQUEST -> org.springframework.http.HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> org.springframework.http.HttpStatus.FORBIDDEN;
            case NOT_FOUND -> org.springframework.http.HttpStatus.NOT_FOUND;
            case UNPROCESSABLE -> org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
            case PRECONDITION_REQUIRED -> org.springframework.http.HttpStatus.PRECONDITION_REQUIRED;
            case CONFLICT -> org.springframework.http.HttpStatus.CONFLICT;
        };
        return org.springframework.http.ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), ex.getMessage(), ex.code(), ex.details()));
    }

}
