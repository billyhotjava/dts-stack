package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class AnalyticsAuthenticationFilterTest {

    private static final String PLATFORM_TOKEN = "platform-analytics-pair-token-20260819";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesAuthenticationFromVerifiedAnalyticsSession() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsUser actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setEmail("analyst@example.test");
        actor.setActive(true);
        when(sessionService.resolveUser(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(actor));
        AnalyticsAuthenticationFilter filter = new AnalyticsAuthenticationFilter(sessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analysis");
        request.addHeader("X-DTS-Roles", "ROLE_ANALYST,ROLE_DEPT_READER");
        AtomicReference<Authentication> observed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().isAuthenticated()).isTrue();
        assertThat(observed.get().getPrincipal()).isSameAs(actor);
        assertThat(observed.get().getAuthorities()).extracting("authority").containsExactly("ROLE_ANALYST", "ROLE_DEPT_READER");
    }

    @Test
    void authenticatesOnlyTheNarrowSemanticPublishRouteWithTheConfiguredPlatformToken() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuthenticationFilter filter = new AnalyticsAuthenticationFilter(sessionService, PLATFORM_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/semantic/publish");
        request.addHeader("X-DTS-Service", "dts-platform");
        request.addHeader("X-DTS-Service-Token", PLATFORM_TOKEN);
        AtomicReference<Authentication> observed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getName()).isEqualTo("dts-platform");
        assertThat(observed.get().getAuthorities()).extracting("authority").containsExactly("ROLE_ANALYTICS_SERVICE");
        verifyNoInteractions(sessionService);
    }

    @Test
    void authenticatesTheInternalScreenReadWithTheConfiguredPlatformToken() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuthenticationFilter filter = new AnalyticsAuthenticationFilter(sessionService, PLATFORM_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/screens");
        request.addHeader("X-DTS-Service", "dts-platform");
        request.addHeader("X-DTS-Service-Token", PLATFORM_TOKEN);
        AtomicReference<Authentication> observed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getName()).isEqualTo("dts-platform");
        assertThat(observed.get().getAuthorities()).extracting("authority").containsExactly("ROLE_ANALYTICS_SERVICE");
        verifyNoInteractions(sessionService);
    }

    @Test
    void rejectsServiceHeadersWithAWrongTokenAndDoesNotTrustThemOnOtherRoutes() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuthenticationFilter filter = new AnalyticsAuthenticationFilter(sessionService, PLATFORM_TOKEN);
        MockHttpServletRequest wrongToken = new MockHttpServletRequest("POST", "/api/semantic/publish");
        wrongToken.addHeader("X-DTS-Service", "dts-platform");
        wrongToken.addHeader("X-DTS-Service-Token", "wrong-token");
        AtomicReference<Authentication> wrongTokenAuth = new AtomicReference<>();

        filter.doFilter(wrongToken, new MockHttpServletResponse(), (req, res) -> wrongTokenAuth.set(SecurityContextHolder.getContext().getAuthentication()));
        SecurityContextHolder.clearContext();

        MockHttpServletRequest wrongRoute = new MockHttpServletRequest("GET", "/api/analysis");
        wrongRoute.addHeader("X-DTS-Service", "dts-platform");
        wrongRoute.addHeader("X-DTS-Service-Token", PLATFORM_TOKEN);
        AtomicReference<Authentication> wrongRouteAuth = new AtomicReference<>();
        filter.doFilter(wrongRoute, new MockHttpServletResponse(), (req, res) -> wrongRouteAuth.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(wrongTokenAuth.get()).isNull();
        assertThat(wrongRouteAuth.get()).isNull();
    }
}
