package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelSpecBusinessContextLiquibaseTest {

    @Test
    void expandMigrationPinsStableContextWithoutGuessingLegacyValues() throws IOException {
        String master = read("/config/liquibase/master.xml");
        String changelog = read(
            "/config/liquibase/changelog/20260810_01_model_spec_business_context_expand.xml"
        );

        assertThat(master).contains("20260810_01_model_spec_business_context_expand.xml");
        assertThat(changelog)
            .contains("author=\"xiezm\"")
            .contains("business_process_id")
            .contains("subject_domain_id")
            .contains("modeling_model_spec_revision")
            .contains("modeling_model_context_migration_issue")
            .contains("resolution_status")
            .contains("<rollback>")
            .doesNotContain("UPDATE modeling_model_spec SET business_process_id")
            .doesNotContain("ORDER BY");
    }

    private String read(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
