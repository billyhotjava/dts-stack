package com.yuzhi.dts.admin.service.audit;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    public void trySend(String actor, String action, String targetKind, String targetRef, String timestamp) {
        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("actor", actor);
            body.put("action", action);
            body.put("targetRef", targetRef);
            body.put("targetKind", targetKind);
            body.put("createdAt", Instant.parse(timestamp).toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
            }
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            try {
                ResponseEntity<Void> resp = http.postForEntity(URI.create(baseUrl + "/api/audit-events"), req, Void.class);
                if (!resp.getStatusCode().is2xxSuccessful()) {
                    log.warn("Audit send failed with status: {}", resp.getStatusCode());
                }
            } catch (Exception ex) {
                log.warn("Audit send failed: {}", ex.toString());
            }
        } catch (Exception e) {
            log.warn("Audit send error: {}", e.toString());
        }
    }
}
