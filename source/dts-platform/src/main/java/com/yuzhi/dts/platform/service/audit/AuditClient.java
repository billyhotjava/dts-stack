package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DtsCommonAuditProperties;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class AuditClient {
    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    private final RestTemplate restTemplate;
    private final DtsCommonAuditProperties props;
    private final ObjectMapper mapper;

    public AuditClient(RestTemplateBuilder builder, DtsCommonAuditProperties props) {
        this.restTemplate = builder.setConnectTimeout(java.time.Duration.ofSeconds(3)).setReadTimeout(java.time.Duration.ofSeconds(5)).build();
        this.props = props;
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void send(String actor, String action, String targetKind, String targetRef) {
        if (!props.isEnabled() || !StringUtils.hasText(props.getBaseUrl())) {
            log.debug("Audit forwarding disabled: {} {} {} {}", actor, action, targetKind, targetRef);
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("actor", actor);
            // attach tenantId if available (optional, best-effort)
            try {
                Object tenant = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication();
                // No-op placeholder: customize if tenant claim is available
            } catch (Exception ignored) {}
            body.put("action", action);
            body.put("targetRef", targetRef);
            body.put("targetKind", targetKind);
            body.put("createdAt", Instant.now().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(props.getServiceToken())) {
                headers.set("Authorization", "Bearer " + props.getServiceToken());
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            URI uri = URI.create(props.getBaseUrl().replaceAll("/+$", "") + "/api/audit-events");
            ResponseEntity<String> resp = restTemplate.postForEntity(uri, entity, String.class);
            log.debug("Audit forwarded: status={} uri={}", resp.getStatusCode(), uri);
        } catch (Exception e) {
            log.warn("Failed to forward audit event: {}", e.getMessage());
        }
    }
}
