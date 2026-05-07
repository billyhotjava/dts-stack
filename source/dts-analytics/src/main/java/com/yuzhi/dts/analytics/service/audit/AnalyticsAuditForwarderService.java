package com.yuzhi.dts.analytics.service.audit;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class AnalyticsAuditForwarderService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsAuditForwarderService.class);
    private static final String SOURCE_SYSTEM = "analytics";

    private final DtsAdminProperties adminProperties;
    private final RestTemplate restTemplate;
    private final URI ingestEndpoint;

    public AnalyticsAuditForwarderService(DtsAdminProperties adminProperties, RestTemplateBuilder restTemplateBuilder) {
        this.adminProperties = adminProperties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
        this.ingestEndpoint = resolveEndpoint(adminProperties);
        if (this.ingestEndpoint == null) {
            LOG.warn("dts-admin base URL is not configured; analytics fallback audit forwarding is disabled");
        }
    }

    @Async
    public void record(AnalyticsAuditEvent event) {
        if (event == null || !adminProperties.isEnabled() || ingestEndpoint == null) {
            return;
        }
        if (!StringUtils.hasText(event.actor())) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", SOURCE_SYSTEM);
        body.put("occurredAt", Instant.now().toString());
        body.put("actor", event.actor());
        if (StringUtils.hasText(event.actorName())) {
            body.put("actorName", event.actorName());
        }
        body.put("module", event.module());
        body.put("action", event.action());
        body.put("operationType", event.operationType());
        body.put("operationTypeCode", event.operationType());
        body.put("resourceType", event.resourceType());
        if (StringUtils.hasText(event.resourceId())) {
            body.put("resourceId", event.resourceId());
            body.put("targetIds", java.util.List.of(event.resourceId()));
        }
        body.put("targetTable", event.resourceType());
        body.put("result", event.result());
        body.put("httpMethod", event.httpMethod());
        body.put("requestUri", event.requestUri());
        if (StringUtils.hasText(event.clientIp())) {
            body.put("clientIp", event.clientIp());
        }
        if (StringUtils.hasText(event.clientAgent())) {
            body.put("clientAgent", event.clientAgent());
        }
        if (event.latencyMs() != null) {
            body.put("latencyMs", event.latencyMs());
        }
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, AdminAuditHttpHeadersFactory.build(adminProperties));
            ResponseEntity<Void> response = restTemplate.postForEntity(ingestEndpoint, entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Forwarded analytics fallback audit but received status {} action={}", response.getStatusCode(), event.action());
            }
        } catch (Exception ex) {
            LOG.warn("Failed to forward analytics fallback audit action={} uri={}: {}", event.action(), event.requestUri(), ex.getMessage());
        }
    }

    private static URI resolveEndpoint(DtsAdminProperties properties) {
        if (properties == null || !properties.isEnabled() || !StringUtils.hasText(properties.getBaseUrl())) {
            return null;
        }
        String base = properties.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String apiPath = StringUtils.hasText(properties.getApiPath()) ? properties.getApiPath().trim() : "/api";
        if (!apiPath.startsWith("/")) {
            apiPath = "/" + apiPath;
        }
        if (apiPath.endsWith("/")) {
            apiPath = apiPath.substring(0, apiPath.length() - 1);
        }
        return URI.create(base + apiPath + "/audit-events");
    }

    public record AnalyticsAuditEvent(
        String actor,
        String actorName,
        String module,
        String action,
        String operationType,
        String resourceType,
        String resourceId,
        String result,
        String httpMethod,
        String requestUri,
        String clientIp,
        String clientAgent,
        Integer latencyMs
    ) {}
}
