package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlanOperationalRunLiquibaseTest {

    @Test
    void addsBindingOwnedOperationalRunsWithoutCreatingAnotherRunLedger()
        throws Exception {
        String migration;
        try (
            var input = getClass()
                .getResourceAsStream(
                    "/config/liquibase/changelog/20260728_07_plan_operational_run.xml"
                )
        ) {
            assertThat(input).isNotNull();
            migration = new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }

        assertThat(migration)
            .contains(
                "<addColumn tableName=\"modeling_pipeline_run\">"
            )
            .contains("<column name=\"execution_binding_id\"")
            .contains("<column name=\"binding_version\"")
            .contains("<column name=\"trigger_type\"")
            .contains("<column name=\"scope_checksum\"")
            .contains(
                "run_purpose = 'OPERATIONAL_RUN'"
            )
            .contains(
                "modeling_operational_run_dispatch"
            )
            .contains(
                "uk_pipeline_run_operational_active_claim"
            )
            .contains(
                "scoped_bundle_checksum ~ '^[0-9a-f]{64}$'"
            )
            .doesNotContain(
                "createTable tableName=\"modeling_operational_run\""
            );
    }
}
