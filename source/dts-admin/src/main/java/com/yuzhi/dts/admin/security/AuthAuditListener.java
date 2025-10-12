package com.yuzhi.dts.admin.security;

import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.common.audit.AuditStage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditListener {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditListener.class);

    private final AdminAuditService auditService;

    public AuthAuditListener(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        auditService.record(username, "ADMIN AUTH LOGIN", "admin", "admin_keycloak_user", username, "SUCCESS", Map.of());
        if (log.isDebugEnabled()) log.debug("Auth success (AuthenticationSuccessEvent) user={}", username);
    }

    @EventListener
    public void onInteractiveAuthSuccess(InteractiveAuthenticationSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        auditService.record(username, "ADMIN AUTH LOGIN", "admin", "admin_keycloak_user", username, "SUCCESS", Map.of());
        if (log.isDebugEnabled()) log.debug("Auth success (InteractiveAuthenticationSuccessEvent) user={}", username);
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        String message = evt.getException() != null && evt.getException().getMessage() != null ? evt.getException().getMessage() : "auth failed";
        auditService.record(username, "ADMIN AUTH LOGIN", "admin", "admin_keycloak_user", username, "FAILED", Map.of("error", message));
        if (log.isDebugEnabled()) log.debug("Auth failure user={} error={}", username, message);
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent evt) {
        String username = evt.getAuthentication() != null ? evt.getAuthentication().getName() : "unknown";
        auditService.record(username, "ADMIN AUTH LOGOUT", "admin", "admin_keycloak_user", username, "SUCCESS", Map.of());
        if (log.isDebugEnabled()) log.debug("Logout success user={}", username);
    }
}
