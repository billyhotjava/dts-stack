package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricPackValidationService {

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
        requireString(manifest, "pack_id", errors);
        requireString(manifest, "pack_name", errors);
        requireString(manifest, "version", errors);
        requireString(manifest, "industry", errors);
        requireString(manifest, "edition_required", errors);
        requireMap(manifest, "files", errors);
        requireMap(manifest, "dependencies", errors);

        Object files = manifest.get("files");
        if (files instanceof Map<?, ?> fileMap) {
            for (String key : List.of("domains", "business_objects", "dimensions", "metrics", "models", "datasets")) {
                if (!fileMap.containsKey(key) || !StringUtils.hasText(String.valueOf(fileMap.get(key)))) {
                    errors.add("files." + key + " is required");
                }
            }
        }

        if (containsRawSql(manifest)) {
            errors.add("raw_sql is not allowed in metric-pack v0.1");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("packId", manifest.getOrDefault("pack_id", ""));
        summary.put("version", manifest.getOrDefault("version", ""));
        summary.put("industry", manifest.getOrDefault("industry", ""));
        summary.put("editionRequired", manifest.getOrDefault("edition_required", ""));
        summary.put("fileCount", files instanceof Map<?, ?> fileMap ? fileMap.size() : 0);

        return errors.isEmpty() ? MetricPackValidationResult.valid(summary) : MetricPackValidationResult.invalid(errors, summary);
    }

    private static void requireString(Map<String, Object> manifest, String key, List<String> errors) {
        Object value = manifest.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            errors.add(key + " is required");
        }
    }

    private static void requireMap(Map<String, Object> manifest, String key, List<String> errors) {
        if (!(manifest.get(key) instanceof Map<?, ?>)) {
            errors.add(key + " must be an object");
        }
    }

    private static boolean containsRawSql(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("raw_sql".equals(String.valueOf(entry.getKey()))) {
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
}
