package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import com.yuzhi.dts.analytics.service.audit.AdminAuditHttpHeadersFactory;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.web.support.RequestContext;
import com.yuzhi.dts.analytics.web.support.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Forwards semantic-layer activity to the central audit ingest endpoint
 * (/api/audit-events) on dts-admin. Unlike ScreenAuditService, semantic
 * audit does not need a local mirror table — the central audit_entry is
 * the single source of truth for query / VDS / promote actions.
 *
 * <p>Failed forwards are queued (bounded) and retried by a scheduled job
 * so that a transient admin outage does not lose security-relevant events.
 */
@Service
public class SemanticAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticAuditService.class);
    private static final String SOURCE_SYSTEM = "analytics";
    private static final String MODULE_KEY = "semantic";
    private static final String MODULE_NAME = "自助BI与语义层";
    private static final int FAILOVER_QUEUE_MAX_SIZE = 5_000;
    private static final int RETRY_BATCH_SIZE = 50;

    private final RestTemplate restTemplate;
    private final URI ingestEndpoint;
    private final DtsAdminProperties adminProperties;
    private final ConcurrentLinkedQueue<Map<String, Object>> failedEventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong droppedEventCount = new AtomicLong(0);

    public SemanticAuditService(
        RestTemplateBuilder restTemplateBuilder,
        DtsAdminProperties adminProperties
    ) {
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
        this.adminProperties = adminProperties;
        this.ingestEndpoint = resolveEndpoint(adminProperties);
        if (this.ingestEndpoint == null) {
            LOG.warn("dts-admin base URL is not configured; semantic audit forwarding will be disabled");
        } else {
            LOG.info("Semantic audit forwarding enabled, endpoint={}", this.ingestEndpoint);
        }
    }

    /** Records a successful semantic action. */
    public void logSuccess(
        String actionCode,
        String summary,
        AnalyticsUser actor,
        HttpServletRequest request,
        Object resourceId,
        Map<String, Object> attributes
    ) {
        record(actionCode, summary, actor, request, "SUCCESS", resourceId, attributes, null);
    }

    /** Records a failed semantic action with the failure reason. */
    public void logFailure(
        String actionCode,
        String summary,
        AnalyticsUser actor,
        HttpServletRequest request,
        Object resourceId,
        Map<String, Object> attributes,
        String reason
    ) {
        record(actionCode, summary, actor, request, "FAIL", resourceId, attributes, reason);
    }

    public void record(
        String actionCode,
        String summary,
        AnalyticsUser actor,
        HttpServletRequest request,
        String result,
        Object resourceId,
        Map<String, Object> attributes,
        String reason
    ) {
        if (!StringUtils.hasText(actionCode)) {
            return;
        }
        try {
            Map<String, Object> body = buildPayload(actionCode, summary, actor, request, result, resourceId, attributes, reason);
            forward(body);
        } catch (Exception ex) {
            LOG.warn("Failed to build semantic audit payload action={}: {}", actionCode, ex.getMessage());
        }
    }

    private Map<String, Object> buildPayload(
        String actionCode,
        String summary,
        AnalyticsUser actor,
        HttpServletRequest request,
        String result,
        Object resourceId,
        Map<String, Object> attributes,
        String reason
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", SOURCE_SYSTEM);
        body.put("occurredAt", Instant.now().toString());
        body.put("actor", resolveActor(actor));
        body.put("actorName", resolveActorName(actor));
        body.put("module", MODULE_KEY);
        body.put("moduleKey", MODULE_KEY);
        body.put("moduleName", MODULE_NAME);
        body.put("buttonCode", actionCode);
        body.put("operationCode", actionCode);
        body.put("operationName", StringUtils.hasText(summary) ? summary : actionCode);
        body.put("summary", StringUtils.hasText(summary) ? summary : actionCode);
        body.put("result", normalizeResult(result));
        body.put("resourceType", "SEMANTIC");
        body.put("targetTable", "SEMANTIC");
        if (resourceId != null) {
            String idText = resourceId.toString();
            body.put("resourceId", idText);
            body.put("targetIds", List.of(idText));
        }

        RequestContext ctx = RequestContextHolder.current();
        if (ctx != null && StringUtils.hasText(ctx.clientIp())) {
            body.put("clientIp", ctx.clientIp());
        } else if (request != null) {
            String ip = clientIpFromRequest(request);
            if (StringUtils.hasText(ip)) {
                body.put("clientIp", ip);
            }
        }
        if (request != null) {
            body.put("requestUri", request.getRequestURI());
            body.put("httpMethod", request.getMethod());
            String userAgent = request.getHeader("User-Agent");
            if (StringUtils.hasText(userAgent)) {
                body.put("clientAgent", userAgent);
            }
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        if (attributes != null && !attributes.isEmpty()) {
            attributes.forEach((k, v) -> {
                if (k != null && v != null) {
                    attrs.put(k, v);
                }
            });
        }
        if (StringUtils.hasText(reason)) {
            attrs.put("reason", reason);
        }
        if (!attrs.isEmpty()) {
            body.put("attributes", attrs);
        }
        return body;
    }

    @Async
    void forward(Map<String, Object> body) {
        if (ingestEndpoint == null || body == null) {
            return;
        }
        postEvent(body);
    }

    private void postEvent(Map<String, Object> body) {
        HttpHeaders headers = AdminAuditHttpHeadersFactory.build(adminProperties);
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                ingestEndpoint, new HttpEntity<>(body, headers), Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Forwarded semantic audit but received non-success status {} (action={})",
                    response.getStatusCode(), body.get("operationCode"));
                enqueueFailedEvent(body);
            }
        } catch (RestClientException ex) {
            LOG.warn("Failed to forward semantic audit action={}: {}", body.get("operationCode"), ex.getMessage());
            enqueueFailedEvent(body);
        }
    }

    private void enqueueFailedEvent(Map<String, Object> body) {
        while (failedEventQueue.size() >= FAILOVER_QUEUE_MAX_SIZE) {
            Map<String, Object> dropped = failedEventQueue.poll();
            if (dropped != null) {
                long total = droppedEventCount.incrementAndGet();
                if (total % 100 == 1) {
                    LOG.warn("Semantic audit failover queue full (max={}), dropping oldest. Total dropped: {}",
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
            if (event == null) break;
            batch.add(event);
        }
        HttpHeaders headers = AdminAuditHttpHeadersFactory.build(adminProperties);
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
        LOG.info("Retried {} semantic audit events, {} succeeded, {} still pending",
            batch.size(), succeeded, failedEventQueue.size());
    }

    private static URI resolveEndpoint(DtsAdminProperties adminProperties) {
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

    private static String resolveActor(AnalyticsUser actor) {
        if (actor == null) {
            return "anonymous";
        }
        if (StringUtils.hasText(actor.getPlatformUsername())) {
            return actor.getPlatformUsername().trim();
        }
        if (StringUtils.hasText(actor.getEmail())) {
            return actor.getEmail().trim();
        }
        return "user:" + actor.getId();
    }

    private static String resolveActorName(AnalyticsUser actor) {
        if (actor == null) {
            return "anonymous";
        }
        String first = actor.getFirstName();
        String last = actor.getLastName();
        if (StringUtils.hasText(first) || StringUtils.hasText(last)) {
            String name = (StringUtils.hasText(last) ? last : "") + (StringUtils.hasText(first) ? first : "");
            if (StringUtils.hasText(name)) {
                return name;
            }
        }
        return resolveActor(actor);
    }

    private static String normalizeResult(String result) {
        if (!StringUtils.hasText(result)) {
            return "SUCCESS";
        }
        String upper = result.trim().toUpperCase();
        if ("FAIL".equals(upper) || "FAILED".equals(upper) || "ERROR".equals(upper) || "DENY".equals(upper)) {
            return "FAIL";
        }
        if ("PENDING".equals(upper) || "BEGIN".equals(upper)) {
            return "PENDING";
        }
        return "SUCCESS";
    }

    private static String clientIpFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
