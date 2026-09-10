package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelingContextInitializationService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DimensionModelApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionModelApplicationService.OperationResult;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for complete atomic dimension ModelSpec operations and recovery. */
@RestController
@RequestMapping("/api/modeling/model-specs/dimension")
public class DimensionModelResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "isAuthenticated()";

    private final DimensionModelApplicationService service;
    private final DimensionModelCreateRequestDecoder decoder;
    private final WarehousePlanActorProvider actorProvider;
    private final ModelingContextInitializationService contexts;
    private final AuditService auditService;
    private final String serverTenantId;

    public DimensionModelResource(
        DimensionModelApplicationService service,
        DimensionModelCreateRequestDecoder decoder,
        WarehousePlanActorProvider actorProvider,
        ModelingContextInitializationService contexts,
        AuditService auditService,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.decoder = decoder;
        this.actorProvider = actorProvider;
        this.contexts = contexts;
        this.auditService = auditService;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<OperationResult>> create(@RequestBody JsonNode body) {
        try {
            OperationResult result = contexts.withContext(serverTenantId, actorProvider.currentActor(),
                java.util.Collections.singletonList(body == null ? null : body.get("modelSpec")), resolved -> {
                    JsonNode normalized = body;
                    if (body != null && body.isObject()) {
                        com.fasterxml.jackson.databind.node.ObjectNode copy = body.deepCopy();
                        copy.set("modelSpec", resolved.getFirst());
                        normalized = copy;
                    }
                    return service.create(serverTenantId, actorId(), decoder.decode(normalized));
                });
            return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(URI.create("/api/modeling/model-specs/" + result.currentModelSpec().id()))
                .cacheControl(CacheControl.noStore())
                .body(ApiResponses.ok(result));
        } catch (ModelSpecException exception) {
            auditService.auditActionStrict(
                "MODELING_DIMENSION_MODEL_CREATE",
                AuditStage.FAIL,
                "dimension-model-request",
                Map.of("errorCode", exception.code(), "errorKind", exception.kind().name())
            );
            throw exception;
        }
    }

    @GetMapping("/operations/{operationId}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<OperationResult>> recover(@PathVariable String operationId) {
        OperationResult result = service.recover(serverTenantId, actorId(), decoder.parseOperationId(operationId));
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponses.ok(result));
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
            .cacheControl(CacheControl.noStore())
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
