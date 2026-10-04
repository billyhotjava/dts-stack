package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelMaterializationAvailabilityPinLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_09_model_materialization_availability_pin.xml";

    @Test
    void recordsImmutableLeaseBoundGenerationEvidence() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("name=\"availability_pinned_at\"")
            .contains("name=\"availability_pin_count\"")
            .contains("tableName=\"modeling_materialization_source_pin\"")
            .contains("name=\"availability_epoch\"")
            .contains("name=\"source_sequence\"")
            .contains("name=\"availability_event_id\"")
            .contains("name=\"resolved_version\"")
            .contains("fk_materialization_source_pin_dispatch")
            .contains("fk_materialization_source_pin_binding")
            .contains("availability_status = 'AVAILABLE'")
            .contains("MODEL_MATERIALIZATION_SOURCE_PIN_APPEND_ONLY")
            .contains("MODEL_MATERIALIZATION_AVAILABILITY_MARKER_IMMUTABLE")
            .contains("BEFORE TRUNCATE ON modeling_materialization_source_pin");
    }

    @Test
    void followsCatalogAvailabilityAndBlocksEvidenceDroppingRollback()
        throws Exception {
        String master = read("/config/liquibase/master.xml");
        String xml = read(CHANGELOG);

        assertThat(master).containsSubsequence(
            "20260801_08_rollback_invalidation_availability.xml",
            "20260801_09_model_materialization_availability_pin.xml"
        );
        assertThat(xml)
            .contains("IN ACCESS EXCLUSIVE MODE")
            .contains(
                "ROLLBACK_BLOCKED_MODEL_MATERIALIZATION_AVAILABILITY_PIN_EXISTS"
            )
            .contains("DROP TRIGGER IF EXISTS trg_materialization_source_pin_append_only")
            .contains("DROP FUNCTION IF EXISTS modeling_reject_materialization_source_pin_mutation()")
            .contains("<dropTable tableName=\"modeling_materialization_source_pin\"/>");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
