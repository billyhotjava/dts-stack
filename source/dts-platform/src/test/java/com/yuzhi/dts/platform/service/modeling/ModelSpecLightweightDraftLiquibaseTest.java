package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelSpecLightweightDraftLiquibaseTest {

    @Test
    void applicationDraftMayDeferItsConsumptionScenarioUntilLogicalDesign() throws Exception {
        String xml;
        try (
            var input = getClass()
                .getResourceAsStream("/config/liquibase/changelog/20260726_01_model_spec_lightweight_draft.xml")
        ) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("DROP CONSTRAINT ck_model_spec_v2_type_context")
            .contains("status = 'DRAFT'")
            .contains("model_type = 'APPLICATION' OR consumption_scenario IS NULL")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_LIGHTWEIGHT_DRAFT_FORWARD_ONLY");
    }
}
