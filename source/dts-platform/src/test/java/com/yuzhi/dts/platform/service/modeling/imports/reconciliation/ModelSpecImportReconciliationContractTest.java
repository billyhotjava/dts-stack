package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ForwardUndoRequest;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportReconciliationContractTest {

    @Test
    void forwardUndoRequestRequiresServerVerifiablePinsAndBoundedIdentity() {
        UUID target = UUID.randomUUID();
        RevisionPins pins = pins();

        ForwardUndoRequest request = new ForwardUndoRequest(
            target,
            List.of("model.finance.fact_budget"),
            Map.of("model.finance.fact_budget", pins),
            "undo-budget-v1"
        );

        assertThat(request.targetAttemptId()).isEqualTo(target);
        assertThat(request.expectedCurrentRevisions()).containsEntry("model.finance.fact_budget", pins);
        assertThatThrownBy(() -> new ForwardUndoRequest(null, List.of(), Map.of(), "key"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ForwardUndoRequest(target, List.of("x".repeat(513)), Map.of(), "key"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ForwardUndoRequest(target, List.of(), Map.of(), "x".repeat(257)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conflictResolutionVocabularyIsClosed() {
        assertThat(ConflictResolution.values())
            .containsExactly(
                ConflictResolution.KEEP_CURRENT,
                ConflictResolution.ACCEPT_INCOMING,
                ConflictResolution.CANCEL
            );
    }

    private static RevisionPins pins() {
        return new RevisionPins(3, "a".repeat(64), 5, "b".repeat(64));
    }
}
