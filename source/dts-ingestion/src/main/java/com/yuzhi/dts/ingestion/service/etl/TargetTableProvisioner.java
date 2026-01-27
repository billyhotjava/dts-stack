package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TargetTableProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(TargetTableProvisioner.class);
    private static final String TABLE_PLACEHOLDER = "${table}";

    private final JdbcMetadataService metadataService;
    private final ObjectMapper objectMapper;

    public TargetTableProvisioner(JdbcMetadataService metadataService, ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.objectMapper = objectMapper;
    }

    public void ensureTargetTables(IngestionTask task) {
        ensureTargetTables(task, null);
    }

    public void ensureTargetTables(IngestionTask task, Map<String, Object> readerConfigOverride) {
        if (task == null) {
            return;
        }
        Map<String, Object> readerConfig = readerConfigOverride != null
            ? mergeReaderConfig(readerConfigOverride, task.getSourceConfig())
            : jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null
            ? jsonNodeToMap(task.getDestinationConfig())
            : Map.of();
        if (!shouldAutoCreate(writerConfig, task.getAddaxConfig())) {
            return;
        }
        List<TableMapping> mappings = resolveMappings(task.getTableMapping(), readerConfig, writerConfig);
        if (mappings.isEmpty()) {
            return;
        }
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = buildConnectionInfo(readerConfig);
        JdbcMetadataService.JdbcConnectionInfo targetInfo = buildConnectionInfo(writerConfig);
        if (!StringUtils.hasText(targetInfo.jdbcUrl())) {
            LOG.warn("Target jdbcUrl missing, skip auto-create tables for task={}", task.getId());
            return;
        }
        try (Connection connection = metadataService.openConnection(targetInfo)) {
            for (TableMapping mapping : mappings) {
                if (!StringUtils.hasText(mapping.target())) {
                    continue;
                }
                TableId target = resolveTargetTable(mapping.target(), resolveSchema(writerConfig));
                if (tableExists(connection, target)) {
                    continue;
                }
                List<JdbcMetadataService.ColumnMeta> columns = resolveColumns(sourceInfo, mapping.source(), readerConfig);
                if (columns.isEmpty()) {
                    throw new IllegalStateException("无法获取源表字段信息: " + mapping.source());
                }
                createSchemaIfNeeded(connection, target.schema());
                createTable(connection, target, columns);
                LOG.info("Auto-created table {} for task {}", target.qualifiedName(), task.getId());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("自动建表失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> mergeReaderConfig(Map<String, Object> baseConfig, JsonNode overrideNode) {
        Map<String, Object> merged = baseConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(baseConfig);
        if (overrideNode == null || overrideNode.isNull()) {
            return merged;
        }
        Map<String, Object> overrides = jsonNodeToMap(overrideNode);
        if (overrides.isEmpty()) {
            return merged;
        }
        List<String> tables = extractTables(overrides);
        overrides.remove("table");
        overrides.remove("tables");
        overrides.remove("connection");
        overrides.remove("jdbcUrl");
        overrides.remove("url");
        overrides.remove("host");
        overrides.remove("port");
        overrides.remove("username");
        overrides.remove("password");
        overrides.remove("database");
        overrides.remove("db");
        overrides.remove("driver");
        overrides.remove("driverClass");
        overrides.remove("driverVersion");
        overrides.remove("jdbcProperties");
        merged.putAll(overrides);
        if (!tables.isEmpty()) {
            applyTables(merged, tables);
        }
        return merged;
    }

    private void applyTables(Map<String, Object> config, List<String> tables) {
        if (config == null || tables == null || tables.isEmpty()) {
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTableField(map, tables);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTableField(entryMap, tables);
                }
            }
            return;
        }
        config.put("table", tables);
    }

    private boolean shouldAutoCreate(Map<String, Object> writerConfig, JsonNode jobConfig) {
        Boolean value = booleanValue(writerConfig.get("autoCreateTables"));
        if (value == null) {
            value = booleanValue(writerConfig.get("autoCreate"));
        }
        if (value == null && jobConfig != null && !jobConfig.isNull()) {
            value = booleanValue(jobConfig.get("autoCreateTables"));
        }
        return value == null ? Boolean.TRUE : value;
    }

    private JdbcMetadataService.JdbcConnectionInfo buildConnectionInfo(Map<String, Object> config) {
        String jdbcUrl = resolveJdbcUrl(config);
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

    private List<JdbcMetadataService.ColumnMeta> resolveColumns(
        JdbcMetadataService.JdbcConnectionInfo sourceInfo,
        String sourceTable,
        Map<String, Object> readerConfig
    ) {
        List<String> configured = extractColumns(readerConfig);
        if (!configured.isEmpty()) {
            List<JdbcMetadataService.ColumnMeta> cols = new ArrayList<>();
            for (String name : configured) {
                if (StringUtils.hasText(name)) {
                    cols.add(new JdbcMetadataService.ColumnMeta(name, Types.VARCHAR, "TEXT", null, null));
                }
            }
            return cols;
        }
        if (!StringUtils.hasText(sourceInfo.jdbcUrl())) {
            return List.of();
        }
        return metadataService.getTableColumns(sourceInfo, sourceTable);
    }

    private List<TableMapping> resolveMappings(JsonNode mappingNode, Map<String, Object> readerConfig, Map<String, Object> writerConfig) {
        List<TableMapping> mappings = parseTableMapping(mappingNode);
        if (!mappings.isEmpty()) {
            return mappings;
        }
        List<String> sources = extractTables(readerConfig);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<String> targets = extractTables(writerConfig);
        String prefix = resolveTablePrefix(writerConfig);
        if (targets.isEmpty()) {
            targets = sources;
            if (StringUtils.hasText(prefix)) {
                targets = sources.stream()
                    .map(source -> prefix + source)
                    .toList();
            }
        } else if (targets.size() == 1 && targets.get(0).contains(TABLE_PLACEHOLDER)) {
            String template = targets.get(0);
            targets = sources.stream().map(src -> template.replace(TABLE_PLACEHOLDER, src)).toList();
        }
        int size = Math.min(sources.size(), targets.size());
        List<TableMapping> resolved = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            resolved.add(new TableMapping(sources.get(i), targets.get(i)));
        }
        return resolved;
    }

    private List<TableMapping> parseTableMapping(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            List<TableMapping> mappings = objectMapper.convertValue(
                node,
                new com.fasterxml.jackson.core.type.TypeReference<List<TableMapping>>() {}
            );
            if (mappings == null) {
                return List.of();
            }
            return mappings.stream()
                .filter(mapping -> StringUtils.hasText(mapping.source()) || StringUtils.hasText(mapping.target()))
                .toList();
        } catch (Exception ex) {
            LOG.debug("Failed to parse table mapping: {}", ex.getMessage());
            return List.of();
        }
    }

    private TableId resolveTargetTable(String raw, String schemaFallback) {
        TableId parsed = TableId.parse(raw);
        if (StringUtils.hasText(parsed.schema())) {
            return parsed;
        }
        if (StringUtils.hasText(schemaFallback)) {
            return new TableId(schemaFallback, parsed.table());
        }
        return parsed;
    }

    private List<String> extractColumns(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> values = extractValues(config.get("column"));
        if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
            return values;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            values = extractValues(map.get("column"));
            if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
                return values;
            }
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    values = extractValues(entryMap.get("column"));
                    if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
                        return values;
                    }
                }
            }
        }
        return List.of();
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

    private String resolveTablePrefix(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String prefix = normalizeText(config.get("tablePrefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        prefix = normalizeText(config.get("prefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        return normalizeText(config.get("targetPrefix"));
    }

    private void createSchemaIfNeeded(Connection connection, String schema) throws Exception {
        if (!StringUtils.hasText(schema) || "public".equalsIgnoreCase(schema)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + quoteIdentifier(schema));
        }
    }

    private boolean tableExists(Connection connection, TableId tableId) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, tableId.schema(), tableId.table(), new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private void createTable(Connection connection, TableId tableId, List<JdbcMetadataService.ColumnMeta> columns) throws Exception {
        StringBuilder ddl = new StringBuilder();
        ddl.append("create table if not exists ").append(tableId.qualifiedName()).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            JdbcMetadataService.ColumnMeta col = columns.get(i);
            ddl.append(quoteIdentifier(col.name())).append(" ").append(mapType(col));
            if (i < columns.size() - 1) {
                ddl.append(", ");
            }
        }
        ddl.append(")");
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl.toString());
        }
    }

    private String mapType(JdbcMetadataService.ColumnMeta column) {
        if (column == null) {
            return "text";
        }
        int jdbcType = column.jdbcType();
        String typeName = normalizeText(column.typeName());
        Integer size = column.columnSize();
        Integer scale = column.decimalDigits();
        return switch (jdbcType) {
            case Types.BOOLEAN, Types.BIT -> "boolean";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> "integer";
            case Types.BIGINT -> "bigint";
            case Types.FLOAT, Types.REAL -> "real";
            case Types.DOUBLE -> "double precision";
            case Types.NUMERIC, Types.DECIMAL -> {
                if (size != null && size > 0) {
                    int scaleVal = scale == null ? 0 : Math.max(0, scale);
                    yield "decimal(" + size + "," + scaleVal + ")";
                }
                yield "decimal";
            }
            case Types.DATE -> "date";
            case Types.TIME -> "time";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "bytea";
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> {
                if (size != null && size > 0 && size <= 65535) {
                    yield "varchar(" + size + ")";
                }
                yield "text";
            }
            default -> {
                if (typeName != null && typeName.toLowerCase(Locale.ROOT).contains("clob")) {
                    yield "text";
                }
                yield "text";
            }
        };
    }

    private String quoteIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            LOG.debug("Failed to convert JsonNode to map: {}", ex.getMessage());
            return Map.of();
        }
    }

    private record TableMapping(String source, String target) {}

    private record TableId(String schema, String table) {
        static TableId parse(String raw) {
            if (!StringUtils.hasText(raw)) {
                return new TableId(null, raw);
            }
            String trimmed = raw.trim();
            int idx = trimmed.indexOf('.');
            if (idx > 0 && idx < trimmed.length() - 1) {
                String schema = trimmed.substring(0, idx).trim();
                String table = trimmed.substring(idx + 1).trim();
                return new TableId(schema, table);
            }
            return new TableId(null, trimmed);
        }

        String qualifiedName() {
            if (StringUtils.hasText(schema)) {
                return "\"" + schema.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
            }
            return "\"" + table.replace("\"", "\"\"") + "\"";
        }
    }
}
