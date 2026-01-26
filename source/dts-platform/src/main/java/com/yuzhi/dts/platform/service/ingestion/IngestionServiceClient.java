package com.yuzhi.dts.platform.service.ingestion;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class IngestionServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionServiceClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final Duration HEALTH_TTL = Duration.ofSeconds(15);

    private final RestTemplate restTemplate;
    private final RestTemplate longRestTemplate;
    private final RestTemplate healthRestTemplate;
    private final DtsIngestionProperties properties;
    private final AtomicReference<HealthStatus> cachedHealth;

    public IngestionServiceClient(RestTemplateBuilder builder, DtsIngestionProperties properties) {
        this.properties = properties;
        RestTemplateBuilder baseBuilder = builder.setConnectTimeout(Duration.ofSeconds(5));
        this.restTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(20)).build();
        this.longRestTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(180)).build();
        this.healthRestTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(3)).build();
        this.cachedHealth = new AtomicReference<>(HealthStatus.unknown());
    }

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getBaseUrl());
    }

    public HealthStatus healthStatus() {
        if (!isEnabled()) {
            return HealthStatus.disabled();
        }
        HealthStatus cached = cachedHealth.get();
        Instant now = Instant.now();
        if (cached.checkedAt() != null && Duration.between(cached.checkedAt(), now).compareTo(HEALTH_TTL) < 0) {
            return cached;
        }
        HealthStatus refreshed = checkHealth(now);
        cachedHealth.set(refreshed);
        return refreshed;
    }

    public ApiResponse<Map<String, Object>> createIngestionTask(Object payload) {
        return exchangeTaskLong("/api/ingestion/tasks", HttpMethod.POST, payload);
    }

    public ApiResponse<Map<String, Object>> listTasks(Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/list", HttpMethod.GET, null, params);
    }

    public ApiResponse<Map<String, Object>> getTask(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id, HttpMethod.GET, null, null);
    }

    public ApiResponse<Map<String, Object>> updateTask(Long id, Object payload) {
        return exchangeTask("/api/ingestion/tasks/" + id, HttpMethod.PUT, payload, null);
    }

    public ApiResponse<Map<String, Object>> deleteTask(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id, HttpMethod.DELETE, null, null);
    }

    public ApiResponse<Map<String, Object>> executeTask(Long id) {
        return exchangeTaskLong("/api/ingestion/tasks/" + id + "/execute", HttpMethod.POST, null);
    }

    public ApiResponse<Map<String, Object>> rebuildDag(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id + "/dag/rebuild", HttpMethod.POST, null, null);
    }

    public ApiResponse<Map<String, Object>> listExecutions(Long id, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + id + "/executions", HttpMethod.GET, null, params);
    }

    public ApiResponse<Map<String, Object>> latestExecution(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id + "/executions/latest", HttpMethod.GET, null, null);
    }

    public ApiResponse<Object> discoverTables(Object payload) {
        return exchangeObject("/api/ingestion/metadata/tables", HttpMethod.POST, payload, null, longRestTemplate);
    }

    private ApiResponse<Map<String, Object>> exchangeTaskLong(String path, HttpMethod method, Object payload) {
        return exchangeTask(path, method, payload, null, longRestTemplate);
    }

    private ApiResponse<Map<String, Object>> exchangeTask(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params
    ) {
        return exchangeTask(path, method, payload, params, restTemplate);
    }

    private ApiResponse<Map<String, Object>> exchangeTask(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        RestTemplate client
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path, params);
        try {
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(defaultHeaders()) : new HttpEntity<>(payload, defaultHeaders());
            ResponseEntity<Object> response = client.exchange(uri, method, entity, Object.class);
            Object body = response.getBody();
            if (body == null) {
                if (response.getStatusCode().is2xxSuccessful()) {
                    return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "OK", null);
                }
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion service empty response", null);
            }
            if (body instanceof ApiResponse<?> apiResponse) {
                @SuppressWarnings("unchecked")
                ApiResponse<Map<String, Object>> casted = (ApiResponse<Map<String, Object>>) apiResponse;
                return casted;
            }
            if (body instanceof Map<?, ?> map) {
                Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> payloadMap.put(String.valueOf(key), value));
                return new ApiResponse<>(response.getStatusCode().value(), "ok", payloadMap);
            }
            return new ApiResponse<>(response.getStatusCode().value(), "ok", Map.of("value", body));
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion API {} error: {}", path, ex.getMessage());
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    private ApiResponse<Object> exchangeObject(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        RestTemplate client
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path, params);
        try {
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(defaultHeaders()) : new HttpEntity<>(payload, defaultHeaders());
            ResponseEntity<Object> response = client.exchange(uri, method, entity, Object.class);
            Object body = response.getBody();
            if (body == null) {
                if (response.getStatusCode().is2xxSuccessful()) {
                    return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "OK", null);
                }
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion service empty response", null);
            }
            if (body instanceof ApiResponse<?> apiResponse) {
                @SuppressWarnings("unchecked")
                ApiResponse<Object> casted = (ApiResponse<Object>) apiResponse;
                return casted;
            }
            if (body instanceof Map<?, ?> map) {
                ApiResponse<Object> unwrapped = unwrapApiResponseMap(map);
                if (unwrapped != null) {
                    return unwrapped;
                }
            }
            return new ApiResponse<>(response.getStatusCode().value(), "ok", body);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion API {} error: {}", path, ex.getMessage());
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    private ApiResponse<Object> unwrapApiResponseMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        if (!map.containsKey("status") || !map.containsKey("data")) {
            return null;
        }
        int status = parseStatus(map.get("status"), ResultStatus.SUCCESS.getCode());
        String message = map.get("message") == null ? "ok" : String.valueOf(map.get("message"));
        ApiResponse<Object> response = new ApiResponse<>(status, message, map.get("data"));
        Object code = map.get("code");
        if (code != null) {
            String text = String.valueOf(code).trim();
            if (!text.isEmpty()) {
                response.setCode(text);
            }
        }
        return response;
    }

    private int parseStatus(Object status, int fallback) {
        if (status instanceof Number number) {
            return number.intValue();
        }
        if (status != null) {
            String text = status.toString().trim();
            if (!text.isEmpty()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private HealthStatus checkHealth(Instant now) {
        try {
            URI uri = buildAbsoluteUri("/management/health");
            ResponseEntity<Map> response = healthRestTemplate.getForEntity(uri, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                Object body = response.getBody();
                if (body instanceof Map<?, ?> map) {
                    Object status = map.get("status");
                    String statusText = status == null ? "" : status.toString();
                    boolean up = "UP".equalsIgnoreCase(statusText);
                    return new HealthStatus(up, now, up ? null : "health=" + statusText);
                }
                return new HealthStatus(true, now, null);
            }
            return new HealthStatus(false, now, "health=http:" + response.getStatusCode().value());
        } catch (Exception ex) {
            return new HealthStatus(false, now, ex.getMessage());
        }
    }

    public record HealthStatus(boolean ready, Instant checkedAt, String message) {
        public static HealthStatus unknown() {
            return new HealthStatus(false, Instant.EPOCH, "unknown");
        }

        public static HealthStatus disabled() {
            return new HealthStatus(false, Instant.now(), "disabled");
        }
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set(SERVICE_HEADER, properties.getServiceName());
        }
        return headers;
    }

    private URI buildAbsoluteUri(String suffix) {
        String base = properties.getBaseUrl();
        String normalizedBase = base == null ? "" : base.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String tail = suffix == null ? "" : suffix;
        if (StringUtils.hasText(tail) && !tail.startsWith("/")) {
            tail = "/" + tail;
        }
        return UriComponentsBuilder.fromHttpUrl(normalizedBase + tail).build(true).toUri();
    }

    private URI buildAbsoluteUri(String suffix, Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(buildAbsoluteUri(suffix));
        if (params != null && !params.isEmpty()) {
            params.forEach(
                (key, value) -> {
                    if (!StringUtils.hasText(key) || value == null) {
                        return;
                    }
                    if (value instanceof Iterable<?> iterable) {
                        for (Object item : iterable) {
                            if (item != null) {
                                builder.queryParam(key, item);
                            }
                        }
                    } else {
                        builder.queryParam(key, value);
                    }
                }
            );
        }
        return builder.build(true).toUri();
    }
}
