package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSession;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSessionRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.security.ApiKeyAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AnalyticsSessionService {

    public static final String SESSION_COOKIE_NAME = "metabase.SESSION";
    private static final String ATTR_RESOLVED_USER = AnalyticsSessionService.class.getName() + ".resolvedUser";

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofDays(14);

    private final AnalyticsSessionRepository sessionRepository;
    private final AnalyticsUserRepository userRepository;
    private final ApiKeyAuthService apiKeyAuthService;

    public AnalyticsSessionService(
            AnalyticsSessionRepository sessionRepository,
            AnalyticsUserRepository userRepository,
            ApiKeyAuthService apiKeyAuthService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.apiKeyAuthService = apiKeyAuthService;
    }

    public UUID createSession(Long userId) {
        AnalyticsSession session = new AnalyticsSession();
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(DEFAULT_SESSION_TTL));
        return sessionRepository.save(session).getId();
    }

    public Optional<AnalyticsUser> resolveUser(HttpServletRequest request) {
        Object cached = request.getAttribute(ATTR_RESOLVED_USER);
        if (cached instanceof AnalyticsUser user) {
            return Optional.of(user);
        }
        if (Boolean.FALSE.equals(cached)) {
            return Optional.empty();
        }

        Optional<AnalyticsUser> byMetabaseSession = resolveSessionId(request)
                .flatMap(sessionId -> sessionRepository.findByIdAndRevokedFalseAndExpiresAtAfter(sessionId, Instant.now()))
                .map(session -> touchSession(session))
                .flatMap(session -> userRepository.findById(session.getUserId()))
                .filter(AnalyticsUser::isActive);
        if (byMetabaseSession.isPresent()) {
            request.setAttribute(ATTR_RESOLVED_USER, byMetabaseSession.get());
            return byMetabaseSession;
        }
        Optional<AnalyticsUser> resolved = resolveViaApiKey(request);
        if (resolved.isPresent()) {
            request.setAttribute(ATTR_RESOLVED_USER, resolved.get());
        } else {
            request.setAttribute(ATTR_RESOLVED_USER, Boolean.FALSE);
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveSessionId(HttpServletRequest request) {
        String header = request.getHeader("X-Metabase-Session");
        if (header != null && !header.isBlank()) {
            try {
                return Optional.of(UUID.fromString(header.trim()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                try {
                    return Optional.of(UUID.fromString(cookie.getValue().trim()));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public void revokeSession(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            sessionRepository.save(session);
        });
    }

    private Optional<AnalyticsUser> resolveViaApiKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return Optional.empty();
        }
        String rawKey;
        String lower = authorization.trim().toLowerCase();
        if (lower.startsWith("bearer ")) {
            rawKey = authorization.trim().substring(7).trim();
        } else {
            rawKey = authorization.trim();
        }
        if (!StringUtils.hasText(rawKey)) {
            return Optional.empty();
        }
        return apiKeyAuthService.authenticate(rawKey)
                .map(ApiKeyAuthService.AuthenticatedUser::user)
                .filter(AnalyticsUser::isActive);
    }

    private AnalyticsSession touchSession(AnalyticsSession session) {
        session.setLastSeenAt(Instant.now());
        return sessionRepository.save(session);
    }
}
