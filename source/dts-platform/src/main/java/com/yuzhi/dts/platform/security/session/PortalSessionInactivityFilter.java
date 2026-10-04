package com.yuzhi.dts.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.AuthEndpointPaths;
import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PortalSessionInactivityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PortalSessionInactivityFilter.class);
    @SuppressWarnings("unused")
    private static final Runnable CLASS_COMPAT_HOLDER = new Runnable() {
        @Override
        public void run() {
            // no-op placeholder to retain generated PortalSessionInactivityFilter$1 for older runtimes
        }
    };

    private final ObjectMapper objectMapper;
    private final PortalSessionActivityService activityService;
    private final PortalSessionCookieService cookieService;

    public PortalSessionInactivityFilter(
        ObjectMapper objectMapper,
        PortalSessionActivityService activityService,
        PortalSessionCookieService cookieService
    ) {
        this.objectMapper = objectMapper;
        this.activityService = activityService;
        this.cookieService = cookieService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = Optional.ofNullable(request.getRequestURI()).orElse("");
        if (AuthEndpointPaths.isKeycloakAuthEndpoint(uri)) {
            return true;
        }
        if (AuthEndpointPaths.isKeycloakLocalizationEndpoint(uri)) {
            return true;
        }
        if (AuthEndpointPaths.isPortalSessionStatusEndpoint(uri)) {
            return true;
        }
        return !StringUtils.hasText(resolveSessionToken(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String tokenValue = resolveSessionToken(request);
        if (tokenValue == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ValidationResult result = activityService.touch(tokenValue, Instant.now());
        switch (result) {
            case ACTIVE -> {
                filterChain.doFilter(request, response);
                return;
            }
            case CONCURRENT -> {
                log.debug(
                    "Portal session takeover detected, rejecting token suffix=...{}",
                    tokenValue.substring(Math.max(0, tokenValue.length() - 6))
                );
                respondConflict(response);
                return;
            }
            default -> {
                log.debug(
                    "Portal session expired for token suffix=...{}",
                    tokenValue.substring(Math.max(0, tokenValue.length() - 6))
                );
                respondExpired(response);
                return;
            }
        }
    }

    private String resolveSessionToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String cookieToken = cookieService == null ? null : cookieService.resolvePortalSessionToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken.trim();
        }
        return null;
    }

    private void respondConflict(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Session-Conflict", "true");
        ApiResponse<Object> payload = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "该账号已在其他位置登录，当前会话已失效", null);
        objectMapper.writeValue(response.getWriter(), payload);
        response.flushBuffer();
    }

    private void respondExpired(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Session-Expired", "true");
        ApiResponse<Object> payload = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), "会话已超时，请重新登录", null);
        objectMapper.writeValue(response.getWriter(), payload);
        response.flushBuffer();
    }
}
