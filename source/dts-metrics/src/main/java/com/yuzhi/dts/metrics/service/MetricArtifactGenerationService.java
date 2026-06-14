package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricArtifactGenerationService {

    private final MetricPackValidationService validationService;
    private final MetricFormulaSqlGenerator formulaSqlGenerator;
    private final PlatformContractClient platformContractClient;
    private final MetricSecurityPolicyService securityPolicyService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Set<String> SOURCE_MODEL_ASSET_TYPES = Set.of("DATASET", "DBT_MODEL", "SEMANTIC_MODEL");

    public MetricArtifactGenerationService(
        MetricPackValidationService validationService,
        MetricFormulaSqlGenerator formulaSqlGenerator,
        PlatformContractClient platformContractClient
    ) {
        this.validationService = validationService;
        this.formulaSqlGenerator = formulaSqlGenerator;
        this.platformContractClient = platformContractClient;
        // The shared security spine is derived from the already-injected platform client so this
        // constructor signature stays pinned by the existing pack test suite. The same component is a
        // Spring @Service bean for the lifecycle path to inject.
        this.securityPolicyService = new MetricSecurityPolicyService(platformContractClient);
    }

    public MetricArtifactPreviewResult preview(String manifestContent) {
        return preview(manifestContent, PreviewActor.system());
    }

    public MetricArtifactPreviewResult preview(String manifestContent, PreviewActor actor) {
        MetricPackValidationResult validation = validationService.validateManifest(manifestContent);
        if (!validation.valid()) {
            return MetricArtifactPreviewResult.invalid(validation.errors(), validation.summary());
        }
        Map<String, Object> manifest;
        try {
            manifest = yamlMapper.readValue(manifestContent, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            return MetricArtifactPreviewResult.invalid(List.of("manifest.yml is not valid YAML: " + e.getMessage()), validation.summary());
        }

        String packId = String.valueOf(manifest.get("pack_id"));
        String modelName = "dws_" + packId.replace('-', '_') + "_summary";
        String sourceModel;
        List<String> dimensions;
        List<MetricColumn> metrics;
        PlatformContractClient.RlsPolicyResult rlsPolicy = PlatformContractClient.RlsPolicyResult.empty();
        Map<String, String> artifacts = new LinkedHashMap<>();
        boolean declaredApplyRls = applyRls(manifest);
        try {
            sourceModel = safeRefName(String.valueOf(manifest.getOrDefault("source_model", "replace_with_dwd_model")));
            PlatformAssetDeclaration sourceAsset = requireSourceModelPlatformAsset(manifest, sourceModel);
            MetricSecurityPolicyService.SecurityActor securityActor = toSecurityActor(actor);
            MetricSecurityPolicyService.PolicyAsset policyAsset = toPolicyAsset(sourceAsset);
            securityPolicyService.requirePermission(securityActor, policyAsset, "PREVIEW");
            if (sourceAsset != null) {
                rlsPolicy = securityPolicyService.resolvePolicy(securityActor, policyAsset, "PREVIEW");
                if (declaredApplyRls) {
                    securityPolicyService.requireDeclaredRlsPolicy(rlsPolicy);
                }
            }
            requireActivePlatformDomains(manifest);
            requireActiveDataStandards(manifest);
            requireActiveGlossaryTerms(manifest);
            dimensions = readDimensions(manifest.get("dimensions"));
            metrics = readMetrics(manifest.get("metrics"));
            artifacts.put("dbtModelSql", dbtModelSql(modelName, sourceModel, dimensions, metrics, rlsPolicy));
            if (securityPolicyService.hasMaskedColumns(rlsPolicy)) {
                artifacts.put("maskingMacroSql", securityPolicyService.maskingMacroSql());
            }
            artifacts.put("securityPolicyJson", securityPolicyService.securityPolicyJson(rlsPolicy));
            artifacts.put("schemaYml", schemaYml(modelName, dimensions, metrics, rlsPolicy));
            artifacts.put("metricDoc", metricDoc(packId, dimensions, metrics));
            recordConsumerPolicyInjection(packId, actor, sourceAsset, sourceModel, rlsPolicy);
        } catch (MetricSecurityPolicyService.PermissionDeniedException e) {
            return MetricArtifactPreviewResult.invalid(
                List.of("platform asset permission check failed before artifact preview"),
                validation.summary()
            );
        } catch (IllegalArgumentException e) {
            return MetricArtifactPreviewResult.invalid(List.of(e.getMessage()), validation.summary());
        } catch (PlatformContractClient.PlatformContractException e) {
            String raw = e.getMessage() != null ? e.getMessage() : "";
            String message;
            if (raw.contains("asset permission")) {
                message = "platform contract unavailable while checking asset permissions; retry later";
            } else if (raw.contains("security policy")) {
                message = "platform contract unavailable while resolving security policy; retry later";
            } else if (raw.contains("domains resolve")) {
                message = "platform contract unavailable while resolving data domains; retry later";
            } else if (raw.contains("data standards resolve")) {
                message = "platform contract unavailable while resolving data standards; retry later";
            } else if (raw.contains("policy injection audit")) {
                message = "platform contract unavailable while writing security policy audit; retry later";
            } else {
                message = "platform contract unavailable while resolving glossary terms; retry later";
            }
            return MetricArtifactPreviewResult.invalid(List.of(message), validation.summary());
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("Generated artifacts are candidates only; platform/dbt release gate must review and publish them.");
        if (metrics.isEmpty()) {
            warnings.add("No inline metrics were found; generated placeholder artifact requires metric files to be imported later.");
        }
        warnings.add("Platform asset existence and permission checks must pass before preview or publish.");
        if (declaredApplyRls || !securityPolicyService.policyEmpty(rlsPolicy)) {
            warnings.add("security.apply_rls is a manifest declaration; effective RLS and masking policy is resolved from dts-platform.");
        }
        if (!declaredApplyRls && !securityPolicyService.policyEmpty(rlsPolicy)) {
            warnings.add("Platform security policy overrides manifest security.apply_rls=false.");
        }

        Map<String, Object> summary = new LinkedHashMap<>(validation.summary());
        summary.put("modelName", modelName);
        summary.put("sourceModel", sourceModel);
        summary.put("dimensionCount", dimensions.size());
        summary.put("metricCount", metrics.size());
        return MetricArtifactPreviewResult.valid(summary, artifacts, warnings);
    }

    private void recordConsumerPolicyInjection(
        String packId,
        PreviewActor actor,
        PlatformAssetDeclaration asset,
        String sourceModel,
        PlatformContractClient.RlsPolicyResult rlsPolicy
    ) {
        if (asset == null) {
            return;
        }
        PreviewActor effectiveActor = actor != null ? actor : PreviewActor.system();
        PlatformContractClient.RlsPolicyResult effective = rlsPolicy != null
            ? rlsPolicy
            : PlatformContractClient.RlsPolicyResult.empty();
        securityPolicyService.recordInjection(
            new PlatformContractClient.PolicyInjectionAuditRequest(
                effectiveActor.username(),
                asset.type(),
                firstText(asset.id(), asset.key(), sourceModel),
                "PREVIEW",
                effective.predicates() != null ? effective.predicates() : List.of(),
                effective.maskedColumns() != null ? effective.maskedColumns() : List.of(),
                StringUtils.hasText(effective.policySource()) ? effective.policySource() : "platform-permission",
                securityPolicyService.predicateHash(effective),
                "CONSUMER",
                packId
            )
        );
    }

    private String dbtModelSql(
        String modelName,
        String sourceModel,
        List<String> dimensions,
        List<MetricColumn> metrics,
        PlatformContractClient.RlsPolicyResult rlsPolicy
    ) {
        Set<String> maskedColumns = securityPolicyService.maskedColumns(rlsPolicy);
        securityPolicyService.validateMaskedMetricInputs(toMetricExpressions(metrics), maskedColumns);
        List<String> selectRows = new ArrayList<>();
        List<String> groupRows = new ArrayList<>();
        for (String dimension : dimensions) {
            String dimensionExpression = securityPolicyService.maskDimensionExpression(dimension, maskedColumns);
            selectRows.add("    " + dimensionExpression + " as " + columnAlias(dimension));
            groupRows.add(dimensionExpression);
        }
        if (metrics.isEmpty()) {
            selectRows.add("    1 as metric_pack_ready");
        } else {
            for (MetricColumn metric : metrics) {
                selectRows.add("    " + metric.sql() + " as " + metric.code());
            }
        }
        StringBuilder sql = new StringBuilder();
        sql.append("{{ config(materialized='table', tags=['dts-metrics', 'metric-pack']) }}\n\n");
        sql.append("-- Candidate artifact generated by dts-metrics. Review through platform/dbt release gate before publishing.\n");
        sql.append("select\n");
        sql.append(String.join(",\n", selectRows));
        sql.append("\nfrom {{ ref('").append(sourceModel).append("') }}\n");
        securityPolicyService.appendRlsWhere(sql, rlsPolicy);
        if (!groupRows.isEmpty()) {
            sql.append("group by\n");
            for (int i = 0; i < groupRows.size(); i++) {
                sql.append("    ").append(groupRows.get(i));
                sql.append(i + 1 < groupRows.size() ? ",\n" : "\n");
            }
        }
        return sql.toString();
    }

    private static String columnAlias(String column) {
        int dot = column.lastIndexOf('.');
        return dot >= 0 && dot + 1 < column.length() ? column.substring(dot + 1) : column;
    }

    private static List<MetricSecurityPolicyService.MetricExpression> toMetricExpressions(List<MetricColumn> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }
        return metrics.stream().map(metric -> new MetricSecurityPolicyService.MetricExpression(metric.code(), metric.sql())).toList();
    }

    private String schemaYml(
        String modelName,
        List<String> dimensions,
        List<MetricColumn> metrics,
        PlatformContractClient.RlsPolicyResult rlsPolicy
    ) {
        Set<String> maskedColumns = securityPolicyService.maskedColumns(rlsPolicy);
        StringBuilder yml = new StringBuilder();
        yml.append("version: 2\n\nmodels:\n");
        yml.append("  - name: ").append(modelName).append("\n");
        yml.append("    description: dts-metrics generated candidate model; publish through platform/dbt gate.\n");
        yml.append("    columns:\n");
        for (String dimension : dimensions) {
            yml.append("      - name: ").append(columnAlias(dimension)).append("\n");
            yml.append("        description: Dimension");
            if (maskedColumns.contains(dimension.toLowerCase(Locale.ROOT))) {
                yml.append(". Masked by dts-platform policy.");
            }
            yml.append("\n");
        }
        if (metrics.isEmpty()) {
            yml.append("      - name: metric_pack_ready\n");
            yml.append("        description: Metric pack placeholder column.\n");
        } else {
            for (MetricColumn metric : metrics) {
                yml.append("      - name: ").append(metric.code()).append("\n");
                yml.append("        description: ").append(metric.name()).append("\n");
            }
        }
        return yml.toString();
    }

    private String metricDoc(String packId, List<String> dimensions, List<MetricColumn> metrics) {
        StringBuilder doc = new StringBuilder();
        doc.append("# Metric Pack ").append(packId).append("\n\n");
        doc.append("## Dimensions\n\n");
        if (dimensions.isEmpty()) {
            doc.append("- No inline dimensions declared.\n");
        } else {
            dimensions.forEach(item -> doc.append("- ").append(item).append("\n"));
        }
        doc.append("\n## Metrics\n\n");
        if (metrics.isEmpty()) {
            doc.append("- No inline metrics declared.\n");
        } else {
            metrics.forEach(item -> doc.append("- `").append(item.code()).append("`: ").append(item.name()).append("\n"));
        }
        return doc.toString();
    }

    private List<String> readDimensions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> dimensions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("field") != null) {
                dimensions.add(MetricFormulaSqlGenerator.safeIdentifier(String.valueOf(map.get("field"))));
            } else {
                dimensions.add(MetricFormulaSqlGenerator.safeIdentifier(String.valueOf(item)));
            }
        }
        return dimensions;
    }

    private List<MetricColumn> readMetrics(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MetricColumn> metrics = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object codeRaw = map.get("metric_code") != null ? map.get("metric_code") : map.get("code");
            String rawCode = String.valueOf(codeRaw == null ? "" : codeRaw).trim();
            if (!StringUtils.hasText(rawCode) || map.get("formula") == null) {
                continue;
            }
            String code = safeRefName(rawCode);
            Object nameRaw = map.get("metric_name") != null ? map.get("metric_name") : map.get("name");
            String name = String.valueOf(nameRaw == null ? code : nameRaw);
            metrics.add(new MetricColumn(code, name, formulaSqlGenerator.render(map.get("formula"))));
        }
        return metrics;
    }

    private static PlatformAssetDeclaration requireSourceModelPlatformAsset(Map<String, Object> manifest, String sourceModel) {
        Object sourceModelRaw = manifest.get("source_model");
        if (sourceModelRaw == null || !StringUtils.hasText(String.valueOf(sourceModelRaw))) {
            return null;
        }
        Object dependenciesRaw = manifest.get("dependencies");
        if (!(dependenciesRaw instanceof Map<?, ?> dependencies)) {
            throw new IllegalArgumentException("source_model must be declared in dependencies.platform_assets before artifact preview");
        }
        Object assetsRaw = dependencies.get("platform_assets");
        if (!(assetsRaw instanceof List<?> assets)) {
            throw new IllegalArgumentException("source_model must be declared in dependencies.platform_assets before artifact preview");
        }
        for (Object item : assets) {
            if (!(item instanceof Map<?, ?> asset)) {
                continue;
            }
            Object typeRaw = asset.get("type") != null ? asset.get("type") : asset.get("asset_type");
            String type = String.valueOf(typeRaw).trim().toUpperCase(Locale.ROOT);
            if (!SOURCE_MODEL_ASSET_TYPES.contains(type)) {
                continue;
            }
            if (
                assetRefMatches(asset.get("id"), sourceModel) ||
                assetRefMatches(asset.get("asset_code"), sourceModel) ||
                assetRefMatches(asset.get("key"), sourceModel) ||
                assetRefMatches(asset.get("asset_key"), sourceModel)
            ) {
                String id = firstText(asset.get("id"), asset.get("asset_code"));
                String key = firstText(asset.get("key"), asset.get("asset_key"));
                String classification = firstText(asset.get("asset_classification"), asset.get("classification"));
                return new PlatformAssetDeclaration(type, id, key, classification);
            }
        }
        throw new IllegalArgumentException("source_model must be declared in dependencies.platform_assets before artifact preview");
    }

    private static MetricSecurityPolicyService.SecurityActor toSecurityActor(PreviewActor actor) {
        PreviewActor effectiveActor = actor != null ? actor : PreviewActor.system();
        return new MetricSecurityPolicyService.SecurityActor(
            effectiveActor.username(),
            effectiveActor.userRoles(),
            effectiveActor.userDeptCode(),
            effectiveActor.userClassification()
        );
    }

    private static MetricSecurityPolicyService.PolicyAsset toPolicyAsset(PlatformAssetDeclaration asset) {
        if (asset == null) {
            return null;
        }
        return new MetricSecurityPolicyService.PolicyAsset(asset.type(), asset.id(), asset.key(), asset.assetClassification());
    }

    private static boolean applyRls(Map<String, Object> manifest) {
        Object securityRaw = manifest.get("security");
        if (!(securityRaw instanceof Map<?, ?> security)) {
            return false;
        }
        Object applyRls = security.get("apply_rls");
        return Boolean.TRUE.equals(applyRls);
    }

    private void requireActivePlatformDomains(Map<String, Object> manifest) {
        List<String> refs = platformDomainRefs(manifest);
        if (refs.isEmpty()) {
            return;
        }
        PlatformContractClient.DomainResolveResult result = platformContractClient.resolveDomains(refs);
        List<String> missing = result != null && result.missing() != null ? result.missing() : refs;
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("data domains are missing in platform: " + String.join(", ", missing));
        }
        List<String> ambiguous = result != null && result.ambiguous() != null ? result.ambiguous() : List.of();
        if (!ambiguous.isEmpty()) {
            throw new IllegalArgumentException("data domains are ambiguous in platform: " + String.join(", ", ambiguous));
        }
    }

    private void requireActiveDataStandards(Map<String, Object> manifest) {
        List<String> refs = dataStandardRefs(manifest);
        if (refs.isEmpty()) {
            return;
        }
        PlatformContractClient.DataStandardResolveResult result = platformContractClient.resolveDataStandards(refs);
        List<String> missing = result != null && result.missing() != null ? result.missing() : refs;
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("data standards are missing in platform: " + String.join(", ", missing));
        }
        List<String> ambiguous = result != null && result.ambiguous() != null ? result.ambiguous() : List.of();
        if (!ambiguous.isEmpty()) {
            throw new IllegalArgumentException("data standards are ambiguous in platform: " + String.join(", ", ambiguous));
        }
        List<String> inactive = new ArrayList<>();
        if (result.inactive() != null) {
            inactive.addAll(result.inactive());
        }
        if (result.standards() != null) {
            for (PlatformContractClient.DataStandardContract standard : result.standards()) {
                if (standard != null && !standard.active() && StringUtils.hasText(standard.ref()) && !inactive.contains(standard.ref())) {
                    inactive.add(standard.ref());
                }
            }
        }
        if (!inactive.isEmpty()) {
            throw new IllegalArgumentException("data standards are not ACTIVE in platform: " + String.join(", ", inactive));
        }
    }

    private void requireActiveGlossaryTerms(Map<String, Object> manifest) {
        List<String> termRefs = inlineGlossaryTermRefs(manifest);
        if (termRefs.isEmpty()) {
            return;
        }
        PlatformContractClient.GlossaryResolveResult result = platformContractClient.resolveGlossaryTerms(termRefs);
        List<String> missing = result != null && result.missing() != null ? result.missing() : termRefs;
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("glossary terms are missing in platform: " + String.join(", ", missing));
        }
        List<String> ambiguous = result != null && result.ambiguous() != null ? result.ambiguous() : List.of();
        if (!ambiguous.isEmpty()) {
            throw new IllegalArgumentException("glossary terms are ambiguous in platform: " + String.join(", ", ambiguous));
        }
        List<String> inactive = new ArrayList<>();
        if (result.inactive() != null) {
            inactive.addAll(result.inactive());
        }
        if (result.terms() != null) {
            for (PlatformContractClient.GlossaryTermContract term : result.terms()) {
                if (term != null && !term.active() && StringUtils.hasText(term.ref()) && !inactive.contains(term.ref())) {
                    inactive.add(term.ref());
                }
            }
        }
        if (!inactive.isEmpty()) {
            throw new IllegalArgumentException("glossary terms are not ACTIVE in platform: " + String.join(", ", inactive));
        }
    }

    private static List<String> inlineGlossaryTermRefs(Map<String, Object> manifest) {
        Object rawMetrics = manifest.get("metrics");
        if (!(rawMetrics instanceof List<?> metrics)) {
            return List.of();
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (Object item : metrics) {
            if (!(item instanceof Map<?, ?> metric)) {
                continue;
            }
            Object terms = metric.get("term_ids") != null ? metric.get("term_ids") : metric.get("terms");
            if (!(terms instanceof List<?> termList)) {
                continue;
            }
            for (Object term : termList) {
                String ref = String.valueOf(term == null ? "" : term).trim();
                if (StringUtils.hasText(ref)) {
                    refs.add(ref);
                }
            }
        }
        return List.copyOf(refs);
    }

    private static List<String> platformDomainRefs(Map<String, Object> manifest) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        Object dependenciesRaw = manifest.get("dependencies");
        if (dependenciesRaw instanceof Map<?, ?> dependencies) {
            addRefs(refs, dependencies.get("platform_domains"), "domain_code", "code", "id", "domain");
        }
        addRefs(refs, manifest.get("domains"), "domain_code", "code", "id", "domain");
        if (refs.isEmpty()) {
            addRef(refs, manifest.get("industry"));
        }
        return List.copyOf(refs);
    }

    private static List<String> dataStandardRefs(Map<String, Object> manifest) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        Object dependenciesRaw = manifest.get("dependencies");
        if (dependenciesRaw instanceof Map<?, ?> dependencies) {
            addRefs(refs, dependencies.get("data_standards"), "standard_code", "code", "id", "data_standard", "standard");
            addRefs(refs, dependencies.get("standards"), "standard_code", "code", "id", "data_standard", "standard");
        }
        addRefs(refs, manifest.get("data_standards"), "standard_code", "code", "id", "data_standard", "standard");
        addRefs(refs, manifest.get("dimensions"), "standard_code", "code", "id", "data_standard", "standard");
        addRefs(refs, manifest.get("metrics"), "standard_code", "data_standard", "standard");
        return List.copyOf(refs);
    }

    private static void addRefs(Set<String> refs, Object raw, String... mapKeys) {
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addRefs(refs, item, mapKeys);
            }
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            for (String key : mapKeys) {
                addRef(refs, map.get(key));
            }
            return;
        }
        addRef(refs, raw);
    }

    private static void addRef(Set<String> refs, Object raw) {
        String ref = valueOf(raw);
        if (StringUtils.hasText(ref)) {
            refs.add(ref);
        }
    }

    private static boolean assetRefMatches(Object raw, String sourceModel) {
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return false;
        }
        String value = String.valueOf(raw).trim();
        if (value.equals(sourceModel)) {
            return true;
        }
        try {
            if (safeRefName(value).equals(sourceModel)) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            // Full platform asset keys may contain separators that are not dbt identifiers.
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.endsWith(":" + sourceModel)
            || normalized.endsWith("/" + sourceModel)
            || normalized.endsWith("/table:" + sourceModel)
            || normalized.endsWith("/model:" + sourceModel);
    }

    private static String safeRefName(String value) {
        String text = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return MetricFormulaSqlGenerator.safeIdentifier(text);
    }

    private static String firstText(Object first, Object second) {
        String value = valueOf(first);
        return StringUtils.hasText(value) ? value : valueOf(second);
    }

    private static String firstText(Object first, Object second, Object third) {
        String value = firstText(first, second);
        return StringUtils.hasText(value) ? value : valueOf(third);
    }

    private static String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record PreviewActor(String username, List<String> userRoles, String userDeptCode, String userClassification) {
        public static PreviewActor system() {
            return new PreviewActor("system", List.of("ROLE_ADMIN"), null, "INTERNAL");
        }
    }

    private record PlatformAssetDeclaration(String type, String id, String key, String assetClassification) {}

    private record MetricColumn(String code, String name, String sql) {}
}
