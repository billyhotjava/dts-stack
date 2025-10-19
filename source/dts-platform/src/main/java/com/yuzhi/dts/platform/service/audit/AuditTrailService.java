package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);
    private static final String SOURCE_SYSTEM_PLATFORM = "platform";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

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

    private final AuditProperties properties;
    private final RestTemplate restTemplate;
    private final URI ingestEndpoint;

    public AuditTrailService(
        AuditProperties properties,
        RestTemplateBuilder restTemplateBuilder,
        DtsAdminProperties adminProperties
    ) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .setReadTimeout(DEFAULT_READ_TIMEOUT)
            .build();
        this.ingestEndpoint = resolveEndpoint(adminProperties);
        if (this.ingestEndpoint == null) {
            log.warn("dts-admin base URL is not configured; platform audit forwarding will be disabled");
        }
    }

    public void record(String actor, String action, String module, String resourceType, String resourceId, String outcome, Object payload) {
        if (!StringUtils.hasText(actor) || isAnonymous(actor)) {
            return;
        }
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
        if (!properties.isEnabled()) {
            return;
        }
        if (ingestEndpoint == null) {
            return;
        }
        if (event == null || !StringUtils.hasText(event.actor) || isAnonymous(event.actor)) {
            return;
        }
        Instant occurredAt = event.occurredAt != null ? event.occurredAt : Instant.now();
        Map<String, Object> body = new HashMap<>();
        body.put("sourceSystem", SOURCE_SYSTEM_PLATFORM);
        body.put("occurredAt", occurredAt.toString());
        body.put("actor", event.actor);
        if (StringUtils.hasText(event.actorRole)) {
            body.put("actorRole", event.actorRole);
        }
        body.put("module", defaultString(event.module, "GENERAL"));
        body.put("action", defaultString(event.action, "UNKNOWN"));
        if (StringUtils.hasText(event.resourceType)) {
            body.put("resourceType", event.resourceType);
        }
        if (StringUtils.hasText(event.resourceId)) {
            body.put("resourceId", event.resourceId);
        }
        body.put("result", defaultString(event.result, "SUCCESS"));
        if (event.latencyMs != null) {
            body.put("latencyMs", event.latencyMs);
        }
        if (StringUtils.hasText(event.clientIp)) {
            body.put("clientIp", event.clientIp);
        }
        if (StringUtils.hasText(event.clientAgent)) {
            body.put("clientAgent", event.clientAgent);
        }
        if (StringUtils.hasText(event.requestUri)) {
            body.put("requestUri", event.requestUri);
        }
        if (StringUtils.hasText(event.httpMethod)) {
            body.put("httpMethod", event.httpMethod);
        }
        if (event.payload != null) {
            body.put("payload", event.payload);
        }
        if (StringUtils.hasText(event.extraTags)) {
            body.put("extraTags", event.extraTags);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(ingestEndpoint, new HttpEntity<>(body, headers), Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn(
                    "Forwarded audit event but received non-success status code {} (action={}, module={})",
                    response.getStatusCode(),
                    body.get("action"),
                    body.get("module")
                );
            }
        } catch (RestClientException ex) {
            log.warn(
                "Failed to forward audit event action={} module={} : {}",
                body.get("action"),
                body.get("module"),
                ex.getMessage()
            );
        }
    }

    private URI resolveEndpoint(DtsAdminProperties adminProperties) {
        if (adminProperties == null || !adminProperties.isEnabled() || !StringUtils.hasText(adminProperties.getBaseUrl())) {
            return null;
        }
        String base = stripTrailingSlash(adminProperties.getBaseUrl());
        String apiPath = adminProperties.getApiPath();
        String normalizedPath = StringUtils.hasText(apiPath) ? "/" + apiPath.replaceAll("^/+", "").replaceAll("/+$", "") : "";
        return URI.create(base + normalizedPath + "/audit-events");
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("/+$", "");
    }

    private boolean isAnonymous(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        return normalized.isEmpty() || "anonymous".equalsIgnoreCase(normalized) || "anonymoususer".equalsIgnoreCase(normalized);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
