package com.yuzhi.dts.ingestion.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository.EnqueueCommand;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class IngestionSecretRestoreAuditTenantConfigurationTest {

    @Test
    void shouldIgnoreRetiredAuditTenantEnvironmentVariableInFavorOfInternalConfiguration() {
        IngestionSecretRestoreAuditOutboxRepository outbox = mock(IngestionSecretRestoreAuditOutboxRepository.class);
        when(outbox.enqueue(any())).thenReturn(UUID.randomUUID());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context
                .getEnvironment()
                .getPropertySources()
                .addFirst(
                    new MapPropertySource(
                        "audit-tenancy-test",
                        Map.of("AUDIT_TENANT_ID", "retired-tenant", "auditing.tenant-id", "default")
                    )
                );
            context.registerBean(IngestionSecretRestoreAuditOutboxRepository.class, () -> outbox);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(IngestionSecretRestoreAuditService.class);
            context.refresh();

            context
                .getBean(IngestionSecretRestoreAuditService.class)
                .recordAttempt("xiezm", "single-tenant-check", true, 1L, null, null);
        }

        ArgumentCaptor<EnqueueCommand> command = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("default");
    }
}
