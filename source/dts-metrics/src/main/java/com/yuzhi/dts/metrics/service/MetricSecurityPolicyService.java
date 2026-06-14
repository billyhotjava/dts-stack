package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Single source of truth for the metrics security spine: platform permission checks, RLS/masking
 * policy resolution, SQL injection helpers and policy-injection auditing. Extracted from
 * {@link MetricArtifactGenerationService} so the metric-pack path and the graph-lifecycle path reach
 * the same security depth instead of maintaining two divergent implementations.
 *
 * <p>All behavior here is verbatim-preserving relative to the pack path it was extracted from; the
 * 921-line {@code MetricArtifactGenerationServiceTest} is the parity guard.
 */
@Service
public class MetricSecurityPolicyService {

    private final PlatformContractClient platformContractClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public MetricSecurityPolicyService(PlatformContractClient platformContractClient) {
        this.platformContractClient = platformContractClient;
    }

    // ---------------------------------------------------------------------
    // Value types
    // ---------------------------------------------------------------------

    /** Caller identity, generalized from the pack's {@code PreviewActor}. */
    public record SecurityActor(String username, List<String> roles, String deptCode, String classification) {
        public static SecurityActor system() {
            return new SecurityActor("system", List.of("ROLE_ADMIN"), null, "INTERNAL");
        }
    }

    /** Platform asset the action targets, generalized from the pack's {@code PlatformAssetDeclaration}. */
    public record PolicyAsset(String type, String id, String key, String classification) {}

    /** A metric/measure expression so both pack columns and lifecycle measures feed one validator. */
    public record MetricExpression(String code, String sql) {}

    /**
     * Denial signal. Unchecked, carries no asset details. The pack path catches it and converts to an
     * invalid preview result; the lifecycle path catches it and maps to HTTP 403.
     */
    public static class PermissionDeniedException extends RuntimeException {

        public PermissionDeniedException(String reason) {
            super(reason);
        }
    }

    // ---------------------------------------------------------------------
    // Permission
    // ---------------------------------------------------------------------

    /** Raw permission decision; null-safe (a null transport result is treated as denied). */
    public PlatformContractClient.PermissionCheckResult checkPermission(SecurityActor actor, PolicyAsset asset, String action) {
        SecurityActor effectiveActor = actor != null ? actor : SecurityActor.system();
        PlatformContractClient.PermissionCheckResult result = platformContractClient.checkPermission(
            new PlatformContractClient.PermissionCheckRequest(
                effectiveActor.username(),
                effectiveActor.roles(),
                effectiveActor.deptCode(),
                effectiveActor.classification(),
                asset != null ? asset.classification() : null,
                action,
                asset != null ? new PlatformContractClient.PermissionAsset(asset.type(), asset.id(), asset.key()) : null
            )
        );
        return result != null ? result : PlatformContractClient.PermissionCheckResult.denied("empty_response");
    }

    /** Enforce permission. No-op when the asset is unmanaged; throws {@link PermissionDeniedException} on denial. */
    public void requirePermission(SecurityActor actor, PolicyAsset asset, String action) {
        if (asset == null) {
            return;
        }
        if (!checkPermission(actor, asset, action).allowed()) {
            throw new PermissionDeniedException("asset_permission_denied");
        }
    }

    // ---------------------------------------------------------------------
    // RLS / masking resolution
    // ---------------------------------------------------------------------

    /** Resolve effective RLS/masking policy from the platform. Never null (falls back to empty). */
    public PlatformContractClient.RlsPolicyResult resolvePolicy(SecurityActor actor, PolicyAsset asset, String action) {
        SecurityActor effectiveActor = actor != null ? actor : SecurityActor.system();
        PlatformContractClient.RlsPolicyResult result = platformContractClient.resolveRlsPolicy(
            new PlatformContractClient.RlsPolicyRequest(
                effectiveActor.username(),
                effectiveActor.roles(),
                effectiveActor.deptCode(),
                effectiveActor.classification(),
                asset != null ? asset.classification() : null,
                action,
                asset != null ? new PlatformContractClient.PermissionAsset(asset.type(), asset.id(), asset.key()) : null
            )
        );
        return result != null ? result : PlatformContractClient.RlsPolicyResult.empty();
    }

    /** Pack-specific: a manifest that declares apply_rls=true must receive a non-empty platform policy. */
    public void requireDeclaredRlsPolicy(PlatformContractClient.RlsPolicyResult policy) {
        if (policy == null || !policy.applyRls() || policyEmpty(policy)) {
            throw new IllegalArgumentException(
                "metric-pack declares apply_rls=true but platform policy returned empty; ask platform admin to configure row-filter or column-mask for asset"
            );
        }
    }

