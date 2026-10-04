package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.web.rest.QualityRulePreflightResource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class F8QualityPreflightAuditTest {
    @Test
    void previewRecordsAnIndependentAuditWithoutRequiringAnOuterTransaction() {
        var service = mock(QualityRulePreflightService.class);
        var audit = mock(QualityAuditRecorder.class);
        var request = new QualityRulePreflightService.Request(UUID.randomUUID(), Map.of("sql", "SELECT * FROM public.projects"));
        when(service.preview(request, "dept")).thenReturn(new QualityRulePreflightService.Preview("checksum",
            new QualityExecutionOutcome(1, "PASSED", "OK", "EXACT", 0L, List.of()), 10, 0));
        new QualityRulePreflightResource(service, audit).preview(request, "dept");
        verify(audit).recordAttempt(eq("GOV_RULE_DRY_RUN"), eq(AuditStage.SUCCESS), eq(request.datasetId().toString()), any());
        verify(audit, never()).recordAction(any(), any(), any(), any());
    }

    @Test
    void inaccessibleAssetNeverReachesTargetValidationOrExecution() {
        var access = mock(QualityDatasetReadGuard.class);
        var executor = mock(QualityDatasetStatementExecutor.class);
        var dataset = UUID.randomUUID();
        when(access.requireReadable(dataset, "dept")).thenThrow(new AccessDeniedException("denied"));
        var service = new QualityRulePreflightService(access, executor);
        var request = new QualityRulePreflightService.Request(dataset, Map.of("sql", "SELECT * FROM public.projects"));
        assertThatThrownBy(() -> service.preview(request, "dept")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(executor);
    }
}
