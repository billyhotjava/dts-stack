package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.repository.RollbackAuditLogRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RollbackAuditServiceTest {

    @Test
    void failureAuditUsesTheSharedSanitizerForNestedSecretsAndOpaqueTokens() throws Exception {
        RollbackAuditLogRepository repository = mock(RollbackAuditLogRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        RollbackAuditService service = new RollbackAuditService(repository, objectMapper);

        var saved = service.recordFailure(
            null,
            RollbackAuditReason.ROLLBACK_FAILED,
            "operator",
            RollbackLevel.TRUNCATE_DATA,
            "task",
            7L,
            null,
            Map.of("connection", Map.of("password", "database-secret"), "tokenValue", "sk_live_1234567890abcdef"),
            Map.of(),
            null,
            "FAILED",
            "Authorization=Bearer abc.def.ghi"
        );

        String request = saved.getRequestJson();
        assertThat(request).contains("[REDACTED]").doesNotContain("database-secret", "sk_live_1234567890abcdef");
        assertThat(saved.getErrorMessage()).doesNotContain("abc.def.ghi");
        assertThat(saved.getReasonCode()).isEqualTo("ROLLBACK_FAILED");
    }
}
