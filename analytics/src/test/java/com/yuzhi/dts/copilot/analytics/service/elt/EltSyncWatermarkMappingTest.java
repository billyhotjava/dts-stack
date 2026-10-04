package com.yuzhi.dts.copilot.analytics.service.elt;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.copilot.analytics.domain.EltSyncWatermark;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EltSyncWatermarkMappingTest {

    @Test
    void shouldMapEntityFieldsToLiquibaseColumns() {
        Map<String, String> columnsByField = Arrays.stream(EltSyncWatermark.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .collect(Collectors.toMap(Field::getName, field -> field.getAnnotation(Column.class).name()));

        assertThat(columnsByField).containsEntry("targetTable", "target_table");
        assertThat(columnsByField).containsEntry("lastWatermark", "last_watermark");
        assertThat(columnsByField).containsEntry("lastSyncTime", "last_sync_time");
        assertThat(columnsByField).containsEntry("lastSyncRows", "last_sync_rows");
        assertThat(columnsByField).containsEntry("lastSyncDurationMs", "last_sync_duration_ms");
        assertThat(columnsByField).containsEntry("syncStatus", "sync_status");
        assertThat(columnsByField).containsEntry("errorMessage", "error_message");
        assertThat(columnsByField).containsEntry("createdAt", "created_at");
        assertThat(columnsByField).containsEntry("updatedAt", "updated_at");

        assertThat(columnsByField).doesNotContainKey("status");
        assertThat(columnsByField).doesNotContainKey("lastBatchId");
        assertThat(columnsByField).doesNotContainKey("lastRowCount");
    }
}
