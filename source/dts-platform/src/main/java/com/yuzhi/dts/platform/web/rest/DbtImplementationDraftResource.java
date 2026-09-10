package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CreateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SaveFilesView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidateDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidationView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST boundary for isolated, static-only advanced dbt implementation drafts. */
@RestController
@RequestMapping("/api/modeling/model-specs/{modelSpecId}/dbt-drafts")
@PreAuthorize("isAuthenticated()")
public class DbtImplementationDraftResource {

    private final DbtImplementationDraftService service;
    private final WarehousePlanActorProvider actorProvider;
    private final DbtImplementationDraftRejectionAudit rejectionAudit;
    private final String tenantId;

    public DbtImplementationDraftResource(
        DbtImplementationDraftService service,
        WarehousePlanActorProvider actorProvider,
        DbtImplementationDraftRejectionAudit rejectionAudit,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.rejectionAudit = rejectionAudit;
        this.tenantId = tenantId;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DraftView>> create(
        @PathVariable UUID modelSpecId,
        @Valid @RequestBody CreateDraftRequest request
    ) {
        DraftView created = service.create(tenantId, actorId(), modelSpecId, request);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(created.etag()).body(ApiResponses.ok(created));
    }

    @PutMapping("/{draftId}/files")
    public ResponseEntity<ApiResponse<SaveFilesView>> saveFiles(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody SaveFilesRequest request
    ) {
        SaveFilesView saved = service.saveFiles(tenantId, actorId(), modelSpecId, draftId, request);
        return ResponseEntity.ok().eTag(saved.etag()).body(ApiResponses.ok(saved));
    }

    @PostMapping("/{draftId}/validate")
    public ResponseEntity<ApiResponse<ValidationView>> validate(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody ValidateDraftRequest request
    ) {
        ValidationView validated = service.validate(tenantId, actorId(), modelSpecId, draftId, request);
        return ResponseEntity.ok().eTag(validated.etag()).body(ApiResponses.ok(validated));
    }

    @PostMapping("/{draftId}/commit")
    public ResponseEntity<ApiResponse<CommitView>> commit(
        @PathVariable UUID modelSpecId,
        @PathVariable UUID draftId,
        @Valid @RequestBody CommitDraftRequest request
    ) {
        CommitView committed = service.commit(tenantId, actorId(), modelSpecId, draftId, request);
        return ResponseEntity.ok().eTag(committed.etag()).body(ApiResponses.ok(committed));
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
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        Object correlationId = exception.details().get("correlationId");
        if (correlationId != null) response.header("X-Correlation-Id", correlationId.toString());
        return response
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
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
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    "Advanced dbt draft request validation failed",
                    "DBT_DRAFT_REQUEST_INVALID",
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
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    "Advanced dbt draft request body is malformed",
                    "DBT_DRAFT_REQUEST_MALFORMED",
                    Map.of("correlationId", correlationId)
                )
            );
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

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
