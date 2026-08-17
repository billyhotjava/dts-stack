package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
