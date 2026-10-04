package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelImplementationInputsLiquibaseTest {

    @Test
    void createsStrictAppendOnlyImplementationInputLedgerAndBackfillsLegacyHeads() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260724_02_model_implementation_inputs.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("modeling_model_implementation_revision")
            .contains("implementation_revision")
            .contains("current_implementation_checksum")
            .contains("inputs_json")
            .contains("field_mappings_json")
            .contains("settings_json")
            .contains("physical_asset_ref")
            .contains("INSERT INTO modeling_model_implementation_revision")
            .contains("ON CONFLICT (tenant_id, implementation_id, revision) DO NOTHING")
            .contains("modeling_implementation_input_payload_valid")
            .contains("PHYSICAL_ASSET")
            .contains("UPSTREAM_MODEL")
            .contains("GENERATED")
            .contains("jsonb_typeof(inputs) <> 'array'")
            .contains("jsonb_typeof(mappings) <> 'array'")
            .contains("jsonb_typeof(settings) <> 'object'")
            .contains("ROLLBACK_BLOCKED_MODEL_IMPLEMENTATION_INPUT_LEDGER_FORWARD_ONLY");
    }

    @Test
    void upgradesUpstreamModelInputsToTheImmutableSixFieldPin() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260724_08_upstream_model_pin_contract.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("modeling_implementation_input_payload_v2_valid")
            .contains("implementationRevision")
            .contains("implementationChecksum")
            .contains("dbtUniqueId")
            .contains("jsonb_object_keys(item)) <> 6")
            .contains("ck_modeling_implementation_input_payload")
            .contains("ck_modeling_implementation_revision_payload")
            .contains(") NOT VALID;")
            .contains("ROLLBACK_BLOCKED_UPSTREAM_MODEL_PIN_CONTRACT_FORWARD_ONLY");
    }
}
