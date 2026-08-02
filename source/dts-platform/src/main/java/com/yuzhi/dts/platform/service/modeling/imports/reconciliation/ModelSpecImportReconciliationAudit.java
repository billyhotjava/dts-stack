package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Allowlisted, payload-safe audit boundary for conflict decisions and forward undo. */
@Component
public class ModelSpecImportReconciliationAudit {

    static final String FORWARD_UNDO_ACTION = "MODELING_DBT_IMPORT_FORWARD_UNDO";
    static final String CONFLICT_ACTION = "MODELING_DBT_IMPORT_CONFLICT";

    private final AuditService auditService;

    public ModelSpecImportReconciliationAudit(AuditService auditService) {
        this.auditService = auditService;
    }

    public void beginForwardUndo(UUID attemptId, UUID targetAttemptId, UUID runId, int selectedCount) {
        auditService.auditActionStrict(
            FORWARD_UNDO_ACTION,
            AuditStage.BEGIN,
            attemptId.toString(),
            Map.of(
                "attemptId", attemptId,
                "targetAttemptId", targetAttemptId,
                "runId", runId,
                "selectedCount", selectedCount
            )
        );
    }

    public void beginConflictDecisions(UUID runId, Map<String, ?> decisions) {
        if (decisions == null || decisions.isEmpty()) return;
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        payload.put("decisionCount", decisions.size());
        payload.put(
            "decisions",
            decisions.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> String.valueOf(entry.getValue()),
                    (left, right) -> left,
                    java.util.TreeMap::new
                )
            )
        );
        auditService.auditActionStrict(
            CONFLICT_ACTION,
            AuditStage.BEGIN,
            runId == null ? "model-import-conflict" : runId.toString(),
            payload
        );
    }

}
