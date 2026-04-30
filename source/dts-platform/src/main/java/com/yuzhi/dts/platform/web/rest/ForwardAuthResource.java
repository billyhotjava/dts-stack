package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Traefik forward-auth endpoint for downstream modules (e.g. analytics).
 *
 * <p>This endpoint relies on platform's existing browser-scoped session cookie.
 * Traefik calls this endpoint with the incoming platform cookies and blocks the downstream
 * request unless this endpoint returns 2xx.
 */
@RestController
@RequestMapping("/api")
public class ForwardAuthResource {

    private static final String EXTERNAL_REDIRECT_ROUTE = "/external-redirect?target=";
    private static final List<String> API_PREFIXES = List.of(
        "/api",
        "/analytics/api",
        "/bi/api",
        "/openapi",
        "/graphql",
        "/ws",
        "/socket",
        "/sockjs",
        "/websocket"
    );
    private static final List<String> STATIC_ASSET_SUFFIXES = List.of(
        ".js",
        ".mjs",
        ".css",
        ".map",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".svg",
        ".webp",
        ".ico",
        ".woff",
        ".woff2",
        ".ttf",
        ".eot",
        ".json",
        ".txt"
    );

    @org.springframework.beans.factory.annotation.Value("${dts.platform.public-base-url:}")
    private String platformPublicBaseUrl = "";

    ForwardAuthResource() {}

    ForwardAuthResource(String platformPublicBaseUrl) {
        this.platformPublicBaseUrl = platformPublicBaseUrl;
    }

