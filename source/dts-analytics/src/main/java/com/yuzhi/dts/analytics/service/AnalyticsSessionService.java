package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsSession;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsSessionRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalyticsSessionService {

    public static final String SESSION_COOKIE_NAME = "metabase.SESSION";
    private static final String ATTR_RESOLVED_USER = AnalyticsSessionService.class.getName() + ".resolvedUser";

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofDays(14);

    private final AnalyticsSessionRepository sessionRepository;
    private final AnalyticsUserRepository userRepository;
    private final PlatformTrustedUserService platformTrustedUserService;

    public AnalyticsSessionService(
            AnalyticsSessionRepository sessionRepository,
            AnalyticsUserRepository userRepository,
            PlatformTrustedUserService platformTrustedUserService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.platformTrustedUserService = platformTrustedUserService;
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
        Optional<AnalyticsUser> resolved = platformTrustedUserService.resolveOrProvision(request).filter(AnalyticsUser::isActive);
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

    private AnalyticsSession touchSession(AnalyticsSession session) {
        session.setLastSeenAt(Instant.now());
        return sessionRepository.save(session);
    }
}
