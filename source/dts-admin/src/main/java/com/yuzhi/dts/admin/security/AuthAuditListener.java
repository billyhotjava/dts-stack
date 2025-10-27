package com.yuzhi.dts.admin.security;

import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
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

    private final AdminAuditService auditService;
    private final ConcurrentMap<String, Boolean> auditedSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> recentLogins = new ConcurrentHashMap<>();

    public AuthAuditListener(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        if (!shouldRecordLogin(evt.getAuthentication())) {
            return;
        }
        auditService.recordAction(
            username,
            "ADMIN_AUTH_LOGIN",
            AuditStage.SUCCESS,
            username,
            Map.of("module", "admin.auth", "mode", resolveAuthMode(evt.getAuthentication()), "audience", "admin")
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
        auditService.recordAction(
            username,
            "ADMIN_AUTH_LOGIN",
            AuditStage.SUCCESS,
            username,
            Map.of("module", "admin.auth", "mode", resolveAuthMode(evt.getAuthentication()), "audience", "admin")
        );
        if (log.isDebugEnabled()) log.debug("Auth success (InteractiveAuthenticationSuccessEvent) user={}", username);
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        String message = evt.getException() != null && evt.getException().getMessage() != null ? evt.getException().getMessage() : "auth failed";
        auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, username, Map.of("error", message));
        if (log.isDebugEnabled()) log.debug("Auth failure user={} error={}", username, message);
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username) || "anonymoususer".equalsIgnoreCase(username)) return;
        auditService.recordAction(username, "ADMIN_AUTH_LOGOUT", AuditStage.SUCCESS, username, Map.of());
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
