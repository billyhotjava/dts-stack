package com.yuzhi.dts.platform.service.modeling.imports.preview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RenameMapping;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stable REST and persistence projections for the model-package preview control plane. */
public final class ModelSpecImportPreviewContract {

    public static final String PREVIEW_RULES_VERSION = "model-spec-import-preview/v1";

    private ModelSpecImportPreviewContract() {}

    public enum Action {
        CREATE,
        UPDATE,
        SKIP,
        CONFLICT,
        BLOCKED,
    }

    public enum ConversionMode {
        DESIGNER_GENERATED,
        DBT_BACKED,
        BLOCKED,
    }

    public enum RunStatus {
        PREVIEWED,
        BLOCKED,
        EXPIRED,
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO,
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PreviewRequest(
        @JsonProperty("package") JsonNode modelPackage,
        String inspectionProof,
        PreviewContext context,
        List<String> selectedUniqueIds,
        List<SemanticOverride> semanticOverrides,
        List<RenameMapping> renameMappings
    ) {
        public PreviewRequest {
            selectedUniqueIds = selectedUniqueIds == null ? List.of() : List.copyOf(selectedUniqueIds);
            semanticOverrides = semanticOverrides == null ? List.of() : List.copyOf(semanticOverrides);
            renameMappings = renameMappings == null ? List.of() : List.copyOf(renameMappings);
        }

        /** Compatibility constructor for requests created before explicit rename decisions. */
        public PreviewRequest(
            JsonNode modelPackage,
            String inspectionProof,
            PreviewContext context,
            List<String> selectedUniqueIds,
            List<SemanticOverride> semanticOverrides
        ) {
            this(modelPackage, inspectionProof, context, selectedUniqueIds, semanticOverrides, List.of());
        }

        /** Compatibility constructor for trusted internal callers created before D13. */
        public PreviewRequest(JsonNode modelPackage, PreviewContext context, List<String> selectedUniqueIds) {
            this(modelPackage, null, context, selectedUniqueIds, List.of(), List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PreviewContext(
        UUID planId,
        Map<String, String> domainMappings,
        Map<String, String> sourceMappings
    ) {}

    /** Target-environment business metadata; technical dbt facts are deliberately absent. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SemanticOverride(
        String modelUniqueId,
        String modelType,
        String layer,
        String businessName,
        String businessDefinition,
        SemanticGrainOverride grain,
        Map<String, String> fieldRoles,
        List<String> businessKeys,
        List<SemanticStandardBindingOverride> standardBindings,
        List<String> consumptionScenarios
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SemanticGrainOverride(String statement, List<String> keys) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SemanticStandardBindingOverride(
        String fieldName,
        UUID standardElementId,
        Integer standardElementVersion,
        String referenceCode,
        Integer referenceCodeVersion,
        UUID measurementUnitId,
        Integer measurementUnitVersion,
        String securityLevel
    ) {}

    public record PreviewIssue(
        String code,
        Severity severity,
        String fieldPath,
        String modelUniqueId,
        String message,
        String recoveryAction
    ) {}

    public record PreviewSummary(
        int total,
        int ready,
        int blocked,
        int create,
        int update,
        int skip,
        int conflict
    ) {}

    public record PreviewItem(
        String dbtUniqueId,
        Action action,
        ConversionMode conversionMode,
        JsonNode proposedModelSpec,
        JsonNode proposedImplementation,
        List<PreviewIssue> issues
    ) {}

    public record PreviewResponse(
        UUID runId,
        String previewHash,
        String applyPayloadChecksum,
        PreviewSummary summary,
        List<PreviewItem> items
    ) {}

    public record PreviewRunResponse(
        UUID runId,
        UUID planId,
        String previewHash,
        String applyPayloadChecksum,
        RunStatus status,
        Instant expiresAt,
        PreviewSummary summary,
        List<PreviewItem> items
    ) {}

    public static final class ModelSpecImportPreviewException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;
        private final Kind kind;
        private final transient Object details;

        public ModelSpecImportPreviewException(String code, String message, Kind kind, Object details) {
            super(message);
            this.code = code;
            this.kind = kind;
            this.details = details;
        }

        public String code() {
            return code;
        }

        public Kind kind() {
            return kind;
        }

        public Object details() {
            return details;
        }
    }

    public enum Kind {
        BAD_REQUEST,
        UNPROCESSABLE,
        FORBIDDEN,
        NOT_FOUND,
        GONE,
        RATE_LIMITED,
    }
}
