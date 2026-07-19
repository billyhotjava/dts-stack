package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WarehousePlanCategoryPolicyDraftLiquibaseTest {

    @Test
    void persistsCategoryValidationAndAllowsOnlyImplementationFieldsToRemainDraft() throws Exception {
        String xml;
        try (
            var input = getClass()
                .getResourceAsStream("/config/liquibase/changelog/20260719_04_warehouse_plan_category_policy_draft.xml")
        ) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("tableName=\"modeling_warehouse_plan_domain\"")
            .contains("name=\"last_validated_at\"")
            .contains("tableName=\"modeling_warehouse_plan_policy\"")
            .contains("columnName=\"layer_policy_code\"")
            .contains("columnName=\"history_policy\"")
            .contains("columnName=\"default_time_zone\"")
            .contains("ROLLBACK_BLOCKED_WAREHOUSE_POLICY_DRAFT_EXISTS")
            .doesNotContain("dropTable")
            .doesNotContain("delete from modeling_warehouse_plan");

        String master;
        try (var input = getClass().getResourceAsStream("/config/liquibase/master.xml")) {
            assertThat(input).isNotNull();
            master = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(master).contains(
            "config/liquibase/changelog/20260719_04_warehouse_plan_category_policy_draft.xml"
        );
    }
}
