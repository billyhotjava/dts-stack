package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class DbtImplementationDraftAuditRecorderTest {

    @Test
    void usesStrictDurableAuditForSuccessFailureAndMachinePurgeEvidence() throws Exception {
        AuditService audit = org.mockito.Mockito.mock(AuditService.class);
        DbtImplementationDraftAuditRecorder recorder = new DbtImplementationDraftAuditRecorder(audit);
        UUID receipt = UUID.randomUUID();
        Map<String, Object> payload = Map.of("correlationId", "corr-83", "fileCount", 2);
        when(audit.auditActionStrict("MODELING_DBT_DRAFT_SAVE", AuditStage.SUCCESS, "draft-83", payload))
            .thenReturn(receipt);

        assertThat(recorder.recordSuccess("MODELING_DBT_DRAFT_SAVE", "draft-83", payload)).isEqualTo(receipt);
        recorder.recordFailure("MODELING_DBT_DRAFT_COMMIT", "draft-83", payload);
        recorder.recordMachineSuccess(
            "MODELING_DBT_DRAFT_PURGE",
            "purge:corr-83",
            Instant.parse("2026-08-02T00:00:00Z"),
            "dbt-drafts",
            payload
        );

        verify(audit).auditActionStrict("MODELING_DBT_DRAFT_COMMIT", AuditStage.FAIL, "draft-83", payload);
        verify(audit).auditActionAsStrict(
            "scheduler",
            "purge:corr-83",
            Instant.parse("2026-08-02T00:00:00Z"),
            "MODELING_DBT_DRAFT_PURGE",
            AuditStage.SUCCESS,
            "dbt-drafts",
            payload
        );
        assertThat(
            DbtImplementationDraftAuditRecorder.class
                .getMethod("recordFailure", String.class, String.class, Map.class)
                .getAnnotation(Transactional.class)
                .propagation()
        ).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
