package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final RestTemplate restTemplate;
    private final AirflowProperties properties;
    private final ObjectMapper objectMapper;

    public AirflowClient(RestTemplateBuilder builder, AirflowProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> triggerDag(String dagId, Map<String, Object> payload) {
        if (!properties.isEnabled() || !StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/dags/" + dagId + "/dagRuns");
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag trigger failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new RuntimeException("Airflow DAG 触发失败 (" + dagId + "): " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            LOG.warn("Airflow dag trigger error: {}", ex.getMessage());
            throw new RuntimeException("Airflow DAG 触发失败 (" + dagId + "): " + ex.getMessage(), ex);
        }
    }

    public Optional<Map<String, Object>> listDagRuns(String dagId, int limit) {
        if (!properties.isEnabled() || !StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/dags/" + dagId + "/dagRuns", Map.of("order_by", "-execution_date", "limit", limit));
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag runs list failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag runs list error: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Looks up one deterministic DagRun without falling back to a list scan.
     *
     * <p>A 404 is a normal empty result. Connectivity and other HTTP failures remain explicit so
     * dispatch reconciliation cannot mistake "Airflow unavailable" for "run does not exist".
     */
    public Optional<Map<String, Object>> getDagRun(
        String dagId,
        String dagRunId
    ) {
        if (
            !properties.isEnabled() ||
            !StringUtils.hasText(dagId) ||
            !StringUtils.hasText(dagRunId)
        ) {
            return Optional.empty();
        }
        URI uri = buildUri(
            "/dags/" + dagId.trim() + "/dagRuns/" + dagRunId.trim()
        );
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                Map.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException failure) {
            if (failure.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new AirflowApiException(
                "查询 DAG Run 失败 (" +
                dagId +
                "/" +
                dagRunId +
                "): HTTP " +
                failure.getStatusCode().value(),
                failure
            );
        } catch (RuntimeException failure) {
            throw new AirflowApiException(
                "查询 DAG Run 失败 (" +
                dagId +
                "/" +
                dagRunId +
                ")",
                failure
            );
        }
    }

    public Optional<Map<String, Object>> listDags(int limit) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        URI uri = buildUri("/dags", Map.of("limit", safeLimit, "order_by", "dag_id"));
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag list failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag list error: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> setDagPaused(String dagId, boolean paused) {
        if (!properties.isEnabled() || !StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/dags/" + dagId);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("is_paused", paused), headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.PATCH, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag state update failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag state update error: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> getDag(String dagId) {
        if (!properties.isEnabled() || !StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/dags/" + dagId);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return Optional.of(resp.getBody());
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty(); // DAG not found yet
            }
            LOG.warn("Airflow getDag failed for {}: status={}", dagId, ex.getStatusCode());
            throw new AirflowApiException(
                "查询 DAG 失败 (" + dagId + "): " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(),
                ex
            );
        } catch (Exception ex) {
            LOG.warn("Airflow getDag error for {}: {}", dagId, ex.getMessage());
            throw new AirflowApiException("查询 DAG 失败 (" + dagId + "): " + ex.getMessage(), ex);
        }
        return Optional.empty();
    }

    public static class AirflowApiException extends RuntimeException {

        public AirflowApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Fetch task instance log from Airflow REST API.
     * GET /api/v1/dags/{dagId}/dagRuns/{dagRunId}/taskInstances/{taskId}/logs/{tryNumber}
     */
    public String getTaskInstanceLog(String dagId, String dagRunId, String taskId, int tryNumber) {
        if (!properties.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId) || !StringUtils.hasText(taskId)) {
            return null;
        }
        int safeTry = Math.max(1, tryNumber);
        String path = "/dags/" + dagId + "/dagRuns/" + dagRunId + "/taskInstances/" + taskId + "/logs/" + safeTry;
        URI uri = buildUri(path);
        try {
            HttpHeaders headers = defaultHeaders();
            headers.setAccept(List.of(MediaType.TEXT_PLAIN));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow task log fetch failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return "[error] Airflow returned status " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString();
        } catch (Exception ex) {
            LOG.warn("Airflow task log fetch error: {}", ex.getMessage());
            return "[error] Failed to fetch log: " + ex.getMessage();
        }
    }

    /**
     * List task instances for a DAG run.
     * GET /api/v1/dags/{dagId}/dagRuns/{dagRunId}/taskInstances
     */
    public JsonNode listTaskInstances(String dagId, String dagRunId) {
        if (!properties.isEnabled()) {
            return objectMapper.createObjectNode();
        }
        if (!StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId)) {
            return objectMapper.createObjectNode();
        }
        String path = "/dags/" + dagId + "/dagRuns/" + dagRunId + "/taskInstances";
        URI uri = buildUri(path);
        try {
            HttpHeaders headers = defaultHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody() != null ? response.getBody() : "{}");
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow listTaskInstances failed status={}", ex.getStatusCode().value());
            return objectMapper.createObjectNode();
        } catch (Exception ex) {
            LOG.warn("Airflow listTaskInstances error: {}", ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getUsername())) {
            String token = properties.getUsername() + ":" + String.valueOf(properties.getPassword());
            String encoded = java.util.Base64.getEncoder().encodeToString(token.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private URI buildUri(String path) {
        return buildUri(path, null);
    }

    private URI buildUri(String path, Map<String, ?> params) {
        String base = properties.getBaseUrl();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path("/api/v1").path(path);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }
}
