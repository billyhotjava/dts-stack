package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCreateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Strict canonical REST boundary for objectless ModelSpecs. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelSpecResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-spec:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final ModelSpecApplicationService service;
    private final ModelSpecCreateRequestDecoder createDecoder;
    private final ModelSpecUpdateRequestDecoder updateDecoder;
    private final ModelSpecStageGateService stageGates;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelSpecResource(
        ModelSpecApplicationService service,
        ModelSpecCreateRequestDecoder createDecoder,
        ModelSpecUpdateRequestDecoder updateDecoder,
        ModelSpecStageGateService stageGates,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.createDecoder = createDecoder;
        this.updateDecoder = updateDecoder;
        this.stageGates = stageGates;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<ModelSpecView>> create(@RequestBody JsonNode body) {
        ModelSpecCreateRequestDecoder.DecodeResult decoded = createDecoder.decode(body);
        if (!decoded.valid()) throw invalidRequest(decoded.issues());
        CreateResult result = service.create(serverTenantId, actorId(), decoded.command());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity
            .status(status)
            .location(URI.create("/api/modeling/model-specs/" + result.modelSpec().id()))
            .eTag(ModelSpecApplicationService.etag(result.modelSpec()))
            .body(ApiResponses.ok(result.modelSpec()));
    }

    @GetMapping
    public ApiResponse<List<ModelSpecView>> list(
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) UUID domainId,
        @RequestParam(required = false) ModelType modelType,
        @RequestParam(required = false) Layer layer
    ) {
        return ApiResponses.ok(service.list(serverTenantId, planId, domainId, modelType, layer));
    }

    @PostMapping("/naming/validate")
    public ApiResponse<NamingValidationView> validatePhysicalName(@RequestBody NamingValidationRequest request) {
        List<FieldIssue> issues = ModelSpecContract.validatePhysicalName(
            request == null ? null : request.physicalName()
        );
        return ApiResponses.ok(new NamingValidationView(issues.isEmpty(), issues));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModelSpecView>> get(@PathVariable UUID id) {
        ModelSpecView view = service.get(serverTenantId, id);
        return ResponseEntity.ok().eTag(ModelSpecApplicationService.etag(view)).body(ApiResponses.ok(view));
    }

    @GetMapping("/{id}/revisions/{revision}")
    public ResponseEntity<ApiResponse<ModelSpecView>> revision(
        @PathVariable UUID id,
        @PathVariable int revision
    ) {
        ModelSpecView view = service.revision(serverTenantId, new ModelRevisionRef(id, revision));
        return ResponseEntity.ok().eTag(ModelSpecApplicationService.etag(view)).body(ApiResponses.ok(view));
    }

    @GetMapping("/{id}/stage-gates")
    public ApiResponse<List<GateView>> stageGates(@PathVariable UUID id) {
        return ApiResponses.ok(stageGates.evaluateAll(serverTenantId, id));
    }

    @GetMapping("/{id}/dependencies")
    public ApiResponse<ModelSpecApplicationService.DependencyGraph> dependencies(@PathVariable UUID id) {
        return ApiResponses.ok(service.dependencyGraph(serverTenantId, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<ModelSpecView>> update(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody JsonNode body
    ) {
        ExpectedVersion expected = parseExpected(ifMatch);
        if (!id.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "If-Match identifies a different ModelSpec",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        ModelSpecUpdateRequestDecoder.DecodeResult decoded = updateDecoder.decode(body);
        if (!decoded.valid()) throw invalidRequest(decoded.issues());
        ModelSpecView view = service.update(serverTenantId, actorId(), id, expected, decoded.command());
        return ResponseEntity.ok().eTag(ModelSpecApplicationService.etag(view)).body(ApiResponses.ok(view));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Void>> deleteDraft(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        ExpectedVersion expected = parseExpected(ifMatch);
        if (!id.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "If-Match identifies a different ModelSpec",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        service.deleteDraft(serverTenantId, actorId(), id, expected);
        return ResponseEntity.ok(ApiResponses.ok((Void) null));
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

    private ExpectedVersion parseExpected(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "If-Match must be a strong canonical ModelSpec ETag",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        try {
            return new ExpectedVersion(UUID.fromString(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3));
        } catch (IllegalArgumentException exception) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "If-Match must be a strong canonical ModelSpec ETag",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ModelSpecException invalidRequest(Object issues) {
        return new ModelSpecException(
            "MODEL_SPEC_REQUEST_INVALID",
            "ModelSpec request contains invalid fields",
            ModelSpecException.Kind.UNPROCESSABLE,
            issues
        );
    }

    public record NamingValidationRequest(String physicalName) {}

    public record NamingValidationView(boolean valid, List<FieldIssue> issues) {}
}
