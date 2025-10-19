package com.yuzhi.dts.platform.security.session;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Simple in-memory session registry used for demo authentication flows.
 * Accepts login requests issued by {@link com.yuzhi.dts.platform.web.rest.KeycloakAuthResource}
 * and stores generated access/refresh tokens together with the granted authorities.
 */
@Component
public class PortalSessionRegistry {

    private final Duration sessionTtl;
    private final Map<String, PortalSession> accessTokenIndex = new ConcurrentHashMap<>();
    private final Map<String, PortalSession> refreshTokenIndex = new ConcurrentHashMap<>();
    private final Map<String, PortalSession> userIndex = new ConcurrentHashMap<>();

    public PortalSessionRegistry(@Value("${dts.platform.session.timeout-minutes:10}") long timeoutMinutes) {
        long minutes = timeoutMinutes <= 0 ? 10 : timeoutMinutes;
        this.sessionTtl = Duration.ofMinutes(minutes);
    }

    /**
     * Register a brand new session for the given username/authorities.
     */
    public synchronized PortalSession createSession(String username, List<String> roles, List<String> permissions, AdminTokens adminTokens) {
        enforceSingleSession(username);
        PortalSession session = PortalSession.create(username, normalizeRoles(roles), permissions, null, null, adminTokens, sessionTtl);
        register(session);
        return session;
    }

    /**
     * Overload: Register a session with additional attributes such as dept code and personnel level.
     */
    public synchronized PortalSession createSession(
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        AdminTokens adminTokens
    ) {
        enforceSingleSession(username);
        PortalSession session = PortalSession.create(
            username,
            normalizeRoles(roles),
            permissions,
            deptCode,
            personnelLevel,
            adminTokens,
            sessionTtl
        );
        register(session);
        return session;
    }

    /**
     * Refresh an existing session by refresh token, returning a new set of access/refresh tokens.
     */
    public synchronized PortalSession refreshSession(String refreshToken, Function<PortalSession, AdminTokens> adminTokenProvider) {
        PortalSession existing = refreshTokenIndex.get(refreshToken);
        if (existing == null) {
            throw new IllegalArgumentException("unknown_refresh_token");
        }
        if (existing.isExpired()) {
            remove(existing);
            throw new IllegalStateException("session_expired");
        }
        AdminTokens tokens = existing.adminTokens();
        if (adminTokenProvider != null) {
            try {
                AdminTokens refreshed = adminTokenProvider.apply(existing);
                if (refreshed != null) {
                    tokens = refreshed;
                }
            } catch (Exception ignored) {
                // fall back to existing tokens when refresh fails
            }
        }
        PortalSession renewed = existing.renew(sessionTtl, tokens);
        register(renewed);
        return renewed;
    }

    /**
     * Locate a session by access token if present and not expired.
     */
    public Optional<PortalSession> findByAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return Optional.empty();
        }
        PortalSession session = accessTokenIndex.get(accessToken);
        if (session == null) {
            return Optional.empty();
        }
        synchronized (this) {
            PortalSession current = accessTokenIndex.get(accessToken);
            if (current == null) {
                return Optional.empty();
            }
            if (current.isExpired()) {
                remove(current);
                return Optional.empty();
            }
            PortalSession extended = current.extend(sessionTtl);
            register(extended);
            return Optional.of(extended);
        }
    }

    public synchronized PortalSession invalidateByRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        PortalSession existing = refreshTokenIndex.remove(refreshToken);
        if (existing == null) {
            return null;
        }
        accessTokenIndex.remove(existing.accessToken());
        PortalSession current = userIndex.get(existing.username());
        if (current != null && current.sessionId().equals(existing.sessionId())) {
            userIndex.remove(existing.username());
        }
        return existing;
    }

    public synchronized boolean hasActiveSession(String username) {
        PortalSession current = userIndex.get(username);
        if (current == null) {
            return false;
        }
        if (current.isExpired()) {
            remove(current);
            return false;
        }
        return true;
    }

    private void enforceSingleSession(String username) {
        PortalSession existing = userIndex.get(username);
        if (existing != null && !existing.isExpired()) {
            throw new IllegalStateException("session_active");
        }
        if (existing != null && existing.isExpired()) {
            remove(existing);
        }
    }

    private void register(PortalSession session) {
        // Remove previous session for same user to enforce single-login semantics
        PortalSession previous = userIndex.put(session.username(), session);
        if (previous != null) {
            accessTokenIndex.remove(previous.accessToken());
            refreshTokenIndex.remove(previous.refreshToken());
        }
        accessTokenIndex.put(session.accessToken(), session);
        refreshTokenIndex.put(session.refreshToken(), session);
    }

    private void remove(PortalSession session) {
        accessTokenIndex.remove(session.accessToken());
        refreshTokenIndex.remove(session.refreshToken());
        PortalSession current = userIndex.get(session.username());
        if (current != null && current.sessionId().equals(session.sessionId())) {
            userIndex.remove(session.username());
        }
    }

    private List<String> normalizeRoles(List<String> roles) {
        List<String> merged = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (StringUtils.hasText(role)) {
                    merged.add(role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase());
                }
            }
        }
        if (merged.isEmpty()) {
            merged.add(AuthoritiesConstants.USER);
        }
        return merged.stream().distinct().toList();
    }

    /**
     * Representation of an authenticated portal session.
     */
    public record PortalSession(
        String sessionId,
        String username,
        List<String> roles,
        List<String> permissions,
        String deptCode,
        String personnelLevel,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        AdminTokens adminTokens
    ) {
        private static PortalSession create(
            String username,
            List<String> roles,
            List<String> permissions,
            String deptCode,
            String personnelLevel,
            AdminTokens adminTokens,
            Duration ttl
        ) {
            String sessionId = UUID.randomUUID().toString();
            String accessToken = "demo-" + UUID.randomUUID();
            String refreshToken = "refresh-" + UUID.randomUUID();
            Instant expiresAt = Instant.now().plus(ttl);
            List<String> perms = permissions == null ? Collections.emptyList() : List.copyOf(permissions);
            AdminTokens tokens = adminTokens == null ? null : adminTokens;
            return new PortalSession(
                sessionId,
                username,
                List.copyOf(roles),
                perms,
                deptCode,
                personnelLevel,
                accessToken,
                refreshToken,
                expiresAt,
                tokens
            );
        }

        private PortalSession renew(Duration ttl, AdminTokens adminTokens) {
            return create(username, roles, permissions, deptCode, personnelLevel, adminTokens, ttl);
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        private PortalSession extend(Duration ttl) {
            Instant newExpiry = Instant.now().plus(ttl);
            return new PortalSession(
                sessionId,
                username,
                roles,
                permissions,
                deptCode,
                personnelLevel,
                accessToken,
                refreshToken,
                newExpiry,
                adminTokens
            );
        }
    }

    public record AdminTokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {}
}
