package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelSpecV1SchemaRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260801_02_retire_model_spec_v1.xml";

    @Test
    void retirementIsFailClosedAndDropsOnlyTheV1Schema() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("MODEL_SPEC_V1_SCHEMA_RETIREMENT_BLOCKED")
            .contains("dropTable tableName=\"modeling_standard_binding\"")
            .contains("dropTable tableName=\"modeling_business_object\"")
            .contains("ALTER COLUMN contract_version SET DEFAULT 2")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_V1_SCHEMA_RETIREMENT_FORWARD_ONLY")
            .doesNotContain("dropColumn tableName=\"modeling_model_spec\" columnName=\"grain_statement\"")
            .doesNotContain("dropColumn tableName=\"modeling_model_spec\" columnName=\"materialization\"");

        for (String column : legacyModelSpecColumns()) {
            assertThat(xml).contains(
                "dropColumn tableName=\"modeling_model_spec\" columnName=\"" + column + "\""
            );
        }
        assertThat(xml).contains(
            "dropColumn tableName=\"modeling_model_spec_revision\" columnName=\"spec_json\""
        );
    }

    @Test
    void rebuiltConstraintsAreCanonicalOnlyAndDoNotReferenceDroppedColumns() throws Exception {
        String xml = resource(CHANGELOG);

        assertThat(constraint(xml, "ck_model_spec_contract_version"))
            .contains("contract_version = 2")
            .doesNotContain("IN (1, 2)");
        assertThat(constraint(xml, "ck_model_spec_v2_identity"))
            .contains("contract_version = 2")
            .doesNotContain("object_id", "process_id");
        assertThat(constraint(xml, "ck_model_spec_v2_type_context"))
            .contains("contract_version = 2")
            .doesNotContain("contract_version <> 2");
        assertThat(constraint(xml, "ck_model_spec_v2_json_shapes"))
            .contains("contract_version = 2")
            .doesNotContain("legacy_refs", "contract_version <> 2");
        assertThat(constraint(xml, "ck_model_spec_revision_contract_version"))
            .contains("contract_version = 2")
            .doesNotContain("IN (1, 2)");
        assertThat(constraint(xml, "ck_model_spec_revision_payload"))
            .contains("contract_version = 2", "snapshot_json IS NOT NULL")
            .doesNotContain("spec_json", "contract_version = 1");
    }

    @Test
    void masterIncludesV1RetirementAfterLegacySemanticRetirement() throws Exception {
        String master = resource("/config/liquibase/master.xml");

        assertThat(master.indexOf("20260801_02_retire_model_spec_v1.xml"))
            .isGreaterThan(master.indexOf("20260801_01_retire_legacy_semantic_modeling.xml"));
    }

    private String constraint(String xml, String constraintName) {
        String marker = "ADD CONSTRAINT " + constraintName;
        int start = xml.lastIndexOf(marker);
        assertThat(start).as(constraintName).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf(';', start);
        assertThat(end).as(constraintName).isGreaterThan(start);
        return xml.substring(start, end + 1);
    }

    private List<String> legacyModelSpecColumns() {
        return List.of(
            "object_id",
            "process_id",
            "dimensions",
            "metrics",
            "dbt_project_key",
            "dbt_unique_id",
            "legacy_ref",
            "legacy_refs"
        );
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