    public boolean policyEmpty(PlatformContractClient.RlsPolicyResult policy) {
        if (policy == null) {
            return true;
        }
        boolean noPredicates = policy.predicates() == null || policy.predicates().stream().noneMatch(StringUtils::hasText);
        boolean noMasking = policy.maskedColumns() == null || policy.maskedColumns().stream().noneMatch(StringUtils::hasText);
        return noPredicates && noMasking;
    }

    public boolean hasMaskedColumns(PlatformContractClient.RlsPolicyResult policy) {
        return !maskedColumns(policy).isEmpty();
    }

    /** Normalized (safe-identifier, lowercased) set of masked columns. */
    public Set<String> maskedColumns(PlatformContractClient.RlsPolicyResult policy) {
        if (policy == null || policy.maskedColumns() == null || policy.maskedColumns().isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (String column : policy.maskedColumns()) {
            if (!StringUtils.hasText(column)) {
                continue;
            }
            columns.add(MetricFormulaSqlGenerator.safeIdentifier(column).toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(columns);
    }

    // ---------------------------------------------------------------------
    // SQL injection helpers
    // ---------------------------------------------------------------------

    /** Append a platform-sourced RLS {@code where} block. No-op for an empty policy (byte-stable). */
    public void appendRlsWhere(StringBuilder sql, PlatformContractClient.RlsPolicyResult policy) {
        if (policy == null || policy.predicates() == null || policy.predicates().isEmpty()) {
            return;
        }
        List<String> predicates = policy.predicates().stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (predicates.isEmpty()) {
            return;
        }
        String source = StringUtils.hasText(policy.policySource()) ? policy.policySource() : "platform-policy";
        sql.append("-- dts-platform RLS: ").append(source).append("\n");
        sql.append("where\n");
        for (int i = 0; i < predicates.size(); i++) {
            sql.append("    (").append(predicates.get(i)).append(")");
            sql.append(i + 1 < predicates.size() ? " and\n" : "\n");
        }
    }

    /** Wrap a dimension in the masking macro when policy masks it, else return it unchanged. */
    public String maskDimensionExpression(String dimension, Set<String> maskedColumns) {
        if (maskedColumns == null || !maskedColumns.contains(dimension.toLowerCase(Locale.ROOT))) {
            return dimension;
        }
        return "{{ dts_mask('" + dimension.replace("'", "''") + "') }}";
    }

    /** Reject a masked column used inside a metric/measure formula. */
    public void validateMaskedMetricInputs(List<MetricExpression> metrics, Set<String> maskedColumns) {
        if (metrics == null || metrics.isEmpty() || maskedColumns == null || maskedColumns.isEmpty()) {
            return;
        }
        for (MetricExpression metric : metrics) {
            for (String maskedColumn : maskedColumns) {
                if (referencesIdentifier(metric.sql(), maskedColumn)) {
                    throw new IllegalArgumentException(
                        "masked column "
                            + maskedColumn
                            + " is used by metric "
                            + metric.code()
                            + "; configure a non-sensitive surrogate or remove it from the metric formula"
                    );
                }
            }
        }
    }

    private static boolean referencesIdentifier(String sql, String identifier) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(identifier)) {
            return false;
        }
        String pattern = "(?<![A-Za-z0-9_])(?:[A-Za-z_][A-Za-z0-9_]*\\.)*" + Pattern.quote(identifier) + "(?![A-Za-z0-9_])";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find();
    }

    public String maskingMacroSql() {
        return """
            {% macro dts_mask(column_name) -%}
                cast(null as varchar)
            {%- endmacro %}
            """;
    }

    public String securityPolicyJson(PlatformContractClient.RlsPolicyResult policy) {
        PlatformContractClient.RlsPolicyResult effective = policy != null ? policy : PlatformContractClient.RlsPolicyResult.empty();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applyRls", effective.applyRls());
        payload.put("predicates", effective.predicates() != null ? effective.predicates() : List.of());
        payload.put("maskedColumns", effective.maskedColumns() != null ? effective.maskedColumns() : List.of());
        payload.put("policySource", StringUtils.hasText(effective.policySource()) ? effective.policySource() : "platform-permission");
        payload.put("releaseGate", "platform/dbt release gate must re-resolve and compare this policy before publishing");
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to render security policy artifact", e);
        }
    }

    // ---------------------------------------------------------------------
    // Audit
    // ---------------------------------------------------------------------

    /** Canonical predicate hash, so callers stop hand-rolling empty placeholders. */
    public String predicateHash(PlatformContractClient.RlsPolicyResult policy) {
        return MetricPolicyAuditSupport.predicateHash(policy);
    }

    /** Record a consumer-side policy-injection audit on the platform. */
    public void recordInjection(PlatformContractClient.PolicyInjectionAuditRequest request) {
        platformContractClient.recordPolicyInjection(request);
    }
}
