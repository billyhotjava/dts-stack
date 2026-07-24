package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelingSqlModelSpecBindingLiquibaseTest {

    @Test
    void bindsAdvancedSqlWorkspaceToOneActiveDbtManagedModelSpec() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260724_09_sql_model_spec_binding.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("model_spec_id")
            .contains("uk_modeling_sql_model_spec")
            .contains("modeling_sql_model_spec_binding_valid")
            .contains("canonical_mode <> 'DBT_MANAGED'")
            .contains("implementation_mode <> 'DBT_MANAGED'")
            .contains("NEW.plan_id IS DISTINCT FROM canonical_plan")
            .contains("NEW.owner_dept IS DISTINCT FROM canonical_tenant")
            .contains("implementation_dbt_unique_id")
            .contains("MODEL_SQL_WORKSPACE_REVISION_REQUIRED")
            .contains("MODEL_SQL_WORKSPACE_RETIREMENT_REQUIRED");
    }
}
