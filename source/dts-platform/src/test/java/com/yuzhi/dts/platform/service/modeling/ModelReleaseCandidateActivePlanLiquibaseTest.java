package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateActivePlanLiquibaseTest {

    @Test
    void enforcesOneNonTerminalCandidatePerTenantPlanAndKeepsTerminalHistory() throws Exception {
        String changelog;
        try (
            var stream = getClass()
                .getResourceAsStream(
                    "/config/liquibase/changelog/20260724_06_model_release_candidate_active_plan.xml"
                )
        ) {
            assertThat(stream).isNotNull();
            changelog = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(changelog)
            .contains("uk_model_release_candidate_active_plan")
            .contains("ON modeling_model_release_candidate (tenant_id, plan_id)")
            .contains("status NOT IN ('REJECTED', 'ROLLED_BACK', 'STALE')")
            .contains("HAVING count(*) > 1")
            .contains("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_DATA_EXISTS")
            .contains("LOCK TABLE modeling_model_release_candidate IN ACCESS EXCLUSIVE MODE")
            .contains("dropIndex");
    }
}
