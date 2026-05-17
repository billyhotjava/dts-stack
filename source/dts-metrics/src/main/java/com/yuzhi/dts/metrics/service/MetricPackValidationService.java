package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final Set<String> ALLOWED_EDITIONS = Set.of("foundation", "professional", "enterprise");
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

        if (StringUtils.hasText(packId) && !PACK_ID_PATTERN.matcher(packId).matches()) {
            errors.add("pack_id must be lowercase ASCII letters, numbers, hyphen or underscore, and start with a letter");
        }
        if (StringUtils.hasText(version) && !VERSION_PATTERN.matcher(version).matches()) {
            errors.add("version must use semantic version format, for example 0.1.0");
        }
        if (StringUtils.hasText(editionRequired) && !ALLOWED_EDITIONS.contains(editionRequired.toLowerCase(Locale.ROOT))) {
            errors.add("edition_required must be one of foundation, professional, enterprise");
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
        int platformAssetCount = validatePlatformAssets(manifest, errors);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("packId", manifest.getOrDefault("pack_id", ""));
        summary.put("version", manifest.getOrDefault("version", ""));
        summary.put("industry", manifest.getOrDefault("industry", ""));
        summary.put("editionRequired", manifest.getOrDefault("edition_required", ""));
        summary.put("fileCount", files instanceof Map<?, ?> fileMap ? fileMap.size() : 0);
        summary.put("platformAssetCount", platformAssetCount);

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

    private static int validatePlatformAssets(Map<String, Object> manifest, List<String> errors) {
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
            if (!StringUtils.hasText(type)) {
                errors.add("dependencies.platform_assets[" + i + "].type is required");
            } else if (!type.matches("^[A-Z_]+$")) {
                errors.add("dependencies.platform_assets[" + i + "].type must be an uppercase asset type");
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
        }
        return assets.size();
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
