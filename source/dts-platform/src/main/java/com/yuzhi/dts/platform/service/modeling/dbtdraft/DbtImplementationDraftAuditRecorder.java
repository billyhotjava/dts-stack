package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transaction-aware strict audit seam for dbt-draft checkpoints, failures and retention. */
@Service
public class DbtImplementationDraftAuditRecorder {

    private final AuditService auditService;

    public DbtImplementationDraftAuditRecorder(AuditService auditService) {
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordSuccess(String actionCode, String resourceId, Map<String, Object> payload) {
        return auditService.auditActionStrict(actionCode, AuditStage.SUCCESS, resourceId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordFailure(String actionCode, String resourceId, Map<String, Object> payload) {
        return auditService.auditActionStrict(actionCode, AuditStage.FAIL, resourceId, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordMachineSuccess(
        String actionCode,
        String eventIdentity,
        Instant occurredAt,
        String resourceId,
        Map<String, Object> payload
    ) {
        return auditService.auditActionAsStrict(
            "scheduler",
            eventIdentity,
            occurredAt,
            actionCode,
            AuditStage.SUCCESS,
            resourceId,
            payload
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordMachineFailure(
        String actionCode,
        String eventIdentity,
        Instant occurredAt,
        String resourceId,
        Map<String, Object> payload
    ) {
        return auditService.auditActionAsStrict(
            "scheduler",
            eventIdentity,
            occurredAt,
            actionCode,
            AuditStage.FAIL,
            resourceId,
            payload
        );
    }
}
