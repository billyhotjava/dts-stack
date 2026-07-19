package com.yuzhi.dts.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuditActionCatalogResourceTest {

    @Test
    void registersWarehousePlanCategoryAndPolicyWritesAsModelingActions() throws Exception {
        String catalog;
        try (var input = getClass().getResourceAsStream("/config/audit-action-catalog.json")) {
            assertThat(input).isNotNull();
            catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(catalog)
            .contains("\"code\": \"MODELING_WAREHOUSE_CATEGORY_SCOPE_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_POLICY_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE\"")
            .contains("\"key\": \"modeling.plan\"");
    }
}
