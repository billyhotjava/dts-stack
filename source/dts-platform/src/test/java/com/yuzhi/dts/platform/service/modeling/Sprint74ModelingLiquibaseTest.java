package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sprint74ModelingLiquibaseTest {

    @Test
    void reclassificationLedgerIsAppendOnlyAndRollbackRefusesToDiscardEvidence() throws IOException {
        String xml = read("/config/liquibase/changelog/20260727_05_model_spec_reclassification_command.xml");

        assertThat(xml)
            .contains("modeling_model_reclassification_command")
            .contains("BEFORE UPDATE OR DELETE OR TRUNCATE")
            .contains("ROLLBACK_BLOCKED_MODEL_RECLASSIFICATION_COMMAND_EXISTS")
            .doesNotContain("<sql>select 1;</sql>");
    }

    @Test
    void governancePolicyBackfillsStrictValuesBeforeInstallingNewPlanDefaults() throws IOException {
        String xml = read("/config/liquibase/changelog/20260727_06_warehouse_plan_governance_policy.xml");
        String master = read("/config/liquibase/master.xml");

        assertThat(xml)
            .contains("standard_coverage = 'ALL_FIELDS'")
            .contains("quality_gate = 'BLOCKING'")
            .contains("defaultValue=\"KEY_AND_MEASURE\"")
            .contains("ROLLBACK_BLOCKED_WAREHOUSE_GOVERNANCE_POLICY_DATA_EXISTS");
        assertThat(master.indexOf("20260727_05_model_spec_reclassification_command.xml"))
            .isLessThan(master.indexOf("20260727_06_warehouse_plan_governance_policy.xml"));
    }

    @Test
    void implementationSettingsDatabaseGuardAcceptsTheCanonicalSprint74OwnerFields() throws IOException {
        String xml = read("/config/liquibase/changelog/20260727_07_model_implementation_settings_contract.xml");
        String master = read("/config/liquibase/master.xml");

        assertThat(xml)
            .contains("CREATE OR REPLACE FUNCTION modeling_implementation_input_payload_valid")
            .contains("targetPhysicalName")
            .contains("loadStrategy")
            .contains("partitionFields")
            .contains("retentionDays")
            .contains("ROLLBACK_BLOCKED_MODEL_IMPLEMENTATION_SETTINGS_CONTRACT_FORWARD_ONLY");
        assertThat(master.indexOf("20260727_06_warehouse_plan_governance_policy.xml"))
            .isLessThan(master.indexOf("20260727_07_model_implementation_settings_contract.xml"));
    }

    private static String read(String path) throws IOException {
        try (var input = Sprint74ModelingLiquibaseTest.class.getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
