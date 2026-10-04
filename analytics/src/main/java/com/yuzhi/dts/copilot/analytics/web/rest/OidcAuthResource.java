package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.config.OidcProperties;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsLoginHistory;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsLoginHistoryRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.GroupService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@ConditionalOnProperty(prefix = "dts.analytics.oidc", name = "enabled", havingValue = "true")
public class OidcAuthResource {

    private static final String OIDC_STATE_COOKIE = "dts.OIDC_STATE";
    private static final String OIDC_REDIRECT_COOKIE = "dts.OIDC_REDIRECT";

    private final OidcProperties properties;
    private final ObjectMapper objectMapper;
    private final AnalyticsUserRepository userRepository;
    private final AnalyticsSessionService sessionService;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsLoginHistoryRepository loginHistoryRepository;

    public OidcAuthResource(
            OidcProperties properties,
            ObjectMapper objectMapper,
            AnalyticsUserRepository userRepository,
            AnalyticsSessionService sessionService,
            GroupService groupService,
            PasswordEncoder passwordEncoder,
            AnalyticsLoginHistoryRepository loginHistoryRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.groupService = groupService;
        this.passwordEncoder = passwordEncoder;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    @GetMapping("/auth/oidc/login")
    public ResponseEntity<?> login(HttpServletRequest request) {
        if (!StringUtils.hasText(properties.issuerUri()) || !StringUtils.hasText(properties.clientId())) {
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN).body("OIDC is enabled but issuer-uri/client-id is not configured.");
        }

        boolean secure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || "https".equalsIgnoreCase(request.getScheme());
        String forwardedPrefix = resolveForwardedPrefix(request);
        String externalHost = resolveExternalHost(request);
        String externalScheme = resolveExternalScheme(request);

        String redirectTo = forwardedPrefix.isBlank() || "/".equals(forwardedPrefix) ? "/" : normalizePrefix(forwardedPrefix) + "/";
        String redirectUri = externalScheme + "://" + externalHost + (forwardedPrefix.isBlank() ? "" : normalizePrefix(forwardedPrefix)) + "/auth/oidc/callback";

        String state = UUID.randomUUID().toString();
        String authUrl = keycloakAuthorizationEndpoint(properties.issuerUri())
                + "?response_type=code"
                + "&client_id=" + url(stateOrEmpty(properties.clientId()))
                + "&redirect_uri=" + url(redirectUri)
                + "&scope=" + url(String.join(" ", properties.scopes()))
                + "&state=" + url(state);

        ResponseCookie stateCookie = ResponseCookie.from(OIDC_STATE_COOKIE, state)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(10))
                .build();
        ResponseCookie redirectCookie = ResponseCookie.from(OIDC_REDIRECT_COOKIE, redirectTo)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(10))
                .build();

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, authUrl)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.SET_COOKIE, redirectCookie.toString())
                .build();
    }

    @GetMapping("/auth/oidc/callback")
    public ResponseEntity<?> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request) {
        boolean secure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || "https".equalsIgnoreCase(request.getScheme());

        String expectedState = readCookie(request, OIDC_STATE_COOKIE).orElse(null);
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state) || !Objects.equals(state, expectedState)) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("Invalid OIDC callback.");
        }

        String forwardedPrefix = resolveForwardedPrefix(request);
        String externalHost = resolveExternalHost(request);
        String externalScheme = resolveExternalScheme(request);
        String redirectUri = externalScheme + "://" + externalHost + (forwardedPrefix.isBlank() ? "" : normalizePrefix(forwardedPrefix)) + "/auth/oidc/callback";

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = exchangeCodeForTokens(code, redirectUri);
        } catch (Exception e) {
            return ResponseEntity.status(502).contentType(MediaType.TEXT_PLAIN).body("OIDC token exchange failed.");
        }

        String idToken = Objects.toString(tokenResponse.get("id_token"), "");
        Map<String, Object> claims;
        try {
            claims = decodeJwtClaims(idToken);
        } catch (Exception e) {
            return ResponseEntity.status(502).contentType(MediaType.TEXT_PLAIN).body("OIDC id_token decode failed.");
        }

        String email = firstNonBlank(
                Objects.toString(claims.get("email"), null),
                Objects.toString(claims.get("preferred_username"), null),
                Objects.toString(claims.get("upn"), null));
        if (!StringUtils.hasText(email)) {
            return ResponseEntity.status(502).contentType(MediaType.TEXT_PLAIN).body("OIDC id_token missing email/username.");
        }
        String firstName = firstNonBlank(Objects.toString(claims.get("given_name"), null), Objects.toString(claims.get("name"), null), "User");
        String lastName = firstNonBlank(Objects.toString(claims.get("family_name"), null), "OIDC");
        boolean isSuperuser = hasAdminRole(claims, properties.adminRole());

        AnalyticsUser user = userRepository.findByUsernameIgnoreCase(email).orElseGet(AnalyticsUser::new);
        boolean creating = user.getId() == null;
        user.setUsername(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);
        user.setSuperuser(isSuperuser);
        if (creating) {
            user.setPasswordHash(passwordEncoder.encode(randomString(24) + "1"));
        }
        user = userRepository.save(user);
        groupService.ensureUserInDefaultGroups(user);

        AnalyticsLoginHistory history = new AnalyticsLoginHistory();
        history.setUserId(user.getId());
        history.setIpAddress(resolveClientIp(request));
        history.setUserAgent(request.getHeader("User-Agent"));
        loginHistoryRepository.save(history);

        UUID sessionId = sessionService.createSession(user.getId());
        UUID deviceId = UUID.randomUUID();

        String redirectTo = readCookie(request, OIDC_REDIRECT_COOKIE).orElseGet(() -> forwardedPrefix.isBlank() ? "/" : normalizePrefix(forwardedPrefix) + "/");

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirectTo);
        for (String cookie : MetabaseCookies.loginCookieHeaders(sessionId, deviceId, secure)) {
            builder.header(HttpHeaders.SET_COOKIE, cookie);
        }
        builder.header(HttpHeaders.SET_COOKIE, expireCookie(OIDC_STATE_COOKIE, secure));
        builder.header(HttpHeaders.SET_COOKIE, expireCookie(OIDC_REDIRECT_COOKIE, secure));
        return builder.build();
    }

    private Map<String, Object> exchangeCodeForTokens(String code, String redirectUri) throws Exception {
        if (!StringUtils.hasText(properties.clientSecret())) {
            throw new IllegalStateException("client-secret is required");
        }

        String tokenEndpoint = keycloakTokenEndpoint(properties.issuerUri());
        String body = "grant_type=authorization_code"
                + "&code=" + url(code)
                + "&redirect_uri=" + url(redirectUri)
                + "&client_id=" + url(properties.clientId())
                + "&client_secret=" + url(properties.clientSecret());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("token endpoint returned " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        Map<String, Object> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue()));
        return out;
    }

    private Map<String, Object> decodeJwtClaims(String jwt) throws Exception {
        String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("invalid jwt");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(payload, Map.class);
    }

    private static String keycloakAuthorizationEndpoint(String issuerUri) {
        return normalizeIssuer(issuerUri) + "/protocol/openid-connect/auth";
    }

    private static String keycloakTokenEndpoint(String issuerUri) {
        return normalizeIssuer(issuerUri) + "/protocol/openid-connect/token";
    }

    private static String normalizeIssuer(String issuerUri) {
        String normalized = issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
        return normalized;
    }

    private static String url(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    private static String stateOrEmpty(String raw) {
        return raw == null ? "" : raw;
    }

    private static String resolveForwardedPrefix(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-Prefix"))
                .map(h -> h.split(",")[0].trim())
                .filter(StringUtils::hasText)
                .orElse("");
    }

    private static String resolveExternalHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (StringUtils.hasText(forwardedHost)) {
            return forwardedHost.split(",")[0].trim();
        }
        return request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
    }

    private static String resolveExternalScheme(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            return forwardedProto.split(",")[0].trim();
        }
        return request.getScheme();
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static String expireCookie(String name, boolean secure) {
        return ResponseCookie.from(name, "")
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasAdminRole(Map<String, Object> claims, String roleName) {
        if (!StringUtils.hasText(roleName) || claims == null) {
            return false;
        }
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof Collection<?> collection) {
                for (Object role : collection) {
                    if (Objects.equals(String.valueOf(role), roleName)) {
                        return true;
                    }
                }
            }
        }
        Object groups = claims.get("groups");
        if (groups instanceof Collection<?> collection) {
            for (Object group : collection) {
                if (StringUtils.hasText(String.valueOf(group)) && String.valueOf(group).contains(roleName)) {
                    return true;
                }
            }
        }
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            for (Object role : collection) {
                if (Objects.equals(String.valueOf(role), roleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String randomString(int minLength) {
        SecureRandom random = new SecureRandom();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int len = Math.max(minLength, 20);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
