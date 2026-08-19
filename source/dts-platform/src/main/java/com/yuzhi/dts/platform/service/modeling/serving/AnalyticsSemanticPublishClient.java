package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.config.DtsAnalyticsProperties;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Timeout-bounded client for the internal Analytics semantic publish endpoint. */
@Component
public class AnalyticsSemanticPublishClient {

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String PUBLISH_PATH = "/api/semantic/publish";

    private final RestTemplate restTemplate;
    private final DtsAnalyticsProperties properties;

    @Autowired
    public AnalyticsSemanticPublishClient(RestTemplateBuilder builder, DtsAnalyticsProperties properties) {
        this(
            builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build(),
            properties
        );
    }

    AnalyticsSemanticPublishClient(RestTemplate restTemplate, DtsAnalyticsProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void publish(PublishPayload payload) {
        if (
            !properties.isEnabled() ||
            !StringUtils.hasText(properties.getBaseUrl()) ||
            !StringUtils.hasText(properties.getServiceToken())
        ) {
            throw new SemanticPublishException("ANALYTICS_SEMANTIC_PUBLISH_NOT_CONFIGURED");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SERVICE_HEADER, properties.getServiceName());
        headers.set(SERVICE_TOKEN_HEADER, properties.getServiceToken().trim());
        try {
            restTemplate.exchange(uri(), HttpMethod.POST, new HttpEntity<>(payload, headers), Object.class);
        } catch (HttpStatusCodeException failure) {
            throw new SemanticPublishException(
                "ANALYTICS_SEMANTIC_PUBLISH_HTTP_" + failure.getStatusCode().value(),
                failure
            );
        } catch (ResourceAccessException failure) {
            throw new SemanticPublishException("ANALYTICS_SEMANTIC_PUBLISH_UNAVAILABLE", failure);
        }
    }

    private URI uri() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return UriComponentsBuilder.fromHttpUrl(baseUrl + PUBLISH_PATH).build(true).toUri();
    }

    public static class SemanticPublishException extends RuntimeException {

        public SemanticPublishException(String code) {
            super(code);
        }

        public SemanticPublishException(String code, Throwable cause) {
            super(code, cause);
        }
    }
}
