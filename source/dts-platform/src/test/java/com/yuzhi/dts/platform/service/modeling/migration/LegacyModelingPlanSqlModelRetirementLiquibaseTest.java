package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LegacyModelingPlanSqlModelRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_07_retire_legacy_modeling_plan_sql_model.xml";

    @Test
    void retirementIsFailClosedOrderedAndForwardOnly() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("LEGACY_MODELING_PLAN_SQL_MODEL_RETIREMENT_BLOCKED")
            .contains("ROLLBACK_BLOCKED_LEGACY_MODELING_PLAN_SQL_MODEL_RETIREMENT_FORWARD_ONLY")
            .doesNotContain("cascadeConstraints=\"true\"");
        assertThat(xml.indexOf("dropTable tableName=\"modeling_sql_model\""))
            .isLessThan(xml.indexOf("dropTable tableName=\"modeling_plan\""));
    }

    @Test
    void retirementRejectsRowsUnknownDependenciesViewsAndPersistedCatalogIdentities() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("SELECT count(*) FROM modeling_plan")
            .contains("SELECT count(*) FROM modeling_sql_model")
            .contains("c.conrelid IN ('modeling_plan'::regclass, 'modeling_sql_model'::regclass)")
            .contains("c.confrelid IN ('modeling_plan'::regclass, 'modeling_sql_model'::regclass)")
            .contains("JOIN pg_rewrite")
            .contains("dependent_view.relkind IN ('v', 'm')")
            .contains("upper(asset_type) IN ('MODELING_SQL_MODEL', 'MODELING_PLAN')")
            .contains("upper(upstream_asset_type) IN ('MODELING_SQL_MODEL', 'MODELING_PLAN')")
            .contains("upper(downstream_asset_type) IN ('MODELING_SQL_MODEL', 'MODELING_PLAN')");
    }

    @Test
    void masterOrdersRetirementAfterPlanLedgersAndCommandReceipts() throws Exception {
        String master = resource("/config/liquibase/master.xml");

        int retirement = master.indexOf("20260801_07_retire_legacy_modeling_plan_sql_model.xml");
        assertThat(retirement).isGreaterThan(master.indexOf("20260801_05_retire_modeling_plan_version_review.xml"));
        assertThat(retirement).isGreaterThan(master.indexOf("20260801_06_model_implementation_command_receipt.xml"));
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
