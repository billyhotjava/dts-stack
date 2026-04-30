package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class PortalSessionResource {

    private final PortalSessionRegistry sessionRegistry;
    private final PortalSessionCookieService cookieService;

    public PortalSessionResource(PortalSessionRegistry sessionRegistry, PortalSessionCookieService cookieService) {
        this.sessionRegistry = sessionRegistry;
        this.cookieService = cookieService;
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> current(HttpServletRequest request) {
        Map<String, Object> data = currentFromSecurityContext(request);
        if (data != null) {
            return ResponseEntity.ok(ApiResponses.ok(data));
        }
        data = currentFromCookie(request);
        if (data != null) {
            return ResponseEntity.ok(ApiResponses.ok(data));
        }
        return ResponseEntity.ok(ApiResponses.ok(Map.of("authenticated", false)));
    }

    private Map<String, Object> currentFromSecurityContext(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2AuthenticatedPrincipal oauthPrincipal)) {
            return null;
        }

        Map<String, Object> attrs = oauthPrincipal.getAttributes();
        String username = firstNonBlank(
            stringValue(attrs.get(OAuth2TokenIntrospectionClaimNames.USERNAME)),
            stringValue(attrs.get("preferred_username")),
            stringValue(attrs.get("sub"))
        );
        if (!StringUtils.hasText(username)) {
            return null;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authenticated", true);
        data.put("username", username);
        putIfText(data, "displayName", firstNonBlank(stringValue(attrs.get("displayName")), stringValue(attrs.get("name"))));
        putIfText(data, "browserId", firstNonBlank(stringValue(attrs.get("browser_id")), resolveBrowserId(request)));
        putExpiresAt(data, attrs.get(OAuth2TokenIntrospectionClaimNames.EXP));
        return data;
    }

    private Map<String, Object> currentFromCookie(HttpServletRequest request) {
        if (cookieService == null || sessionRegistry == null) {
            return null;
        }
        String token = cookieService.resolvePortalSessionToken(request);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        PortalSession session = sessionRegistry.findByAccessToken(token).orElse(null);
        if (session == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authenticated", true);
        data.put("username", session.username());
        putIfText(data, "displayName", session.displayName());
        putIfText(data, "browserId", resolveBrowserId(request));
        if (session.expiresAt() != null) {
            data.put("expiresAt", session.expiresAt().toString());
        }
        return data;
    }

    private String resolveBrowserId(HttpServletRequest request) {
        if (cookieService == null) {
            return null;
        }
        return cookieService.resolveBrowserId(request);
    }

    private void putExpiresAt(Map<String, Object> data, Object value) {
        if (value instanceof Instant instant) {
            data.put("expiresAt", instant.toString());
            return;
        }
        if (value instanceof Number number) {
            data.put("expiresAt", Instant.ofEpochSecond(number.longValue()).toString());
            return;
        }
        putIfText(data, "expiresAt", stringValue(value));
    }

    private void putIfText(Map<String, Object> data, String key, String value) {
        if (StringUtils.hasText(value)) {
            data.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
