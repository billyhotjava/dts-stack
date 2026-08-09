package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import java.time.Instant;
import java.util.List;

/** Public inspect response and the independent three-axis dbt compatibility projection. */
public final class DbtArchiveInspectionContract {

    private DbtArchiveInspectionContract() {}

    public enum InspectionCompatibility {
        SUPPORTED,
        UNSUPPORTED,
        UNKNOWN,
    }

    public enum ImportProjectionCompatibility {
        IMPORTABLE,
        STRUCTURE_VIEW_ONLY,
        BLOCKED,
    }

    public enum MaterializationCompatibility {
        CERTIFIED,
        NOT_CERTIFIED,
        UNSUPPORTED,
        UNKNOWN,
    }

    public enum PackageProfile {
        ARTIFACT_RICH,
        SOURCE_ONLY,
        LEGACY_TSV,
    }

    public enum CandidateEligibility {
        ELIGIBLE,
        REQUIRES_MAPPING,
        BLOCKED,
    }

    public enum DiagnosticSeverity {
        ERROR,
        WARNING,
        INFO,
    }

    public enum DiagnosticAxis {
        INSPECTION,
        IMPORT_PROJECTION,
        MATERIALIZATION,
    }

    public record CompatibilityIssue(
        String code,
        String stage,
        String category,
        String message,
        boolean retryable,
        String recoveryAction,
        String correlationId
    ) {}

    public record DbtCompatibilityView(
        InspectionCompatibility inspection,
        ImportProjectionCompatibility importProjection,
        MaterializationCompatibility materialization,
        String dbtCoreVersion,
        String manifestSchemaVersion,
        String adapterType,
        String adapterPackageVersion,
        String certificationProfileId,
        List<CompatibilityIssue> issues
    ) {
        public DbtCompatibilityView {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record ImportDiagnostic(
        String code,
        DiagnosticSeverity severity,
        DiagnosticAxis axis,
        boolean blocksImport,
        String modelUniqueId,
        List<String> affectedUniqueIds,
        String message,
        String recoveryAction,
        boolean retryable,
        String correlationId
    ) {
        public ImportDiagnostic {
            affectedUniqueIds = affectedUniqueIds == null ? List.of() : List.copyOf(affectedUniqueIds);
        }
    }

    public record InspectionSummary(int discovered, int technicalOnly, int eligible, int requiresMapping, int blocked) {}

    public record InspectCandidate(String dbtUniqueId, CandidateEligibility eligibility, List<String> diagnosticCodes) {
        public InspectCandidate {
            diagnosticCodes = diagnosticCodes == null ? List.of() : List.copyOf(diagnosticCodes);
        }
    }

    public record InspectionReport(
        PackageProfile packageProfile,
        InspectionSummary summary,
        List<InspectCandidate> candidates,
        List<ImportDiagnostic> diagnostics
    ) {
        public InspectionReport {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    public record InspectArchiveResponse(
        @JsonProperty("package") ModelPackage modelPackage,
        DbtCompatibilityView compatibility,
        InspectionReport report,
        String inspectionProof,
        Instant proofExpiresAt
    ) {
        public InspectArchiveResponse(
            ModelPackage modelPackage,
            DbtCompatibilityView compatibility,
            String inspectionProof,
            Instant proofExpiresAt
        ) {
            this(modelPackage, compatibility, null, inspectionProof, proofExpiresAt);
        }
    }
}
