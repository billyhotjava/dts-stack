package com.yuzhi.dts.admin.service.infra;

import com.yuzhi.dts.admin.config.PlatformIntegrationProperties;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class PlatformInfraClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformInfraClient.class);

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
}
