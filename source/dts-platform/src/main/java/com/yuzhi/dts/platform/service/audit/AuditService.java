package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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

    private static final String DEFAULT_LEGACY_ACTIONS_LOCATION = "classpath:config/legacy-action-mappings.json";
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
    private final Map<String, LegacyActionMapping> legacyActions;

    public AuditService(
        ObjectProvider<AuditForwarderService> auditForwarderServiceProvider,
        AuditActionCatalog actionCatalog,
        PortalSessionRegistry portalSessionRegistry,
        ObjectMapper objectMapper,
        OperationTypeNormalizer operationTypeNormalizer,
        PkiContextEnricher pkiContextEnricher,
        ResourceLoader resourceLoader,
        AuditDictionarySignatureGuard signatureGuard,
        @Value("${auditing.legacy-actions.config-location:" + DEFAULT_LEGACY_ACTIONS_LOCATION + "}") String legacyActionsLocation
    ) {
        this.auditForwarderServiceProvider = auditForwarderServiceProvider;
        this.actionCatalog = actionCatalog;
        this.portalSessionRegistry = portalSessionRegistry;
        this.objectMapper = objectMapper;
        this.operationTypeNormalizer = operationTypeNormalizer;
        this.pkiContextEnricher = pkiContextEnricher;
        this.legacyActions = loadLegacyActions(resourceLoader, objectMapper, signatureGuard, legacyActionsLocation);
        log.info("Loaded {} legacy audit action mappings from {}", legacyActions.size(), legacyActionsLocation);
    }

    private static Map<String, LegacyActionMapping> loadLegacyActions(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        AuditDictionarySignatureGuard signatureGuard,
        String location
    ) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("legacy-action-mappings config not found: " + location);
        }
        byte[] content;
        try (InputStream in = resource.getInputStream()) {
            content = in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load legacy-action-mappings from " + location, ex);
        }
        signatureGuard.verify(location, content);
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            LegacyActionsFile file = objectMapper.readValue(in, LegacyActionsFile.class);
            List<LegacyActionEntry> entries = file.entries();
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("legacy-action-mappings file is empty: " + location);
            }
            Map<String, LegacyActionMapping> map = new LinkedHashMap<>();
            for (LegacyActionEntry entry : entries) {
                AuditStage stage;
                try {
                    stage = AuditStage.valueOf(entry.defaultStage());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException(
                        "Unknown defaultStage '" + entry.defaultStage() + "' for module=" + entry.module() + " action=" + entry.action(),
                        ex
                    );
                }
                LegacyActionMapping mapping = new LegacyActionMapping(
                    entry.actionCode(),
                    entry.successSummary(),
                    entry.failureSummary(),
                    entry.pendingSummary(),
                    entry.operationType(),
                    entry.allowEmptyTargets(),
                    stage
                );
                map.put(legacyKey(entry.module(), entry.action()), mapping);
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load legacy-action-mappings from " + location, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LegacyActionsFile(String version, String description, List<LegacyActionEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LegacyActionEntry(
        String module,
        String action,
        String actionCode,
        String successSummary,
        String failureSummary,
        String pendingSummary,
        String operationType,
        boolean allowEmptyTargets,
        String defaultStage
    ) {}

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
     * Legacy 6-arg form. Use {@link #auditAction(String, AuditStage, String, Object)} with a
     * canonical {@code actionCode} from {@code audit-action-catalog.json} for new call sites —
     * this overload performs free-form action-string matching against legacy-action-mappings,
     * which is fuzzier and harder to evolve. Still ~30 call sites in 2026-04 (RE-4 follow-up).
     */
    @Deprecated
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

    /**
     * Failure-channel shortcut. Use {@link #auditAction(String, AuditStage, String, Object)} with
     * {@code AuditStage.FAIL} for new call sites.
     */
    @Deprecated
    public void auditFailure(String action, String targetKind, String targetRef, Object payload) {
        record(action, targetKind, targetKind, targetRef, "FAILED", payload, null);
    }

    /**
     * Internal writer used by {@link #auditAction(String, AuditStage, String, Object)} and
     * the deprecated {@link #record(String, String, String, String, String, Object)} overload.
     * Not part of the public API — outside callers should use {@code auditAction}.
     */
    public void record(
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

    /**
     * Auxiliary-channel writer: marks the entry as {@code auxiliary=true} so the forwarder
     * skips pushing it to admin. Used by lightweight UX-tracing events (dashboard open,
     * report click) that platform wants in its own telemetry but not in central governance
     * audit. New code should prefer {@link #auditAction(String, AuditStage, String, Object)}
     * when the event is real audit material; this overload is kept for the legacy 4 UX-trace
     * call sites.
     */
    @Deprecated
    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        Object payload
    ) {
        submitAuditInternal(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action, module, resourceType, resourceId,
            "SUCCESS", payload, null, true
        );
    }

    /** @see #recordAuxiliary(String, String, String, String, Object) */
    @Deprecated
    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        submitAuditInternal(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action, module, resourceType, resourceId,
            result, payload, null, true
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
        LegacyActionMapping legacyMapping = null;
        AuditActionDefinition legacyDefinition = null;
        String overrideAction = null;
        String overrideOperationType = null;
        boolean disableDefaultResourceFallback = auxiliary;
        AuditStage stage = resolveStageFromResult(result, null);

        if (!auxiliary && (effectiveExtraTags == null || !effectiveExtraTags.containsKey("actionCode"))) {
            legacyMapping = legacyActions.get(legacyKey(module, action));
            if (legacyMapping != null) {
                stage = resolveStageFromResult(result, legacyMapping.defaultStage());
                legacyDefinition = actionCatalog.findByCode(legacyMapping.actionCode()).orElse(null);
                overrideAction = legacyMapping.summaryForStage(stage);
                overrideOperationType = legacyMapping.operationType();
                if (legacyMapping.allowEmptyTargets()) {
                    disableDefaultResourceFallback = true;
                }
                if (legacyDefinition != null) {
                    module = legacyDefinition.getModuleKey();
                    if (StringUtils.hasText(legacyDefinition.getEntryKey())) {
                        resourceType = legacyDefinition.getEntryKey();
                    }
                    effectiveExtraTags = new java.util.LinkedHashMap<>();
                    effectiveExtraTags.put("actionCode", legacyDefinition.getCode());
                    effectiveExtraTags.put("moduleKey", legacyDefinition.getModuleKey());
                    effectiveExtraTags.put("moduleTitle", legacyDefinition.getModuleTitle());
                    effectiveExtraTags.put("entryKey", legacyDefinition.getEntryKey());
                    effectiveExtraTags.put("entryTitle", legacyDefinition.getEntryTitle());
                    effectiveExtraTags.put("supportsFlow", legacyDefinition.isSupportsFlow());
                    effectiveExtraTags.put("stage", stage.name());
                }
                if (StringUtils.hasText(overrideAction) && !payloadMap.containsKey("summary")) {
                    payloadMap.put("summary", overrideAction);
                }
                if (StringUtils.hasText(overrideOperationType) && !payloadMap.containsKey("operationType")) {
                    payloadMap.put("operationType", overrideOperationType);
                }
            }
        }

        result = normalizeResultForStage(stage, result);
        String logAction = StringUtils.hasText(overrideAction) ? overrideAction : action;
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
        event.action = StringUtils.hasText(overrideAction)
            ? overrideAction
            : (StringUtils.hasText(action) ? action : "READ");
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
        if (StringUtils.hasText(overrideOperationType)) {
            event.operationType = overrideOperationType;
        } else {
            event.operationType = operationTypeNormalizer.deriveOperationType(event.action, payloadMap);
        }

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

    private static String legacyKey(String module, String action) {
        String normalizedModule = StringUtils.hasText(module) ? module.trim() : "";
        String normalizedAction = StringUtils.hasText(action) ? action.trim() : "";
        return normalizedModule + ":" + normalizedAction;
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

    private static final class LegacyActionMapping {
        private final String actionCode;
        private final String successSummary;
        private final String failureSummary;
        private final String pendingSummary;
        private final String operationType;
        private final boolean allowEmptyTargets;
        private final AuditStage defaultStage;

        LegacyActionMapping(
            String actionCode,
            String successSummary,
            String failureSummary,
            String pendingSummary,
            String operationType,
            boolean allowEmptyTargets,
            AuditStage defaultStage
        ) {
            this.actionCode = actionCode;
            this.successSummary = successSummary;
            this.failureSummary = failureSummary;
            this.pendingSummary = pendingSummary;
            this.operationType = operationType;
            this.allowEmptyTargets = allowEmptyTargets;
            this.defaultStage = defaultStage;
        }

        String actionCode() {
            return actionCode;
        }

        boolean allowEmptyTargets() {
            return allowEmptyTargets;
        }

        AuditStage defaultStage() {
            return defaultStage;
        }

        String operationType() {
            return operationType;
        }

        String summaryForStage(AuditStage stage) {
            if (stage == AuditStage.FAIL) {
                if (StringUtils.hasText(failureSummary)) {
                    return failureSummary;
                }
                if (StringUtils.hasText(successSummary) && !successSummary.endsWith("失败")) {
                    return successSummary + "失败";
                }
            }
            if (stage == AuditStage.BEGIN && StringUtils.hasText(pendingSummary)) {
                return pendingSummary;
            }
            return successSummary;
        }
    }
}
