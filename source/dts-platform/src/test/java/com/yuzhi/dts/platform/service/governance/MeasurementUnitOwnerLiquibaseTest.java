package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MeasurementUnitOwnerLiquibaseTest {

    @Test
    void createsARevisionLedgerWithCasFieldsAndNoHardDeleteCascade() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260719_06_measurement_unit_owner.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("tableName=\"governance_measurement_unit\"")
            .contains("tableName=\"governance_measurement_unit_revision\"")
            .contains("name=\"base_unit_ref\"")
            .contains("name=\"conversion_factor\"")
            .contains("name=\"precision\"")
            .contains("name=\"status\"")
            .contains("name=\"version\"")
            .contains("name=\"snapshot_json\"")
            .contains("ck_measurement_unit_factor_positive")
            .contains("ck_measurement_unit_precision")
            .contains("uk_measurement_unit_code_ci")
            .contains("ROLLBACK_BLOCKED_MEASUREMENT_UNIT_DATA_EXISTS")
            .doesNotContain("onDelete=\"CASCADE\"");
    }
}
