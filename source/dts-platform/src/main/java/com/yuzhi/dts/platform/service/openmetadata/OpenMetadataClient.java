package com.yuzhi.dts.platform.service.openmetadata;

import com.yuzhi.dts.platform.config.OpenMetadataProperties;
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

    public Optional<Map<String, Object>> getLineage(String entityId, int upstreamDepth, int downstreamDepth) {
        if (!StringUtils.hasText(entityId) || !props.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri(
            "/lineage/table/" + entityId,
            Map.of("upstreamDepth", upstreamDepth, "downstreamDepth", downstreamDepth)
        );
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
                    "OpenMetadata lineage lookup failed status={} uri={} body={}",
                    ex.getStatusCode().value(),
                    uri,
                    trim(ex.getResponseBodyAsString(), 256)
                );
            }
        } catch (Exception ex) {
            LOG.debug("OpenMetadata lineage lookup error: {}", ex.getMessage());
            LOG.trace("OpenMetadata lineage lookup stack", ex);
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> getTestCases(String entityLink) {
        if (!StringUtils.hasText(entityLink) || !props.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri(
            "/dataQuality/testCases",
            Map.of("entityLink", entityLink, "fields", "testCaseResult,owner,testSuite")
        );
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
                    "OpenMetadata quality lookup failed status={} uri={} body={}",
                    ex.getStatusCode().value(),
                    uri,
                    trim(ex.getResponseBodyAsString(), 256)
                );
            }
        } catch (Exception ex) {
            LOG.debug("OpenMetadata quality lookup error: {}", ex.getMessage());
            LOG.trace("OpenMetadata quality lookup stack", ex);
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
        if (value == null) return null;
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
