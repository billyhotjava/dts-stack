package com.yuzhi.dts.admin.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Transactional
public class AdminSessionRegistry {

    public enum ValidationResult {
        ACTIVE,
        CONCURRENT,
        EXPIRED,
        LOGOUT,
        UNKNOWN
    }

    private static final Logger log = LoggerFactory.getLogger(AdminSessionRegistry.class);

    private final AdminSessionRepository repository;
    private final Duration sessionTtl;

    public AdminSessionRegistry(
        @Value("${dts.admin.session.timeout-minutes:10}") long timeoutMinutes,
        AdminSessionRepository repository
    ) {
        long minutes = timeoutMinutes <= 0 ? 10 : timeoutMinutes;
        this.sessionTtl = Duration.ofMinutes(minutes);
        this.repository = repository;
    }

    public record SessionRegistration(AdminSessionEntity session, boolean takeover, int terminatedSessions) {}

    public SessionRegistration registerLogin(
        String username,
        String sessionState,
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
    ) {
        String normalized = normalize(username);
        if (normalized == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String accessHash = hash(accessToken);
        if (!StringUtils.hasText(accessHash)) {
            throw new IllegalArgumentException("access token 缺失，无法建立会话");
        }
        String refreshHash = hash(refreshToken);

        Instant now = Instant.now();
        UUID newSessionId = UUID.randomUUID();
        boolean takeover = false;
        int terminated = 0;
        String trimmedState = clean(sessionState);
        List<AdminSessionEntity> activeSessions = repository.findActiveSessionsForUpdate(normalized);
        for (AdminSessionEntity existing : activeSessions) {
            if (existing.getRevokedAt() != null) {
                continue;
            }
            if (isExpired(existing, now)) {
                revoke(existing, AdminSessionCloseReason.EXPIRED, null, now);
                terminated++;
                continue;
            }
            takeover = true;
            terminated++;
            revoke(existing, AdminSessionCloseReason.CONCURRENT, newSessionId, now);
        }

        AdminSessionEntity entity = new AdminSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(username == null ? normalized : username);
        entity.setNormalizedUsername(normalized);
        entity.setSessionId(newSessionId);
        entity.setSessionState(trimmedState);
        entity.setAccessTokenHash(accessHash);
        entity.setRefreshTokenHash(refreshHash);
        entity.setCreatedAt(now);
        entity.setLastSeenAt(now);
        entity.setExpiresAt(resolveExpiry(now, accessExpiresAt));
        repository.save(entity);
        return new SessionRegistration(entity, takeover, terminated);
    }

    public AdminSessionEntity refreshSession(
        String refreshToken,
        String sessionState,
        String nextAccessToken,
        String nextRefreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
    ) {
        String refreshHash = hash(refreshToken);
        if (!StringUtils.hasText(refreshHash)) {
            throw new IllegalArgumentException("refresh token 缺失");
        }
        AdminSessionEntity entity = repository
            .findByRefreshTokenHash(refreshHash)
            .orElseThrow(() -> new IllegalArgumentException("unknown_refresh_token"));

        Instant now = Instant.now();
        if (entity.getRevokedAt() != null) {
            throw new IllegalStateException("session_revoked");
        }
        if (isExpired(entity, now)) {
            revoke(entity, AdminSessionCloseReason.EXPIRED, null, now);
            throw new IllegalStateException("session_expired");
        }

        String nextAccessHash = hash(nextAccessToken);
        if (!StringUtils.hasText(nextAccessHash)) {
            throw new IllegalArgumentException("access token 缺失");
        }
        entity.setAccessTokenHash(nextAccessHash);
        if (StringUtils.hasText(nextRefreshToken)) {
            entity.setRefreshTokenHash(hash(nextRefreshToken));
        }
        entity.setSessionState(clean(sessionState));
        entity.setLastSeenAt(now);
        entity.setExpiresAt(resolveExpiry(now, accessExpiresAt));
        repository.save(entity);
        return entity;
    }

    public ValidationResult validate(String accessToken, String sessionState, String username) {
        String accessHash = hash(accessToken);
        if (!StringUtils.hasText(accessHash)) {
            return ValidationResult.EXPIRED;
        }
        Instant now = Instant.now();
        Optional<AdminSessionEntity> optional = repository.findByAccessTokenHash(accessHash);
        AdminSessionEntity entity = optional.orElse(null);
        if (entity == null) {
            return ValidationResult.EXPIRED;
        }
        if (entity.getRevokedAt() != null) {
            return mapReason(entity.getRevokedReason());
        }
        if (isExpired(entity, now)) {
            revoke(entity, AdminSessionCloseReason.EXPIRED, null, now);
            return ValidationResult.EXPIRED;
        }
        String storedState = entity.getSessionState();
        String candidateState = clean(sessionState);
        if (storedState != null && candidateState != null && !storedState.equals(candidateState)) {
            revoke(entity, AdminSessionCloseReason.CONCURRENT, null, now);
            return ValidationResult.CONCURRENT;
        }
        String normalized = normalize(username);
        if (normalized != null && !normalized.equals(entity.getNormalizedUsername())) {
            // Token 与当前认证主体不一致，视为并发登录
            revoke(entity, AdminSessionCloseReason.CONCURRENT, null, now);
            return ValidationResult.CONCURRENT;
        }
        entity.setLastSeenAt(now);
        entity.setExpiresAt(resolveExpiry(now, null));
        repository.save(entity);
        return ValidationResult.ACTIVE;
    }

    public void invalidateByRefreshToken(String refreshToken, AdminSessionCloseReason reason) {
        String refreshHash = hash(refreshToken);
        if (!StringUtils.hasText(refreshHash)) {
            return;
        }
        repository.findByRefreshTokenHash(refreshHash).ifPresent(entity -> revoke(entity, reason, null, Instant.now()));
    }

    public void invalidateByAccessToken(String accessToken, AdminSessionCloseReason reason) {
        String accessHash = hash(accessToken);
        if (!StringUtils.hasText(accessHash)) {
            return;
        }
        repository.findByAccessTokenHash(accessHash).ifPresent(entity -> revoke(entity, reason, null, Instant.now()));
    }

    public Optional<String> resolveUsernameFromAccessToken(String accessToken) {
        String accessHash = hash(accessToken);
        if (!StringUtils.hasText(accessHash)) {
            return Optional.empty();
        }
        return repository.findByAccessTokenHash(accessHash).map(AdminSessionEntity::getUsername);
    }

    private void revoke(AdminSessionEntity entity, AdminSessionCloseReason reason, UUID takeoverSessionId, Instant when) {
        if (entity.getRevokedAt() != null) {
            return;
        }
        entity.setRevokedAt(when == null ? Instant.now() : when);
        entity.setRevokedReason(reason);
        if (takeoverSessionId != null) {
            entity.setRevokedBySessionId(takeoverSessionId);
        }
        repository.save(entity);
    }

    private boolean isExpired(AdminSessionEntity entity, Instant now) {
        Instant expiresAt = entity.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(now);
    }

    private Instant resolveExpiry(Instant now, Instant tokenExpiry) {
        Instant candidate = now.plus(sessionTtl);
        if (tokenExpiry != null && tokenExpiry.isBefore(candidate)) {
            return tokenExpiry;
        }
        return candidate;
    }

    private String normalize(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String hash(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            log.debug("Failed to hash token: {}", ex.getMessage());
            return Integer.toHexString(token.hashCode());
        }
    }

    private ValidationResult mapReason(AdminSessionCloseReason reason) {
        if (reason == null) {
            return ValidationResult.UNKNOWN;
        }
        return switch (reason) {
            case LOGOUT -> ValidationResult.LOGOUT;
            case CONCURRENT -> ValidationResult.CONCURRENT;
            case EXPIRED -> ValidationResult.EXPIRED;
            case MANUAL -> ValidationResult.CONCURRENT;
        };
    }
}
