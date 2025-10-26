package com.yuzhi.dts.admin.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry.ValidationResult;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

@Component
public class SessionInactivityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionInactivityFilter.class);

    private final ObjectMapper objectMapper;
    private final AdminSessionRegistry sessionRegistry;

    public SessionInactivityFilter(ObjectMapper objectMapper, AdminSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header == null || !header.startsWith("Bearer ");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String tokenValue = extractTokenValue(authHeader);
        if (!StringUtils.hasText(tokenValue)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        String sessionState = extractSessionState(authentication);
        ValidationResult result = sessionRegistry.validate(tokenValue, sessionState, username);
        switch (result) {
            case ACTIVE -> {
                filterChain.doFilter(request, response);
                return;
            }
            case EXPIRED -> {
                log.debug("Session expired for {}", username);
                emitResponse(response, HttpStatus.UNAUTHORIZED, "会话已超时，请重新登录", "X-Session-Expired");
                return;
            }
            case CONCURRENT -> {
                log.debug("Rejecting concurrent session for {}", username);
                emitResponse(response, HttpStatus.UNAUTHORIZED, "该账号已在其他位置登录，当前会话已失效", "X-Session-Conflict");
                return;
            }
            case LOGOUT -> {
                log.debug("Session has been terminated for {}", username);
                emitResponse(response, HttpStatus.UNAUTHORIZED, "会话已注销，请重新登录", "X-Session-Expired");
                return;
            }
            default -> {
                log.debug("Unknown session state for {}", username);
                emitResponse(response, HttpStatus.UNAUTHORIZED, "会话状态异常，请重新登录", "X-Session-Expired");
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

    private String extractSessionState(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String state = jwtAuth.getToken().getClaimAsString("session_state");
            if (state != null && !state.isBlank()) {
                return state;
            }
        }
        if (authentication instanceof BearerTokenAuthentication bearerAuth) {
            Object value = Optional.ofNullable(bearerAuth.getTokenAttributes()).map(attrs -> attrs.get("session_state")).orElse(null);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    private void emitResponse(HttpServletResponse response, HttpStatus status, String message, String header) throws IOException {
        response.resetBuffer();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(header, "true");
        ApiResponse<Object> payload = ApiResponse.error(message);
        objectMapper.writeValue(response.getWriter(), payload);
        response.flushBuffer();
    }
}
