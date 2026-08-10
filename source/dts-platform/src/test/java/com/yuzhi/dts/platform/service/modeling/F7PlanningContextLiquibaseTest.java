package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class F7PlanningContextLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260810_04_modeling_planning_context_policy.xml";

    @Test
    void masterRegistersTheF7PlanningContextExpandMigration() throws IOException {
        assertThat(read("/config/liquibase/master.xml")).contains("20260810_04_modeling_planning_context_policy.xml");
    }

    @Test
    void migrationAddsCompatiblePolicyDefaultsAndRetirableBusinessProcesses() throws IOException {
        String xml = read(CHANGELOG);

        assertThat(xml).contains("name=\"business_category_mode\"");
        assertThat(xml).contains("name=\"default_business_category_id\"");
        assertThat(xml).contains("name=\"business_process_mode\"");
        assertThat(xml).contains("defaultValue=\"SINGLE_DEFAULT\"");
        assertThat(xml).contains("defaultValue=\"AUTO_SELECT_SINGLE\"");
        assertThat(xml).contains("tableName=\"sprint64_business_process\"");
        assertThat(xml).contains("name=\"lifecycle_status\"");
        assertThat(xml).contains("defaultValue=\"ACTIVE\"");
        assertThat(xml).contains("<rollback>");
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = F7PlanningContextLiquibaseTest.class.getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
