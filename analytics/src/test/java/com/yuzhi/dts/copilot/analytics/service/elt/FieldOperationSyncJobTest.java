package com.yuzhi.dts.copilot.analytics.service.elt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class FieldOperationSyncJobTest {

    @Test
    void targetTableAndUpsertSqlShouldMatchFactSchema() throws Exception {
        assertThat(readStaticString("TARGET_TABLE"))
                .isEqualTo("fact_field_operation_event");

        String upsertSql = readStaticString("UPSERT_SQL");
        assertThat(upsertSql).contains("INSERT INTO fact_field_operation_event");
        assertThat(upsertSql).contains("event_date");
        assertThat(upsertSql).contains("event_month");
        assertThat(upsertSql).contains("event_year");
        assertThat(upsertSql).contains("biz_type_name");
        assertThat(upsertSql).contains("biz_status_name");
        assertThat(upsertSql).contains("is_urgent");
        assertThat(upsertSql).contains("bear_cost_type_name");
        assertThat(upsertSql).contains("source_updated_at");
        assertThat(upsertSql).contains("sync_batch_id");

        assertThat(upsertSql).doesNotContain("biz_type_label");
        assertThat(upsertSql).doesNotContain("status_label");
        assertThat(upsertSql).doesNotContain("urgent_label");
        assertThat(upsertSql).doesNotContain("bear_cost_type_label");
        assertThat(upsertSql).doesNotContain("source_update_time");
        assertThat(upsertSql).doesNotContain("batch_id = EXCLUDED.batch_id");
    }

    private static String readStaticString(String fieldName) throws Exception {
        Field field = FieldOperationSyncJob.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
