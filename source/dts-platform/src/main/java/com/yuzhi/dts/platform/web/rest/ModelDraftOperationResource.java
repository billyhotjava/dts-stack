package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelDraftSaveApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelDraftSaveApplicationService.SaveResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Atomic REST boundary for the first save of a manually authored model. */
@RestController
@RequestMapping("/api/modeling/model-specs/draft-operations")
public class ModelDraftOperationResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelDraftSaveApplicationService service;
    private final ModelSpecCreateRequestDecoder createDecoder;
    private final ModelSpecUpdateRequestDecoder updateDecoder;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public ModelDraftOperationResource(
        ModelDraftSaveApplicationService service,
        ModelSpecCreateRequestDecoder createDecoder,
        ModelSpecUpdateRequestDecoder updateDecoder,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.createDecoder = createDecoder;
        this.updateDecoder = updateDecoder;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<SaveResult>> save(@RequestBody DraftOperationRequest request) {
        if (request == null) throw invalidRequest(List.of());
        SaveMode saveMode = SaveMode.parse(request.saveMode());
        if (saveMode == SaveMode.DEFINITION_ONLY && request.implementation() != null) {
            throw invalidRequest(List.of(new FieldIssue(
                "MODEL_DRAFT_OPERATION_IMPLEMENTATION_FORBIDDEN",
                "implementation",
                com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity.ERROR,
                "DEFINITION_ONLY save does not accept an implementation"
            )));
        }
        ModelSpecCreateRequestDecoder.DecodeResult create = createDecoder.decode(request.create());
        if (!create.valid()) throw invalidRequest(create.issues());
        if (DimensionModelCreateRequestDecoder.isInternalIdempotencyKey(create.command().idempotencyKey())) {
            throw invalidRequest(
                List.of(new FieldIssue(
                    "MODEL_SPEC_IDEMPOTENCY_KEY_RESERVED",
                    "create.idempotencyKey",
                    com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity.ERROR,
                    "The idempotency key uses a server-reserved namespace"
                ))
            );
        }
        ModelSpecUpdateRequestDecoder.DecodeResult modelSpec = saveMode == SaveMode.DEFINITION_ONLY
            ? updateDecoder.decodeDefinition(request.modelSpec())
            : updateDecoder.decode(request.modelSpec());
        if (!modelSpec.valid()) throw invalidRequest(modelSpec.issues());

        SaveResult saved = saveMode == SaveMode.DEFINITION_ONLY
            ? service.saveDefinition(tenantId, actorId(), create.command(), modelSpec.command())
            : service.save(
                tenantId,
                actorId(),
                create.command(),
                modelSpec.command(),
                request.implementation() == null ? null : ModelLifecycleResource.decode(request.implementation())
            );
        HttpStatus status = saved.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity
            .status(status)
            .location(URI.create("/api/modeling/model-specs/" + saved.model().id()))
            .eTag(ModelSpecApplicationService.etag(saved.model()))
            .body(ApiResponses.ok(saved));
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
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details())
        );
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ModelSpecException invalidRequest(Object issues) {
        return new ModelSpecException(
            "MODEL_SPEC_REQUEST_INVALID",
            "Model draft operation contains invalid fields",
            ModelSpecException.Kind.UNPROCESSABLE,
            issues
        );
    }

    public record DraftOperationRequest(
        JsonNode create,
        JsonNode modelSpec,
        ModelLifecycleResource.ImplementationWriteRequest implementation,
        String saveMode
    ) {}

    private enum SaveMode {
        DEFINITION_ONLY,
        WITH_IMPLEMENTATION;

        private static SaveMode parse(String value) {
            if (value == null || value.isBlank()) return WITH_IMPLEMENTATION;
            try {
                return SaveMode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw invalidRequest(List.of(new FieldIssue(
                    "MODEL_DRAFT_OPERATION_SAVE_MODE_INVALID",
                    "saveMode",
                    com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity.ERROR,
                    "saveMode must be DEFINITION_ONLY or WITH_IMPLEMENTATION"
                )));
            }
        }
    }
}
