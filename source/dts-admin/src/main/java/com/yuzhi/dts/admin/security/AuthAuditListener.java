package com.yuzhi.dts.admin.security;

import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private final ConcurrentMap<String, Boolean> auditedSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> recentLogins = new ConcurrentHashMap<>();

    public AuthAuditListener(AuditV2Service auditV2Service) {
        this.auditV2Service = auditV2Service;
    }

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        if (!shouldRecordLogin(evt.getAuthentication())) {
            return;
        }
        Map<String, Object> detail = Map.of("module", "admin.auth", "mode", resolveAuthMode(evt.getAuthentication()), "audience", "admin");
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGIN,
            AuditResultStatus.SUCCESS,
            "系统登录（管理端）成功",
            detail
        );
        if (log.isDebugEnabled()) log.debug("Auth success (AuthenticationSuccessEvent) user={}", username);
    }

    @EventListener
    public void onInteractiveAuthSuccess(InteractiveAuthenticationSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        if (!shouldRecordLogin(evt.getAuthentication())) {
            return;
        }
        Map<String, Object> detail = Map.of("module", "admin.auth", "mode", resolveAuthMode(evt.getAuthentication()), "audience", "admin");
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGIN,
            AuditResultStatus.SUCCESS,
            "系统登录（管理端）成功",
            detail
        );
        if (log.isDebugEnabled()) log.debug("Auth success (InteractiveAuthenticationSuccessEvent) user={}", username);
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        String message = evt.getException() != null && evt.getException().getMessage() != null ? evt.getException().getMessage() : "auth failed";
        Map<String, Object> detail = Map.of("error", message);
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGIN,
            AuditResultStatus.FAILED,
            "系统登录（管理端）失败",
            detail
        );
        if (log.isDebugEnabled()) log.debug("Auth failure user={} error={}", username, message);
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        recordAuthEventV2(
            evt.getAuthentication(),
            username,
            ButtonCodes.AUTH_ADMIN_LOGOUT,
            AuditResultStatus.SUCCESS,
            "系统退出（管理端）成功",
            Map.of()
        );
        clearLoginMarkers(evt.getAuthentication());
        if (log.isDebugEnabled()) log.debug("Logout success user={}", username);
    }

    private boolean shouldRecordLogin(Authentication authentication) {
        if (!markSessionFlag()) {
            return false;
        }
        boolean sessionRecorded = registerSessionMarker(authentication);
        if (!sessionRecorded && !registerPrincipalMarker(authentication)) {
            return false;
        }
        if (!throttleLoginDuplicates(authentication)) {
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

    private boolean throttleLoginDuplicates(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
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

    private void clearLoginMarkers(Authentication authentication) {
        String sessionId = extractSessionId(authentication);
        if (sessionId != null) {
            auditedSessions.remove(sessionMarkerKey(sessionId));
        }
        String principalMarker = principalMarkerKey(authentication);
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
        String username = authentication != null ? authentication.getName() : null;
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
        String userPart = StringUtils.isBlank(username) ? "unknown" : username.trim().toLowerCase();
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

    private boolean registerPrincipalMarker(Authentication authentication) {
        String marker = principalMarkerKey(authentication);
        if (marker == null) {
            return true;
        }
        return auditedSessions.putIfAbsent(marker, Boolean.TRUE) == null;
    }

    private String principalMarkerKey(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "anonymous".equals(normalized) || "anonymoususer".equals(normalized)) {
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
                .actorName(username)
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
