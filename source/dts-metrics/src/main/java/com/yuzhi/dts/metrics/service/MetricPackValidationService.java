package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricPackValidationService {

    private static final Pattern PACK_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$");
    private static final Pattern ASSET_REF_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]+$");
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$");
    private static final Set<String> ALLOWED_EDITIONS = Set.of("foundation", "professional", "enterprise");
    private static final Set<String> ALLOWED_METRIC_PACK_ASSET_TYPES = Set.of(
        "DATASET",
        "DBT_MODEL",
        "BI_DATASET",
        "METRIC",
        "SEMANTIC_MODEL",
        "GLOSSARY_TERM"
    );
    private static final List<String> REQUIRED_FILES = List.of("domains", "business_objects", "dimensions", "metrics", "models", "datasets");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public MetricPackValidationResult validateManifest(String manifestContent) {
        if (!StringUtils.hasText(manifestContent)) {
            return MetricPackValidationResult.invalid(List.of("manifest.yml is empty"), Map.of());
        }
        Map<String, Object> manifest;
        try {
            manifest = yamlMapper.readValue(manifestContent, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            return MetricPackValidationResult.invalid(List.of("manifest.yml is not valid YAML: " + e.getMessage()), Map.of());
        }

        List<String> errors = new ArrayList<>();
        String packId = requireString(manifest, "pack_id", errors);
        requireString(manifest, "pack_name", errors);
        String version = requireString(manifest, "version", errors);
        requireString(manifest, "industry", errors);
        String editionRequired = requireString(manifest, "edition_required", errors);
        requireMap(manifest, "files", errors);
        requireMap(manifest, "dependencies", errors);
        String tenantNamespace = firstText(manifest.get("tenant_namespace"), manifest.get("tenant"));

        if (StringUtils.hasText(packId) && !PACK_ID_PATTERN.matcher(packId).matches()) {
            errors.add("pack_id must be lowercase ASCII letters, numbers, hyphen or underscore, and start with a letter");
        }
        if (StringUtils.hasText(version) && !VERSION_PATTERN.matcher(version).matches()) {
            errors.add("version must use semantic version format, for example 0.1.0");
        }
        if (StringUtils.hasText(editionRequired) && !ALLOWED_EDITIONS.contains(editionRequired.toLowerCase(Locale.ROOT))) {
            errors.add("edition_required must be one of foundation, professional, enterprise");
        }
        if (StringUtils.hasText(tenantNamespace) && !NAMESPACE_PATTERN.matcher(tenantNamespace).matches()) {
            errors.add("tenant_namespace must be lowercase ASCII letters, numbers, hyphen or underscore, and start with a letter");
        }

        Object files = manifest.get("files");
        if (files instanceof Map<?, ?> fileMap) {
            for (String key : REQUIRED_FILES) {
                Object value = fileMap.get(key);
                String path = value != null ? String.valueOf(value).trim() : "";
                if (!StringUtils.hasText(path)) {
                    errors.add("files." + key + " is required");
                    continue;
                }
                if (!isSafeRelativePackPath(path)) {
                    errors.add("files." + key + " must be a relative .yml/.yaml/.json file path inside the metric pack");
                }
            }
        }

        if (containsRawSql(manifest)) {
            errors.add("raw_sql is not allowed in metric-pack v0.1");
        }
        int platformAssetCount = validatePlatformAssets(manifest, tenantNamespace, errors);
        int packDependencyCount = validatePackDependencies(manifest, errors);
        Set<String> declaredGlossaryTerms = declaredPlatformAssetRefs(manifest, "GLOSSARY_TERM");
        int inlineMetricCount = validateInlineMetrics(manifest, declaredGlossaryTerms, errors);
        validateSecurityPolicy(manifest, platformAssetCount, errors);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("packId", manifest.getOrDefault("pack_id", ""));
        summary.put("version", manifest.getOrDefault("version", ""));
        summary.put("industry", manifest.getOrDefault("industry", ""));
        summary.put("editionRequired", manifest.getOrDefault("edition_required", ""));
        summary.put("tenantNamespace", tenantNamespace);
        summary.put("fileCount", files instanceof Map<?, ?> fileMap ? fileMap.size() : 0);
        summary.put("platformAssetCount", platformAssetCount);
        summary.put("packDependencyCount", packDependencyCount);
        summary.put("inlineMetricCount", inlineMetricCount);

        return errors.isEmpty() ? MetricPackValidationResult.valid(summary) : MetricPackValidationResult.invalid(errors, summary);
    }

    private static String requireString(Map<String, Object> manifest, String key, List<String> errors) {
        Object value = manifest.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            errors.add(key + " is required");
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static void requireMap(Map<String, Object> manifest, String key, List<String> errors) {
        if (!(manifest.get(key) instanceof Map<?, ?>)) {
            errors.add(key + " must be an object");
        }
    }

    private static boolean containsRawSql(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("rawsql".equals(normalizeKey(entry.getKey()))) {
                    return true;
                }
                if (containsRawSql(entry.getValue())) {
                    return true;
                }
            }
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsRawSql(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int validatePlatformAssets(Map<String, Object> manifest, String tenantNamespace, List<String> errors) {
        Object dependenciesRaw = manifest.get("dependencies");
        if (!(dependenciesRaw instanceof Map<?, ?> dependencies)) {
            return 0;
        }
        Object assetsRaw = dependencies.get("platform_assets");
        if (!(assetsRaw instanceof List<?> assets)) {
            return 0;
        }
        for (int i = 0; i < assets.size(); i++) {
            Object item = assets.get(i);
            if (!(item instanceof Map<?, ?> asset)) {
                errors.add("dependencies.platform_assets[" + i + "] must be an object");
                continue;
            }
            String type = firstText(asset.get("type"), asset.get("asset_type"));
            String id = firstText(asset.get("id"), asset.get("asset_code"));
            String key = firstText(asset.get("key"), asset.get("asset_key"));
            String ownerNamespace = firstText(asset.get("owner_namespace"), asset.get("tenant_namespace"));
            if (!StringUtils.hasText(type)) {
                errors.add("dependencies.platform_assets[" + i + "].type is required");
            } else if (!ALLOWED_METRIC_PACK_ASSET_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
                errors.add("dependencies.platform_assets[" + i + "].type is not allowed for metric-pack references");
            }
            if (!StringUtils.hasText(id) && !StringUtils.hasText(key)) {
                errors.add("dependencies.platform_assets[" + i + "] must declare id or key");
            }
            if (StringUtils.hasText(id) && !ASSET_REF_PATTERN.matcher(id).matches()) {
                errors.add("dependencies.platform_assets[" + i + "].id must be a safe asset reference");
            }
            if (StringUtils.hasText(key) && !ASSET_REF_PATTERN.matcher(key).matches()) {
                errors.add("dependencies.platform_assets[" + i + "].key must be a safe asset reference");
            }
            if (!StringUtils.hasText(tenantNamespace) && !StringUtils.hasText(ownerNamespace)) {
                errors.add("dependencies.platform_assets[" + i + "] must declare owner_namespace or inherit top-level tenant_namespace");
            }
            if (StringUtils.hasText(ownerNamespace) && !NAMESPACE_PATTERN.matcher(ownerNamespace).matches()) {
                errors.add("dependencies.platform_assets[" + i + "].owner_namespace must be lowercase ASCII letters, numbers, hyphen or underscore");
            }
        }
        return assets.size();
    }

    private static Set<String> declaredPlatformAssetRefs(Map<String, Object> manifest, String assetType) {
        Object dependenciesRaw = manifest.get("dependencies");
        if (!(dependenciesRaw instanceof Map<?, ?> dependencies)) {
            return Set.of();
        }
        Object assetsRaw = dependencies.get("platform_assets");
        if (!(assetsRaw instanceof List<?> assets)) {
            return Set.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        for (Object item : assets) {
            if (!(item instanceof Map<?, ?> asset)) {
                continue;
            }
            String type = firstText(asset.get("type"), asset.get("asset_type"));
            if (!assetType.equalsIgnoreCase(type)) {
                continue;
            }
            addRef(refs, asset.get("id"));
            addRef(refs, asset.get("asset_code"));
            addRef(refs, asset.get("key"));
            addRef(refs, asset.get("asset_key"));
        }
        return refs;
    }

    private static void addRef(Set<String> refs, Object raw) {
        String value = valueOf(raw);
        if (StringUtils.hasText(value)) {
            refs.add(value);
        }
    }

    private static int validatePackDependencies(Map<String, Object> manifest, List<String> errors) {
        Object dependenciesRaw = manifest.get("dependencies");
        if (!(dependenciesRaw instanceof Map<?, ?> dependencies)) {
            return 0;
        }
        Object dependenciesList = dependencies.get("pack_dependencies");
        if (!(dependenciesList instanceof List<?> packs)) {
            return 0;
        }
        for (int i = 0; i < packs.size(); i++) {
            Object item = packs.get(i);
            if (!(item instanceof Map<?, ?> pack)) {
                errors.add("dependencies.pack_dependencies[" + i + "] must be an object");
                continue;
            }
            String packId = firstText(pack.get("pack_id"), pack.get("id"));
            String version = firstText(pack.get("version"), pack.get("version_constraint"));
            if (!StringUtils.hasText(packId) || !PACK_ID_PATTERN.matcher(packId).matches()) {
                errors.add("dependencies.pack_dependencies[" + i + "].pack_id must be a safe pack id");
            }
            if (!StringUtils.hasText(version)) {
                errors.add("dependencies.pack_dependencies[" + i + "].version or version_constraint is required");
            } else if (!version.matches("^[0-9A-Za-z.*+<>=~^|, -]+$")) {
                errors.add("dependencies.pack_dependencies[" + i + "].version must be a safe semantic version constraint");
            }
        }
        return packs.size();
    }

    private static int validateInlineMetrics(Map<String, Object> manifest, Set<String> declaredGlossaryTerms, List<String> errors) {
        Object rawMetrics = manifest.get("metrics");
        if (!(rawMetrics instanceof List<?> metrics)) {
            return 0;
        }
        for (int i = 0; i < metrics.size(); i++) {
            Object item = metrics.get(i);
            if (!(item instanceof Map<?, ?> metric)) {
                errors.add("metrics[" + i + "] must be an object");
                continue;
            }
            Object terms = metric.get("term_ids") != null ? metric.get("term_ids") : metric.get("terms");
            if (!(terms instanceof List<?> termList) || termList.isEmpty()) {
                errors.add("metrics[" + i + "] must bind at least one glossary term via term_ids");
            } else {
                for (int j = 0; j < termList.size(); j++) {
                    String term = valueOf(termList.get(j));
                    if (!StringUtils.hasText(term) || !ASSET_REF_PATTERN.matcher(term).matches()) {
                        errors.add("metrics[" + i + "].term_ids[" + j + "] must be a safe glossary term reference");
                    } else if (!isDeclaredAssetRef(term, declaredGlossaryTerms)) {
                        errors.add("metrics[" + i + "].term_ids[" + j + "] must be declared as GLOSSARY_TERM in dependencies.platform_assets");
                    }
                }
            }
        }
        return metrics.size();
    }

    private static void validateSecurityPolicy(Map<String, Object> manifest, int platformAssetCount, List<String> errors) {
        if (platformAssetCount == 0) {
            return;
        }
        Object securityRaw = manifest.get("security");
        if (!(securityRaw instanceof Map<?, ?> security)) {
            errors.add("security.apply_rls must be declared as a boolean when platform assets are referenced");
            return;
        }
        Object applyRls = security.get("apply_rls");
        if (!(applyRls instanceof Boolean)) {
            errors.add("security.apply_rls must be declared as a boolean when platform assets are referenced");
        }
    }

    private static boolean isDeclaredAssetRef(String expected, Set<String> declaredRefs) {
        for (String ref : declaredRefs) {
            String normalized = ref.trim();
            if (normalized.equals(expected)
                || normalized.endsWith(":" + expected)
                || normalized.endsWith("/" + expected)
                || normalized.endsWith("/glossary_term:" + expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSafeRelativePackPath(String path) {
        String value = path.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        return !value.startsWith("/")
            && !value.startsWith("~")
            && !value.contains("\\")
            && !value.contains("..")
            && !lower.matches("^[a-z][a-z0-9+.-]*:.*")
            && (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".json"));
    }

    private static String normalizeKey(Object key) {
        return String.valueOf(key).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstText(Object first, Object second) {
        String value = valueOf(first);
        return StringUtils.hasText(value) ? value : valueOf(second);
    }
}
