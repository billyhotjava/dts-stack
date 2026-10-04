package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacySemanticModelingRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_01_retire_legacy_semantic_modeling.xml";

    @Test
    void retirementIsFailClosedAndRemovesEveryLegacyStructure() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream(CHANGELOG)) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("<sqlCheck expectedResult=\"0\">")
            .contains("LEGACY_SEMANTIC_MODELING_DATA_REQUIRES_MIGRATION")
            .contains("LEGACY_MODELING_RETIREMENT_EVIDENCE_REQUIRES_ARCHIVE")
            .contains("LEGACY_SEMANTIC_MODELING_RETIREMENT_LOCKED_RECHECK_FAILED")
            .contains("ROLLBACK_BLOCKED_LEGACY_SEMANTIC_MODELING_RETIREMENT_FORWARD_ONLY");
        for (String table : legacyTables()) {
            assertThat(xml).contains("dropTable tableName=\"" + table + "\"");
        }
    }

    @Test
    void masterIncludesRetirementAfterAuditOutbox() throws Exception {
        String master;
        try (var input = getClass().getResourceAsStream("/config/liquibase/master.xml")) {
            assertThat(input).isNotNull();
            master = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(master.indexOf("20260801_01_retire_legacy_semantic_modeling.xml"))
            .isGreaterThan(master.indexOf("20260731_02_platform_audit_outbox.xml"));
    }

    private List<String> legacyTables() {
        return List.of(
            "semantic_model_dimension",
            "semantic_model_metric",
            "semantic_generated_artifact",
            "semantic_model_review_log",
            "semantic_model_run",
            "semantic_object_table_mapping",
            "semantic_dimension",
            "semantic_metric",
            "semantic_model",
            "semantic_business_object",
            "semantic_subject_domain",
            "modeling_legacy_object_migration",
            "modeling_legacy_object_migration_batch",
            "modeling_legacy_api_usage",
            "modeling_model_registration_step"
        );
    }
}
