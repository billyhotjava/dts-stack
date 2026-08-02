package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.UndoTargetItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportForwardUndoPlannerTest {

    private final ModelSpecImportForwardUndoPlanner planner = new ModelSpecImportForwardUndoPlanner();

    @Test
    void freezesServerSideDownstreamClosureInReverseTopology() {
        UndoTargetItem staging = item("model.demo.stg_order", "UPDATE", List.of());
        UndoTargetItem fact = item("model.demo.fact_order", "UPDATE", List.of(staging.dbtUniqueId()));
        UndoTargetItem mart = item("model.demo.mart_order", "UPDATE", List.of(fact.dbtUniqueId()));

        var plan = planner.plan(
            List.of(staging.dbtUniqueId()),
            List.of(staging, fact, mart),
            Map.of(staging.dbtUniqueId(), staging.postPins(), fact.dbtUniqueId(), fact.postPins(), mart.dbtUniqueId(), mart.postPins())
        );

        assertThat(plan.selectedUniqueIds()).containsExactly(staging.dbtUniqueId());
        assertThat(plan.reverseClosure()).extracting(UndoTargetItem::dbtUniqueId)
            .containsExactly(mart.dbtUniqueId(), fact.dbtUniqueId(), staging.dbtUniqueId());
    }

    @Test
    void rejectsClientPinsThatDoNotCoverTheServerClosure() {
        UndoTargetItem staging = item("model.demo.stg_order", "UPDATE", List.of());
        UndoTargetItem fact = item("model.demo.fact_order", "UPDATE", List.of(staging.dbtUniqueId()));

        assertThatThrownBy(() ->
            planner.plan(
                List.of(staging.dbtUniqueId()),
                List.of(staging, fact),
                Map.of(staging.dbtUniqueId(), staging.postPins())
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedCurrentRevisions");
    }

    @Test
    void defaultsToAllHandledTargetItemsAndNeverInventsAnUnknownSelection() {
        UndoTargetItem updated = item("model.demo.updated", "UPDATE", List.of());
        UndoTargetItem skipped = item("model.demo.skipped", "SKIP", List.of());

        var plan = planner.plan(
            List.of(),
            List.of(updated, skipped),
            Map.of(updated.dbtUniqueId(), updated.postPins(), skipped.dbtUniqueId(), skipped.postPins())
        );
        assertThat(plan.reverseClosure()).extracting(UndoTargetItem::dbtUniqueId)
            .containsExactly(skipped.dbtUniqueId(), updated.dbtUniqueId());

        assertThatThrownBy(() ->
            planner.plan(
                List.of("model.demo.unknown"),
                List.of(updated, skipped),
                Map.of(updated.dbtUniqueId(), updated.postPins(), skipped.dbtUniqueId(), skipped.postPins())
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("target attempt");
    }

    private static UndoTargetItem item(String uniqueId, String action, List<String> dependencies) {
        UUID id = UUID.nameUUIDFromBytes(uniqueId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new UndoTargetItem(
            id,
            uniqueId,
            action,
            dependencies,
            pins(1),
            "UPDATE".equals(action) ? pins(0) : null
        );
    }

    private static RevisionPins pins(int offset) {
        return new RevisionPins(
            10 + offset,
            String.valueOf((char) ('a' + offset)).repeat(64),
            20 + offset,
            String.valueOf((char) ('c' + offset)).repeat(64)
        );
    }
}
