package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.AdminTokens;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.service.admin.AdminAuthClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak/auth")
public class KeycloakAuthResource {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthResource.class);
    private final PortalSessionRegistry sessionRegistry;
    private final AdminAuthClient adminAuthClient;
    private final com.yuzhi.dts.platform.service.audit.AuditService audit;

    public KeycloakAuthResource(PortalSessionRegistry sessionRegistry, AdminAuthClient adminAuthClient, com.yuzhi.dts.platform.service.audit.AuditService audit) {
        this.sessionRegistry = sessionRegistry;
        this.adminAuthClient = adminAuthClient;
        this.audit = audit;
    }

    public record LoginPayload(String username, String password) {}
    public record RefreshPayload(String refreshToken, String username) {}

    public record PkiSessionPayload(String username, Map<String, Object> user) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginPayload payload) {
        String username = payload.username() == null ? "" : payload.username().trim();
        String password = payload.password() == null ? "" : payload.password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponses.error("用户名或密码不能为空"));
        }

        // No username-based blocking; admin service enforces gating/approval rules

        try {
            // audit: login begin
            java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
            auditPayload.put("username", username);
            auditPayload.put("audience", "platform");
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "PENDING",
                auditPayload,
                Map.of("audience", "platform")
            );
            if (log.isInfoEnabled()) {
                log.info("[login] attempt username={}", username);
            }
            var result = adminAuthClient.login(username, password);
            Map<String, Object> user = result.user();
            // Role-based blocks are handled by admin; platform trusts admin decision
            List<String> rawRoles = toStringList(user.get("roles"));
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

            // Extract optional attributes for ABAC (dept_code/personnel_level)
            String deptCode = extractUserAttribute(user, "dept_code");
            String personnelLevel = normalizePersonnelLevel(extractUserAttribute(user, "personnel_level", "person_security_level", "person_level"));

            // Issue a portal session (opaque tokens) for platform API access
            AdminTokens adminTokens = computeAdminTokens(
                result.accessToken(),
                result.accessTokenExpiresIn(),
                result.refreshToken(),
                result.refreshTokenExpiresIn(),
                null
            );
            PortalSession session = sessionRegistry.createSession(username, mappedRoles, permissions, deptCode, personnelLevel, adminTokens);

            // Build user payload (override roles/permissions with mapped ones)
            Map<String, Object> userOut = new java.util.HashMap<>(user);
            userOut.put("roles", mappedRoles);
            userOut.put("permissions", permissions);
            userOut.putIfAbsent("enabled", Boolean.TRUE);
            userOut.putIfAbsent("id", UUID.nameUUIDFromBytes(username.getBytes()).toString());
            // Propagate ABAC attributes so frontend can initialize active context (scope/dept)
            if (deptCode != null && !deptCode.isBlank()) {
                userOut.put("dept_code", deptCode);
            }
            if (personnelLevel != null && !personnelLevel.isBlank()) {
                userOut.put("personnel_level", personnelLevel);
            }
            // Ensure nested attributes map contains dept_code/personnel_level for FE store defaults
            try {
                Object existingAttrs = userOut.get("attributes");
                java.util.Map<String, Object> attrs = new java.util.LinkedHashMap<>();
                if (existingAttrs instanceof java.util.Map<?, ?> m) {
                    for (var e : m.entrySet()) {
                        if (e.getKey() != null) attrs.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                if (deptCode != null && !deptCode.isBlank()) {
                    attrs.put("dept_code", java.util.List.of(deptCode));
                }
                if (personnelLevel != null && !personnelLevel.isBlank()) {
                    attrs.put("personnel_level", java.util.List.of(personnelLevel));
                }
                if (!attrs.isEmpty()) {
                    userOut.put("attributes", attrs);
                }
            } catch (Exception ignore) {}

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", userOut);
            data.put("accessToken", session.accessToken());
            data.put("refreshToken", session.refreshToken());
            if (adminTokens != null) {
                data.put("adminAccessToken", adminTokens.accessToken());
                if (adminTokens.accessExpiresAt() != null) {
                    data.put("adminAccessTokenExpiresAt", adminTokens.accessExpiresAt().toString());
                }
                data.put("adminRefreshToken", adminTokens.refreshToken());
                if (adminTokens.refreshExpiresAt() != null) {
                    data.put("adminRefreshTokenExpiresAt", adminTokens.refreshExpiresAt().toString());
                }
            }
            if (log.isInfoEnabled()) {
                log.info("[login] success username={} roles={} perms={}", username, mappedRoles, permissions);
            }
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "SUCCESS",
                Map.of("audience", "platform"),
                null
            );
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (IllegalStateException ex) {
            if ("session_active".equals(ex.getMessage())) {
                String msg = "当前账号已在其他页面登录，请先退出后再尝试";
                log.warn("[login] active session blocked username={}", username);
                audit.recordAs(
                    username,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    username,
                    "FAILED",
                    Map.of("audience", "platform", "error", msg),
                    null
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponses.error("SESSION_ACTIVE", msg));
            }
            throw ex;
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            log.warn("[login] unauthorized username={} reason={}", username, ex.getMessage());
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "FAILED",
                Map.of("audience", "platform", "error", ex.getMessage()),
                null
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error(ex.getMessage()));
        } catch (Exception ex) {
            String msg = ex.getMessage() == null || ex.getMessage().isBlank() ? "登录失败，请稍后重试" : ex.getMessage();
            log.error("[login] error username={} msg={}", username, msg);
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "FAILED",
                Map.of("audience", "platform", "error", msg),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponses.error(msg));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshPayload payload) {
        String portalRefresh = payload != null ? payload.refreshToken() : null;
        PortalSession session = sessionRegistry.invalidateByRefreshToken(portalRefresh);
        String requestedActor = payload != null ? sanitizeActor(payload.username()) : null;
        String sessionActor = session != null ? sanitizeActor(session.username()) : null;
        String contextActor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
        String actor = firstNonBlank(sessionActor, requestedActor, contextActor, "unknown");

        Map<String, Object> beginPayload = new LinkedHashMap<>();
        beginPayload.put("hasRefreshToken", StringUtils.hasText(portalRefresh));
        audit.recordAs(
            actor,
            "AUTH LOGOUT",
            "platform",
            "portal_user",
            actor,
            "PENDING",
            beginPayload,
            Map.of("audience", "platform")
        );

        boolean revokeFailed = false;
        String revokeError = null;
        if (session != null) {
            AdminTokens adminTokens = session.adminTokens();
            if (adminTokens != null && StringUtils.hasText(adminTokens.refreshToken())) {
                try {
                    adminAuthClient.logout(adminTokens.refreshToken());
                } catch (Exception ex) {
                    revokeFailed = true;
                    revokeError = ex.getMessage();
                }
            }
        }

        if (revokeFailed) {
            audit.recordAs(
                actor,
                "AUTH LOGOUT",
                "platform",
                "portal_user",
                actor,
                "FAILED",
                Map.of("audience", "platform", "error", revokeError == null ? "LOGOUT_ERROR" : revokeError),
                null
            );
        } else {
            audit.recordAs(
                actor,
                "AUTH LOGOUT",
                "platform",
                "portal_user",
                actor,
                "SUCCESS",
                Map.of("audience", "platform"),
                null
            );
        }
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    /**
     * Establish a portal session after upstream PKI login succeeded on admin service.
     * This endpoint does NOT perform certificate verification; it only converts a verified identity
     * (provided via 'username' and optional 'user' profile from admin) into platform session tokens.
     */
    @PostMapping("/pki-session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPkiSession(@RequestBody PkiSessionPayload payload) {
        String username = payload == null ? null : (payload.username() == null ? null : payload.username().trim());
        if (!org.springframework.util.StringUtils.hasText(username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponses.error("缺少用户名"));
        }

        try {
            // audit: login begin (pki)
            java.util.Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
            auditPayload.put("username", username);
            auditPayload.put("mode", "pki");
            auditPayload.put("audience", "platform");
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "PENDING",
                auditPayload,
                Map.of("audience", "platform", "mode", "pki")
            );

            Map<String, Object> user = payload.user() == null ? java.util.Collections.emptyMap() : new java.util.LinkedHashMap<>(payload.user());
            // Normalize and map roles from upstream into platform authorities
            java.util.List<String> rawRoles = toStringList(user.get("roles"));
            java.util.List<String> mappedRoles = mapRoles(rawRoles);

            // Derive basic permissions
            java.util.List<String> permissions = new java.util.ArrayList<>();
            permissions.add("portal.view");
            if (mappedRoles.contains(com.yuzhi.dts.platform.security.AuthoritiesConstants.OP_ADMIN)) {
                permissions.add("portal.manage");
                permissions.add("catalog.manage");
                permissions.add("governance.manage");
                permissions.add("iam.manage");
            }

            // Extract optional attributes for ABAC (dept_code/personnel_level)
            String deptCode = extractUserAttribute(user, "dept_code");
            String personnelLevel = normalizePersonnelLevel(extractUserAttribute(user, "personnel_level", "person_security_level", "person_level"));

            // Issue a portal session (opaque tokens) for platform API access (no admin tokens needed for PKI path)
            AdminTokens adminTokens = null;
            PortalSession session = sessionRegistry.createSession(username, mappedRoles, permissions, deptCode, personnelLevel, adminTokens);

            // Build user payload (override roles/permissions with mapped ones)
            Map<String, Object> userOut = new java.util.LinkedHashMap<>(user);
            userOut.put("username", userOut.getOrDefault("username", username));
            userOut.put("roles", mappedRoles);
            userOut.put("permissions", permissions);
            userOut.putIfAbsent("enabled", Boolean.TRUE);
            userOut.putIfAbsent("id", java.util.UUID.nameUUIDFromBytes(username.getBytes()).toString());
            if (deptCode != null && !deptCode.isBlank()) userOut.put("dept_code", deptCode);
            if (personnelLevel != null && !personnelLevel.isBlank()) userOut.put("personnel_level", personnelLevel);
            // Ensure nested attributes map contains dept_code/personnel_level for FE store defaults
            try {
                Object existingAttrs = userOut.get("attributes");
                java.util.Map<String, Object> attrs = new java.util.LinkedHashMap<>();
                if (existingAttrs instanceof java.util.Map<?, ?> m) {
                    for (var e : m.entrySet()) {
                        if (e.getKey() != null) attrs.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                if (deptCode != null && !deptCode.isBlank()) attrs.put("dept_code", java.util.List.of(deptCode));
                if (personnelLevel != null && !personnelLevel.isBlank()) attrs.put("personnel_level", java.util.List.of(personnelLevel));
                if (!attrs.isEmpty()) userOut.put("attributes", attrs);
            } catch (Exception ignore) {}

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("user", userOut);
            data.put("accessToken", session.accessToken());
            data.put("refreshToken", session.refreshToken());

            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "SUCCESS",
                Map.of("audience", "platform", "mode", "pki"),
                null
            );
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (IllegalStateException ex) {
            if ("session_active".equals(ex.getMessage())) {
                String msg = "当前账号已在其他页面登录，请先退出后再尝试";
                audit.recordAs(
                    username,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    username,
                    "FAILED",
                    Map.of("audience", "platform", "mode", "pki", "error", msg),
                    null
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponses.error("SESSION_ACTIVE", msg));
            }
            throw ex;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null || ex.getMessage().isBlank() ? "登录失败，请稍后重试" : ex.getMessage();
            audit.recordAs(
                username,
                "AUTH LOGIN",
                "platform",
                "portal_user",
                username,
                "FAILED",
                Map.of("audience", "platform", "mode", "pki", "error", msg),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponses.error(msg));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody RefreshPayload payload) {
        try {
            String currentActor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
            String actor = currentActor != null ? currentActor : "unknown";
            audit.recordAs(
                actor,
                "AUTH REFRESH",
                "platform",
                "portal_user",
                actor,
                "PENDING",
                Map.of("audience", "platform"),
                null
            );
            PortalSession refreshed = sessionRegistry.refreshSession(
                payload.refreshToken(),
                existing -> {
                    if (existing == null) return null;
                    AdminTokens tokens = existing.adminTokens();
                    String adminRefresh = tokens != null ? tokens.refreshToken() : null;
                    if (!StringUtils.hasText(adminRefresh)) {
                        return tokens;
                    }
                    try {
                        var result = adminAuthClient.refresh(adminRefresh);
                        return computeAdminTokens(
                            result.accessToken(),
                            result.accessTokenExpiresIn(),
                            result.refreshToken(),
                            result.refreshTokenExpiresIn(),
                            tokens
                        );
                    } catch (Exception ex) {
                        log.warn("[refresh] admin token refresh failed: {}", ex.getMessage());
                        return tokens;
                    }
                }
            );
            Map<String, String> data = new LinkedHashMap<>();
            data.put("accessToken", refreshed.accessToken());
            data.put("refreshToken", refreshed.refreshToken());
            AdminTokens adminTokens = refreshed.adminTokens();
            if (adminTokens != null) {
                data.put("adminAccessToken", adminTokens.accessToken());
                if (adminTokens.accessExpiresAt() != null) {
                    data.put("adminAccessTokenExpiresAt", adminTokens.accessExpiresAt().toString());
                }
                data.put("adminRefreshToken", adminTokens.refreshToken());
                if (adminTokens.refreshExpiresAt() != null) {
                    data.put("adminRefreshTokenExpiresAt", adminTokens.refreshExpiresAt().toString());
                }
            }
            audit.recordAs(
                actor,
                "AUTH REFRESH",
                "platform",
                "portal_user",
                actor,
                "SUCCESS",
                Map.of("audience", "platform"),
                null
            );
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (IllegalArgumentException ex) {
            String actor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
            audit.recordAs(
                actor != null ? actor : "unknown",
                "AUTH REFRESH",
                "platform",
                "portal_user",
                actor != null ? actor : "unknown",
                "FAILED",
                Map.of("audience", "platform", "error", ex.getMessage()),
                null
            );
            return ResponseEntity.status(401).body(ApiResponses.error("刷新令牌无效，请重新登录"));
        } catch (IllegalStateException ex) {
            String actor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
            audit.recordAs(
                actor != null ? actor : "unknown",
                "AUTH REFRESH",
                "platform",
                "portal_user",
                actor != null ? actor : "unknown",
                "FAILED",
                Map.of("audience", "platform", "error", ex.getMessage()),
                null
            );
            return ResponseEntity.status(401).body(ApiResponses.error("会话已过期，请重新登录"));
        }
    }

    private String sanitizeActor(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String trimmed = candidate.trim();
        if ("anonymous".equalsIgnoreCase(trimmed) || "anonymoususer".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
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

    private List<String> toStringList(Object value) {
        if (value instanceof java.util.Collection<?> c) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object o : c) if (o != null) out.add(o.toString());
            return out;
        }
        if (value instanceof String s) return java.util.List.of(s);
        return java.util.List.of();
    }

    @SuppressWarnings("unchecked")
    private String extractUserAttribute(Map<String, Object> user, String... keys) {
        if (user == null) return null;
        // Try flat fields first
        for (String k : keys) {
            Object v = user.get(k);
            if (v instanceof String s && !s.isBlank()) return s.trim();
        }
        // Try nested attributes map as in Keycloak userinfo
        Object attrs = user.get("attributes");
        if (attrs instanceof Map<?, ?> map) {
            for (String k : keys) {
                Object v = map.get(k);
                if (v instanceof String s && !s.isBlank()) return s.trim();
                if (v instanceof java.util.List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof String s && !s.isBlank()) return s.trim();
                }
            }
        }
        return null;
    }

    private String normalizePersonnelLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        // Map Chinese labels to canonical levels
        if (v.equals("非密") || v.equals("一般") || v.equals("INTERNAL") || v.equals("GENERAL") || v.equals("PUBLIC")) {
            return "GENERAL";
        }
        if (v.equals("重要") || v.equals("秘密") || v.equals("SECRET") || v.equals("IMPORTANT") || v.equals("IMPORTAN") || v.equals("CONFIDENTIAL") || v.equals("机密")) {
            return "IMPORTANT";
        }
        if (v.equals("核心") || v.equals("CORE") || v.equals("TOP_SECRET")) {
            return "CORE";
        }
        // Best-effort: keep as-is
        return v;
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
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String up = trimmed.toUpperCase(Locale.ROOT);
            if (up.startsWith("ROLE_")) {
                up = up.substring(5);
            }
            boolean handled = switch (up) {
                case "OP_ADMIN", "OPADMIN" -> {
                    mapped.add(AuthoritiesConstants.OP_ADMIN);
                    yield true;
                }
                case "CATALOG_ADMIN", "CATALOGADMIN" -> {
                    mapped.add(AuthoritiesConstants.INST_DATA_OWNER);
                    yield true;
                }
                case "GOV_ADMIN", "GOVERNANCE_ADMIN", "GOVADMIN" -> {
                    mapped.add(AuthoritiesConstants.INST_DATA_OWNER);
                    yield true;
                }
                case "IAM_ADMIN", "IAMADMIN" -> {
                    mapped.add(AuthoritiesConstants.INST_DATA_OWNER);
                    yield true;
                }
                case "ADMIN" -> {
                    mapped.add(AuthoritiesConstants.ADMIN);
                    yield true;
                }
                // Data roles (client roles on dts-system). Map to canonical ROLE_* for audience filtering in Admin.
                case "DEPT_DATA_VIEWER", "DEPT_VIEWER", "DEPARTMENT_VIEWER" -> {
                    mapped.add("ROLE_DEPT_DATA_VIEWER");
                    yield true;
                }
                case "DEPT_DATA_DEV", "DEPT_EDITOR", "DEPARTMENT_EDITOR" -> {
                    mapped.add("ROLE_DEPT_DATA_DEV");
                    yield true;
                }
                case "DEPT_DATA_OWNER", "DEPT_OWNER", "DEPARTMENT_OWNER" -> {
                    mapped.add("ROLE_DEPT_DATA_OWNER");
                    yield true;
                }
                case "INST_DATA_VIEWER", "INST_VIEWER", "INSTITUTE_VIEWER", "INSTITUTION_VIEWER" -> {
                    mapped.add("ROLE_INST_DATA_VIEWER");
                    yield true;
                }
                case "INST_DATA_DEV", "INST_EDITOR", "INSTITUTE_EDITOR", "INSTITUTION_EDITOR" -> {
                    mapped.add("ROLE_INST_DATA_DEV");
                    yield true;
                }
                case "INST_DATA_OWNER", "INST_OWNER", "INSTITUTE_OWNER", "INSTITUTION_OWNER" -> {
                    mapped.add("ROLE_INST_DATA_OWNER");
                    yield true;
                }
                case "SYS_ADMIN", "SYSADMIN", "AUTH_ADMIN", "AUTHADMIN", "SECURITY_AUDITOR", "SECURITYAUDITOR", "AUDIT_ADMIN", "AUDITADMIN", "AUDITOR_ADMIN" -> {
                    // triad roles handled earlier; skip silently
                    yield true;
                }
                default -> false;
            };
            if (handled) {
                continue;
            }
            String canonical = sanitizeRoleToken(up);
            if (!canonical.isEmpty()) {
                mapped.add("ROLE_" + canonical);
            }
        }
        // Only assign ROLE_USER when no specific roles were mapped
        if (mapped.isEmpty()) {
            mapped.add(AuthoritiesConstants.USER);
        }
        return java.util.List.copyOf(mapped);
    }

    private String sanitizeRoleToken(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "";
        }
        String sanitized = candidate.replaceAll("[^A-Z0-9_]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        return sanitized;
    }

    private AdminTokens computeAdminTokens(
        String accessToken,
        Long accessExpiresIn,
        String refreshToken,
        Long refreshExpiresIn,
        AdminTokens fallback
    ) {
        if (!StringUtils.hasText(accessToken) && !StringUtils.hasText(refreshToken)) {
            return fallback;
        }
        Instant now = Instant.now();
        Instant accessExpiry = resolveExpiry(now, accessExpiresIn, fallback != null ? fallback.accessExpiresAt() : null, 300);
        Instant refreshExpiry = resolveExpiry(now, refreshExpiresIn, fallback != null ? fallback.refreshExpiresAt() : null, 7200);
        String nextAccess = StringUtils.hasText(accessToken) ? accessToken : (fallback != null ? fallback.accessToken() : null);
        String nextRefresh = StringUtils.hasText(refreshToken) ? refreshToken : (fallback != null ? fallback.refreshToken() : null);
        return new AdminTokens(nextAccess, accessExpiry, nextRefresh, refreshExpiry);
    }

    private Instant resolveExpiry(Instant now, Long offsetSeconds, Instant fallback, long defaultSeconds) {
        if (offsetSeconds != null && offsetSeconds > 0) {
            return now.plusSeconds(offsetSeconds);
        }
        if (fallback != null) {
            return fallback;
        }
        return now.plusSeconds(defaultSeconds);
    }
}
