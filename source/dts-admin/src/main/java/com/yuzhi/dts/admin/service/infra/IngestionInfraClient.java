package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.config.IngestionIntegrationProperties;
import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.client.RestTemplate;

@Component
public class IngestionInfraClient {

    private static final Logger log = LoggerFactory.getLogger(IngestionInfraClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final ParameterizedTypeReference<IngestionApiResponse<Map<String, Object>>> MAP_RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final IngestionIntegrationProperties properties;

    public IngestionInfraClient(RestTemplateBuilder builder, IngestionIntegrationProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Object> getServiceSettings(String service) {
        return fetchServiceSettings(service, null);
    }

    public Map<String, Object> updateServiceSettings(String service, Map<String, Object> settings) {
        return fetchServiceSettings(service, settings);
    }

    public Map<String, Object> testServiceSettings(String service, Map<String, Object> settings) {
        if (!properties.isEnabled()) {
            return Map.of();
        }
        String target = StringUtils.hasText(service) ? service.trim().toLowerCase() : "";
        URI uri = buildInfraUri("/settings/" + target + "/test");
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(settings == null ? Map.of() : settings, headers);
        try {
            ResponseEntity<IngestionApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                MAP_RESPONSE_TYPE
            );
            IngestionApiResponse<Map<String, Object>> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                return payload.getData();
            }
            log.debug("Ingestion settings test returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to test ingestion settings: {}", ex.getMessage());
            log.debug("Ingestion settings test failure stack", ex);
        }
        return Map.of();
    }

    private URI buildInfraUri(String suffix) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "http://dts-ingestion:8083";
        }
        String normalizedBase = base.replaceAll("/+$", "");
        String path = properties.getInfraApiPath();
        if (!StringUtils.hasText(path)) {
            path = "/api/infra";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalizedBase + path + tail);
    }

    private Map<String, Object> fetchServiceSettings(String service, Map<String, Object> settings) {
        if (!properties.isEnabled()) {
            return Map.of();
        }
        String target = StringUtils.hasText(service) ? service.trim().toLowerCase() : "";
        URI uri = buildInfraUri("/settings/" + target);
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(settings == null ? null : settings, headers);
        HttpMethod method = settings == null ? HttpMethod.GET : HttpMethod.POST;
        try {
            ResponseEntity<IngestionApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                uri,
                method,
                entity,
                MAP_RESPONSE_TYPE
            );
            IngestionApiResponse<Map<String, Object>> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                return payload.getData();
            }
            log.debug("Ingestion settings request returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to fetch ingestion settings: {}", ex.getMessage());
            log.debug("Ingestion settings failure stack", ex);
        }
        return Map.of();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set(SERVICE_HEADER, properties.getServiceName());
        }
        return headers;
    }

    static class IngestionApiResponse<T> {
        private int status;
        private String message;
        private String code;
        private T data;

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }
}
