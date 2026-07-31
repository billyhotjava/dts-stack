package com.yuzhi.dts.admin.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator.Decision;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuditIngestPreAuthenticationFilterTest {

    private AdminInboundServiceAuthenticator authenticator;
    private AuditIngestProperties properties;

    @BeforeEach
    void setUp() {
        authenticator = mock(AdminInboundServiceAuthenticator.class);
        properties = new AuditIngestProperties();
        properties.setMaxBodyBytes(64);
        properties.setRateLimitPerSecond(10);
        properties.setRateLimitBurst(10);
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeReadingOrDispatchingBody() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(false, null, "token_mismatch"));
        MockHttpServletRequest request = auditRequest("x".repeat(1_024));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean dispatched = new AtomicBoolean();

        filter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> dispatched.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(dispatched).isFalse();
    }

    @Test
    void rejectsAuthenticatedBodyThatExceedsConfiguredByteLimit() throws Exception {
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "accepted"));
        MockHttpServletRequest request = auditRequest("x".repeat(65));
        request.removeHeader("Content-Length");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean dispatched = new AtomicBoolean();

        filter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> dispatched.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(dispatched).isFalse();
    }

    @Test
    void authenticatedBoundedBodyIsReplayedToMvcWithDecisionAttribute() throws Exception {
        Decision decision = new Decision(true, "dts-platform", "accepted");
        when(authenticator.authenticate(any())).thenReturn(decision);
        MockHttpServletRequest request = auditRequest("{\"eventId\":\"event-1\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean dispatched = new AtomicBoolean();

        filter().doFilter(request, response, (wrapped, ignoredResponse) -> {
            dispatched.set(true);
            assertThat(wrapped.getInputStream().readAllBytes())
                .isEqualTo("{\"eventId\":\"event-1\"}".getBytes(StandardCharsets.UTF_8));
            assertThat(wrapped.getAttribute(AuditIngestPreAuthenticationFilter.AUTHENTICATION_ATTRIBUTE))
                .isSameAs(decision);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(dispatched).isTrue();
    }

    @Test
    void rateLimitIsAppliedPerAuthenticatedProducer() throws Exception {
        properties.setRateLimitPerSecond(1);
        properties.setRateLimitBurst(1);
        when(authenticator.authenticate(any())).thenReturn(new Decision(true, "dts-platform", "accepted"));
        AuditIngestPreAuthenticationFilter filter = filter();
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(auditRequest("{}"), first, (request, response) -> {});
        filter.doFilter(auditRequest("{}"), second, (request, response) -> {});

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("1");
    }

    private AuditIngestPreAuthenticationFilter filter() {
        return new AuditIngestPreAuthenticationFilter(authenticator, properties);
    }

    private MockHttpServletRequest auditRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/audit-events");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
