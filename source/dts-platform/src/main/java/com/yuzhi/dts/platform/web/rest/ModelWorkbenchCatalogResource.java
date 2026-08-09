package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogPage;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogQuery;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogQueryService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, server-paginated projection used by the model workbench table. */
@RestController
@RequestMapping("/api/modeling/model-specs/workbench")
public class ModelWorkbenchCatalogResource {

    private final ModelWorkbenchCatalogQueryService service;
    private final String serverTenantId;

    public ModelWorkbenchCatalogResource(
        ModelWorkbenchCatalogQueryService service,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping
    public ApiResponse<CatalogPage> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) UUID domainId,
        @RequestParam(required = false) String objectType,
        @RequestParam(required = false) Layer layer,
        @RequestParam(required = false) String status
    ) {
        return ApiResponses.ok(
            service.query(
                serverTenantId,
                new CatalogQuery(page, size, query, planId, domainId, objectType, layer, status)
            )
        );
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
        ApiResponse<Object> response = new ApiResponse<>(
            ResultStatus.ERROR.getCode(),
            exception.getMessage(),
            exception.code(),
            exception.details()
        );
        return ResponseEntity.status(status).body(response);
    }
}
