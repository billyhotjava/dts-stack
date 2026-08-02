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

    public record InspectArchiveResponse(
        @JsonProperty("package") ModelPackage modelPackage,
        DbtCompatibilityView compatibility,
        String inspectionProof,
        Instant proofExpiresAt
    ) {}
}
