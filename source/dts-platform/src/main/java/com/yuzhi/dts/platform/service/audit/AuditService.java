package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private static final boolean AUDIT_CONTEXT_PRESENT;
    private static final Class<?> AUDIT_CONTEXT_CLASS;

    static {
        boolean present;
        Class<?> ctxClass = null;
        try {
            ctxClass = Class.forName("com.yuzhi.dts.platform.service.audit.AuditRequestContext");
            present = true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            present = false;
        }
        AUDIT_CONTEXT_PRESENT = present;
        AUDIT_CONTEXT_CLASS = ctxClass;
    }

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
                event.clientIp = resolveClientIp(req);
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
            }
        } catch (Exception ignore) {}
        markDomainAuditSafe();
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
                event.clientIp = resolveClientIp(req);
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
            }
        } catch (Exception ignore) {}
        markDomainAuditSafe();
        AuditTrailService svc = auditTrailServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.record(event);
        } else if (log.isDebugEnabled()) {
            log.debug("AuditTrailService not available; skipping audit record action={} module={} resourceId={}", action, module, resourceId);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            for (String part : parts) {
                String candidate = sanitizeIpCandidate(part);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        String realIp = sanitizeIpCandidate(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            candidates.add(realIp);
        }
        String remote = sanitizeIpCandidate(request.getRemoteAddr());
        if (remote != null) {
            candidates.add(remote);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            if (isPublicAddress(candidate)) {
                return normalizeLoopback(candidate);
            }
        }
        for (String candidate : candidates) {
            if (!isLoopbackOrUnspecified(candidate)) {
                return normalizeLoopback(candidate);
            }
        }
        return normalizeLoopback(candidates.get(0));
    }

    private String sanitizeIpCandidate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeLoopback(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String value = ip.trim();
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return "127.0.0.1";
        }
        if (value.startsWith("::ffff:")) {
            return value.substring(7);
        }
        return value;
    }

    private boolean isLoopbackOrUnspecified(String ip) {
        InetAddress addr = tryParseInet(ip);
        if (addr == null) {
            return false;
        }
        return addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
    }

    private boolean isPublicAddress(String ip) {
        InetAddress addr = tryParseInet(ip);
        if (addr == null) {
            return false;
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) {
            return false;
        }
        if (addr.isSiteLocalAddress()) {
            return false;
        }
        if (addr instanceof Inet6Address inet6) {
            String lower = ip.toLowerCase(Locale.ROOT);
            if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80")) {
                return false;
            }
            if (inet6.isIPv4CompatibleAddress()) {
                return isPublicAddress(ipv4FromIpv6(inet6));
            }
        }
        return true;
    }

    private InetAddress tryParseInet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return InetAddress.getByName(raw.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private String ipv4FromIpv6(Inet6Address inet6) {
        byte[] addr = inet6.getAddress();
        return (addr[12] & 0xFF) + "." + (addr[13] & 0xFF) + "." + (addr[14] & 0xFF) + "." + (addr[15] & 0xFF);
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

    private void markDomainAuditSafe() {
        if (!AUDIT_CONTEXT_PRESENT) {
            return;
        }
        try {
            if (AUDIT_CONTEXT_CLASS != null) {
                AUDIT_CONTEXT_CLASS.getMethod("markDomainAudit").invoke(null);
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            // tolerate missing context helper at runtime
            if (log.isDebugEnabled()) {
                log.debug("AuditRequestContext not available: {}", ex.getMessage());
            }
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
