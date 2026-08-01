package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelingPlanVersionReviewRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_05_retire_modeling_plan_version_review.xml";

    @Test
    void retirementIsFailClosedOrderedAndForwardOnly() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("MODELING_PLAN_VERSION_REVIEW_RETIREMENT_BLOCKED")
            .contains("ROLLBACK_BLOCKED_MODELING_PLAN_VERSION_REVIEW_RETIREMENT_FORWARD_ONLY")
            .doesNotContain("CASCADE");
        assertThat(xml.indexOf("dropTable tableName=\"modeling_plan_review\""))
            .isLessThan(xml.indexOf("dropTable tableName=\"modeling_plan_version\""));
    }

    @Test
    void retirementRequiresEmptyLedgersAndNoUnknownForeignKeyOrViewDependencies() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("SELECT count(*) FROM modeling_plan_review")
            .contains("SELECT count(*) FROM modeling_plan_version")
            .contains("c.confrelid IN")
            .contains("c.conrelid IN")
            .contains("c.conname NOT IN")
            .contains("JOIN pg_rewrite")
            .contains("dependent_view.relkind IN ('v', 'm')");
    }

    @Test
    void masterOrdersTheRetirementAfterEarlierModelingContracts() throws Exception {
        String master = resource("/config/liquibase/master.xml");

        assertThat(master.indexOf("20260801_05_retire_modeling_plan_version_review.xml"))
            .isGreaterThan(master.indexOf("20260801_04_platform_audit_outbox_tenant.xml"));
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
