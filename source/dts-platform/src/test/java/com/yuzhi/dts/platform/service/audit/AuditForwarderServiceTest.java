package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AuditForwarderServiceTest {

    @Test
    void recordShouldSendThroughAdminAuditGateway() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.recordEvent(anyMap())).thenReturn(true);

        AuditForwarderService service = new AuditForwarderService(enabledProperties(), gateway);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "CREATE";
        event.module = "FOUNDATION";

        service.record(event);

        verify(gateway).recordEvent(anyMap());
        assertThat(queue(service)).isEmpty();
    }

    @Test
    void retryFailedEventsShouldReplayQueuedPayloadsThroughGateway() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.recordEvent(anyMap())).thenReturn(false).thenReturn(true);

        AuditForwarderService service = new AuditForwarderService(enabledProperties(), gateway);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "UPDATE";
        event.module = "FOUNDATION";

        service.record(event);
        assertThat(queue(service)).hasSize(1);

        service.retryFailedEvents();

        verify(gateway, times(2)).recordEvent(anyMap());
        assertThat(queue(service)).isEmpty();
    }

    @Test
    void recordShouldSkipMachineActors() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);

        AuditForwarderService service = new AuditForwarderService(enabledProperties(), gateway);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "service:dts-analytics";
        event.action = "查看数据源列表";
        event.module = "platform.infra";

        service.record(event);

        verify(gateway, never()).recordEvent(anyMap());
        assertThat(queue(service)).isEmpty();
    }

    @Test
    void recordShouldSendStableButtonCodeForPlatformLoginEvents() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.recordEvent(anyMap())).thenReturn(true);

        AuditForwarderService service = new AuditForwarderService(enabledProperties(), gateway);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGIN";
        event.module = "platform";
        event.operationType = "LOGIN";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(gateway);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGIN");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGIN");
    }

    @Test
    void recordShouldSendStableButtonCodeForPlatformLogoutEvents() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.recordEvent(anyMap())).thenReturn(true);

        AuditForwarderService service = new AuditForwarderService(enabledProperties(), gateway);
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.actor = "xiezm";
        event.action = "AUTH LOGOUT";
        event.module = "platform";
        event.operationType = "LOGOUT";
        event.resourceType = "portal_user";
        event.resourceId = "xiezm";
        event.result = "SUCCESS";

        service.record(event);

        Map<String, Object> body = capturedBody(gateway);
        assertThat(body).containsEntry("buttonCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
        assertThat(body).containsEntry("operationCode", "ADMIN_AUTH_PLATFORM_LOGOUT");
    }

    private AuditProperties enabledProperties() {
        AuditProperties properties = new AuditProperties();
        properties.setEnabled(true);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody(AdminAuditGateway gateway) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(gateway).recordEvent(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Queue<java.util.Map<String, Object>> queue(AuditForwarderService service) {
        return (Queue<java.util.Map<String, Object>>) ReflectionTestUtils.getField(service, "failedEventQueue");
    }
}
