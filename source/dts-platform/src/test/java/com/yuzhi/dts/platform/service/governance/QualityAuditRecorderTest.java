package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class QualityAuditRecorderTest {

    @Test
    void delegatesOnlyToStrictAuditWrites() {
        AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
        QualityAuditRecorder recorder = new QualityAuditRecorder(auditService);
        Instant occurredAt = Instant.parse("2026-08-01T01:00:00Z");

        recorder.recordAction("GOV_QUALITY_TASK_CREATE", AuditStage.SUCCESS, "task-1", Map.of("summary", "ok"));
        recorder.recordFailureAction("GOV_QUALITY_TASK_CREATE", "task-1", Map.of("summary", "failed"));
        recorder.recordMachine(
            "scheduler",
            "quality-task:task-1:SUCCESS",
            occurredAt,
            "GOV_QUALITY_TASK_EXECUTE",
            AuditStage.SUCCESS,
            "task-1",
            Map.of("summary", "ok")
        );

        verify(auditService).auditActionStrict(
            eq("GOV_QUALITY_TASK_CREATE"), eq(AuditStage.SUCCESS), eq("task-1"), any()
        );
        verify(auditService).auditActionStrict(
            eq("GOV_QUALITY_TASK_CREATE"), eq(AuditStage.FAIL), eq("task-1"), any()
        );
        verify(auditService).auditActionAsStrict(
            eq("scheduler"),
            eq("quality-task:task-1:SUCCESS"),
            eq(occurredAt),
            eq("GOV_QUALITY_TASK_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq("task-1"),
            any()
        );
    }

    @Test
    void successJoinsBusinessTransactionAndFailureUsesIndependentTransaction() throws Exception {
        Transactional action = QualityAuditRecorder.class
            .getMethod("recordAction", String.class, AuditStage.class, String.class, Object.class)
            .getAnnotation(Transactional.class);
        Transactional failure = QualityAuditRecorder.class
            .getMethod("recordFailureAction", String.class, String.class, Object.class)
            .getAnnotation(Transactional.class);
        Transactional machine = QualityAuditRecorder.class
            .getMethod(
                "recordMachine",
                String.class,
                String.class,
                Instant.class,
                String.class,
                AuditStage.class,
                String.class,
                Object.class
            )
            .getAnnotation(Transactional.class);

        assertThat(action.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(machine.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(failure.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
