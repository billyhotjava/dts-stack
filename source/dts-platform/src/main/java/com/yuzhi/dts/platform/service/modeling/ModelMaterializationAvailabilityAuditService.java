package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists source-fence denials independently from the command transaction that must roll back. */
@Service
public class ModelMaterializationAvailabilityAuditService {

    private final AuditService auditService;

    public ModelMaterializationAvailabilityAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStartDenied(String actorId, UUID candidateId, int candidateVersion, String errorCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", actorId);
        payload.put("candidate", candidateId);
        payload.put("version", candidateVersion);
        payload.put("errorCode", errorCode);
        payload.put("status", "BLOCKED");
        auditService.auditActionStrict(
            "MODEL_MATERIALIZATION_SOURCE_FENCE_DENIED",
            AuditStage.FAIL,
            candidateId.toString(),
            Map.copyOf(payload)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRuntimeDenied(RuntimeSpecRecord runtime, String errorCode, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant", runtime.tenantId());
        payload.put("candidate", runtime.candidateId());
        payload.put("version", runtime.candidateVersion());
        payload.put("attempt", runtime.attempt());
        payload.put("dispatch", runtime.dispatchId());
        payload.put("run", runtime.airflowRunId());
        payload.put("errorCode", errorCode);
        payload.put("status", "BLOCKED");
        auditService.auditActionAsStrict(
            "airflow",
            "model-materialization-runtime-spec:" + runtime.dispatchId() + ":attempt:" + runtime.attempt() + ":blocked",
            occurredAt,
            "MODEL_MATERIALIZATION_RUNTIME_SPEC_BLOCKED",
            AuditStage.FAIL,
            runtime.dispatchId().toString(),
            Map.copyOf(payload)
        );
    }
}
