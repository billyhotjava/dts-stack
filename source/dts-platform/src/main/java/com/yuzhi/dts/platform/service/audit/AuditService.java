package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.common.net.IpAddressUtils;
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


    private final ObjectProvider<AuditTrailService> auditTrailServiceProvider;
    private final AuditActionCatalog actionCatalog;
    private final PortalSessionRegistry portalSessionRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, LegacyActionMapping> legacyActions;

    public AuditService(
        ObjectProvider<AuditTrailService> auditTrailServiceProvider,
        AuditActionCatalog actionCatalog,
        PortalSessionRegistry portalSessionRegistry,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        @Value("${auditing.legacy-actions.config-location:" + DEFAULT_LEGACY_ACTIONS_LOCATION + "}") String legacyActionsLocation
    ) {
        this.auditTrailServiceProvider = auditTrailServiceProvider;
        this.actionCatalog = actionCatalog;
        this.portalSessionRegistry = portalSessionRegistry;
        this.objectMapper = objectMapper;
        this.legacyActions = loadLegacyActions(resourceLoader, objectMapper, legacyActionsLocation);
        log.info("Loaded {} legacy audit action mappings from {}", legacyActions.size(), legacyActionsLocation);
    }

    private static Map<String, LegacyActionMapping> loadLegacyActions(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        String location
    ) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("legacy-action-mappings config not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
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

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        Object payload
    ) {
        recordAuxiliary(action, module, resourceType, resourceId, "SUCCESS", payload, null);
    }

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        recordAuxiliary(action, module, resourceType, resourceId, result, payload, null);
    }

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAuditInternal(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action,
            module,
            resourceType,
            resourceId,
            result,
            payload,
            extraTags,
            true
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
        AuditTrailService.PendingAuditEvent event = new AuditTrailService.PendingAuditEvent();
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
            event.operationType = deriveOperationType(event.action, payloadMap);
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
                event.clientIp = resolveClientIp(req);
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
                enrichWithPkiContext(req, payloadMap, effectiveExtraTags);
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

    private String deriveOperationType(String action, Map<String, Object> payload) {
        String direct = extractText(payload, "operationType");
        if (!StringUtils.hasText(direct)) {
            direct = extractText(payload, "operation_type");
        }
        if (StringUtils.hasText(direct)) {
            return canonicalOperationType(direct);
        }
        if (StringUtils.hasText(action)) {
            return canonicalOperationType(action);
        }
        String summary = extractText(payload, "summary");
        if (StringUtils.hasText(summary)) {
            return canonicalOperationType(summary);
        }
        return "READ";
    }

    private String canonicalOperationType(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "READ";
        }
        String trimmed = candidate.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (upper.contains("LOGIN") || containsAny(lower, "登录", "登入")) {
            return "LOGIN";
        }
        if (upper.contains("LOGOUT") || containsAny(lower, "登出", "退出登录", "注销登录")) {
            return "LOGOUT";
        }
        if (upper.contains("DOWNLOAD") || containsAny(lower, "下载", "download")) {
            return "DOWNLOAD";
        }
        if (upper.contains("UPLOAD") || containsAny(lower, "上传", "upload")) {
            return "UPLOAD";
        }
        if (upper.contains("EXPORT") || containsAny(lower, "导出", "export")) {
            return "EXPORT";
        }
        if (upper.contains("IMPORT") || containsAny(lower, "导入", "import")) {
            return "IMPORT";
        }
        if (upper.contains("GRANT") || containsAny(lower, "授权", "共享", "grant")) {
            return "GRANT";
        }
        if (upper.contains("REVOKE") || containsAny(lower, "撤销授权", "取消授权", "收回", "回收", "revoke")) {
            return "REVOKE";
        }
        if (upper.contains("ENABLE") || containsAny(lower, "启用", "开启", "激活", "enable")) {
            return "ENABLE";
        }
        if (upper.contains("DISABLE") || containsAny(lower, "禁用", "停用", "关闭", "失效", "disable")) {
            return "DISABLE";
        }
        if (
            upper.contains("CLEAN") ||
            upper.contains("PURGE") ||
            containsAny(lower, "清理", "清除", "清空", "清扫", "purge", "cleanup")
        ) {
            return "CLEAN";
        }
        if (upper.contains("ARCHIVE") || containsAny(lower, "归档", "封存", "archive")) {
            return "ARCHIVE";
        }
        if (upper.contains("PUBLISH") || containsAny(lower, "发布", "publish")) {
            return "PUBLISH";
        }
        if (upper.contains("APPROVE") || containsAny(lower, "批准", "审批通过")) {
            return "APPROVE";
        }
        if (upper.contains("REJECT") || containsAny(lower, "拒绝", "驳回")) {
            return "REJECT";
        }
        if (
            upper.contains("EXECUTE") ||
            upper.contains("RUN") ||
            containsAny(lower, "执行", "运行", "run", "apply")
        ) {
            return "EXECUTE";
        }
        if (upper.contains("REFRESH") || containsAny(lower, "刷新", "refresh")) {
            return "REFRESH";
        }
        if (upper.contains("TEST") || containsAny(lower, "测试", "校验", "验证", "test")) {
            return "TEST";
        }
        if (
            upper.contains("CREATE") ||
            upper.contains("ADD") ||
            upper.contains("NEW") ||
            containsAny(lower, "新增", "新建", "创建", "提交", "申请")
        ) {
            return "CREATE";
        }
        if (upper.contains("DELETE") || containsAny(lower, "删除", "移除", "下线", "注销")) {
            return "DELETE";
        }
        if (
            upper.contains("UPDATE") ||
            upper.contains("MODIFY") ||
            upper.contains("EDIT") ||
            upper.contains("SAVE") ||
            containsAny(lower, "修改", "更新", "调整", "保存", "编辑", "配置")
        ) {
            return "UPDATE";
        }
        if (
            upper.contains("READ") ||
            upper.contains("QUERY") ||
            upper.contains("GET") ||
            containsAny(lower, "查看", "查询", "预览", "浏览", "列表", "检索")
        ) {
            return "READ";
        }
        return "READ";
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

    private boolean containsAny(String source, String... needles) {
        if (!StringUtils.hasText(source) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && source.contains(needle)) {
                return true;
            }
        }
        return false;
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

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return IpAddressUtils.resolveClientIp(
            request.getHeader("X-Forwarded-For"),
            request.getHeader("X-Real-IP"),
            request.getRemoteAddr()
        );
    }

    private void enrichWithPkiContext(HttpServletRequest request, Map<String, Object> payload, Map<String, Object> extraTags) {
        if (request == null) {
            return;
        }
        try {
            com.yuzhi.dts.platform.service.security.pki.PkiClientCert cert =
                com.yuzhi.dts.platform.service.security.pki.PkiClientCert.fromRequest(request);
            if (!cert.present()) {
                return;
            }
            payload.putIfAbsent("pkiCertPresent", true);
            payload.putIfAbsent("pkiCertVerified", cert.verified());
            if (StringUtils.hasText(cert.serial())) {
                payload.putIfAbsent("pkiCertSerial", cert.serial());
            }
            if (StringUtils.hasText(cert.subjectDn())) {
                payload.putIfAbsent("pkiCertSubjectDn", cert.subjectDn());
            }
            if (StringUtils.hasText(cert.issuerDn())) {
                payload.putIfAbsent("pkiCertIssuerDn", cert.issuerDn());
            }
            if (cert.notAfter() != null) {
                payload.putIfAbsent("pkiCertNotAfter", cert.notAfter().toString());
            }
            if (extraTags != null) {
                extraTags.putIfAbsent("pkiCertVerified", cert.verified());
                if (StringUtils.hasText(cert.serial())) {
                    extraTags.putIfAbsent("pkiCertSerial", cert.serial());
                }
            }
        } catch (Exception ignored) {}
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
