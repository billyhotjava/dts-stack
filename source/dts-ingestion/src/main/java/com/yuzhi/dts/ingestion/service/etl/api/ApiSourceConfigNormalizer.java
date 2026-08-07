package com.yuzhi.dts.ingestion.service.etl.api;

import com.yuzhi.dts.ingestion.config.ApiProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

public final class ApiSourceConfigNormalizer {

    private static final List<String> NESTED_RUNTIME_KEYS = List.of(
        "baseUrl",
        "baseURL",
        "authProvider",
        "auth",
        "defaultHeaders",
        "requestPolicy",
        "rateLimit",
        "tls",
        "resource",
        "resources",
        "path",
        "resourceId",
        "displayName",
        "method",
        "query",
        "bodyTemplate",
        "recordPath",
        "pagination",
        "cursor",
        "targetTable",
        "landing",
        "schemaSnapshot",
        "stagingFields"
    );

    private ApiSourceConfigNormalizer() {}

    public static Map<String, Object> normalize(
        Map<String, Object> sourceConfig,
        UUID sourceDataSourceId,
        String fallbackSourceName
    ) {
        return normalize(sourceConfig, sourceDataSourceId, fallbackSourceName, ApiProperties.DEFAULT_TABLE_PREFIX);
    }

    public static Map<String, Object> normalize(
        Map<String, Object> sourceConfig,
        UUID sourceDataSourceId,
        String fallbackSourceName,
        String tablePrefix
    ) {
        Map<String, Object> normalized = safeMap(sourceConfig);
        promoteNestedRuntimeConfig(normalized);
        normalized.put("connectorType", ApiConnectorTypes.CONNECTOR_TYPE);
        normalized.put("readerType", ApiConnectorTypes.DEFAULT_READER_TYPE);
        normalized.put("sourceCategory", ApiConnectorTypes.CONNECTOR_TYPE);
        normalized.remove("fields");

        List<Map<String, Object>> resources = extractResources(normalized);
        if (resources.isEmpty()) {
            return normalized;
        }

        String sourceKey = resolveSourceKey(normalized, sourceDataSourceId, fallbackSourceName);
        List<Map<String, Object>> normalizedResources = new ArrayList<>(resources.size());
        for (Map<String, Object> resource : resources) {
            normalizedResources.add(normalizeResource(resource, sourceKey, tablePrefix));
        }
        normalized.put("resource", normalizedResources.get(0));
        normalized.put("resources", normalizedResources);
        return normalized;
    }

