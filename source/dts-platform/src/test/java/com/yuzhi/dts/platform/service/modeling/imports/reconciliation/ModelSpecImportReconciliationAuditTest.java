package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecImportReconciliationAuditTest {

    @Test
    void emitsAllowlistedForwardUndoBeginWithoutImplementationPayloads() {
        AuditService auditService = mock(AuditService.class);
        ModelSpecImportReconciliationAudit audit = new ModelSpecImportReconciliationAudit(auditService);
        UUID attemptId = UUID.randomUUID();
        UUID targetAttemptId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        audit.beginForwardUndo(attemptId, targetAttemptId, runId, 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditActionStrict(
            eq("MODELING_DBT_IMPORT_FORWARD_UNDO"),
            eq(AuditStage.BEGIN),
            eq(attemptId.toString()),
            payload.capture()
        );
        assertThat(payload.getValue())
            .containsOnlyKeys("attemptId", "targetAttemptId", "runId", "selectedCount")
            .doesNotContainKeys("sql", "archive", "payload", "message");
    }

    @Test
    void emitsConflictBeginUsingOnlyDecisionFacts() {
        AuditService auditService = mock(AuditService.class);
        ModelSpecImportReconciliationAudit audit = new ModelSpecImportReconciliationAudit(auditService);
        UUID runId = UUID.randomUUID();
        Map<String, ConflictResolution> decisions = Map.of(
            "model.finance.fact_budget",
            ConflictResolution.KEEP_CURRENT
        );
        audit.beginConflictDecisions(runId, decisions);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(auditService).auditActionStrict(
            eq("MODELING_DBT_IMPORT_CONFLICT"),
            eq(AuditStage.BEGIN),
            eq(runId.toString()),
            payload.capture()
        );
        assertThat(payload.getValue())
            .containsOnlyKeys("runId", "decisionCount", "decisions")
            .doesNotContainKeys("sql", "archive", "payload", "message");
    }
}
