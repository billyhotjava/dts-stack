package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.audit.AuditForwarderService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLoggingFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordAuthenticatedReadFallbackForPageListOperations() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/governance/issues");
        request.setQueryString("page=0&size=20");
        request.addParameter("page", "0");
        request.addParameter("size", "20");
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.11");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        AuditForwarderService.PendingAuditEvent event = captor.getValue();
        assertThat(event.actor).isEqualTo("opadmin");
        assertThat(event.httpMethod).isEqualTo("GET");
        assertThat(event.action).contains("查看");
        assertThat(event.clientIp).isEqualTo("223.86.189.127");
    }

    @Test
    void shouldKeepSupplementaryReadsOutOfFallbackAudit() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/directory/users");
        request.setQueryString("keyword=test1");
        request.addParameter("keyword", "test1");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AuditForwarderService.PendingAuditEvent.class));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AuditForwarderService> mockProvider(AuditForwarderService forwarder) {
        ObjectProvider<AuditForwarderService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(forwarder);
        return provider;
    }

    private void authenticate(String username) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    username,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
            );
    }
}
