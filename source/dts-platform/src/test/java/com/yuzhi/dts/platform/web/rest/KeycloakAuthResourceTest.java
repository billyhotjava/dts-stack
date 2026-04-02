package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class KeycloakAuthResourceTest {

    @Test
    void pkiChallengeShouldProxyAdminChallengeThroughPlatformApi() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        when(gateway.getPkiChallenge())
            .thenReturn(new AdminAuthGateway.PkiChallengeView("c-1", "nonce-1", "dts-admin", 123L, 456L));

        KeycloakAuthResource resource = newResource(gateway);

        ResponseEntity<ApiResponse<AdminAuthGateway.PkiChallengeView>> response = resource.pkiChallenge();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().challengeId()).isEqualTo("c-1");
    }

    @Test
    void pkiLoginShouldProxyPayloadThroughPlatformApi() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        when(gateway.pkiLogin(anyMap())).thenReturn(Map.of("user", Map.of("username", "alice")));

        KeycloakAuthResource resource = newResource(gateway);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.pkiLogin(Map.of("challengeId", "c-1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsKey("user");
    }

    @Test
    void loginShouldIssueBrowserIdCookieAndPassBrowserIdToSessionRegistry() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        when(gateway.login("alice", "secret"))
            .thenReturn(new AdminAuthGateway.LoginResult(Map.of("username", "alice", "roles", List.of("ROLE_USER")), "admin-access", "admin-refresh", 300L, 600L));
        when(cookieService.resolveBrowserId(any())).thenReturn("browser-1");
        when(registry.hasActiveSession("alice", "browser-1")).thenReturn(false);
        when(cookieService.buildBrowserIdCookie("browser-1"))
            .thenReturn(ResponseCookie.from("browser_id", "browser-1").path("/").build());
        when(cookieService.buildPortalSessionCookie("access-1"))
            .thenReturn(ResponseCookie.from("portal_session", "access-1").path("/").httpOnly(true).build());
        when(registry.createSession(eq("alice"), anyList(), anyList(), any(), any(), any(), eq("browser-1"), any()))
            .thenReturn(
                new PortalSession(
                    "session-1",
                    "alice",
                    "Alice",
                    List.of("ROLE_USER"),
                    List.of("portal.view"),
                    null,
                    null,
                    "browser-1",
                    "access-1",
                    "refresh-1",
                    Instant.parse("2026-04-02T00:30:00Z"),
                    null
                )
            );

        KeycloakAuthResource resource = newResource(gateway, registry, cookieService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.login(
            new KeycloakAuthResource.LoginPayload("alice", "secret"),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains(
            "browser_id=browser-1; Path=/",
            "portal_session=access-1; Path=/; HttpOnly"
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .doesNotContainKeys("accessToken", "refreshToken", "adminAccessToken", "adminRefreshToken");
        verify(registry).hasActiveSession("alice", "browser-1");
        verify(registry).createSession(eq("alice"), anyList(), anyList(), any(), any(), any(), eq("browser-1"), any());
        verify(cookieService).buildPortalSessionCookie("access-1");
    }

    @Test
    void refreshShouldRejectBrowserRefreshRequests() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);

        KeycloakAuthResource resource = newResource(gateway, registry, cookieService);

        ResponseEntity<ApiResponse<Map<String, String>>> response = resource.refresh(
            new KeycloakAuthResource.RefreshPayload("refresh-1", "alice")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(410);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("停用");
        verify(registry, never()).refreshSession(eq("refresh-1"), any());
    }

    private KeycloakAuthResource newResource(AdminAuthGateway gateway) {
        return newResource(gateway, mock(PortalSessionRegistry.class), mock(PortalSessionCookieService.class));
    }

    private KeycloakAuthResource newResource(
        AdminAuthGateway gateway,
        PortalSessionRegistry registry,
        PortalSessionCookieService cookieService
    ) {
        return new KeycloakAuthResource(
            registry,
            gateway,
            cookieService,
            mock(AuditService.class),
            mock(InceptorDataSourceRegistry.class),
            false,
            false
        );
    }
}
