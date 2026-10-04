package com.yuzhi.dts.copilot.analytics.web.filter;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Embedded-mode bridge:
 * - If the request is already authenticated via platform (forward-auth headers or bearer token)
 * - and no Metabase session cookie exists,
 * then mint a {@code metabase.SESSION} for the browser so legacy Metabase UI does not show a
 * separate login screen.
 */
@Component
public class PlatformSessionBridgeFilter extends OncePerRequestFilter {

    private final AnalyticsSessionService sessionService;

    public PlatformSessionBridgeFilter(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (sessionService.resolveSessionId(request).isEmpty()) {
            Optional<AnalyticsUser> user = sessionService.resolveUser(request);
            if (user.isPresent()) {
                boolean secure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))
                        || "https".equalsIgnoreCase(request.getScheme());
                UUID deviceId = resolveDeviceId(request).orElseGet(UUID::randomUUID);
                UUID sessionId = sessionService.createSession(user.get().getId());
                for (String cookie : MetabaseCookies.loginCookieHeaders(sessionId, deviceId, secure)) {
                    response.addHeader("Set-Cookie", cookie);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private static Optional<UUID> resolveDeviceId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (!"metabase.DEVICE".equals(c.getName())) {
                continue;
            }
            String v = c.getValue();
            if (v == null || v.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(v.trim()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

