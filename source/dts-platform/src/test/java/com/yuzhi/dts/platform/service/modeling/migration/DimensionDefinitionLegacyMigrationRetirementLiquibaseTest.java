package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DimensionDefinitionLegacyMigrationRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_03_retire_dimension_definition_legacy_migration.xml";

    @Test
    void retirementIsFailClosedOrderedAndForwardOnly() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("DIMENSION_DEFINITION_LEGACY_MIGRATION_RETIREMENT_BLOCKED")
            .contains("ROLLBACK_BLOCKED_DIMENSION_DEFINITION_LEGACY_MIGRATION_RETIREMENT_FORWARD_ONLY")
            .doesNotContain("CASCADE");
        assertThat(xml.indexOf("dropTable tableName=\"modeling_dimension_definition_legacy_map\""))
            .isLessThan(xml.indexOf("dropTable tableName=\"modeling_dimension_definition_migration_batch\""));
    }

    @Test
    void retirementRequiresEmptyLedgersAndFullyPinnedCanonicalHeads() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("SELECT count(*) FROM modeling_dimension_definition_legacy_map")
            .contains("SELECT count(*) FROM modeling_dimension_definition_migration_batch")
            .contains("contract_version IS DISTINCT FROM 2")
            .contains("contract_version = 2")
            .contains("model_type = 'DIMENSION'")
            .contains("dimension_definition_id IS NOT NULL")
            .contains("dimension_definition_revision IS NOT NULL")
            .contains("model_type <> 'DIMENSION'")
            .contains("dimension_definition_id IS NULL")
            .contains("dimension_definition_revision IS NULL");
    }

    @Test
    void onlyTheHeadConstraintIsTightenedAndMasterOrdersRetirementLast() throws Exception {
        String xml = resource(CHANGELOG);
        String master = resource("/config/liquibase/master.xml");

        assertThat(xml)
            .contains("DROP CONSTRAINT ck_model_spec_dimension_definition_ref")
            .contains("ADD CONSTRAINT ck_model_spec_dimension_definition_ref")
            .doesNotContain("ck_model_spec_revision_dimension_definition_ref");
        assertThat(master.indexOf("20260801_03_retire_dimension_definition_legacy_migration.xml"))
            .isGreaterThan(master.indexOf("20260801_02_retire_model_spec_v1.xml"));
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
