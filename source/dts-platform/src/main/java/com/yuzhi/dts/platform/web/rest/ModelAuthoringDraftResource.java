package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CommitAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CommitAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.CreateAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftRequest;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.SaveAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.ValidateAuthoringDraftView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Canonical REST facade shared by visual and code model authoring views. */
@RestController
@RequestMapping("/api/modeling/model-specs/{modelSpecId}")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)")
public class ModelAuthoringDraftResource {

    private final ModelAuthoringDraftService service;
    private final WarehousePlanActorProvider actorProvider;
    private final DbtImplementationDraftRejectionAudit rejectionAudit;
    private final String tenantId;

    public ModelAuthoringDraftResource(
        ModelAuthoringDraftService service,
        WarehousePlanActorProvider actorProvider,
        DbtImplementationDraftRejectionAudit rejectionAudit,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.rejectionAudit = rejectionAudit;
        this.tenantId = tenantId;
    }

    @GetMapping("/authoring-context")
    public ResponseEntity<ApiResponse<AuthoringContextView>> context(
        @PathVariable UUID modelSpecId,
        @RequestParam(required = false) Integer modelRevision,
        @RequestParam(required = false) Integer implementationRevision
    ) {
        AuthoringContextView view = service.context(
            tenantId,
            actorId(),
            modelSpecId,
            modelRevision,
            implementationRevision,
            true
        );
        return ResponseEntity.ok().eTag(ModelSpecApplicationService.etag(view.model())).body(ApiResponses.ok(view));
    }

    @PostMapping("/authoring-drafts")
    public ResponseEntity<ApiResponse<AuthoringDraftView>> create(
        @PathVariable UUID modelSpecId,
        @Valid @RequestBody CreateAuthoringDraftRequest request
    ) {
        AuthoringDraftView created = service.create(tenantId, actorId(), modelSpecId, request);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(created.draft().etag()).body(ApiResponses.ok(created));
    }

    @PutMapping("/authoring-drafts/{draftId}")
    public ResponseEntity<ApiResponse<SaveAuthoringDraftView>> save(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody SaveAuthoringDraftRequest request
    ) {
        SaveAuthoringDraftView saved = service.save(tenantId, actorId(), modelSpecId, draftId, request);
        return ResponseEntity.ok().eTag(saved.etag()).body(ApiResponses.ok(saved));
    }

    @PostMapping("/authoring-drafts/{draftId}/validate")
    public ResponseEntity<ApiResponse<ValidateAuthoringDraftView>> validate(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody ValidateDraftRequest request
    ) {
        ValidateAuthoringDraftView validated = service.validate(
            tenantId,
            actorId(),
            modelSpecId,
            draftId,
            request.expectedEtag()
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (validated.implementationValidation() != null) response.eTag(validated.implementationValidation().etag());
        return response.body(ApiResponses.ok(validated));
    }

    @PostMapping("/authoring-drafts/{draftId}/commit")
    public ResponseEntity<ApiResponse<CommitAuthoringDraftView>> commit(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody CommitAuthoringDraftRequest request
    ) {
        CommitAuthoringDraftView committed = service.commit(tenantId, actorId(), modelSpecId, draftId, request);
        return ResponseEntity.ok().eTag(committed.receipt().etag()).body(ApiResponses.ok(committed));
    }

    @ExceptionHandler(ModelAuthoringException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthoringError(ModelAuthoringException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(DraftException.class)
    public ResponseEntity<ApiResponse<Object>> handleDraftError(DraftException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GONE -> HttpStatus.GONE;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage(), exception.details()));
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
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationError(
        MethodArgumentNotValidException exception,
        HttpServletRequest request,
        HttpServletResponse servletResponse
    ) {
        String correlationId = DbtImplementationDraftCorrelation.install(request, servletResponse);
        rejectionAudit.recordValidationFailure(request, correlationId, exception.getBindingResult().getErrorCount());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(DbtImplementationDraftCorrelation.HEADER, correlationId)
            .body(
                error(
                    "MODEL_AUTHORING_REQUEST_INVALID",
                    "Model authoring request validation failed",
                    Map.of("correlationId", correlationId)
                )
            );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMalformedBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request,
        HttpServletResponse servletResponse
    ) {
        String correlationId = DbtImplementationDraftCorrelation.install(request, servletResponse);
        rejectionAudit.recordMalformedBody(request, correlationId);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(DbtImplementationDraftCorrelation.HEADER, correlationId)
            .body(
                error(
                    "MODEL_AUTHORING_REQUEST_MALFORMED",
                    "Model authoring request body is malformed",
                    Map.of("correlationId", correlationId)
                )
            );
    }

    private static ApiResponse<Object> error(String code, String message, Object details) {
        return new ApiResponse<>(ResultStatus.ERROR.getCode(), message, code, details);
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
