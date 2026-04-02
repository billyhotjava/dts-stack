package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PortalSessionInactivityFilterTest {

    @Test
    void activeCookieSessionPassesThroughFilterChain() throws Exception {
        PortalSessionActivityService activityService = mock(PortalSessionActivityService.class);
        PortalSessionBearerTokenResolver tokenResolver = mock(PortalSessionBearerTokenResolver.class);
        PortalSessionInactivityFilter filter = new PortalSessionInactivityFilter(new ObjectMapper(), activityService, tokenResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screens/66");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(tokenResolver.resolve(request)).thenReturn("cookie-token");
        when(activityService.touch(org.mockito.Mockito.eq("cookie-token"), any())).thenReturn(ValidationResult.ACTIVE);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentCookieSessionReturnsConflictHeader() throws Exception {
        PortalSessionActivityService activityService = mock(PortalSessionActivityService.class);
        PortalSessionBearerTokenResolver tokenResolver = mock(PortalSessionBearerTokenResolver.class);
        PortalSessionInactivityFilter filter = new PortalSessionInactivityFilter(new ObjectMapper(), activityService, tokenResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screens/66");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(tokenResolver.resolve(request)).thenReturn("cookie-token");
        when(activityService.touch(org.mockito.Mockito.eq("cookie-token"), org.mockito.Mockito.any())).thenReturn(ValidationResult.CONCURRENT);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Session-Conflict")).isEqualTo("true");
        verifyNoInteractions(chain);
    }

    @Test
    void expiredCookieSessionReturnsExpiredHeader() throws Exception {
        PortalSessionActivityService activityService = mock(PortalSessionActivityService.class);
        PortalSessionBearerTokenResolver tokenResolver = mock(PortalSessionBearerTokenResolver.class);
        PortalSessionInactivityFilter filter = new PortalSessionInactivityFilter(new ObjectMapper(), activityService, tokenResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screens/66");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(tokenResolver.resolve(request)).thenReturn("cookie-token");
        when(activityService.touch(org.mockito.Mockito.eq("cookie-token"), org.mockito.Mockito.any())).thenReturn(ValidationResult.EXPIRED);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Session-Expired")).isEqualTo("true");
        verifyNoInteractions(chain);
    }
}
