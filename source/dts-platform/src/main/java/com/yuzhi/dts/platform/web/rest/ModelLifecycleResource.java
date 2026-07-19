package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.CompileView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReleaseView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ReviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RunCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TimelineView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextApplicationService.RunView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelLifecycleResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"model-spec:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final ModelLifecycleService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public ModelLifecycleResource(
        ModelLifecycleService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @PutMapping("/{id}/implementation")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ImplementationView> claim(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody ClaimImplementationCommand command
    ) {
        return ApiResponses.ok(service.claim(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/compile")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<CompileView> compile(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody IdempotencyRequest request
    ) {
        return ApiResponses.ok(service.compile(tenantId, actorId(), id, expected(id, ifMatch), request.idempotencyKey()));
    }

    @PostMapping("/{id}/lifecycle/tests")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleEventView> test(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody TestEvidenceCommand command
    ) {
        return ApiResponses.ok(service.recordTest(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/reviews")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleEventView> submitReview(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody ReviewCommand command
    ) {
        return ApiResponses.ok(service.submitReview(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/reviews/approve")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleEventView> approveReview(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody ReviewCommand command
    ) {
        return ApiResponses.ok(service.approveReview(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/publish")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ReleaseView> publish(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody PublishCommand command
    ) {
        return ApiResponses.ok(service.publish(tenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/lifecycle/releases/{releaseId}/retry")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ReleaseView> retry(
        @PathVariable UUID id,
        @PathVariable UUID releaseId
    ) {
        return ApiResponses.ok(service.retryRegistration(tenantId, actorId(), id, releaseId));
    }

    @PostMapping("/{id}/lifecycle/rollback")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleEventView> rollback(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody RollbackCommand command
    ) {
        return ApiResponses.ok(service.rollback(tenantId, actorId(), id, expected(id, ifMatch), command));
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

    private static ModelSpecException invalidEtag() {
        return new ModelSpecException(
            "MODEL_SPEC_IF_MATCH_INVALID",
            "If-Match must identify the current canonical ModelSpec revision",
            ModelSpecException.Kind.BAD_REQUEST
        );
    }

    public record IdempotencyRequest(String idempotencyKey) {}
}
