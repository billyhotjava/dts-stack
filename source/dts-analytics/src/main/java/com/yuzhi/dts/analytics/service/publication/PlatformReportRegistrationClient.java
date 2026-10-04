package com.yuzhi.dts.analytics.service.publication;

import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PlatformReportRegistrationClient {

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final RestTemplate restTemplate;
    private final AnalyticsOutboundPlatformProperties properties;

    public PlatformReportRegistrationClient(
        RestTemplateBuilder builder,
        AnalyticsOutboundPlatformProperties properties
    ) {
        long timeout = Math.max(2, properties.getTimeoutSeconds());
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(timeout))
            .setReadTimeout(Duration.ofSeconds(timeout))
            .build();
        this.properties = properties;
    }

    public RegistrationResponse register(ReportRegistrationCommand command) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("platform report registration is disabled");
        }
        Map<?, ?> response = restTemplate.exchange(
            uri(),
            HttpMethod.PUT,
            new HttpEntity<>(command, headers()),
            Map.class
        ).getBody();
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map<?, ?> result)) {
            throw new IllegalStateException("platform returned no report registration result");
        }
        return new RegistrationResponse(
            text(result.get("registrationId")),
            longValue(result.get("assetVersion")),
            text(result.get("reconcileStatus"))
        );
    }

    private URI uri() {
        String baseUrl = StringUtils.hasText(properties.getBaseUrl())
            ? properties.getBaseUrl().trim()
            : "http://dts-platform:8081";
        String apiPath = StringUtils.hasText(properties.getApiPath()) ? properties.getApiPath().trim() : "/api";
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(apiPath)
            .path("/internal/reports/registrations")
            .build(true)
            .toUri();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SERVICE_HEADER, StringUtils.hasText(properties.getServiceName())
            ? properties.getServiceName().trim()
            : "dts-analytics");
        if (StringUtils.hasText(properties.getServiceToken())) {
            headers.set(SERVICE_TOKEN_HEADER, properties.getServiceToken().trim());
        }
        return headers;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public record RegistrationResponse(String registrationId, long assetVersion, String reconcileStatus) {}
}
