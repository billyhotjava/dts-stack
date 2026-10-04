package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionSchemaSnapshot;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionSchemaSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IngestionSchemaSnapshotService {

    private final IngestionSchemaSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public IngestionSchemaSnapshotService(IngestionSchemaSnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestionSchemaSnapshot saveSnapshot(
        IngestionTask task,
        IngestionExecution execution,
        String sourceType,
        String sourceSystem,
        String sourceSchema,
        String sourceTable,
        String sourceResource,
        String odsSchema,
        String odsTable,
        List<JdbcMetadataService.ColumnMeta> sourceColumns,
        List<JdbcMetadataService.ColumnMeta> odsColumns,
        List<String> primaryKeyColumns,
        List<JdbcMetadataService.IndexMeta> indexes,
        List<String> warnings
    ) {
        if (task == null || task.getId() == null || !StringUtils.hasText(odsTable)) {
            return null;
        }
        ArrayNode columnsJson = buildColumnsJson(sourceColumns, odsColumns);

        IngestionSchemaSnapshot snapshot = new IngestionSchemaSnapshot();
        snapshot.setTask(task);
        snapshot.setExecution(execution);
        snapshot.setSnapshotVersion(1);
        snapshot.setSourceType(sourceType);
        snapshot.setSourceSystem(sourceSystem);
        snapshot.setSourceSchema(sourceSchema);
        snapshot.setSourceTable(sourceTable);
        snapshot.setSourceResource(sourceResource);
        snapshot.setOdsSchema(odsSchema);
        snapshot.setOdsTable(odsTable);
        snapshot.setColumnsJson(columnsJson);
        snapshot.setPrimaryKeyColumns(objectMapper.valueToTree(primaryKeyColumns == null ? List.of() : primaryKeyColumns));
        snapshot.setIndexesJson(objectMapper.valueToTree(indexes == null ? List.of() : indexes));
        snapshot.setWarningsJson(objectMapper.valueToTree(warnings == null ? List.of() : warnings));
        snapshot.setSchemaFingerprint(fingerprint(columnsJson));
        return repository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<IngestionSchemaSnapshot> findByTask(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return repository.findByTask_IdOrderByCreatedDateDesc(taskId);
    }

    public List<JdbcMetadataService.ColumnMeta> toOdsColumns(IngestionSchemaSnapshot snapshot) {
        if (snapshot == null || snapshot.getColumnsJson() == null || !snapshot.getColumnsJson().isArray()) {
            return List.of();
        }
        List<JdbcMetadataService.ColumnMeta> columns = new ArrayList<>();
        for (JsonNode node : snapshot.getColumnsJson()) {
            String name = text(node, "odsName");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            int jdbcType = intValue(node, "targetJdbcType", Types.VARCHAR);
            String typeName = text(node, "targetTypeName");
            Integer size = integer(node, "targetColumnSize");
            Integer scale = integer(node, "targetDecimalDigits");
            Boolean nullable = booleanValue(node, "nullable");
            String defaultValue = text(node, "defaultValue");
            String comment = text(node, "comment");
            Integer ordinal = integer(node, "ordinalPosition");
            columns.add(new JdbcMetadataService.ColumnMeta(name, jdbcType, typeName, size, scale, nullable, defaultValue, comment, ordinal));
        }
        return columns;
    }

    private ArrayNode buildColumnsJson(
        List<JdbcMetadataService.ColumnMeta> sourceColumns,
        List<JdbcMetadataService.ColumnMeta> odsColumns
    ) {
        ArrayNode array = objectMapper.createArrayNode();
        List<JdbcMetadataService.ColumnMeta> safeSource = sourceColumns == null ? List.of() : sourceColumns;
        List<JdbcMetadataService.ColumnMeta> safeOds = odsColumns == null ? List.of() : odsColumns;
        int sourceIndex = 0;
        for (int i = 0; i < safeOds.size(); i++) {
            JdbcMetadataService.ColumnMeta ods = safeOds.get(i);
            boolean technical = DtsOdsTechnicalColumns.isTechnicalColumn(ods.name());
            JdbcMetadataService.ColumnMeta source = null;
            if (!technical && sourceIndex < safeSource.size()) {
                source = safeSource.get(sourceIndex++);
            }
            boolean renamed = source != null
                && source.name() != null
                && ods.name() != null
                && !source.name().equals(ods.name());
            ObjectNode item = objectMapper.createObjectNode();
            item.put("ordinalPosition", i + 1);
            item.put("technical", technical);
            item.put(
                "conflictAction",
                technical ? "append_dts_technical" : (renamed ? "rename_source_column" : "none")
            );
            putText(item, "sourceName", source == null ? null : source.name());
            putText(item, "odsName", ods.name());
            putNumber(item, "sourceJdbcType", source == null ? null : source.jdbcType());
            putText(item, "sourceTypeName", source == null ? null : source.typeName());
            putNumber(item, "sourceColumnSize", source == null ? null : source.columnSize());
            putNumber(item, "sourceDecimalDigits", source == null ? null : source.decimalDigits());
            putNumber(item, "sourceOrdinalPosition", source == null ? null : source.ordinalPosition());
            putNumber(item, "targetJdbcType", ods.jdbcType());
            putText(item, "targetTypeName", ods.typeName());
            putNumber(item, "targetColumnSize", ods.columnSize());
            putNumber(item, "targetDecimalDigits", ods.decimalDigits());
            putBoolean(item, "nullable", source == null ? ods.nullable() : source.nullable());
            putText(item, "defaultValue", source == null ? ods.defaultValue() : source.defaultValue());
            putText(item, "comment", source == null ? ods.comment() : source.comment());
            array.add(item);
        }
        return array;
    }

    private String fingerprint(JsonNode node) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            return null;
        }
    }

    private void putText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        } else {
            node.putNull(field);
        }
    }

    private void putNumber(ObjectNode node, String field, Integer value) {
        if (value != null) {
            node.put(field, value);
        } else {
            node.putNull(field);
        }
    }

    private void putBoolean(ObjectNode node, String field, Boolean value) {
        if (value != null) {
            node.put(field, value);
        } else {
            node.putNull(field);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asInt(fallback);
    }

    private Boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }
}
