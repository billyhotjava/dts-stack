package com.yuzhi.dts.platform.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditTrailServiceTest {

    @Test
    void recordShouldSendThroughAdminAuditGateway() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.recordEvent(anyMap())).thenReturn(true);

        AuditTrailService service = new AuditTrailService(enabledProperties(), gateway);
        AuditTrailService.PendingAuditEvent event = new AuditTrailService.PendingAuditEvent();
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

        AuditTrailService service = new AuditTrailService(enabledProperties(), gateway);
        AuditTrailService.PendingAuditEvent event = new AuditTrailService.PendingAuditEvent();
        event.actor = "alice";
        event.action = "UPDATE";
        event.module = "FOUNDATION";

        service.record(event);
        assertThat(queue(service)).hasSize(1);

        service.retryFailedEvents();

        verify(gateway, times(2)).recordEvent(anyMap());
        assertThat(queue(service)).isEmpty();
    }

    private AuditProperties enabledProperties() {
        AuditProperties properties = new AuditProperties();
        properties.setEnabled(true);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private Queue<java.util.Map<String, Object>> queue(AuditTrailService service) {
        return (Queue<java.util.Map<String, Object>>) ReflectionTestUtils.getField(service, "failedEventQueue");
    }
}
