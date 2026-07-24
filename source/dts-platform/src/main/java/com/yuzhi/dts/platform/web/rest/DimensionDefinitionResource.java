package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Strict REST boundary for reusable business dimension definitions. */
@RestController
@RequestMapping("/api/modeling/dimension-definitions")
public class DimensionDefinitionResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"dimension-definition:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final DimensionDefinitionApplicationService service;
    private final ObjectMapper strictObjectMapper;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public DimensionDefinitionResource(
        DimensionDefinitionApplicationService service,
        ObjectMapper objectMapper,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.strictObjectMapper = strictObjectMapper(objectMapper);
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> create(@RequestBody JsonNode body) {
        CreateCommand command = decode(body, DimensionDefinitionContract.CREATE_FIELDS, CreateCommand.class);
        CreateResult result = service.create(serverTenantId, actorId(), command);
        View view = result.dimensionDefinition();
        return ResponseEntity
            .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .location(URI.create("/api/modeling/dimension-definitions/" + view.id()))
            .eTag(DimensionDefinitionApplicationService.etag(view))
            .body(ApiResponses.ok(view));
    }

    @GetMapping
    public ApiResponse<List<View>> list(
        @RequestParam(required = false) UUID domainId,
        @RequestParam(required = false) Status status,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "" + DimensionDefinitionApplicationService.DEFAULT_LIST_LIMIT) int limit
    ) {
        return ApiResponses.ok(service.list(serverTenantId, domainId, status, offset, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID id) {
        View view = service.get(serverTenantId, id);
        return ResponseEntity
            .ok()
            .eTag(DimensionDefinitionApplicationService.etag(view))
            .body(ApiResponses.ok(view));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> update(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody JsonNode body
    ) {
        ExpectedVersion expected = parseExpected(id, ifMatch);
        UpdateCommand command = decode(body, DimensionDefinitionContract.UPDATE_FIELDS, UpdateCommand.class);
        View view = service.update(serverTenantId, actorId(), id, expected, command);
        return response(view);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> confirm(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        View view = service.confirm(serverTenantId, actorId(), id, parseExpected(id, ifMatch));
        return response(view);
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> retire(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        View view = service.retire(serverTenantId, actorId(), id, parseExpected(id, ifMatch));
        return response(view);
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
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
    }

    private ResponseEntity<ApiResponse<View>> response(View view) {
        return ResponseEntity
            .ok()
            .eTag(DimensionDefinitionApplicationService.etag(view))
            .body(ApiResponses.ok(view));
    }

    private <T> T decode(JsonNode body, Set<String> allowedFields, Class<T> type) {
        if (body == null || !body.isObject()) {
            throw invalidRequest(List.of(new FieldIssue("request", "DIMENSION_DEFINITION_REQUEST_OBJECT_REQUIRED", "JSON object is required")));
        }
        List<FieldIssue> issues = new ArrayList<>();
        body
            .fieldNames()
            .forEachRemaining(field -> {
                if (!allowedFields.contains(field)) {
                    issues.add(
                        new FieldIssue(
                            field,
                            "DIMENSION_DEFINITION_FIELD_NOT_ALLOWED",
                            "Field is not allowed at this boundary"
                        )
                    );
                }
            });
        if (!issues.isEmpty()) {
            throw invalidRequest(issues);
        }
        rejectNonIntegralHierarchyOrders(body);
        try {
            return strictObjectMapper.treeToValue(body, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidRequest(
                List.of(
                    new FieldIssue(
                        "request",
                        "DIMENSION_DEFINITION_FIELD_INVALID",
                        "Request contains a malformed value"
                    )
                )
            );
        }
    }

    private static void rejectNonIntegralHierarchyOrders(JsonNode body) {
        JsonNode hierarchies = body.get("hierarchies");
        if (hierarchies == null || !hierarchies.isArray()) {
            return;
        }
        for (int hierarchyIndex = 0; hierarchyIndex < hierarchies.size(); hierarchyIndex++) {
            JsonNode levels = hierarchies.get(hierarchyIndex).get("levels");
            if (levels == null || !levels.isArray()) {
                continue;
            }
            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                JsonNode order = levels.get(levelIndex).get("order");
                if (order != null && (!order.isIntegralNumber() || !order.canConvertToInt())) {
                    throw invalidRequest(
                        List.of(
                            new FieldIssue(
                                "hierarchies[" + hierarchyIndex + "].levels[" + levelIndex + "].order",
                                "DIMENSION_DEFINITION_FIELD_INVALID",
                                "Hierarchy level order must be an integer"
                            )
                        )
                    );
                }
            }
        }
    }

    private static ObjectMapper strictObjectMapper(ObjectMapper objectMapper) {
        ObjectMapper strict = objectMapper
            .copy()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS
            )
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strict
            .coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strict
            .coercionConfigFor(LogicalType.Integer)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        return strict;
    }

    private ExpectedVersion parseExpected(UUID id, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ModelSpecException(
                "DIMENSION_DEFINITION_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) {
            throw invalidIfMatch();
        }
        try {
            UUID etagId = UUID.fromString(matcher.group(1));
            if (!id.equals(etagId)) {
                throw invalidIfMatch();
            }
            return new ExpectedVersion(etagId, Integer.parseInt(matcher.group(2)), matcher.group(3));
        } catch (IllegalArgumentException exception) {
            throw invalidIfMatch();
        }
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ModelSpecException invalidRequest(Object details) {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_REQUEST_INVALID",
            "Dimension definition request contains invalid fields",
            ModelSpecException.Kind.UNPROCESSABLE,
            details
        );
    }

    private static ModelSpecException invalidIfMatch() {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_IF_MATCH_INVALID",
            "If-Match must be a strong canonical dimension definition ETag for this object",
            ModelSpecException.Kind.BAD_REQUEST
        );
    }
}
