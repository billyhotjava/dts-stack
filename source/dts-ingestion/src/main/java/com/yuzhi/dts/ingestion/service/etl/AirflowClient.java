package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.security.IngestionSensitiveConfigSupport;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
public class AirflowClient {

    private static final Logger LOG = LoggerFactory.getLogger(AirflowClient.class);

    /** Circuit breaker: consecutive failure count before tripping. */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    /** Circuit breaker: how long to stay open (fast-fail) before retrying. */
    private static final Duration CIRCUIT_BREAKER_COOLDOWN = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final AirflowProperties properties;
    private final IngestionSettingsService settingsService;

    // Simple circuit breaker state (atomic for thread safety)
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0L);

    public AirflowClient(RestTemplateBuilder builder, AirflowProperties properties, IngestionSettingsService settingsService) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(30)).build();
        this.properties = properties;
        this.settingsService = settingsService;
    }

    private boolean isCircuitOpen() {
        int failures = consecutiveFailures.get();
        if (failures < CIRCUIT_BREAKER_THRESHOLD) {
            return false;
        }
        if (System.currentTimeMillis() >= circuitOpenUntil.get()) {
            // Cooldown expired — allow one probe request (half-open)
            consecutiveFailures.compareAndSet(failures, CIRCUIT_BREAKER_THRESHOLD - 1);
            return false;
        }
        return true;
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntil.set(0L);
    }

    private void recordFailure() {
        int current = consecutiveFailures.incrementAndGet();
        if (current >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN.toMillis());
            LOG.warn("[airflow-circuit] circuit OPEN — {} consecutive failures, cooling down for {}s",
                current, CIRCUIT_BREAKER_COOLDOWN.toSeconds());
        }
    }

    public TriggerResult triggerDag(String dagId, Map<String, Object> payload) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            return new TriggerResult(false, 0, "Airflow 未启用或缺少 DAG", null);
        }
        if (isCircuitOpen()) {
            return new TriggerResult(false, -1, "Airflow 熔断中，请稍后重试", null);
        }
        URI uri = buildUri(settings, "/dags/" + dagId + "/dagRuns", null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            recordSuccess();
            return new TriggerResult(true, response.getStatusCode().value(), null, response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag trigger failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            // 4xx errors are Airflow-side issues, not connectivity — don't count toward circuit breaker
            if (ex.getStatusCode().is5xxServerError()) {
                recordFailure();
            }
            return new TriggerResult(false, ex.getStatusCode().value(), "Airflow DAG 触发失败", null);
        } catch (Exception ex) {
            LOG.warn("Airflow dag trigger error: {}", sanitized(ex.getMessage()));
            recordFailure();
            return new TriggerResult(false, -1, "Airflow DAG 触发调用失败", null);
        }
    }

    public Optional<Map<String, Object>> listDags(int limit) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl())) {
            return Optional.empty();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        URI uri = buildUri(settings, "/dags", Map.of("limit", safeLimit, "order_by", "dag_id"));
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag list failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            LOG.warn("Airflow dag list error: {}", sanitized(ex.getMessage()));
        }
        return Optional.empty();
    }

    public boolean deleteDag(String dagId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            return false;
        }
        URI uri = buildUri(settings, "/dags/" + dagId, null);
        try {
            HttpHeaders headers = deleteHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.DELETE, entity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            LOG.warn("Airflow DAG delete failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            throw new IllegalStateException("AIRFLOW_DAG_DELETE_HTTP_FAILED: status=" + ex.getStatusCode().value());
        } catch (Exception ex) {
            LOG.warn("Airflow DAG delete failed detail={}", sanitized(ex.getMessage()));
            throw new IllegalStateException("AIRFLOW_DAG_DELETE_FAILED");
        }
    }

    /** Strict lifecycle variant used by admission reconciliation; 404 is idempotent success. */
    public void deleteDagStrict(String dagId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            throw new IllegalStateException("Airflow DAG deletion is not configured: " + dagId);
        }
        URI uri = buildUri(settings, "/dags/" + dagId, null);
        try {
            HttpHeaders headers = deleteHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(uri, HttpMethod.DELETE, entity, Void.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return;
            }
            LOG.warn("Airflow DAG strict delete failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            throw new IllegalStateException(
                "AIRFLOW_DAG_DELETE_HTTP_FAILED: status=" + ex.getStatusCode().value()
            );
        } catch (Exception ex) {
            LOG.warn("Airflow DAG strict delete failed detail={}", sanitized(ex.getMessage()));
            throw new IllegalStateException("AIRFLOW_DAG_DELETE_FAILED");
        }
    }

    public void setDagPausedStrict(String dagId, boolean paused) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            throw new IllegalStateException("Airflow DAG state update is not configured: " + dagId);
        }
        URI uri = buildUri(settings, "/dags/" + dagId, null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("is_paused", paused), headers);
            restTemplate.exchange(uri, HttpMethod.PATCH, entity, Map.class);
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow DAG state update failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            throw new IllegalStateException(
                "AIRFLOW_DAG_STATE_UPDATE_HTTP_FAILED: status=" + ex.getStatusCode().value()
            );
        } catch (Exception ex) {
            LOG.warn("Airflow DAG state update failed detail={}", sanitized(ex.getMessage()));
            throw new IllegalStateException("AIRFLOW_DAG_STATE_UPDATE_FAILED");
        }
    }

    public Optional<Map<String, Object>> getDagRun(String dagId, String dagRunId) {
        DagRunLookupResult lookup = getDagRunLookup(dagId, dagRunId);
        if (!lookup.found()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lookup.dagRun());
    }

    public DagRunLookupResult getDagRunLookup(String dagId, String dagRunId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId)) {
            return DagRunLookupResult.disabled();
        }
        if (isCircuitOpen()) {
            return DagRunLookupResult.error(-1, "Airflow 熔断中");
        }
        URI uri = buildUri(settings, "/dags/" + dagId + "/dagRuns/" + dagRunId, null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            recordSuccess();
            return DagRunLookupResult.found(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                recordSuccess();
                LOG.debug("Airflow dag run not found body={}", sanitized(ex.getResponseBodyAsString()));
                return DagRunLookupResult.notFound(ex.getStatusCode().value(), "Airflow DAG run 不存在");
            }
            LOG.warn("Airflow dag run fetch failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            if (ex.getStatusCode().is5xxServerError()) {
                recordFailure();
            }
            return DagRunLookupResult.error(ex.getStatusCode().value(), "Airflow DAG run 查询失败");
        } catch (Exception ex) {
            LOG.warn("Airflow dag run fetch error: {}", sanitized(ex.getMessage()));
            recordFailure();
            return DagRunLookupResult.error(-1, "Airflow DAG run 查询调用失败");
        }
    }

    public Optional<Map<String, Object>> listDagRuns(String dagId, int limit) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        URI uri = buildUri(settings, "/dags/" + dagId + "/dagRuns", Map.of("limit", safeLimit, "order_by", "-start_date"));
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag runs list failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            LOG.warn("Airflow dag runs list error: {}", sanitized(ex.getMessage()));
        }
        return Optional.empty();
    }

    public Optional<String> getTaskLog(String dagId, String dagRunId, String taskId, int tryNumber) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled()
            || !StringUtils.hasText(settings.baseUrl())
            || !StringUtils.hasText(dagId)
            || !StringUtils.hasText(dagRunId)
            || !StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        int resolvedTry = Math.max(1, tryNumber);
        URI uri = buildUri(
            settings,
            "/dags/" + dagId + "/dagRuns/" + dagRunId + "/taskInstances/" + taskId + "/logs/" + resolvedTry,
            Map.of("full_content", true)
        );
        try {
            HttpHeaders headers = defaultHeaders(settings);
            headers.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                LOG.debug("Airflow task log not ready dag={} run={} task={} try={} body={}", dagId, dagRunId, taskId, resolvedTry, sanitized(ex.getResponseBodyAsString()));
            } else {
                LOG.warn("Airflow task log fetch failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            }
        } catch (Exception ex) {
            LOG.warn("Airflow task log fetch error: {}", sanitized(ex.getMessage()));
        }
        return Optional.empty();
    }

    public Optional<List<Map<String, Object>>> listTaskInstances(String dagId, String dagRunId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled()
            || !StringUtils.hasText(settings.baseUrl())
            || !StringUtils.hasText(dagId)
            || !StringUtils.hasText(dagRunId)) {
            return Optional.empty();
        }
        URI uri = buildUri(
            settings,
            "/dags/" + dagId + "/dagRuns/" + dagRunId + "/taskInstances",
            Map.of("limit", 200)
        );
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            Map body = response.getBody();
            return Optional.of(extractMapList(body, "task_instances"));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                LOG.debug("Airflow task instances not ready dag={} run={} body={}", dagId, dagRunId, sanitized(ex.getResponseBodyAsString()));
            } else {
                LOG.warn("Airflow task instance list failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            }
        } catch (Exception ex) {
            LOG.warn("Airflow task instance list error: {}", sanitized(ex.getMessage()));
        }
        return Optional.empty();
    }

    public Optional<List<Map<String, Object>>> listImportErrors(int limit) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl())) {
            return Optional.empty();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        URI uri = buildUri(settings, "/importErrors", Map.of("limit", safeLimit));
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            Map body = response.getBody();
            return Optional.of(extractMapList(body, "import_errors"));
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow import errors fetch failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            LOG.warn("Airflow import errors fetch error: {}", sanitized(ex.getMessage()));
        }
        return Optional.empty();
    }

    /**
     * Check whether a DAG is already registered in Airflow (single non-blocking call).
     */
    public boolean isDagRegistered(String dagId) {
        return dagExists(dagId);
    }

    public boolean waitForDag(String dagId, Duration timeout, Duration interval) {
        if (!StringUtils.hasText(dagId)) {
            return false;
        }
        Duration safeTimeout = timeout == null ? Duration.ZERO : timeout;
        Duration safeInterval = interval == null ? Duration.ofSeconds(1) : interval;
        long deadline = System.currentTimeMillis() + safeTimeout.toMillis();
        do {
            if (dagExists(dagId)) {
                return true;
            }
            if (safeTimeout.isZero() || safeTimeout.isNegative()) {
                return false;
            }
            try {
                Thread.sleep(Math.max(250L, safeInterval.toMillis()));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return dagExists(dagId);
    }

    private boolean dagExists(String dagId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl())) {
            return false;
        }
        if (isCircuitOpen()) {
            return false;
        }
        URI uri = buildUri(settings, "/dags/" + dagId, null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            recordSuccess();
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                recordSuccess(); // 404 = Airflow is reachable, DAG just not there yet
                return false;
            }
            LOG.warn("Airflow dag check failed status={} body={}", ex.getStatusCode().value(), sanitized(ex.getResponseBodyAsString()));
            if (ex.getStatusCode().is5xxServerError()) {
                recordFailure();
            }
        } catch (Exception ex) {
            LOG.warn("Airflow dag check error: {}", sanitized(ex.getMessage()));
            recordFailure();
        }
        return false;
    }

    private HttpHeaders defaultHeaders(AirflowSettings settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(settings.username())) {
            String token = settings.username() + ":" + String.valueOf(settings.password());
            String encoded = java.util.Base64.getEncoder().encodeToString(token.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private HttpHeaders deleteHeaders(AirflowSettings settings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(settings.username())) {
            String token = settings.username() + ":" + String.valueOf(settings.password());
            String encoded = java.util.Base64.getEncoder().encodeToString(token.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private URI buildUri(AirflowSettings settings, String path, Map<String, ?> params) {
        String base = settings.baseUrl();
        String apiPath = settings.apiPath() == null ? "/api/v1" : settings.apiPath();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(apiPath).path(path);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private AirflowSettings resolveSettings() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW);
        boolean enabled = settings.getBoolean("enabled", properties.isEnabled());
        String baseUrl = settings.getString("baseUrl", properties.getBaseUrl());
        String apiPath = settings.getString("apiPath", properties.getApiPath());
        String username = settings.getString("username", properties.getUsername());
        String password = settings.getString("password", properties.getPassword());
        String fallbackUser = properties.getUsername();
        String fallbackPass = properties.getPassword();
        if ("airflow".equals(username) && StringUtils.hasText(fallbackUser) && !fallbackUser.equals(username)) {
            username = fallbackUser;
        }
        if ("airflow".equals(password) && StringUtils.hasText(fallbackPass) && !fallbackPass.equals(password)) {
            password = fallbackPass;
        }
        return new AirflowSettings(enabled, baseUrl, apiPath, username, password);
    }

    private String sanitized(String value) {
        return IngestionSensitiveConfigSupport.sanitizeText(value);
    }

    private List<Map<String, Object>> extractMapList(Map body, String key) {
        if (body == null || !(body.get(key) instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        converted.put(k.toString(), v);
                    }
                });
                result.add(converted);
            }
        }
        return result;
    }

    public record TriggerResult(boolean success, int statusCode, String message, Map<String, Object> payload) {}

    public record DagRunLookupResult(Map<String, Object> dagRun, int statusCode, String message) {
        public static DagRunLookupResult disabled() {
            return new DagRunLookupResult(Map.of(), 0, null);
        }

        public static DagRunLookupResult found(int statusCode, Map<String, Object> dagRun) {
            return new DagRunLookupResult(dagRun == null ? Map.of() : dagRun, statusCode, null);
        }

        public static DagRunLookupResult notFound(int statusCode, String message) {
            return new DagRunLookupResult(Map.of(), statusCode, message);
        }

        public static DagRunLookupResult error(int statusCode, String message) {
            return new DagRunLookupResult(Map.of(), statusCode, message);
        }

        public boolean found() {
            return dagRun != null && !dagRun.isEmpty();
        }

        public boolean notFound() {
            return statusCode == 404;
        }
    }

    private record AirflowSettings(boolean enabled, String baseUrl, String apiPath, String username, String password) {}
}
