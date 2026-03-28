package com.yuzhi.dts.platform.service.analytics;

import com.yuzhi.dts.platform.config.DtsAnalyticsProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Lightweight HTTP client for pushing semantic model definitions
 * from dts-platform to the dts-analytics service.
 */
@Component
public class AnalyticsServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsServiceClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";

    private final RestTemplate restTemplate;
    private final DtsAnalyticsProperties properties;

    public AnalyticsServiceClient(RestTemplateBuilder builder, DtsAnalyticsProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
            .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getBaseUrl());
    }

    /**
     * Publish a semantic model definition to dts-analytics.
     *
     * @param payload the semantic publish request body (modelName, tableName, metrics, dimensions, etc.)
     * @return response body from dts-analytics, or an error map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> publishSemantic(Map<String, Object> payload) {
        if (!isEnabled()) {
            return Map.of("error", "analytics service disabled");
        }
        URI uri = buildUri("/api/semantic/publish");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.getServiceName())) {
                headers.set(SERVICE_HEADER, properties.getServiceName());
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            Object body = restTemplate.postForObject(uri, entity, Object.class);
            if (body instanceof Map<?, ?> map) {
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            return Map.of("result", body != null ? body : "ok");
        } catch (HttpStatusCodeException ex) {
            LOG.warn("[analytics-publish] HTTP error status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return Map.of("error", "analytics service error", "status", ex.getStatusCode().value());
        } catch (Exception ex) {
            LOG.warn("[analytics-publish] error: {}", ex.getMessage());
            return Map.of("error", "analytics service error: " + ex.getMessage());
        }
    }

    private URI buildUri(String path) {
        String base = properties.getBaseUrl();
        String normalizedBase = base == null ? "" : base.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String tail = path == null ? "" : path;
        if (StringUtils.hasText(tail) && !tail.startsWith("/")) {
            tail = "/" + tail;
        }
        return UriComponentsBuilder.fromHttpUrl(normalizedBase + tail).build(true).toUri();
    }
}
