package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.session.PkiSessionTicketService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
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
    void pkiLoginShouldIssueTicketCookieAfterVerifiedLogin() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PkiSessionTicketService ticketService = mock(PkiSessionTicketService.class);
        when(gateway.pkiLogin(any())).thenReturn(Map.of("user", Map.of("username", "alice", "roles", List.of("ROLE_USER"))));
        when(ticketService.issue(eq("alice"), any(), any()))
            .thenReturn(ResponseCookie.from("pki_session_ticket", "ticket-1").path("/").httpOnly(true).build());

        KeycloakAuthResource resource = newResource(gateway, mock(PortalSessionRegistry.class), mock(KeycloakAuthService.class), ticketService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.pkiLogin(Map.of("challengeId", "c-1"), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains("pki_session_ticket=ticket-1; Path=/; HttpOnly");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsKey("user");
    }

    @Test
    void pkiSessionShouldRejectMissingOrExpiredTicket() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PkiSessionTicketService ticketService = mock(PkiSessionTicketService.class);
        when(ticketService.resolve(any(), anyString())).thenReturn(null);
        when(ticketService.clearTicketCookie(any())).thenReturn(ResponseCookie.from("pki_session_ticket", "").path("/").maxAge(0).build());

        KeycloakAuthResource resource = newResource(gateway, mock(PortalSessionRegistry.class), mock(KeycloakAuthService.class), ticketService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.createPkiSession(
            new KeycloakAuthResource.PkiSessionPayload("alice", Map.of("roles", List.of("ROLE_OP_ADMIN"))),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains("pki_session_ticket=; Path=/; Max-Age=0");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("USB-Key");
    }

    @Test
    void pkiSessionShouldOnlyTrustVerifiedUserFromTicket() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        PkiSessionTicketService ticketService = mock(PkiSessionTicketService.class);
        when(ticketService.resolve(any(), eq("alice")))
            .thenReturn(new PkiSessionTicketService.VerifiedPkiPrincipal("alice", Map.of("username", "alice", "roles", List.of("ROLE_USER"))));
        when(ticketService.clearTicketCookie(any())).thenReturn(ResponseCookie.from("pki_session_ticket", "").path("/").maxAge(0).build());
        when(keycloakAuthService.loginByTokenExchange("alice")).thenThrow(new IllegalStateException("kc unavailable"));
        when(registry.hasActiveSession(eq("alice"), anyString())).thenReturn(false);
        when(registry.createSession(eq("alice"), anyList(), anyList(), eq(null), eq(null), eq("alice"), anyString(), eq(null)))
            .thenReturn(
                new PortalSession(
                    "session-1",
                    "alice",
                    "alice",
                    List.of("ROLE_USER"),
                    List.of("portal.view"),
                    null,
                    null,
                    "access-1",
                    "refresh-1",
                    Instant.parse("2026-04-22T12:00:00Z"),
                    "browser-1",
                    null
                )
            );

        KeycloakAuthResource resource = newResource(gateway, registry, keycloakAuthService, ticketService);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.createPkiSession(
            new KeycloakAuthResource.PkiSessionPayload("alice", Map.of("username", "alice", "roles", List.of("ROLE_OP_ADMIN"))),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).contains("pki_session_ticket=; Path=/; Max-Age=0");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
            .anyMatch(cookie -> cookie.startsWith("portal_session=access-1;"));
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(cookie -> cookie.startsWith("browser_id=browser-1."));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsEntry("authenticated", true).doesNotContainKey("accessToken");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().getData().get("user");
        assertThat(user.get("roles")).isEqualTo(List.of("ROLE_USER"));
        verify(registry)
            .createSession(eq("alice"), eq(List.of("ROLE_USER")), eq(List.of("portal.view")), eq(null), eq(null), eq("alice"), anyString(), eq(null));
        verifyNoInteractions(gateway);
    }

    private KeycloakAuthResource newResource(AdminAuthGateway gateway) {
        return newResource(gateway, mock(PortalSessionRegistry.class), mock(KeycloakAuthService.class), mock(PkiSessionTicketService.class));
    }

    private KeycloakAuthResource newResource(
        AdminAuthGateway gateway,
        PortalSessionRegistry registry,
        KeycloakAuthService keycloakAuthService,
        PkiSessionTicketService ticketService
    ) {
        return new KeycloakAuthResource(
            registry,
            keycloakAuthService,
            gateway,
            ticketService,
            new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax", "test-secret"),
            mock(AuditService.class),
            mock(InceptorDataSourceRegistry.class),
            false,
            false
        );
    }
}
