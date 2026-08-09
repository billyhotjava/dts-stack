package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ImportIssue;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CandidateEligibility;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.CompatibilityIssue;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DiagnosticAxis;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DiagnosticSeverity;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.ImportDiagnostic;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectCandidate;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionReport;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectionSummary;
import com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.PackageProfile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Projects raw dbt inspection facts into a stable import readiness report. */
public class DbtArchiveInspectionReportProjector {

    public InspectionReport project(ModelPackage modelPackage, DbtCompatibilityView compatibility) {
        if (modelPackage == null || compatibility == null) {
            throw new IllegalArgumentException("modelPackage and compatibility are required");
        }
        List<PackageModel> models = modelPackage.models() == null ? List.of() : modelPackage.models();
        List<InspectCandidate> candidates = models.stream().map(DbtArchiveInspectionReportProjector::candidate).toList();
        int eligible = count(candidates, CandidateEligibility.ELIGIBLE);
        int requiresMapping = count(candidates, CandidateEligibility.REQUIRES_MAPPING);
        int blocked = count(candidates, CandidateEligibility.BLOCKED);
        int technicalOnly = modelPackage.technicalNodes() == null ? 0 : modelPackage.technicalNodes().size();
        return new InspectionReport(
            packageProfile(modelPackage),
            new InspectionSummary(models.size(), technicalOnly, eligible, requiresMapping, blocked),
            candidates,
            diagnostics(modelPackage, compatibility)
        );
    }

    private static InspectCandidate candidate(PackageModel model) {
        List<String> reasons = reasons(model);
        return new InspectCandidate(model.dbtUniqueId(), eligibility(model, reasons), reasons);
    }

    private static CandidateEligibility eligibility(PackageModel model, List<String> reasons) {
        if (model.conversion() != null && model.conversion().mode() != ConversionMode.BLOCKED) {
            return CandidateEligibility.ELIGIBLE;
        }
        if (!reasons.isEmpty() && reasons.stream().allMatch(DbtArchiveInspectionReportProjector::mappingReason)) {
            return CandidateEligibility.REQUIRES_MAPPING;
        }
        return CandidateEligibility.BLOCKED;
    }

    private static boolean mappingReason(String reason) {
        return reason != null && (reason.startsWith("MISSING_") || "SOURCE_SEMANTICS_INCOMPLETE".equals(reason));
    }

    private static List<String> reasons(PackageModel model) {
        if (model.conversion() == null || model.conversion().reasonCodes() == null) {
            return List.of();
        }
        return model.conversion().reasonCodes().stream().filter(reason -> reason != null && !reason.isBlank()).distinct().toList();
    }

    private static int count(List<InspectCandidate> candidates, CandidateEligibility eligibility) {
        return (int) candidates.stream().filter(candidate -> candidate.eligibility() == eligibility).count();
    }

    private static PackageProfile packageProfile(ModelPackage modelPackage) {
        String manifestVersion = modelPackage.dbt() == null ? null : modelPackage.dbt().manifestVersion();
        if ("source-project/v1".equals(manifestVersion)) {
            return PackageProfile.SOURCE_ONLY;
        }
        if ("legacy-tsv/v1".equals(manifestVersion)) {
            return PackageProfile.LEGACY_TSV;
        }
        return PackageProfile.ARTIFACT_RICH;
    }

    private static List<ImportDiagnostic> diagnostics(ModelPackage modelPackage, DbtCompatibilityView compatibility) {
        List<ImportDiagnostic> diagnostics = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (CompatibilityIssue issue : compatibility.issues()) {
            add(diagnostics, keys, fromCompatibility(issue));
        }
        if (modelPackage.issues() != null) {
            for (ImportIssue issue : modelPackage.issues()) {
                add(diagnostics, keys, fromPackage(issue, modelPackage.packageId()));
            }
        }
        if (modelPackage.models() != null) {
            for (PackageModel model : modelPackage.models()) {
                for (String reason : reasons(model)) {
                    add(diagnostics, keys, fromReason(reason, model.dbtUniqueId(), modelPackage.packageId()));
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void add(List<ImportDiagnostic> target, Set<String> keys, ImportDiagnostic diagnostic) {
        String key = diagnostic.code() + "\u0000" + diagnostic.modelUniqueId();
        if (keys.add(key)) {
            target.add(diagnostic);
        }
    }

    private static ImportDiagnostic fromCompatibility(CompatibilityIssue issue) {
        DiagnosticAxis axis = issue.code() != null && (issue.code().contains("RUNTIME") || issue.code().contains("ADAPTER"))
            ? DiagnosticAxis.MATERIALIZATION
            : DiagnosticAxis.IMPORT_PROJECTION;
        return new ImportDiagnostic(
            issue.code(),
            DiagnosticSeverity.ERROR,
            axis,
            axis == DiagnosticAxis.IMPORT_PROJECTION,
            null,
            List.of(),
            issue.message(),
            issue.recoveryAction(),
            issue.retryable(),
            issue.correlationId()
        );
    }

    private static ImportDiagnostic fromPackage(ImportIssue issue, String packageId) {
        DiagnosticSeverity severity = severity(issue.severity());
        return new ImportDiagnostic(
            issue.code(),
            severity,
            DiagnosticAxis.INSPECTION,
            severity == DiagnosticSeverity.ERROR,
            issue.modelUniqueId(),
            issue.modelUniqueId() == null ? List.of() : List.of(issue.modelUniqueId()),
            issue.message(),
            issue.recoveryAction(),
            false,
            correlationId(packageId)
        );
    }

    private static ImportDiagnostic fromReason(String code, String uniqueId, String packageId) {
        boolean mapping = mappingReason(code);
        return new ImportDiagnostic(
            code,
            mapping ? DiagnosticSeverity.WARNING : DiagnosticSeverity.ERROR,
            DiagnosticAxis.IMPORT_PROJECTION,
            !mapping,
            uniqueId,
            List.of(uniqueId),
            message(code),
            mapping ? "COMPLETE_MAPPING" : "REUPLOAD",
            false,
            correlationId(packageId)
        );
    }

    private static DiagnosticSeverity severity(String value) {
        if (value == null) {
            return DiagnosticSeverity.WARNING;
        }
        try {
            return DiagnosticSeverity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DiagnosticSeverity.WARNING;
        }
    }

    private static String message(String code) {
        return switch (code) {
            case "SOURCE_FIELDS_UNVERIFIED" -> "模型缺少 enforced 且字段 name/data_type 完整唯一的 schema 契约";
            case "SOURCE_MACRO_DEPENDENCY_UNVERIFIED" -> "模型或其上游包含无法静态证明的宏依赖";
            case "SOURCE_DEPENDENCY_DYNAMIC" -> "模型或其上游包含动态 ref、source 或 Jinja 依赖";
            case "SOURCE_PACKAGE_MISSING" -> "模型引用的 dbt package 未包含在导入包中";
            case "SOURCE_SEMANTICS_INCOMPLETE" -> "技术结构已识别，需要补充模型类型、分层、粒度等业务语义";
            default -> "模型不满足当前导入投影约束：" + code;
        };
    }

    private static String correlationId(String packageId) {
        return "dbt-inspect:" + (packageId == null || packageId.isBlank() ? "unknown-package" : packageId);
    }
}
