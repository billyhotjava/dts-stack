package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditForwarderService {

    private static final Logger log = LoggerFactory.getLogger(AuditForwarderService.class);
    private static final String SOURCE_SYSTEM_PLATFORM = "platform";
    private static final String AUTH_LOGIN_ACTION = "AUTH LOGIN";
    private static final String AUTH_LOGOUT_ACTION = "AUTH LOGOUT";
    private static final String PORTAL_USER_RESOURCE = "portal_user";
    private static final String PLATFORM_AUTH_LOGIN_BUTTON_CODE = "ADMIN_AUTH_PLATFORM_LOGIN";
    private static final String PLATFORM_AUTH_LOGOUT_BUTTON_CODE = "ADMIN_AUTH_PLATFORM_LOGOUT";
    private static final Duration READ_DEDUPE_WINDOW = Duration.ofSeconds(2);
    private static final int READ_DEDUPE_MAX_SIZE = 2048;
    private static final int FAILOVER_QUEUE_MAX_SIZE = 10_000;
    private static final int RETRY_BATCH_SIZE = 100;

    public static final class PendingAuditEvent {
        public Instant occurredAt;
        public String actor;
        public String actorRole;
        public String actorName;
        public String module;
        public String action;
        public String summary;
        public String operationType;
        public String resourceType;
        public String resourceId;
        public String resourceName;
        public String clientIp;
        public String clientAgent;
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public Object payload;
        public Map<String, Object> attributes;
        public Map<String, Object> metadata;
        public String extraTags;
        public boolean disableDefaultResourceFallback;
        public boolean auxiliary;
    }

    private final AuditProperties properties;
    private final AdminAuditGateway adminAuditGateway;
    private final ConcurrentHashMap<String, Long> recentReadEvents = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> failedEventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedEventCount = new AtomicLong(0);

    public AuditForwarderService(AuditProperties properties, AdminAuditGateway adminAuditGateway) {
        this.properties = properties;
        this.adminAuditGateway = adminAuditGateway;
        if (!adminAuditGateway.isEnabled()) {
            log.warn("dts-admin audit gateway is disabled; platform audit forwarding will be disabled");
        }
    }

    public void record(String actor, String action, String module, String resourceType, String resourceId, String outcome, Object payload) {
        if (!StringUtils.hasText(actor) || isAnonymous(actor)) {
            return;
        }
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = actor;
        event.action = defaultString(action, "UNKNOWN");
        event.module = defaultString(module, "GENERAL");
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = defaultString(outcome, "SUCCESS");
        event.payload = payload;
        record(event);
    }

    public void record(String actor, String action, String module, String resourceId, String outcome, Object payload) {
        record(actor, action, module, module, resourceId, outcome, payload);
    }

    public void record(PendingAuditEvent event) {
        if (!properties.isEnabled() || !adminAuditGateway.isEnabled()) {
            return;
        }
        if (event == null || !StringUtils.hasText(event.actor) || isAnonymous(event.actor)) {
            return;
        }
        if (event.auxiliary) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Skipping auxiliary audit event action={} module={} resourceId={} uri={}",
                    event.action,
                    event.module,
                    event.resourceId,
                    event.requestUri
                );
            }
            return;
        }
        if (shouldSkipByDedupe(event)) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Deduplicated audit event within {}ms window actor={} action={} resourceId={} uri={}",
                    READ_DEDUPE_WINDOW.toMillis(),
                    event.actor,
                    event.action,
                    event.resourceId,
                    event.requestUri
                );
            }
            return;
        }
        Map<String, Object> body = toRequestBody(event);
        if (!forwardEvent(body)) {
            enqueueFailedEvent(body);
        }
    }

    private Map<String, Object> toRequestBody(PendingAuditEvent event) {
        Instant occurredAt = event.occurredAt != null ? event.occurredAt : Instant.now();
        Map<String, Object> body = new HashMap<>();
        body.put("sourceSystem", SOURCE_SYSTEM_PLATFORM);
        body.put("occurredAt", occurredAt.toString());
        body.put("actor", event.actor);
        if (StringUtils.hasText(event.actorRole)) {
            body.put("actorRole", event.actorRole);
        }
        if (StringUtils.hasText(event.actorName)) {
            body.put("actorName", event.actorName);
        }
        body.put("module", defaultString(event.module, "GENERAL"));
        body.put("action", defaultString(event.action, "UNKNOWN"));
        if (StringUtils.hasText(event.summary)) {
            body.put("summary", event.summary);
        }
        if (StringUtils.hasText(event.operationType)) {
            body.put("operationType", event.operationType);
        }
        if (StringUtils.hasText(event.resourceType)) {
            body.put("resourceType", event.resourceType);
        }
        if (StringUtils.hasText(event.resourceId)) {
            body.put("resourceId", event.resourceId);
            body.put("targetIds", List.of(event.resourceId));
        }
        body.put("targetTable", defaultString(event.resourceType, null));
        if (StringUtils.hasText(event.resourceName)) {
            body.put("resourceName", event.resourceName);
        }
        body.put("result", defaultString(event.result, "SUCCESS"));
        if (StringUtils.hasText(event.operationType)) {
            body.put("operationType", event.operationType);
            body.put("operationTypeCode", event.operationType);
        }
        if (event.latencyMs != null) {
            body.put("latencyMs", event.latencyMs);
        }
        if (StringUtils.hasText(event.clientIp)) {
            body.put("clientIp", event.clientIp);
        }
        if (StringUtils.hasText(event.clientAgent)) {
            body.put("clientAgent", event.clientAgent);
        }
        if (StringUtils.hasText(event.requestUri)) {
            body.put("requestUri", event.requestUri);
        }
        if (StringUtils.hasText(event.httpMethod)) {
            body.put("httpMethod", event.httpMethod);
        }
        if (event.payload != null) {
            body.put("payload", event.payload);
        }
        if (event.attributes != null && !event.attributes.isEmpty()) {
            body.put("attributes", event.attributes);
        }
        Map<String, Object> metadata = prepareMetadata(event);
        if (!metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        String buttonCode = resolveButtonCode(event, metadata);
        if (StringUtils.hasText(buttonCode)) {
            body.put("buttonCode", buttonCode);
            body.putIfAbsent("operationCode", buttonCode);
        }
        String moduleKey = textValue(metadata.get("moduleKey"));
        if (StringUtils.hasText(moduleKey)) {
            body.put("moduleKey", moduleKey);
        }
        String moduleTitle = textValue(metadata.get("moduleTitle"));
        if (StringUtils.hasText(moduleTitle)) {
            body.put("moduleName", moduleTitle);
        }
        String entryKey = textValue(metadata.get("entryKey"));
        if (StringUtils.hasText(entryKey)) {
            body.put("targetTable", entryKey);
            body.putIfAbsent("resourceType", entryKey);
        }
        String entryTitle = textValue(metadata.get("entryTitle"));
        if (StringUtils.hasText(entryTitle)) {
            body.putIfAbsent("resourceName", entryTitle);
        }
        String stage = textValue(metadata.get("stage"));
        if (StringUtils.hasText(stage)) {
            body.put("stage", stage);
        }
        if (StringUtils.hasText(event.extraTags)) {
            body.put("extraTags", event.extraTags);
        }
        return body;
    }

    private String resolveButtonCode(PendingAuditEvent event, Map<String, Object> metadata) {
        String buttonCode = textValue(metadata.get("actionCode"));
        if (!StringUtils.hasText(buttonCode)) {
            buttonCode = textValue(metadata.get("buttonCode"));
        }
        if (StringUtils.hasText(buttonCode)) {
            return buttonCode;
        }
        return legacyPlatformAuthButtonCode(event);
    }

    private String legacyPlatformAuthButtonCode(PendingAuditEvent event) {
        if (event == null || !isPortalAuthEvent(event)) {
            return null;
        }
        String action = normalizeAction(event.action);
        if (AUTH_LOGIN_ACTION.equals(action)) {
            return PLATFORM_AUTH_LOGIN_BUTTON_CODE;
        }
        if (AUTH_LOGOUT_ACTION.equals(action)) {
            return PLATFORM_AUTH_LOGOUT_BUTTON_CODE;
        }
        return null;
    }

    private boolean isPortalAuthEvent(PendingAuditEvent event) {
        return PORTAL_USER_RESOURCE.equalsIgnoreCase(defaultString(event.resourceType, "")) ||
            SOURCE_SYSTEM_PLATFORM.equalsIgnoreCase(defaultString(event.module, ""));
    }

    private String normalizeAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private boolean forwardEvent(Map<String, Object> body) {
        boolean submitted = adminAuditGateway.recordEvent(body);
        if (!submitted) {
            log.warn(
                "Failed to forward audit event action={} module={}",
                body.get("action"),
                body.get("module")
            );
        }
        return submitted;
    }

    private void enqueueFailedEvent(Map<String, Object> body) {
        while (failedEventQueue.size() >= FAILOVER_QUEUE_MAX_SIZE) {
            Map<String, Object> dropped = failedEventQueue.poll();
            if (dropped != null) {
                long total = droppedEventCount.incrementAndGet();
                if (total % 100 == 1) {
                    log.warn("Audit failover queue full (max={}), dropping oldest events. Total dropped so far: {}", FAILOVER_QUEUE_MAX_SIZE, total);
                }
            }
        }
        failedEventQueue.offer(body);
    }

    @Scheduled(fixedDelay = 30000)
    public void retryFailedEvents() {
        if (!adminAuditGateway.isEnabled()) {
            return;
        }
        int queueSize = failedEventQueue.size();
        if (queueSize == 0) {
            return;
        }

        int toProcess = Math.min(queueSize, RETRY_BATCH_SIZE);
        List<Map<String, Object>> batch = new ArrayList<>(toProcess);
        for (int i = 0; i < toProcess; i++) {
            Map<String, Object> event = failedEventQueue.poll();
            if (event == null) {
                break;
            }
            batch.add(event);
        }

        int succeeded = 0;
        for (Map<String, Object> body : batch) {
            if (forwardEvent(body)) {
                succeeded++;
                continue;
            }
            enqueueFailedEvent(body);
        }

        int pending = failedEventQueue.size();
        log.info("Retried {} audit events, {} succeeded, {} still pending", batch.size(), succeeded, pending);
    }

    private boolean shouldSkipByDedupe(PendingAuditEvent event) {
        if (event == null) {
            return false;
        }
        if (READ_DEDUPE_WINDOW.isZero() || READ_DEDUPE_WINDOW.isNegative()) {
            return false;
        }
        if (!isReadLike(event)) {
            return false;
        }
        String actor = defaultString(event.actor, "");
        if (!StringUtils.hasText(actor)) {
            return false;
        }
        String resourceKey = firstNonBlank(event.resourceId, event.requestUri, event.action);
        if (!StringUtils.hasText(resourceKey)) {
            return false;
        }
        String key = actor + '|' + defaultString(event.module, "") + '|' + defaultString(event.action, "") + '|' + resourceKey;
        long now = System.currentTimeMillis();
        Long previous = recentReadEvents.put(key, now);
        if (previous != null && (now - previous) <= READ_DEDUPE_WINDOW.toMillis()) {
            return true;
        }
        if (recentReadEvents.size() > READ_DEDUPE_MAX_SIZE) {
            pruneDedupeCache(now);
        }
        return false;
    }

    private boolean isReadLike(PendingAuditEvent event) {
        if (event == null) {
            return false;
        }
        if (StringUtils.hasText(event.httpMethod) && "GET".equalsIgnoreCase(event.httpMethod)) {
            return true;
        }
        if (StringUtils.hasText(event.operationType) && "READ".equalsIgnoreCase(event.operationType)) {
            return true;
        }
        if (StringUtils.hasText(event.action)) {
            String lower = event.action.toLowerCase(Locale.ROOT);
            if (
                lower.contains("查看") ||
                lower.contains("查询") ||
                lower.contains("预览") ||
                lower.contains("download") ||
                lower.contains("导出") ||
                lower.contains("read") ||
                lower.contains("list") ||
                lower.contains("preview")
            ) {
                return true;
            }
        }
        return false;
    }

    private void pruneDedupeCache(long now) {
        long threshold = now - READ_DEDUPE_WINDOW.toMillis();
        recentReadEvents.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }

    private boolean isAnonymous(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return "anonymous".equals(lower) ||
            "anonymoususer".equals(lower) ||
            "unknown".equals(lower) ||
            "system".equals(lower) ||
            "liquibase".equals(lower) ||
            "postgresql".equals(lower) ||
            "success".equals(lower) ||
            "failed".equals(lower) ||
            "execute".equals(lower) ||
            lower.startsWith("service:") ||
            lower.startsWith("_system:") ||
            lower.startsWith("dts-") ||
            lower.contains("liquibase") ||
            lower.chars().allMatch(Character::isDigit);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Map<String, Object> prepareMetadata(PendingAuditEvent event) {
        if (event == null || event.metadata == null || event.metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        event.metadata.forEach((k, v) -> {
            if (k != null && v != null) {
                copy.put(String.valueOf(k), v);
            }
        });
        return copy;
    }

    private String textValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
