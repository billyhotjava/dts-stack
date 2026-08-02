package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CompatibilityIssue;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportProjectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionCompatibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.MaterializationCompatibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Derives display capabilities from inspected facts without claiming runtime certification. */
@Component
public class DbtCompatibilityEvaluator {

    public DbtCompatibilityView evaluate(ModelPackage modelPackage) {
        if (modelPackage == null) {
            throw new IllegalArgumentException("modelPackage is required");
        }
        ImportProjectionCompatibility importProjection = importProjection(modelPackage);
        String adapterType = modelPackage.dbt() == null ? null : text(modelPackage.dbt().adapterType());
        MaterializationCompatibility materialization = isPostgres(adapterType)
            ? MaterializationCompatibility.NOT_CERTIFIED
            : MaterializationCompatibility.UNSUPPORTED;
        List<CompatibilityIssue> issues = new ArrayList<>();
        String correlationId = "dbt-inspect:" + textOr(modelPackage.packageId(), "unknown-package");
        if (importProjection == ImportProjectionCompatibility.BLOCKED) {
            issues.add(
                new CompatibilityIssue(
                    "DBT_IMPORT_PROJECTION_BLOCKED",
                    "INSPECT",
                    "VALIDATION",
                    "The inspected package does not contain a trustworthy model projection",
                    false,
                    "REUPLOAD",
                    correlationId
                )
            );
        }
        if (materialization == MaterializationCompatibility.NOT_CERTIFIED) {
            issues.add(
                new CompatibilityIssue(
                    "DBT_RUNTIME_NOT_CERTIFIED",
                    "INSPECT",
                    "VALIDATION",
                    "The exact dbt PostgreSQL runtime profile has not been certified",
                    false,
                    "CONTACT_ADMIN",
                    correlationId
                )
            );
        } else {
            issues.add(
                new CompatibilityIssue(
                    "DBT_ADAPTER_UNSUPPORTED",
                    "INSPECT",
                    "VALIDATION",
                    "The package adapter is outside the supported materialization scope",
                    false,
                    "CONTACT_ADMIN",
                    correlationId
                )
            );
        }
        return new DbtCompatibilityView(
            InspectionCompatibility.SUPPORTED,
            importProjection,
            materialization,
            null,
            modelPackage.dbt() == null ? null : modelPackage.dbt().manifestVersion(),
            adapterType,
            null,
            null,
            issues
        );
    }

    private static ImportProjectionCompatibility importProjection(ModelPackage modelPackage) {
        List<PackageModel> models = modelPackage.models() == null ? List.of() : modelPackage.models();
        boolean importable = models.stream().anyMatch(DbtCompatibilityEvaluator::canBecomeImportable);
        if (importable) {
            return ImportProjectionCompatibility.IMPORTABLE;
        }
        boolean hasStructure = !models.isEmpty() ||
        (modelPackage.sources() != null && !modelPackage.sources().isEmpty()) ||
        (modelPackage.technicalNodes() != null && !modelPackage.technicalNodes().isEmpty());
        return hasStructure
            ? ImportProjectionCompatibility.STRUCTURE_VIEW_ONLY
            : ImportProjectionCompatibility.BLOCKED;
    }

    private static boolean canBecomeImportable(PackageModel model) {
        if (model == null || model.conversion() == null || model.conversion().mode() == null) {
            return false;
        }
        if (
            model.conversion().mode() == ConversionMode.DESIGNER_GENERATED ||
            model.conversion().mode() == ConversionMode.DBT_BACKED
        ) {
            return true;
        }
        return model.conversion().mode() == ConversionMode.BLOCKED &&
        model.conversion().reasonCodes() != null &&
        !model.conversion().reasonCodes().isEmpty() &&
        model
            .conversion()
            .reasonCodes()
            .stream()
            .allMatch(reason ->
                reason != null && (reason.startsWith("MISSING_") || "SOURCE_SEMANTICS_INCOMPLETE".equals(reason))
            );
    }

    private static boolean isPostgres(String adapterType) {
        return adapterType != null && SetHolder.POSTGRES.contains(adapterType.toLowerCase(Locale.ROOT));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String textOr(String value, String fallback) {
        String normalized = text(value);
        return normalized == null ? fallback : normalized;
    }

    private static final class SetHolder {

        private static final java.util.Set<String> POSTGRES = java.util.Set.of("postgres", "postgresql");

        private SetHolder() {}
    }
}
