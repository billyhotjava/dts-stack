package com.yuzhi.dts.ingestion.service.etl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DtsOdsTechnicalColumns {

    static final String SOURCE_SYSTEM = "_dts_source_system";
    static final String SOURCE_TABLE = "_dts_source_table";
    static final String SOURCE_FILE = "_dts_source_file";
    static final String SOURCE_SHEET = "_dts_source_sheet";
    static final String FILE_HASH = "_dts_file_hash";
    static final String ROW_NUMBER = "_dts_row_number";
    static final String IMPORT_TIME = "_dts_import_time";
    static final String BATCH_ID = "_dts_batch_id";
    static final String EXECUTION_ID = "_dts_execution_id";
    static final String TASK_ID = "_dts_task_id";

    private static final Set<String> COMMON_NAMES = Set.of(
        SOURCE_SYSTEM,
        SOURCE_TABLE,
        IMPORT_TIME,
        BATCH_ID,
        EXECUTION_ID,
        TASK_ID
    );

    private static final Set<String> FILE_NAMES = Set.of(
        SOURCE_FILE,
        SOURCE_SHEET,
        FILE_HASH,
        ROW_NUMBER
    );

    private DtsOdsTechnicalColumns() {}

    static boolean isCommonTechnicalColumn(String name) {
        return name != null && COMMON_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    static boolean isTechnicalColumn(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return COMMON_NAMES.contains(lower) || FILE_NAMES.contains(lower);
    }

    static List<JdbcMetadataService.ColumnMeta> businessColumns(
        List<JdbcMetadataService.ColumnMeta> sourceColumns
    ) {
        if (sourceColumns == null || sourceColumns.isEmpty()) {
            return List.of();
        }
        Set<String> usedNames = new LinkedHashSet<>();
        usedNames.addAll(COMMON_NAMES);
        usedNames.addAll(FILE_NAMES);
        List<JdbcMetadataService.ColumnMeta> landingColumns = new ArrayList<>(sourceColumns.size());
        for (JdbcMetadataService.ColumnMeta sourceColumn : sourceColumns) {
            String sourceName = sourceColumn == null ? null : sourceColumn.name();
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalStateException("create table mapping validation failed: 源字段名称不能为空");
            }
            String base = sourceName.trim();
            String targetName = base;
            int suffix = 2;
            while (!usedNames.add(targetName.toLowerCase(Locale.ROOT))) {
                targetName = base + "_" + suffix++;
            }
            landingColumns.add(sourceColumn.withName(targetName));
        }
        return List.copyOf(landingColumns);
    }

    static List<JdbcMetadataService.ColumnMeta> commonJdbcColumns() {
        return List.of(
            varchar(SOURCE_SYSTEM, 200),
            varchar(SOURCE_TABLE, 300),
            new JdbcMetadataService.ColumnMeta(IMPORT_TIME, Types.TIMESTAMP, "TIMESTAMP", null, null),
            varchar(BATCH_ID, 128),
            varchar(EXECUTION_ID, 128),
            varchar(TASK_ID, 64)
        );
    }

    static List<JdbcMetadataService.ColumnMeta> fileJdbcColumns() {
        return List.of(
            varchar(SOURCE_FILE, 500),
            varchar(SOURCE_SHEET, 200),
            varchar(FILE_HASH, 128),
            new JdbcMetadataService.ColumnMeta(ROW_NUMBER, Types.INTEGER, "INTEGER", null, null)
        );
    }

    private static JdbcMetadataService.ColumnMeta varchar(String name, int size) {
        return new JdbcMetadataService.ColumnMeta(name, Types.VARCHAR, "VARCHAR", size, null);
    }
}
