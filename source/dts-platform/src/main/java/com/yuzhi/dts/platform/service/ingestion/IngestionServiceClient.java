package com.yuzhi.dts.platform.service.ingestion;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class IngestionServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionServiceClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String USER_HEADER = "X-DTS-User";
    private static final String ROLES_HEADER = "X-DTS-Roles";
    private static final Duration HEALTH_TTL = Duration.ofSeconds(15);

    private final RestTemplate restTemplate;
    private final RestTemplate longRestTemplate;
    private final RestTemplate healthRestTemplate;
    private final DtsIngestionProperties properties;
    private final AtomicReference<HealthStatus> cachedHealth;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public IngestionServiceClient(RestTemplateBuilder builder, DtsIngestionProperties properties) {
        this.properties = properties;
        RestTemplateBuilder baseBuilder = builder.setConnectTimeout(Duration.ofSeconds(5));
        this.restTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(20)).build();
        this.longRestTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(180)).build();
        this.healthRestTemplate = baseBuilder.setReadTimeout(Duration.ofSeconds(3)).build();
        this.cachedHealth = new AtomicReference<>(HealthStatus.unknown());

        // Resilience4j retry: exponential backoff, only on transient failures
        DtsIngestionProperties.Retry retryProps = properties.getRetry();
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(retryProps.getMaxAttempts())
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                retryProps.getWaitDurationMs(), retryProps.getMultiplier()))
            .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
            .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class)
            .build();
        this.retry = RetryRegistry.of(retryConfig).retry("ingestion");
        this.retry.getEventPublisher()
            .onRetry(event -> LOG.warn("[ingestion-retry] attempt #{} for {}: {}",
                event.getNumberOfRetryAttempts(), event.getName(), event.getLastThrowable().getMessage()));

        // Resilience4j circuit breaker: open on sustained failures, allow probing in half-open
        DtsIngestionProperties.CircuitBreaker cbProps = properties.getCircuitBreaker();
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(cbProps.getFailureRateThreshold())
            .slidingWindowSize(cbProps.getSlidingWindowSize())
            .waitDurationInOpenState(Duration.ofSeconds(cbProps.getWaitDurationInOpenStateSeconds()))
            .permittedNumberOfCallsInHalfOpenState(cbProps.getPermittedCallsInHalfOpenState())
            .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
            .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class)
            .build();
        this.circuitBreaker = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("ingestion");
        this.circuitBreaker.getEventPublisher()
            .onStateTransition(event -> LOG.warn("[ingestion-cb] state transition: {}", event.getStateTransition()));
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

    public Object listTemplates() {
        return exchangeObject("/api/ingestion/templates", HttpMethod.GET, null, null, restTemplate);
    }

    public Object renderTemplate(String templateId, Object payload) {
        return exchangeObject("/api/ingestion/templates/" + templateId + "/render", HttpMethod.POST, payload, null, restTemplate);
    }

    public ApiResponse<Map<String, Object>> createIngestionTask(Object payload) {
        return exchangeTaskLong("/api/ingestion/tasks", HttpMethod.POST, payload);
    }

    public ApiResponse<Map<String, Object>> listTasks(Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/list", HttpMethod.GET, null, params);
    }

    public ApiResponse<Object> listTasksBySource(java.util.UUID sourceDataSourceId, boolean includeDeleted) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (sourceDataSourceId != null) {
            params.put("sourceDataSourceId", sourceDataSourceId.toString());
        }
        params.put("includeDeleted", String.valueOf(includeDeleted));
        return exchangeObject("/api/ingestion/tasks/by-source", HttpMethod.GET, null, params, restTemplate);
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

    public ApiResponse<Map<String, Object>> executeTaskAsync(Long id) {
        return exchangeTaskLong("/api/ingestion/tasks/" + id + "/execute/async", HttpMethod.POST, null);
    }

    public ApiResponse<Map<String, Object>> backfillTask(Long id, Object payload) {
        return exchangeTaskLong("/api/ingestion/tasks/" + id + "/backfill", HttpMethod.POST, payload);
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

    public ApiResponse<Map<String, Object>> getExecutionLog(Long taskId, Long executionId, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/logs", HttpMethod.GET, null, params);
    }

    public ApiResponse<Map<String, Object>> retryExecution(Long taskId, Long executionId, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/retry", HttpMethod.POST, null, params);
    }

    public ApiResponse<Map<String, Object>> retryExecutionAsync(Long taskId, Long executionId, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/retry/async", HttpMethod.POST, null, params);
    }

    public ApiResponse<Object> discoverTables(Object payload) {
        return exchangeObject("/api/ingestion/metadata/tables", HttpMethod.POST, payload, null, longRestTemplate);
    }

    public ApiResponse<Object> listConnectorCapabilities() {
        return exchangeObject("/api/ingestion/connectors/capabilities", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> getConnectorCapability(String connectorType) {
        return exchangeObject("/api/ingestion/connectors/capabilities/" + connectorType, HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> getApiConnectorContract() {
        return exchangeObject("/api/ingestion/api/contract", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> listApiAuthProviders() {
        return exchangeObject("/api/ingestion/api/auth-providers", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> getRealtimeStatus(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/realtime-status", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> parseStagingFile(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/parse", HttpMethod.POST, null, null, longRestTemplate);
    }

    public ApiResponse<Object> preCheckStaging(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/pre-check", HttpMethod.POST, null, null, longRestTemplate);
    }

    public ApiResponse<Object> updateStagingCell(Long taskId, Integer rowNum, Object payload) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/staging/" + rowNum, HttpMethod.PUT, payload, null, restTemplate);
    }

    public ApiResponse<Object> reCheckStaging(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/re-check", HttpMethod.POST, null, null, longRestTemplate);
    }

    public ApiResponse<Object> submitStaging(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/submit", HttpMethod.POST, null, null, longRestTemplate);
    }

    public ApiResponse<Object> dropStaging(Long taskId) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/staging", HttpMethod.DELETE, null, null, restTemplate);
    }

    public ApiResponse<Object> getStagingData(Long taskId, Map<String, ?> params) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/staging", HttpMethod.GET, null, params, restTemplate);
    }

    public ApiResponse<Object> getStagingErrorSummary(Long taskId, Map<String, ?> params) {
        return exchangeObject("/api/ingestion/tasks/" + taskId + "/staging/errors/summary", HttpMethod.GET, null, params, restTemplate);
    }

    public ResponseEntity<byte[]> downloadStagingErrors(Long taskId) {
        if (!isEnabled()) {
            return ResponseEntity.status(503).body(null);
        }
        URI uri = buildAbsoluteUri("/api/ingestion/tasks/" + taskId + "/staging/errors/download");
        try {
            return longRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(defaultHeaders()),
                byte[].class
            );
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion staging error download failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsByteArray());
        } catch (Exception ex) {
            LOG.warn("Ingestion staging error download error: {}", ex.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    public ApiResponse<Object> getExecutionsObservability(Map<String, ?> params) {
        return exchangeObject("/api/ingestion/tasks/executions/observability", HttpMethod.GET, null, params, restTemplate);
    }

    public ApiResponse<Object> getGovernanceOverview(Map<String, ?> params) {
        return exchangeObject("/api/ingestion/tasks/executions/governance-overview", HttpMethod.GET, null, params, restTemplate);
    }

    public ApiResponse<Map<String, Object>> listChangeLogs(Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/changes", HttpMethod.GET, null, params);
    }

    public ApiResponse<Map<String, Object>> createChangeLog(Object payload) {
        return exchangeTask("/api/ingestion/tasks/changes", HttpMethod.POST, payload, null);
    }

    public ApiResponse<Object> uploadFile(org.springframework.web.multipart.MultipartFile file) {
        return uploadFile(file, "/api/ingestion/files/upload", null, null, null);
    }

    public ApiResponse<Object> uploadAndParse(
        org.springframework.web.multipart.MultipartFile file,
        Integer previewLimit,
        Integer sheetIndex,
        String sheetName
    ) {
        return uploadFile(file, "/api/ingestion/files/upload-and-parse", previewLimit, sheetIndex, sheetName);
    }

    private ApiResponse<Object> uploadFile(
        org.springframework.web.multipart.MultipartFile file,
        String path,
        Integer previewLimit,
        Integer sheetIndex,
        String sheetName
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (StringUtils.hasText(properties.getServiceName())) {
                headers.set(SERVICE_HEADER, properties.getServiceName());
            }
            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            if (previewLimit != null) {
                body.add("previewLimit", String.valueOf(previewLimit));
            }
            if (sheetIndex != null) {
                body.add("sheetIndex", String.valueOf(sheetIndex));
            }
            if (StringUtils.hasText(sheetName)) {
                body.add("sheetName", sheetName);
            }
            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Object> response = longRestTemplate.exchange(uri, HttpMethod.POST, entity, Object.class);
            Object responseBody = response.getBody();
            if (responseBody instanceof Map<?, ?> map) {
                ApiResponse<Object> unwrapped = unwrapApiResponseMap(map);
                if (unwrapped != null) {
                    return unwrapped;
                }
            }
            return new ApiResponse<>(response.getStatusCode().value(), "ok", responseBody);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion file upload failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "文件上传失败", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion file upload error: {}", ex.getMessage());
            return new ApiResponse<>(500, "文件上传失败: " + ex.getMessage(), null);
        }
    }

    public ApiResponse<Object> parseUploadedFile(
        String fileId,
        Integer previewLimit,
        Integer sheetIndex,
        String sheetName,
        String originalName
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("fileId", fileId);
            if (previewLimit != null) {
                payload.put("previewLimit", previewLimit);
            }
            if (sheetIndex != null) {
                payload.put("sheetIndex", sheetIndex);
            }
            if (StringUtils.hasText(sheetName)) {
                payload.put("sheetName", sheetName);
            }
            if (StringUtils.hasText(originalName)) {
                payload.put("originalName", originalName);
            }
            return exchangeObject("/api/ingestion/files/parse", HttpMethod.POST, payload, null, longRestTemplate);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion file parse failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "文件解析失败", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion file parse error: {}", ex.getMessage());
            return new ApiResponse<>(500, "文件解析失败: " + ex.getMessage(), null);
        }
    }

    public ApiResponse<Object> rollbackAnalyze(Object request) {
        return exchangeObject("/api/ingestion/rollback/analyze", HttpMethod.POST, request, null, longRestTemplate);
    }

    public ApiResponse<Object> rollbackExecute(Object request) {
        return exchangeObject("/api/ingestion/rollback/execute", HttpMethod.POST, request, null, longRestTemplate);
    }

    public ApiResponse<Object> getRollbackAuditLog(Long taskId, java.util.UUID dataSourceId) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (taskId != null) {
            params.put("taskId", taskId.toString());
        }
        if (dataSourceId != null) {
            params.put("dataSourceId", dataSourceId.toString());
        }
        return exchangeObject("/api/ingestion/rollback/audit-log", HttpMethod.GET, null, params, restTemplate);
    }

    public Map<String, Object> getInfraSettings(String service) {
        if (!isEnabled() || !StringUtils.hasText(service)) {
            return Map.of();
        }
        ApiResponse<Object> response = exchangeObject("/api/infra/settings/" + service.trim(), HttpMethod.GET, null, null, restTemplate);
        if (response == null || response.getData() == null) {
            return Map.of();
        }
        Object data = response.getData();
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            Object nested = payload.get("settings");
            if (nested instanceof Map<?, ?> nestedMap) {
                Map<String, Object> settings = new java.util.LinkedHashMap<>();
                nestedMap.forEach((key, value) -> settings.put(String.valueOf(key), value));
                return settings;
            }
            return payload;
        }
        return Map.of();
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
        return doExchange(path, method, payload, params, client, (body, statusCode) -> {
            if (body instanceof Map<?, ?> map) {
                Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> payloadMap.put(String.valueOf(key), value));
                ApiResponse<Map<String, Object>> unwrapped = unwrapTaskResponseMap(payloadMap);
                if (unwrapped != null) {
                    return unwrapped;
                }
                if (statusCode.is2xxSuccessful()) {
                    return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), resolveFallbackMessage(payloadMap), payloadMap);
                }
                return new ApiResponse<>(statusCode.value(), resolveFallbackMessage(payloadMap), payloadMap);
            }
            if (statusCode.is2xxSuccessful()) {
                return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "ok", Map.of("value", body));
            }
            return new ApiResponse<>(statusCode.value(), "ok", Map.of("value", body));
        });
    }

    private ApiResponse<Object> exchangeObject(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        RestTemplate client
    ) {
        return doExchange(path, method, payload, params, client, (body, statusCode) -> {
            if (body instanceof Map<?, ?> map) {
                ApiResponse<Object> unwrapped = unwrapApiResponseMap(map);
                if (unwrapped != null) {
                    return unwrapped;
                }
                if (statusCode.is2xxSuccessful()) {
                    return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), resolveFallbackMessage(map), body);
                }
            }
            if (statusCode.is2xxSuccessful()) {
                return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "ok", body);
            }
            return new ApiResponse<>(statusCode.value(), "ok", body);
        });
    }

    /**
     * Shared exchange method that encapsulates circuit breaker decoration, retry, error handling,
     * and delegates response body unwrapping to the caller-supplied function.
     *
     * @param bodyHandler receives the non-null response body and HTTP status code, returns the final ApiResponse
     */
    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> doExchange(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        RestTemplate client,
        BiFunction<Object, org.springframework.http.HttpStatusCode, ApiResponse<T>> bodyHandler
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path, params);
        try {
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(defaultHeaders()) : new HttpEntity<>(payload, defaultHeaders());
            Supplier<ResponseEntity<Object>> supplier = () -> client.exchange(uri, method, entity, Object.class);
            ResponseEntity<Object> response = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, supplier)).get();
            Object body = response.getBody();
            if (body == null) {
                if (response.getStatusCode().is2xxSuccessful()) {
                    return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "OK", null);
                }
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion service empty response", null);
            }
            if (body instanceof ApiResponse<?> apiResponse) {
                return (ApiResponse<T>) apiResponse;
            }
            return bodyHandler.apply(body, response.getStatusCode());
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
            LOG.warn("Ingestion API {} blocked by circuit breaker (state=OPEN)", path);
            return new ApiResponse<>(503, "数据采集服务暂时不可用，请稍后重试", null);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof HttpStatusCodeException hsce) {
                LOG.warn("Ingestion API {} failed after retries status={}", path, hsce.getStatusCode().value());
                return new ApiResponse<>(hsce.getStatusCode().value(), "ingestion service error", null);
            }
            LOG.warn("Ingestion API {} error after retries: {}", path, cause.getMessage());
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String, Object>> unwrapTaskResponseMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        if (!map.containsKey("status") || !map.containsKey("data")) {
            return null;
        }
        int status = parseStatus(map.get("status"), ResultStatus.SUCCESS.getCode());
        String message = map.get("message") == null ? "ok" : String.valueOf(map.get("message"));
        Object data = map.get("data");
        Map<String, Object> dataMap;
        if (data instanceof Map<?, ?> dm) {
            Map<String, Object> tmp = new java.util.LinkedHashMap<>();
            dm.forEach((key, value) -> tmp.put(String.valueOf(key), value));
            dataMap = tmp;
        } else {
            dataMap = null;
        }
        ApiResponse<Map<String, Object>> response = new ApiResponse<>(status, message, dataMap);
        Object code = map.get("code");
        if (code != null) {
            String text = String.valueOf(code).trim();
            if (!text.isEmpty()) {
                response.setCode(text);
            }
        }
        return response;
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

    private String resolveFallbackMessage(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "ok";
        }
        Object message = map.get("message");
        if (message == null) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key != null && "message".equalsIgnoreCase(String.valueOf(key))) {
                    message = entry.getValue();
                    break;
                }
            }
        }
        if (message == null) {
            return "ok";
        }
        String text = String.valueOf(message).trim();
        return text.isEmpty() ? "ok" : text;
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
        SecurityUtils.getCurrentUserLogin()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .ifPresent(user -> headers.set(USER_HEADER, user));
        List<String> roles = SecurityUtils.getCurrentUserAuthorities()
            .stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (!roles.isEmpty()) {
            headers.set(ROLES_HEADER, String.join(",", roles));
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
