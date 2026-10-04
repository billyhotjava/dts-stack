package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecMetricReferenceService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecMetricReferenceService.BindMetricReferenceCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Strong-CAS boundary for binding published indicator versions to published metric models. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelSpecMetricReferenceResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-spec:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final ModelSpecMetricReferenceService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public ModelSpecMetricReferenceResource(
        ModelSpecMetricReferenceService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @PutMapping("/{id}/metric-refs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<ModelSpecView>> bind(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody BindMetricReferenceCommand command
    ) {
        ExpectedVersion expected = parseExpected(ifMatch);
        if (!id.equals(expected.modelSpecId())) throw invalidEtag();
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        ModelSpecView result = service.bind(tenantId, actor == null ? null : actor.ownerId(), id, expected, command);
        return ResponseEntity.ok().eTag(ModelSpecApplicationService.etag(result)).body(ApiResponses.ok(result));
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

    private static ExpectedVersion parseExpected(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) throw invalidEtag();
        try {
            return new ExpectedVersion(UUID.fromString(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3));
        } catch (IllegalArgumentException exception) {
            throw invalidEtag();
        }
    }

    private static ModelSpecException invalidEtag() {
        return new ModelSpecException(
            "MODEL_SPEC_IF_MATCH_INVALID",
            "If-Match must identify the same canonical ModelSpec",
            ModelSpecException.Kind.BAD_REQUEST
        );
    }
}
