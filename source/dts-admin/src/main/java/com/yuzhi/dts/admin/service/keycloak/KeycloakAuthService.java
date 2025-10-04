package com.yuzhi.dts.admin.service.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin client for authenticating against Keycloak using the direct access grant flow.
 */
@Service
public class KeycloakAuthService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final URI tokenEndpoint;
    private final URI userInfoEndpoint;
    private final URI logoutEndpoint;
    private final URI revokeEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String scopeParam;

    public KeycloakAuthService(
        @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}") String issuerUri,
        @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId,
        @Value("${spring.security.oauth2.client.registration.oidc.client-secret:}") String clientSecret,
        @Value("${spring.security.oauth2.client.registration.oidc.scope:openid,profile,email,offline_access}") String scope,
        RestTemplateBuilder restTemplateBuilder,
        ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.scopeParam = normaliseScope(scope);
        this.tokenEndpoint = UriComponentsBuilder.fromUriString(issuerUri).path("/protocol/openid-connect/token").build().toUri();
        this.userInfoEndpoint = UriComponentsBuilder.fromUriString(issuerUri).path("/protocol/openid-connect/userinfo").build().toUri();
        this.logoutEndpoint = UriComponentsBuilder.fromUriString(issuerUri).path("/protocol/openid-connect/logout").build().toUri();
        this.revokeEndpoint = UriComponentsBuilder.fromUriString(issuerUri).path("/protocol/openid-connect/revoke").build().toUri();
        log.info("Keycloak OIDC endpoints: issuer={}, token={}, userinfo={}", issuerUri, tokenEndpoint, userInfoEndpoint);
    }

    public TokenResponse obtainToken(String username, String password) {
        return exchangeForTokens(username, password);
    }

    public LoginResult login(String username, String password) {
        TokenResponse tokens = exchangeForTokens(username, password);
        Map<String, Object> claims = decodeTokenClaims(tokens.accessToken());
        Map<String, Object> userInfo = fetchUserInfo(tokens.accessToken());
        Map<String, Object> enrichedUser = buildUserProfile(username, userInfo, claims);
        return new LoginResult(tokens, enrichedUser);
    }

    /**
     * Refresh tokens using Keycloak's refresh_token grant.
     */
    public TokenResponse refreshTokens(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                TokenResponse.class
            );
            TokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null) {
                throw new IllegalStateException("Keycloak refresh response missing access_token");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            throw translateAuthError(ex);
        }
    }

    private TokenResponse exchangeForTokens(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        if (!scopeParam.isBlank()) {
            form.add("scope", scopeParam);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                TokenResponse.class
            );
            TokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null) {
                throw new IllegalStateException("Keycloak token response missing access_token");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            throw translateAuthError(ex);
        }
    }

    public TokenResponse obtainClientCredentialsToken(String clientId, String clientSecretOverride) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        if (clientSecretOverride != null && !clientSecretOverride.isBlank()) {
            form.add("client_secret", clientSecretOverride);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                TokenResponse.class
            );
            TokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null) {
                throw new IllegalStateException("Keycloak client credentials response missing access_token");
            }
            return body;
        } catch (HttpStatusCodeException ex) {
            throw translateAuthError(ex);
        }
    }

    /**
     * Perform RP-initiated logout to invalidate the refresh token/session in Keycloak.
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                logoutEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Void.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Keycloak logout failed: status=" + response.getStatusCode());
            }
        } catch (HttpStatusCodeException ex) {
            throw translateAuthError(ex);
        }
    }

    /**
     * Revoke refresh token as a safety fallback.
     */
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.exchange(
                revokeEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Void.class
            );
        } catch (RestClientException ex) {
            // best-effort; ignore
            log.debug("Keycloak revoke failed: {}", ex.getMessage());
        }
    }

    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                userInfoEndpoint,
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null || body.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.convertValue(body, MAP_TYPE);
        } catch (HttpStatusCodeException ex) {
            log.warn("Keycloak userinfo request failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return new HashMap<>();
        } catch (RestClientException ex) {
            log.warn("Keycloak userinfo request failed: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, Object> buildUserProfile(String fallbackUsername, Map<String, Object> userInfo, Map<String, Object> claims) {
        Map<String, Object> user = new HashMap<>();
        if (userInfo != null) {
            user.putAll(userInfo);
        }

        String username = firstNonBlank(
            user.get("preferred_username"),
            claims.get("preferred_username"),
            fallbackUsername
        );
        String givenName = firstNonBlank(user.get("given_name"), claims.get("given_name"), username);
        String familyName = firstNonBlank(user.get("family_name"), claims.get("family_name"));
        String fullName = firstNonBlank(user.get("name"), claims.get("name"), givenName, username);
        String email = firstNonBlank(user.get("email"), claims.get("email"));

        user.put("id", firstNonBlank(user.get("sub"), claims.get("sub"), UUID.randomUUID().toString()));
        user.put("username", username);
        user.put("firstName", givenName);
        user.put("lastName", familyName);
        user.put("fullName", fullName);
        user.put("email", email);
        user.put("enabled", Boolean.TRUE);

        List<String> roles = extractRoles(claims);
        user.put("roles", roles);

        List<String> groups = toStringList(claims.get("groups"));
        if (!groups.isEmpty()) {
            user.put("groups", groups);
        }

        user.putIfAbsent("permissions", Collections.emptyList());
        user.putIfAbsent("attributes", Collections.emptyMap());

        return user;
    }

    private Map<String, Object> decodeTokenClaims(String token) {
        if (token == null || token.isBlank()) {
            return Collections.emptyMap();
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Collections.emptyMap();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (IllegalArgumentException | java.io.IOException e) {
            log.warn("Failed to decode Keycloak access token claims: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<String> extractRoles(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        Set<String> roles = new LinkedHashSet<>();

        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            roles.addAll(toStringList(realmMap.get("roles")));
        }

        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object value : resourceMap.values()) {
                if (value instanceof Map<?, ?> client) {
                    roles.addAll(toStringList(client.get("roles")));
                }
            }
        }

        return List.copyOf(roles);
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        if (value instanceof Object[] array) {
            List<String> result = new ArrayList<>(array.length);
            for (Object item : array) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String s) {
                if (!s.isBlank()) {
                    return s;
                }
            } else {
                String text = value.toString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private RuntimeException translateAuthError(HttpStatusCodeException ex) {
        String detail = extractErrorMessage(ex.getResponseBodyAsString());
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
            String message = detail.isBlank() ? "用户名或密码错误" : detail;
            return new BadCredentialsException(message, ex);
        }
        String message = detail.isBlank() ? "Keycloak认证失败" : detail;
        return new IllegalStateException(message, ex);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("error_description")) {
                return node.get("error_description").asText();
            }
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (java.io.IOException e) {
            log.debug("Failed to parse Keycloak error body: {}", e.getMessage());
        }
        return body;
    }

    private String normaliseScope(String scope) {
        if (scope == null) {
            return "";
        }
        String[] parts = scope.split(",|\\s+");
        List<String> scopes = new ArrayList<>();
        for (String part : parts) {
            if (part != null) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    scopes.add(trimmed);
                }
            }
        }
        return String.join(" ", scopes);
    }

    public record LoginResult(TokenResponse tokens, Map<String, Object> user) {}

    public record TokenResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
        @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
        @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn,
        @com.fasterxml.jackson.annotation.JsonProperty("refresh_expires_in") Long refreshExpiresIn,
        @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
        @com.fasterxml.jackson.annotation.JsonProperty("id_token") String idToken,
        @com.fasterxml.jackson.annotation.JsonProperty("session_state") String sessionState,
        @com.fasterxml.jackson.annotation.JsonProperty("scope") String scope
    ) {}
}
