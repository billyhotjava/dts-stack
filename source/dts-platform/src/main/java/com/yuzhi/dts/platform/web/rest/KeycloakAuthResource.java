package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.service.admin.AdminAuthClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/keycloak/auth")
public class KeycloakAuthResource {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthResource.class);
    private final PortalSessionRegistry sessionRegistry;
    private final AdminAuthClient adminAuthClient;

    public KeycloakAuthResource(PortalSessionRegistry sessionRegistry, AdminAuthClient adminAuthClient) {
        this.sessionRegistry = sessionRegistry;
        this.adminAuthClient = adminAuthClient;
    }

    public record LoginPayload(String username, String password) {}
    public record RefreshPayload(String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginPayload payload) {
        String username = payload.username() == null ? "" : payload.username().trim();
        String password = payload.password() == null ? "" : payload.password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponses.error("用户名或密码不能为空"));
        }

        String norm = username.toLowerCase(Locale.ROOT);
        if (List.of("sysadmin", "authadmin", "auditadmin").contains(norm)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error("系统管理角色用户不能登录业务平台"));
        }

        try {
            if (log.isInfoEnabled()) {
                log.info("[login] attempt username={}", username);
            }
            var result = adminAuthClient.login(username, password);
            Map<String, Object> user = result.user();
            // Strengthen triad rejection: check raw Keycloak roles BEFORE mapping
            List<String> rawRoles = toStringList(user.get("roles"));
            if (containsTriadOriginal(rawRoles)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error("系统管理角色用户不能登录业务平台"));
            }
            // Normalize and map roles from Keycloak into platform authorities
            List<String> mappedRoles = mapRoles(rawRoles);

            // Derive basic permissions
            List<String> permissions = new ArrayList<>();
            permissions.add("portal.view");
            if (mappedRoles.contains(AuthoritiesConstants.OP_ADMIN)) {
                permissions.add("portal.manage");
                permissions.add("catalog.manage");
                permissions.add("governance.manage");
                permissions.add("iam.manage");
            }

            // Issue a portal session (opaque tokens) for platform API access
            PortalSession session = sessionRegistry.createSession(username, mappedRoles, permissions);

            // Build user payload (override roles/permissions with mapped ones)
            Map<String, Object> userOut = new java.util.HashMap<>(user);
            userOut.put("roles", mappedRoles);
            userOut.put("permissions", permissions);
            userOut.putIfAbsent("enabled", Boolean.TRUE);
            userOut.putIfAbsent("id", UUID.nameUUIDFromBytes(username.getBytes()).toString());

            Map<String, Object> data = Map.of(
                "user",
                userOut,
                "accessToken",
                session.accessToken(),
                "refreshToken",
                session.refreshToken()
            );
            if (log.isInfoEnabled()) {
                log.info("[login] success username={} roles={} perms={}", username, mappedRoles, permissions);
            }
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            log.warn("[login] unauthorized username={} reason={}", username, ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error(ex.getMessage()));
        } catch (Exception ex) {
            String msg = ex.getMessage() == null || ex.getMessage().isBlank() ? "登录失败，请稍后重试" : ex.getMessage();
            log.error("[login] error username={} msg={}", username, msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponses.error(msg));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshPayload payload) {
        if (payload != null && StringUtils.hasText(payload.refreshToken())) {
            try {
                adminAuthClient.logout(payload.refreshToken());
            } catch (Exception ignore) {
                // best-effort
            }
            sessionRegistry.invalidateByRefreshToken(payload.refreshToken());
        }
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody RefreshPayload payload) {
        try {
            PortalSession refreshed = sessionRegistry.refreshSession(payload.refreshToken());
            Map<String, String> data = Map.of(
                "accessToken",
                refreshed.accessToken(),
                "refreshToken",
                refreshed.refreshToken()
            );
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401).body(ApiResponses.error("刷新令牌无效，请重新登录"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(401).body(ApiResponses.error("会话已过期，请重新登录"));
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof java.util.Collection<?> c) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : c) if (o != null) out.add(o.toString());
            return out;
        }
        if (value instanceof String s) return java.util.List.of(s);
        return java.util.List.of();
    }

    private boolean containsTriad(List<String> roles) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String r : roles) set.add(r.toUpperCase());
        return set.contains("ROLE_SYS_ADMIN") || set.contains("ROLE_AUTH_ADMIN") || set.contains("ROLE_SECURITY_AUDITOR");
    }

    // Detect triad roles from raw Keycloak role names (with or without ROLE_ prefix and common aliases)
    private boolean containsTriadOriginal(List<String> roles) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String r : roles) if (r != null) set.add(r.toUpperCase());
        return set.contains("ROLE_SYS_ADMIN") || set.contains("SYS_ADMIN") ||
               set.contains("ROLE_AUTH_ADMIN") || set.contains("AUTH_ADMIN") ||
               set.contains("ROLE_SECURITY_AUDITOR") || set.contains("SECURITY_AUDITOR") ||
               set.contains("ROLE_AUDITOR_ADMIN") || set.contains("AUDITOR_ADMIN") ||
               set.contains("ROLE_AUDIT_ADMIN") || set.contains("AUDIT_ADMIN") ||
               set.contains("ROLE_AUDITADMIN") || set.contains("AUDITADMIN");
    }

    

    private List<String> mapRoles(List<String> kcRoles) {
        java.util.Set<String> mapped = new java.util.LinkedHashSet<>();
        for (String raw : kcRoles) {
            if (raw == null || raw.isBlank()) continue;
            String up = raw.toUpperCase(Locale.ROOT);
            if (up.startsWith("ROLE_")) up = up.substring(5);
            switch (up) {
                case "OP_ADMIN", "OPADMIN" -> mapped.add(AuthoritiesConstants.OP_ADMIN);
                case "CATALOG_ADMIN", "CATALOGADMIN" -> mapped.add(AuthoritiesConstants.CATALOG_ADMIN);
                case "GOV_ADMIN", "GOVERNANCE_ADMIN", "GOVADMIN" -> mapped.add(AuthoritiesConstants.GOV_ADMIN);
                case "IAM_ADMIN", "IAMADMIN" -> mapped.add(AuthoritiesConstants.IAM_ADMIN);
                case "ADMIN" -> mapped.add(AuthoritiesConstants.ADMIN);
                // Data roles (client roles on dts-system). Map to canonical ROLE_* for audience filtering in Admin.
                case "DEPT_VIEWER", "DEPARTMENT_VIEWER" -> mapped.add("ROLE_DEPT_VIEWER");
                case "DEPT_EDITOR", "DEPARTMENT_EDITOR" -> mapped.add("ROLE_DEPT_EDITOR");
                case "DEPT_OWNER",  "DEPARTMENT_OWNER"  -> mapped.add("ROLE_DEPT_OWNER");
                case "INST_VIEWER", "INSTITUTE_VIEWER", "INSTITUTION_VIEWER" -> mapped.add("ROLE_INST_VIEWER");
                case "INST_EDITOR", "INSTITUTE_EDITOR", "INSTITUTION_EDITOR" -> mapped.add("ROLE_INST_EDITOR");
                case "INST_OWNER",  "INSTITUTE_OWNER",  "INSTITUTION_OWNER"  -> mapped.add("ROLE_INST_OWNER");
                case "SYS_ADMIN", "SYSADMIN", "AUTH_ADMIN", "AUTHADMIN", "SECURITY_AUDITOR", "SECURITYAUDITOR", "AUDIT_ADMIN", "AUDITADMIN", "AUDITOR_ADMIN" -> {
                    // triad roles handled in containsTriad(); do not map into platform authorities
                }
                default -> {
                    // ignore other roles for platform authorities
                }
            }
        }
        // Only assign ROLE_USER when no specific roles were mapped
        if (mapped.isEmpty()) {
            mapped.add(AuthoritiesConstants.USER);
        }
        return java.util.List.copyOf(mapped);
    }
}
