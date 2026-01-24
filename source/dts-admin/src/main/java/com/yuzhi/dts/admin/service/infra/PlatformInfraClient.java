package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.config.PlatformIntegrationProperties;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.JdbcConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.JdbcDriverInfo;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class PlatformInfraClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformInfraClient.class);
    private static final ParameterizedTypeReference<PlatformApiResponse<HiveConnectionTestResult>> TEST_RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<PlatformApiResponse<List<JdbcDriverInfo>>> DRIVER_RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<PlatformApiResponse<Map<String, Object>>> MAP_RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    static final String SERVICE_HEADER = "X-DTS-Service";

    private final RestTemplate restTemplate;
    private final PlatformIntegrationProperties properties;

    public PlatformInfraClient(RestTemplateBuilder builder, PlatformIntegrationProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
    }

    public void publishInceptor(HiveConnectionPersistRequest request) {
        if (!properties.isEnabled()) {
            return;
        }
        URI uri = buildUri("/infra/data-sources/inceptor/publish");
        HttpHeaders headers = buildHeaders();
        HttpEntity<HiveConnectionPersistRequest> entity = new HttpEntity<>(request, headers);
        try {
            restTemplate.exchange(uri, HttpMethod.POST, entity, Void.class);
            log.info("Synchronized Inceptor data source to platform service");
        } catch (Exception ex) {
            log.warn("Failed to sync Inceptor data source to platform: {}", ex.getMessage());
            log.debug("Platform publish failure stack", ex);
        }
    }

    public void refreshInceptor() {
        if (!properties.isEnabled()) {
            return;
        }
        URI uri = buildUri("/infra/data-sources/inceptor/refresh");
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(uri, HttpMethod.POST, entity, Void.class);
        } catch (Exception ex) {
            log.warn("Failed to trigger platform Inceptor refresh: {}", ex.getMessage());
            log.debug("Platform refresh failure stack", ex);
        }
    }

    public Optional<HiveConnectionTestResult> testInceptorConnection(HiveConnectionTestRequest request) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri("/infra/data-sources/inceptor/test");
        HttpHeaders headers = buildHeaders();
        HttpEntity<HiveConnectionTestRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<PlatformApiResponse<HiveConnectionTestResult>> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                TEST_RESPONSE_TYPE
            );
            PlatformApiResponse<HiveConnectionTestResult> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                return Optional.of(payload.getData());
            }
            if (payload != null) {
                HiveConnectionTestResult failure = new HiveConnectionTestResult();
                failure.setSuccess(false);
                failure.setElapsedMillis(0L);
                failure.setMessage(StringUtils.hasText(payload.getMessage()) ? payload.getMessage() : "平台测试失败");
                return Optional.of(failure);
            }
            log.debug("Platform test returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to test Inceptor connection via platform: {}", ex.getMessage());
            log.debug("Platform test failure stack", ex);
        }
        return Optional.empty();
    }

    public Optional<HiveConnectionTestResult> testJdbcConnection(JdbcConnectionTestRequest request) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri("/infra/data-sources/jdbc/test");
        HttpHeaders headers = buildHeaders();
        HttpEntity<JdbcConnectionTestRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<PlatformApiResponse<HiveConnectionTestResult>> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                TEST_RESPONSE_TYPE
            );
            PlatformApiResponse<HiveConnectionTestResult> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                return Optional.of(payload.getData());
            }
            if (payload != null) {
                HiveConnectionTestResult failure = new HiveConnectionTestResult();
                failure.setSuccess(false);
                failure.setElapsedMillis(0L);
                failure.setMessage(StringUtils.hasText(payload.getMessage()) ? payload.getMessage() : "平台测试失败");
                return Optional.of(failure);
            }
            log.debug("Platform JDBC test returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to test JDBC connection via platform: {}", ex.getMessage());
            log.debug("Platform JDBC test failure stack", ex);
        }
        return Optional.empty();
    }

    public List<JdbcDriverInfo> fetchJdbcDrivers() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        URI uri = buildUri("/infra/jdbc/drivers");
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<PlatformApiResponse<List<JdbcDriverInfo>>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                DRIVER_RESPONSE_TYPE
            );
            PlatformApiResponse<List<JdbcDriverInfo>> payload = response.getBody();
            if (payload != null && payload.getStatus() == 200 && payload.getData() != null) {
                return payload.getData();
            }
            log.debug("Platform driver list returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to fetch JDBC driver list from platform: {}", ex.getMessage());
            log.debug("Platform driver list failure stack", ex);
        }
        return List.of();
    }

    public Optional<String> registerDestinationDefinition(String name, String dockerRepo, String dockerTag) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri("/infra/settings/airbyte/destination-definitions/register");
        HttpHeaders headers = buildHeaders();
        Map<String, String> payload = Map.of(
            "name",
            name,
            "dockerRepository",
            dockerRepo,
            "dockerImageTag",
            dockerTag
        );
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<PlatformApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                MAP_RESPONSE_TYPE
            );
            PlatformApiResponse<Map<String, Object>> body = response.getBody();
            if (body != null && body.getStatus() == 200 && body.getData() != null) {
                Object id = body.getData().get("destinationDefinitionId");
                return Optional.ofNullable(id != null ? String.valueOf(id) : null);
            }
            log.debug("Register destination definition returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to register destination definition via platform: {}", ex.getMessage());
            log.debug("Platform register destination failure stack", ex);
        }
        return Optional.empty();
    }

    private URI buildUri(String suffix) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "http://dts-platform:8081";
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

    static class PlatformApiResponse<T> {
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
