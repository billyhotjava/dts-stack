package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
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

    public AirflowClient(RestTemplateBuilder builder, AirflowProperties properties) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.properties = properties;
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
        } catch (Exception ex) {
            LOG.warn("Airflow dag trigger error: {}", ex.getMessage());
        }
        return Optional.empty();
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
        String apiPath = properties.getApiPath() == null ? "/api/v1" : properties.getApiPath();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(apiPath).path(path);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }
}
