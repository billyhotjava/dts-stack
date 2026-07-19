package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegacyObjectMigrationPlannerTest {

    private final LegacyObjectMigrationPlanner planner = new LegacyObjectMigrationPlanner();

    @Test
    void keepsUnmappedDomainsIsolatedAndProducesAStableBatchIdentity() {
        UUID legacyDomain = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var snapshots = List.of(
            new LegacyObjectMigrationPlanner.LegacyObjectSnapshot(
                "SEMANTIC",
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "order_summary",
                "订单汇总",
                null,
                legacyDomain,
                null,
                null,
                null,
                "DRAFT",
                List.of("order_id"),
                List.of(new LegacyObjectMigrationPlanner.LegacyModel("model-1", "DWS", List.of("day"))),
                List.of(new LegacyObjectMigrationPlanner.LegacySource("mapping-1", "dwd_order", "LEFT", "a.id = b.id", 1)),
                1,
                2,
                3,
                1,
                1
            )
        );
        Map<String, Long> counts = Map.of("semanticBusinessObject", 1L, "semanticModel", 1L);
        Map<String, Long> orphans = Map.of("modelWithoutObject", 0L);

        var first = planner.plan(counts, orphans, snapshots);
        var second = planner.plan(counts, orphans, snapshots);

        assertThat(first.checksum()).isEqualTo(second.checksum());
        assertThat(first.batchId()).isEqualTo(second.batchId());
        assertThat(first.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.classification()).isEqualTo(LegacyObjectMigrationPlanner.Classification.NEEDS_CLASSIFICATION);
            assertThat(decision.ready()).isFalse();
            assertThat(decision.blockers()).contains("DOMAIN_CLASSIFICATION_REQUIRED");
            assertThat(decision.targetRef()).isNull();
        });
    }

    @Test
    void reportsJoinAndGrainConflictsWithoutInventingSourceMetadata() {
        UUID domain = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID plan = UUID.fromString("40000000-0000-0000-0000-000000000001");
        var snapshot = new LegacyObjectMigrationPlanner.LegacyObjectSnapshot(
            "SEMANTIC",
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "order_fact",
            "订单事实",
            "FACT",
            domain,
            domain,
            plan,
            "order-process",
            "ACTIVE",
            List.of("order_id"),
            List.of(new LegacyObjectMigrationPlanner.LegacyModel("model-2", "DWD", List.of("order_no"))),
            List.of(new LegacyObjectMigrationPlanner.LegacySource("mapping-2", "dim_customer", "DIMENSION", "o.customer_id = c.id", 2)),
            0,
            1,
            0,
            0,
            0
        );

        var report = planner.plan(Map.of("semanticBusinessObject", 1L), Map.of(), List.of(snapshot));

        assertThat(report.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.classification()).isEqualTo(LegacyObjectMigrationPlanner.Classification.MANUAL_SPLIT);
            assertThat(decision.blockers()).contains("GRAIN_CONFLICT", "SOURCE_JOIN_TYPE_REQUIRED");
            assertThat(decision.sourceRefs()).singleElement().satisfies(source -> {
                assertThat(source.joinType()).isNull();
                assertThat(source.legacyRawRole()).isEqualTo("DIMENSION");
                assertThat(source.joinExpressionChecksum()).matches("[0-9a-f]{64}");
            });
        });
    }

    @Test
    void identifiesAnExplicitDimensionCandidateWithoutBusinessObjectIdentityInTheTarget() {
        UUID domain = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID plan = UUID.fromString("70000000-0000-0000-0000-000000000001");
        var snapshot = new LegacyObjectMigrationPlanner.LegacyObjectSnapshot(
            "MODELING_VNEXT",
            UUID.fromString("80000000-0000-0000-0000-000000000001"),
            "customer",
            "客户维度",
            "DIMENSION",
            domain,
            domain,
            plan,
            "customer-management",
            "DRAFT",
            List.of("customer_id"),
            List.of(),
            List.of(),
            1,
            0,
            0,
            0,
            0
        );

        var report = planner.plan(Map.of("modelingBusinessObject", 1L), Map.of(), List.of(snapshot));

        assertThat(report.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.classification()).isEqualTo(LegacyObjectMigrationPlanner.Classification.AUTO_DIMENSION);
            assertThat(decision.targetType()).isEqualTo("DIMENSION");
            assertThat(decision.ready()).isTrue();
            assertThat(decision.targetRef()).isNull();
        });
    }
}
