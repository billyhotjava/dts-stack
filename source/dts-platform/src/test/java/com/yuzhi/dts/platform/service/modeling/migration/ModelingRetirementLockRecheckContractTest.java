package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelingRetirementLockRecheckContractTest {

    @Test
    void destructiveRetirementsLockAndRecheckInsideOneTransaction() throws Exception {
        Map<String, String> migrations = Map.of(
            "20260801_01_retire_legacy_semantic_modeling.xml",
            "LEGACY_SEMANTIC_MODELING_RETIREMENT_LOCKED_RECHECK_FAILED",
            "20260801_02_retire_model_spec_v1.xml",
            "MODEL_SPEC_V1_SCHEMA_RETIREMENT_LOCKED_RECHECK_FAILED",
            "20260801_03_retire_dimension_definition_legacy_migration.xml",
            "DIMENSION_DEFINITION_LEGACY_MIGRATION_RETIREMENT_LOCKED_RECHECK_FAILED",
            "20260801_05_retire_modeling_plan_version_review.xml",
            "MODELING_PLAN_VERSION_REVIEW_RETIREMENT_LOCKED_RECHECK_FAILED",
            "20260801_07_retire_legacy_modeling_plan_sql_model.xml",
            "LEGACY_MODELING_PLAN_SQL_MODEL_RETIREMENT_LOCKED_RECHECK_FAILED"
        );

        for (Map.Entry<String, String> migration : migrations.entrySet()) {
            String resource = "/config/liquibase/changelog/" + migration.getKey();
            String xml;
            try (var input = getClass().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(xml)
                .as(migration.getKey())
                .contains("runInTransaction=\"true\"")
                .contains("LOCK TABLE")
                .contains("ACCESS EXCLUSIVE MODE")
                .contains(migration.getValue())
                .contains("<rollback>")
                .contains("FORWARD_ONLY");
        }
    }
}
