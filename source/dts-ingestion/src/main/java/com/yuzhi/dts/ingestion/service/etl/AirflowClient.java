package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
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
    private final IngestionSettingsService settingsService;

    public AirflowClient(RestTemplateBuilder builder, AirflowProperties properties, IngestionSettingsService settingsService) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.properties = properties;
        this.settingsService = settingsService;
    }

    public TriggerResult triggerDag(String dagId, Map<String, Object> payload) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId)) {
            return new TriggerResult(false, 0, "Airflow 未启用或缺少 DAG", null);
        }
        URI uri = buildUri(settings, "/dags/" + dagId + "/dagRuns", null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            return new TriggerResult(true, response.getStatusCode().value(), null, response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag trigger failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new TriggerResult(false, ex.getStatusCode().value(), ex.getResponseBodyAsString(), null);
        } catch (Exception ex) {
            LOG.warn("Airflow dag trigger error: {}", ex.getMessage());
            return new TriggerResult(false, -1, ex.getMessage(), null);
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
            LOG.warn("Airflow dag list failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag list error: {}", ex.getMessage());
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
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.DELETE, entity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404) {
                LOG.warn("Airflow dag delete failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            }
        } catch (Exception ex) {
            LOG.warn("Airflow dag delete error: {}", ex.getMessage());
        }
        return false;
    }

    public Optional<Map<String, Object>> getDagRun(String dagId, String dagRunId) {
        AirflowSettings settings = resolveSettings();
        if (!settings.enabled() || !StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(dagId) || !StringUtils.hasText(dagRunId)) {
            return Optional.empty();
        }
        URI uri = buildUri(settings, "/dags/" + dagId + "/dagRuns/" + dagRunId, null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airflow dag run fetch failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag run fetch error: {}", ex.getMessage());
        }
        return Optional.empty();
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
        URI uri = buildUri(settings, "/dags/" + dagId, null);
        try {
            HttpHeaders headers = defaultHeaders(settings);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            LOG.warn("Airflow dag check failed status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airflow dag check error: {}", ex.getMessage());
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

    public record TriggerResult(boolean success, int statusCode, String message, Map<String, Object> payload) {}

    private record AirflowSettings(boolean enabled, String baseUrl, String apiPath, String username, String password) {}
}