    @RequestMapping(path = "/forward-auth", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> forwardAuth(Authentication authentication, HttpServletRequest request) {
        String forwardedUri = request == null ? null : request.getHeader("X-Forwarded-Uri");
        String forwardedPrefix = request == null ? null : request.getHeader("X-Forwarded-Prefix");
        String requestedPath = resolveRequestedPath(forwardedUri, forwardedPrefix);
        boolean isApiRequest = isApiRequestPath(requestedPath);
        boolean isUiRequest = !isApiRequest && !isStaticAssetRequest(requestedPath);

        if (authentication == null || !authentication.isAuthenticated()) {
            return unauthorized(isApiRequest, isUiRequest, requestedPath, request);
        }

        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(username)) {
            return unauthorized(isApiRequest, isUiRequest, requestedPath, request);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-DTS-User", username);
        SecurityUtils.getCurrentUserDisplayName().filter(StringUtils::hasText).ifPresent(v -> headers.add("X-DTS-Display-Name", v));
        SecurityUtils.getCurrentUserId().filter(StringUtils::hasText).ifPresent(v -> headers.add("X-DTS-User-Id", v));

        List<String> roles = new ArrayList<>(authorities(authentication));
        // Ensure opadmin always carries ROLE_OP_ADMIN regardless of token content
        if ("opadmin".equalsIgnoreCase(username) && !roles.contains("ROLE_OP_ADMIN")) {
            roles.add("ROLE_OP_ADMIN");
        }
        if (!roles.isEmpty()) {
            headers.add("X-DTS-Roles", String.join(",", roles));
        }

        extractPrincipal(authentication)
                .flatMap(principal -> optionalStringList(principal.getAttributes().get("permissions")))
                .ifPresent(perms -> headers.add("X-DTS-Permissions", String.join(",", perms)));

        // Extract dept_code and personnel_level from JWT claims or OAuth2 principal attributes.
        // JwtAuthenticationToken wraps a Jwt (not OAuth2AuthenticatedPrincipal), so we must
        // check both paths to ensure these fields are propagated to downstream services.
        Map<String, Object> claimsMap = extractClaims(authentication);
        Optional<OAuth2AuthenticatedPrincipal> principalOpt = extractPrincipal(authentication);

        optionalString(claimsMap.get("dept_code"))
            .or(() -> optionalString(claimsMap.get("deptCode")))
            .or(() -> principalOpt.flatMap(p -> optionalString(p.getAttribute("dept_code"))))
            .or(() -> principalOpt.flatMap(p -> optionalString(p.getAttribute("deptCode"))))
            .ifPresent(v -> headers.add("X-DTS-Dept-Code", v));

        optionalString(claimsMap.get("person_security_level"))
            .or(() -> optionalString(claimsMap.get("personnel_level")))
            .or(() -> optionalString(claimsMap.get("personnelLevel")))
            .or(() -> principalOpt.flatMap(p -> optionalString(p.getAttribute("person_security_level"))))
            .or(() -> principalOpt.flatMap(p -> optionalString(p.getAttribute("personnel_level"))))
            .or(() -> principalOpt.flatMap(p -> optionalString(p.getAttribute("personnelLevel"))))
            .ifPresent(v -> headers.add("X-DTS-Personnel-Level", v));

        return ResponseEntity.noContent().headers(headers).build();
    }

    private ResponseEntity<Void> unauthorized(
            boolean isApiRequest,
            boolean isUiRequest,
            String requestedPath,
            HttpServletRequest request
    ) {
        if (isUiRequest) {
            return ResponseEntity.status(302).headers(buildRedirectHeaders(requestedPath, request)).build();
        }
        if (isApiRequest) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(401).build();
    }

    private HttpHeaders buildRedirectHeaders(String requestedPath, HttpServletRequest request) {
        String redirectTarget = buildLoginRedirectTarget(requestedPath, request);
        String encodedRedirectTarget = URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8);
        String loginPath =
            "/auth/login?redirect=" + encodedRedirectTarget + "#/auth/login?redirect=" + encodedRedirectTarget;
        String loginOrigin = resolvePlatformOrigin(request);
        String loginUrl = StringUtils.hasText(loginOrigin) ? loginOrigin + loginPath : loginPath;
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, loginUrl);
        return headers;
    }

    private String buildLoginRedirectTarget(String requestedPath, HttpServletRequest request) {
        String absoluteTarget = resolveAbsoluteTarget(requestedPath, request);
        if (StringUtils.hasText(absoluteTarget)) {
            return EXTERNAL_REDIRECT_ROUTE + URLEncoder.encode(absoluteTarget, StandardCharsets.UTF_8);
        }
        return StringUtils.hasText(requestedPath) ? requestedPath : "/bi";
    }

    private String resolvePlatformOrigin(HttpServletRequest request) {
        String configured = normalizeOrigin(platformPublicBaseUrl);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return resolveForwardedOrigin(request);
    }

    private static String resolveAbsoluteTarget(String requestedPath, HttpServletRequest request) {
        String origin = resolveForwardedOrigin(request);
        if (!StringUtils.hasText(origin) || !StringUtils.hasText(requestedPath)) {
            return null;
        }
        return origin + requestedPath;
    }

    private static String resolveForwardedOrigin(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xfProtoRaw = request.getHeader("X-Forwarded-Proto");
        String xfHostRaw = request.getHeader("X-Forwarded-Host");
        String xfProto = StringUtils.hasText(xfProtoRaw) ? xfProtoRaw.split(",")[0].trim() : null;
        String xfHost = StringUtils.hasText(xfHostRaw) ? xfHostRaw.split(",")[0].trim() : null;
        if (!StringUtils.hasText(xfProto) || !StringUtils.hasText(xfHost)) {
            return null;
        }
        return normalizeOrigin(xfProto + "://" + xfHost);
    }

    private static String normalizeOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim().replaceAll("/+$", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isApiRequestPath(String path) {
        String normalized = normalizeComparablePath(path);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return API_PREFIXES.stream().anyMatch(prefix -> normalized.equals(prefix) || normalized.startsWith(prefix + "/"));
    }

    private static boolean isStaticAssetRequest(String path) {
        String normalized = normalizeComparablePath(path);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return STATIC_ASSET_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private static String normalizeComparablePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim().toLowerCase();
        int queryIdx = normalized.indexOf('?');
        if (queryIdx >= 0) {
            normalized = normalized.substring(0, queryIdx);
        }
        int hashIdx = normalized.indexOf('#');
        if (hashIdx >= 0) {
            normalized = normalized.substring(0, hashIdx);
        }
        return normalized;
    }

    private static String resolveRequestedPath(String forwardedUri, String forwardedPrefix) {
        String normalizedUri = normalizePath(forwardedUri);
        String normalizedPrefix = normalizePath(forwardedPrefix);
        if (StringUtils.hasText(normalizedPrefix) && StringUtils.hasText(normalizedUri)) {
            if (normalizedUri.equals(normalizedPrefix) || normalizedUri.startsWith(normalizedPrefix + "/")) {
                return normalizedUri;
            }
            return joinPaths(normalizedPrefix, normalizedUri);
        }
        if (StringUtils.hasText(normalizedUri) && normalizedUri.startsWith("/")) {
            return normalizedUri;
        }
        if (StringUtils.hasText(normalizedPrefix)) {
            return normalizedPrefix;
        }
        return "/";
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String trimmed = path.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String joinPaths(String prefix, String suffix) {
        if (!StringUtils.hasText(prefix)) {
            return suffix;
        }
        if (!StringUtils.hasText(suffix)) {
            return prefix;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        String normalizedSuffix = suffix.startsWith("/") ? suffix : "/" + suffix;
        return normalizedPrefix + normalizedSuffix;
    }

    private static List<String> authorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication == null ? List.of() : authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return List.of();
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private static Map<String, Object> extractClaims(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwt != null ? jwt.getClaims() : Map.of();
        }
        return Map.of();
    }

    private static Optional<OAuth2AuthenticatedPrincipal> extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth) {
            return Optional.of(oauth);
        }
        return Optional.empty();
    }

    private static Optional<List<String>> optionalStringList(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        }
        if (value instanceof String s) {
            String text = s.trim();
            if (text.isEmpty()) return Optional.empty();
            return Optional.of(List.of(text));
        }
        if (value instanceof Map<?, ?> map) {
            Object candidate = map.get("value");
            if (candidate == null) candidate = map.get("permissions");
            return optionalStringList(candidate);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.of(text));
    }

    private static Optional<String> optionalString(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String s) {
            String text = s.trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Optional<String> v = optionalString(item);
                if (v.isPresent()) return v;
            }
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            Object candidate = map.get("value");
            if (candidate == null) candidate = map.get("code");
            if (candidate == null) candidate = map.get("id");
            return optionalString(candidate);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
