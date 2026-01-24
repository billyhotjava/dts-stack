package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultDestinationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDestinationSyncService.class);
    private static final List<String> DESTINATION_KEYWORDS = List.of("hive", "jdbc");
    private static final List<String> DESTINATION_BLOCKLIST = List.of(
        "vector",
        "milvus",
        "weaviate",
        "pinecone",
        "qdrant",
        "chroma",
        "embedding"
    );

    private final AdminInfraClient adminInfraClient;
    private final IngestionServiceClient ingestionServiceClient;

    public DefaultDestinationSyncService(AdminInfraClient adminInfraClient, IngestionServiceClient ingestionServiceClient) {
        this.adminInfraClient = adminInfraClient;
        this.ingestionServiceClient = ingestionServiceClient;
    }

    public DefaultDestinationSnapshot ensureDefaultDestination() {
        if (!ingestionServiceClient.isEnabled()) {
            return null;
        }
        AdminInfraClient.AdminDataLakeConfig lake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (lake == null) {
            return null;
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        if (destinationConfig.isEmpty()) {
            LOG.debug("Default data lake missing destination config; skip ingestion default destination sync");
            return null;
        }
        String definitionId = resolveDestinationDefinitionId(lake);
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName(), "dts-ods-destination");
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(definitionId, destinationName, destinationConfig);

        AdminInfraClient.AdminDataLakeDestinationUpdateRequest updateRequest =
            new AdminInfraClient.AdminDataLakeDestinationUpdateRequest(null, destinationName, definitionId, destinationConfig);
        adminInfraClient.updateDefaultDataLakeDestination(updateRequest);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destinationDefinitionId", definitionId);
        payload.put("destinationName", destinationName);
        payload.put("destinationConfig", destinationConfig);
        if (StringUtils.hasText(lake.getDestinationId())) {
            payload.put("destinationId", lake.getDestinationId());
        } else {
            payload.put("resetDestinationId", Boolean.TRUE);
        }
        ApiResponse<Map<String, Object>> response = ingestionServiceClient.updateDefaultDestination(payload);
        if (response == null || response.getStatus() != 200) {
            LOG.warn(
                "Failed to sync ingestion default destination: status={}, message={}",
                response == null ? null : response.getStatus(),
                response == null ? null : response.getMessage()
            );
        }
        return snapshot;
    }

    public void updateAdminDestinationIfNeeded(DefaultDestinationSnapshot snapshot, String destinationId) {
        if (snapshot == null || !StringUtils.hasText(destinationId)) {
            return;
        }
        AdminInfraClient.AdminDataLakeDestinationUpdateRequest request =
            new AdminInfraClient.AdminDataLakeDestinationUpdateRequest(
                destinationId,
                snapshot.destinationName(),
                snapshot.destinationDefinitionId(),
                snapshot.destinationConfig()
            );
        adminInfraClient.updateDefaultDataLakeDestination(request);
    }

    private Map<String, Object> resolveDestinationConfig(AdminInfraClient.AdminDataLakeConfig lake) {
        if (lake == null) {
            return Map.of();
        }
        Map<String, Object> payload = lake.getDestinationConfig() != null
            ? new LinkedHashMap<>(lake.getDestinationConfig())
            : new LinkedHashMap<>();
        String jdbcUrl = lake.getJdbcUrl();
        putIfMissing(payload, "jdbc_url", jdbcUrl);
        putIfMissing(payload, "username", lake.getUsername());
        putIfMissing(payload, "password", lake.getPassword());
        String database = extractDatabase(jdbcUrl);
        putIfMissing(payload, "database", database);
        putIfMissing(payload, "schema", database);
        JdbcParts parts = parseJdbc(jdbcUrl);
        if (StringUtils.hasText(parts.host())) {
            payload.putIfAbsent("host", parts.host());
        }
        if (parts.port() != null) {
            payload.putIfAbsent("port", parts.port());
        }
        if (lake.getJdbcProperties() != null && !lake.getJdbcProperties().isEmpty()) {
            payload.put("jdbc_properties", lake.getJdbcProperties());
        }
        return payload;
    }

    private JdbcParts parseJdbc(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return new JdbcParts(null, null);
        }
        try {
            String trimmed = jdbcUrl.trim();
            int idx = trimmed.indexOf("://");
            String rest = idx > 0 ? trimmed.substring(idx + 3) : trimmed;
            // strip path/query/params
            String hostPort = rest;
            int slash = hostPort.indexOf('/');
            if (slash >= 0) {
                hostPort = hostPort.substring(0, slash);
            }
            int semicolon = hostPort.indexOf(';');
            if (semicolon >= 0) {
                hostPort = hostPort.substring(0, semicolon);
            }
            int question = hostPort.indexOf('?');
            if (question >= 0) {
                hostPort = hostPort.substring(0, question);
            }
            String[] parts = hostPort.split(":", 2);
            String host = parts[0];
            if (!StringUtils.hasText(host)) {
                host = null;
            }
            Integer port = null;
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
            if (port == null) {
                if (trimmed.toLowerCase(Locale.ROOT).contains("postgres")) {
                    port = 5432;
                } else if (trimmed.toLowerCase(Locale.ROOT).contains("mysql")) {
                    port = 3306;
                }
            }
            return new JdbcParts(host, port);
        } catch (Exception ex) {
            return new JdbcParts(null, null);
        }
    }

    private record JdbcParts(String host, Integer port) {}

    private void putIfMissing(Map<String, Object> payload, String key, String value) {
        if (!payload.containsKey(key)) {
            putIfText(payload, key, value);
        }
    }

    private String resolveDestinationDefinitionId(AdminInfraClient.AdminDataLakeConfig lake) {
        ApiResponse<List<Map<String, Object>>> response = ingestionServiceClient.listDestinationDefinitions();
        if (response == null || response.getStatus() != 200 || response.getData() == null) {
            return null;
        }
        List<Map<String, Object>> defs = response.getData();
        List<DefinitionMeta> candidates = defs.stream().map(DefinitionMeta::from).toList();
        List<String> preferred = buildPreferredKeywords(lake);
        String existingId = lake == null ? null : lake.getDestinationDefinitionId();
        DefinitionMeta existing = findById(candidates, existingId);
        if (existing != null && isAcceptable(existing, preferred)) {
            return existing.id();
        }
        String resolved = findDefinitionIdByKeywords(candidates, preferred);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (existing != null && !existing.blocked()) {
            return existing.id();
        }
        return null;
    }

    private List<String> buildPreferredKeywords(AdminInfraClient.AdminDataLakeConfig lake) {
        String type = lake == null ? "" : normalize(lake.getType());
        String jdbcUrl = lake == null ? "" : normalize(lake.getJdbcUrl());
        String combined = (type + " " + jdbcUrl).toLowerCase(Locale.ROOT);
        List<String> preferred = new java.util.ArrayList<>();
        if (containsAny(combined, "hive", "inceptor")) {
            preferred.add("hive");
            preferred.add("jdbc");
        }
        if (containsAny(combined, "iceberg")) {
            preferred.add("iceberg");
        }
        if (containsAny(combined, "postgres", "pgsql")) {
            preferred.add("postgres");
            preferred.add("jdbc");
        }
        if (containsAny(combined, "mysql", "mariadb")) {
            preferred.add("mysql");
            preferred.add("jdbc");
        }
        if (containsAny(combined, "oracle")) {
            preferred.add("oracle");
            preferred.add("jdbc");
        }
        if (containsAny(combined, "sqlserver", "mssql")) {
            preferred.add("sqlserver");
            preferred.add("jdbc");
        }
        if (containsAny(combined, "clickhouse")) {
            preferred.add("clickhouse");
            preferred.add("jdbc");
        }
        if (preferred.isEmpty() && jdbcUrl.contains("jdbc")) {
            preferred.add("jdbc");
        }
        if (preferred.isEmpty()) {
            preferred.addAll(DESTINATION_KEYWORDS);
        }
        return preferred;
    }

    private String findDefinitionIdByKeywords(List<DefinitionMeta> candidates, List<String> keywords) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<String> normalized = keywords == null ? List.of() : keywords.stream().filter(StringUtils::hasText).toList();
        for (String keyword : normalized) {
            String repoHint = "destination-" + keyword.toLowerCase(Locale.ROOT);
            for (DefinitionMeta meta : candidates) {
                if (meta.blocked()) {
                    continue;
                }
                if (meta.repo().contains(repoHint)) {
                    return meta.id();
                }
            }
        }
        for (String keyword : normalized) {
            for (DefinitionMeta meta : candidates) {
                if (meta.blocked()) {
                    continue;
                }
                if (meta.haystack().contains(keyword.toLowerCase(Locale.ROOT))) {
                    return meta.id();
                }
            }
        }
        return null;
    }

    private DefinitionMeta findById(List<DefinitionMeta> candidates, String id) {
        if (!StringUtils.hasText(id) || candidates == null) {
            return null;
        }
        String needle = id.trim();
        for (DefinitionMeta meta : candidates) {
            if (needle.equals(meta.id())) {
                return meta;
            }
        }
        return null;
    }

    private boolean isAcceptable(DefinitionMeta meta, List<String> preferred) {
        if (meta == null || meta.blocked()) {
            return false;
        }
        if (preferred == null || preferred.isEmpty()) {
            return true;
        }
        for (String keyword : preferred) {
            if (StringUtils.hasText(keyword) && meta.haystack().contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty() || needles == null) {
            return false;
        }
        String lower = haystack.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (StringUtils.hasText(needle) && lower.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record DefinitionMeta(String id, String name, String repo, String haystack, boolean blocked) {
        static DefinitionMeta from(Map<String, Object> def) {
            String id = def == null ? null : stringValStatic(def.get("destinationDefinitionId"));
            String name = def == null ? "" : normalizeStatic(def.get("name"));
            String repo = def == null ? "" : normalizeStatic(def.get("dockerRepository"));
            String hay = (name + " " + repo).toLowerCase(Locale.ROOT);
            boolean blocked = DESTINATION_BLOCKLIST.stream().anyMatch(hay::contains);
            return new DefinitionMeta(id, name, repo, hay, blocked);
        }
    }

    private static String stringValStatic(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeStatic(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String extractDatabase(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        int scheme = jdbcUrl.indexOf("://");
        int start = scheme > -1 ? jdbcUrl.indexOf("/", scheme + 3) : jdbcUrl.indexOf("/");
        if (start < 0 || start + 1 >= jdbcUrl.length()) {
            return null;
        }
        String tail = jdbcUrl.substring(start + 1);
        int cut = tail.indexOf("?");
        if (cut < 0) {
            cut = tail.indexOf(";");
        }
        if (cut > -1) {
            tail = tail.substring(0, cut);
        }
        String trimmed = tail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void putIfText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value.trim());
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonEmpty(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private String firstNonEmpty(String first, String second, String third) {
        String resolved = firstNonEmpty(first, second);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (StringUtils.hasText(third)) {
            return third.trim();
        }
        return null;
    }

    public record DefaultDestinationSnapshot(
        String destinationDefinitionId,
        String destinationName,
        Map<String, Object> destinationConfig
    ) {
        public DefaultDestinationSnapshot {
            destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
        }

        public boolean isEmpty() {
            return !StringUtils.hasText(destinationDefinitionId)
                && !StringUtils.hasText(destinationName)
                && (destinationConfig == null || destinationConfig.isEmpty());
        }
    }
}
