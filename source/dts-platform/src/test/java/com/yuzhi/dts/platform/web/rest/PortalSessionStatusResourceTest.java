package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PortalSessionStatusResourceTest {

    @Test
    void statusShouldReportAuthenticatedSessionWithoutTouchingLifecycle() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setDisplayName("Alice");
        entity.setAccessToken("token-1");
        entity.setRefreshToken("refresh-1");
        entity.setRoles(List.of("ROLE_INST_DATA_OWNER"));
        entity.setPermissions(List.of("portal.view"));
        entity.setDeptCode("dept-a");
        entity.setPersonnelLevel("L2");
        entity.setExpiresAt(Instant.parse("2026-04-09T10:30:45Z"));
        entity.setCreatedAt(Instant.parse("2026-04-09T09:00:00Z"));
        entity.setLastSeenAt(Instant.parse("2026-04-09T09:59:00Z"));
        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        PortalSessionStatusResource resource = resourceWithCookie(sessionRepository, "token-1");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("displayName", "Alice")
            .containsEntry("roles", List.of("ROLE_INST_DATA_OWNER"))
            .containsEntry("permissions", List.of("portal.view"))
            .containsEntry("deptCode", "dept-a")
            .containsEntry("personnelLevel", "L2")
            .containsEntry("expiresAt", "2026-04-09T10:30:45Z")
            .containsEntry("serverNow", "2026-04-09T10:00:00Z")
            .containsEntry("remainingSeconds", 1845L);
        verify(sessionRepository).findByAccessToken("token-1");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldReportExpiredSessionWithoutMutatingIt() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setAccessToken("token-1");
        entity.setRefreshToken("refresh-1");
        entity.setExpiresAt(Instant.parse("2026-04-09T09:59:30Z"));
        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        PortalSessionStatusResource resource = resourceWithCookie(sessionRepository, "token-1");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData())
            .containsEntry("authenticated", false)
            .containsEntry("reason", "EXPIRED")
            .containsEntry("expiresAt", "2026-04-09T09:59:30Z")
            .containsEntry("remainingSeconds", 0L);
        verify(sessionRepository).findByAccessToken("token-1");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldReportConcurrentReasonForRevokedSession() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setAccessToken("token-1");
        entity.setRefreshToken("refresh-1");
        entity.setRevokedAt(Instant.parse("2026-04-09T09:58:00Z"));
        entity.setRevokedReason(PortalSessionCloseReason.CONCURRENT);
        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        PortalSessionStatusResource resource = resourceWithCookie(sessionRepository, "token-1");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData()).containsEntry("authenticated", false).containsEntry("reason", "CONCURRENT");
        verify(sessionRepository).findByAccessToken("token-1");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldReportUnauthenticatedWhenCookieIsMissing() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );

        ApiResponse<Map<String, Object>> response = resource.status(new MockHttpServletRequest());

        assertThat(response.getData()).containsEntry("authenticated", false).containsEntry("serverNow", "2026-04-09T10:00:00Z");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldReportExpiredWhenTokenDoesNotResolveToSession() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        when(sessionRepository.findByAccessToken("missing-token")).thenReturn(Optional.empty());
        PortalSessionStatusResource resource = resourceWithCookie(sessionRepository, "missing-token");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData())
            .containsEntry("authenticated", false)
            .containsEntry("reason", "EXPIRED")
            .containsEntry("remainingSeconds", 0L);
        verify(sessionRepository).findByAccessToken("missing-token");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldUseCookieTokenAndIgnoreStaleHeaders() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setAccessToken("cookie-token");
        entity.setRefreshToken("refresh-1");
        entity.setExpiresAt(Instant.parse("2026-04-09T10:30:45Z"));
        when(cookieService.resolvePortalSessionToken(any())).thenReturn("cookie-token");
        when(sessionRepository.findByAccessToken("cookie-token")).thenReturn(Optional.of(entity));
        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC),
            cookieService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer stale-header-token");
        request.addHeader("X-Portal-Access-Token", "stale-direct-token");

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData()).containsEntry("authenticated", true).containsEntry("username", "alice");
        verify(sessionRepository).findByAccessToken("cookie-token");
    }

    @Test
    void statusShouldExposeResolvedLoginIpForRecoveredPortalSession() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setAccessToken("token-1");
        entity.setRefreshToken("refresh-1");
        entity.setExpiresAt(Instant.parse("2026-04-09T10:30:45Z"));
        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        PortalSessionStatusResource resource = resourceWithCookie(sessionRepository, "token-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Forwarded", "for=192.168.31.88;proto=https");
        request.setRemoteAddr("172.18.0.1");

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("loginIp", "192.168.31.88")
            .containsEntry("clientIp", "192.168.31.88");
        verify(sessionRepository).findByAccessToken("token-1");
    }

    @Test
    void statusShouldIgnoreLegacyAccessTokenHeaderWhenCookieIsMissing() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Portal-Access-Token", "legacy-token");

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData()).containsEntry("authenticated", false);
        verifyNoMoreInteractions(sessionRepository);
    }

    private PortalSessionStatusResource resourceWithCookie(PortalSessionRepository sessionRepository, String token) {
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        when(cookieService.resolvePortalSessionToken(any())).thenReturn(token);
        return new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC),
            cookieService
        );
    }
}
