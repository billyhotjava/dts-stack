package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelLifecycleLiquibaseTest {

    @Test
    void bindsArtifactsAndRunsToCanonicalRevisionAndKeepsDurableRegistrationAttempts() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(
            "/config/liquibase/changelog/20260720_02_model_spec_lifecycle.xml"
        )) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("modeling_dbt_artifact_legacy_orphan")
            .contains("dropUniqueConstraint tableName=\"modeling_dbt_artifact\"")
            .contains("addNotNullConstraint tableName=\"modeling_dbt_artifact\" columnName=\"model_spec_id\"")
            .contains("uk_modeling_dbt_artifact_owner_revision")
            .contains("name=\"model_revision\"")
            .contains("name=\"model_checksum\"")
            .contains("name=\"repair_path\"")
            .contains("modeling_model_lifecycle_event")
            .contains("modeling_model_registration_step")
            .contains("attempt_count");
    }
}
