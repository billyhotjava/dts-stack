package com.yuzhi.dts.admin.security;

import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAuditListener {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditListener.class);
    private static final String LOGIN_AUDITED_SESSION_ATTR = "dts.admin.audit.login.recorded";
    private static final String LOGIN_AUDITED_REQUEST_ATTR = "dts.admin.audit.login.request.marked";

    private static final Duration LOGIN_DUP_WINDOW = Duration.ofSeconds(15);

    private final AuditV2Service auditV2Service;
    private final AdminUserService adminUserService;
    private final ConcurrentMap<String, Boolean> auditedSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> recentLogins = new ConcurrentHashMap<>();

    public AuthAuditListener(AuditV2Service auditV2Service, AdminUserService adminUserService) {
        this.auditV2Service = auditV2Service;
        this.adminUserService = adminUserService;
    }

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent evt) {
        String username = sanitizeUsername(evt.getAuthentication());
        if (username == null) {
            return;
        }
        if (!shouldRecordLogin(evt.getAuthentication(), username)) {
            return;
        }
        Map<String, Object> detail = Map.of(
            "module",
            "admin.auth",
            "mode",
            resolveAuthMode(evt.getAuthentication()),
            "audience",
            "admin"
        );
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGIN,
            AuditResultStatus.SUCCESS,
            "系统登录（管理端）成功",
            detail
        );
        if (log.isDebugEnabled()) {
            log.debug("Auth success (AuthenticationSuccessEvent) user={}", username);
        }
    }

    @EventListener
    public void onInteractiveAuthSuccess(InteractiveAuthenticationSuccessEvent evt) {
        String username = sanitizeUsername(evt.getAuthentication());
        if (username == null) {
            return;
        }
        if (!shouldRecordLogin(evt.getAuthentication(), username)) {
            return;
        }
        Map<String, Object> detail = Map.of(
            "module",
            "admin.auth",
            "mode",
            resolveAuthMode(evt.getAuthentication()),
            "audience",
            "admin"
        );
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGIN,
            AuditResultStatus.SUCCESS,
            "系统登录（管理端）成功",
            detail
        );
        if (log.isDebugEnabled()) {
            log.debug("Auth success (InteractiveAuthenticationSuccessEvent) user={}", username);
        }
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent evt) {
        String username = sanitizeUsername(evt.getAuthentication());
        String message = evt.getException() != null && evt.getException().getMessage() != null ? evt.getException().getMessage() : "auth failed";
        if (log.isDebugEnabled() && username != null) {
            log.debug("Auth failure ignored for audit user={} error={}", username, message);
        }
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent evt) {
        String username = sanitizeUsername(evt.getAuthentication());
        if (username == null) {
            return;
        }
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGOUT,
            AuditResultStatus.SUCCESS,
            "系统退出（管理端）成功",
            Map.of()
        );
        clearLoginMarkers(evt.getAuthentication(), username);
        if (log.isDebugEnabled()) {
            log.debug("Logout success user={}", username);
        }
    }

    private boolean shouldRecordLogin(Authentication authentication, String username) {
        if (!markSessionFlag()) {
            return false;
        }
        boolean sessionRecorded = registerSessionMarker(authentication);
        if (!sessionRecorded && !registerPrincipalMarker(username)) {
            return false;
        }
        if (!throttleLoginDuplicates(username)) {
            return false;
        }
        return true;
    }

    private boolean markSessionFlag() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                if (request != null) {
                    if (Boolean.TRUE.equals(request.getAttribute(LOGIN_AUDITED_REQUEST_ATTR))) {
                        return false;
                    }
                    request.setAttribute(LOGIN_AUDITED_REQUEST_ATTR, Boolean.TRUE);
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        if (Boolean.TRUE.equals(session.getAttribute(LOGIN_AUDITED_SESSION_ATTR))) {
                            return false;
                        }
                        session.setAttribute(LOGIN_AUDITED_SESSION_ATTR, Boolean.TRUE);
                    }
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to mark login audit flag: {}", ex.getMessage());
            }
        }
        return true;
    }

    private boolean throttleLoginDuplicates(String username) {
        if (StringUtils.isBlank(username)) {
            return true;
        }
        String clientIp = resolveClientIp();
        String key = buildLoginKey(username, clientIp);
        Instant now = Instant.now();
        AtomicBoolean allowed = new AtomicBoolean(true);
        recentLogins.compute(key, (k, last) -> {
            if (last != null && Duration.between(last, now).compareTo(LOGIN_DUP_WINDOW) <= 0) {
                allowed.set(false);
                return last;
            }
            return now;
        });
        return allowed.get();
    }

    private void clearLoginMarkers(Authentication authentication, String username) {
        String sessionId = extractSessionId(authentication);
        if (sessionId != null) {
            auditedSessions.remove(sessionMarkerKey(sessionId));
        }
        String principalMarker = principalMarkerKey(username);
        if (principalMarker != null) {
            auditedSessions.remove(principalMarker);
        }
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                if (request != null) {
                    request.removeAttribute(LOGIN_AUDITED_REQUEST_ATTR);
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.removeAttribute(LOGIN_AUDITED_SESSION_ATTR);
                    }
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to clear login audit flag: {}", ex.getMessage());
            }
        }
        String clientIp = resolveClientIp();
        String key = buildLoginKey(username, clientIp);
        recentLogins.remove(key);
    }

    private String extractSessionId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails webDetails) {
            return webDetails.getSessionId();
        }
        return null;
    }

    private String resolveClientIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                if (request != null) {
                    return IpAddressUtils.resolveClientIp(
                        request.getHeader("X-Forwarded-For"),
                        request.getHeader("X-Real-IP"),
                        request.getRemoteAddr()
                    );
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve client IP for login audit: {}", ex.getMessage());
            }
        }
        return null;
    }

    private String buildLoginKey(String username, String clientIp) {
        String userPart = StringUtils.isBlank(username) ? "unknown" : username.trim().toLowerCase(Locale.ROOT);
        String ipPart = StringUtils.isBlank(clientIp) ? "-" : clientIp.trim();
        return userPart + "@" + ipPart;
    }

    private boolean registerSessionMarker(Authentication authentication) {
        String sessionId = extractSessionId(authentication);
        if (StringUtils.isBlank(sessionId)) {
            return false;
        }
        String key = sessionMarkerKey(sessionId);
        return auditedSessions.putIfAbsent(key, Boolean.TRUE) == null;
    }

    private boolean registerPrincipalMarker(String username) {
        String marker = principalMarkerKey(username);
        if (marker == null) {
            return true;
        }
        return auditedSessions.putIfAbsent(marker, Boolean.TRUE) == null;
    }

    private String principalMarkerKey(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        String clientIp = resolveClientIp();
        if (StringUtils.isBlank(clientIp)) {
            clientIp = "-";
        }
        return "principal:" + normalized + "@" + clientIp;
    }

    private String sessionMarkerKey(String sessionId) {
        return "session:" + sessionId;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private void recordAuthEventV2(
        Authentication authentication,
        String username,
        String buttonCode,
        AuditResultStatus result,
        String summary,
        Map<String, Object> detail
    ) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        try {
            HttpServletRequest request = currentRequest();
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(username, buttonCode)
                .summary(summary)
                .result(result)
                .actorName(resolveDisplayName(username))
                .client(resolveClientIp(), request != null ? request.getHeader("User-Agent") : null)
                .request(
                    request != null ? request.getRequestURI() : "/internal/admin-auth",
                    request != null ? request.getMethod() : "POST"
                )
                .allowEmptyTargets();
            if (authentication != null && authentication.getAuthorities() != null) {
                List<String> roles = authentication
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
                builder.actorRoles(roles);
            }
            if (detail != null && !detail.isEmpty()) {
                LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
                detail.forEach((k, v) -> {
                    if (k != null && v != null) {
                        copied.put(k, v);
                    }
                });
                copied.forEach(builder::metadata);
                builder.detail("detail", copied);
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 auth event [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private String sanitizeUsername(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String candidate = sanitizeUsername(authentication.getName());
        if (candidate != null) {
            return candidate;
        }
        candidate = sanitizeUsername(extractUsernameFromPrincipal(authentication.getPrincipal()));
        if (candidate != null) {
            return candidate;
        }
        candidate = sanitizeUsername(resolveUsernameFromRequest());
        if (candidate != null) {
            return candidate;
        }
        return "unknown";
    }

    private String sanitizeUsername(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String sanitized = SecurityUtils.sanitizeLogin(raw);
        if (!org.springframework.util.StringUtils.hasText(sanitized)) {
            return null;
        }
        if ("system".equalsIgnoreCase(sanitized)) {
            return null;
        }
        if (isMaskedToken(sanitized)) {
            return null;
        }
        return sanitized;
    }

    private String extractUsernameFromPrincipal(Object principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth) {
            String preferred = oauth.getAttribute("preferred_username");
            if (StringUtils.isNotBlank(preferred)) {
                return preferred;
            }
            String username = oauth.getAttribute("username");
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
            String legacy = oauth.getAttribute("user_name");
            if (StringUtils.isNotBlank(legacy)) {
                return legacy;
            }
            String sub = oauth.getAttribute("sub");
            if (StringUtils.isNotBlank(sub)) {
                return sub;
            }
        }
        if (principal instanceof Jwt jwt) {
            return extractUsernameFromMap(jwt.getClaims());
        }
        if (principal instanceof Map<?, ?> map) {
            return extractUsernameFromMap(map);
        }
        if (principal instanceof String str) {
            return str;
        }
        return null;
    }

    private String extractUsernameFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return firstNonBlank(
            toString(map.get("preferred_username")),
            toString(map.get("username")),
            toString(map.get("user_name")),
            toString(map.get("sub")),
            toString(map.get("name"))
        );
    }

    private String toString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text == null ? null : text.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isMaskedToken(String value) {
        return value != null && value.startsWith("token:");
    }

    private static final String LAST_USERNAME_ATTRIBUTE = "SPRING_SECURITY_LAST_USERNAME";

    private String resolveUsernameFromRequest() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        for (String param : List.of("username", "user", "login", "account", "email")) {
            String value = request.getParameter(param);
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        Object attr = request.getAttribute(LAST_USERNAME_ATTRIBUTE);
        if (attr instanceof String attrValue && StringUtils.isNotBlank(attrValue)) {
            return attrValue.trim();
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionAttr = session.getAttribute(LAST_USERNAME_ATTRIBUTE);
            if (sessionAttr instanceof String sessionValue && StringUtils.isNotBlank(sessionValue)) {
                return sessionValue.trim();
            }
        }
        String authHeaderUser = resolveUsernameFromAuthorizationHeader(request.getHeader("Authorization"));
        if (StringUtils.isNotBlank(authHeaderUser)) {
            return authHeaderUser;
        }
        return null;
    }

    private String resolveUsernameFromAuthorizationHeader(String authorization) {
        if (!org.springframework.util.StringUtils.hasText(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Basic ", 0, 6)) {
            String token = trimmed.substring(6).trim();
            try {
                byte[] decoded = Base64.getDecoder().decode(token);
                String pair = new String(decoded, StandardCharsets.UTF_8);
                int colon = pair.indexOf(':');
                if (colon >= 0) {
                    return pair.substring(0, colon);
                }
                return pair;
            } catch (IllegalArgumentException ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to decode basic auth header: {}", ex.getMessage());
                }
            }
        }
        return null;
    }

    private String resolveDisplayName(String username) {
        if (StringUtils.isBlank(username)) {
            return username;
        }
        String trimmed = username.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return trimmed;
        }
        String builtin = BUILTIN_LABELS.get(lower);
        try {
            Map<String, String> resolved = adminUserService.resolveDisplayNames(List.of(trimmed));
            String display = resolved.get(trimmed);
            if (StringUtils.isBlank(display)) {
                display = resolved.get(lower);
            }
            if (StringUtils.isNotBlank(display)) {
                return display.trim();
            }
        } catch (Exception ex) {
            log.debug("Failed to resolve display name for {}: {}", username, ex.getMessage());
        }
        return builtin != null ? builtin : trimmed;
    }

    private static final Map<String, String> BUILTIN_LABELS = Map.of(
        "sysadmin",
        "系统管理员",
        "authadmin",
        "授权管理员",
        "auditadmin",
        "安全审计员",
        "opadmin",
        "运维管理员"
    );

    private String resolveAuthMode(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object mode = map.get("mode");
            if (mode != null) {
                return mode.toString();
            }
        }
        return "password";
    }
}
