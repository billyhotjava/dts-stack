package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.PlanExecutionDeploymentService;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionDeploymentService.DeployRequest;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionDeploymentService.Publication;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modeling/plans/{planId}/execution-bindings")
public class PlanExecutionDeploymentResource {
    private final PlanExecutionDeploymentService service;
    private final WarehousePlanActorProvider actors;
    private final String tenant;
    public PlanExecutionDeploymentResource(PlanExecutionDeploymentService service, WarehousePlanActorProvider actors,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) {
        this.service=service; this.actors=actors; this.tenant=tenant;
    }
    @GetMapping("/publications")
    @PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_DUTIES)")
    public ApiResponse<List<Publication>> publications(@PathVariable UUID planId) {
        return ApiResponses.ok(service.publications(tenant, actors.currentActor().ownerId(), planId));
    }
    @PostMapping("/deploy")
    @PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_OPERATORS)")
    public ResponseEntity<Void> deploy(@PathVariable UUID planId, @RequestBody DeployRequest request) {
        service.deploy(tenant, actors.currentActor().ownerId(), planId, request);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/{bindingId}/enable")
    @PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_OPERATORS)")
    public ResponseEntity<Void> enable(@PathVariable UUID planId, @PathVariable UUID bindingId, @RequestBody EnableRequest request) {
        service.enable(tenant, actors.currentActor().ownerId(), planId, bindingId, request.version());
        return ResponseEntity.accepted().build();
    }
    @ExceptionHandler(PlanExecutionException.class)
    public ResponseEntity<Map<String,String>> handle(PlanExecutionException failure) {
        int status = switch (failure.kind()) { case FORBIDDEN -> 403; case NOT_FOUND -> 404; case INVALID -> 400; case CONFLICT -> 409; case UNAVAILABLE -> 503; };
        return ResponseEntity.status(status).body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }
    public record EnableRequest(int version) {}
}
