package com.yuzhi.dts.platform.service.admin.gateway.audit;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminAuditGateway {

    public record AuditSubmissionResult(Outcome outcome, Duration retryAfter) {

        public static final AuditSubmissionResult RECORDED = new AuditSubmissionResult(Outcome.RECORDED, null);
        public static final AuditSubmissionResult DUPLICATE = new AuditSubmissionResult(Outcome.DUPLICATE, null);
        public static final AuditSubmissionResult IDEMPOTENCY_CONFLICT = new AuditSubmissionResult(
            Outcome.IDEMPOTENCY_CONFLICT,
            null
        );
        public static final AuditSubmissionResult PERMANENT_FAILURE = new AuditSubmissionResult(Outcome.PERMANENT_FAILURE, null);
        public static final AuditSubmissionResult RETRYABLE_FAILURE = new AuditSubmissionResult(Outcome.RETRYABLE_FAILURE, null);

        public static AuditSubmissionResult retryable(Duration retryAfter) {
            return new AuditSubmissionResult(Outcome.RETRYABLE_FAILURE, retryAfter);
        }

        public enum Outcome {
            RECORDED,
            DUPLICATE,
            IDEMPOTENCY_CONFLICT,
            PERMANENT_FAILURE,
            RETRYABLE_FAILURE,
        }
    }

    private final RestTemplate restTemplate;
    private final PlatformOutboundAdminProperties adminProperties;

    public AdminAuditGateway(RestTemplateBuilder builder, PlatformOutboundAdminProperties adminProperties) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.adminProperties = adminProperties;
    }

    public boolean isEnabled() {
        return adminProperties != null && adminProperties.isEnabled();
    }

    public Object list(Map<String, String> params, String authorization) {
        return unwrapAdminData(exchange(buildAdminUri("/audit-entries", params), authorization));
    }

    public Object get(String id, String authorization) {
        return unwrapAdminData(exchange(buildAdminUri("/audit-entries/" + id, Map.of()), authorization));
    }

    public byte[] export(Map<String, String> params, String authorization) {
        ResponseEntity<byte[]> response = exchangeBytes(buildAdminUri("/audit-entries/export", params), authorization);
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "审计日志导出失败");
        }
        return response.getBody() != null ? response.getBody() : new byte[0];
    }

    public AuditSubmissionResult submitEvent(Map<String, Object> body) {
        if (!isEnabled() || body == null) {
            return AuditSubmissionResult.RETRYABLE_FAILURE;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(adminProperties.getServiceToken())) {
                String raw = adminProperties.getServiceToken().trim();
                headers.set("X-DTS-Service-Token", stripBearerPrefix(raw));
            }
            if (StringUtils.hasText(adminProperties.getServiceName())) {
                headers.set("X-DTS-Service", adminProperties.getServiceName().trim());
            }
            ResponseEntity<Map> response = restTemplate.postForEntity(
                buildAdminUri("/audit-events", Map.of()),
                new HttpEntity<>(body, headers),
                Map.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                return response.getStatusCode().is4xxClientError()
                    ? AuditSubmissionResult.PERMANENT_FAILURE
                    : AuditSubmissionResult.RETRYABLE_FAILURE;
            }
            Map responseBody = response.getBody();
            String status = responseBody == null ? null : String.valueOf(responseBody.get("status"));
            String submittedEventId = body.get("eventId") == null ? null : String.valueOf(body.get("eventId"));
            String acknowledgedEventId = responseBody == null || responseBody.get("eventId") == null
                ? null
                : String.valueOf(responseBody.get("eventId"));
            if (!StringUtils.hasText(submittedEventId) || !submittedEventId.equals(acknowledgedEventId)) {
                return AuditSubmissionResult.RETRYABLE_FAILURE;
            }
            if (response.getStatusCode().value() == 201 && "RECORDED".equalsIgnoreCase(status)) {
                return AuditSubmissionResult.RECORDED;
            }
            if (response.getStatusCode().value() == 200 && "DUPLICATE".equalsIgnoreCase(status)) {
                return AuditSubmissionResult.DUPLICATE;
            }
            return AuditSubmissionResult.RETRYABLE_FAILURE;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 409) {
                return AuditSubmissionResult.IDEMPOTENCY_CONFLICT;
            }
            if (isRetryableStatus(ex.getStatusCode().value())) {
                return AuditSubmissionResult.retryable(retryAfter(ex));
            }
            return ex.getStatusCode().is4xxClientError()
                ? AuditSubmissionResult.PERMANENT_FAILURE
                : AuditSubmissionResult.RETRYABLE_FAILURE;
        } catch (RestClientException ex) {
            return AuditSubmissionResult.RETRYABLE_FAILURE;
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 401 || status == 403 || status == 408 || status == 425 || status == 429;
    }

    private Duration retryAfter(HttpStatusCodeException failure) {
        if (failure.getResponseHeaders() == null) {
            return null;
        }
        String raw = failure.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return Duration.ofSeconds(Math.max(1, Math.min(seconds, 3600)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stripBearerPrefix(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        String trimmed = token.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
            ? trimmed.substring("Bearer ".length()).trim()
            : trimmed;
    }

    private ResponseEntity<Map> exchange(URI uri, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<byte[]> exchangeBytes(URI uri, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(new MediaType("text", "csv", StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
    }

    private Object unwrapAdminData(ResponseEntity<Map> response) {
        Map body = response != null ? response.getBody() : null;
        if (body == null) {
            return null;
        }
        Object status = body.get("status");
        if (status != null) {
            String text = String.valueOf(status).trim().toUpperCase();
            if (!text.isEmpty() && !"SUCCESS".equals(text) && !"OK".equals(text)) {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    String.valueOf(body.getOrDefault("message", "审计服务返回错误"))
                );
            }
        }
        Object data = body.get("data");
        return data != null ? data : body;
    }

    private URI buildAdminUri(String path, Map<String, String> params) {
        if (!isEnabled()) {
            throw new IllegalStateException("dts-admin 服务调用已禁用");
        }
        String base = StringUtils.hasText(adminProperties.getBaseUrl()) ? adminProperties.getBaseUrl().trim() : "http://dts-admin:8081";
        String normalizedBase = base.replaceAll("/+$", "");
        String apiPath = StringUtils.hasText(adminProperties.getApiPath()) ? adminProperties.getApiPath().trim() : "/api";
        String normalizedApiPath = "/" + apiPath.replaceAll("^/+", "").replaceAll("/+$", "");
        String normalizedRelPath = "/" + String.valueOf(path).replaceAll("^/+", "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizedBase + normalizedApiPath + normalizedRelPath);
        if (params != null && !params.isEmpty()) {
            params.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.queryParam(k, v);
                }
            });
        }
        return builder.build(true).toUri();
    }
}
