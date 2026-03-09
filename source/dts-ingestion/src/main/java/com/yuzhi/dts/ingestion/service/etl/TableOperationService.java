package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TableOperationService {

    private static final Logger LOG = LoggerFactory.getLogger(TableOperationService.class);

    private final JdbcMetadataService metadataService;
    private final AddaxJobService addaxJobService;
    private final ObjectMapper objectMapper;

    public TableOperationService(JdbcMetadataService metadataService, AddaxJobService addaxJobService, ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.addaxJobService = addaxJobService;
        this.objectMapper = objectMapper;
    }

    /**
     * Build JDBC connection info from task's destinationConfig.
     * Replicates the pattern from TargetTableProvisioner.buildConnectionInfo().
     */
    public JdbcMetadataService.JdbcConnectionInfo resolveTargetConnectionInfo(IngestionTask task) {
        Map<String, Object> config = jsonNodeToMap(task.getDestinationConfig());
        String jdbcUrl = appendPostgresSslDisable(resolveJdbcUrl(config));
        String username = normalizeText(config.get("username"));
        String password = normalizeText(config.get("password"));
        String driverClass = normalizeText(config.get("driver"));
        if (!StringUtils.hasText(driverClass)) {
            driverClass = normalizeText(config.get("driverClass"));
        }
        String driverVersion = normalizeText(config.get("driverVersion"));
        Map<String, String> jdbcProps = resolveJdbcProperties(config);
        return new JdbcMetadataService.JdbcConnectionInfo(jdbcUrl, username, password, driverClass, driverVersion, jdbcProps);
    }

    /**
     * TRUNCATE specified table. 30s timeout.
     */
    public void truncateTable(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table) {
        String qualified = StringUtils.hasText(schema) ? schema + "." + table : table;
        LOG.info("[rollback] TRUNCATE TABLE {}", qualified);
        try (Connection conn = metadataService.openConnection(connInfo);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            stmt.execute("TRUNCATE TABLE " + qualified);
        } catch (Exception ex) {
            throw new RuntimeException("TRUNCATE failed for " + qualified + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * DROP specified table with CASCADE. 30s timeout.
     */
    public void dropTable(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table) {
        String qualified = StringUtils.hasText(schema) ? schema + "." + table : table;
        LOG.info("[rollback] DROP TABLE IF EXISTS {} CASCADE", qualified);
        try (Connection conn = metadataService.openConnection(connInfo);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            stmt.execute("DROP TABLE IF EXISTS " + qualified + " CASCADE");
        } catch (Exception ex) {
            throw new RuntimeException("DROP failed for " + qualified + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Resolve all target tables for a task.
     * Priority: Addax job file > tableMapping field.
     */
    public List<String> resolveTargetTables(IngestionTask task) {
        // 1. Try Addax job file
        List<String> tables = addaxJobService.listWriterTablesFromJob(task.getAddaxJobPath());
        if (!tables.isEmpty()) {
            return applySchema(tables, task);
        }

        // 2. Fallback: parse tableMapping
        List<String> targets = parseTargetTablesFromMapping(task.getTableMapping());
        if (!targets.isEmpty()) {
            return applySchema(targets, task);
        }

        // 3. Fallback: extract tables from destinationConfig directly
        Map<String, Object> writerConfig = jsonNodeToMap(task.getDestinationConfig());
        List<String> configTables = extractTables(writerConfig);
        if (!configTables.isEmpty()) {
            return applySchema(configTables, task);
        }

        return List.of();
    }

    /**
     * Check if a table exists.
     */
    public boolean tableExists(JdbcMetadataService.JdbcConnectionInfo connInfo, String schema, String table) {
        var tables = metadataService.listTables(connInfo, schema, table, 1);
        return !tables.isEmpty();
    }

    // ---- private helpers ----

    private List<String> applySchema(List<String> tables, IngestionTask task) {
        Map<String, Object> writerConfig = jsonNodeToMap(task.getDestinationConfig());
        String schema = resolveSchema(writerConfig);
        if (!StringUtils.hasText(schema)) {
            return tables;
        }
        List<String> qualified = new ArrayList<>(tables.size());
        for (String table : tables) {
            if (!StringUtils.hasText(table)) {
                continue;
            }
            if (table.contains(".")) {
                qualified.add(table);
            } else {
                qualified.add(schema + "." + table);
            }
        }
        return qualified;
    }

    private List<String> parseTargetTablesFromMapping(JsonNode mappingNode) {
        if (mappingNode == null || mappingNode.isNull()) {
            return List.of();
        }
        try {
            List<Map<String, String>> mappings = objectMapper.convertValue(
                mappingNode,
                new TypeReference<List<Map<String, String>>>() {}
            );
            if (mappings == null || mappings.isEmpty()) {
                return List.of();
            }
            List<String> targets = new ArrayList<>();
            for (Map<String, String> mapping : mappings) {
                String target = mapping.get("target");
                if (StringUtils.hasText(target)) {
                    targets.add(target.trim());
                }
            }
            return targets;
        } catch (Exception ex) {
            LOG.debug("Failed to parse table mapping for target tables: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> direct = extractValues(config.get("table"));
        if (!direct.isEmpty()) {
            return direct;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            List<String> values = extractValues(map.get("table"));
            if (!values.isEmpty()) {
                return values;
            }
        } else if (connection instanceof List<?> list) {
            List<String> collected = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    collected.addAll(extractValues(entryMap.get("table")));
                }
            }
            if (!collected.isEmpty()) {
                return collected;
            }
        }
        return List.of();
    }

    private List<String> extractValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String str) {
            String normalized = normalizeText(str);
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> collected = new ArrayList<>();
            for (Object entry : iterable) {
                String normalized = normalizeText(entry);
                if (StringUtils.hasText(normalized)) {
                    collected.add(normalized);
                }
            }
            return collected;
        }
        return List.of();
    }

    private String resolveSchema(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String schema = normalizeText(config.get("schema"));
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        Object schemas = config.get("schemas");
        if (schemas instanceof List<?> list && !list.isEmpty()) {
            String candidate = normalizeText(list.get(0));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return normalizeText(map.get("schema"));
        }
        return null;
    }

    private String resolveJdbcUrl(Map<String, Object> config) {
        Object direct = config.get("jdbcUrl");
        String url = firstStringValue(direct);
        if (StringUtils.hasText(url)) {
            return url;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return firstStringValue(map.get("jdbcUrl"));
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    String candidate = firstStringValue(entryMap.get("jdbcUrl"));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, String> resolveJdbcProperties(Map<String, Object> config) {
        Object props = config.get("jdbcProperties");
        if (props instanceof Map<?, ?> map) {
            Map<String, String> resolved = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    resolved.put(k.toString(), v.toString());
                }
            });
            return resolved;
        }
        return Map.of();
    }

    private String appendPostgresSslDisable(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return url;
        }
        if (url.contains("sslmode=") || url.contains("ssl=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=disable";
    }

    private String firstStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return normalizeText(str);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                String text = normalizeText(entry);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return normalizeText(value);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            LOG.debug("Failed to convert JsonNode to map: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
