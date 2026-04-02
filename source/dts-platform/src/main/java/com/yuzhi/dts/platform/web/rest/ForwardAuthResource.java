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

    @RequestMapping(path = "/forward-auth", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> forwardAuth(Authentication authentication, HttpServletRequest request) {
        String forwardedUri = request == null ? null : request.getHeader("X-Forwarded-Uri");
        String forwardedPrefix = request == null ? null : request.getHeader("X-Forwarded-Prefix");

        boolean isAnalyticsRequest =
                (forwardedUri != null && forwardedUri.startsWith("/analytics"))
                        || (forwardedPrefix != null && (forwardedPrefix.equals("/analytics") || forwardedPrefix.startsWith("/analytics/")));

        boolean isAnalyticsApiRequest =
                (forwardedUri != null && forwardedUri.startsWith("/analytics/api"))
                        || (isAnalyticsRequest && forwardedUri != null && forwardedUri.startsWith("/api"));

        boolean isAnalyticsUiRequest = isAnalyticsRequest && !isAnalyticsApiRequest;

        if (authentication == null || !authentication.isAuthenticated()) {
            return unauthorized(isAnalyticsApiRequest, isAnalyticsUiRequest, forwardedUri, forwardedPrefix, request);
        }

        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(username)) {
            return unauthorized(isAnalyticsApiRequest, isAnalyticsUiRequest, forwardedUri, forwardedPrefix, request);
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

    private static ResponseEntity<Void> unauthorized(
            boolean isAnalyticsApiRequest,
            boolean isAnalyticsUiRequest,
            String forwardedUri,
            String forwardedPrefix,
            HttpServletRequest request
    ) {
        if (isAnalyticsUiRequest) {
            String returnTo = "/analytics";
            if (StringUtils.hasText(forwardedUri) && forwardedUri.startsWith("/analytics")) {
                returnTo = forwardedUri;
            } else if (StringUtils.hasText(forwardedPrefix) && forwardedPrefix.startsWith("/analytics")) {
                returnTo = forwardedPrefix;
            }
            String encodedReturnTo = URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
            String loginPath = "/#/auth/login?redirect=" + encodedReturnTo;

            // Build an absolute URL using X-Forwarded-* (original external request), to avoid leaking
            // internal docker hostname (e.g. "dts-platform:8081") to the browser.
            // If X-Forwarded-* are absent, fall back to a relative URL.
            String loginUrl = loginPath;
            if (request != null) {
                String xfProtoRaw = request.getHeader("X-Forwarded-Proto");
                String xfHostRaw = request.getHeader("X-Forwarded-Host");
                String xfProto = StringUtils.hasText(xfProtoRaw) ? xfProtoRaw.split(",")[0].trim() : null;
                String xfHost = StringUtils.hasText(xfHostRaw) ? xfHostRaw.split(",")[0].trim() : null;
                if (StringUtils.hasText(xfProto) && StringUtils.hasText(xfHost)) {
                    loginUrl = xfProto + "://" + xfHost + loginPath;
                }
            }
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, loginUrl);
            return ResponseEntity.status(302).headers(headers).build();
        }
        if (isAnalyticsApiRequest) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.status(401).build();
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
