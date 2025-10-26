package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalSessionActivityServiceTest {

    @Mock
    private PortalSessionRepository sessionRepository;

    @InjectMocks
    private PortalSessionActivityService service;

    @Test
    void touchUpdatesLastSeenForActiveToken() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setAccessToken("token-1");
        entity.setExpiresAt(now.plusSeconds(120));
        entity.setLastSeenAt(now.minusSeconds(30));

        when(sessionRepository.findByAccessToken("token-1")).thenReturn(Optional.of(entity));

        ValidationResult result = service.touch("token-1", now);

        assertThat(result).isEqualTo(ValidationResult.ACTIVE);
        ArgumentCaptor<PortalSessionEntity> captor = ArgumentCaptor.forClass(PortalSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getLastSeenAt()).isEqualTo(now);
    }

    @Test
    void touchReturnsExpiredWhenTokenMissing() {
        when(sessionRepository.findByAccessToken("missing")).thenReturn(Optional.empty());

        ValidationResult result = service.touch("missing", Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(result).isEqualTo(ValidationResult.EXPIRED);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void touchReturnsRevocationReasonWhenAlreadyRevoked() {
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setAccessToken("token-2");
        entity.setRevokedAt(Instant.parse("2025-01-01T00:00:00Z"));
        entity.setRevokedReason(PortalSessionCloseReason.CONCURRENT);

        when(sessionRepository.findByAccessToken("token-2")).thenReturn(Optional.of(entity));

        ValidationResult result = service.touch("token-2", Instant.parse("2025-01-01T00:10:00Z"));

        assertThat(result).isEqualTo(ValidationResult.CONCURRENT);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void touchMarksExpiredTokenAsRevoked() {
        Instant now = Instant.parse("2025-01-01T00:05:00Z");
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setAccessToken("token-3");
        entity.setExpiresAt(now.minusSeconds(10));

        when(sessionRepository.findByAccessToken("token-3")).thenReturn(Optional.of(entity));

        ValidationResult result = service.touch("token-3", now);

        assertThat(result).isEqualTo(ValidationResult.EXPIRED);
        ArgumentCaptor<PortalSessionEntity> captor = ArgumentCaptor.forClass(PortalSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        PortalSessionEntity saved = captor.getValue();
        assertThat(saved.getRevokedAt()).isEqualTo(now);
        assertThat(saved.getRevokedReason()).isEqualTo(PortalSessionCloseReason.EXPIRED);
    }

    @Test
    void invalidateSetsRevokedReason() {
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setAccessToken("token-4");

        when(sessionRepository.findByAccessToken("token-4")).thenReturn(Optional.of(entity));

        service.invalidate("token-4", ValidationResult.CONCURRENT);

        ArgumentCaptor<PortalSessionEntity> captor = ArgumentCaptor.forClass(PortalSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        PortalSessionEntity saved = captor.getValue();
        assertThat(saved.getRevokedReason()).isEqualTo(PortalSessionCloseReason.CONCURRENT);
        assertThat(saved.getRevokedAt()).isNotNull();
    }
}
