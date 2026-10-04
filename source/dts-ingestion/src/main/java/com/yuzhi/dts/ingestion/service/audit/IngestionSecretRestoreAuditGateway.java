package com.yuzhi.dts.ingestion.service.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Narrow adapter for the existing dts-admin /api/audit-events ingest contract. */
@Component
public class IngestionSecretRestoreAuditGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String serviceToken;

    public IngestionSecretRestoreAuditGateway(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        @Value("${DTS_ADMIN_BASE_URL:http://dts-admin:8081}") String adminBaseUrl,
        @Value("${DTS_ADMIN_API_PATH:/api}") String adminApiPath,
        @Value("${DTS_INGESTION_TO_ADMIN_TOKEN:}") String serviceToken
    ) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.objectMapper = objectMapper;
        this.endpoint = endpoint(adminBaseUrl, adminApiPath);
        this.serviceToken = serviceToken == null ? null : serviceToken.trim();
    }

    public SubmissionResult submit(String eventId, String payloadJson) {
        if (!StringUtils.hasText(serviceToken)) {
            return SubmissionResult.retryable("SERVICE_TOKEN_NOT_CONFIGURED");
        }
        try {
            Map<String, Object> body = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-DTS-Service", "dts-ingestion");
            headers.set("X-DTS-Service-Token", stripBearerPrefix(serviceToken));
            ResponseEntity<Map> response = restTemplate.postForEntity(
                endpoint,
                new HttpEntity<>(body, headers),
                Map.class
            );
            Map<?, ?> responseBody = response.getBody();
            String acknowledged = responseBody == null || responseBody.get("eventId") == null
                ? null
                : String.valueOf(responseBody.get("eventId"));
            String status = responseBody == null || responseBody.get("status") == null
                ? null
                : String.valueOf(responseBody.get("status"));
            if (!eventId.equals(acknowledged)) {
                return SubmissionResult.retryable("ACK_EVENT_ID_MISMATCH");
            }
            if (response.getStatusCode().value() == 201 && "RECORDED".equalsIgnoreCase(status)) {
                return SubmissionResult.recorded();
            }
            if (response.getStatusCode().value() == 200 && "DUPLICATE".equalsIgnoreCase(status)) {
                return SubmissionResult.duplicate();
            }
            return SubmissionResult.retryable("UNEXPECTED_ACKNOWLEDGEMENT");
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status == 409) {
                return SubmissionResult.permanent("IDEMPOTENCY_CONFLICT");
            }
            if (status == 400 || status == 404 || status == 405 || status == 422) {
                return SubmissionResult.permanent("HTTP_" + status);
            }
            return SubmissionResult.retryable("HTTP_" + status);
        } catch (RestClientException ex) {
            return SubmissionResult.retryable("TRANSPORT_FAILURE");
        } catch (Exception ex) {
            return SubmissionResult.permanent("INVALID_OUTBOX_PAYLOAD");
        }
    }

    private URI endpoint(String baseUrl, String apiPath) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("DTS_ADMIN_BASE_URL is required");
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        String path = StringUtils.hasText(apiPath)
            ? "/" + apiPath.trim().replaceAll("^/+", "").replaceAll("/+$", "")
            : "";
        return URI.create(base + path + "/audit-events");
    }

    private String stripBearerPrefix(String token) {
        return token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
            ? token.substring("Bearer ".length()).trim()
            : token;
    }

    public record SubmissionResult(Outcome outcome, String errorCode) {
        static SubmissionResult recorded() {
            return new SubmissionResult(Outcome.RECORDED, null);
        }

        static SubmissionResult duplicate() {
            return new SubmissionResult(Outcome.DUPLICATE, null);
        }

        static SubmissionResult retryable(String errorCode) {
            return new SubmissionResult(Outcome.RETRYABLE, errorCode);
        }

        static SubmissionResult permanent(String errorCode) {
            return new SubmissionResult(Outcome.PERMANENT, errorCode);
        }
    }

    public enum Outcome {
        RECORDED,
        DUPLICATE,
        RETRYABLE,
        PERMANENT,
    }
}
