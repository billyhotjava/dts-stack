package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sprint73ModelingLiquibaseTest {

    @Test
    void createsDataMartPlanningLedgerAndPlanBindingWithoutAssetLedgerCoupling() throws Exception {
        String xml = read("/config/liquibase/changelog/20260727_01_modeling_data_mart.xml");

        assertThat(xml)
            .contains("tableName=\"modeling_data_mart\"")
            .contains("tableName=\"modeling_data_mart_revision\"")
            .contains("tableName=\"modeling_data_mart_domain\"")
            .contains("tableName=\"modeling_warehouse_plan_data_mart\"")
            .contains("name=\"data_marts_version\"")
            .contains("uk_modeling_data_mart_code")
            .contains("uk_modeling_data_mart_idempotency")
            .contains("ROLLBACK_BLOCKED_MODELING_DATA_MART_FORWARD_ONLY")
            .doesNotContain("catalog_asset");
    }

    @Test
    void expandsDimensionDefinitionsAndModelSpecsWithoutChangingTheDimensionLayer() throws Exception {
        String dimension = read("/config/liquibase/changelog/20260727_02_dimension_scope_attributes.xml");
        String model = read("/config/liquibase/changelog/20260727_03_model_spec_mart_dimension_fields.xml");

        assertThat(dimension)
            .contains("name=\"scope_type\"")
            .contains("name=\"data_mart_id\"")
            .contains("name=\"attributes_json\"")
            .contains("scope_type = 'DOMAIN'")
            .contains("scope_type = 'DATA_MART'")
            .contains("ROLLBACK_BLOCKED_DIMENSION_SCOPE_ATTRIBUTES_FORWARD_ONLY");
        assertThat(model)
            .contains("name=\"data_mart_id\"")
            .contains("name=\"variant_code\"")
            .contains("uk_model_spec_active_dimension_variant")
            .contains("model_type = 'DIMENSION'")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_MART_VARIANT_FORWARD_ONLY")
            .doesNotContain("'DIM'");
    }

    @Test
    void includesAllForwardChangesetsInOrder() throws Exception {
        String master = read("/config/liquibase/master.xml");
        int dataMart = master.indexOf("20260727_01_modeling_data_mart.xml");
        int dimension = master.indexOf("20260727_02_dimension_scope_attributes.xml");
        int model = master.indexOf("20260727_03_model_spec_mart_dimension_fields.xml");

        assertThat(dataMart).isGreaterThan(0);
        assertThat(dimension).isGreaterThan(dataMart);
        assertThat(model).isGreaterThan(dimension);
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
