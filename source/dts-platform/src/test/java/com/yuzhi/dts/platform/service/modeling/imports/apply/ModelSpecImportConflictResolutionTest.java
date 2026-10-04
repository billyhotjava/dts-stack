package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportConflictResolutionTest {

    @Test
    void convertsOnlyExplicitConflictDecisionsAndPinsKeepCurrentSeparatelyFromIncoming() {
        Candidate conflict = candidate("CONFLICT");

        Candidate kept = ModelSpecImportApplyPreflightService.resolveConflict(conflict, ConflictResolution.KEEP_CURRENT);
        Candidate accepted = ModelSpecImportApplyPreflightService.resolveConflict(conflict, ConflictResolution.ACCEPT_INCOMING);
        Candidate cancelled = ModelSpecImportApplyPreflightService.resolveConflict(conflict, ConflictResolution.CANCEL);

        assertThat(kept.action()).isEqualTo("SKIP");
        assertThat(kept.proposedImplementationChecksum()).isEqualTo(conflict.expectedImplementationChecksum());
        assertThat(kept.incomingExternalChecksum()).isEqualTo(conflict.incomingExternalChecksum());
        assertThat(accepted.action()).isEqualTo("UPDATE");
        assertThat(accepted.proposedImplementationChecksum()).isEqualTo(conflict.proposedImplementationChecksum());
        assertThat(cancelled.action()).isEqualTo("CANCEL");
        assertThat(cancelled.targetRevision()).isEqualTo(conflict.expectedModelRevision());
    }

    @Test
    void rejectsAConflictDecisionForANonConflictCandidate() {
        assertThatThrownBy(() ->
            ModelSpecImportApplyPreflightService.resolveConflict(candidate("UPDATE"), ConflictResolution.ACCEPT_INCOMING)
        ).isInstanceOf(ModelSpecImportApplyContract.ModelSpecImportApplyException.class);
    }

    private static Candidate candidate(String action) {
        return new Candidate(
            "model.finance.fact_budget",
            UUID.randomUUID(),
            8,
            6,
            7,
            "a".repeat(64),
            "DRAFT",
            5,
            "b".repeat(64),
            "c".repeat(64),
            "d".repeat(64),
            action,
            "DBT_BACKED",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}",
            "e".repeat(64),
            List.of()
        );
    }
}
