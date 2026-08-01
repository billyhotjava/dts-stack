package com.yuzhi.dts.platform.service.modeling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelMaterializationAvailabilityAuditServiceTest {

    @Test
    void runtimeFenceDenialUsesStrictMachineAudit() {
        AuditService audit = mock(AuditService.class);
        ModelMaterializationAvailabilityAuditService service = new ModelMaterializationAvailabilityAuditService(audit);
        UUID dispatchId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RuntimeSpecRecord runtime = mock(RuntimeSpecRecord.class);
        org.mockito.Mockito.when(runtime.tenantId()).thenReturn("tenant-a");
        org.mockito.Mockito.when(runtime.candidateId()).thenReturn(
            UUID.fromString("11111111-1111-1111-1111-111111111111")
        );
        org.mockito.Mockito.when(runtime.candidateVersion()).thenReturn(3);
        org.mockito.Mockito.when(runtime.attempt()).thenReturn(2);
        org.mockito.Mockito.when(runtime.dispatchId()).thenReturn(dispatchId);
        org.mockito.Mockito.when(runtime.airflowRunId()).thenReturn("run-17");
        Instant occurredAt = Instant.parse("2026-08-01T02:03:04Z");

        service.recordRuntimeDenied(runtime, "MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE", occurredAt);

        verify(audit).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-runtime-spec:" + dispatchId + ":attempt:2:blocked"),
            eq(occurredAt),
            eq("MODEL_MATERIALIZATION_RUNTIME_SPEC_BLOCKED"),
            eq(AuditStage.FAIL),
            eq(dispatchId.toString()),
            any()
        );
    }
}
