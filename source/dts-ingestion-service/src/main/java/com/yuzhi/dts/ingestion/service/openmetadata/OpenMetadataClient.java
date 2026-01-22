package com.yuzhi.dts.ingestion.service.openmetadata;

import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
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
public class OpenMetadataClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMetadataClient.class);

    private final RestTemplate restTemplate;
    private final OpenMetadataProperties props;

    public OpenMetadataClient(RestTemplateBuilder builder, OpenMetadataProperties props) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.props = props;
    }

    public Optional<Map<String, Object>> getTableByFqn(String fqn, String fields) {
        if (!StringUtils.hasText(fqn) || !props.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri("/tables/name/" + fqn, fields);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && !body.isEmpty()) {
                return Optional.of(body);
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404) {
                LOG.debug(
                    "OpenMetadata table lookup failed status={} uri={} body={}",
                    ex.getStatusCode().value(),
                    uri,
                    trim(ex.getResponseBodyAsString(), 256)
                );
            }
        } catch (Exception ex) {
            LOG.debug("OpenMetadata table lookup error: {}", ex.getMessage());
            LOG.trace("OpenMetadata table lookup stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> upsertLineage(String fromId, String toId, String description) {
        if (!props.isEnabled() || !StringUtils.hasText(fromId) || !StringUtils.hasText(toId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/lineage", null);
        Map<String, Object> edge = new java.util.LinkedHashMap<>();
        edge.put("fromEntity", Map.of("id", fromId, "type", "table"));
        edge.put("toEntity", Map.of("id", toId, "type", "table"));
        if (StringUtils.hasText(description)) {
            edge.put("description", description.trim());
        }
        Map<String, Object> payload = Map.of("edge", edge);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "OpenMetadata lineage update failed status={} uri={} body={}",
                ex.getStatusCode().value(),
                uri,
                trim(ex.getResponseBodyAsString(), 256)
            );
        } catch (Exception ex) {
            LOG.debug("OpenMetadata lineage update error: {}", ex.getMessage());
            LOG.trace("OpenMetadata lineage update stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> getDatabaseServiceByName(String name) {
        if (!props.isEnabled() || !StringUtils.hasText(name)) {
            return Optional.empty();
        }
        URI uri = buildUri("/services/databaseServices/name/" + name.trim(), null);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404) {
                LOG.debug(
                    "OpenMetadata service lookup failed status={} uri={} body={}",
                    ex.getStatusCode().value(),
                    uri,
                    trim(ex.getResponseBodyAsString(), 256)
                );
            }
        } catch (Exception ex) {
            LOG.debug("OpenMetadata service lookup error: {}", ex.getMessage());
            LOG.trace("OpenMetadata service lookup stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> createDatabaseService(String name, String serviceType, Map<String, Object> connectionConfig) {
        if (!props.isEnabled() || !StringUtils.hasText(name) || !StringUtils.hasText(serviceType) || connectionConfig == null) {
            return Optional.empty();
        }
        URI uri = buildUri("/services/databaseServices", null);
        Map<String, Object> payload = Map.of(
            "name",
            name.trim(),
            "serviceType",
            serviceType.trim(),
            "connection",
            Map.of("config", connectionConfig)
        );
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "OpenMetadata service create failed status={} uri={} body={}",
                ex.getStatusCode().value(),
                uri,
                trim(ex.getResponseBodyAsString(), 256)
            );
        } catch (Exception ex) {
            LOG.debug("OpenMetadata service create error: {}", ex.getMessage());
            LOG.trace("OpenMetadata service create stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> getIngestionPipelineByName(String name) {
        if (!props.isEnabled() || !StringUtils.hasText(name)) {
            return Optional.empty();
        }
        URI uri = buildUri("/services/ingestionPipelines/name/" + name.trim(), null);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404) {
                LOG.debug(
                    "OpenMetadata pipeline lookup failed status={} uri={} body={}",
                    ex.getStatusCode().value(),
                    uri,
                    trim(ex.getResponseBodyAsString(), 256)
                );
            }
        } catch (Exception ex) {
            LOG.debug("OpenMetadata pipeline lookup error: {}", ex.getMessage());
            LOG.trace("OpenMetadata pipeline lookup stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> createIngestionPipeline(
        String name,
        String serviceId,
        String scheduleCron,
        String pipelineType
    ) {
        if (!props.isEnabled() || !StringUtils.hasText(name) || !StringUtils.hasText(serviceId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/services/ingestionPipelines", null);
        Map<String, Object> airflowConfig = new java.util.LinkedHashMap<>();
        String schedule = StringUtils.hasText(scheduleCron) ? scheduleCron.trim() : null;
        if (StringUtils.hasText(schedule)) {
            airflowConfig.put("scheduleInterval", schedule);
            airflowConfig.put("timezone", "UTC");
        }
        airflowConfig.put("startDate", java.time.Instant.now().toString());
        airflowConfig.put("pausePipeline", Boolean.FALSE);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", name.trim());
        payload.put("pipelineType", StringUtils.hasText(pipelineType) ? pipelineType.trim() : "metadata");
        payload.put("service", Map.of("id", serviceId, "type", "databaseService"));
        payload.put("sourceConfig", Map.of("config", Map.of("type", "DatabaseMetadata")));
        payload.put("airflowConfig", airflowConfig);
        payload.put("loggerLevel", "INFO");
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "OpenMetadata pipeline create failed status={} uri={} body={}",
                ex.getStatusCode().value(),
                uri,
                trim(ex.getResponseBodyAsString(), 256)
            );
        } catch (Exception ex) {
            LOG.debug("OpenMetadata pipeline create error: {}", ex.getMessage());
            LOG.trace("OpenMetadata pipeline create stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> triggerIngestionPipeline(String pipelineId) {
        if (!props.isEnabled() || !StringUtils.hasText(pipelineId)) {
            return Optional.empty();
        }
        URI uri = buildUri("/services/ingestionPipelines/trigger/" + pipelineId.trim(), null);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.POST, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "OpenMetadata pipeline trigger failed status={} uri={} body={}",
                ex.getStatusCode().value(),
                uri,
                trim(ex.getResponseBodyAsString(), 256)
            );
        } catch (Exception ex) {
            LOG.debug("OpenMetadata pipeline trigger error: {}", ex.getMessage());
            LOG.trace("OpenMetadata pipeline trigger stack", ex);
        }
        return Optional.empty();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(props.getAuthToken())) {
            String raw = props.getAuthToken().trim();
            headers.set(HttpHeaders.AUTHORIZATION, raw.startsWith("Bearer ") ? raw : "Bearer " + raw);
        }
        return headers;
    }

    private URI buildUri(String suffix, String fields) {
        String base = props.getBaseUrl();
        String path = props.getApiPath() == null ? "/api/v1" : props.getApiPath();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(path).path(suffix);
        if (StringUtils.hasText(fields)) {
            builder.queryParam("fields", fields.trim());
        }
        return builder.build(true).toUri();
    }

    private URI buildUri(String suffix, Map<String, ?> params) {
        String base = props.getBaseUrl();
        String path = props.getApiPath() == null ? "/api/v1" : props.getApiPath();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(path).path(suffix);
        if (params != null && !params.isEmpty()) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
