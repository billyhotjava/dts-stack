package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** A bound model target is immutable here: validate it, then let the normal writer insert rows. */
@Service
public class ModelTargetGuard {
    private final PlatformInfraClient platform;
    public ModelTargetGuard(PlatformInfraClient platform) { this.platform = platform; }

    public static boolean isBound(IngestionTask task) {
        return task != null && task.getDestinationConfig() != null && task.getDestinationConfig().has("modelTarget");
    }
    public static boolean isBound(Map<String, Object> writer) { return writer != null && writer.containsKey("modelTarget"); }

    public void validate(IngestionTask task, Connection connection, String schema, String table,
        List<JdbcMetadataService.ColumnMeta> writtenColumns) throws Exception {
        JsonNode target = task.getDestinationConfig().get("modelTarget");
        if (target == null || !target.isObject()) throw failure("MODEL_INGESTION_TARGET_INVALID");
        platform.validateModelIngestionTarget(target);
        String sourceId = null;
        for (String key : List.of("targetDataSourceId", "destinationDataSourceId", "dataSourceId")) {
            if (task.getDestinationConfig().hasNonNull(key)) { sourceId = task.getDestinationConfig().path(key).asText(); break; }
        }
        if (!Objects.equals(sourceId, target.path("dataSourceId").asText()) ||
            !Objects.equals(schema, target.path("schemaName").asText()) || !Objects.equals(table, target.path("tableName").asText()) ||
            !Objects.equals(connection.getCatalog(), target.path("databaseName").asText())) throw failure("MODEL_INGESTION_TARGET_IDENTITY_MISMATCH");
        Map<String, ActualColumn> actual = new HashMap<>();
        try (var query = connection.prepareStatement("""
            select a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod) as physical_type,
                   a.attnotnull, a.atthasdef or a.attidentity <> '' as has_default
              from pg_catalog.pg_attribute a
              join pg_catalog.pg_class c on c.oid=a.attrelid
              join pg_catalog.pg_namespace n on n.oid=c.relnamespace
             where n.nspname=? and c.relname=? and c.relkind='r' and a.attnum>0 and not a.attisdropped
            """)) {
            query.setString(1, schema); query.setString(2, table);
            try (var rows = query.executeQuery()) {
                while (rows.next()) actual.put(rows.getString(1), new ActualColumn(rows.getString(2), !rows.getBoolean(3), rows.getBoolean(4)));
            }
        }
        if (actual.isEmpty()) throw failure("MODEL_INGESTION_TARGET_NOT_MATERIALIZED");
        Set<String> keys = new HashSet<>();
        try (var rows = connection.getMetaData().getPrimaryKeys(null, schema, table)) {
            while (rows.next()) keys.add(rows.getString("COLUMN_NAME"));
        }
        Set<String> declaredNames = new HashSet<>(), declaredKeys = new HashSet<>();
        for (JsonNode column : target.path("columns")) {
            String name = column.path("name").asText(); declaredNames.add(name);
            if (column.path("primaryKey").asBoolean()) declaredKeys.add(name);
            ActualColumn current = actual.get(name);
            if (current == null || !type(current.type()).equals(type(column.path("dataType").asText())) ||
                current.nullable() != column.path("nullable").asBoolean()) throw failure("MODEL_INGESTION_TARGET_STRUCTURE_DRIFT: " + name);
        }
        if (!declaredNames.equals(actual.keySet()) || !declaredKeys.equals(keys)) throw failure("MODEL_INGESTION_TARGET_STRUCTURE_DRIFT");
        Set<String> written = new HashSet<>();
        for (var column : writtenColumns) {
            written.add(column.name());
            ActualColumn destination = actual.get(column.name());
            if (destination == null || !compatible(column, destination.type())) throw failure("MODEL_INGESTION_FIELD_MISMATCH: " + column.name());
        }
        for (var column : actual.entrySet()) {
            if (!column.getValue().nullable() && !column.getValue().hasDefault() && !written.contains(column.getKey())) {
                throw failure("MODEL_INGESTION_REQUIRED_FIELD_MISSING: " + column.getKey());
            }
        }
    }

    static String type(String value) {
        return value.toLowerCase(Locale.ROOT).replace("character varying", "varchar").replace("timestamp without time zone", "timestamp")
            .replace("timestamp with time zone", "timestamptz").replace("decimal", "numeric").replace("int8", "bigint")
            .replace("int4", "integer").replace("int2", "smallint").replaceAll("\\s+", "");
    }
    static boolean compatible(JdbcMetadataService.ColumnMeta source, String targetType) {
        String target = type(targetType);
        return switch (source.jdbcType()) {
            case java.sql.Types.BIGINT -> target.equals("bigint");
            case java.sql.Types.INTEGER -> target.equals("integer") || target.equals("bigint");
            case java.sql.Types.SMALLINT, java.sql.Types.TINYINT -> Set.of("smallint", "integer", "bigint").contains(target);
            case java.sql.Types.BOOLEAN, java.sql.Types.BIT -> target.equals("boolean");
            case java.sql.Types.DATE -> target.equals("date");
            case java.sql.Types.TIMESTAMP -> target.equals("timestamp");
            case java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> target.equals("timestamptz");
            case java.sql.Types.VARCHAR, java.sql.Types.LONGVARCHAR, java.sql.Types.NVARCHAR, java.sql.Types.CHAR ->
                target.equals("text") || target.equals("varchar") || (target.matches("varchar\\([0-9]+\\)") && source.columnSize() != null &&
                    source.columnSize() <= Integer.parseInt(target.substring(8, target.length()-1)));
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> target.equals("numeric") ||
                (source.columnSize() != null && source.decimalDigits() != null && target.equals("numeric("+source.columnSize()+","+source.decimalDigits()+")"));
            case java.sql.Types.REAL -> target.equals("real") || target.equals("doubleprecision");
            case java.sql.Types.DOUBLE, java.sql.Types.FLOAT -> target.equals("doubleprecision");
            default -> type(source.typeName()).equals(target);
        };
    }
    private static IllegalStateException failure(String code) { return new IllegalStateException(code + "：请核对模型版本、目标身份及字段映射；接入不会修改模型表结构"); }
    private record ActualColumn(String type, boolean nullable, boolean hasDefault) {}
}
