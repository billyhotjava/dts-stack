package com.yuzhi.dts.platform.security.session;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Transactional
public class PortalSessionActivityService {

    public enum ValidationResult {
        ACTIVE,
        EXPIRED,
        CONCURRENT
    }

    private final PortalSessionRepository sessionRepository;

    public PortalSessionActivityService(PortalSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public ValidationResult touch(String tokenKey, Instant now) {
        if (!StringUtils.hasText(tokenKey)) {
            return ValidationResult.ACTIVE;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        PortalSessionEntity entity = sessionRepository.findByAccessToken(tokenKey).orElse(null);
        if (entity == null) {
            return ValidationResult.EXPIRED;
        }
        if (entity.getRevokedAt() != null) {
            return toValidationResult(entity.getRevokedReason());
        }
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(effectiveNow)) {
            entity.setRevokedAt(effectiveNow);
            entity.setRevokedReason(PortalSessionCloseReason.EXPIRED);
            sessionRepository.save(entity);
            return ValidationResult.EXPIRED;
        }
        entity.setLastSeenAt(effectiveNow);
        sessionRepository.save(entity);
        return ValidationResult.ACTIVE;
    }

    public void invalidate(String tokenKey) {
        invalidate(tokenKey, ValidationResult.EXPIRED, Instant.now());
    }

    public void invalidate(String tokenKey, ValidationResult reason) {
        invalidate(tokenKey, reason, Instant.now());
    }

    private void invalidate(String tokenKey, ValidationResult reason, Instant when) {
        if (!StringUtils.hasText(tokenKey)) {
            return;
        }
        sessionRepository
            .findByAccessToken(tokenKey)
            .ifPresent(entity -> {
                Instant effectiveWhen = when == null ? Instant.now() : when;
                if (entity.getRevokedAt() == null) {
                    entity.setRevokedAt(effectiveWhen);
                }
                if (entity.getRevokedReason() == null) {
                    entity.setRevokedReason(toCloseReason(reason));
                }
                sessionRepository.save(entity);
            });
    }

    private ValidationResult toValidationResult(PortalSessionCloseReason reason) {
        if (reason == null) {
            return ValidationResult.EXPIRED;
        }
        return switch (reason) {
            case CONCURRENT -> ValidationResult.CONCURRENT;
            default -> ValidationResult.EXPIRED;
        };
    }

    private PortalSessionCloseReason toCloseReason(ValidationResult reason) {
        if (reason == ValidationResult.CONCURRENT) {
            return PortalSessionCloseReason.CONCURRENT;
        }
        return PortalSessionCloseReason.EXPIRED;
    }
}
