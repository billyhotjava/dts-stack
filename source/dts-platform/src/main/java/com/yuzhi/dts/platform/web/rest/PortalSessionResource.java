package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        Map<String, Object> data = new LinkedHashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && SecurityUtils.isAuthenticated()) {
            data.put("authenticated", Boolean.TRUE);
            attributes(authentication).forEach((key, value) -> applySessionAttribute(data, key, value));
            return ResponseEntity.ok(ApiResponses.ok(data));
        }

        PortalSession session = sessionRegistry.findByAccessToken(cookieService.resolvePortalSessionToken(request)).orElse(null);
        if (session == null) {
            data.put("authenticated", Boolean.FALSE);
            return ResponseEntity.ok(ApiResponses.ok(data));
        }
        data.put("authenticated", Boolean.TRUE);
        data.put("username", session.username());
        if (session.displayName() != null && !session.displayName().isBlank()) {
            data.put("displayName", session.displayName());
        }
        if (session.browserId() != null && !session.browserId().isBlank()) {
            data.put("browserId", session.browserId());
        }
        data.put("roles", session.roles());
        data.put("permissions", session.permissions());
        if (session.deptCode() != null && !session.deptCode().isBlank()) {
            data.put("deptCode", session.deptCode());
        }
        if (session.personnelLevel() != null && !session.personnelLevel().isBlank()) {
            data.put("personnelLevel", session.personnelLevel());
        }
        if (session.expiresAt() != null) {
            data.put("expiresAt", session.expiresAt().toString());
        }
        return ResponseEntity.ok(ApiResponses.ok(data));
    }

    private void applySessionAttribute(Map<String, Object> data, String key, Object value) {
        if (value == null) {
            return;
        }
        switch (key) {
            case OAuth2TokenIntrospectionClaimNames.USERNAME, "preferred_username", "sub" -> data.putIfAbsent("username", String.valueOf(value));
            case "displayName", "full_name", "name" -> data.putIfAbsent("displayName", String.valueOf(value));
            case "browser_id" -> data.put("browserId", String.valueOf(value));
            case "roles", "permissions" -> data.put(key, value);
            case "dept_code" -> data.put("deptCode", String.valueOf(value));
            case "personnel_level" -> data.put("personnelLevel", String.valueOf(value));
            case "exp" -> {
                if (value instanceof Instant instant) {
                    data.put("expiresAt", instant.toString());
                } else {
                    data.put("expiresAt", String.valueOf(value));
                }
            }
            default -> {
                // ignore irrelevant claims
            }
        }
    }

    private Map<String, Object> attributes(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaims();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth) {
            return oauth.getAttributes();
        }
        return Map.of();
    }
}
