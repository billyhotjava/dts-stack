package com.yuzhi.dts.ingestion.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository.EnqueueCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionSecretRestoreAuditServiceTest {

    @Mock private IngestionSecretRestoreAuditOutboxRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPersistClassifiedRedactedRestoreEvidenceWithoutConfigurationPayload() throws Exception {
        UUID receipt = UUID.randomUUID();
        when(outbox.enqueue(any())).thenReturn(receipt);
        IngestionSecretRestoreAuditService service = new IngestionSecretRestoreAuditService(
            outbox,
            objectMapper,
            "tenant-a"
        );

        assertThat(service.recordResult(
            "xiezm",
            "release-2026-08-01",
            false,
            61L,
            91L,
            "4f9f2cab",
            "RESTORED",
            null
        )).isEqualTo(receipt);

        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(captor.capture());
        EnqueueCommand command = captor.getValue();
        assertThat(command.tenantId()).isEqualTo("tenant-a");
        assertThat(command.actor()).isEqualTo("xiezm");
        assertThat(command.classification()).isEqualTo("SENSITIVE_CONFIGURATION_RECOVERY");
        assertThat(command.batchId()).isEqualTo("release-2026-08-01");
        assertThat(command.revisionId()).isEqualTo(91L);
        assertThat(command.configChecksum()).isEqualTo("4f9f2cab");
        assertThat(command.payloadHash()).matches("[0-9a-f]{64}");

        JsonNode payload = objectMapper.readTree(command.payloadJson());
        assertThat(payload.path("actor").asText()).isEqualTo("xiezm");
        assertThat(payload.path("attributes").path("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(payload.path("attributes").path("revisionId").asLong()).isEqualTo(91L);
        assertThat(payload.path("attributes").path("configChecksum").asText()).isEqualTo("4f9f2cab");
        assertThat(command.payloadJson().toLowerCase())
            .doesNotContain("password", "clientsecret", "accesstoken", "sourceconfig", "destinationconfig");
    }

    @Test
    void shouldRejectMachineOrAnonymousActorsBeforeAuditPersistence() {
        IngestionSecretRestoreAuditService service = new IngestionSecretRestoreAuditService(
            outbox,
            objectMapper,
            "tenant-a"
        );

        for (String actor : new String[] { "anonymous", "system", "service:dts-platform", "_system:airflow" }) {
            assertThatThrownBy(() -> service.recordAttempt(actor, "batch-1", false, 1L, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
        }
    }
}
