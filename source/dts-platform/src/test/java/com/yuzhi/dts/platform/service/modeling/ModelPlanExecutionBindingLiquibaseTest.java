package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelPlanExecutionBindingLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260728_05_model_plan_execution_binding.xml";

    @Test
    void ownsOneManualBindingPerPlanEnvironmentAndExecutionTargetWithRevisionBoundEntries() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260728_04_model_release_publication_requested.xml",
                "20260728_05_model_plan_execution_binding.xml"
            );
        assertThat(changelog)
            .contains("modeling_plan_execution_binding")
            .contains("modeling_plan_execution_binding_entry")
            .contains("tenant_id,plan_id,environment,execution_target_key")
            .contains("schedule_mode = 'MANUAL_ONLY'")
            .contains("desired_scope_checksum")
            .contains("desired_deployment_checksum")
            .contains("published_release_id")
            .contains("artifact_checksum")
            .contains("dependency_snapshot_checksum")
            .contains("ROLLBACK_BLOCKED_MODEL_PLAN_EXECUTION_BINDING_DATA_EXISTS");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
