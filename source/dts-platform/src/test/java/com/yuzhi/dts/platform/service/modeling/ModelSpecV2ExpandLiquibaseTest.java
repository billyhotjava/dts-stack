package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelSpecV2ExpandLiquibaseTest {

    @Test
    void expandsTheExistingLedgerWithoutDroppingLegacyIdentity() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream("/config/liquibase/changelog/20260719_03_model_spec_v2_expand.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("tableName=\"modeling_model_spec\"")
            .contains("columnName=\"object_id\"")
            .contains("columnName=\"process_id\"")
            .contains("name=\"contract_version\"")
            .contains("name=\"domain_id\"")
            .contains("name=\"consumption_scenario\"")
            .contains("name=\"idempotency_response_snapshot\"")
            .contains("name=\"snapshot_json\"")
            .contains("dropNotNullConstraint tableName=\"modeling_model_spec_revision\" columnName=\"spec_json\"")
            .contains("ck_model_spec_revision_payload")
            .contains("uk_model_spec_v2_idempotency")
            .contains("uk_model_spec_v2_plan_name")
            .contains("fk_model_spec_v2_tenant_plan")
            .contains("fk_model_spec_v2_domain")
            .contains("status IN ('DRAFT', 'DESIGNING', 'VALIDATING', 'READY_TO_PUBLISH', 'PUBLISHED', 'ARCHIVED')")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_V2_DATA_EXISTS")
            .doesNotContain("dropColumn tableName=\"modeling_model_spec\" columnName=\"object_id\"")
            .doesNotContain("dropColumn tableName=\"modeling_model_spec\" columnName=\"process_id\"");
    }
}
