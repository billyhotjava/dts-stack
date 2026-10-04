package com.yuzhi.dts.platform.service.metrics;

import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricModelValidationService {

    private static final Pattern UNSAFE_SQL = Pattern.compile(
        "(?i)(;|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b|\\btruncate\\b|\\bgrant\\b|\\brevoke\\b|\\bcopy\\b)"
    );

    private final DbtReleaseGateService dbtReleaseGateService;

    public MetricModelValidationService(DbtReleaseGateService dbtReleaseGateService) {
        this.dbtReleaseGateService = dbtReleaseGateService;
    }

    public ValidationResult validate(MetricModelValidationRequest request) {
        List<ValidationDiagnostic> diagnostics = new ArrayList<>();
        if (request == null) {
            diagnostics.add(error("request", "request_required", "validation request is required"));
            return result(null, null, null, diagnostics, null);
        }

        validateRequestShape(request, diagnostics);
        if (hasErrors(diagnostics)) {
            return result(request.modelId(), request.modelName(), request.artifactRef(), diagnostics, null);
        }

        DbtReleaseGateService.DbtReleaseGateResult releaseGate = dbtReleaseGateService.evaluate(request.modelName(), null, null, true);
        if (releaseGate != null && releaseGate.blocking()) {
            diagnostics.add(error("dbt", "dbt_validation_failed", "dbt release gate blocked candidate model"));
        } else if (releaseGate != null && releaseGate.warning()) {
            diagnostics.add(warning("dbt", "dbt_validation_warning", "dbt release gate returned warnings"));
        }
        return result(request.modelId(), request.modelName(), request.artifactRef(), diagnostics, releaseGate);
    }

    private static void validateRequestShape(MetricModelValidationRequest request, List<ValidationDiagnostic> diagnostics) {
        if (!StringUtils.hasText(request.modelId())) {
            diagnostics.add(error("modelId", "model_id_required", "modelId is required"));
        }
        if (!StringUtils.hasText(request.modelName())) {
            diagnostics.add(error("modelName", "model_name_required", "modelName is required"));
        }
        if (!StringUtils.hasText(request.artifactRef())) {
            diagnostics.add(error("artifactRef", "artifact_ref_required", "artifactRef is required"));
        }
        if (request.graph() == null || request.graph().isEmpty()) {
            diagnostics.add(error("graph", "graph_required", "graph is required"));
        }
        Map<String, Object> artifacts = request.artifacts() != null ? request.artifacts() : Map.of();
        String dbtModelSql = text(artifacts.get("dbtModelSql"));
        if (!StringUtils.hasText(dbtModelSql)) {
            diagnostics.add(error("artifacts.dbtModelSql", "artifact_required", "dbtModelSql artifact is required"));
        } else if (UNSAFE_SQL.matcher(dbtModelSql).find()) {
            diagnostics.add(error("artifacts.dbtModelSql", "artifact_sql_unsafe", "dbtModelSql contains unsupported statement tokens"));
        }
        if (!StringUtils.hasText(text(artifacts.get("schemaYml")))) {
            diagnostics.add(error("artifacts.schemaYml", "artifact_required", "schemaYml artifact is required"));
        }
        if (!(artifacts.get("lineageHint") instanceof Map<?, ?>)) {
            diagnostics.add(error("artifacts.lineageHint", "artifact_required", "lineageHint artifact is required"));
        }
        validateSecuritySnapshot(request, artifacts, diagnostics);
    }

    private static void validateSecuritySnapshot(
        MetricModelValidationRequest request,
        Map<String, Object> artifacts,
        List<ValidationDiagnostic> diagnostics
    ) {
        Object securitySnapshot = artifacts.get("securitySnapshot");
        if (!(securitySnapshot instanceof Map<?, ?> snapshot)) {
            diagnostics.add(error("artifacts.securitySnapshot", "artifact_required", "securitySnapshot artifact is required"));
            return;
        }
        String policySource = text(snapshot.get("policySource"));
        String predicateHash = text(snapshot.get("predicateHash"));
        if (!StringUtils.hasText(policySource)) {
            diagnostics.add(error("artifacts.securitySnapshot.policySource", "security_policy_required", "security policy source is required"));
        } else if (!policySource.equals(text(request.appliedPolicySource()))) {
            diagnostics.add(error("artifacts.securitySnapshot.policySource", "security_policy_mismatch", "security policy source does not match request"));
        }
        if (!StringUtils.hasText(predicateHash)) {
            diagnostics.add(error("artifacts.securitySnapshot.predicateHash", "security_hash_required", "security predicate hash is required"));
        } else if (!predicateHash.equals(text(request.appliedPredicateHash()))) {
            diagnostics.add(error("artifacts.securitySnapshot.predicateHash", "security_hash_mismatch", "security predicate hash does not match request"));
        }
    }

    private static ValidationResult result(
        String modelId,
        String modelName,
        String artifactRef,
        List<ValidationDiagnostic> diagnostics,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate
    ) {
        boolean valid = !hasErrors(diagnostics);
        return new ValidationResult(
            modelId,
            modelName,
            artifactRef,
            valid ? "DBT_VALIDATED" : "DBT_VALIDATION_FAILED",
            valid ? "PASS" : "BLOCK",
            valid,
            List.copyOf(diagnostics),
            sanitizeReleaseGate(releaseGate),
            "metrics-validation:" + safeTracePart(modelId) + ":" + Instant.now().toEpochMilli(),
            Instant.now().toString()
        );
    }

    private static Map<String, Object> sanitizeReleaseGate(DbtReleaseGateService.DbtReleaseGateResult releaseGate) {
        if (releaseGate == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selector", releaseGate.selector());
        result.put("strictMode", releaseGate.strictMode());
        result.put("decision", releaseGate.decision());
        result.put("blocking", releaseGate.blocking());
        result.put("warning", releaseGate.warning());
        result.put("blockers", releaseGate.blockers() != null ? releaseGate.blockers() : List.of());
        result.put("warnings", releaseGate.warnings() != null ? releaseGate.warnings() : List.of());
        DbtReleaseGateService.BuildEvidence evidence = releaseGate.buildEvidence();
        if (evidence != null) {
            result.put(
                "buildEvidence",
                Map.of(
                    "invocationId",
                    text(evidence.invocationId()),
                    "command",
                    text(evidence.command()),
                    "status",
                    text(evidence.status()),
                    "generatedAt",
                    text(evidence.generatedAt())
                )
            );
        }
        return result;
    }

    private static boolean hasErrors(List<ValidationDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(item -> "ERROR".equals(item.severity()));
    }

    private static ValidationDiagnostic error(String field, String code, String message) {
        return new ValidationDiagnostic(field, "ERROR", code, message);
    }

    private static ValidationDiagnostic warning(String field, String code, String message) {
        return new ValidationDiagnostic(field, "WARNING", code, message);
    }

    private static String safeTracePart(String value) {
        String normalized = text(value).replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private static String text(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    public record MetricModelValidationRequest(
        String modelId,
        String modelName,
        String artifactRef,
        Map<String, Object> graph,
        Map<String, Object> artifacts,
        String appliedPolicySource,
        String appliedPredicateHash
    ) {}

    public record ValidationResult(
        String modelId,
        String modelName,
        String artifactRef,
        String status,
        String decision,
        boolean valid,
        List<ValidationDiagnostic> diagnostics,
        Map<String, Object> releaseGate,
        String validationTraceId,
        String validatedAt
    ) {}

    public record ValidationDiagnostic(String field, String severity, String code, String message) {}
}
