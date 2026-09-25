package com.yuzhi.dts.platform.web.rest;
import com.yuzhi.dts.platform.service.modeling.ModelDataRegistrationService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelDataRegistrationResource {
    private final ModelDataRegistrationService service;
    private final WarehousePlanActorProvider actors;
    private final String tenant;
    public ModelDataRegistrationResource(ModelDataRegistrationService service, WarehousePlanActorProvider actors,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) { this.service=service; this.actors=actors; this.tenant=tenant; }
    @PostMapping("/{id}/data-registration")
    public ApiResponse<List<UUID>> register(@PathVariable UUID id, @RequestBody ModelDataRegistrationService.Command command) {
        var actor=actors.currentActor();
        return ApiResponses.ok(service.register(tenant, actor == null ? null : actor.ownerId(), id, command));
    }
    @GetMapping("/{id}/data-registration")
    public ApiResponse<com.yuzhi.dts.platform.service.modeling.CandidateQualityAssetRegistrationService.RegistrationStatus> status(
        @PathVariable UUID id, @RequestParam UUID candidateId) {
        return ApiResponses.ok(service.status(tenant, id, candidateId));
    }
    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> error(ModelReleaseCandidateException ex) {
        HttpStatus status = switch(ex.kind()) { case FORBIDDEN -> HttpStatus.FORBIDDEN; case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST; case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED; case CONFLICT -> HttpStatus.CONFLICT; };
        return ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), ex.getMessage(), ex.code(), ex.details()));
    }
}