    public static List<String> resolveResourceIds(Map<String, Object> normalizedSourceConfig) {
        List<Map<String, Object>> resources = extractResources(safeMap(normalizedSourceConfig));
        if (resources.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(resources.size());
        for (Map<String, Object> resource : resources) {
            String id = firstText(resource, "resourceId", "id", "name");
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public static List<Map<String, String>> deriveOdsMappings(
        Map<String, Object> normalizedSourceConfig,
        UUID sourceDataSourceId,
        String fallbackSourceName
    ) {
        return deriveOdsMappings(
            normalizedSourceConfig,
            sourceDataSourceId,
            fallbackSourceName,
            ApiProperties.DEFAULT_TABLE_PREFIX
        );
    }

    public static List<Map<String, String>> deriveOdsMappings(
        Map<String, Object> normalizedSourceConfig,
        UUID sourceDataSourceId,
        String fallbackSourceName,
        String tablePrefix
    ) {
        Map<String, Object> normalized = normalize(normalizedSourceConfig, sourceDataSourceId, fallbackSourceName, tablePrefix);
        List<Map<String, Object>> resources = extractResources(normalized);
        if (resources.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> mappings = new ArrayList<>(resources.size());
        for (Map<String, Object> resource : resources) {
            String resourceId = firstText(resource, "resourceId", "id", "name");
            String targetTable = firstText(resource, "targetTable");
            if (!StringUtils.hasText(resourceId) || !StringUtils.hasText(targetTable)) {
                continue;
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            mapping.put("source", resourceId);
            mapping.put("target", targetTable);
            mapping.put("landingMode", ApiSourceContracts.ODS_LANDING_MODE);
            mapping.put("rawRecordColumn", ApiSourceContracts.RAW_RECORD_COLUMN);
            mappings.add(mapping);
        }
        return List.copyOf(mappings);
    }

    private static void promoteNestedRuntimeConfig(Map<String, Object> sourceConfig) {
        Map<String, Object> apiNode = asMap(sourceConfig.get("api"));
        Map<String, Object> readerNode = asMap(sourceConfig.get("readerConfig"));
        // Older task snapshots embed the whole datasource props under "props";
        // consult those nested nodes as well so runtime policies survive round-trips.
        Map<String, Object> propsNode = asMap(sourceConfig.get("props"));
        Map<String, Object> propsApiNode = asMap(propsNode == null ? null : propsNode.get("api"));
        Map<String, Object> propsReaderNode = asMap(propsNode == null ? null : propsNode.get("readerConfig"));
        for (String key : NESTED_RUNTIME_KEYS) {
            if (sourceConfig.containsKey(key) && sourceConfig.get(key) != null) {
                continue;
            }
            Object value = apiNode.get(key);
            if (value == null) {
                value = readerNode.get(key);
            }
            if (value == null) {
                value = propsNode == null ? null : propsNode.get(key);
            }
            if (value == null) {
                value = propsApiNode.get(key);
            }
            if (value == null) {
                value = propsReaderNode.get(key);
            }
            if (value != null) {
                sourceConfig.put(key, value);
            }
        }
    }

    private static Map<String, Object> normalizeResource(Map<String, Object> resource, String sourceKey, String tablePrefix) {
        Map<String, Object> normalized = safeMap(resource);
        normalized.remove("fields");

        String resourceId = firstText(normalized, "resourceId", "id", "name");
        if (!StringUtils.hasText(resourceId)) {
            resourceId = normalizeIdentifier(firstText(normalized, "path"));
        }
        if (!StringUtils.hasText(resourceId)) {
            resourceId = "api_resource";
        }
        normalized.put("resourceId", resourceId);

        String targetTable = normalizeTableName(firstText(normalized, "targetTable"));
        if (!StringUtils.hasText(targetTable)) {
            targetTable = buildOdsTableName(tablePrefix, sourceKey, resourceId);
        }
        normalized.put("targetTable", targetTable);
        normalized.put("landing", normalizeLanding(normalized.get("landing")));
        normalized.put("schemaSnapshot", normalizeSchemaSnapshot(normalized.get("schemaSnapshot")));
        return normalized;
    }

    private static Map<String, Object> normalizeLanding(Object raw) {
        Map<String, Object> landing = asMap(raw);
        landing.put("mode", ApiSourceContracts.ODS_LANDING_MODE);
        landing.put("rawRecordColumn", ApiSourceContracts.RAW_RECORD_COLUMN);
        landing.put("technicalColumns", ApiSourceContracts.TECHNICAL_COLUMNS);
        return landing;
    }

    private static Map<String, Object> normalizeSchemaSnapshot(Object raw) {
        Map<String, Object> snapshot = asMap(raw);
        snapshot.putIfAbsent("enabled", Boolean.TRUE);
        snapshot.putIfAbsent("driftPolicy", ApiSourceContracts.DEFAULT_SCHEMA_DRIFT_POLICY);
        return snapshot;
    }

    private static List<Map<String, Object>> extractResources(Map<String, Object> sourceConfig) {
        if (sourceConfig == null || sourceConfig.isEmpty()) {
            return List.of();
        }
        Object rawResources = sourceConfig.get("resources");
        List<Map<String, Object>> resources = new ArrayList<>();
        if (rawResources instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> resource = asMap(item);
                if (!resource.isEmpty()) {
                    resources.add(resource);
                }
            }
        }
        if (!resources.isEmpty()) {
            return resources;
        }
        Map<String, Object> resource = asMap(sourceConfig.get("resource"));
        if (!resource.isEmpty()) {
            return List.of(resource);
        }
        if (StringUtils.hasText(firstText(sourceConfig, "path", "resourceId"))) {
            Map<String, Object> rootResource = new LinkedHashMap<>();
            copyIfPresent(sourceConfig, rootResource, "resourceId");
            copyIfPresent(sourceConfig, rootResource, "displayName");
            copyIfPresent(sourceConfig, rootResource, "path");
            copyIfPresent(sourceConfig, rootResource, "method");
            copyIfPresent(sourceConfig, rootResource, "query");
            copyIfPresent(sourceConfig, rootResource, "bodyTemplate");
            copyIfPresent(sourceConfig, rootResource, "recordPath");
            copyIfPresent(sourceConfig, rootResource, "pagination");
            copyIfPresent(sourceConfig, rootResource, "cursor");
            copyIfPresent(sourceConfig, rootResource, "targetTable");
            copyIfPresent(sourceConfig, rootResource, "landing");
            copyIfPresent(sourceConfig, rootResource, "schemaSnapshot");
            copyIfPresent(sourceConfig, rootResource, "stagingFields");
            return List.of(rootResource);
        }
        return List.of();
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private static String resolveSourceKey(Map<String, Object> sourceConfig, UUID sourceDataSourceId, String fallbackSourceName) {
        String source = firstText(sourceConfig, "sourceSystem", "sourceApp", "appCode", "system", "sourceName");
        if (!StringUtils.hasText(source)) {
            source = fallbackSourceName;
        }
        String normalized = normalizeIdentifier(source);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (sourceDataSourceId != null) {
            String compact = sourceDataSourceId.toString().replace("-", "");
            if (compact.length() >= 8) {
                return "src_" + compact.substring(0, 8);
            }
        }
        return "api";
    }

    private static String buildOdsTableName(String tablePrefix, String sourceKey, String resourceId) {
        String source = normalizeIdentifier(sourceKey);
        if (!StringUtils.hasText(source)) {
            source = "api";
        }
        String resource = normalizeIdentifier(resourceId);
        if (!StringUtils.hasText(resource)) {
            resource = "resource";
        }
        return tablePrefix(tablePrefix) + source + "_" + resource;
    }

    private static String tablePrefix(String tablePrefix) {
        return StringUtils.hasText(tablePrefix) ? tablePrefix.trim() : ApiProperties.DEFAULT_TABLE_PREFIX;
    }

    private static String normalizeTableName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.trim().split("\\.");
        List<String> normalized = new ArrayList<>(parts.length);
        for (String part : parts) {
            String item = normalizeIdentifier(part);
            if (!StringUtils.hasText(item)) {
                return null;
            }
            normalized.add(item);
        }
        return String.join(".", normalized);
    }

    private static String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String safe = value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_")
            .replaceAll("^_+|_+$", "")
            .replaceAll("_+", "_");
        if (!StringUtils.hasText(safe)) {
            return null;
        }
        if (Character.isDigit(safe.charAt(0))) {
            safe = "col_" + safe;
        }
        return safe;
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private static Map<String, Object> asMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String firstText(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }
}
