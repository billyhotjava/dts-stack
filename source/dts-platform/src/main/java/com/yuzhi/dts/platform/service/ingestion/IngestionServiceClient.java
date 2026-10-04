package com.yuzhi.dts.platform.service.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String USER_HEADER = "X-DTS-User";
    private static final String ROLES_HEADER = "X-DTS-Roles";
    private static final String UPLOAD_TRACE_HEADER = "X-DTS-Upload-Trace";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UNKNOWN_REQUEST_ID = "unknown";
    private static final Pattern STABLE_LOG_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
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
        RestTemplateBuilder baseBuilder = builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .additionalInterceptors((request, body, execution) -> {
                if (StringUtils.hasText(properties.getServiceToken())) {
                    request.getHeaders().set(SERVICE_TOKEN_HEADER, properties.getServiceToken().trim());
                }
                return execution.execute(request, body);
            });
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
            .onRetry(event -> logFailure(
                "INGESTION_RETRY",
                statusCode(event.getLastThrowable(), 503),
                responseRequestId(event.getLastThrowable(), UNKNOWN_REQUEST_ID)
            ));

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

    public ApiResponse<Object> getAccessDefaultPolicy() {
        return exchangeObject("/api/ingestion/access/default-policy", HttpMethod.GET, null, null, restTemplate);
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

    public ApiResponse<Map<String, Object>> getTaskAccessMetadata(Long id) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri("/api/ingestion/tasks/" + id);
        String requestId = UUID.randomUUID().toString();
        try {
            HttpEntity<?> entity = new HttpEntity<>(headersWithRequestId(requestId));
            Supplier<ResponseEntity<TaskAccessProjection>> supplier = () ->
                restTemplate.exchange(uri, HttpMethod.GET, entity, TaskAccessProjection.class);
            Supplier<ResponseEntity<TaskAccessProjection>> circuitProtected = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                supplier
            );
            ResponseEntity<TaskAccessProjection> response = Retry.decorateSupplier(retry, circuitProtected).get();
            TaskAccessProjection body = response.getBody();
            if (body == null) {
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion task access metadata unavailable", null);
            }
            return new ApiResponse<>(response.getStatusCode().value(), "ok", body.toMap());
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
            logFailure("INGESTION_TASK_ACCESS_CIRCUIT_OPEN", 503, requestId);
            return new ApiResponse<>(503, "数据采集服务暂时不可用，请稍后重试", null);
        } catch (HttpStatusCodeException ex) {
            logFailure(
                "INGESTION_TASK_ACCESS_HTTP_ERROR",
                ex.getStatusCode().value(),
                responseRequestId(ex, requestId)
            );
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            logFailure("INGESTION_TASK_ACCESS_ERROR", statusCode(ex, 500), responseRequestId(ex, requestId));
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    public ApiResponse<Object> getTaskRevisions(Long id) {
        return exchangeObject("/api/ingestion/tasks/" + id + "/revisions", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Object> getTaskEffectiveConfig(Long id) {
        return exchangeObject("/api/ingestion/tasks/" + id + "/effective-config", HttpMethod.GET, null, null, restTemplate);
    }

    public ApiResponse<Map<String, Object>> getTaskDesign(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id + "/design", HttpMethod.GET, null, null);
    }

    public ApiResponse<Map<String, Object>> updateTaskDesign(Long id, Object payload, String expectedPlanChecksum) {
        Map<String, String> headers = StringUtils.hasText(expectedPlanChecksum)
            ? Map.of("If-Match", expectedPlanChecksum)
            : Map.of();
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + id + "/design",
            HttpMethod.PUT,
            payload,
            null,
            headers
        );
    }

    public ApiResponse<Map<String, Object>> validateTaskDesign(Long id, Object payload, String expectedPlanChecksum) {
        Map<String, String> headers = StringUtils.hasText(expectedPlanChecksum)
            ? Map.of("X-Expected-Plan-Checksum", expectedPlanChecksum)
            : Map.of();
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + id + "/design/validate",
            HttpMethod.POST,
            payload,
            null,
            headers
        );
    }

    public ApiResponse<Map<String, Object>> getTaskTopology(Long id) {
        return getTaskTopology(id, "DRAFT");
    }

    public ApiResponse<Map<String, Object>> getTaskTopology(Long id, String view) {
        Map<String, Object> params = StringUtils.hasText(view) ? Map.of("view", view) : Map.of();
        return exchangeTask("/api/ingestion/tasks/" + id + "/topology", HttpMethod.GET, null, params);
    }

    public ApiResponse<Map<String, Object>> setTaskSchedulePaused(Long id, boolean paused) {
        return exchangeTask(
            "/api/ingestion/tasks/" + id + "/schedule/" + (paused ? "pause" : "enable"),
            HttpMethod.POST,
            null,
            null
        );
    }

    public ApiResponse<Map<String, Object>> updateTask(Long id, Object payload) {
        return exchangeTask("/api/ingestion/tasks/" + id, HttpMethod.PUT, payload, null);
    }

    public ApiResponse<Map<String, Object>> admitTask(Long id, Object payload) {
        return exchangeTask("/api/ingestion/tasks/" + id + "/admit", HttpMethod.POST, payload, null);
    }

    public ApiResponse<Map<String, Object>> admitTask(Long id, Object payload, String expectedPlanChecksum) {
        Map<String, String> headers = StringUtils.hasText(expectedPlanChecksum)
            ? Map.of("X-Expected-Plan-Checksum", expectedPlanChecksum)
            : Map.of();
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + id + "/admit",
            HttpMethod.POST,
            payload,
            null,
            headers
        );
    }

    public ApiResponse<Map<String, Object>> deleteTask(Long id) {
        return exchangeTask("/api/ingestion/tasks/" + id, HttpMethod.DELETE, null, null);
    }

    public ApiResponse<Map<String, Object>> executeTask(Long id) {
        return exchangeTaskLong("/api/ingestion/tasks/" + id + "/execute", HttpMethod.POST, null);
    }

    public ApiResponse<Map<String, Object>> executeTask(Long id, String idempotencyKey) {
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + id + "/execute",
            HttpMethod.POST,
            null,
            null,
            Map.of("Idempotency-Key", idempotencyKey)
        );
    }

    public ApiResponse<Map<String, Object>> executeTaskAsync(Long id) {
        return exchangeTaskLong("/api/ingestion/tasks/" + id + "/execute/async", HttpMethod.POST, null);
    }

    public ApiResponse<Map<String, Object>> executeTaskAsync(Long id, String idempotencyKey) {
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + id + "/execute/async",
            HttpMethod.POST,
            null,
            null,
            Map.of("Idempotency-Key", idempotencyKey)
        );
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

    public ApiResponse<Map<String, Object>> getExecution(Long taskId, Long executionId) {
        return exchangeTask(
            "/api/ingestion/tasks/" + taskId + "/executions/" + executionId,
            HttpMethod.GET,
            null,
            null
        );
    }

    public ApiResponse<Map<String, Object>> cancelExecution(Long taskId, Long executionId) {
        return exchangeTask(
            "/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/cancel",
            HttpMethod.POST,
            null,
            null
        );
    }

    public ApiResponse<Map<String, Object>> retryExecution(Long taskId, Long executionId, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/retry", HttpMethod.POST, null, params);
    }

    public ApiResponse<Map<String, Object>> retryExecutionAsync(Long taskId, Long executionId, Map<String, ?> params) {
        return exchangeTask("/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/retry/async", HttpMethod.POST, null, params);
    }

    public ApiResponse<Map<String, Object>> retryExecutionAsync(
        Long taskId,
        Long executionId,
        Map<String, ?> params,
        String idempotencyKey
    ) {
        return exchangeTaskWithHeaders(
            "/api/ingestion/tasks/" + taskId + "/executions/" + executionId + "/retry/async",
            HttpMethod.POST,
            null,
            params,
            Map.of("Idempotency-Key", idempotencyKey)
        );
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

    public ApiResponse<Object> testApiConnection(Object payload) {
        return exchangeObject("/api/ingestion/api/test-connection", HttpMethod.POST, payload, null, restTemplate);
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
        String requestId = UUID.randomUUID().toString();
        try {
            return longRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headersWithRequestId(requestId)),
                byte[].class
            );
        } catch (HttpStatusCodeException ex) {
            logFailure(
                "INGESTION_STAGING_DOWNLOAD_HTTP_ERROR",
                ex.getStatusCode().value(),
                responseRequestId(ex, requestId)
            );
            return ResponseEntity.status(ex.getStatusCode()).body(null);
        } catch (Exception ex) {
            logFailure("INGESTION_STAGING_DOWNLOAD_ERROR", statusCode(ex, 500), responseRequestId(ex, requestId));
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
        String traceId = UUID.randomUUID().toString();
        String originalName = file == null ? null : file.getOriginalFilename();
        Long declaredSize = file == null ? null : file.getSize();
        String contentType = file == null ? null : file.getContentType();
        if (!isEnabled()) {
            LOG.warn(
                "Ingestion file upload proxy disabled: traceId={}, path={}, enabled={}, baseUrlSet={}, name={}, size={}, contentType={}",
                traceId,
                path,
                properties.isEnabled(),
                StringUtils.hasText(properties.getBaseUrl()),
                originalName,
                declaredSize,
                contentType
            );
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(UPLOAD_TRACE_HEADER, traceId);
            headers.set(REQUEST_ID_HEADER, traceId);
            if (StringUtils.hasText(properties.getServiceName())) {
                headers.set(SERVICE_HEADER, properties.getServiceName());
            }
            LOG.info(
                "Ingestion file upload proxy start: traceId={}, path={}, target={}, name={}, size={}, contentType={}, previewLimit={}, sheetIndex={}, sheetName={}, serviceHeaderSet={}",
                traceId,
                path,
                uri,
                originalName,
                declaredSize,
                contentType,
                previewLimit,
                sheetIndex,
                sheetName,
                StringUtils.hasText(properties.getServiceName())
            );
            byte[] fileBytes = readMultipartBytes(file, traceId, path, originalName, declaredSize, contentType);
            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalName;
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
                    logResponse("INGESTION_UPLOAD_RESPONSE", unwrapped.getStatus(), traceId);
                    return unwrapped;
                }
            }
            logResponse("INGESTION_UPLOAD_RESPONSE", response.getStatusCode().value(), traceId);
            return new ApiResponse<>(response.getStatusCode().value(), "ok", responseBody);
        } catch (HttpStatusCodeException ex) {
            logFailure("INGESTION_UPLOAD_HTTP_ERROR", ex.getStatusCode().value(), responseRequestId(ex, traceId));
            return new ApiResponse<>(ex.getStatusCode().value(), "文件上传失败", null);
        } catch (Exception ex) {
            logFailure("INGESTION_UPLOAD_ERROR", statusCode(ex, 500), responseRequestId(ex, traceId));
            return new ApiResponse<>(500, "文件上传失败", null);
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
        String requestId = UUID.randomUUID().toString();
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
            logFailure("INGESTION_FILE_PARSE_HTTP_ERROR", ex.getStatusCode().value(), responseRequestId(ex, requestId));
            return new ApiResponse<>(ex.getStatusCode().value(), "文件解析失败", null);
        } catch (Exception ex) {
            logFailure("INGESTION_FILE_PARSE_ERROR", statusCode(ex, 500), responseRequestId(ex, requestId));
            return new ApiResponse<>(500, "文件解析失败", null);
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

    private ApiResponse<Map<String, Object>> exchangeTaskWithHeaders(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        Map<String, String> forwardHeaders
    ) {
        return doExchange(path, method, payload, params, restTemplate, forwardHeaders, (body, statusCode) -> {
            if (body instanceof Map<?, ?> map) {
                Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> payloadMap.put(String.valueOf(key), value));
                ApiResponse<Map<String, Object>> unwrapped = unwrapTaskResponseMap(payloadMap);
                if (unwrapped != null) {
                    return unwrapped;
                }
                return new ApiResponse<>(
                    statusCode.is2xxSuccessful() ? ResultStatus.SUCCESS.getCode() : statusCode.value(),
                    resolveFallbackMessage(payloadMap),
                    payloadMap
                );
            }
            return new ApiResponse<>(statusCode.value(), "ok", Map.of("value", body));
        });
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
        return doExchange(path, method, payload, params, client, Map.of(), bodyHandler);
    }

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> doExchange(
        String path,
        HttpMethod method,
        Object payload,
        Map<String, ?> params,
        RestTemplate client,
        Map<String, String> forwardHeaders,
        BiFunction<Object, org.springframework.http.HttpStatusCode, ApiResponse<T>> bodyHandler
    ) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path, params);
        String requestId = UUID.randomUUID().toString();
        try {
            HttpHeaders headers = headersWithRequestId(requestId);
            applyForwardHeaders(headers, forwardHeaders);
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
            Supplier<ResponseEntity<Object>> supplier = () -> client.exchange(uri, method, entity, Object.class);
            Supplier<ResponseEntity<Object>> circuitProtected = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
            ResponseEntity<Object> response = (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))
                ? Retry.decorateSupplier(retry, circuitProtected).get()
                : circuitProtected.get();
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
            logFailure("INGESTION_CIRCUIT_OPEN", 503, requestId);
            return new ApiResponse<>(503, "数据采集服务暂时不可用，请稍后重试", null);
        } catch (HttpStatusCodeException ex) {
            logFailure("INGESTION_HTTP_ERROR", ex.getStatusCode().value(), responseRequestId(ex, requestId));
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof HttpStatusCodeException hsce) {
                logFailure(
                    "INGESTION_HTTP_ERROR_AFTER_RETRY",
                    hsce.getStatusCode().value(),
                    responseRequestId(hsce, requestId)
                );
                return new ApiResponse<>(hsce.getStatusCode().value(), "ingestion service error", null);
            }
            logFailure("INGESTION_REQUEST_ERROR", statusCode(cause, 500), responseRequestId(cause, requestId));
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    private void applyForwardHeaders(HttpHeaders headers, Map<String, String> forwardHeaders) {
        if (forwardHeaders == null || forwardHeaders.isEmpty()) {
            return;
        }
        for (String name : List.of("If-Match", "X-Expected-Plan-Checksum", "Idempotency-Key")) {
            String value = forwardHeaders.get(name);
            if (StringUtils.hasText(value)) {
                headers.set(name, value.trim());
            }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TaskAccessProjection(
        Long id,
        String sourceKind,
        String sourceType,
        UUID sourceDataSourceId,
        UUID targetDataSourceId,
        UUID destinationDataSourceId,
        TaskAccessConfigProjection sourceConfig,
        TaskAccessConfigProjection destinationConfig,
        ClassificationSealProjection classificationSeal
    ) {
        private Map<String, Object> toMap() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            putIfNotNull(metadata, "id", id);
            putIfNotNull(metadata, "sourceKind", sourceKind);
            putIfNotNull(metadata, "sourceType", sourceType);
            putIfNotNull(metadata, "sourceDataSourceId", sourceDataSourceId == null ? null : sourceDataSourceId.toString());
            putIfNotNull(metadata, "targetDataSourceId", uuidText(targetDataSourceId));
            putIfNotNull(metadata, "destinationDataSourceId", uuidText(destinationDataSourceId));
            if (sourceConfig != null) {
                metadata.put("sourceConfig", sourceConfig.toMap());
            }
            if (destinationConfig != null) {
                metadata.put("destinationConfig", destinationConfig.toMap());
            }
            if (classificationSeal != null) {
                metadata.put("classificationSeal", classificationSeal.toMap());
            }
            return metadata;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TaskAccessConfigProjection(
        UUID sourceDataSourceId,
        UUID dataSourceId,
        UUID targetDataSourceId,
        UUID destinationDataSourceId,
        String readerType,
        @JsonProperty("_fileId") String fileId,
        ClassificationSealProjection classificationSeal
    ) {
        private Map<String, Object> toMap() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            putIfNotNull(metadata, "sourceDataSourceId", uuidText(sourceDataSourceId));
            putIfNotNull(metadata, "dataSourceId", uuidText(dataSourceId));
            putIfNotNull(metadata, "targetDataSourceId", uuidText(targetDataSourceId));
            putIfNotNull(metadata, "destinationDataSourceId", uuidText(destinationDataSourceId));
            putIfNotNull(metadata, "readerType", readerType);
            putIfNotNull(metadata, "_fileId", fileId);
            if (classificationSeal != null) {
                metadata.put("classificationSeal", classificationSeal.toMap());
            }
            return metadata;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClassificationSealProjection(String effectiveLevel, String fileFloor) {
        private Map<String, Object> toMap() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            putIfNotNull(metadata, "effectiveLevel", effectiveLevel);
            putIfNotNull(metadata, "fileFloor", fileFloor);
            return metadata;
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String uuidText(UUID value) {
        return value == null ? null : value.toString();
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

    private byte[] readMultipartBytes(
        org.springframework.web.multipart.MultipartFile file,
        String traceId,
        String path,
        String originalName,
        Long declaredSize,
        String contentType
    ) {
        if (file == null) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            LOG.info(
                "Ingestion file upload proxy read multipart: traceId={}, path={}, name={}, declaredSize={}, actualSize={}, contentType={}",
                traceId,
                path,
                originalName,
                declaredSize,
                bytes.length,
                contentType
            );
            return bytes;
        } catch (Exception ex) {
            logFailure("INGESTION_MULTIPART_READ_ERROR", statusCode(ex, 500), traceId);
            throw new IllegalStateException("读取上传文件失败", ex);
        }
    }

    private HttpHeaders headersWithRequestId(String requestId) {
        HttpHeaders headers = defaultHeaders();
        headers.set(REQUEST_ID_HEADER, stableLogValue(requestId, UNKNOWN_REQUEST_ID));
        return headers;
    }

    /**
     * Security boundary for downstream failures: response bodies and exception messages never reach logs.
     * Only an allowlisted, single-line projection is emitted.
     */
    private static void logFailure(String code, int status, String requestId) {
        LOG.warn(
            "[ingestion-client] code={} status={} requestId={}",
            stableLogValue(code, "INGESTION_ERROR"),
            status,
            stableLogValue(requestId, UNKNOWN_REQUEST_ID)
        );
    }

    private static void logResponse(String code, int status, String requestId) {
        LOG.info(
            "[ingestion-client] code={} status={} requestId={}",
            stableLogValue(code, "INGESTION_RESPONSE"),
            status,
            stableLogValue(requestId, UNKNOWN_REQUEST_ID)
        );
    }

    private static int statusCode(Throwable error, int fallback) {
        if (error instanceof HttpStatusCodeException statusError) {
            return statusError.getStatusCode().value();
        }
        Throwable cause = error == null ? null : error.getCause();
        if (cause instanceof HttpStatusCodeException statusError) {
            return statusError.getStatusCode().value();
        }
        return fallback;
    }

    private static String responseRequestId(Throwable error, String fallback) {
        HttpStatusCodeException statusError = null;
        if (error instanceof HttpStatusCodeException direct) {
            statusError = direct;
        } else if (error != null && error.getCause() instanceof HttpStatusCodeException nested) {
            statusError = nested;
        }
        if (statusError != null && statusError.getResponseHeaders() != null) {
            String downstreamRequestId = statusError.getResponseHeaders().getFirst(REQUEST_ID_HEADER);
            if (StringUtils.hasText(downstreamRequestId)) {
                return stableLogValue(downstreamRequestId, fallback);
            }
        }
        return stableLogValue(fallback, UNKNOWN_REQUEST_ID);
    }

    private static String stableLogValue(String value, String fallback) {
        String candidate = value == null ? "" : value.trim();
        return STABLE_LOG_VALUE.matcher(candidate).matches() ? candidate : fallback;
    }

}
