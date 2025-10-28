package com.yuzhi.dts.platform.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    public PortalSessionInactivityFilter(ObjectMapper objectMapper, PortalSessionActivityService activityService) {
        this.objectMapper = objectMapper;
        this.activityService = activityService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = Optional.ofNullable(request.getRequestURI()).orElse("");
        if (uri.startsWith("/api/keycloak/auth/")) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header == null || !header.startsWith("Bearer ");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String tokenValue = extractTokenValue(header);
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

    private String extractTokenValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int idx = header.indexOf(' ');
        if (idx < 0) {
            return header.trim();
        }
        return header.substring(idx + 1).trim();
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
