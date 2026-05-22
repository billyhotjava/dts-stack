package com.yuzhi.dts.platform.service.admin.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminAuthGateway {

    private static final ParameterizedTypeReference<AdminGatewayEnvelope<Map<String, Object>>> MAP_ENVELOPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<PkiChallengeView>> PKI_CHALLENGE_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    private final AdminGatewayTransport transport;
    private final PlatformOutboundAdminProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuthGateway(AdminGatewayTransport transport, PlatformOutboundAdminProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    public LoginResult login(String username, String password) {
        ensureEnabled();
        try {
            Map<String, Object> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.POST,
                "/keycloak/auth/platform/login?auditSilent=true",
                Map.of("username", username == null ? "" : username, "password", password == null ? "" : password),
                MAP_ENVELOPE,
                AdminGatewayRequestOptions.builder().auditSilent(true).includeServiceAuthorization(false).build()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> user = data == null ? Map.of() : (Map<String, Object>) data.getOrDefault("user", Map.of());
            return new LoginResult(
                user,
                asText(data == null ? null : data.get("accessToken")),
                asText(data == null ? null : data.get("refreshToken")),
                firstLong(data, "expiresIn", "accessTokenExpiresIn"),
                firstLong(data, "refreshExpiresIn", "refreshTokenExpiresIn")
            );
        } catch (AdminGatewayException ex) {
            if (ex.getUpstreamStatus() != null && (ex.getUpstreamStatus() == 400 || ex.getUpstreamStatus() == 401)) {
                throw new BadCredentialsException(messageFrom(ex.getMessage(), "auth failed"));
            }
            throw new IllegalStateException(messageFrom(ex.getMessage(), "auth failed"), ex);
        }
    }

    public ProfileResult profile(String username, Map<String, Object> keycloakUser, String keycloakAccessToken) {
        ensureEnabled();
        try {
            AdminGatewayRequestOptions.Builder options = AdminGatewayRequestOptions
                .builder()
                .auditSilent(true)
                .includeServiceAuthorization(false);
            if (StringUtils.hasText(keycloakAccessToken)) {
                String bearer = keycloakAccessToken.trim();
                options.header(HttpHeaders.AUTHORIZATION, bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
            }
            Map<String, Object> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.POST,
                "/keycloak/auth/platform/profile?auditSilent=true",
                Map.of("username", username == null ? "" : username, "user", keycloakUser == null ? Map.of() : keycloakUser),
                MAP_ENVELOPE,
                options.build()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> user = data == null ? Map.of() : (Map<String, Object>) data.getOrDefault("user", Map.of());
            return new ProfileResult(user);
        } catch (AdminGatewayException ex) {
            if (ex.getUpstreamStatus() != null && ex.getUpstreamStatus() == 404) {
                throw new ProfileEndpointUnavailableException(messageFrom(ex.getMessage(), "user profile endpoint unavailable"), ex);
            }
            if (ex.getUpstreamStatus() != null && (ex.getUpstreamStatus() == 401 || ex.getUpstreamStatus() == 403)) {
                throw new BadCredentialsException(messageFrom(ex.getMessage(), "user profile rejected"));
            }
            if (ex.getUpstreamStatus() != null && ex.getUpstreamStatus() == 400) {
                throw new IllegalArgumentException(messageFrom(ex.getMessage(), "user profile rejected"), ex);
            }
            throw new IllegalStateException(messageFrom(ex.getMessage(), "user profile unavailable"), ex);
        }
    }

    public void logout(String refreshToken) {
        ensureEnabled();
        try {
            transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.POST,
                "/keycloak/auth/logout?auditSilent=true",
                StringUtils.hasText(refreshToken) ? Map.of("refreshToken", refreshToken) : Map.of(),
                MAP_ENVELOPE,
                AdminGatewayRequestOptions.builder().auditSilent(true).includeServiceAuthorization(false).build()
            );
        } catch (AdminGatewayException ignored) {
            // best effort
        }
    }

    public RefreshResult refresh(String refreshToken) {
        ensureEnabled();
        try {
            Map<String, Object> data = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.POST,
                "/keycloak/auth/refresh?auditSilent=true",
                Map.of("refreshToken", refreshToken == null ? "" : refreshToken),
                MAP_ENVELOPE,
                AdminGatewayRequestOptions.builder().auditSilent(true).includeServiceAuthorization(false).build()
            );
            return new RefreshResult(
                asText(data == null ? null : data.get("accessToken")),
                asText(data == null ? null : data.get("refreshToken")),
                firstLong(data, "expiresIn", "accessTokenExpiresIn"),
                firstLong(data, "refreshExpiresIn", "refreshTokenExpiresIn")
            );
        } catch (AdminGatewayException ex) {
            throw new IllegalStateException(messageFrom(ex.getMessage(), "refresh failed"), ex);
        }
    }

    public PkiChallengeView getPkiChallenge() {
        ensureEnabled();
        return transport.exchangeEnvelopeData(
            AdminGatewayTarget.API,
            HttpMethod.GET,
            "/keycloak/auth/pki-challenge",
            null,
            PKI_CHALLENGE_ENVELOPE,
            AdminGatewayRequestOptions.builder().includeServiceAuthorization(false).build()
        );
    }

    public Map<String, Object> pkiLogin(Map<String, Object> payload) {
        ensureEnabled();
        return transport.exchangeEnvelopeData(
            AdminGatewayTarget.API,
            HttpMethod.POST,
            "/keycloak/auth/pki-login",
            payload == null ? Map.of() : payload,
            MAP_ENVELOPE,
            AdminGatewayRequestOptions.builder().auditSilent(true).includeServiceAuthorization(false).build()
        );
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("dts-admin 服务调用已禁用");
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long firstLong(Map<String, Object> data, String primary, String secondary) {
        Long primaryValue = asLong(data == null ? null : data.get(primary));
        return primaryValue != null ? primaryValue : asLong(data == null ? null : data.get(secondary));
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String messageFrom(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {}
        return raw;
    }

    public record LoginResult(
        Map<String, Object> user,
        String accessToken,
        String refreshToken,
        Long accessTokenExpiresIn,
        Long refreshTokenExpiresIn
    ) {}

    public record RefreshResult(String accessToken, String refreshToken, Long accessTokenExpiresIn, Long refreshTokenExpiresIn) {}

    public record PkiChallengeView(String challengeId, String nonce, String aud, Long ts, Long exp) {}

    public record ProfileResult(Map<String, Object> user) {}

    public static class ProfileEndpointUnavailableException extends RuntimeException {

        public ProfileEndpointUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
