package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditTrailService auditTrailService;

    public AuditService(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    public void audit(String action, String targetKind, String targetRef) {
        record(action, targetKind, targetKind, targetRef, "SUCCESS", null);
    }

    public void auditFailure(String action, String targetKind, String targetRef, Object payload) {
        record(action, targetKind, targetKind, targetRef, "FAILURE", payload);
    }

    public void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        log.info(
            "AUDIT actor={} action={} module={} resourceType={} resourceId={} result={}",
            actor,
            action,
            module,
            resourceType,
            resourceId,
            result
        );
        AuditTrailService.PendingAuditEvent event = new AuditTrailService.PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = actor;
        event.actorRole = resolvePrimaryAuthority();
        event.module = module;
        event.action = action;
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = result;
        event.payload = payload;
        auditTrailService.record(event);
    }

    private String resolvePrimaryAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Optional<? extends GrantedAuthority> authority = authentication.getAuthorities().stream().findFirst();
        return authority.map(GrantedAuthority::getAuthority).orElse(null);
    }
}
