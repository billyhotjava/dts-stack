package com.yuzhi.dts.admin.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.security.session.SessionControlService;
import com.yuzhi.dts.admin.security.session.SessionControlService.ValidationResult;
import com.yuzhi.dts.admin.security.session.SessionKeyGenerator;
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

@Component
public class SessionInactivityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionInactivityFilter.class);

    private final ObjectMapper objectMapper;
    private final SessionControlService sessionControlService;

    public SessionInactivityFilter(ObjectMapper objectMapper, SessionControlService sessionControlService) {
        this.objectMapper = objectMapper;
        this.sessionControlService = sessionControlService;
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
        String tokenId = extractTokenId(authentication);
        String sessionKey = SessionKeyGenerator.fromToken(tokenId, tokenValue);
        if (sessionKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        String sessionState = extractSessionState(authentication);
        ValidationResult result = sessionControlService.touch(username, sessionState, sessionKey, Instant.now());
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

    private String extractTokenId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getId();
        }
        if (authentication instanceof BearerTokenAuthentication bearerAuth) {
            Object jti = Optional.ofNullable(bearerAuth.getTokenAttributes()).map(attrs -> attrs.get("jti")).orElse(null);
            if (jti instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
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
