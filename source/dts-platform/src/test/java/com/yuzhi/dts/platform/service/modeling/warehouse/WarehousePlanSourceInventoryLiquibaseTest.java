package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WarehousePlanSourceInventoryLiquibaseTest {

    @Test
    void expandsSourceEvidenceAndConceptualPolicyWithoutDroppingLegacyMappings() throws Exception {
        String xml;
        try (
            var input = getClass()
                .getResourceAsStream("/config/liquibase/changelog/20260719_05_warehouse_plan_source_inventory.xml")
        ) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("tableName=\"modeling_warehouse_plan_source\"")
            .contains("name=\"locator_json\"")
            .contains("type=\"jsonb\"")
            .contains("name=\"resolution_status\"")
            .contains("name=\"last_validated_at\"")
            .contains("tableName=\"modeling_warehouse_plan_policy\"")
            .contains("name=\"conceptual_design_allowed\"")
            .contains("defaultValueBoolean=\"false\"")
            .contains("<rollback>")
            .contains("columnName=\"locator_json\"")
            .contains("columnName=\"conceptual_design_allowed\"")
            .doesNotContain("dropTable")
            .doesNotContain("modeling_warehouse_plan_source_mapping");

        String master;
        try (var input = getClass().getResourceAsStream("/config/liquibase/master.xml")) {
            assertThat(input).isNotNull();
            master = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(master).contains(
            "config/liquibase/changelog/20260719_05_warehouse_plan_source_inventory.xml"
        );
    }
}
