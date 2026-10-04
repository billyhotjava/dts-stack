package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.RetryResult;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.ServingSyncView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Status and controlled retry facade for model-to-Analytics semantic delivery. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class CatalogModelSemanticSyncResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-serving-sync:([0-9a-fA-F-]{36}):([1-9][0-9]*)\\\"$"
    );

    private final CatalogModelSemanticSyncCommandService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public CatalogModelSemanticSyncResource(
        CatalogModelSemanticSyncCommandService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @GetMapping("/{modelSpecId}/serving-sync")
    public ResponseEntity<ApiResponse<ServingSyncView>> get(@PathVariable UUID modelSpecId) {
        ServingSyncView status = service.get(tenantId, modelSpecId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (status.version() > 0) response.eTag(etag(status));
        return response.body(ApiResponses.ok(status));
    }

    @GetMapping("/serving-sync")
    public ApiResponse<List<ServingSyncView>> list(@RequestParam List<UUID> modelSpecIds) {
        return ApiResponses.ok(service.getMany(tenantId, modelSpecIds));
    }

    @PostMapping("/{modelSpecId}/serving-sync/retry")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<RetryResult>> retry(
        @PathVariable UUID modelSpecId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        long expectedVersion = expectedVersion(modelSpecId, ifMatch);
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        RetryResult result = service.retry(
            tenantId,
            actor == null ? null : actor.ownerId(),
            modelSpecId,
            expectedVersion
        );
        return ResponseEntity
            .ok()
            .eTag(etag(result.status()))
            .body(ApiResponses.ok(result));
    }

    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> handle(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }

    private static long expectedVersion(UUID modelSpecId, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw error(
                "MODEL_SEMANTIC_SYNC_IF_MATCH_REQUIRED",
                "A strong semantic sync If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED,
                Map.of("modelSpecId", modelSpecId)
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) throw invalidEtag(modelSpecId);
        try {
            UUID etagModelId = UUID.fromString(matcher.group(1));
            if (!modelSpecId.equals(etagModelId)) throw invalidEtag(modelSpecId);
            return Long.parseLong(matcher.group(2));
        } catch (IllegalArgumentException invalid) {
            throw invalidEtag(modelSpecId);
        }
    }

    private static ModelSpecException invalidEtag(UUID modelSpecId) {
        return error(
            "MODEL_SEMANTIC_SYNC_IF_MATCH_INVALID",
            "If-Match must identify the same semantic sync projection",
            ModelSpecException.Kind.BAD_REQUEST,
            Map.of("modelSpecId", modelSpecId)
        );
    }

    private static ModelSpecException error(String code, String message, ModelSpecException.Kind kind, Object details) {
        return new ModelSpecException(code, message, kind, details);
    }

    private static String etag(ServingSyncView status) {
        return "\"model-serving-sync:" + status.modelSpecId() + ":" + status.version() + "\"";
    }
}
