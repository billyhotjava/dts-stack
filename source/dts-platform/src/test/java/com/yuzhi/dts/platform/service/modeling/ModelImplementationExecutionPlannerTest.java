package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelImplementationExecutionPlannerTest {

    private static final String NODE_ID = "model.dts.model_finance_detail";

    @Test
    void plansFullTableAndViewFromTheRealUiSettings() {
        var table = plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "FULL",
                "partitionFields", List.of(),
                "retentionDays", 365,
                "casts", Map.of("amount", "decimal"),
                "deduplicateBy", List.of("finance_id")
            ),
            "table"
        );
        var view = plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dws_finance_summary",
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            "view"
        );

        assertThat(table.valid()).isTrue();
        assertThat(table.code()).isEqualTo("MODEL_IMPLEMENTATION_VALID");
        assertThat(table.blockers()).isEmpty();
        assertThat(table.executionPlan()).satisfies(plan -> {
            assertThat(plan.engine()).isEqualTo("DBT");
            assertThat(plan.adapter()).isEqualTo("postgres");
            assertThat(plan.nodeUniqueId()).isEqualTo(NODE_ID);
            assertThat(plan.selector()).isEqualTo("model_finance_detail");
            assertThat(plan.targetIdentifier()).isEqualTo("dwd_finance_detail");
            assertThat(plan.effectiveMaterialization()).isEqualTo("table");
            assertThat(plan.physicalExpected()).isTrue();
            assertThat(plan.uniqueKey()).containsExactly("finance_id");
            assertThat(plan.capabilityCodes()).containsExactly("FULL_TABLE", "RELATION_PROBE");
        });
        assertThat(view.executionPlan().effectiveMaterialization()).isEqualTo("view");
        assertThat(view.executionPlan().capabilityCodes()).containsExactly("FULL_VIEW", "RELATION_PROBE");
    }

    @Test
    void plansIncrementalOnlyWhenCanonicalKeyAndMaterializationAgree() {
        var valid = plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "INCREMENTAL",
                "partitionFields", List.of()
            ),
            "incremental"
        );
        var missingKey = plan(
            List.of(),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "INCREMENTAL",
                "partitionFields", List.of()
            ),
            "incremental"
        );
        var conflict = plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "INCREMENTAL",
                "partitionFields", List.of()
            ),
            "table"
        );

        assertThat(valid.valid()).isTrue();
        assertThat(valid.executionPlan().effectiveMaterialization()).isEqualTo("incremental");
        assertThat(valid.executionPlan().capabilityCodes()).containsExactly("INCREMENTAL_UNIQUE_KEY", "RELATION_PROBE");
        assertThat(missingKey.code()).isEqualTo("IMPLEMENTATION_INCREMENTAL_KEY_REQUIRED");
        assertThat(missingKey.blockers()).singleElement().extracting(ModelImplementationExecutionPlanner.Blocker::field)
            .isEqualTo("fields");
        assertThat(conflict.code()).isEqualTo("IMPLEMENTATION_MATERIALIZATION_CONFLICT");
    }

    @Test
    void failsClosedForMissingTargetSnapshotPartitionAndUnknownSettings() {
        assertThat(plan(
            List.of("finance_id"),
            Map.of("loadStrategy", "FULL", "partitionFields", List.of()),
            "table"
        ).code()).isEqualTo("IMPLEMENTATION_TARGET_REQUIRED");

        assertThat(plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "SNAPSHOT",
                "partitionFields", List.of()
            ),
            "table"
        ).code()).isEqualTo("IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED");

        assertThat(plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "FULL",
                "partitionFields", List.of("business_date")
            ),
            "table"
        ).code()).isEqualTo("IMPLEMENTATION_PARTITION_UNSUPPORTED");

        assertThat(plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "FULL",
                "partitionFields", List.of(),
                "whereSql", "1=1"
            ),
            "table"
        ).code()).isEqualTo("IMPLEMENTATION_SETTING_NOT_ALLOWED");

        assertThat(ModelImplementationExecutionPlanner.plan(
            List.of("finance_id"),
            Map.of(
                "targetPhysicalName", "dwd_finance_detail",
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            "table",
            "model.invalid-project.finance-detail",
            "postgres"
        ).code()).isEqualTo("IMPLEMENTATION_DBT_UNIQUE_ID_INVALID");
    }

    private static ModelImplementationExecutionPlanner.ValidationResult plan(
        List<String> keyFields,
        Map<String, Object> settings,
        String materialization
    ) {
        return ModelImplementationExecutionPlanner.plan(
            keyFields,
            settings,
            materialization,
            NODE_ID,
            "postgres"
        );
    }
}
