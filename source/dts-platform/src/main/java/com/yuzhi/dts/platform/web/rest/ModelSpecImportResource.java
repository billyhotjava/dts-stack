package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.RetryRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtModelArchiveInspectService.ArchiveInspectionException;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRunResponse;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewService;
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

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate
    ) {
        this(service, requestParser, audit, admissionGate, null, null);
    }

    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService
    ) {
        this(service, requestParser, audit, admissionGate, applyService, null);
    }

    @Autowired
    public ModelSpecImportResource(
        ModelSpecImportPreviewService service,
        ModelSpecImportPreviewRequestParser requestParser,
        ModelSpecImportPreviewAudit audit,
        ModelSpecImportPreviewAdmissionGate admissionGate,
        ModelSpecImportApplyService applyService,
        DbtModelArchiveInspectService archiveInspectService
    ) {
        this.service = service;
        this.requestParser = requestParser;
        this.audit = audit;
        this.admissionGate = admissionGate;
        this.applyService = applyService;
        this.archiveInspectService = archiveInspectService;
    }

    @PostMapping("/dbt/preview")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<PreviewResponse> preview(HttpServletRequest request) {
        try (ModelSpecImportPreviewAdmissionGate.Admission admission = admissionGate.enter()) {
            PreviewRequest parsed = requestParser.parse(request);
            admission.admitPlan(parsed == null || parsed.context() == null ? null : parsed.context().planId());
            PreviewResponse response = service.preview(parsed);
            audit.success(response);
            return ApiResponses.ok(response);
        }
    }

    @PostMapping(value = "/dbt/archive/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelPackage> inspectArchive(@RequestPart("archive") MultipartFile archive) {
        try (ModelSpecImportPreviewAdmissionGate.Admission admission = admissionGate.enter()) {
            return ApiResponses.ok(archiveInspectService.inspect(archive));
        }
    }

    @GetMapping("/{runId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PreviewRunResponse> get(@PathVariable UUID runId) {
        return ApiResponses.ok(service.get(runId));
    }

    @PostMapping("/dbt/apply")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ApplyResponse> apply(HttpServletRequest request) {
        ApplyRequest parsed = requestParser.parseApply(request);
        return ApiResponses.ok(applyService.apply(parsed));
    }

    @PostMapping("/{runId}/retry")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ApplyResponse> retry(@PathVariable UUID runId, HttpServletRequest request) {
        RetryRequest parsed = requestParser.parseRetry(request);
        return ApiResponses.ok(applyService.retry(runId, parsed));
    }

    @GetMapping("/{runId}/apply")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ApplyResponse> latestApply(@PathVariable UUID runId) {
        return ApiResponses.ok(applyService.latest(runId));
    }

    @ExceptionHandler(ModelSpecImportApplyException.class)
    public ResponseEntity<ApiResponse<Object>> handleApplyError(ModelSpecImportApplyException exception) {
        audit.rejected(exception.code());
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
        audit.rejected(exception.code());
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
        audit.rejected(exception.code());
        HttpStatus status = exception.code().endsWith("TOO_LARGE") ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }

    @ExceptionHandler(WarehousePlanException.class)
    public ResponseEntity<ApiResponse<Object>> handleWarehousePlanError(WarehousePlanException exception) {
        audit.rejected(exception.code());
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
        audit.rejected(exception.code());
        return ResponseEntity.status(exception.status()).body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), null)
        );
    }
}
