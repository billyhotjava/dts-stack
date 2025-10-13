package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.config.DtsCommonAuditProperties;
import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class DtsCommonAuditClient {

    private static final Logger log = LoggerFactory.getLogger(DtsCommonAuditClient.class);

    private final DtsCommonAuditProperties properties;
    private final RestTemplate http = new RestTemplate();
    private final BlockingQueue<AuditEvent> forwardQueue = new LinkedBlockingQueue<>(2000);

    public DtsCommonAuditClient(DtsCommonAuditProperties properties) {
        this.properties = properties;
    }

    public void enqueue(AuditEvent event) {
        if (!properties.isEnabled() || properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return;
        }
        if (!forwardQueue.offer(event)) {
            log.warn("Audit forward queue is full, dropping event id={}", event.getId());
        }
    }

    @Scheduled(fixedDelayString = "${auditing.forward-retry-interval:60000}")
    public void flush() {
        if (!properties.isEnabled() || properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return;
        }
        AuditEvent event;
        while ((event = forwardQueue.poll()) != null) {
            try {
                send(event);
            } catch (RestClientException ex) {
                log.warn("Audit forward failed, will retry: {}", ex.getMessage());
                try {
                    if (!forwardQueue.offer(event, 100, TimeUnit.MILLISECONDS)) {
                        log.error("Audit forward queue saturated while retrying, event id={} dropped", event.getId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }

    private void send(AuditEvent event) {
        Map<String, Object> body = new HashMap<>();
        body.put("actor", event.getActor());
        body.put("action", event.getAction());
        body.put("targetRef", event.getResourceId());
        body.put("targetKind", event.getResourceType());
        body.put("module", event.getModule());
        body.put("result", event.getResult());
        body.put("occurredAt", event.getOccurredAt() != null ? event.getOccurredAt().toString() : Instant.now().toString());
        try { body.put("clientIp", event.getClientIp() != null ? event.getClientIp().getHostAddress() : null); } catch (Exception ignore) {}
        body.put("clientAgent", event.getClientAgent());
        body.put("requestUri", event.getRequestUri());
        body.put("httpMethod", event.getHttpMethod());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getServiceToken() != null && !properties.getServiceToken().isBlank()) {
            headers.setBearerAuth(properties.getServiceToken());
        }

        ResponseEntity<Void> response = http.postForEntity(URI.create(properties.getBaseUrl() + "/api/audit-events"), new HttpEntity<>(body, headers), Void.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Forward response: " + response.getStatusCode());
        }
    }
}
