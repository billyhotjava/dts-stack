package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.common.audit.AuditPayloadSanitizer;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.EnqueueCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AuditForwarderService {

    private static final Logger log = LoggerFactory.getLogger(AuditForwarderService.class);
    private static final String SOURCE_SYSTEM_PLATFORM = "platform";
    private static final String PRODUCER = "dts-platform";
    private static final String AUTH_LOGIN_ACTION = "AUTH LOGIN";
    private static final String AUTH_LOGOUT_ACTION = "AUTH LOGOUT";
    private static final String PORTAL_USER_RESOURCE = "portal_user";
    private static final String PLATFORM_AUTH_LOGIN_BUTTON_CODE = "ADMIN_AUTH_PLATFORM_LOGIN";
    private static final String PLATFORM_AUTH_LOGOUT_BUTTON_CODE = "ADMIN_AUTH_PLATFORM_LOGOUT";
    private static final Duration READ_DEDUPE_WINDOW = Duration.ofSeconds(2);
    private static final int READ_DEDUPE_MAX_SIZE = 2048;
    private static final int MACHINE_EVENT_IDENTITY_MAX_LENGTH = 200;
    private static final Pattern MACHINE_EVENT_IDENTITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/+@-]{0,199}");

    public static final class PendingAuditEvent {
        public String eventId;
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
    private final PlatformAuditOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final AuditTenantResolver tenantResolver;
    private final ConcurrentHashMap<String, Long> recentReadEvents = new ConcurrentHashMap<>();

    public AuditForwarderService(
        AuditProperties properties,
        PlatformAuditOutboxRepository outbox,
        ObjectMapper objectMapper,
        AuditTenantResolver tenantResolver
    ) {
        this.properties = properties;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tenantResolver = tenantResolver;
    }

    public void record(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String outcome,
        Object payload
    ) {
        if (!StringUtils.hasText(actor) || isAnonymous(actor)) return;
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
        if (!properties.isEnabled()) return;
        if (event == null || !StringUtils.hasText(event.actor) || isAnonymous(event.actor)) return;
        persist(event, true);
    }

    /**
     * Persists compliance-sensitive audit evidence or fails the caller transaction.
     * This path deliberately bypasses read deduplication and never silently skips an event.
     */
    public UUID recordStrict(PendingAuditEvent event) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Audit recording is disabled");
        }
        if (event == null || !StringUtils.hasText(event.actor) || isAnonymous(event.actor)) {
            throw new IllegalStateException("Authenticated audit actor is required");
        }
        if (event.auxiliary) {
            throw new IllegalArgumentException("Strict audit events cannot be auxiliary");
        }
        UUID receiptId = persist(event, false, true);
        if (receiptId == null) {
            throw new IllegalStateException("Strict audit event was not persisted");
        }
        return receiptId;
    }

    void recordTrustedMachine(PendingAuditEvent event, String eventIdentity) {
        prepareTrustedMachineEvent(event, eventIdentity);
        if (!properties.isEnabled()) return;
        persist(event, false);
    }

    UUID recordTrustedMachineStrict(PendingAuditEvent event, String eventIdentity) {
        prepareTrustedMachineEvent(event, eventIdentity);
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Audit recording is disabled");
        }
        UUID receiptId = persist(event, false, true);
        if (receiptId == null) {
            throw new IllegalStateException("Strict machine audit event was not persisted");
        }
        return receiptId;
    }

    private void prepareTrustedMachineEvent(PendingAuditEvent event, String eventIdentity) {
        if (event == null) throw new IllegalArgumentException("event is required");
        String actor = normalizeTrustedMachineActor(event.actor);
        String identity = requireMachineEventIdentity(eventIdentity);
        if (event.occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required for machine audit events");
        }
        event.actor = actor;
        event.eventId = deterministicMachineEventId(actor, identity);
    }

    private UUID persist(PendingAuditEvent event, boolean applyReadDedupe) {
        return persist(event, applyReadDedupe, false);
    }

    private UUID persist(PendingAuditEvent event, boolean applyReadDedupe, boolean strict) {
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
            return null;
        }
        if (applyReadDedupe && shouldSkipByDedupe(event)) {
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
            return null;
        }
        Instant occurredAt = event.occurredAt != null ? event.occurredAt : Instant.now();
        String eventId = StringUtils.hasText(event.eventId) ? event.eventId.trim() : UUID.randomUUID().toString();
        Map<String, Object> body = AuditPayloadSanitizer.sanitize(toRequestBody(event, eventId, occurredAt));
        String bodyJson = serializeCanonical(body);
        EnqueueCommand command = new EnqueueCommand(
            tenantResolver.currentTenantId(), eventId, occurredAt, sha256(bodyJson), bodyJson
        );
        if (strict && TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return outbox.enqueueTransactional(command);
        }
        return outbox.enqueue(command);
    }

    static String normalizeTrustedMachineActor(String actor) {
        if (!StringUtils.hasText(actor)) {
            throw new IllegalArgumentException("machineActor is required");
        }
        return switch (actor.trim().toLowerCase(Locale.ROOT)) {
            case "airflow", "dts-airflow", "service:dts-airflow", "_system:airflow" -> "_system:airflow";
            case "scheduler", "dts-scheduler", "service:dts-scheduler", "_system:scheduler" -> "_system:scheduler";
            case "ingestion", "dts-ingestion", "service:dts-ingestion", "_system:ingestion" -> "_system:ingestion";
            default -> throw new IllegalArgumentException("machineActor is not trusted");
        };
    }

    static String requireMachineEventIdentity(String eventIdentity) {
        if (!StringUtils.hasText(eventIdentity)) {
            throw new IllegalArgumentException("eventIdentity is required");
        }
        String identity = eventIdentity.trim();
        if (identity.length() > MACHINE_EVENT_IDENTITY_MAX_LENGTH || !MACHINE_EVENT_IDENTITY.matcher(identity).matches()) {
            throw new IllegalArgumentException("eventIdentity has an invalid format");
        }
        return identity;
    }

    private static String deterministicMachineEventId(String actor, String eventIdentity) {
        String identity = PRODUCER + "|machine-audit-v1|" + actor + '|' + eventIdentity;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, Object> toRequestBody(PendingAuditEvent event, String eventId, Instant occurredAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("producer", PRODUCER);
        body.put("sourceSystem", SOURCE_SYSTEM_PLATFORM);
        body.put("occurredAt", occurredAt.toString());
        body.put("actor", event.actor);
        if (StringUtils.hasText(event.actorRole)) body.put("actorRole", event.actorRole);
        if (StringUtils.hasText(event.actorName)) body.put("actorName", event.actorName);
        body.put("module", defaultString(event.module, "GENERAL"));
        body.put("action", defaultString(event.action, "UNKNOWN"));
        if (StringUtils.hasText(event.summary)) body.put("summary", event.summary);
        if (StringUtils.hasText(event.operationType)) body.put("operationType", event.operationType);
        if (StringUtils.hasText(event.resourceType)) body.put("resourceType", event.resourceType);
        if (StringUtils.hasText(event.resourceId)) {
            body.put("resourceId", event.resourceId);
            body.put("targetIds", List.of(event.resourceId));
        }
        body.put("targetTable", defaultString(event.resourceType, null));
        if (StringUtils.hasText(event.resourceName)) body.put("resourceName", event.resourceName);
        body.put("result", defaultString(event.result, "SUCCESS"));
        if (StringUtils.hasText(event.operationType)) body.put("operationTypeCode", event.operationType);
        if (event.latencyMs != null) body.put("latencyMs", event.latencyMs);
        if (StringUtils.hasText(event.clientIp)) body.put("clientIp", event.clientIp);
        if (StringUtils.hasText(event.clientAgent)) body.put("clientAgent", event.clientAgent);
        if (StringUtils.hasText(event.requestUri)) body.put("requestUri", event.requestUri);
        if (StringUtils.hasText(event.httpMethod)) body.put("httpMethod", event.httpMethod);
        if (event.payload != null) body.put("payload", event.payload);
        if (event.attributes != null && !event.attributes.isEmpty()) body.put("attributes", event.attributes);
        Map<String, Object> metadata = prepareMetadata(event);
        if (!metadata.isEmpty()) body.put("metadata", metadata);
        String buttonCode = resolveButtonCode(event, metadata);
        if (StringUtils.hasText(buttonCode)) {
            body.put("buttonCode", buttonCode);
            body.putIfAbsent("operationCode", buttonCode);
        }
        String moduleKey = textValue(metadata.get("moduleKey"));
        if (StringUtils.hasText(moduleKey)) body.put("moduleKey", moduleKey);
        String moduleTitle = textValue(metadata.get("moduleTitle"));
        if (StringUtils.hasText(moduleTitle)) body.put("moduleName", moduleTitle);
        String entryKey = textValue(metadata.get("entryKey"));
        if (StringUtils.hasText(entryKey)) {
            body.put("targetTable", entryKey);
            body.putIfAbsent("resourceType", entryKey);
        }
        String entryTitle = textValue(metadata.get("entryTitle"));
        if (StringUtils.hasText(entryTitle)) body.putIfAbsent("resourceName", entryTitle);
        String stage = textValue(metadata.get("stage"));
        if (StringUtils.hasText(stage)) body.put("stage", stage);
        if (StringUtils.hasText(event.extraTags)) body.put("extraTags", event.extraTags);
        return body;
    }

    private String serializeCanonical(Map<String, Object> body) {
        try {
            return objectMapper
                .writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Audit event could not be serialized", failure);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private String resolveButtonCode(PendingAuditEvent event, Map<String, Object> metadata) {
        String buttonCode = textValue(metadata.get("actionCode"));
        if (!StringUtils.hasText(buttonCode)) buttonCode = textValue(metadata.get("buttonCode"));
        return StringUtils.hasText(buttonCode) ? buttonCode : legacyPlatformAuthButtonCode(event);
    }

    private String legacyPlatformAuthButtonCode(PendingAuditEvent event) {
        if (event == null || !isPortalAuthEvent(event)) return null;
        String action = normalizeAction(event.action);
        if (AUTH_LOGIN_ACTION.equals(action)) return PLATFORM_AUTH_LOGIN_BUTTON_CODE;
        if (AUTH_LOGOUT_ACTION.equals(action)) return PLATFORM_AUTH_LOGOUT_BUTTON_CODE;
        return null;
    }

    private boolean isPortalAuthEvent(PendingAuditEvent event) {
        return PORTAL_USER_RESOURCE.equalsIgnoreCase(defaultString(event.resourceType, "")) ||
            SOURCE_SYSTEM_PLATFORM.equalsIgnoreCase(defaultString(event.module, ""));
    }

    private String normalizeAction(String raw) {
        return StringUtils.hasText(raw) ? raw.trim().replace('_', ' ').toUpperCase(Locale.ROOT) : "";
    }

    private boolean shouldSkipByDedupe(PendingAuditEvent event) {
        if (event == null || READ_DEDUPE_WINDOW.isZero() || READ_DEDUPE_WINDOW.isNegative() || !isReadLike(event)) {
            return false;
        }
        String actor = defaultString(event.actor, "");
        if (!StringUtils.hasText(actor)) return false;
        String resourceKey = firstNonBlank(event.resourceId, event.requestUri, event.action);
        if (!StringUtils.hasText(resourceKey)) return false;
        String key = actor + '|' + defaultString(event.module, "") + '|' + defaultString(event.action, "") + '|' + resourceKey;
        long now = System.currentTimeMillis();
        Long previous = recentReadEvents.put(key, now);
        if (previous != null && (now - previous) <= READ_DEDUPE_WINDOW.toMillis()) return true;
        if (recentReadEvents.size() > READ_DEDUPE_MAX_SIZE) pruneDedupeCache(now);
        return false;
    }

    private boolean isReadLike(PendingAuditEvent event) {
        if (event == null) return false;
        if (StringUtils.hasText(event.httpMethod) && "GET".equalsIgnoreCase(event.httpMethod)) return true;
        if (StringUtils.hasText(event.operationType) && "READ".equalsIgnoreCase(event.operationType)) return true;
        if (!StringUtils.hasText(event.action)) return false;
        String lower = event.action.toLowerCase(Locale.ROOT);
        return lower.contains("查看") ||
            lower.contains("查询") ||
            lower.contains("预览") ||
            lower.contains("download") ||
            lower.contains("导出") ||
            lower.contains("read") ||
            lower.contains("list") ||
            lower.contains("preview");
    }

    private void pruneDedupeCache(long now) {
        long threshold = now - READ_DEDUPE_WINDOW.toMillis();
        recentReadEvents.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }

    private boolean isAnonymous(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isEmpty()) return true;
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
        if (event == null || event.metadata == null || event.metadata.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        event.metadata.forEach((key, value) -> {
            if (key != null && value != null) copy.put(String.valueOf(key), value);
        });
        return copy;
    }

    private String textValue(Object raw) {
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (StringUtils.hasText(value)) return value;
        return null;
    }
}
