package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectArchiveResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Emits allowlisted model-import audit facts independently from HTTP fallback headers. */
@Component
public class ModelSpecImportPreviewAudit {

    static final String INSPECT_ACTION = "MODELING_DBT_IMPORT_INSPECT";
    static final String PREVIEW_ACTION = "MODEL_SPEC_IMPORT_PREVIEW";
    static final String APPLY_ACTION = "MODELING_DBT_IMPORT_APPLY";
    static final String RETRY_ACTION = "MODELING_DBT_IMPORT_RETRY";
    private final AuditService auditService;

    public ModelSpecImportPreviewAudit(AuditService auditService) {
        this.auditService = auditService;
    }

    public void inspectSuccess(InspectArchiveResponse response, long archiveBytes, String requestCorrelationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "correlationId", requestCorrelationId);
        put(payload, "requestCorrelationId", requestCorrelationId);
        payload.put("archiveBytes", Math.max(0, archiveBytes));
        if (response != null && response.modelPackage() != null) {
            payload.put("packageId", response.modelPackage().packageId());
            payload.put("packageChecksum", response.modelPackage().packageChecksum());
            payload.put("modelCount", response.modelPackage().models() == null ? 0 : response.modelPackage().models().size());
            payload.put(
                "technicalNodeCount",
                response.modelPackage().technicalNodes() == null ? 0 : response.modelPackage().technicalNodes().size()
            );
        }
        if (response != null && response.compatibility() != null) {
            var compatibility = response.compatibility();
            payload.put("inspectionCompatibility", compatibility.inspection());
            payload.put("importProjectionCompatibility", compatibility.importProjection());
            payload.put("materializationCompatibility", compatibility.materialization());
            payload.put("compatibilityIssues", compatibility.issues().stream().map(ModelSpecImportPreviewAudit::safeIssue).toList());
        }
        auditService.auditActionStrict(
            INSPECT_ACTION,
            AuditStage.SUCCESS,
            response == null ||
                response.modelPackage() == null ||
                response.modelPackage().packageId() == null ||
                response.modelPackage().packageId().isBlank()
                ? "inspect"
                : response.modelPackage().packageId(),
            payload
        );
    }

    public void rejected(
        String action,
        String code,
        String resourceId,
        String requestCorrelationId,
        UUID runId,
        String packageChecksum
    ) {
        String actionCode = switch (action) {
            case INSPECT_ACTION, PREVIEW_ACTION, APPLY_ACTION, RETRY_ACTION -> action;
            default -> throw new IllegalArgumentException("Unsupported model import audit action");
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code == null || code.isBlank() ? "MODEL_IMPORT_REJECTED" : code);
        put(payload, "correlationId", runId == null ? requestCorrelationId : runId);
        put(payload, "requestCorrelationId", requestCorrelationId);
        put(payload, "runId", runId);
        put(payload, "packageChecksum", packageChecksum);
        auditService.auditActionStrict(
            actionCode,
            AuditStage.FAIL,
            resourceId == null || resourceId.isBlank() ? "model-import" : resourceId,
            Map.copyOf(payload)
        );
    }

    private static Map<String, Object> safeIssue(
        com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CompatibilityIssue issue
    ) {
        if (issue == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        if (issue.code() != null) result.put("code", issue.code());
        if (issue.stage() != null) result.put("stage", issue.stage());
        if (issue.category() != null) result.put("category", issue.category());
        result.put("retryable", issue.retryable());
        if (issue.recoveryAction() != null) result.put("recoveryAction", issue.recoveryAction());
        if (issue.correlationId() != null) result.put("correlationId", issue.correlationId());
        return Map.copyOf(result);
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null && !value.toString().isBlank()) payload.put(key, value);
    }
}
