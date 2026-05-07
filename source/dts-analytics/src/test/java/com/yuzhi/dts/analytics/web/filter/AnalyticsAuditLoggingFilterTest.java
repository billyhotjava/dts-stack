package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalyticsAuditLoggingFilterTest {

    @Test
    void shouldRecordAuthenticatedAnalyticsPageReadWithHumanActorAndRealIp() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screen");
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.15");
        request.addHeader("User-Agent", "JUnit");
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(user()));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        ArgumentCaptor<AnalyticsAuditEvent> captor = ArgumentCaptor.forClass(AnalyticsAuditEvent.class);
        verify(forwarder).record(captor.capture());
        AnalyticsAuditEvent event = captor.getValue();
        assertThat(event.actor()).isEqualTo("opadmin@platform.local");
        assertThat(event.actorName()).isEqualTo("Ops Admin");
        assertThat(event.action()).isEqualTo("查看大屏");
        assertThat(event.operationType()).isEqualTo("READ");
        assertThat(event.clientIp()).isEqualTo("223.86.189.127");
    }

    @Test
    void shouldSkipAnalyticsAuditWhenNoHumanUserIsResolved() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dataset");
        when(sessionService.resolveUser(request)).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AnalyticsAuditEvent.class));
    }

    private AnalyticsUser user() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        user.setEmail("opadmin@platform.local");
        user.setPlatformUsername("opadmin");
        user.setFirstName("Ops");
        user.setLastName("Admin");
        return user;
    }
}
