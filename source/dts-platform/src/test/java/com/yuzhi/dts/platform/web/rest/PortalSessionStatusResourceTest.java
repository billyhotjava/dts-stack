package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        entity.setExpiresAt(Instant.parse("2026-04-09T10:30:45Z"));
        entity.setCreatedAt(Instant.parse("2026-04-09T09:00:00Z"));
        entity.setLastSeenAt(Instant.parse("2026-04-09T09:59:00Z"));
        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Portal-Access-Token", "token-1");

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("displayName", "Alice")
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

        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Portal-Access-Token", "token-1");

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

        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Portal-Access-Token", "token-1");

        ApiResponse<Map<String, Object>> response = resource.status(request);

        assertThat(response.getData()).containsEntry("authenticated", false).containsEntry("reason", "CONCURRENT");
        verify(sessionRepository).findByAccessToken("token-1");
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void statusShouldReportUnauthenticatedWhenHeaderIsMissing() {
        PortalSessionRepository sessionRepository = mock(PortalSessionRepository.class);
        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            sessionRepository,
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );

        ApiResponse<Map<String, Object>> response = resource.status(new MockHttpServletRequest());

        assertThat(response.getData()).containsEntry("authenticated", false).containsEntry("serverNow", "2026-04-09T10:00:00Z");
        verifyNoMoreInteractions(sessionRepository);
    }
}
