package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelAssetRegistrationInboxService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modeling/model-specs/registration-tasks")
public class ModelAssetRegistrationInboxResource {
    private final ModelAssetRegistrationInboxService service;
    private final WarehousePlanActorProvider actors;
    private final String tenant;
    public ModelAssetRegistrationInboxResource(ModelAssetRegistrationInboxService service, WarehousePlanActorProvider actors,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) {
        this.service = service; this.actors = actors; this.tenant = tenant;
    }
    @GetMapping
    public ApiResponse<ModelAssetRegistrationInboxService.Page> list(@RequestParam(defaultValue = "0") int offset) {
        return ApiResponses.ok(service.list(tenant, actors.currentActor().ownerId(), offset));
    }
    public record RetryRequest(int buildVersion) {}
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable UUID id, @RequestBody RetryRequest command) {
        service.retry(tenant, actors.currentActor().ownerId(), id, command.buildVersion());
        return ApiResponses.ok(null);
    }
    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> error(ModelReleaseCandidateException error) {
        return ResponseEntity.status(error.kind() == ModelReleaseCandidateException.Kind.FORBIDDEN ? 403 : 409)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), error.getMessage(), error.code(), error.details()));
    }
}
