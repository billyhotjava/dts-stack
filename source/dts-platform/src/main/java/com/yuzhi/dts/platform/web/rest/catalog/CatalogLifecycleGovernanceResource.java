package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.ActionView;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.ApprovalResult;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.ConsumeTokenCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.SubmitCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService.TokenConsumption;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleProjectionService;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleProjectionService.LifecycleView;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/lifecycle/governance")
public class CatalogLifecycleGovernanceResource {

    private static final String MAINTAIN =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN')";

    private final CatalogLifecycleControlService controlService;
    private final CatalogLifecycleProjectionService projectionService;

    public CatalogLifecycleGovernanceResource(
        CatalogLifecycleControlService controlService,
        CatalogLifecycleProjectionService projectionService
    ) {
        this.controlService = controlService;
        this.projectionService = projectionService;
    }

    @PostMapping("/actions")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<ActionView> submit(@RequestBody SubmitCommand command) {
        return ApiResponses.ok(controlService.submit(command, actor()));
    }

    @GetMapping("/actions")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<List<ActionView>> list(
        @RequestParam(required = false) UUID datasetId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponses.ok(controlService.list(datasetId, status, limit));
    }

    @GetMapping("/actions/{id}")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<ActionView> get(@PathVariable UUID id) {
        return ApiResponses.ok(controlService.get(id));
    }

    @PostMapping("/actions/{id}/approve")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<ApprovalResult> approve(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionRequest request
    ) {
        return ApiResponses.ok(controlService.approve(id, actor(), request == null ? null : request.notes()));
    }

    @PostMapping("/actions/{id}/reject")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<ActionView> reject(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionRequest request
    ) {
        return ApiResponses.ok(controlService.reject(id, actor(), request == null ? null : request.notes()));
    }

    @PostMapping("/actions/{id}/retry-destruction")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<ActionView> retryDestruction(@PathVariable UUID id) {
        return ApiResponses.ok(controlService.retryPermanentDestruction(id, actor()));
    }

    @PostMapping("/execution-tokens/consume")
    @PreAuthorize(MAINTAIN)
    public ApiResponse<TokenConsumption> consume(@RequestBody ConsumeTokenCommand command) {
        return ApiResponses.ok(controlService.consume(command, actor()));
    }

    @GetMapping("/datasets/{datasetId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<LifecycleView> lifecycle(
        @PathVariable UUID datasetId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(projectionService.project(datasetId, activeDept));
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<ApiResponse<Object>> handleInvalidLifecycleAction(RuntimeException exception) {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    "CATALOG_LIFECYCLE_CONTROL_REJECTED",
                    null
                )
            );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleDenied(AccessDeniedException exception) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    "CATALOG_LIFECYCLE_FORBIDDEN",
                    null
                )
            );
    }

    private String actor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    public record DecisionRequest(String notes) {}
}
