package com.yuzhi.dts.platform.security.session;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Transactional
public class PortalSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PortalSessionRegistry.class);

    private final Duration sessionTtl;
    private final PortalSessionRepository sessionRepository;
    private final boolean allowTakeover;

    public PortalSessionRegistry(
        @Value("${dts.platform.session.timeout-minutes:30}") long timeoutMinutes,
        @Value("${dts.platform.session.allow-takeover:true}") boolean allowTakeover,
        PortalSessionRepository sessionRepository
    ) {
        long minutes = timeoutMinutes <= 0 ? 30 : timeoutMinutes;
        this.sessionTtl = Duration.ofMinutes(minutes);
        this.sessionRepository = sessionRepository;
        this.allowTakeover = allowTakeover;
    }

    public PortalSession createSession(String username, List<String> roles, List<String> permissions, AdminTokens adminTokens) {
        return createSessionInternal(username, roles, permissions, null, null, null, null, adminTokens);
    }

    public PortalSession createSession(
        String username,
        List<String> roles,
        List<String> permissions,
        String displayName,
        AdminTokens adminTokens
    ) {
        return createSessionInternal(username, roles, permissions, null, null, displayName, null, adminTokens);
    }

    public PortalSession createSession(
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        AdminTokens adminTokens
    ) {
        return createSessionInternal(username, roles, permissions, deptCode, personnelLevel, null, null, adminTokens);
    }

    public PortalSession createSession(
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        String displayName,
        AdminTokens adminTokens
    ) {
        return createSessionInternal(username, roles, permissions, deptCode, personnelLevel, displayName, null, adminTokens);
    }

    public PortalSession createSession(
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        String displayName,
        String browserId,
        AdminTokens adminTokens
    ) {
        return createSessionInternal(username, roles, permissions, deptCode, personnelLevel, displayName, browserId, adminTokens);
    }

    public PortalSession refreshSession(String refreshToken, Function<PortalSession, AdminTokens> adminTokenProvider) {
        PortalSessionEntity existing = sessionRepository
            .findByRefreshTokenForUpdate(refreshToken)
            .orElseThrow(() -> new IllegalArgumentException("unknown_refresh_token"));

        Instant now = Instant.now();
        if (existing.getRevokedAt() != null) {
            throw new IllegalStateException("session_revoked");
        }
        if (isExpired(existing, now)) {
            revokeSession(existing, PortalSessionCloseReason.EXPIRED, null, now);
            throw new IllegalStateException("session_expired");
        }

        PortalSession current = toPortalSession(existing);
        AdminTokens tokens = current.adminTokens();
        if (adminTokenProvider != null) {
            AdminTokens refreshed = adminTokenProvider.apply(current);
            if (refreshed != null) {
                tokens = refreshed;
            }
        }

        existing.setExpiresAt(now.plus(sessionTtl));
        existing.setLastSeenAt(now);
        if (tokens != null) {
            existing.setAdminAccessToken(tokens.accessToken());
            existing.setAdminAccessTokenExpiresAt(tokens.accessExpiresAt());
            existing.setAdminRefreshToken(tokens.refreshToken());
            existing.setAdminRefreshTokenExpiresAt(tokens.refreshExpiresAt());
        }
        PortalSessionEntity saved = sessionRepository.saveAndFlush(existing);
        return toPortalSession(saved);
    }

    public Optional<PortalSession> findByAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return sessionRepository
            .findByAccessToken(accessToken)
            .flatMap(entity -> {
                if (entity.getRevokedAt() != null) {
                    return Optional.empty();
                }
                if (isExpired(entity, now)) {
                    revokeSession(entity, PortalSessionCloseReason.EXPIRED, null, now);
                    return Optional.empty();
                }
                entity.setLastSeenAt(now);
                entity.setExpiresAt(now.plus(sessionTtl));
                sessionRepository.save(entity);
                return Optional.of(toPortalSession(entity));
            });
    }

    public PortalSession invalidateByRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        Instant now = Instant.now();
        return sessionRepository
            .findByRefreshToken(refreshToken)
            .map(entity -> {
                revokeSession(entity, PortalSessionCloseReason.LOGOUT, null, now);
                return toPortalSession(entity);
            })
            .orElse(null);
    }

    public PortalSession invalidateByAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        Instant now = Instant.now();
        return sessionRepository
            .findByAccessToken(accessToken)
            .map(entity -> {
                revokeSession(entity, PortalSessionCloseReason.LOGOUT, null, now);
                return toPortalSession(entity);
            })
            .orElse(null);
    }

    public boolean hasActiveSession(String username) {
        return hasActiveSession(username, null);
    }

    public boolean hasActiveSession(String username, String browserId) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }
        Instant now = Instant.now();
        PortalSessionEntity entity = sessionRepository.findActiveForUpdate(normalized).orElse(null);
        if (entity == null) {
            return false;
        }
        if (isExpired(entity, now)) {
            revokeSession(entity, PortalSessionCloseReason.EXPIRED, null, now);
            return false;
        }
        String normalizedBrowserId = normalizeBrowserId(browserId);
        if (normalizedBrowserId != null && normalizedBrowserId.equals(entity.getBrowserId())) {
            return false;
        }
        return true;
    }

    public boolean isTakeoverAllowed() {
        return allowTakeover;
    }

    public Optional<String> resolveUsernameByRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        return sessionRepository
            .findByRefreshToken(refreshToken)
            .map(PortalSessionEntity::getUsername)
            .map(name -> name == null ? null : name.trim())
            .filter(StringUtils::hasText);
    }

    public Optional<String> resolveDisplayName(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return Optional.empty();
        }
        return sessionRepository
            .findByNormalizedUsernameAndRevokedAtIsNull(normalized)
            .map(PortalSessionEntity::getDisplayName)
            .map(display -> display == null ? null : display.trim())
            .filter(StringUtils::hasText);
    }

    private PortalSession createSessionInternal(
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        String displayName,
        String browserId,
        AdminTokens adminTokens
    ) {
        String sanitizedUsername = requireUsername(username);
        String normalizedUsername = normalizeUsername(sanitizedUsername);
        List<String> normalizedRoles = normalizeRoles(roles);
        String sanitizedDisplayName = displayName == null ? null : displayName.trim();
        String sanitizedBrowserId = normalizeBrowserId(browserId);

        PortalSession session = PortalSession.create(
            sanitizedUsername,
            sanitizedDisplayName,
            normalizedRoles,
            permissions,
            deptCode,
            personnelLevel,
            sanitizedBrowserId,
            adminTokens,
            sessionTtl
        );

        Instant now = Instant.now();
        UUID newSessionUuid = UUID.fromString(session.sessionId());
        final PortalSession[] refreshed = new PortalSession[1];

        sessionRepository
            .findActiveForUpdate(normalizedUsername)
            .ifPresent(existing -> {
                if (isExpired(existing, now)) {
                    revokeSession(existing, PortalSessionCloseReason.EXPIRED, null, now);
                    return;
                }
                if (sanitizedBrowserId != null && sanitizedBrowserId.equals(existing.getBrowserId())) {
                    refreshed[0] = refreshExistingBrowserSession(existing, session, normalizedUsername, now);
                    return;
                }
                if (!allowTakeover) {
                    throw new ActiveSessionExistsException(normalizedUsername);
                }
                handleTakeover(existing, newSessionUuid, now);
            });

        if (refreshed[0] != null) {
            return refreshed[0];
        }
        PortalSessionEntity entity = toEntity(session, normalizedUsername, now);
        sessionRepository.save(entity);
        return toPortalSession(entity);
    }

    private PortalSession refreshExistingBrowserSession(
        PortalSessionEntity existing,
        PortalSession session,
        String normalizedUsername,
        Instant now
    ) {
        existing.setUsername(session.username());
        existing.setNormalizedUsername(normalizedUsername);
        existing.setDisplayName(session.displayName());
        existing.setBrowserId(session.browserId());
        existing.setRoles(new ArrayList<>(session.roles()));
        existing.setPermissions(new ArrayList<>(session.permissions()));
        existing.setDeptCode(session.deptCode());
        existing.setPersonnelLevel(session.personnelLevel());
        existing.setExpiresAt(session.expiresAt());
        existing.setLastSeenAt(now);
        AdminTokens tokens = session.adminTokens();
        if (tokens == null) {
            existing.setAdminAccessToken(null);
            existing.setAdminAccessTokenExpiresAt(null);
            existing.setAdminRefreshToken(null);
            existing.setAdminRefreshTokenExpiresAt(null);
        } else {
            existing.setAdminAccessToken(tokens.accessToken());
            existing.setAdminAccessTokenExpiresAt(tokens.accessExpiresAt());
            existing.setAdminRefreshToken(tokens.refreshToken());
            existing.setAdminRefreshTokenExpiresAt(tokens.refreshExpiresAt());
        }
        PortalSessionEntity saved = sessionRepository.saveAndFlush(existing);
        return toPortalSession(saved);
    }

    private void handleTakeover(PortalSessionEntity existing, UUID takeoverSessionId, Instant now) {
        if (isExpired(existing, now)) {
            revokeSession(existing, PortalSessionCloseReason.EXPIRED, null, now);
            return;
        }
        revokeSession(existing, PortalSessionCloseReason.CONCURRENT, takeoverSessionId, now);
    }

    private boolean isExpired(PortalSessionEntity entity, Instant reference) {
        Instant expiresAt = entity.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(reference);
    }

    private void revokeSession(PortalSessionEntity entity, PortalSessionCloseReason reason, UUID takeoverSessionId, Instant when) {
        Instant effectiveWhen = when == null ? Instant.now() : when;
        if (entity.getRevokedAt() == null) {
            entity.setRevokedAt(effectiveWhen);
        }
        if (entity.getRevokedReason() == null && reason != null) {
            entity.setRevokedReason(reason);
        }
        if (takeoverSessionId != null && entity.getRevokedBySessionId() == null) {
            entity.setRevokedBySessionId(takeoverSessionId);
        }
        sessionRepository.saveAndFlush(entity);
        if (log.isDebugEnabled()) {
            log.debug(
                "[session] revoke username={} session={} reason={}",
                entity.getUsername(),
                entity.getSessionId(),
                reason == null ? "UNKNOWN" : reason
            );
        }
    }

    private PortalSessionEntity toEntity(PortalSession session, String normalizedUsername, Instant now) {
        PortalSessionEntity entity = new PortalSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(session.username());
        entity.setNormalizedUsername(normalizedUsername);
        entity.setSessionId(UUID.fromString(session.sessionId()));
        entity.setDisplayName(session.displayName());
        entity.setBrowserId(session.browserId());
        entity.setAccessToken(session.accessToken());
        entity.setRefreshToken(session.refreshToken());
        entity.setRoles(new ArrayList<>(session.roles()));
        entity.setPermissions(new ArrayList<>(session.permissions()));
        entity.setDeptCode(session.deptCode());
        entity.setPersonnelLevel(session.personnelLevel());
        entity.setExpiresAt(session.expiresAt());
        Instant effectiveNow = now == null ? Instant.now() : now;
        entity.setLastSeenAt(effectiveNow);
        entity.setCreatedAt(effectiveNow);
        if (session.adminTokens() != null) {
            entity.setAdminAccessToken(session.adminTokens().accessToken());
            entity.setAdminAccessTokenExpiresAt(session.adminTokens().accessExpiresAt());
            entity.setAdminRefreshToken(session.adminTokens().refreshToken());
            entity.setAdminRefreshTokenExpiresAt(session.adminTokens().refreshExpiresAt());
        }
        return entity;
    }

    private PortalSession toPortalSession(PortalSessionEntity entity) {
        List<String> roles = entity.getRoles() == null ? Collections.emptyList() : entity.getRoles();
        List<String> permissions = entity.getPermissions() == null ? Collections.emptyList() : entity.getPermissions();
        AdminTokens adminTokens = null;
        if (entity.getAdminAccessToken() != null || entity.getAdminRefreshToken() != null) {
            adminTokens =
                new AdminTokens(
                    entity.getAdminAccessToken(),
                    entity.getAdminAccessTokenExpiresAt(),
                    entity.getAdminRefreshToken(),
                    entity.getAdminRefreshTokenExpiresAt()
                );
        }
        return new PortalSession(
            entity.getSessionId().toString(),
            entity.getUsername(),
            entity.getDisplayName(),
            List.copyOf(roles),
            List.copyOf(permissions),
            entity.getDeptCode(),
            entity.getPersonnelLevel(),
            entity.getAccessToken(),
            entity.getRefreshToken(),
            entity.getExpiresAt(),
            entity.getBrowserId(),
            adminTokens
        );
    }

    private List<String> normalizeRoles(List<String> roles) {
        List<String> merged = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (StringUtils.hasText(role)) {
                    merged.add(role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(Locale.ROOT));
                }
            }
        }
        if (merged.isEmpty()) {
            merged.add(AuthoritiesConstants.USER);
        }
        return merged.stream().distinct().toList();
    }

    private String requireUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        String sanitized = username.trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return sanitized;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeBrowserId(String browserId) {
        if (browserId == null) {
            return null;
        }
        String trimmed = browserId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PortalSession(
        String sessionId,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String browserId,
        AdminTokens adminTokens
    ) {
        public PortalSession(
            String sessionId,
            String username,
            String displayName,
            List<String> roles,
            List<String> permissions,
            String deptCode,
            String personnelLevel,
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            AdminTokens adminTokens
        ) {
            this(
                sessionId,
                username,
                displayName,
                roles,
                permissions,
                deptCode,
                personnelLevel,
                accessToken,
                refreshToken,
                expiresAt,
                null,
                adminTokens
            );
        }

        private static PortalSession create(
            String username,
            String displayName,
            List<String> roles,
            List<String> permissions,
            String deptCode,
            String personnelLevel,
            String browserId,
            AdminTokens adminTokens,
            Duration ttl
        ) {
            String sessionId = UUID.randomUUID().toString();
            String accessToken = "demo-" + UUID.randomUUID();
            String refreshToken = "refresh-" + UUID.randomUUID();
            Instant expiresAt = Instant.now().plus(ttl);
            List<String> perms = permissions == null ? Collections.emptyList() : List.copyOf(permissions);
            AdminTokens tokens = adminTokens == null ? null : adminTokens;
            String normalizedDisplayName = displayName == null ? null : displayName.trim();
            if (normalizedDisplayName != null && normalizedDisplayName.isEmpty()) {
                normalizedDisplayName = null;
            }
            return new PortalSession(
                sessionId,
                username,
                normalizedDisplayName,
                List.copyOf(roles),
                perms,
                deptCode,
                personnelLevel,
                accessToken,
                refreshToken,
                expiresAt,
                browserId,
                tokens
            );
        }

        private PortalSession renew(Duration ttl, AdminTokens adminTokens) {
            return create(username, displayName, roles, permissions, deptCode, personnelLevel, browserId, adminTokens, ttl);
        }
    }

    public record AdminTokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {}

    public static class ActiveSessionExistsException extends RuntimeException {
        private final String normalizedUsername;

        public ActiveSessionExistsException(String normalizedUsername) {
            super("active_session_exists");
            this.normalizedUsername = normalizedUsername;
        }

        public String getNormalizedUsername() {
            return normalizedUsername;
        }
    }
}
