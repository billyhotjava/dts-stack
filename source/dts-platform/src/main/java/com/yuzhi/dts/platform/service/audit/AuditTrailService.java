package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import com.yuzhi.dts.platform.repository.audit.AuditEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@DependsOn({"entityManagerFactory", "liquibase"})
@Transactional
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    public static final class AuditEventView {
        public long id;
        public Instant occurredAt;
        public String actor;
        public String actorRole;
        public String module;
        public String action;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public String extraTags;
        public String payloadPreview;
    }

    public static final class PendingAuditEvent {
        public Instant occurredAt;
        public String actor;
        public String actorRole;
        public String module;
        public String action;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public Object payload;
        public String extraTags;
    }

    private final AuditEventRepository repository;
    private final AuditProperties properties;
    private final DtsCommonAuditClient auditClient;
    private final ObjectMapper objectMapper;

    private BlockingQueue<PendingAuditEvent> queue;
    private ScheduledExecutorService workerPool;
    private SecretKey encryptionKey;
    private SecretKey hmacKey;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> lastChainSignature = new AtomicReference<>("");

    public AuditTrailService(
        AuditEventRepository repository,
        AuditProperties properties,
        DtsCommonAuditClient auditClient,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditClient = auditClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.warn("Auditing is disabled via configuration");
            return;
        }
        this.queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "audit-writer");
            t.setDaemon(true);
            return t;
        };
        this.workerPool = Executors.newScheduledThreadPool(1, factory);
        this.encryptionKey = AuditCrypto.buildKey(resolveEncryptionKey());
        this.hmacKey = AuditCrypto.buildMacKey(resolveHmacKey());
        try {
            this.lastChainSignature.set(repository.findTopByOrderByIdDesc().map(AuditEvent::getChainSignature).orElse(""));
        } catch (Exception ex) {
            // In case Liquibase hasn't created the table yet or DB is not ready, degrade gracefully.
            log.warn("AuditTrailService could not fetch last chain signature (will retry later)", ex);
            this.lastChainSignature.set("");
        }
        running.set(true);
        workerPool.scheduleWithFixedDelay(this::drainQueue, 0, 500, TimeUnit.MILLISECONDS);
        Long existingCount = null;
        try {
            existingCount = repository.count();
        } catch (Exception ex) {
            log.warn("Failed to query existing audit event count", ex);
        }
        if (existingCount != null) {
            log.info(
                "Audit writer started with capacity {} and {} existing audit events",
                properties.getQueueCapacity(),
                existingCount
            );
        } else {
            log.info("Audit writer started with capacity {}; existing count unavailable", properties.getQueueCapacity());
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    public void record(String actor, String action, String module, String resourceType, String resourceId, String outcome, Object payload) {
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = defaultString(actor, "anonymous");
        event.action = defaultString(action, "UNKNOWN");
        event.module = defaultString(module, "GENERAL");
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = defaultString(outcome, "SUCCESS");
        event.payload = payload;
        offer(event);
    }

    public void record(String actor, String action, String module, String resourceId, String outcome, Object payload) {
        record(actor, action, module, module, resourceId, outcome, payload);
    }

    public void record(PendingAuditEvent event) {
        if (event.occurredAt == null) {
            event.occurredAt = Instant.now();
        }
        offer(event);
    }

    public Page<AuditEvent> find(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Optional<AuditEvent> findById(Long id) {
        return repository.findById(id);
    }

    public List<AuditEvent> findAll(Sort sort) {
        return repository.findAll(sort);
    }

    public byte[] decryptPayload(AuditEvent event) {
        if (event.getPayloadCipher() == null || event.getPayloadCipher().length == 0) {
            return new byte[0];
        }
        return AuditCrypto.decrypt(event.getPayloadCipher(), encryptionKey, event.getPayloadIv());
    }

    public long purgeAll() {
        long removed = repository.count();
        if (removed == 0) {
            if (queue != null) {
                queue.clear();
            }
            lastChainSignature.set("");
            return 0;
        }
        repository.deleteAllInBatch();
        repository.flush();
        if (queue != null) {
            queue.clear();
        }
        lastChainSignature.set("");
        return removed;
    }

    private void offer(PendingAuditEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!queue.offer(event)) {
            log.warn("Audit queue is full, falling back to synchronous write");
            persist(List.of(event));
        }
    }

    private void drainQueue() {
        if (!running.get()) {
            return;
        }
        List<PendingAuditEvent> batch = new ArrayList<>();
        queue.drainTo(batch, 256);
        if (batch.isEmpty()) {
            return;
        }
        persist(batch);
    }

    private void persist(List<PendingAuditEvent> batch) {
        List<AuditEvent> entities = new ArrayList<>(batch.size());
        String previousChain = lastChainSignature.get();
        for (PendingAuditEvent pending : batch) {
            try {
                AuditEvent entity = buildEntity(pending, previousChain);
                entities.add(entity);
                previousChain = entity.getChainSignature();
            } catch (Exception ex) {
                log.error("Failed to prepare audit event {}", pending.action, ex);
            }
        }
        if (entities.isEmpty()) {
            return;
        }
        repository.saveAll(entities);
        lastChainSignature.set(previousChain);
        if (properties.isForwardEnabled()) {
            entities.forEach(auditClient::enqueue);
        }
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldEvents() {
        Instant threshold = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
        int purged = repository.deleteAllByOccurredAtBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} audit events older than {}", purged, threshold);
        }
    }

    private AuditEvent buildEntity(PendingAuditEvent pending, String previousChain) throws JsonProcessingException {
        AuditEvent entity = new AuditEvent();
        entity.setOccurredAt(pending.occurredAt);
        entity.setActor(defaultString(pending.actor, "anonymous"));
        entity.setActorRole(pending.actorRole);
        entity.setSourceSystem("platform");
        entity.setEventUuid(java.util.UUID.randomUUID());
        entity.setModule(defaultString(pending.module, "GENERAL"));
        entity.setAction(defaultString(pending.action, "UNKNOWN"));
        String normalizedAction = entity.getAction() == null ? "" : entity.getAction().toUpperCase(java.util.Locale.ROOT);
        boolean security = normalizedAction.contains("AUTH_") || normalizedAction.contains("LOGIN") || normalizedAction.contains("LOGOUT") || normalizedAction.contains("ACCESS_DENIED");
        entity.setEventClass(security ? "SecurityEvent" : "AuditEvent");
        entity.setEventType(mapEventType(normalizedAction, entity.getResult()));
        entity.setResourceType(pending.resourceType);
        entity.setResourceId(pending.resourceId);
        InetAddress clientIp = parseClientIp(pending.clientIp);
        if (clientIp == null && StringUtils.hasText(pending.clientIp)) {
            log.debug("Discarding invalid client IP {} for audit action {}", pending.clientIp, pending.action);
        }
        entity.setClientIp(clientIp);
        entity.setClientAgent(pending.clientAgent);
        entity.setRequestUri(pending.requestUri);
        entity.setHttpMethod(pending.httpMethod);
        entity.setResult(defaultString(pending.result, "SUCCESS"));
        entity.setLatencyMs(pending.latencyMs);
        entity.setExtraTags(normalizeExtraTags(pending.extraTags));

        // operator_* from security context
        String username = entity.getActor();
        entity.setOperatorId(username);
        entity.setOperatorName(username);
        java.util.List<String> roles = new java.util.ArrayList<>();
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                    if (ga != null && ga.getAuthority() != null) roles.add(ga.getAuthority());
                }
            }
        } catch (Exception ignore) {}
        java.util.LinkedHashSet<String> filtered = new java.util.LinkedHashSet<>();
        for (String r : roles) {
            if (r == null || r.isBlank()) continue;
            String norm = normalizeRole(r);
            if (norm.startsWith("DEPT_DATA_") || norm.startsWith("INST_DATA_")) filtered.add(norm);
        }
        try { entity.setOperatorRoles(objectMapper.writeValueAsString(new java.util.ArrayList<>(filtered))); } catch (Exception ignored) {}
        entity.setOrgCode(null);
        entity.setOrgName(null);

        java.util.Map<String, Object> det = new java.util.LinkedHashMap<>();
        det.put("target_table", tableFromResource(entity.getResourceType(), entity.getModule()));
        // Do not include target_ref; only target_id when resolvable
        String rid = entity.getResourceId();
        if (rid != null) {
            String s = rid.trim();
            boolean isNumeric = s.matches("\\d+");
            boolean isUuid = s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
            if (isNumeric || isUuid) {
                det.put("target_id", s);
            }
        }
        det.put("action_result", entity.getResult());
        det.put("request_id", java.util.UUID.randomUUID().toString());

        byte[] payloadBytes = serializePayload(pending.payload);
        String payloadDigest = sha256Hex(payloadBytes); // phase 1: remove key/HMAC
        det.put("param_digest", payloadDigest);
        try { entity.setDetails(objectMapper.writeValueAsString(det)); } catch (Exception ignored) {}
        entity.setPayloadIv(null);
        entity.setPayloadCipher(null);
        entity.setPayloadHmac(payloadDigest);
        entity.setChainSignature(sha256Hex((previousChain + "|" + payloadDigest).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        entity.setRecordSignature(simpleRecordSignature(entity, payloadDigest));
        entity.setSignatureKeyVer(null);
        entity.setSummary(buildSummary(entity));
        entity.setCreatedBy(entity.getActor());
        entity.setCreatedDate(Instant.now());
        return entity;
    }

    private String mapEventType(String actionDisplay, String result) {
        if (actionDisplay == null) return "UNKNOWN";
        String a = actionDisplay.toUpperCase(java.util.Locale.ROOT);
        if (a.contains("LOGIN")) return ("SUCCESS".equalsIgnoreCase(result) ? "AUTH_LOGIN_SUCCESS" : "AUTH_LOGIN_FAILURE");
        if (a.contains("LOGOUT")) return "AUTH_LOGOUT";
        return a.replace(' ', '_');
    }

    private String tableFromResource(String resourceType, String module) {
        if (resourceType == null || resourceType.isBlank()) {
            return module == null || module.isBlank() ? "general" : normalizeKey(module);
        }
        String r = resourceType.toLowerCase(java.util.Locale.ROOT);
        // Align key aliases to concrete table names where applicable
        if (r.equals("admin.auth") || r.equals("auth") || r.equals("admin") || r.equals("user") || r.equals("admin_keycloak_user")) {
            return "admin_keycloak_user";
        }
        if (r.equals("portal_menu") || r.equals("menu") || r.equals("portal.menus") || r.equals("portal-menus")) {
            return "portal_menu";
        }
        if (r.equals("api") || r.equals("v1") || r.equals("v2")) {
            return "general";
        }
        return normalizeKey(r);
    }

    private String normalizeKey(String key) {
        if (key == null) return "general";
        String s = key.trim().toLowerCase(java.util.Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s.isBlank() ? "general" : s;
    }

    private String simpleRecordSignature(AuditEvent e, String payloadHmac) {
        String base = String.join("|",
            nullToEmpty(e.getSourceSystem()), nullToEmpty(e.getActor()), nullToEmpty(e.getAction()), nullToEmpty(e.getModule()),
            nullToEmpty(e.getResourceType()), nullToEmpty(e.getResourceId()), nullToEmpty(payloadHmac), e.getOccurredAt() == null ? "" : e.getOccurredAt().toString()
        );
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) { return null; }
    }

    private String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(data);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        return r;
    }

    private String buildSummary(AuditEvent e) {
        String name = e.getOperatorName() != null && !e.getOperatorName().isBlank() ? e.getOperatorName() : e.getActor();
        String actionDesc = e.getEventType() != null ? e.getEventType() : e.getAction();
        String target = (e.getResourceType() == null ? "" : e.getResourceType()) + (e.getResourceId() == null ? "" : (":" + e.getResourceId()));
        return String.format("用户【%s】执行【%s】 %s", nullToEmpty(name), nullToEmpty(actionDesc), target);
    }

    private byte[] serializePayload(Object payload) throws JsonProcessingException {
        if (payload == null) {
            return new byte[0];
        }
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        return objectMapper.writeValueAsBytes(payload);
    }

    private String resolveEncryptionKey() {
        if (!StringUtils.hasText(properties.getEncryptionKey())) {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            String generated = java.util.Base64.getEncoder().encodeToString(random);
            log.warn("No auditing.encryption-key configured; generated ephemeral key for this runtime session");
            properties.setEncryptionKey(generated);
        }
        return properties.getEncryptionKey();
    }

    private String resolveHmacKey() {
        if (StringUtils.hasText(properties.getHmacKey())) {
            return properties.getHmacKey();
        }
        return properties.getEncryptionKey();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeExtraTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return objectMapper.readTree(trimmed).toString();
        } catch (JsonProcessingException ex) {
            if (log.isDebugEnabled()) {
                log.debug("extraTags payload is not JSON, storing as string", ex);
            }
            try {
                return objectMapper.writeValueAsString(trimmed);
            } catch (JsonProcessingException secondary) {
                log.warn("Failed to serialize extraTags payload as JSON string, discarding", secondary);
                return null;
            }
        }
    }

    private InetAddress parseClientIp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return InetAddress.getByName(raw.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
    }
}
