package com.yuzhi.dts.common.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ManagedDatabaseLandingPlanTest {
    private static final String SOURCE = "11111111-2222-3333-4444-555555555555";
    private static final String TARGET = "a0000000-0000-0000-0000-000000000001";

    @Test
    void acceptsConcreteSingleAndMultipleManagedTargets() {
        assertThat(configured(List.of("prs.customer"), List.of("ods_customer"))).isTrue();
        assertThat(configured(List.of("prs.customer", "prs.contract"), List.of("ods_customer", "ods_contract"))).isTrue();
    }

    @Test
    void rejectsIncompleteAndCollidingPlans() {
        assertThat(configured(List.of(), List.of())).isFalse();
        assertThat(configured(List.of("a", "b"), List.of("target"))).isFalse();
        assertThat(configured(List.of("a", "b"), List.of("target", "TARGET"))).isFalse();
        assertThat(configured(List.of("${table}"), List.of("target"))).isFalse();
        assertThat(configured(List.of("a"), List.of(""))).isFalse();
        assertThat(ManagedDatabaseLandingPlan.isConfigured("mysqlreader", SOURCE, "postgresqlwriter", "invalid",
            "full_refresh", List.of("a"), List.of("b"))).isFalse();
        assertThat(ManagedDatabaseLandingPlan.isConfigured("httpreader", SOURCE, "postgresqlwriter", TARGET,
            "full_refresh", List.of("a"), List.of("b"))).isFalse();
        assertThat(ManagedDatabaseLandingPlan.isConfigured("mysqlreader", SOURCE, "postgresqlwriter", TARGET,
            "cdc", List.of("a"), List.of("b"))).isFalse();
    }

    private boolean configured(List<String> sources, List<String> targets) {
        return ManagedDatabaseLandingPlan.isConfigured("mysqlreader", SOURCE, "postgresqlwriter", TARGET,
            "full_refresh", sources, targets);
    }
}
