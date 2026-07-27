package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService;
import com.yuzhi.dts.platform.service.modeling.ModelPublicationIntentService.PublicationIntentResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
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

/** Strict single-model publish intent boundary that stops before human review and publication. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelPublicationIntentResource {

    private static final String MODEL_MAINTAINER_EXPRESSION =
        "hasAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_MAINTAINER)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"release-candidate:([0-9a-fA-F-]{36}):([1-9][0-9]*)\\\"$"
    );
    private static final Set<String> BODY_FIELDS = Set.of("candidateId", "reason");

    private final ModelPublicationIntentService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelPublicationIntentResource(
        ModelPublicationIntentService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping("/{modelSpecId}/publish-intents")
    @PreAuthorize(MODEL_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<PublicationIntentResult>> start(
        @PathVariable UUID modelSpecId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) JsonNode body
    ) {
        PublishIntentBody request = decode(body);
        int expectedVersion = expectedVersion(request.candidateId(), ifMatch);
        PublicationIntentResult result = service.start(
            serverTenantId,
            actorId(),
            modelSpecId,
            request.candidateId(),
            expectedVersion,
            requiredIdempotencyKey(idempotencyKey),
            request.reason()
        );
        HttpStatus status =
            result.candidateStatus() == DeliveryStatus.QUALITY_RUNNING &&
            !result.replayed()
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity
            .status(status)
            .eTag(
                "\"release-candidate:" +
                result.candidateId() +
                ":" +
                result.candidateVersion() +
                "\""
            )
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

    private static PublishIntentBody decode(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("Publish intent body must be an object");
        }
        body
            .fieldNames()
            .forEachRemaining(field -> {
                if (!BODY_FIELDS.contains(field)) {
                    throw invalid("Publish intent field is not allowed: " + field);
                }
            });
        String candidateId = text(body, "candidateId");
        String reason = text(body, "reason");
        try {
            return new PublishIntentBody(UUID.fromString(candidateId), reason);
        } catch (IllegalArgumentException error) {
            throw invalid("candidateId must be a UUID");
        }
    }

    private static int expectedVersion(UUID candidateId, String value) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_PUBLICATION_INTENT_IF_MATCH_REQUIRED",
                "A strong release-candidate If-Match precondition is required",
                Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(value.trim());
        if (!matcher.matches()) throw invalid("If-Match is invalid");
        try {
            UUID etagCandidateId = UUID.fromString(matcher.group(1));
            if (!candidateId.equals(etagCandidateId)) {
                throw invalid("If-Match identifies another release candidate");
            }
            return Integer.parseInt(matcher.group(2));
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

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " is required");
        }
        return value.asText().trim();
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ModelReleaseCandidateException invalid(String message) {
        return new ModelReleaseCandidateException(
            "MODEL_PUBLICATION_INTENT_REQUEST_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    private record PublishIntentBody(UUID candidateId, String reason) {}
}
