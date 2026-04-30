package com.yuzhi.dts.admin.web.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.AuthEndpointPaths;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry.ValidationResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionInactivityFilterTest {

    @Test
    void platformProfileSkipsAdminSessionValidationEvenWithBearerHeader() throws Exception {
        AdminSessionRegistry sessionRegistry = mock(AdminSessionRegistry.class);
        SessionInactivityFilter filter = new SessionInactivityFilter(new ObjectMapper(), sessionRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", AuthEndpointPaths.KEYCLOAK_AUTH_API_PREFIX + "platform/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer keycloak-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void loginEndpointSkipsAdminSessionValidationEvenWithStaleBearerHeader() throws Exception {
        AdminSessionRegistry sessionRegistry = mock(AdminSessionRegistry.class);
        SessionInactivityFilter filter = new SessionInactivityFilter(new ObjectMapper(), sessionRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", AuthEndpointPaths.KEYCLOAK_AUTH_API_PREFIX + "login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer stale-admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void normalApiBearerStillRequiresAdminSessionValidation() throws Exception {
        AdminSessionRegistry sessionRegistry = mock(AdminSessionRegistry.class);
        SessionInactivityFilter filter = new SessionInactivityFilter(new ObjectMapper(), sessionRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/keycloak/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer stale-admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("sysadmin", "n/a");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(sessionRegistry.validate("stale-admin-token", null, "sysadmin")).thenReturn(ValidationResult.EXPIRED);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(sessionRegistry).validate("stale-admin-token", null, "sysadmin");
        verifyNoInteractions(chain);
    }
}
