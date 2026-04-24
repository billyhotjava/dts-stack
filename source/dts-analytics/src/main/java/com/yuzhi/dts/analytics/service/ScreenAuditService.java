package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAuditLogRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.yuzhi.dts.analytics.web.support.RequestContext;
import com.yuzhi.dts.analytics.web.support.RequestContextHolder;

@Service
@Transactional
public class ScreenAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenAuditService.class);
    private static final String SOURCE_SYSTEM = "analytics";
    private static final int FAILOVER_QUEUE_MAX_SIZE = 5_000;
    private static final int RETRY_BATCH_SIZE = 50;

    private final AnalyticsScreenAuditLogRepository screenAuditLogRepository;
    private final AnalyticsUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final URI ingestEndpoint;
    private final ConcurrentLinkedQueue<Map<String, Object>> failedEventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedEventCount = new AtomicLong(0);

    public ScreenAuditService(
        AnalyticsScreenAuditLogRepository screenAuditLogRepository,
        AnalyticsUserRepository userRepository,
        ObjectMapper objectMapper,
        RestTemplateBuilder restTemplateBuilder,
        DtsAdminProperties adminProperties
    ) {
        this.screenAuditLogRepository = screenAuditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
        this.ingestEndpoint = resolveEndpoint(adminProperties);
        if (this.ingestEndpoint == null) {
            LOG.warn("dts-admin base URL is not configured; screen audit forwarding to central audit will be disabled");
        } else {
            LOG.info("Screen audit forwarding enabled, endpoint={}", this.ingestEndpoint);
        }
    }

    public void log(Long screenId, Long actorId, String action, Object before, Object after, String requestId) {
        logAndReturn(screenId, actorId, action, before, after, requestId);
    }

    public AnalyticsScreenAuditLog logAndReturn(
            Long screenId,
            Long actorId,
            String action,
            Object before,
            Object after,
            String requestId) {
        if (screenId == null || action == null || action.isBlank()) {
            return null;
        }

        // Capture client IP from request thread before @Async dispatch
        String clientIp = resolveCurrentClientIp();

        AnalyticsScreenAuditLog log = new AnalyticsScreenAuditLog();
        log.setScreenId(screenId);
        log.setActorId(actorId);
        log.setAction(action);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setRequestId(trimToNull(requestId));
        AnalyticsScreenAuditLog saved = screenAuditLogRepository.save(log);

        // Forward to dts-admin audit center asynchronously
        forwardToAdmin(saved, clientIp);

        return saved;
    }

    private String resolveCurrentClientIp() {
        RequestContext ctx = RequestContextHolder.current();
        return ctx != null ? ctx.clientIp() : null;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenAuditLog> listByScreenId(Long screenId, int limit) {
        if (screenId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        List<AnalyticsScreenAuditLog> all = screenAuditLogRepository.findAllByScreenIdOrderByCreatedAtDesc(screenId);
        if (all.size() <= safeLimit) {
            return all;
        }
        return new ArrayList<>(all.subList(0, safeLimit));
    }

    // ---- Admin audit forwarding ----

    @Async
    void forwardToAdmin(AnalyticsScreenAuditLog auditLog, String clientIp) {
        if (ingestEndpoint == null || auditLog == null) {
            return;
        }
        try {
            Map<String, Object> body = buildForwardPayload(auditLog, clientIp);
            postEvent(body);
        } catch (Exception ex) {
            LOG.warn("Failed to forward screen audit event action={} screenId={}: {}",
                auditLog.getAction(), auditLog.getScreenId(), ex.getMessage());
        }
    }

    private Map<String, Object> buildForwardPayload(AnalyticsScreenAuditLog auditLog, String clientIp) {
        Instant occurredAt = auditLog.getCreatedAt() != null ? auditLog.getCreatedAt() : Instant.now();
        String actor = resolveActorUsername(auditLog.getActorId());
        String action = auditLog.getAction();
        String buttonCode = mapActionToButtonCode(action);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceSystem", SOURCE_SYSTEM);
        body.put("occurredAt", occurredAt.toString());
        body.put("actor", actor);
        body.put("module", "SCREEN");
        body.put("action", action);
        body.put("resourceType", "SCREEN");
        body.put("resourceId", String.valueOf(auditLog.getScreenId()));
        body.put("targetIds", List.of(String.valueOf(auditLog.getScreenId())));
        body.put("targetTable", "SCREEN");
        body.put("result", "SUCCESS");
        if (buttonCode != null) {
            body.put("buttonCode", buttonCode);
            body.put("operationCode", buttonCode);
        }
        if (StringUtils.hasText(clientIp)) {
            body.put("clientIp", clientIp);
        }
        return body;
    }

    private String resolveActorUsername(Long actorId) {
        if (actorId == null) {
            return "unknown";
        }
        try {
            return userRepository.findById(actorId)
                .map(AnalyticsUser::getEmail)
                .orElse("user:" + actorId);
        } catch (Exception ex) {
            return "user:" + actorId;
        }
    }

    private String mapActionToButtonCode(String action) {
        if (action == null) {
            return null;
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "screen.create" -> "SCREEN_CREATE";
            case "screen.update" -> "SCREEN_UPDATE";
            case "screen.delete" -> "SCREEN_DELETE";
            case "screen.publish" -> "SCREEN_PUBLISH";
            case "screen.rollback" -> "SCREEN_ROLLBACK";
            case "screen.migrate" -> "SCREEN_MIGRATE";
            case "screen.export.json" -> "SCREEN_EXPORT_JSON";
            case "screen.export.image" -> "SCREEN_EXPORT_IMAGE";
            case "screen.export.pdf" -> "SCREEN_EXPORT_PDF";
            case "screen.public_link.enable" -> "SCREEN_PUBLIC_LINK_ENABLE";
            case "screen.public_link.disable" -> "SCREEN_PUBLIC_LINK_DISABLE";
            case "screen.acl.grant" -> "SCREEN_ACL_GRANT";
            case "screen.acl.revoke" -> "SCREEN_ACL_REVOKE";
            default -> {
                // Fallback: uppercase and replace dots with underscores
                yield action.toUpperCase(Locale.ROOT).replace('.', '_');
            }
        };
    }

    private void postEvent(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                ingestEndpoint, new HttpEntity<>(body, headers), Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Forwarded screen audit event but received non-success status {} (action={})",
                    response.getStatusCode(), body.get("action"));
            }
        } catch (RestClientException ex) {
            LOG.warn("Failed to forward screen audit event action={}: {}", body.get("action"), ex.getMessage());
            enqueueFailedEvent(body);
        }
    }

    private void enqueueFailedEvent(Map<String, Object> body) {
        while (failedEventQueue.size() >= FAILOVER_QUEUE_MAX_SIZE) {
            Map<String, Object> dropped = failedEventQueue.poll();
            if (dropped != null) {
                long total = droppedEventCount.incrementAndGet();
                if (total % 100 == 1) {
                    LOG.warn("Screen audit failover queue full (max={}), dropping oldest. Total dropped: {}",
                        FAILOVER_QUEUE_MAX_SIZE, total);
                }
            }
        }
        failedEventQueue.offer(body);
    }

    @Scheduled(fixedDelay = 30_000)
    public void retryFailedEvents() {
        if (ingestEndpoint == null) {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        int succeeded = 0;
        for (Map<String, Object> body : batch) {
            try {
                ResponseEntity<Void> response = restTemplate.postForEntity(
                    ingestEndpoint, new HttpEntity<>(body, headers), Void.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    succeeded++;
                } else {
                    enqueueFailedEvent(body);
                }
            } catch (RestClientException ex) {
                enqueueFailedEvent(body);
            }
        }

        LOG.info("Retried {} screen audit events, {} succeeded, {} still pending",
            batch.size(), succeeded, failedEventQueue.size());
    }

    // ---- Utility ----

    private URI resolveEndpoint(DtsAdminProperties adminProperties) {
        if (adminProperties == null || !adminProperties.isEnabled() || !StringUtils.hasText(adminProperties.getBaseUrl())) {
            return null;
        }
        String base = adminProperties.getBaseUrl().replaceAll("/+$", "");
        String apiPath = adminProperties.getApiPath();
        String normalizedPath = StringUtils.hasText(apiPath)
            ? "/" + apiPath.replaceAll("^/+", "").replaceAll("/+$", "")
            : "";
        return URI.create(base + normalizedPath + "/audit-events");
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
