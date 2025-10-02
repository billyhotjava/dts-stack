package com.yuzhi.dts.admin.service.audit;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class DtsCommonAuditClient {

    private static final Logger log = LoggerFactory.getLogger(DtsCommonAuditClient.class);

    @Value("${dts.common.audit.enabled:false}")
    private boolean enabled;

    @Value("${dts.common.audit.base-url:}")
    private String baseUrl;

    @Value("${dts.common.audit.token:}")
    private String token;

    private final RestTemplate http = new RestTemplate();
    private final BlockingQueue<com.yuzhi.dts.admin.domain.AuditEvent> forwardQueue = new LinkedBlockingQueue<>(2000);

    public void enqueue(com.yuzhi.dts.admin.domain.AuditEvent event) {
        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        if (!forwardQueue.offer(event)) {
            log.warn("Audit forward queue is full, dropping event id={}", event.getId());
        }
    }

    @Scheduled(fixedDelayString = "${auditing.forward-retry-interval:60000}")
    public void flush() {
        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        com.yuzhi.dts.admin.domain.AuditEvent event;
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

    private void send(com.yuzhi.dts.admin.domain.AuditEvent event) {
        Map<String, Object> body = new HashMap<>();
        body.put("actor", event.getActor());
        body.put("action", event.getAction());
        body.put("targetRef", event.getResourceId());
        body.put("targetKind", event.getResourceType());
        body.put("module", event.getModule());
        body.put("occurredAt", event.getOccurredAt().toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Void> resp = http.postForEntity(URI.create(baseUrl + "/api/audit-events"), req, Void.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RestClientException("Forward response: " + resp.getStatusCode());
        }
    }
}
