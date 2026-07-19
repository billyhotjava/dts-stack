package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LegacyObjectMigrationLiquibaseTest {

    @Test
    void expandsMigrationAndUsageLedgersWithoutDroppingLegacyStructures() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream("/config/liquibase/changelog/20260719_07_legacy_object_retirement.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("modeling_legacy_object_migration_batch")
            .contains("modeling_legacy_object_migration")
            .contains("modeling_legacy_api_usage")
            .contains("source_checksum")
            .contains("classification")
            .contains("decision_status")
            .contains("target_model_spec_id")
            .contains("legacy_source")
            .doesNotContain("dropTable tableName=\"semantic_business_object\"")
            .doesNotContain("dropColumn tableName=\"modeling_model_spec\" columnName=\"object_id\"");
    }
}
