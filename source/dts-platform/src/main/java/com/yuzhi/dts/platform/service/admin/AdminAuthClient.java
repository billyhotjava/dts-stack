package com.yuzhi.dts.platform.service.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.common.net.IpAddressUtils;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
        URI uri = buildUri("/keycloak/auth/platform/login?auditSilent=true");
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
        Long accessExpiresIn = asLong(data.get("expiresIn"));
        Long refreshExpiresIn = asLong(data.get("refreshExpiresIn"));
        if (accessExpiresIn == null) accessExpiresIn = asLong(data.get("accessTokenExpiresIn"));
        if (refreshExpiresIn == null) refreshExpiresIn = asLong(data.get("refreshTokenExpiresIn"));
        return new LoginResult(user, accessToken, refreshToken, accessExpiresIn, refreshExpiresIn);
    }

    public void logout(String refreshToken) {
        URI uri = buildUri("/keycloak/auth/logout?auditSilent=true");
        Map<String, String> payload = refreshToken == null || refreshToken.isBlank() ? Map.of() : Map.of("refreshToken", refreshToken);
        try {
            exchangeJson(uri, HttpMethod.POST, payload);
        } catch (Exception ex) {
            // best-effort, log and continue
            log.debug("Admin logout call failed: {}", ex.getMessage());
        }
    }

    public RefreshResult refresh(String refreshToken) {
        URI uri = buildUri("/keycloak/auth/refresh?auditSilent=true");
        Map<String, String> payload = Map.of("refreshToken", refreshToken == null ? "" : refreshToken);
        ApiEnvelope<Map<String, Object>> resp = exchangeJson(uri, HttpMethod.POST, payload);
        if (resp == null || !resp.isSuccess() || resp.data() == null) {
            String msg = resp == null ? "refresh failed" : (resp.message() == null ? "refresh failed" : resp.message());
            throw new IllegalStateException(msg);
        }
        Map<String, Object> data = resp.data();
        String accessToken = asText(data.get("accessToken"));
        String newRefreshToken = asText(data.get("refreshToken"));
        Long accessExpiresIn = asLong(data.get("expiresIn"));
        Long refreshExpiresIn = asLong(data.get("refreshExpiresIn"));
        if (accessExpiresIn == null) accessExpiresIn = asLong(data.get("accessTokenExpiresIn"));
        if (refreshExpiresIn == null) refreshExpiresIn = asLong(data.get("refreshTokenExpiresIn"));
        return new RefreshResult(accessToken, newRefreshToken, accessExpiresIn, refreshExpiresIn);
    }

    private ApiEnvelope<Map<String, Object>> exchangeJson(URI uri, HttpMethod method, Map<String, String> json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(props.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
        }
        // Suppress duplicate audit entries on dts-admin; platform records its own auth audits.
        headers.set("X-Audit-Silent", "true");
        propagateForwardedHeaders(headers);
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

    private void propagateForwardedHeaders(HttpHeaders headers) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            if (request == null) {
                return;
            }
            String forwardedCombined = request.getHeader("Forwarded");
            String forwarded = request.getHeader("X-Forwarded-For");
            String realIp = request.getHeader("X-Real-IP");
            String remote = request.getRemoteAddr();
            String resolved = IpAddressUtils.resolveClientIp(forwarded, realIp, remote);

            StringBuilder chain = new StringBuilder();
            if (StringUtils.hasText(forwarded)) {
                chain.append(forwarded.trim());
            }
            if (StringUtils.hasText(resolved)) {
                String resolvedTrimmed = resolved.trim();
                if (chain.length() == 0) {
                    chain.append(resolvedTrimmed);
                } else if (!forwardedContains(chain.toString(), resolvedTrimmed)) {
                    chain.insert(0, resolvedTrimmed + ", ");
                }
            } else if (chain.length() == 0 && StringUtils.hasText(remote)) {
                chain.append(remote.trim());
            }

            if (chain.length() > 0) {
                headers.set("X-Forwarded-For", chain.toString());
            }
            if (StringUtils.hasText(resolved)) {
                headers.set("X-Real-IP", resolved.trim());
            } else if (StringUtils.hasText(realIp)) {
                headers.set("X-Real-IP", realIp.trim());
            } else if (StringUtils.hasText(remote)) {
                headers.set("X-Real-IP", remote.trim());
            }
            if (StringUtils.hasText(forwardedCombined)) {
                headers.set("Forwarded", forwardedCombined.trim());
            } else if (StringUtils.hasText(resolved)) {
                headers.set("Forwarded", "for=\"" + resolved.trim() + "\"");
            }
            String outboundForwarded = headers.getFirst("X-Forwarded-For");
            String outboundForwardedCombined = headers.getFirst("Forwarded");
            boolean fallbackToRemote = StringUtils.hasText(resolved)
                && StringUtils.hasText(remote)
                && resolved.trim().equals(remote.trim());
            boolean missingForwarded = !StringUtils.hasText(forwarded);
            if (log.isInfoEnabled()) {
                log.info("[admin-auth-client-ip] forwarded='{}' forwardedStd='{}' real='{}' remote='{}' resolved='{}' outbound='{}' outboundStd='{}' fallbackToRemote={} missingForwarded={}",
                    nullSafe(forwarded),
                    nullSafe(forwardedCombined),
                    nullSafe(realIp),
                    nullSafe(remote),
                    nullSafe(resolved),
                    nullSafe(outboundForwarded),
                    nullSafe(outboundForwardedCombined),
                    fallbackToRemote,
                    missingForwarded
                );
            } else if (log.isDebugEnabled()) {
                log.debug("[admin-auth-client-ip] forwarded='{}' forwardedStd='{}' real='{}' remote='{}' resolved='{}' outbound='{}' outboundStd='{}' fallbackToRemote={} missingForwarded={}",
                    nullSafe(forwarded),
                    nullSafe(forwardedCombined),
                    nullSafe(realIp),
                    nullSafe(remote),
                    nullSafe(resolved),
                    nullSafe(outboundForwarded),
                    nullSafe(outboundForwardedCombined),
                    fallbackToRemote,
                    missingForwarded
                );
            }
        } catch (Exception ex) {
            log.debug("Failed to propagate client IP headers: {}", ex.getMessage());
        }
    }

    private boolean forwardedContains(String chain, String candidate) {
        if (!StringUtils.hasText(chain) || !StringUtils.hasText(candidate)) {
            return false;
        }
        String[] parts = chain.split(",");
        for (String part : parts) {
            if (candidate.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private String asText(Object v) {
        return v == null ? null : v.toString();
    }

    private Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
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

    public record LoginResult(
        Map<String, Object> user,
        String accessToken,
        String refreshToken,
        Long accessTokenExpiresIn,
        Long refreshTokenExpiresIn
    ) {}

    public record RefreshResult(String accessToken, String refreshToken, Long accessTokenExpiresIn, Long refreshTokenExpiresIn) {}

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
