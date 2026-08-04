package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.CreateWarehouseLayerCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.WarehouseLayerView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Strict canonical REST boundary for the global warehouse-layer registry. */
@RestController
@RequestMapping("/api/modeling/warehouse-layers")
public class WarehouseLayerResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final WarehouseLayerApplicationService service;
    private final WarehousePlanActorProvider actorProvider;

    public WarehouseLayerResource(WarehouseLayerApplicationService service, WarehousePlanActorProvider actorProvider) {
        this.service = service;
        this.actorProvider = actorProvider;
    }

    @GetMapping
    public ApiResponse<List<WarehouseLayerView>> list() {
        return ApiResponses.ok(service.list());
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<WarehouseLayerView>> create(@RequestBody CreateWarehouseLayerCommand command) {
        WarehouseLayerView view = service.create(actorId(), command);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/modeling/warehouse-layers/" + view.code()))
            .body(ApiResponses.ok(view));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(actorId(), code);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(WarehouseLayerException.class)
    public ResponseEntity<ApiResponse<Object>> handleWarehouseLayerError(WarehouseLayerException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        ApiResponse<Object> response = new ApiResponse<>(
            ResultStatus.ERROR.getCode(),
            exception.getMessage(),
            exception.code(),
            exception.details()
        );
        return ResponseEntity.status(status).body(response);
    }

    private String actorId() {
        WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
