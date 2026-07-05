package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.BusinessObjectDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.BusinessObjectRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.DimensionDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.DimensionRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.GeneratedArtifactDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.GenerateArtifactsResult;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.MetricDerivationValidationDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.MetricDerivationValidationRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.MetricDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.MetricRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelBindingDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelBindingRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelPreviewResult;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelReviewLogDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelRunDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelRunRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ModelRunStatusRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ObjectTableMappingDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ObjectTableMappingSaveRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.PublishArtifactsResult;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.RegisterBiDatasetResult;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.RegisterLineageResult;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.ReviewActionRequest;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.SubjectDomainDto;
import com.yuzhi.dts.platform.service.modeling.SemanticModelingService.SubjectDomainRequest;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semantic")
public class SemanticModelingResource {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SemanticModelingResource.class);

    private static final String SEMANTIC_VALIDATION_FAILED = "SEMANTIC_VALIDATION_FAILED";

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final SemanticModelingService service;
    private final AuditService audit;
    private final PlatformEventOutboxService eventOutbox;

    public SemanticModelingResource(
        SemanticModelingService service,
        AuditService audit,
        PlatformEventOutboxService eventOutbox
    ) {
        this.service = service;
        this.audit = audit;
        this.eventOutbox = eventOutbox;
    }

    @GetMapping("/subject-domains")
    @Transactional(readOnly = true)
    public ApiResponse<List<SubjectDomainDto>> listSubjectDomains() {
        List<SubjectDomainDto> data = service.listSubjectDomains();
        audit.auditAction("SEMANTIC_SUBJECT_DOMAIN_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @GetMapping("/workbench")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> workbenchOverview() {
        Map<String, Object> data = service.workbenchOverview();
        audit.auditAction("SEMANTIC_WORKBENCH_VIEW", AuditStage.SUCCESS, "semantic-workbench", Map.of("summary", "查看语义建模工作台"));
        return ApiResponses.ok(data);
    }

    @GetMapping("/menu-diagnostics")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> menuDiagnostics() {
        List<Map<String, Object>> data = service.semanticMenuDiagnostics();
        audit.auditAction("SEMANTIC_MENU_DIAGNOSTICS_VIEW", AuditStage.SUCCESS, "semantic-menu-diagnostics", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/subject-domains")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SubjectDomainDto> createSubjectDomain(@RequestBody SubjectDomainRequest request) {
        SubjectDomainDto dto = service.createSubjectDomain(request);
        audit.auditAction("SEMANTIC_SUBJECT_DOMAIN_CREATE", AuditStage.SUCCESS, dto.id().toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/subject-domains/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SubjectDomainDto> updateSubjectDomain(@PathVariable UUID id, @RequestBody SubjectDomainRequest request) {
        SubjectDomainDto dto = service.updateSubjectDomain(id, request);
        audit.auditAction("SEMANTIC_SUBJECT_DOMAIN_UPDATE", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @GetMapping("/business-objects")
    @Transactional(readOnly = true)
    public ApiResponse<List<BusinessObjectDto>> listBusinessObjects(@RequestParam(required = false) UUID domainId) {
        List<BusinessObjectDto> data = service.listBusinessObjects(domainId);
        audit.auditAction("SEMANTIC_BUSINESS_OBJECT_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/business-objects")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<BusinessObjectDto> createBusinessObject(@RequestBody BusinessObjectRequest request) {
        BusinessObjectDto dto = service.createBusinessObject(request);
        audit.auditAction("SEMANTIC_BUSINESS_OBJECT_CREATE", AuditStage.SUCCESS, dto.id().toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/business-objects/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<BusinessObjectDto> updateBusinessObject(@PathVariable UUID id, @RequestBody BusinessObjectRequest request) {
        BusinessObjectDto dto = service.updateBusinessObject(id, request);
        audit.auditAction("SEMANTIC_BUSINESS_OBJECT_UPDATE", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @GetMapping("/business-objects/{id}/table-mappings")
    @Transactional(readOnly = true)
    public ApiResponse<List<ObjectTableMappingDto>> listObjectTableMappings(@PathVariable UUID id) {
        List<ObjectTableMappingDto> data = service.listObjectTableMappings(id);
        audit.auditAction("SEMANTIC_OBJECT_TABLE_MAPPING_LIST", AuditStage.SUCCESS, id.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PutMapping("/business-objects/{id}/table-mappings")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<List<ObjectTableMappingDto>> saveObjectTableMappings(@PathVariable UUID id, @RequestBody ObjectTableMappingSaveRequest request) {
        List<ObjectTableMappingDto> data = service.saveObjectTableMappings(id, request);
        audit.auditAction("SEMANTIC_OBJECT_TABLE_MAPPING_SAVE", AuditStage.SUCCESS, id.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @GetMapping("/dimensions")
    @Transactional(readOnly = true)
    public ApiResponse<List<DimensionDto>> listDimensions(@RequestParam(required = false) UUID objectId) {
        List<DimensionDto> data = service.listDimensions(objectId);
        audit.auditAction("SEMANTIC_DIMENSION_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/dimensions")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> createDimension(@RequestBody DimensionRequest request) {
        DimensionDto dto = service.createDimension(request);
        audit.auditAction("SEMANTIC_DIMENSION_CREATE", AuditStage.SUCCESS, dto.id().toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/dimensions/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> updateDimension(@PathVariable UUID id, @RequestBody DimensionRequest request) {
        DimensionDto dto = service.updateDimension(id, request);
        audit.auditAction("SEMANTIC_DIMENSION_UPDATE", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @GetMapping("/metrics")
    @Transactional(readOnly = true)
    public ApiResponse<List<MetricDto>> listMetrics(@RequestParam(required = false) UUID objectId) {
        List<MetricDto> data = service.listMetrics(objectId);
        audit.auditAction("SEMANTIC_METRIC_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/metrics")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetricDto> createMetric(@RequestBody MetricRequest request) {
        MetricDto dto = service.createMetric(request);
        audit.auditAction("SEMANTIC_METRIC_CREATE", AuditStage.SUCCESS, dto.id().toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/metrics/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetricDto> updateMetric(@PathVariable UUID id, @RequestBody MetricRequest request) {
        MetricDto dto = service.updateMetric(id, request);
        audit.auditAction("SEMANTIC_METRIC_UPDATE", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PostMapping("/metrics/derivation/validate")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetricDerivationValidationDto> validateMetricDerivation(@RequestBody MetricDerivationValidationRequest request) {
        MetricDerivationValidationDto result = service.validateMetricDerivation(request);
        String target = request == null || request.targetMetricId() == null ? "unknown" : request.targetMetricId().toString();
        audit.auditAction("SEMANTIC_METRIC_DERIVATION_VALIDATE", AuditStage.SUCCESS, target, Map.of("valid", result.valid(), "issues", result.issues().size()));
        return ApiResponses.ok(result);
    }

    @GetMapping("/models")
    @Transactional(readOnly = true)
    public ApiResponse<List<ModelDto>> listModels(@RequestParam(required = false) String type) {
        List<ModelDto> data = service.listModels(type);
        audit.auditAction("SEMANTIC_MODEL_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/models")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelDto> createModel(@RequestBody ModelRequest request) {
        ModelDto dto = service.createModel(request);
        audit.auditAction("SEMANTIC_MODEL_CREATE", AuditStage.SUCCESS, dto.id().toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/models/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelDto> updateModel(@PathVariable UUID id, @RequestBody ModelRequest request) {
        ModelDto dto = service.updateModel(id, request);
        audit.auditAction("SEMANTIC_MODEL_UPDATE", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @GetMapping("/models/{id}/bindings")
    @Transactional(readOnly = true)
    public ApiResponse<ModelBindingDto> getModelBindings(@PathVariable UUID id) {
        ModelBindingDto data = service.getModelBindings(id);
        audit.auditAction("SEMANTIC_MODEL_BINDING_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("dimensions", data.dimensionIds().size(), "metrics", data.metricIds().size()));
        return ApiResponses.ok(data);
    }

    @PutMapping("/models/{id}/bindings")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelBindingDto> saveModelBindings(@PathVariable UUID id, @RequestBody ModelBindingRequest request) {
        ModelBindingDto data = service.saveModelBindings(id, request);
        audit.auditAction("SEMANTIC_MODEL_BINDING_SAVE", AuditStage.SUCCESS, id.toString(), Map.of("dimensions", data.dimensionIds().size(), "metrics", data.metricIds().size()));
        return ApiResponses.ok(data);
    }

    @GetMapping("/generated-artifacts")
    @Transactional(readOnly = true)
    public ApiResponse<List<GeneratedArtifactDto>> listGeneratedArtifacts(@RequestParam(required = false) UUID modelId) {
        List<GeneratedArtifactDto> data = service.listGeneratedArtifacts(modelId);
        audit.auditAction("SEMANTIC_GENERATED_ARTIFACT_LIST", AuditStage.SUCCESS, "list", Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/models/{id}/generate-artifacts")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<GenerateArtifactsResult> generateArtifacts(@PathVariable UUID id) {
        GenerateArtifactsResult result = service.generateArtifacts(id);
        audit.auditAction("SEMANTIC_MODEL_GENERATE_ARTIFACTS", AuditStage.SUCCESS, id.toString(), Map.of("count", result.artifacts().size()));
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/preview-data")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelPreviewResult> previewData(@PathVariable UUID id, @RequestParam(required = false) Integer limit) {
        ModelPreviewResult result = service.previewModel(id, limit);
        audit.auditAction("SEMANTIC_MODEL_PREVIEW_DATA", AuditStage.SUCCESS, id.toString(), Map.of("success", result.success(), "rows", result.rowCount()));
        return ApiResponses.ok(result);
    }

    @GetMapping("/models/{id}/runs")
    @Transactional(readOnly = true)
    public ApiResponse<List<ModelRunDto>> listModelRuns(@PathVariable UUID id) {
        List<ModelRunDto> data = service.listModelRuns(id);
        audit.auditAction("SEMANTIC_MODEL_RUN_LIST", AuditStage.SUCCESS, id.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/models/{id}/runs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelRunDto> triggerModelRun(
        @PathVariable UUID id,
        @RequestBody(required = false) ModelRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ModelRunDto result = service.triggerModelRun(id, request, currentActor(), activeDept);
        audit.auditAction("SEMANTIC_MODEL_RUN_TRIGGER", AuditStage.SUCCESS, id.toString(), Map.of("runId", result.id().toString(), "status", result.status()));
        publishSemanticModelEvent(
            "METRIC.SEMANTIC_MODEL.RUN_TRIGGERED",
            "EXECUTE",
            result.status(),
            id,
            null,
            eventPayload("runId", result.id().toString(), "status", result.status(), "selector", result.selector()),
            "SEMANTIC_MODEL_RUN_TRIGGER"
        );
        return ApiResponses.ok(result);
    }

    @PutMapping("/models/{id}/runs/{runId}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelRunDto> updateModelRun(
        @PathVariable UUID id,
        @PathVariable UUID runId,
        @RequestBody ModelRunStatusRequest request
    ) {
        ModelRunDto result = service.updateModelRun(id, runId, request);
        audit.auditAction("SEMANTIC_MODEL_RUN_UPDATE", AuditStage.SUCCESS, runId.toString(), Map.of("modelId", id.toString(), "status", result.status()));
        publishSemanticModelEvent(
            "METRIC.SEMANTIC_MODEL.RUN_UPDATED",
            "UPDATE",
            result.status(),
            id,
            null,
            eventPayload("runId", runId.toString(), "status", result.status()),
            "SEMANTIC_MODEL_RUN_UPDATE"
        );
        return ApiResponses.ok(result);
    }

    @GetMapping("/models/{id}/review-logs")
    @Transactional(readOnly = true)
    public ApiResponse<List<ModelReviewLogDto>> listReviewLogs(@PathVariable UUID id) {
        List<ModelReviewLogDto> data = service.listModelReviewLogs(id);
        audit.auditAction("SEMANTIC_MODEL_REVIEW_LOG_LIST", AuditStage.SUCCESS, id.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/models/{id}/submit-review")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelDto> submitReview(@PathVariable UUID id, @RequestBody(required = false) ReviewActionRequest request) {
        ModelDto result = service.submitModelReview(id, request, currentActor());
        audit.auditAction("SEMANTIC_MODEL_SUBMIT_REVIEW", AuditStage.SUCCESS, id.toString(), Map.of("reviewStatus", result.reviewStatus()));
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/approve-review")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelDto> approveReview(@PathVariable UUID id, @RequestBody(required = false) ReviewActionRequest request) {
        ModelDto result = service.approveModelReview(id, request, currentActor());
        audit.auditAction("SEMANTIC_MODEL_APPROVE_REVIEW", AuditStage.SUCCESS, id.toString(), Map.of("reviewStatus", result.reviewStatus()));
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/reject-review")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelDto> rejectReview(@PathVariable UUID id, @RequestBody ReviewActionRequest request) {
        ModelDto result = service.rejectModelReview(id, request, currentActor());
        audit.auditAction("SEMANTIC_MODEL_REJECT_REVIEW", AuditStage.SUCCESS, id.toString(), Map.of("reviewStatus", result.reviewStatus()));
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/publish-dbt")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<PublishArtifactsResult> publishDbt(@PathVariable UUID id) {
        PublishArtifactsResult result = service.publishArtifacts(id);
        audit.auditAction("SEMANTIC_MODEL_PUBLISH_DBT", AuditStage.SUCCESS, id.toString(), Map.of("count", result.publishedPaths().size()));
        publishSemanticModelEvent(
            "METRIC.SEMANTIC_MODEL.PUBLISHED_DBT",
            "PUBLISH",
            "SUCCESS",
            id,
            null,
            Map.of("count", result.publishedPaths().size(), "paths", result.publishedPaths()),
            "SEMANTIC_MODEL_PUBLISH_DBT"
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/register-bi-dataset")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<RegisterBiDatasetResult> registerBiDataset(@PathVariable UUID id) {
        RegisterBiDatasetResult result = service.registerBiDataset(id);
        audit.auditAction("SEMANTIC_MODEL_REGISTER_BI_DATASET", AuditStage.SUCCESS, id.toString(), Map.of("datasetId", result.datasetId().toString()));
        publishSemanticModelEvent(
            "METRIC.SEMANTIC_MODEL.REGISTERED_BI_DATASET",
            "PUBLISH",
            "SUCCESS",
            id,
            result.datasetName(),
            eventPayload("datasetId", result.datasetId().toString(), "datasetName", result.datasetName(), "versionNo", result.versionNo()),
            "SEMANTIC_MODEL_REGISTER_BI_DATASET"
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/models/{id}/register-lineage")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<RegisterLineageResult> registerLineage(@PathVariable UUID id) {
        RegisterLineageResult result = service.registerLineage(id);
        audit.auditAction("SEMANTIC_MODEL_REGISTER_LINEAGE", AuditStage.SUCCESS, id.toString(), Map.of("lineageId", result.lineageId().toString()));
        publishSemanticModelEvent(
            "METRIC.SEMANTIC_MODEL.REGISTERED_LINEAGE",
            "PUBLISH",
            "SUCCESS",
            id,
            null,
            eventPayload(
                "lineageId",
                result.lineageId().toString(),
                "upstreamDatasetId",
                result.upstreamDatasetId().toString(),
                "downstreamDatasetId",
                result.downstreamDatasetId().toString()
            ),
            "SEMANTIC_MODEL_REGISTER_LINEAGE"
        );
        return ApiResponses.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleSemanticValidationFailure(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        // F3-T03: 受控建模违规码收口——unsafe_expression（受控 DSL）→ 422；
        // invalid_layer/grain_mismatch/standard_code_required（ELT 分层闸）→ 400 并透出具体码；
        // 其余校验异常保持 400 + SEMANTIC_VALIDATION_FAILED（现状）。
        String code = controlledViolationCode(message);
        HttpStatus status = "unsafe_expression".equals(code) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST;
        String responseCode = code != null ? code : SEMANTIC_VALIDATION_FAILED;
        LOG.debug("Semantic modeling validation failed [{}]: {}", responseCode, message);
        return ResponseEntity.status(status).body(ApiResponses.error(responseCode, message));
    }

    /** 解析受控建模违规码（来自 ControlledMetricDslCompiler / EltLayerGate 抛出的 "code: message" 前缀）。 */
    static String controlledViolationCode(String message) {
        if (message == null) {
            return null;
        }
        int idx = message.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        return switch (message.substring(0, idx).trim()) {
            case "unsafe_expression", "invalid_layer", "grain_mismatch", "standard_code_required" -> message.substring(0, idx).trim();
            default -> null;
        };
    }

    private Map<String, Object> eventPayload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (values == null) {
            return payload;
        }
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            if (key != null) {
                payload.put(String.valueOf(key), values[i + 1]);
            }
        }
        return payload;
    }

    private void publishSemanticModelEvent(
        String eventType,
        String action,
        String status,
        UUID modelId,
        String aggregateName,
        Map<String, Object> payload,
        String auditActionCode
    ) {
        try {
            eventOutbox.publishInternal(
                new PlatformEventRequest(
                    null,
                    eventType,
                    "METRICS",
                    "dts-platform",
                    "SEMANTIC_MODEL",
                    modelId == null ? null : modelId.toString(),
                    aggregateName,
                    action,
                    "INFO",
                    normalizeEventStatus(status),
                    Instant.now(),
                    currentActor(),
                    null,
                    null,
                    auditActionCode,
                    null,
                    payload == null ? Map.of() : new LinkedHashMap<>(payload)
                )
            );
        } catch (RuntimeException ex) {
            LOG.warn("Failed to publish semantic model event {} for {}: {}", eventType, modelId, ex.getMessage());
        }
    }

    private String normalizeEventStatus(String status) {
        if (status == null || status.isBlank()) {
            return "SUCCESS";
        }
        String upper = status.trim().toUpperCase();
        if ("SUCCEEDED".equals(upper) || "COMPLETED".equals(upper)) {
            return "SUCCESS";
        }
        if ("ERROR".equals(upper) || "TIMEOUT".equals(upper)) {
            return "FAILED";
        }
        return upper;
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
