package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes strict data-quality audit evidence with transaction semantics chosen by the caller's outcome. */
@Service
public class QualityAuditRecorder {

    private final AuditService auditService;

    public QualityAuditRecorder(AuditService auditService) {
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAction(String actionCode, AuditStage stage, String resourceId, Object payload) {
        auditService.auditActionStrict(actionCode, stage, resourceId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureAction(String actionCode, String resourceId, Object payload) {
        auditService.auditActionStrict(actionCode, AuditStage.FAIL, resourceId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(String actionCode, AuditStage stage, String resourceId, Object payload) {
        auditService.auditActionStrict(actionCode, stage, resourceId, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMachine(
        String machineActor,
        String eventIdentity,
        Instant occurredAt,
        String actionCode,
        AuditStage stage,
        String resourceId,
        Object payload
    ) {
        auditService.auditActionAsStrict(machineActor, eventIdentity, occurredAt, actionCode, stage, resourceId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMachineAttempt(
        String machineActor,
        String eventIdentity,
        Instant occurredAt,
        String actionCode,
        AuditStage stage,
        String resourceId,
        Object payload
    ) {
        auditService.auditActionAsStrict(machineActor, eventIdentity, occurredAt, actionCode, stage, resourceId, payload);
    }
}
