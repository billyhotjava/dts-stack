package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IndicatorBusinessContextLiquibaseTest {

    @Test
    void expandMigrationKeepsLegacyColumnsAndCreatesReversibleEvidence() throws Exception {
        String xml = Files.readString(
            Path.of("src/main/resources/config/liquibase/changelog/20260810_03_indicator_business_context_expand.xml")
        );

        assertThat(xml)
            .contains("author=\"xiezm\"")
            .contains("business_category_id")
            .contains("data_domain_id")
            .contains("business_process_id")
            .contains("metric_type")
            .contains("source_refs")
            .contains("indicator_context_migration_issue")
            .contains("indicator_context_migration_batch")
            .contains("indicator_context_migration_evidence")
            .contains("BUSINESS_CATEGORY_MATCH_AMBIGUOUS")
            .contains("DERIVED_TYPE_AMBIGUOUS")
            .doesNotContain("dropColumn tableName=\"gov_indicator_definition\" columnName=\"category\"")
            .doesNotContain("UPDATE gov_indicator_definition SET category");
    }

    @Test
    void platformMasterIncludesTheIndicatorExpandMigration() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/config/liquibase/master.xml")))
            .contains("20260810_03_indicator_business_context_expand.xml");
    }
}
