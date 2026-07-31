package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.EnqueueCommand;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditForwarderServiceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    void recordShouldPersistStableEventBeforeAnyRemoteDelivery() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_SPEC_CREATE";
        event.module = "modeling";
        event.resourceType = "modeling.model-spec";
        event.resourceId = "spec-1";

        service.record(event);

        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(captor.capture());
        EnqueueCommand command = captor.getValue();
        Map<String, Object> body = objectMapper.readValue(command.bodyJson(), MAP_TYPE);
        assertThat(command.eventId()).isNotBlank();
        assertThat(command.payloadHash()).hasSize(64);
        assertThat(body)
            .containsEntry("eventId", command.eventId())
            .containsEntry("producer", "dts-platform")
            .containsEntry("action", "MODEL_SPEC_CREATE")
            .containsEntry("resourceId", "spec-1");
    }

    @Test
    void recordShouldPersistEvenWhenRemoteGatewayIsUnavailable() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, new ObjectMapper());
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_RELEASE_CANDIDATE_PUBLISH";
        event.module = "modeling";

        service.record(event);

        verify(outbox).enqueue(any(EnqueueCommand.class));
    }

    @Test
    void recordShouldSkipMachineActors() {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, new ObjectMapper());
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "service:dts-analytics";
        event.action = "查看数据源列表";
        event.module = "platform.infra";

        service.record(event);

        verify(outbox, never()).enqueue(any(EnqueueCommand.class));
    }

    @Test
    void recordShouldPersistStableButtonCodeForPlatformLoginEvents() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGIN";
        event.module = "platform";
        event.operationType = "LOGIN";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGIN");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGIN");
    }

    @Test
    void recordShouldPersistStableButtonCodeForPlatformLogoutEvents() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGOUT";
        event.module = "platform";
        event.operationType = "LOGOUT";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
    }

    @Test
    void recordShouldRedactSensitiveNestedValuesBeforeHashingAndPersistence() throws Exception {
        PlatformAuditOutboxRepository outbox = mock(PlatformAuditOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditForwarderService service = new AuditForwarderService(enabledProperties(), outbox, objectMapper);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "MODEL_SPEC_UPDATE";
        event.module = "modeling";
        event.payload = Map.of(
            "modelCode",
            "dim_account",
            "password",
            "must-not-leak",
            "connection",
            Map.of("access_token", "must-not-leak-either")
        );

        service.record(event);

        Map<String, Object> body = capturedBody(outbox, objectMapper);
        Map<String, Object> payload = (Map<String, Object>) body.get("payload");
        assertThat(payload).containsEntry("modelCode", "dim_account").containsEntry("password", "[REDACTED]");
        assertThat((Map<String, Object>) payload.get("connection"))
            .containsEntry("access_token", "[REDACTED]");
        assertThat(capturedCommand(outbox).bodyJson()).doesNotContain("must-not-leak");
    }

    private AuditProperties enabledProperties() {
        AuditProperties properties = new AuditProperties();
        properties.setEnabled(true);
        return properties;
    }

    private Map<String, Object> capturedBody(PlatformAuditOutboxRepository outbox, ObjectMapper objectMapper) throws Exception {
        return objectMapper.readValue(capturedCommand(outbox).bodyJson(), MAP_TYPE);
    }

    private EnqueueCommand capturedCommand(PlatformAuditOutboxRepository outbox) {
        ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
        verify(outbox).enqueue(captor.capture());
        return captor.getValue();
    }
}
