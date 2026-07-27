package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReleaseView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RunCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.LegacyModelLifecycleCandidateAdapter;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationValidationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationMigrationBatch;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ImplementationMigrationRollback;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextApplicationService.RunView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelLifecycleResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final String RELEASE_DUTY_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_DUTIES)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-spec:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );
    private static final Pattern IMPLEMENTATION_ETAG = Pattern.compile(
        "^\\\"model-implementation:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final ModelLifecycleService service;
    private final LegacyModelLifecycleCandidateAdapter candidateCompatibility;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public ModelLifecycleResource(
        ModelLifecycleService service,
        LegacyModelLifecycleCandidateAdapter candidateCompatibility,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.candidateCompatibility = candidateCompatibility;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @PutMapping("/{id}/implementation")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationView> claim(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody ClaimImplementationCommand command
    ) {
        return ApiResponses.ok(service.claim(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), command));
    }

    @PutMapping("/{id}/implementation/inputs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationView> saveImplementation(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody ImplementationWriteRequest request
    ) {
        SaveImplementationCommand command = decode(request);
        return ApiResponses.ok(
            service.saveImplementation(
                tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch),
                request.projectKey(), request.dbtUniqueId(), command
            )
        );
    }

    @PostMapping("/{id}/implementation/inputs/validate")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationValidationView> validateImplementation(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody ImplementationWriteRequest request
    ) {
        return ApiResponses.ok(service.validateImplementation(tenantId, actorId(), id, expected(id, ifMatch), decode(request)));
    }

    @PostMapping("/implementation-migrations/dry-run")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationMigrationBatch> previewImplementationMigrations(
        @RequestBody(required = false) ImplementationMigrationScopeRequest request
    ) {
        return ApiResponses.ok(
            service.previewImplementationMigrations(
                tenantId,
                request == null ? List.of() : request.modelSpecIds()
            )
        );
    }

    @PostMapping("/implementation-migrations/apply")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationMigrationBatch> applyImplementationMigrations(
        @RequestBody ImplementationMigrationApplyRequest request
    ) {
        return ApiResponses.ok(
            service.applyImplementationMigrations(
                tenantId,
                actorId(),
                request.modelSpecIds(),
                request.previewChecksum()
            )
        );
    }

    @PostMapping("/implementation-migrations/rollback")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationMigrationRollback> rollbackImplementationMigration(
        @RequestBody ImplementationMigrationRollbackRequest request
    ) {
        return ApiResponses.ok(service.rollbackImplementationMigration(request.previewChecksum()));
    }

    @PostMapping("/{id}/implementation/convert-to-designer-generated")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationView> convertToDesignerGenerated(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody ImplementationWriteRequest request
    ) {
        SaveImplementationCommand command = decode(request);
        return ApiResponses.ok(
            service.convertToDesignerGenerated(
                tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch),
                request.projectKey(), request.dbtUniqueId(), command
            )
        );
    }

    @PostMapping("/{id}/lifecycle/compile")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<CompileView> compile(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody IdempotencyRequest request
    ) {
        return ApiResponses.ok(service.compile(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), request.idempotencyKey()));
    }

    @PostMapping("/{id}/lifecycle/tests")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleEventView> test(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody TestEvidenceCommand command
    ) {
        return ApiResponses.ok(service.recordTest(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), command));
    }

    @PostMapping("/{id}/lifecycle/reviews")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ApiResponse<LifecycleEventView> submitReview(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody ReviewCommand command
    ) {
        return ApiResponses.ok(candidateCompatibility.submitReview(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), command));
    }

    @PostMapping("/{id}/lifecycle/reviews/approve")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ApiResponse<LifecycleEventView> approveReview(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody ReviewCommand command
    ) {
        return ApiResponses.ok(candidateCompatibility.approveReview(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), command));
    }

    @PostMapping("/{id}/lifecycle/publish")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ApiResponse<ReleaseView> publish(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "If-Match-Implementation", required = false) String implementationIfMatch,
        @RequestBody PublishCommand command
    ) {
        return ApiResponses.ok(candidateCompatibility.publish(tenantId, actorId(), id, expected(id, ifMatch), expectedImplementation(id, implementationIfMatch), command));
    }

    @PostMapping("/{id}/lifecycle/releases/{releaseId}/retry")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ApiResponse<ReleaseView> retry(
        @PathVariable UUID id,
        @PathVariable UUID releaseId
    ) {
        return ApiResponses.ok(candidateCompatibility.retryRegistration(tenantId, actorId(), id, releaseId));
    }

    @PostMapping("/{id}/lifecycle/rollback")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ApiResponse<LifecycleEventView> rollback(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody RollbackCommand command
    ) {
        return ApiResponses.ok(candidateCompatibility.rollback(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/runs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<RunView> run(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody RunCommand command
    ) {
        return ApiResponses.ok(service.run(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @GetMapping("/{id}/lifecycle")
    public ApiResponse<TimelineView> timeline(@PathVariable UUID id) {
        return ApiResponses.ok(service.timeline(tenantId, id));
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

    @ExceptionHandler(ModelReleaseCandidateException.class)
    public ResponseEntity<ApiResponse<Object>> handleCandidateError(ModelReleaseCandidateException exception) {
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

    private ExpectedVersion expected(UUID id, String value) {
        if (value == null || value.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(value.trim());
        if (!matcher.matches()) throw invalidEtag();
        try {
            ExpectedVersion expected = new ExpectedVersion(
                UUID.fromString(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3)
            );
            if (!id.equals(expected.modelSpecId())) throw invalidEtag();
            return expected;
        } catch (IllegalArgumentException exception) {
            throw invalidEtag();
        }
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static ExpectedImplementationVersion expectedImplementation(UUID id, String value) {
        if (value == null || value.isBlank()) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_IF_MATCH_REQUIRED",
                "If-Match-Implementation is required; use * only when no implementation exists",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if ("*".equals(value.trim())) return new ExpectedImplementationVersion(id, 0, null);
        Matcher matcher = IMPLEMENTATION_ETAG.matcher(value.trim());
        if (!matcher.matches()) throw invalidImplementationEtag();
        try {
            ExpectedImplementationVersion expected = new ExpectedImplementationVersion(
                UUID.fromString(matcher.group(1)), Integer.parseInt(matcher.group(2)), matcher.group(3)
            );
            if (!id.equals(expected.modelSpecId())) throw invalidImplementationEtag();
            return expected;
        } catch (IllegalArgumentException exception) {
            throw invalidImplementationEtag();
        }
    }

    private static SaveImplementationCommand decode(ImplementationWriteRequest request) {
        if (request == null || request.inputMode() == null || request.inputMode().isBlank() || request.inputs() == null || request.inputs().isEmpty()) {
            throw invalidInput("MODEL_IMPLEMENTATION_INPUT_REQUIRED");
        }
        try {
            InputMode mode = InputMode.valueOf(request.inputMode().trim().toUpperCase());
            ImplementationMode ownership = request.ownership() == null
                ? null
                : ImplementationMode.valueOf(request.ownership().trim().toUpperCase());
            List<ImplementationInput> inputs = request.inputs().stream().map(input -> decodeInput(mode, input)).toList();
            return new SaveImplementationCommand(
                mode,
                inputs,
                request.fieldMappings() == null ? List.of() : request.fieldMappings(),
                request.settings() == null ? Map.of() : request.settings(),
                ownership,
                request.materialization(),
                request.idempotencyKey()
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidInput("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        }
    }

    private static ImplementationInput decodeInput(InputMode mode, ImplementationInputRequest input) {
        if (input == null) throw invalidInput("MODEL_IMPLEMENTATION_INPUT_REQUIRED");
        return switch (mode) {
            case PHYSICAL_ASSET -> new PhysicalAssetInput(input.sourceBindingId(), input.resolvedVersion());
            case UPSTREAM_MODEL -> new UpstreamModelInput(
                input.modelSpecId(),
                input.revision() == null ? 0 : input.revision(),
                input.checksum(),
                input.implementationRevision() == null ? 0 : input.implementationRevision(),
                input.implementationChecksum(),
                input.dbtUniqueId()
            );
            case GENERATED -> new GeneratedInput(input.generatorType(), input.config() == null ? Map.of() : input.config());
        };
    }

    private static ModelSpecException invalidInput(String code) {
        return new ModelSpecException(code, "Implementation input payload is invalid", ModelSpecException.Kind.UNPROCESSABLE);
    }

    private static ModelSpecException invalidEtag() {
        return new ModelSpecException(
            "MODEL_SPEC_IF_MATCH_INVALID",
            "If-Match must identify the current canonical ModelSpec revision",
            ModelSpecException.Kind.BAD_REQUEST
        );
    }

    private static ModelSpecException invalidImplementationEtag() {
        return new ModelSpecException(
            "MODEL_IMPLEMENTATION_IF_MATCH_INVALID",
            "If-Match-Implementation must identify the current implementation revision",
            ModelSpecException.Kind.BAD_REQUEST
        );
    }

    public record IdempotencyRequest(String idempotencyKey) {}

    public record ImplementationWriteRequest(
        String projectKey,
        String dbtUniqueId,
        String inputMode,
        List<ImplementationInputRequest> inputs,
        List<FieldMapping> fieldMappings,
        Map<String, Object> settings,
        String ownership,
        String materialization,
        String idempotencyKey
    ) {}

    public record ImplementationMigrationScopeRequest(List<UUID> modelSpecIds) {
        public ImplementationMigrationScopeRequest {
            modelSpecIds = modelSpecIds == null ? List.of() : List.copyOf(modelSpecIds);
        }

        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object ignored) {
            throw invalidMigrationCommand(fieldName);
        }
    }

    public record ImplementationMigrationApplyRequest(List<UUID> modelSpecIds, String previewChecksum) {
        public ImplementationMigrationApplyRequest {
            modelSpecIds = modelSpecIds == null ? List.of() : List.copyOf(modelSpecIds);
        }

        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object ignored) {
            throw invalidMigrationCommand(fieldName);
        }
    }

    public record ImplementationMigrationRollbackRequest(String previewChecksum) {
        @JsonAnySetter
        public void rejectUnknownField(String fieldName, Object ignored) {
            throw invalidMigrationCommand(fieldName);
        }
    }

    private static ModelSpecException invalidMigrationCommand(String fieldName) {
        return new ModelSpecException(
            "MODEL_IMPLEMENTATION_MIGRATION_COMMAND_INVALID",
            "Unsupported implementation migration field: " + fieldName,
            ModelSpecException.Kind.BAD_REQUEST
        );
    }

    public record ImplementationInputRequest(
        UUID sourceBindingId,
        String resolvedVersion,
        UUID modelSpecId,
        Integer revision,
        String checksum,
        Integer implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        String generatorType,
        Map<String, Object> config
    ) {}
}
