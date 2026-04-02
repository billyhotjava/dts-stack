package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

class PortalSessionResourceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentShouldReturnAuthenticatedSessionShape() {
        DefaultOAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
            Map.of(
                OAuth2TokenIntrospectionClaimNames.USERNAME,
                "alice",
                "preferred_username",
                "alice",
                "displayName",
                "Alice",
                "browser_id",
                "browser-1",
                OAuth2TokenIntrospectionClaimNames.EXP,
                Instant.parse("2026-04-02T00:30:00Z")
            ),
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );

        PortalSessionResource resource = new PortalSessionResource(mock(PortalSessionRegistry.class), mock(PortalSessionCookieService.class));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.current(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("displayName", "Alice")
            .containsEntry("browserId", "browser-1")
            .containsEntry("expiresAt", "2026-04-02T00:30:00Z");
    }

    @Test
    void currentShouldResolveCookieBackedSessionWithoutSecurityContext() {
        PortalSessionRegistry sessionRegistry = mock(PortalSessionRegistry.class);
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        when(cookieService.resolvePortalSessionToken(org.mockito.Mockito.any(HttpServletRequest.class))).thenReturn("cookie-token");
        when(sessionRegistry.findByAccessToken("cookie-token"))
            .thenReturn(
                java.util.Optional.of(
                    new PortalSessionRegistry.PortalSession(
                        "session-1",
                        "alice",
                        "Alice",
                        List.of("ROLE_USER"),
                        List.of("portal.view"),
                        null,
                        null,
                        "browser-1",
                        "cookie-token",
                        "refresh-token",
                        Instant.parse("2026-04-02T00:30:00Z"),
                        null
                    )
                )
            );

        PortalSessionResource resource = new PortalSessionResource(sessionRegistry, cookieService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.current(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("displayName", "Alice")
            .containsEntry("browserId", "browser-1")
            .containsEntry("expiresAt", "2026-04-02T00:30:00Z");
    }

    @Test
    void currentShouldReportUnauthenticatedWhenContextIsEmpty() {
        PortalSessionRegistry sessionRegistry = mock(PortalSessionRegistry.class);
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        when(cookieService.resolvePortalSessionToken(org.mockito.Mockito.any(HttpServletRequest.class))).thenReturn(null);
        PortalSessionResource resource = new PortalSessionResource(sessionRegistry, cookieService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.current(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("authenticated", false);
    }
}
