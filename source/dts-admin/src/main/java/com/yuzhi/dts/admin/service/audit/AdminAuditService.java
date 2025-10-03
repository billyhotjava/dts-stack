package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditProperties;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.repository.AuditEventRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    public static class AuditEventView {
        public long id;
        public Instant occurredAt;
        public String actor;
        public String module;
        public String action;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String httpMethod;
        public String result;
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

    public AdminAuditService(
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
        this.lastChainSignature.set(repository.findTopByOrderByIdDesc().map(AuditEvent::getChainSignature).orElse(""));
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
        logEnqueuedEvent(event);
        offer(event);
    }

    public void record(String actor, String action, String module, String resourceId, String outcome, Object payload) {
        record(actor, action, module, module, resourceId, outcome, payload);
    }

    public void record(PendingAuditEvent event) {
        if (event.occurredAt == null) {
            event.occurredAt = Instant.now();
        }
        logEnqueuedEvent(event);
        offer(event);
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
        if (log.isDebugEnabled()) {
            AuditEvent last = entities.get(entities.size() - 1);
            log.debug(
                "Persisted {} audit events (module={}, action={}, result={}, queueSize={})",
                entities.size(),
                last.getModule(),
                last.getAction(),
                last.getResult(),
                queue != null ? queue.size() : -1
            );
        }
        if (properties.isForwardEnabled()) {
            entities.forEach(auditClient::enqueue);
        }
    }

    private AuditEvent buildEntity(PendingAuditEvent pending, String previousChain) throws JsonProcessingException {
        AuditEvent entity = new AuditEvent();
        entity.setOccurredAt(pending.occurredAt);
        entity.setActor(defaultString(pending.actor, "anonymous"));
        entity.setActorRole(pending.actorRole);
        entity.setModule(defaultString(pending.module, "GENERAL"));
        entity.setAction(defaultString(pending.action, "UNKNOWN"));
        entity.setResourceType(pending.resourceType);
        entity.setResourceId(pending.resourceId);
        entity.setClientIp(parseClientIp(pending.clientIp));
        entity.setClientAgent(pending.clientAgent);
        entity.setRequestUri(pending.requestUri);
        entity.setHttpMethod(pending.httpMethod);
        entity.setResult(defaultString(pending.result, "SUCCESS"));
        entity.setLatencyMs(pending.latencyMs);
        entity.setExtraTags(normalizeExtraTags(pending.extraTags));

        byte[] payloadBytes = pending.payload == null ? new byte[0] : objectMapper.writeValueAsBytes(pending.payload);
        byte[] iv = AuditCrypto.randomIv();
        byte[] cipher = AuditCrypto.encrypt(payloadBytes, encryptionKey, iv);
        String payloadHmac = AuditCrypto.hmac(payloadBytes, hmacKey);
        String chainSignature = AuditCrypto.chain(previousChain, payloadHmac, hmacKey);

        entity.setPayloadIv(iv);
        entity.setPayloadCipher(cipher);
        entity.setPayloadHmac(payloadHmac);
        entity.setChainSignature(chainSignature);
        entity.setCreatedBy(entity.getActor());
        entity.setCreatedDate(Instant.now());
        return entity;
    }

    public Page<AuditEvent> search(
        String actor,
        String module,
        String action,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.search(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            effectivePageable
        );
    }

    public List<AuditEventView> list(
        String actor,
        String module,
        String action,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp
    ) {
        return search(actor, module, action, result, resourceType, resource, requestUri, from, to, clientIp, Pageable.unpaged())
            .getContent()
            .stream()
            .map(this::toView)
            .toList();
    }

    public Optional<AuditEvent> findById(Long id) {
        return repository.findById(id);
    }

    public List<AuditEvent> findAllForExport(
        String actor,
        String module,
        String action,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp
    ) {
        return repository
            .search(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                Pageable.unpaged()
            )
            .getContent();
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

    public byte[] decryptPayload(AuditEvent event) {
        if (event.getPayloadCipher() == null || event.getPayloadCipher().length == 0) {
            return new byte[0];
        }
        return AuditCrypto.decrypt(event.getPayloadCipher(), encryptionKey, event.getPayloadIv());
    }

    public AuditEventView toView(AuditEvent event) {
        AuditEventView view = new AuditEventView();
        view.id = event.getId();
        view.occurredAt = event.getOccurredAt();
        view.actor = event.getActor();
        view.module = event.getModule();
        view.action = event.getAction();
        view.resourceType = event.getResourceType();
        view.resourceId = event.getResourceId();
        view.clientIp = event.getClientIp();
        view.clientAgent = event.getClientAgent();
        view.httpMethod = event.getHttpMethod();
        view.result = event.getResult();
        view.extraTags = event.getExtraTags();
        return view;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldEvents() {
        Instant threshold = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
        int purged = repository.deleteAllByOccurredAtBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} audit events older than {}", purged, threshold);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String likePattern(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "%";
        }
        return "%" + escapeLike(normalized) + "%";
    }

    private String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private void logEnqueuedEvent(PendingAuditEvent event) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
            "Queue audit event actor={} module={} action={} result={} resource={} queueSize={}",
            defaultString(event.actor, "anonymous"),
            defaultString(event.module, "GENERAL"),
            defaultString(event.action, "UNKNOWN"),
            defaultString(event.result, "SUCCESS"),
            event.resourceId,
            queue != null ? queue.size() : 0
        );
    }

    private InetAddress parseClientIp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        InetAddress parsed = tryParseInet(candidate);
        if (parsed != null) {
            return parsed;
        }

        if (candidate.startsWith("[") && candidate.contains("]")) {
            String inside = candidate.substring(1, candidate.indexOf(']'));
            parsed = tryParseInet(inside);
            if (parsed != null) {
                return parsed;
            }
        }

        int lastColon = candidate.lastIndexOf(':');
        if (lastColon > 0 && candidate.indexOf(':') == lastColon && candidate.contains(".")) {
            String withoutPort = candidate.substring(0, lastColon);
            parsed = tryParseInet(withoutPort);
            if (parsed != null) {
                return parsed;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Discarding invalid client IP string '{}'", raw);
        }
        return null;
    }

    private InetAddress tryParseInet(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String resolveEncryptionKey() {
        if (!StringUtils.hasText(properties.getEncryptionKey())) {
            byte[] random = new byte[32];
            java.security.SecureRandom randomSource = new java.security.SecureRandom();
            randomSource.nextBytes(random);
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
}
