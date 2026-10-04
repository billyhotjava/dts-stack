package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.security.session.PkiSessionTicketService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.keycloak.KeycloakAuthService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class PkiLoginAuditEvidenceTest {

    @Test
    void pkiLoginShouldIssueTicketWithClientIpEvidence() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PkiSessionTicketService ticketService = mock(PkiSessionTicketService.class);
        when(gateway.pkiLogin(any())).thenReturn(Map.of("user", Map.of("username", "alice", "roles", List.of("ROLE_USER"))));
        when(ticketService.issue(eq("alice"), any(), any()))
            .thenReturn(ResponseCookie.from("pki_session_ticket", "ticket-1").path("/").httpOnly(true).build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.31.88");

        KeycloakAuthResource resource = newResource(gateway, mock(PortalSessionRegistry.class), mock(KeycloakAuthService.class), ticketService, mock(AuditService.class), false);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.pkiLogin(Map.of("challengeId", "c-1"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> userCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ticketService).issue(eq("alice"), userCaptor.capture(), eq(request));
        assertThat(userCaptor.getValue()).containsEntry("pkiLoginClientIp", "192.168.31.88");
    }

    @Test
    void pkiSessionAuditShouldCarryClientIpCapturedDuringPkiLogin() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        PortalSessionRegistry registry = mock(PortalSessionRegistry.class);
        KeycloakAuthService keycloakAuthService = mock(KeycloakAuthService.class);
        PkiSessionTicketService ticketService = mock(PkiSessionTicketService.class);
        AuditService audit = mock(AuditService.class);
        when(ticketService.resolve(any(), eq("alice")))
            .thenReturn(
                new PkiSessionTicketService.VerifiedPkiPrincipal(
                    "alice",
                    Map.of("username", "alice", "roles", List.of("ROLE_USER"), "pkiLoginClientIp", "192.168.31.88")
                )
            );
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

        KeycloakAuthResource resource = newResource(gateway, registry, keycloakAuthService, ticketService, audit, true);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.createPkiSession(
            new KeycloakAuthResource.PkiSessionPayload("alice", Map.of("username", "alice", "roles", List.of("ROLE_OP_ADMIN"))),
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(audit)
            .recordAs(
                eq("alice"),
                eq("AUTH LOGIN"),
                eq("platform"),
                eq("portal_user"),
                eq("alice"),
                eq("SUCCESS"),
                payloadCaptor.capture(),
                any()
        );
        assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> auditPayload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(auditPayload).containsEntry("clientIp", "192.168.31.88");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().getData().get("user");
        assertThat(user)
            .containsEntry("loginIp", "192.168.31.88")
            .containsEntry("clientIp", "192.168.31.88")
            .doesNotContainKey("pkiLoginClientIp");
    }

    @Test
    void compactPkiTicketShouldPreserveClientIpEvidence() {
        PkiSessionTicketService ticketService = new PkiSessionTicketService(
            new ObjectMapper(),
            120,
            "pki_session_ticket",
            "/",
            "false",
            "Lax",
            "test-secret"
        );
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "alice");
        user.put("roles", List.of("ROLE_USER"));
        user.put("pkiLoginClientIp", "192.168.31.88");
        user.put("largeProfile", "x".repeat(5000));

        ResponseCookie cookie = ticketService.issue("alice", user, new MockHttpServletRequest());
        MockHttpServletRequest resolveRequest = new MockHttpServletRequest();
        resolveRequest.setCookies(new Cookie("pki_session_ticket", cookie.getValue()));

        PkiSessionTicketService.VerifiedPkiPrincipal principal = ticketService.resolve(resolveRequest, "alice");

        assertThat(principal).isNotNull();
        assertThat(principal.user()).containsEntry("pkiLoginClientIp", "192.168.31.88");
    }

    private KeycloakAuthResource newResource(
        AdminAuthGateway gateway,
        PortalSessionRegistry registry,
        KeycloakAuthService keycloakAuthService,
        PkiSessionTicketService ticketService,
        AuditService audit,
        boolean portalAuditEnabled
    ) {
        return new KeycloakAuthResource(
            registry,
            keycloakAuthService,
            gateway,
            ticketService,
            new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax", "test-secret"),
            audit,
            mock(InceptorDataSourceRegistry.class),
            portalAuditEnabled,
            false
        );
    }
}
