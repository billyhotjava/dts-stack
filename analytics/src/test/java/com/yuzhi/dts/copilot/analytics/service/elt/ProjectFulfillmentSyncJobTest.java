package com.yuzhi.dts.copilot.analytics.service.elt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ProjectFulfillmentSyncJobTest {

    @Test
    void targetTableAndUpsertSqlShouldMatchMartSchema() throws Exception {
        assertThat(readStaticString("TARGET_TABLE"))
                .isEqualTo("mart_project_fulfillment_daily");

        String upsertSql = readStaticString("UPSERT_SQL");
        assertThat(upsertSql).contains("INSERT INTO mart_project_fulfillment_daily");
        assertThat(upsertSql).contains("project_status_name");
        assertThat(upsertSql).contains("settlement_type_name");
        assertThat(upsertSql).contains("change_flower_count");
        assertThat(upsertSql).contains("sync_batch_id");

        assertThat(upsertSql).doesNotContain("fact_project_fulfillment_snapshot");
        assertThat(upsertSql).doesNotContain("project_status,");
        assertThat(upsertSql).doesNotContain("settlement_type,");
        assertThat(upsertSql).doesNotContain("flower_change_count");
        assertThat(upsertSql).doesNotContain("batch_id = EXCLUDED.batch_id");
    }

    private static String readStaticString(String fieldName) throws Exception {
        Field field = ProjectFulfillmentSyncJob.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
