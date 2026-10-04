package com.yuzhi.dts.copilot.analytics.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Filter that extracts and validates API keys via copilot-ai.
 * Falls back to metabase.SESSION cookie when no Authorization header is present.
 * Sets the SecurityContext with an authentication token on success.
 */
@Component
public class AnalyticsApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsApiKeyAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> WHITELISTED_PATHS = List.of(
            "/actuator/**",
            "/api/health",
            "/api/info",
            "/api/public/**",
            "/api/setup",
            "/api/setup/**",
            "/api/session",
            "/api/session/**"
    );

    private final ApiKeyAuthService apiKeyAuthService;
    private final AnalyticsSessionService sessionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsApiKeyAuthFilter(ApiKeyAuthService apiKeyAuthService, AnalyticsSessionService sessionService) {
        this.apiKeyAuthService = apiKeyAuthService;
        this.sessionService = sessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return WHITELISTED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // 1. Try Authorization: Bearer <key>
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String rawKey = authHeader.substring(BEARER_PREFIX.length()).trim();
            Optional<ApiKeyAuthService.AuthenticatedUser> authResult = apiKeyAuthService.authenticate(rawKey);

            if (authResult.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid, expired, or revoked API key\"}");
                return;
            }

            ApiKeyAuthService.AuthenticatedUser authenticatedUser = authResult.get();
            AnalyticsAuthentication authentication = new AnalyticsAuthentication(authenticatedUser);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated analytics request via API key for user id={}", authenticatedUser.user().getId());
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Fallback: try metabase.SESSION cookie
        Optional<AnalyticsUser> sessionUser = sessionService.resolveUser(request);
        if (sessionUser.isPresent()) {
            AnalyticsUser user = sessionUser.get();
            SessionAuthentication authentication = new SessionAuthentication(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated analytics request via session cookie for user id={}", user.getId());
            filterChain.doFilter(request, response);
            return;
        }

        // 3. No valid credentials
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
    }

    /**
     * Authentication token for session-cookie-based requests.
     */
    public static class SessionAuthentication extends AbstractAuthenticationToken {
        private final AnalyticsUser user;

        public SessionAuthentication(AnalyticsUser user) {
            super(Collections.emptyList());
            this.user = user;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return user;
        }
    }

    /**
     * Authentication token for analytics requests.
     */
    public static class AnalyticsAuthentication extends AbstractAuthenticationToken {

        private final ApiKeyAuthService.AuthenticatedUser authenticatedUser;

        public AnalyticsAuthentication(ApiKeyAuthService.AuthenticatedUser authenticatedUser) {
            super(Collections.emptyList());
            this.authenticatedUser = authenticatedUser;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return authenticatedUser.apiKey();
        }

        @Override
        public Object getPrincipal() {
            return authenticatedUser.user();
        }

        public ApiKeyAuthService.AuthenticatedUser getAuthenticatedUser() {
            return authenticatedUser;
        }
    }
}
