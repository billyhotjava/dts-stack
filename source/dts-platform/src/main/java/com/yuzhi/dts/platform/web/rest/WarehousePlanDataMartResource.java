package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.DataMartApplicationService;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineCommand;
import com.yuzhi.dts.platform.service.modeling.DataMartContract.PlanBaselineView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/warehouse-plans/{planId}/baseline/data-marts")
public class WarehousePlanDataMartResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final DataMartApplicationService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public WarehousePlanDataMartResource(
        DataMartApplicationService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping
    public ApiResponse<PlanBaselineView> get(@PathVariable UUID planId) {
        return ApiResponses.ok(service.getPlanBaseline(serverTenantId, planId));
    }

    @PutMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<PlanBaselineView> save(
        @PathVariable UUID planId,
        @RequestBody PlanBaselineCommand command
    ) {
        return ApiResponses.ok(
            service.savePlanBaseline(serverTenantId, actorProvider.currentActor().ownerId(), planId, command)
        );
    }

    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> handleModelingError(ModelSpecException exception) {
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
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }
}
