package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.session.PkiSessionTicketService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.AdminTokens;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.keycloak.KeycloakAuthService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class KeycloakAuthResourceSessionContractTest {

    @Test
    void loginAuthenticatesKeycloakOnceAndLoadsAdminProfileWithoutPasswordReplay() {
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        AdminAuthGateway adminAuthGateway = mock(AdminAuthGateway.class);
        KeycloakAuthResource resource = new KeycloakAuthResource(
            registry,
            keycloakAuthService,
            adminAuthGateway,
            mock(PkiSessionTicketService.class),
            new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax", "test-secret"),
            mock(AuditService.class),
            mock(InceptorDataSourceRegistry.class),
            false,
            false
        );
        var kcTokens = new KeycloakAuthService.TokenResponse(
            "kc-access",
            "kc-refresh",
            300L,
            1800L,
            "Bearer",
            null,
            "kc-session",
            "openid profile"
        );
        when(keycloakAuthService.login("alice", "secret"))
            .thenReturn(new KeycloakAuthService.LoginResult(kcTokens, Map.of("username", "alice", "roles", List.of("ROLE_USER"))));
        when(adminAuthGateway.profile(eq("alice"), anyMap(), eq("kc-access")))
            .thenReturn(
                new AdminAuthGateway.ProfileResult(
                    Map.of("username", "alice", "fullName", "Alice", "roles", List.of("ROLE_DEPT_DATA_OWNER"))
                )
            );
        when(registry.hasActiveSession(eq("alice"), anyString())).thenReturn(false);
        when(
            registry.createSession(
                eq("alice"),
                anyList(),
                anyList(),
                isNull(),
                isNull(),
                eq("Alice"),
                anyString(),
                any(AdminTokens.class)
            )
        )
            .thenReturn(
                new PortalSession(
                    "session-1",
                    "alice",
                    "Alice",
                    List.of("ROLE_DEPT_DATA_OWNER"),
                    List.of("portal.view"),
                    null,
                    null,
                    "portal-access",
                    "portal-refresh",
                    Instant.parse("2026-04-29T12:00:00Z"),
                    "browser-1",
                    null
                )
            );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.login(
            new KeycloakAuthResource.LoginPayload("alice", "secret")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains("portal_session=portal-access; Path=/; HttpOnly; SameSite=Lax");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(cookie -> cookie.startsWith("browser_id=browser-1."));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("authenticated", true).doesNotContainKey("accessToken");
        verify(keycloakAuthService).login("alice", "secret");
        verify(adminAuthGateway).profile(eq("alice"), anyMap(), eq("kc-access"));
        verify(adminAuthGateway, never()).login(any(), any());
    }

    @Test
    void loginFallsBackToLegacyAdminLoginWhenProfileEndpointIsMissing() {
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        AdminAuthGateway adminAuthGateway = mock(AdminAuthGateway.class);
        KeycloakAuthResource resource = new KeycloakAuthResource(
            registry,
            keycloakAuthService,
            adminAuthGateway,
            mock(PkiSessionTicketService.class),
            new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax", "test-secret"),
            mock(AuditService.class),
            mock(InceptorDataSourceRegistry.class),
            false,
            false
        );
        var kcTokens = new KeycloakAuthService.TokenResponse(
            "kc-access",
            "kc-refresh",
            300L,
            1800L,
            "Bearer",
            null,
            "kc-session",
            "openid profile"
        );
        when(keycloakAuthService.login("alice", "secret"))
            .thenReturn(new KeycloakAuthService.LoginResult(kcTokens, Map.of("username", "alice", "roles", List.of("ROLE_USER"))));
        when(adminAuthGateway.profile(eq("alice"), anyMap(), eq("kc-access")))
            .thenThrow(new AdminAuthGateway.ProfileEndpointUnavailableException("error.http.404", new RuntimeException()));
        when(adminAuthGateway.login("alice", "secret"))
            .thenReturn(
                new AdminAuthGateway.LoginResult(
                    Map.of("username", "alice", "fullName", "Alice", "roles", List.of("ROLE_DEPT_DATA_OWNER")),
                    "legacy-access",
                    "legacy-refresh",
                    300L,
                    1800L
                )
            );
        when(registry.hasActiveSession(eq("alice"), anyString())).thenReturn(false);
        when(
            registry.createSession(
                eq("alice"),
                anyList(),
                anyList(),
                isNull(),
                isNull(),
                eq("Alice"),
                anyString(),
                any(AdminTokens.class)
            )
        )
            .thenReturn(
                new PortalSession(
                    "session-1",
                    "alice",
                    "Alice",
                    List.of("ROLE_DEPT_DATA_OWNER"),
                    List.of("portal.view"),
                    null,
                    null,
                    "portal-access",
                    "portal-refresh",
                    Instant.parse("2026-04-29T12:00:00Z"),
                    "browser-1",
                    null
                )
            );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.login(
            new KeycloakAuthResource.LoginPayload("alice", "secret")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains("portal_session=portal-access; Path=/; HttpOnly; SameSite=Lax");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("authenticated", true).doesNotContainKey("accessToken");
        verify(keycloakAuthService).login("alice", "secret");
        verify(adminAuthGateway).profile(eq("alice"), anyMap(), eq("kc-access"));
        verify(adminAuthGateway).login("alice", "secret");
    }
}
