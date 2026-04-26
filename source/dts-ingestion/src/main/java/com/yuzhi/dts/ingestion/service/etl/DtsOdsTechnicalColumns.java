package com.yuzhi.dts.ingestion.service.etl;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DtsOdsTechnicalColumns {

    static final String SOURCE_SYSTEM = "_dts_source_system";
    static final String SOURCE_TABLE = "_dts_source_table";
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

    private DtsOdsTechnicalColumns() {}

    static boolean isCommonTechnicalColumn(String name) {
        return name != null && COMMON_NAMES.contains(name.toLowerCase(Locale.ROOT));
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

    private static JdbcMetadataService.ColumnMeta varchar(String name, int size) {
        return new JdbcMetadataService.ColumnMeta(name, Types.VARCHAR, "VARCHAR", size, null);
    }
}
