package com.yuzhi.dts.platform.service.ingestion;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
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

    private final RestTemplate restTemplate;
    private final DtsIngestionProperties properties;

    public IngestionServiceClient(RestTemplateBuilder builder, DtsIngestionProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(20)).build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getBaseUrl());
    }

    public ApiResponse<List<Map<String, Object>>> listSourceDefinitions() {
        return exchange("/definitions/sources", HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {});
    }

    public ApiResponse<List<Map<String, Object>>> listSources() {
        return exchange("/sources", HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {});
    }

    public ApiResponse<Map<String, Object>> createSource(Object payload) {
        return exchange("/sources", HttpMethod.POST, payload, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> updateSource(String id, Object payload) {
        return exchange("/sources/" + id, HttpMethod.PUT, payload, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> checkSource(String id) {
        return exchange("/sources/" + id + "/check", HttpMethod.POST, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> discoverSource(String id) {
        return exchange("/sources/" + id + "/discover", HttpMethod.POST, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> deleteSource(String id) {
        return exchange("/sources/" + id, HttpMethod.DELETE, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<List<Map<String, Object>>> listDestinationDefinitions() {
        return exchange("/definitions/destinations", HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {});
    }

    public ApiResponse<List<Map<String, Object>>> listConnections(boolean refresh) {
        String suffix = refresh ? "/connections?refresh=true" : "/connections";
        return exchange(suffix, HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {});
    }

    public ApiResponse<Map<String, Object>> createConnection(Object payload) {
        return exchange("/connections", HttpMethod.POST, payload, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> updateConnection(String id, Object payload) {
        return exchange("/connections/" + id, HttpMethod.PUT, payload, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> syncConnection(String id) {
        return exchange("/connections/" + id + "/sync", HttpMethod.POST, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<List<Map<String, Object>>> listJobs(String id, int limit) {
        String suffix = "/connections/" + id + "/jobs?limit=" + Math.max(1, limit);
        return exchange(suffix, HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {});
    }

    public ApiResponse<Map<String, Object>> refreshCatalog(String id) {
        return exchange("/connections/" + id + "/catalog", HttpMethod.POST, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> deleteConnection(String id) {
        return exchange("/connections/" + id, HttpMethod.DELETE, null, new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
    }

    public ApiResponse<Map<String, Object>> createIngestionTask(Object payload) {
        return exchangeTask("/api/ingestion/tasks", HttpMethod.POST, payload);
    }

    private ApiResponse<Map<String, Object>> exchangeTask(String path, HttpMethod method, Object payload) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildAbsoluteUri(path);
        try {
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(defaultHeaders()) : new HttpEntity<>(payload, defaultHeaders());
            ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                uri,
                method,
                entity,
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
            );
            ApiResponse<Map<String, Object>> body = response.getBody();
            if (body == null) {
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion service empty response", null);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion API {} error: {}", path, ex.getMessage());
            return new ApiResponse<>(500, "ingestion service error", null);
        }
    }

    private <T> ApiResponse<T> exchange(String path, HttpMethod method, Object payload, ParameterizedTypeReference<ApiResponse<T>> ref) {
        if (!isEnabled()) {
            return new ApiResponse<>(503, "ingestion service disabled", null);
        }
        URI uri = buildUri(path);
        try {
            HttpEntity<?> entity = payload == null ? new HttpEntity<>(defaultHeaders()) : new HttpEntity<>(payload, defaultHeaders());
            ResponseEntity<ApiResponse<T>> response = restTemplate.exchange(uri, method, entity, ref);
            ApiResponse<T> body = response.getBody();
            if (body == null) {
                return new ApiResponse<>(response.getStatusCode().value(), "ingestion service empty response", null);
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Ingestion API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new ApiResponse<>(ex.getStatusCode().value(), "ingestion service error", null);
        } catch (Exception ex) {
            LOG.warn("Ingestion API {} error: {}", path, ex.getMessage());
            return new ApiResponse<>(500, "ingestion service error", null);
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

    private URI buildUri(String suffix) {
        String base = properties.getBaseUrl();
        String apiPath = properties.getApiPath();
        String normalizedBase = base == null ? "" : base.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String normalizedPath = StringUtils.hasText(apiPath) ? apiPath.trim() : "";
        if (StringUtils.hasText(normalizedPath) && !normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        String tail = suffix == null ? "" : suffix;
        return UriComponentsBuilder.fromHttpUrl(normalizedBase + normalizedPath + tail).build(true).toUri();
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
}
