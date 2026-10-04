package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelQueryDatasetProjectionLiquibaseTest {

    @Test
    void addsNullableStableModelLinkWithConcurrentUniqueIndexAndRollback() throws Exception {
        String migration = read("/config/liquibase/changelog/20260819_01_model_query_dataset_projection.xml");
        String master = read("/config/liquibase/master.xml");

        assertThat(migration)
            .contains("tableName=\"query_dataset_asset\"")
            .contains("name=\"source_model_spec_id\"")
            .contains("CREATE UNIQUE INDEX CONCURRENTLY")
            .contains("WHERE source_model_spec_id IS NOT NULL")
            .contains("DROP INDEX CONCURRENTLY")
            .contains("dropColumn tableName=\"query_dataset_asset\" columnName=\"source_model_spec_id\"");
        assertThat(master).contains("20260819_01_model_query_dataset_projection.xml");
    }

    private String read(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
