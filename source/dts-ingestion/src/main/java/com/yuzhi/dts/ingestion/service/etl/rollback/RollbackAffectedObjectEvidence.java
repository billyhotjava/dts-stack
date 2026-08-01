package com.yuzhi.dts.ingestion.service.etl.rollback;

import java.util.Map;

public record RollbackAffectedObjectEvidence(
    String objectType,
    String objectRef,
    String action,
    String status,
    Map<String, Object> evidence
) {
    public RollbackAffectedObjectEvidence {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public static RollbackAffectedObjectEvidence planned(String objectType, String objectRef, String action) {
        return new RollbackAffectedObjectEvidence(objectType, objectRef, action, "PLANNED", Map.of());
    }
}
