package com.yuzhi.dts.platform.service.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Minimal client for delegating authentication to dts-admin.
 * Calls /api/keycloak/auth/* on the admin service instead of talking to Keycloak directly.
 */
@Component
public class AdminAuthClient {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthClient.class);

    private final RestTemplate restTemplate;
    private final DtsAdminProperties props;

    private static final ParameterizedTypeReference<ApiEnvelope<Map<String, Object>>> MAP_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    public AdminAuthClient(RestTemplateBuilder builder, DtsAdminProperties props) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.props = props;
    }

    public LoginResult login(String username, String password) {
        // Call the platform-friendly login endpoint exposed by dts-admin
        URI uri = buildUri("/keycloak/auth/platform/login");
        Map<String, String> payload = Map.of("username", username == null ? "" : username, "password", password == null ? "" : password);
        ApiEnvelope<Map<String, Object>> resp = exchangeJson(uri, HttpMethod.POST, payload);
        if (resp == null || !resp.isSuccess() || resp.data() == null) {
            String msg = resp == null ? "auth failed" : (resp.message() == null ? "auth failed" : resp.message());
            throw new org.springframework.security.authentication.BadCredentialsException(msg);
        }
        Map<String, Object> data = resp.data();
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.getOrDefault("user", java.util.Collections.emptyMap());
        String accessToken = asText(data.get("accessToken"));
        String refreshToken = asText(data.get("refreshToken"));
        return new LoginResult(user, accessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        URI uri = buildUri("/keycloak/auth/logout");
        Map<String, String> payload = refreshToken == null || refreshToken.isBlank() ? Map.of() : Map.of("refreshToken", refreshToken);
        try {
            exchangeJson(uri, HttpMethod.POST, payload);
        } catch (Exception ex) {
            // best-effort, log and continue
            log.debug("Admin logout call failed: {}", ex.getMessage());
        }
    }

    public RefreshResult refresh(String refreshToken) {
        URI uri = buildUri("/keycloak/auth/refresh");
        Map<String, String> payload = Map.of("refreshToken", refreshToken == null ? "" : refreshToken);
        ApiEnvelope<Map<String, Object>> resp = exchangeJson(uri, HttpMethod.POST, payload);
        if (resp == null || !resp.isSuccess() || resp.data() == null) {
            String msg = resp == null ? "refresh failed" : (resp.message() == null ? "refresh failed" : resp.message());
            throw new IllegalStateException(msg);
        }
        Map<String, Object> data = resp.data();
        String accessToken = asText(data.get("accessToken"));
        String newRefreshToken = asText(data.get("refreshToken"));
        return new RefreshResult(accessToken, newRefreshToken);
    }

    private ApiEnvelope<Map<String, Object>> exchangeJson(URI uri, HttpMethod method, Map<String, String> json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(props.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
        }
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(json, headers);
        try {
            ResponseEntity<ApiEnvelope<Map<String, Object>>> response = restTemplate.exchange(uri, method, entity, MAP_ENVELOPE);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Admin auth call {} failed: status={} body={} uri={}", method, ex.getStatusCode().value(), trim(body, 256), uri);
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                throw new org.springframework.security.authentication.BadCredentialsException(messageFromBody(body, ex.getMessage()));
            }
            throw new IllegalStateException(messageFromBody(body, ex.getMessage()), ex);
        }
    }

    private URI buildUri(String suffix) {
        String baseUrl = props.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) baseUrl = "http://dts-admin:8081";
        String normalized = baseUrl.replaceAll("/+$", "");
        String apiPath = props.getApiPath();
        String basePath = (apiPath == null ? "" : apiPath).replaceAll("/+$", "");
        String tail = suffix == null ? "" : suffix;
        return URI.create(normalized + basePath + tail);
    }

    private String asText(Object v) {
        return v == null ? null : v.toString();
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String messageFromBody(String body, String fallback) {
        if (body == null || body.isBlank()) return fallback == null ? "" : fallback;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = om.readTree(body);
            if (node.hasNonNull("message")) return node.get("message").asText();
        } catch (Exception ignored) {}
        return fallback == null ? body : fallback;
    }

    public record LoginResult(Map<String, Object> user, String accessToken, String refreshToken) {}
    public record RefreshResult(String accessToken, String refreshToken) {}

    public record ApiEnvelope<T>(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data
    ) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
    }
}
