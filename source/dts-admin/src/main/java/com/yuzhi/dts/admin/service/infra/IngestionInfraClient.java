package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.config.IngestionIntegrationProperties;
import com.yuzhi.dts.admin.service.infra.dto.AirbyteDestinationDefinitionDto;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.web.client.RestTemplate;

@Component
public class IngestionInfraClient {

    private static final Logger log = LoggerFactory.getLogger(IngestionInfraClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final ParameterizedTypeReference<IngestionApiResponse<List<Map<String, Object>>>> DEST_DEF_RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final IngestionIntegrationProperties properties;

    public IngestionInfraClient(RestTemplateBuilder builder, IngestionIntegrationProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
    }

    public List<AirbyteDestinationDefinitionDto> listDestinationDefinitions() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        URI uri = buildUri("/definitions/destinations");
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<IngestionApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                DEST_DEF_RESPONSE_TYPE
            );
            IngestionApiResponse<List<Map<String, Object>>> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                List<AirbyteDestinationDefinitionDto> output = new ArrayList<>();
                for (Map<String, Object> item : payload.getData()) {
                    AirbyteDestinationDefinitionDto dto = toDefinition(item);
                    if (dto != null) {
                        output.add(dto);
                    }
                }
                return output;
            }
            log.debug("Ingestion destination list returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to fetch destination definitions from ingestion: {}", ex.getMessage());
            log.debug("Ingestion destination list failure stack", ex);
        }
        return List.of();
    }

    private AirbyteDestinationDefinitionDto toDefinition(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        String id = stringVal(item.get("destinationDefinitionId"));
        String name = stringVal(item.get("name"));
        String repo = stringVal(item.get("dockerRepository"));
        if (!StringUtils.hasText(id) && !StringUtils.hasText(name)) {
            return null;
        }
        return new AirbyteDestinationDefinitionDto(id, name, repo);
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private URI buildUri(String suffix) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "http://dts-ingestion:8083";
        }
        String normalizedBase = base.replaceAll("/+$", "");
        String path = properties.getApiPath();
        if (!StringUtils.hasText(path)) {
            path = "";
        }
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalizedBase + path + tail);
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
