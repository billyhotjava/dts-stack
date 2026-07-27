package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelBuildIntentService;
import com.yuzhi.dts.platform.service.modeling.ModelBuildIntentService.BuildIntentCommand;
import com.yuzhi.dts.platform.service.modeling.ModelBuildIntentService.BuildIntentResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Strict single-model facade. Technical execution fields are rejected instead of ignored; the
 * service resolves them from the canonical ModelSpec, implementation and server target.
 */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelBuildIntentResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_MAINTAINER)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-spec:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );
    private static final Set<String> BODY_FIELDS = Set.of(
        "planId",
        "environment"
    );

    private final ModelBuildIntentService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelBuildIntentResource(
        ModelBuildIntentService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping("/{modelSpecId}/build-intents")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<BuildIntentResult>> start(
        @PathVariable UUID modelSpecId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false)
        String idempotencyKey,
        @RequestBody(required = false) JsonNode body
    ) {
        ExpectedVersion expected = expectedVersion(modelSpecId, ifMatch);
        BuildIntentBody request = decode(body);
        BuildIntentResult result = service.start(
            serverTenantId,
            actorId(),
            modelSpecId,
            expected,
            new BuildIntentCommand(
                request.planId(),
                request.environment(),
                requiredIdempotencyKey(idempotencyKey)
            )
        );
        HttpStatus status =
            result.build() != null && !result.replayed()
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity
            .status(status)
            .location(
                URI.create(
                    "/api/modeling/plans/" +
                    result.candidate().planId() +
                    "/release-candidates/" +
                    result.candidate().id()
                )
            )
            .eTag(ModelReleaseCandidateApplicationService.etag(result.candidate()))
            .body(ApiResponses.ok(result));
    }

    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> handleCandidateError(
        ModelReleaseCandidateException exception
    ) {
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

    private static BuildIntentBody decode(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("Build intent body must be an object");
        }
        body
            .fieldNames()
            .forEachRemaining(field -> {
                if (!BODY_FIELDS.contains(field)) {
                    throw invalid(
                        "Build intent field is not allowed: " + field
                    );
                }
            });
        String planId = text(body, "planId");
        String environment = text(body, "environment");
        try {
            return new BuildIntentBody(UUID.fromString(planId), environment);
        } catch (IllegalArgumentException invalidUuid) {
            throw invalid("planId must be a UUID");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " is required");
        }
        return value.asText().trim();
    }

    private static ExpectedVersion expectedVersion(
        UUID modelSpecId,
        String value
    ) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_BUILD_INTENT_IF_MATCH_REQUIRED",
                "A strong ModelSpec If-Match precondition is required",
                Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(value.trim());
        if (!matcher.matches()) throw invalid("If-Match is invalid");
        try {
            UUID etagModelId = UUID.fromString(matcher.group(1));
            if (!modelSpecId.equals(etagModelId)) {
                throw invalid("If-Match identifies another ModelSpec");
            }
            return new ExpectedVersion(
                etagModelId,
                Integer.parseInt(matcher.group(2)),
                matcher.group(3)
            );
        } catch (IllegalArgumentException error) {
            throw invalid("If-Match is invalid");
        }
    }

    private static String requiredIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("Idempotency-Key is required");
        }
        String key = value.trim();
        if (key.length() > 128) {
            throw invalid("Idempotency-Key exceeds 128 characters");
        }
        return key;
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor =
            actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ModelReleaseCandidateException invalid(String message) {
        return new ModelReleaseCandidateException(
            "MODEL_BUILD_INTENT_REQUEST_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    private record BuildIntentBody(UUID planId, String environment) {}
}
