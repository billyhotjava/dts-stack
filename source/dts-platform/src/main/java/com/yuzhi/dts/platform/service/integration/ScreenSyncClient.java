package com.yuzhi.dts.platform.service.integration;

import com.yuzhi.dts.platform.config.DtsAnalyticsProperties;
import com.yuzhi.dts.platform.service.integration.dto.ScreenSummary;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sprint-17 / F1 — calls dts-analytics' internal screens endpoint
 * {@code GET /api/internal/screens} on behalf of the reconcile job.
 *
 * <p>Auth model: forwards the {@code X-DTS-Service: dts-platform}
 * header. dts-bi's {@code InternalScreenResource} only accepts requests
 * with that header in its allow-list.
 *
 * <p>Configuration off-state: when {@link DtsAnalyticsProperties#isEnabled()}
 * is {@code false} or {@code baseUrl} is blank, callers see
 * {@link NotConfiguredException}; the reconcile job catches it and skips.
 */
@Component
public class ScreenSyncClient {

    private static final Logger log = LoggerFactory.getLogger(ScreenSyncClient.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String SCREENS_PATH = "/api/internal/screens";

    private final RestTemplate restTemplate;
    private final DtsAnalyticsProperties properties;

    @Autowired
    public ScreenSyncClient(RestTemplateBuilder builder, DtsAnalyticsProperties properties) {
        this(
            builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build(),
            properties
        );
    }

    /** Test seam — allows wiring a {@link MockRestServiceServer}-bound RestTemplate. */
    ScreenSyncClient(RestTemplate restTemplate, DtsAnalyticsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isEnabled() && StringUtils.hasText(properties.getBaseUrl());
    }

    public List<ScreenSummary> listScreens() {
        if (!isConfigured()) {
            throw new NotConfiguredException("dts.analytics.base-url not set or feature disabled");
        }

        URI uri = buildUri(SCREENS_PATH);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(SERVICE_HEADER, properties.getServiceName());
        if (StringUtils.hasText(properties.getServiceToken())) {
            headers.set(SERVICE_TOKEN_HEADER, properties.getServiceToken().trim());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<ScreenSummary>> response = restTemplate.exchange(
            uri,
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<List<ScreenSummary>>() {}
        );
        List<ScreenSummary> body = response.getBody();
        log.debug("dts-analytics screens fetched: count={}", body == null ? 0 : body.size());
        return body == null ? List.of() : body;
    }

    private URI buildUri(String path) {
        String base = properties.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return UriComponentsBuilder.fromHttpUrl(base + path).build(true).toUri();
    }
}
