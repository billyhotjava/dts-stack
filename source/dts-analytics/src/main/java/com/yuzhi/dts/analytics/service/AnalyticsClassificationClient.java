package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AnalyticsClassificationClient {

    public static final String SOURCE_CLASSIFICATION_MISSING =
        "CONSUMER_CLASSIFICATION_SOURCE_MISSING";

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String LEGACY_SOURCE_CLASSIFICATION_MISSING_DETAIL =
        "Consumer source classification is missing or pending:";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyticsOutboundPlatformProperties properties;

    public AnalyticsClassificationClient(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        AnalyticsOutboundPlatformProperties properties
    ) {
        Duration timeout = Duration.ofSeconds(Math.max(1L, properties.getTimeoutSeconds()));
        this.restTemplate = builder.setConnectTimeout(timeout).setReadTimeout(timeout).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ClassificationResult derive(
        String consumerType,
        String consumerKey,
        String manualFloor,
        List<SubjectRef> upstreams,
        String originRef
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consumerType", consumerType);
        body.put("consumerKey", consumerKey);
        if (StringUtils.hasText(manualFloor)) {
            body.put("manualFloor", manualFloor.trim());
        }
        body.put("upstreams", upstreams);
        body.put("originRef", originRef);
        return result(post("/api/catalog/classifications/consumers/derive", body));
    }

    public ClassificationResult requireCurrent(String consumerType, String consumerKey) {
        URI uri = UriComponentsBuilder.fromUri(uri("/api/catalog/classifications/consumers/guard"))
            .queryParam("consumerType", consumerType)
            .queryParam("consumerKey", consumerKey)
            .build(true)
            .toUri();
        return result(exchange(uri, HttpMethod.GET, null));
    }

    public AccessBinding bind(
        String bindingType,
        String bindingKey,
        String consumerType,
        String consumerKey,
        Instant validTo
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bindingType", bindingType);
        body.put("bindingKey", bindingKey);
        body.put("consumerType", consumerType);
        body.put("consumerKey", consumerKey);
        if (validTo != null) {
            body.put("validTo", validTo.toString());
        }
        Map<String, Object> data = data(post("/api/catalog/classifications/consumers/access-bindings", body));
        return objectMapper.convertValue(data, AccessBinding.class);
    }

    public AccessBinding requireCurrentBinding(String bindingType, String bindingKey) {
        URI uri = UriComponentsBuilder.fromUri(uri("/api/catalog/classifications/consumers/access-bindings/guard"))
            .queryParam("bindingType", bindingType)
            .queryParam("bindingKey", bindingKey)
            .build(true)
            .toUri();
        return objectMapper.convertValue(data(exchange(uri, HttpMethod.GET, null)), AccessBinding.class);
    }

    public ExportSeal sealExport(String fileSubjectKey, List<SubjectRef> upstreams, String originRef) {
        Map<String, Object> body = Map.of(
            "fileSubjectKey",
            fileSubjectKey,
            "upstreams",
            upstreams,
            "originRef",
            originRef
        );
        return objectMapper.convertValue(
            data(post("/api/catalog/classifications/consumers/exports/seal", body)),
            ExportSeal.class
        );
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        return exchange(uri(path), HttpMethod.POST, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(URI uri, HttpMethod method, Map<String, Object> body) {
        if (!properties.isEnabled()) {
            throw new ClassificationContractException("Platform classification contract is disabled");
        }
        try {
            Object raw = restTemplate.exchange(
                uri,
                method,
                new HttpEntity<>(body, headers()),
                Map.class
            ).getBody();
            if (!(raw instanceof Map<?, ?> response)) {
                throw new ClassificationContractException("Platform classification contract returned an empty response");
            }
            return (Map<String, Object>) response;
        } catch (ClassificationContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClassificationContractException("Platform classification contract call failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        Object raw = response.get("data");
        if (raw instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        if (response.containsKey("consumerType") || response.containsKey("bindingType")) {
            return response;
        }
        throw new ClassificationContractException("Platform classification contract response has no data");
    }

    private ClassificationResult result(Map<String, Object> response) {
        return objectMapper.convertValue(data(response), ClassificationResult.class);
    }

    private URI uri(String path) {
        String baseUrl = StringUtils.hasText(properties.getBaseUrl())
            ? properties.getBaseUrl().trim()
            : "http://dts-platform:8081";
        return UriComponentsBuilder.fromHttpUrl(baseUrl).path(path).build(true).toUri();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(
            SERVICE_HEADER,
            StringUtils.hasText(properties.getServiceName())
                ? properties.getServiceName().trim()
                : "dts-analytics"
        );
        if (StringUtils.hasText(properties.getServiceToken())) {
            headers.set(SERVICE_TOKEN_HEADER, properties.getServiceToken().trim());
        }
        return headers;
    }

    public record SubjectRef(String subjectType, String subjectKey) {}

    public record ClassificationResult(
        String consumerType,
        String consumerKey,
        String effectiveLevel,
        String snapshotId,
        Long snapshotVersion,
        List<ResolvedSource> upstreams,
        String manualFloor,
        List<String> blockers
    ) {}

    public record ResolvedSource(
        String subjectType,
        String subjectKey,
        String snapshotId,
        Long snapshotVersion,
        String effectiveLevel
    ) {}

    public record AccessBinding(
        String bindingType,
        String bindingKey,
        String consumerType,
        String consumerKey,
        String snapshotId,
        Long snapshotVersion,
        String effectiveLevel,
        String status,
        Instant validTo
    ) {}

    public record ExportSeal(String snapshotId, String subjectKey, String effectiveLevel, Long snapshotVersion) {}

    public static class ClassificationContractException extends RuntimeException {

        private final boolean sourceClassificationMissing;

        public ClassificationContractException(String message) {
            super(message);
            this.sourceClassificationMissing = false;
        }

        public ClassificationContractException(String message, Throwable cause) {
            super(message, cause);
            this.sourceClassificationMissing = containsSourceClassificationMissing(cause);
        }

        public boolean isSourceClassificationMissing() {
            return sourceClassificationMissing;
        }

        private static boolean containsSourceClassificationMissing(Throwable cause) {
            Throwable current = cause;
            while (current != null) {
                if (current instanceof RestClientResponseException response) {
                    String body = response.getResponseBodyAsString();
                    if (
                        StringUtils.hasText(body) &&
                        (
                            body.contains(SOURCE_CLASSIFICATION_MISSING) ||
                            body.contains(LEGACY_SOURCE_CLASSIFICATION_MISSING_DETAIL)
                        )
                    ) {
                        return true;
                    }
                }
                current = current.getCause();
            }
            return false;
        }
    }
}
