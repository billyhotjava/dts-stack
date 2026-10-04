package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WarehouseLayerGovernanceLiquibaseTest {

    @Test
    void addsGlobalCustomLayerRegistryAndBackfillsModelSelections() throws Exception {
        String master = resource("config/liquibase/master.xml");
        String migration = resource("config/liquibase/changelog/20260804_01_modeling_warehouse_layer.xml");
        String groupMigration = resource("config/liquibase/changelog/20260806_02_modeling_warehouse_layer_group.xml");

        assertThat(master).contains("20260804_01_modeling_warehouse_layer.xml");
        assertThat(master).contains("20260806_02_modeling_warehouse_layer_group.xml");
        assertThat(migration)
            .contains("modeling_warehouse_layer")
            .contains("system_layer_code")
            .contains("status in ('ACTIVE', 'DELETED')")
            .contains("warehouse_layer_code")
            .contains("update modeling_model_spec")
            .contains("set warehouse_layer_code = layer")
            .contains("nullable=\"false\"")
            .doesNotContain("tenant_id");
        assertThat(groupMigration)
            .contains("layer_group")
            .contains("'STAGING', 'COMMON', 'APPLICATION'")
            .contains("alter column layer_group set not null");
    }

    private static String resource(String path) throws Exception {
        try (var input = WarehouseLayerGovernanceLiquibaseTest.class.getResourceAsStream("/" + path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
