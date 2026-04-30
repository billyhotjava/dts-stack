package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Set<String> ACTOR_HINT_KEYS = Set.of(
        "username",
        "user",
        "operator",
        "operatorName",
        "operatorId",
        "account",
        "principal",
        "actor",
        "login",
        "owner",
        "requester"
    );


    private final ObjectProvider<AuditForwarderService> auditForwarderServiceProvider;
    private final AuditActionCatalog actionCatalog;
    private final PortalSessionRegistry portalSessionRegistry;
    private final ObjectMapper objectMapper;
    private final OperationTypeNormalizer operationTypeNormalizer;
    private final PkiContextEnricher pkiContextEnricher;

    public AuditService(
        ObjectProvider<AuditForwarderService> auditForwarderServiceProvider,
        AuditActionCatalog actionCatalog,
        PortalSessionRegistry portalSessionRegistry,
        ObjectMapper objectMapper,
        OperationTypeNormalizer operationTypeNormalizer,
        PkiContextEnricher pkiContextEnricher
    ) {
        this.auditForwarderServiceProvider = auditForwarderServiceProvider;
        this.actionCatalog = actionCatalog;
        this.portalSessionRegistry = portalSessionRegistry;
        this.objectMapper = objectMapper;
        this.operationTypeNormalizer = operationTypeNormalizer;
        this.pkiContextEnricher = pkiContextEnricher;
    }

    public void auditAction(String actionCode, AuditStage stage, String resourceId, Object payload) {
        if (!StringUtils.hasText(actionCode)) {
            log.warn("auditAction invoked without action code; falling back to general audit");
            record(actionCode, "general", "general", resourceId, "SUCCESS", payload, null);
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

    /**
     * Internal writer used by {@link #auditAction(String, AuditStage, String, Object)}.
     * Package-private — outside callers must use {@code auditAction}.
     */
    void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAudit(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action,
            module,
            resourceType,
            resourceId,
            result,
            payload,
            extraTags
        );
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
        submitAuditInternal(actor, action, module, resourceType, resourceId, result, payload, extraTags, false);
    }

    private void submitAudit(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAuditInternal(actor, action, module, resourceType, resourceId, result, payload, extraTags, false);
    }

    private void submitAuditInternal(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags,
        boolean auxiliary
    ) {
        Map<String, Object> payloadMap = toPayloadMap(payload);
        String safeActor = resolveActor(actor, payloadMap);
        if (safeActor == null) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Skip audit record without resolved actor action={} module={} resourceId={}",
                    action,
                    module,
                    resourceId
                );
            }
            return;
        }
        Map<String, Object> effectiveExtraTags = extraTags != null ? new java.util.LinkedHashMap<>(extraTags) : null;
        boolean disableDefaultResourceFallback = auxiliary;
        AuditStage stage = resolveStageFromResult(result, null);

        result = normalizeResultForStage(stage, result);
        String logAction = action;
        log.info(
            "AUDIT actor={} action={} module={} resourceType={} resourceId={} result={}",
            safeActor,
            logAction,
            module,
            resourceType,
            resourceId,
            result
        );
        AuditForwarderService.PendingAuditEvent event = new AuditForwarderService.PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = safeActor;
        event.actorRole = resolvePrimaryAuthority();
        event.module = StringUtils.hasText(module) ? module : "general";
        event.action = StringUtils.hasText(action) ? action : "READ";
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = StringUtils.hasText(result) ? result : "SUCCESS";

        if (!payloadMap.isEmpty()) {
            event.payload = payloadMap;
        } else {
            event.payload = payload;
        }
        String actorDisplayName = resolveActorName(payloadMap, safeActor);
        if (StringUtils.hasText(actorDisplayName)) {
            payloadMap.putIfAbsent("actorName", actorDisplayName);
            event.actorName = actorDisplayName;
        }
        if (effectiveExtraTags != null && !effectiveExtraTags.isEmpty()) {
            event.extraTags = serializeTags(effectiveExtraTags);
        }

        String summary = extractText(payloadMap, "summary");
        String targetName = extractText(payloadMap, "targetName");
        if (StringUtils.hasText(targetName)) {
            event.resourceName = targetName;
        } else {
            String resourceName = extractText(payloadMap, "resourceName");
            if (StringUtils.hasText(resourceName)) {
                event.resourceName = resourceName;
            }
        }
        if (!StringUtils.hasText(summary)) {
            if (StringUtils.hasText(event.action) && StringUtils.hasText(event.resourceName)) {
                summary = event.action + "：" + event.resourceName;
            } else if (StringUtils.hasText(event.action)) {
                summary = event.action;
            }
        }
        event.summary = summary;
        event.operationType = operationTypeNormalizer.deriveOperationType(event.action, payloadMap);

        Map<String, Object> attributes = extractNestedAttributes(payloadMap);
        if (!attributes.isEmpty()) {
            event.attributes = attributes;
        }
        if (effectiveExtraTags != null && !effectiveExtraTags.isEmpty()) {
            event.metadata = new java.util.LinkedHashMap<>(effectiveExtraTags);
        }
        event.auxiliary = auxiliary;
        if (disableDefaultResourceFallback) {
            event.disableDefaultResourceFallback = true;
        }

        // Best-effort populate client/network fields from current request
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                event.clientIp = pkiContextEnricher.resolveClientIp(req);
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
                pkiContextEnricher.enrichWithPkiContext(req, payloadMap, effectiveExtraTags);
            }
        } catch (Exception ignore) {}
        markDomainAuditSafe();
        AuditForwarderService svc = auditForwarderServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.record(event);
        } else if (log.isDebugEnabled()) {
            log.debug("AuditForwarderService not available; skipping audit record action={} module={} resourceId={}", action, module, resourceId);
        }
    }

    private String resolveActor(String actor, Map<String, Object> payloadMap) {
        String primary = sanitizeActorString(actor);
        if (primary != null) {
            return primary;
        }
        String fromPayload = extractActorFromPayload(payloadMap);
        if (fromPayload != null) {
            return fromPayload;
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        String sanitizedLogin = sanitizeActorString(login);
        if (sanitizedLogin != null) {
            return sanitizedLogin;
        }
        return sanitizeActorString(SecurityUtils.getCurrentUserId().orElse(null));
    }

    private String extractActorFromPayload(Map<String, Object> payloadMap) {
        if (payloadMap == null || payloadMap.isEmpty()) {
            return null;
        }
        return extractActorFromPayloadInternal(
            payloadMap,
            Collections.newSetFromMap(new IdentityHashMap<>())
        );
    }

    private String extractActorFromPayloadInternal(Object source, Set<Object> visited) {
        if (source == null) {
            return null;
        }
        if (source instanceof String s) {
            return sanitizeActorString(s);
        }
        if (source instanceof Map<?, ?> map) {
            if (!visited.add(map)) {
                return null;
            }
            for (String key : ACTOR_HINT_KEYS) {
                if (map.containsKey(key)) {
                    String candidate = extractActorFromPayloadInternal(map.get(key), visited);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
            for (Object value : map.values()) {
                String candidate = extractActorFromPayloadInternal(value, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (source instanceof Collection<?> collection) {
            if (!visited.add(collection)) {
                return null;
            }
            for (Object value : collection) {
                String candidate = extractActorFromPayloadInternal(value, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            for (int i = 0; i < length; i++) {
                String candidate = extractActorFromPayloadInternal(Array.get(source, i), visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        return sanitizeActorString(source.toString());
    }

    private String sanitizeActorString(String candidate) {
        if (candidate == null) {
            return null;
        }
        String text = candidate.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("Bearer ")) {
            text = text.substring(7).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("anonymous") || lower.equals("anonymoususer") || lower.equals("unknown")) {
            return null;
        }
        return text;
    }

    private Map<String, Object> toPayloadMap(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return new java.util.LinkedHashMap<>();
    }

    private Map<String, Object> extractNestedAttributes(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return java.util.Collections.emptyMap();
    }

    private String extractText(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }



    private AuditStage resolveStageFromResult(String result, AuditStage defaultStage) {
        String normalized = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
        if ("FAILED".equals(normalized) || "FAIL".equals(normalized) || "ERROR".equals(normalized) || "DENY".equals(normalized) || "DENIED".equals(normalized)) {
            return AuditStage.FAIL;
        }
        if ("PENDING".equals(normalized) || "BEGIN".equals(normalized)) {
            return AuditStage.BEGIN;
        }
        if (defaultStage != null) {
            return defaultStage;
        }
        return AuditStage.SUCCESS;
    }

    private String normalizeResultForStage(AuditStage stage, String original) {
        String normalized = original == null ? "" : original.trim().toUpperCase(Locale.ROOT);
        return switch (stage) {
            case FAIL -> "FAILED";
            case BEGIN -> "PENDING";
            case SUCCESS -> {
                if (!StringUtils.hasText(normalized)) {
                    yield "SUCCESS";
                }
                if (isCanonicalResult(normalized)) {
                    yield normalized;
                }
                log.warn("Unrecognised audit result '{}' for stage SUCCESS — falling back to UNKNOWN", original);
                yield "UNKNOWN";
            }
        };
    }

    private static boolean isCanonicalResult(String normalized) {
        return switch (normalized) {
            case "SUCCESS", "SUCCEEDED", "OK", "PASS", "FAIL", "FAILED", "ERROR", "DENY", "DENIED",
                "PENDING", "PROCESSING", "IN_PROGRESS", "UNKNOWN" -> true;
            default -> false;
        };
    }


    private String resolveActorName(Map<String, Object> payload, String actorId) {
        String fromPayload = extractText(payload, "actorName");
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        String fromSecurity = SecurityUtils.getCurrentUserDisplayName().orElse(null);
        if (StringUtils.hasText(fromSecurity)) {
            return fromSecurity;
        }
        if (StringUtils.hasText(actorId)) {
            return portalSessionRegistry.resolveDisplayName(actorId).orElse(null);
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
