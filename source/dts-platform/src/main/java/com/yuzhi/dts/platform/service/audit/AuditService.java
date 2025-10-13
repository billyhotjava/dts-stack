package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final ObjectProvider<AuditTrailService> auditTrailServiceProvider;
    private final AuditActionCatalog actionCatalog;
    private final ObjectMapper objectMapper;

    public AuditService(
        ObjectProvider<AuditTrailService> auditTrailServiceProvider,
        AuditActionCatalog actionCatalog,
        ObjectMapper objectMapper
    ) {
        this.auditTrailServiceProvider = auditTrailServiceProvider;
        this.actionCatalog = actionCatalog;
        this.objectMapper = objectMapper;
    }

    public void auditAction(String actionCode, AuditStage stage, String resourceId, Object payload) {
        if (!StringUtils.hasText(actionCode)) {
            log.warn("auditAction invoked without action code; falling back to legacy audit");
            audit(actionCode, "general", resourceId);
            return;
        }
        AuditStage effectiveStage = stage == null ? AuditStage.SUCCESS : stage;
        AuditActionDefinition definition = actionCatalog
            .findByCode(actionCode)
            .orElseGet(() -> {
                log.warn("Unknown audit action code {}, using fallback metadata", actionCode);
                return new AuditActionDefinition(
                    actionCode.trim().toUpperCase(),
                    actionCode,
                    "general",
                    "General",
                    "general",
                    "通用动作",
                    false,
                    null
                );
            });
        if (!definition.isStageSupported(effectiveStage)) {
            log.debug(
                "Audit action {} does not declare stage {}; proceeding for backward compatibility",
                definition.getCode(),
                effectiveStage
            );
        }

        String module = definition.getModuleKey();
        String actionDisplay = definition.getDisplay();
        String resourceType = definition.getEntryKey();
        String result = switch (effectiveStage) {
            case BEGIN -> "PENDING";
            case SUCCESS -> "SUCCESS";
            case FAIL -> "FAILED";
        };

        Map<String, Object> tags = new HashMap<>();
        tags.put("actionCode", definition.getCode());
        tags.put("stage", effectiveStage.name());
        tags.put("moduleKey", definition.getModuleKey());
        tags.put("moduleTitle", definition.getModuleTitle());
        tags.put("entryKey", definition.getEntryKey());
        tags.put("entryTitle", definition.getEntryTitle());
        tags.put("supportsFlow", definition.isSupportsFlow());

        record(actionDisplay, module, resourceType, resourceId, result, payload, tags);
    }

    public void audit(String action, String targetKind, String targetRef) {
        record(action, targetKind, targetKind, targetRef, "SUCCESS", null, null);
    }

    public void auditFailure(String action, String targetKind, String targetRef, Object payload) {
        record(action, targetKind, targetKind, targetRef, "FAILED", payload, null);
    }

    public void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        record(action, module, resourceType, resourceId, result, payload, null);
    }

    public void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
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
        event.extraTags = serializeTags(extraTags);
        // Best-effort populate client/network fields from current request
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                String xfip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : null;
                String realIp = req.getHeader("X-Real-IP");
                String remote = req.getRemoteAddr();
                event.clientIp = firstNonBlank(xfip, realIp, remote, "127.0.0.1");
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
            }
        } catch (Exception ignore) {}
        com.yuzhi.dts.platform.service.audit.AuditRequestContext.markDomainAudit();
        AuditTrailService svc = auditTrailServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.record(event);
        } else if (log.isDebugEnabled()) {
            log.debug("AuditTrailService not available; skipping audit record action={} module={} resourceId={}", action, module, resourceId);
        }
    }

    // Explicit-actor variant used for events occurring before SecurityContext is populated (e.g., login)
    public void recordAs(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        if (!StringUtils.hasText(actor)) {
            actor = "anonymous";
        }
        if (log.isInfoEnabled()) {
            log.info(
                "AUDIT actor={} action={} module={} resourceType={} resourceId={} result={}",
                actor, action, module, resourceType, resourceId, result
            );
        }
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
        event.extraTags = serializeTags(extraTags);
        // Best-effort populate client/network fields from current request
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                String xfip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : null;
                String realIp = req.getHeader("X-Real-IP");
                String remote = req.getRemoteAddr();
                event.clientIp = firstNonBlank(xfip, realIp, remote, "127.0.0.1");
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
            }
        } catch (Exception ignore) {}
        com.yuzhi.dts.platform.service.audit.AuditRequestContext.markDomainAudit();
        AuditTrailService svc = auditTrailServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.record(event);
        } else if (log.isDebugEnabled()) {
            log.debug("AuditTrailService not available; skipping audit record action={} module={} resourceId={}", action, module, resourceId);
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private String serializeTags(Map<String, Object> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit extra tags", ex);
            return null;
        }
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
