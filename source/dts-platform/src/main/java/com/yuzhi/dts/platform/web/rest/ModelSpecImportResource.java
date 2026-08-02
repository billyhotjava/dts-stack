package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.RetryRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService.ArchiveInspectionException;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectArchiveResponse;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtCompatibilityEvaluator;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec.InspectionProofException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRunResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewService;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardUndoService;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST boundary for recoverable, preview-only dbt model-package imports. */
@RestController
@RequestMapping("/api/modeling/model-spec-imports")
public class ModelSpecImportResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelSpecImportPreviewService service;
    private final ModelSpecImportPreviewRequestParser requestParser;
    private final ModelSpecImportPreviewAudit audit;
    private final ModelSpecImportPreviewAdmissionGate admissionGate;
    private final ModelSpecImportApplyService applyService;
    private final DbtModelArchiveInspectService archiveInspectService;
    private final ModelSpecImportInspectionProofCodec inspectionProofCodec;
    private final DbtCompatibilityEvaluator compatibilityEvaluator;
    private final ModelSpecImportForwardUndoService forwardUndoService;

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate
    ) {
        this(service, requestParser, audit, admissionGate, null, null, null, null, null);
    }

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService
    ) {
        this(service, requestParser, audit, admissionGate, applyService, null, null, null, null);
    }

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService,
        DbtModelArchiveInspectService archiveInspectService
    ) {
        this(service, requestParser, audit, admissionGate, applyService, archiveInspectService, null, null, null);
    }

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService,
        DbtModelArchiveInspectService archiveInspectService,
        ModelSpecImportInspectionProofCodec inspectionProofCodec,
        DbtCompatibilityEvaluator compatibilityEvaluator
    ) {
        this(
            service,
            requestParser,
            audit,
            admissionGate,
            applyService,
            archiveInspectService,
            inspectionProofCodec,
            compatibilityEvaluator,
            null
        );
    }

    @Autowired
    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService,
        DbtModelArchiveInspectService archiveInspectService,
        ModelSpecImportInspectionProofCodec inspectionProofCodec,
        DbtCompatibilityEvaluator compatibilityEvaluator,
        ModelSpecImportForwardUndoService forwardUndoService
    ) {
        this.service = service;
        this.requestParser = requestParser;
        this.audit = audit;
        this.admissionGate = admissionGate;
        this.applyService = applyService;
        this.archiveInspectService = archiveInspectService;
        this.inspectionProofCodec = inspectionProofCodec;
        this.compatibilityEvaluator = compatibilityEvaluator;
        this.forwardUndoService = forwardUndoService;
    }

    @PostMapping("/dbt/preview")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<PreviewResponse> preview(HttpServletRequest request) {
        String requestCorrelationId = DbtImplementationDraftCorrelation.currentOrCreate();
        String packageChecksum = null;
        PreviewResponse response;
        try (ModelSpecImportPreviewAdmissionGate.Admission admission = admissionGate.enter()) {
            PreviewRequest parsed = requestParser.parse(request);
            packageChecksum = packageChecksum(parsed);
            requireInspectionProofCodec().verify(parsed == null ? null : parsed.inspectionProof(), parsed == null ? null : parsed.modelPackage());
            admission.admitPlan(parsed == null || parsed.context() == null ? null : parsed.context().planId());
            response = service.preview(parsed);
        } catch (RuntimeException exception) {
            audit.rejected(
                ModelSpecImportPreviewAudit.PREVIEW_ACTION,
                errorCode(exception, "MODEL_IMPORT_PREVIEW_FAILED"),
                "preview",
                requestCorrelationId,
                null,
                packageChecksum
            );
            throw exception;
        }
        return ApiResponses.ok(response);
    }

    @PostMapping(value = "/dbt/archive/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<InspectArchiveResponse> inspectArchive(@RequestPart("archive") MultipartFile archive) {
        String requestCorrelationId = DbtImplementationDraftCorrelation.currentOrCreate();
        String packageChecksum = null;
        InspectArchiveResponse response;
        try (ModelSpecImportPreviewAdmissionGate.Admission admission = admissionGate.enter()) {
            var modelPackage = archiveInspectService.inspect(archive);
            packageChecksum = modelPackage == null ? null : modelPackage.packageChecksum();
            var compatibility = requireCompatibilityEvaluator().evaluate(modelPackage);
            if (compatibility.importProjection() == ImportProjectionCompatibility.BLOCKED) {
                throw new ModelSpecImportPreviewException(
                    "MODEL_IMPORT_PROJECTION_BLOCKED",
                    "The inspected dbt archive cannot be projected into an importable model",
                    com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.Kind.UNPROCESSABLE,
                    compatibility.issues()
                );
            }
            var issuedProof = requireInspectionProofCodec().issue(modelPackage);
            response = new InspectArchiveResponse(
                modelPackage,
                compatibility,
                issuedProof.inspectionProof(),
                issuedProof.proofExpiresAt()
            );
        } catch (RuntimeException exception) {
            audit.rejected(
                ModelSpecImportPreviewAudit.INSPECT_ACTION,
                errorCode(exception, "MODEL_IMPORT_INSPECT_FAILED"),
                "inspect",
                requestCorrelationId,
                null,
                packageChecksum
            );
            throw exception;
        }
        audit.inspectSuccess(response, archive == null ? 0 : archive.getSize(), requestCorrelationId);
        return ApiResponses.ok(response);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PreviewRunResponse> get(@PathVariable UUID runId) {
        return ApiResponses.ok(service.get(runId));
    }

    @PostMapping("/dbt/apply")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ApplyResponse> apply(HttpServletRequest request) {
        String requestCorrelationId = DbtImplementationDraftCorrelation.currentOrCreate();
        UUID runId = null;
        ApplyResponse response;
        try {
            ApplyRequest parsed = requestParser.parseApply(request);
            runId = parsed == null ? null : parsed.runId();
            response = applyService.apply(parsed);
        } catch (RuntimeException exception) {
            audit.rejected(
                ModelSpecImportPreviewAudit.APPLY_ACTION,
                errorCode(exception, "MODEL_IMPORT_APPLY_FAILED"),
                runId == null ? "apply" : runId.toString(),
                requestCorrelationId,
                runId,
                null
            );
            throw exception;
        }
        return ApiResponses.ok(response);
    }

    @PostMapping("/dbt/forward-undo")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ApplyResponse> forwardUndo(@RequestBody ForwardUndoRequest request) {
        return ApiResponses.ok(requireForwardUndoService().forwardUndo(request));
    }

    @PostMapping("/{runId}/retry")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ApplyResponse> retry(@PathVariable UUID runId, HttpServletRequest request) {
        String requestCorrelationId = DbtImplementationDraftCorrelation.currentOrCreate();
        ApplyResponse response;
        try {
            RetryRequest parsed = requestParser.parseRetry(request);
            response = applyService.retry(runId, parsed);
        } catch (RuntimeException exception) {
            audit.rejected(
                ModelSpecImportPreviewAudit.RETRY_ACTION,
                errorCode(exception, "MODEL_IMPORT_RETRY_FAILED"),
                runId == null ? "retry" : runId.toString(),
                requestCorrelationId,
                runId,
                null
            );
            throw exception;
        }
        return ApiResponses.ok(response);
    }

    @GetMapping("/{runId}/apply")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ApplyResponse> latestApply(@PathVariable UUID runId) {
        return ApiResponses.ok(applyService.latest(runId));
    }

    @ExceptionHandler(ModelSpecImportApplyException.class)
    public ResponseEntity<ApiResponse<Object>> handleApplyError(ModelSpecImportApplyException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GONE -> HttpStatus.GONE;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details())
        );
    }

    @ExceptionHandler(ModelSpecImportPreviewException.class)
    public ResponseEntity<ApiResponse<Object>> handlePreviewError(ModelSpecImportPreviewException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GONE -> HttpStatus.GONE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details())
        );
    }

    @ExceptionHandler(ArchiveInspectionException.class)
    public ResponseEntity<ApiResponse<Object>> handleArchiveInspectionError(ArchiveInspectionException exception) {
        HttpStatus status = exception.code().endsWith("TOO_LARGE") ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    @ExceptionHandler(InspectionProofException.class)
    public ResponseEntity<ApiResponse<Object>> handleInspectionProofError(InspectionProofException exception) {
        HttpStatus status = "DBT_IMPORT_INSPECTION_PROOF_EXPIRED".equals(exception.code())
            ? HttpStatus.GONE
            : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    @ExceptionHandler(WarehousePlanException.class)
    public ResponseEntity<ApiResponse<Object>> handleWarehousePlanError(WarehousePlanException exception) {
        HttpStatus status = switch (exception.code()) {
            case "WAREHOUSE_PLAN_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "WAREHOUSE_PLAN_OWNER_FORBIDDEN", "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    @ExceptionHandler(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleRequestLimit(ModelSpecImportPreviewRequestParser.RequestLimitException exception) {
        return ResponseEntity.status(exception.status()).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    private ModelSpecImportInspectionProofCodec requireInspectionProofCodec() {
        if (inspectionProofCodec == null) {
            throw new IllegalStateException("Model import inspection proof codec is not configured");
        }
        return inspectionProofCodec;
    }

    private DbtCompatibilityEvaluator requireCompatibilityEvaluator() {
        if (compatibilityEvaluator == null) {
            throw new IllegalStateException("dbt compatibility evaluator is not configured");
        }
        return compatibilityEvaluator;
    }

    private ModelSpecImportForwardUndoService requireForwardUndoService() {
        if (forwardUndoService == null) {
            throw new IllegalStateException("Model import forward undo service is not configured");
        }
        return forwardUndoService;
    }

    private static String errorCode(RuntimeException exception, String fallback) {
        if (exception instanceof ModelSpecImportApplyException failure) return failure.code();
        if (exception instanceof ModelSpecImportPreviewException failure) return failure.code();
        if (exception instanceof ArchiveInspectionException failure) return failure.code();
        if (exception instanceof InspectionProofException failure) return failure.code();
        if (exception instanceof WarehousePlanException failure) return failure.code();
        if (exception instanceof ModelSpecImportPreviewRequestParser.RequestLimitException failure) return failure.code();
        return fallback;
    }

    private static String packageChecksum(PreviewRequest request) {
        if (request == null || request.modelPackage() == null) return null;
        String checksum = request.modelPackage().path("packageChecksum").asText(null);
        return checksum == null || !checksum.matches("^[0-9a-f]{64}$") ? null : checksum;
    }
}
