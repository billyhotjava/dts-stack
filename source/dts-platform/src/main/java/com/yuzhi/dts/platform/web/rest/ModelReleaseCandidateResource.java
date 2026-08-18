package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateApplicationService;
import com.yuzhi.dts.platform.service.modeling.CandidateGovernanceQualityRerunService;
import com.yuzhi.dts.platform.service.modeling.CandidateGovernanceQualityRerunService.RerunResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ModelMaterializationStatusView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.MaterializationAttemptView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidatePreflightService.BatchPreflightView;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

/** Strict plan-scoped REST boundary for release-candidate workbench reads and commands. */
@RestController
@RequestMapping("/api/modeling/plans/{planId}/release-candidates")
public class ModelReleaseCandidateResource {

    private static final String RELEASE_DUTY_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_DUTIES)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"release-candidate:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}):([1-9][0-9]*)\\\"$"
    );

    private final ModelReleaseCandidateApplicationService service;
    private final CandidateGovernanceQualityRerunService governanceQualityReruns;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelReleaseCandidateResource(
        ModelReleaseCandidateApplicationService service,
        CandidateGovernanceQualityRerunService governanceQualityReruns,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.governanceQualityReruns = governanceQualityReruns;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping("/workspace")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<WorkbenchView>> workspace(@PathVariable UUID planId) {
        WorkbenchView view = service.workspace(serverTenantId, actorId(), planId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (view.etag() != null) response.eTag(view.etag());
        return response.body(ApiResponses.ok(view));
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CandidateView>> get(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId
    ) {
        CandidateView candidate = service.get(serverTenantId, actorId(), planId, candidateId);
        return ResponseEntity
            .ok()
            .eTag(ModelReleaseCandidateApplicationService.etag(candidate))
            .body(ApiResponses.ok(candidate));
    }

    @GetMapping("/materializations")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<List<ModelMaterializationStatusView>>> materializationStatuses(
        @PathVariable UUID planId,
        @RequestParam List<UUID> modelSpecIds
    ) {
        return ResponseEntity.ok(
            ApiResponses.ok(service.materializationStatuses(serverTenantId, actorId(), planId, modelSpecIds))
        );
    }

    @GetMapping("/{candidateId}/materialization-attempts")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<List<MaterializationAttemptView>>> materializationHistory(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId
    ) {
        return ResponseEntity.ok(
            ApiResponses.ok(service.materializationHistory(serverTenantId, actorId(), planId, candidateId))
        );
    }

    @PostMapping("/preflight")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<BatchPreflightView>> preflight(
        @PathVariable UUID planId,
        @RequestBody(required = false) CreateRequest request
    ) {
        CreateRequest body = requiredRequest(request, "preflight request");
        return ResponseEntity.ok(
            ApiResponses.ok(
                service.preflight(
                    serverTenantId,
                    actorId(),
                    planId,
                    new CreateCandidateCommand(
                        planId,
                        body.environment(),
                        body.entries(),
                        "preflight",
                        body.reason()
                    )
                )
            )
        );
    }

    @PostMapping
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> create(
        @PathVariable UUID planId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CreateRequest request
    ) {
        String key = requiredIdempotencyKey(idempotencyKey);
        CreateRequest body = requiredRequest(request, "create request");
        CreateCandidateCommand command = new CreateCandidateCommand(
            planId,
            body.environment(),
            body.entries(),
            key,
            body.reason()
        );
        CommandResult result = body.materializationPlanChecksum() == null
            ? service.create(serverTenantId, actorId(), planId, command)
            : service.create(
                serverTenantId,
                actorId(),
                planId,
                command,
                body.materializationPlanChecksum(),
                body.strategy()
            );
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity
            .status(status)
            .location(candidateLocation(planId, result.candidate().id()))
            .eTag(ModelReleaseCandidateApplicationService.etag(result.candidate()))
            .body(ApiResponses.ok(result));
    }

    @PutMapping("/{candidateId}/scope")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> replaceScope(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ScopeRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        ScopeRequest body = requiredRequest(request, "scope request");
        return write(
            service.replaceScope(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                new ReplaceScopeCommand(expectedVersion, body.entries(), key, body.reason())
            )
        );
    }

    @PostMapping("/{candidateId}/lock")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> lock(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        ReasonRequest body = requiredRequest(request, "lock request");
        return write(
            service.lock(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                key,
                body.reason()
            )
        );
    }

    @PostMapping("/{candidateId}/retry")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> retry(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        ReasonRequest body = requiredRequest(request, "retry request");
        return write(
            service.retry(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                key,
                body.reason()
            )
        );
    }

    @PostMapping("/{candidateId}/refresh")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> refreshDrift(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        ReasonRequest body = requiredRequest(request, "refresh request");
        return write(
            service.refreshDrift(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                key,
                body.reason()
            )
        );
    }

    @PostMapping("/{candidateId}/cancel")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> cancel(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        ReasonRequest body = requiredRequest(request, "cancel request");
        return write(
            service.cancel(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                key,
                body.reason()
            )
        );
    }

    @PostMapping("/{candidateId}/replacement")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> createReplacement(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CreateRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        CreateRequest body = requiredRequest(request, "replacement request");
        CreateCandidateCommand command = new CreateCandidateCommand(
            planId,
            body.environment(),
            body.entries(),
            key,
            body.reason()
        );
        CommandResult result = body.materializationPlanChecksum() == null
            ? service.createReplacement(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                command
            )
            : service.createReplacement(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                command,
                body.materializationPlanChecksum(),
                body.strategy()
            );
        return ResponseEntity
            .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .location(candidateLocation(planId, result.candidate().id()))
            .eTag(ModelReleaseCandidateApplicationService.etag(result.candidate()))
            .body(ApiResponses.ok(result));
    }

    @PostMapping("/{candidateId}/rematerialize")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> rematerialize(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) CreateRequest request
    ) {
        int expectedVersion = expectedVersion(candidateId, ifMatch);
        String key = requiredIdempotencyKey(idempotencyKey);
        CreateRequest body = requiredRequest(request, "rematerialization request");
        CreateCandidateCommand command = new CreateCandidateCommand(
            planId,
            body.environment(),
            body.entries(),
            key,
            body.reason()
        );
        CommandResult result = body.materializationPlanChecksum() == null
            ? service.rematerialize(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                command
            )
            : service.rematerialize(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion,
                command,
                body.materializationPlanChecksum(),
                body.strategy()
            );
        return ResponseEntity
            .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .location(candidateLocation(planId, result.candidate().id()))
            .eTag(ModelReleaseCandidateApplicationService.etag(result.candidate()))
            .body(ApiResponses.ok(result));
    }

    @PostMapping("/{candidateId}/quality")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> runQuality(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.runQuality(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "quality request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/governance-quality/runs")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<RerunResult>> rerunGovernanceQuality(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        RerunResult result = governanceQualityReruns.rerun(
            serverTenantId,
            actorId(),
            planId,
            candidateId,
            expectedVersion(candidateId, ifMatch),
            requiredIdempotencyKey(idempotencyKey),
            activeDept
        );
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponses.ok(result));
    }

    @PostMapping("/{candidateId}/reviews")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> submitReview(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.submitReview(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "review request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/reviews/approve")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> approve(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.approve(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "approval request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/reviews/reject")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> reject(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.reject(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "rejection request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/publish")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> publish(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.publish(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "publish request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/publication/retry")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> retryPublication(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.retryPublication(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "publication retry request").reason()
            )
        );
    }

    @PostMapping("/{candidateId}/rollback")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<CommandResult>> rollback(
        @PathVariable UUID planId,
        @PathVariable UUID candidateId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) ReasonRequest request
    ) {
        return write(
            service.rollback(
                serverTenantId,
                actorId(),
                planId,
                candidateId,
                expectedVersion(candidateId, ifMatch),
                requiredIdempotencyKey(idempotencyKey),
                requiredRequest(request, "rollback request").reason()
            )
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

    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequest(Exception exception) {
        return ResponseEntity
            .badRequest()
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    "Release candidate request is invalid",
                    "MODEL_RELEASE_CANDIDATE_REQUEST_INVALID",
                    null
                )
            );
    }

    private ResponseEntity<ApiResponse<CommandResult>> write(CommandResult result) {
        return ResponseEntity
            .ok()
            .eTag(ModelReleaseCandidateApplicationService.etag(result.candidate()))
            .body(ApiResponses.ok(result));
    }

    private int expectedVersion(UUID candidateId, String value) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(value.trim());
        if (!matcher.matches()) throw invalidEtag();
        try {
            UUID etagCandidateId = UUID.fromString(matcher.group(1));
            int version = Integer.parseInt(matcher.group(2));
            if (!candidateId.equals(etagCandidateId)) throw invalidEtag();
            return version;
        } catch (IllegalArgumentException exception) {
            throw invalidEtag();
        }
    }

    private String requiredIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_KEY_REQUIRED",
                "Idempotency-Key is required",
                Kind.BAD_REQUEST
            );
        }
        String key = value.trim();
        if (key.length() > 128) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_KEY_INVALID",
                "Idempotency-Key exceeds 128 characters",
                Kind.BAD_REQUEST
            );
        }
        return key;
    }

    private ModelReleaseCandidateException invalidEtag() {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_CANDIDATE_IF_MATCH_INVALID",
            "If-Match must identify the request candidate and a positive version",
            Kind.BAD_REQUEST
        );
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }

    private static URI candidateLocation(UUID planId, UUID candidateId) {
        return URI.create(
            "/api/modeling/plans/" +
            planId +
            "/release-candidates/" +
            candidateId
        );
    }

    private static <T> T requiredRequest(T request, String name) {
        if (request == null) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CANDIDATE_REQUEST_REQUIRED",
                name + " is required",
                Kind.BAD_REQUEST
            );
        }
        return request;
    }

    public record CreateRequest(
        String environment,
        List<ScopeEntryCommand> entries,
        String reason,
        String materializationPlanChecksum,
        Strategy strategy
    ) {}

    public record ScopeRequest(List<ScopeEntryCommand> entries, String reason) {}

    public record ReasonRequest(String reason) {}
}
