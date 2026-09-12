package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.net.ClientIpTrace;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.session.PkiSessionTicketService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.AdminTokens;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import com.yuzhi.dts.platform.service.keycloak.KeycloakAuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak/auth")
public class KeycloakAuthResource {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthResource.class);
    private static final String PKI_LOGIN_CLIENT_IP = "pkiLoginClientIp";
    private static final String PKI_LOGIN_USER_AGENT = "pkiLoginUserAgent";
    private final PortalSessionRegistry sessionRegistry;
    private final KeycloakAuthService keycloakAuthService;
    private final AdminAuthGateway adminAuthGateway;
    private final PkiSessionTicketService pkiSessionTicketService;
    private final PortalSessionCookieService portalSessionCookieService;
    private final com.yuzhi.dts.platform.service.audit.AuditService audit;
    private final com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry inceptorRegistry;
    private final boolean portalAuditEnabled;
    private final boolean portalRefreshAuditEnabled;

    public KeycloakAuthResource(
        PortalSessionRegistry sessionRegistry,
        KeycloakAuthService keycloakAuthService,
        AdminAuthGateway adminAuthGateway,
        PkiSessionTicketService pkiSessionTicketService,
        PortalSessionCookieService portalSessionCookieService,
        com.yuzhi.dts.platform.service.audit.AuditService audit,
        com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry inceptorRegistry,
        @Value("${auditing.portal-auth.enabled:true}") boolean portalAuditEnabled,
        @Value("${auditing.portal-auth.refresh-enabled:false}") boolean portalRefreshAuditEnabled
    ) {
        this.sessionRegistry = sessionRegistry;
        this.keycloakAuthService = keycloakAuthService;
        this.adminAuthGateway = adminAuthGateway;
        this.pkiSessionTicketService = pkiSessionTicketService;
        this.portalSessionCookieService = portalSessionCookieService;
        this.audit = audit;
        this.inceptorRegistry = inceptorRegistry;
        this.portalAuditEnabled = portalAuditEnabled;
        this.portalRefreshAuditEnabled = portalRefreshAuditEnabled;
    }

    public record LoginPayload(String username, String password) {}
    public record RefreshPayload(String refreshToken, String username) {}

    public record PkiSessionPayload(String username, Map<String, Object> user) {}

    /**
     * Backwards-compatible alias for older portal clients.
     *
     * <p>Platform's canonical login endpoint is {@code /api/keycloak/auth/login}. Some legacy bundles post to
     * {@code /api/keycloak/auth/platform/login}.
     */
    @PostMapping("/platform/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> platformLogin(
        @RequestBody LoginPayload payload,
        HttpServletRequest request
    ) {
        return login(payload, request);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginPayload payload, HttpServletRequest request) {
        String username = payload.username() == null ? "" : payload.username().trim();
        String password = payload.password() == null ? "" : payload.password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponses.error("用户名或密码不能为空"));
        }
        String browserId = resolveBrowserId(request);
        ClientIpTrace clientIpTrace = resolveClientIpTrace(request);
        clientIpTrace.logInfo(log, "platform-login", requestMethod(request), requestUri(request));
        String loginIp = clientIpTrace.resolved();

        // No username-based blocking; admin service enforces gating/approval rules

        try {
            if (log.isInfoEnabled()) {
                log.info("[login] attempt username={}", username);
            }
            // Step 1: Authenticate directly against Keycloak → get KC tokens
            var kcResult = keycloakAuthService.login(username, password);
            var kcTokens = kcResult.tokens();

            // Step 2: Get enriched user data from admin (roles/permissions/profile from admin DB).
            Map<String, Object> user = loadAdminUserProfile(username, password, kcResult, kcTokens);
            String displayName = resolveUserDisplayName(user);
            List<String> rawRoles = toStringList(user.get("roles"));
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
            String deptCode = resolveDeptCode(user);
            String deptName = resolveDeptName(user);
            String personnelLevel = normalizePersonnelLevel(extractUserAttribute(user, "personnel_level", "person_security_level", "person_level"));

            // Issue a portal session (opaque tokens) for platform API access
            boolean takeover;
            try {
                takeover = sessionRegistry.hasActiveSession(username, browserId);
            } catch (RuntimeException ex) {
                if (isRelationMissing(ex, "portal_sessions")) {
                    String msg = "平台数据库未初始化或升级未完成（缺少 portal_sessions 表），请先执行初始化/升级脚本后重试";
                    log.error("[login] portal session store not ready username={}", username);
                    String auditActor = sanitizeActor(username);
                    if (shouldRecordPortalLoginAudit() && auditActor != null) {
                        Map<String, Object> failurePayload = authAuditPayload(auditActor);
                        applyIdentityMetadata(failurePayload, displayName, auditActor);
                        failurePayload.put("summary", buildSummary("业务端登录失败", displayName, auditActor));
                        failurePayload.put("operationType", "LOGIN");
                        failurePayload.put("error", "PORTAL_SESSION_STORE_NOT_READY");
                        audit.recordAs(
                            auditActor,
                            "AUTH LOGIN",
                            "platform",
                            "portal_user",
                            auditActor,
                            "FAILED",
                            failurePayload,
                            Map.of("audience", "platform")
                        );
                    }
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponses.error(msg));
                }
                throw ex;
            }
            if (takeover && !sessionRegistry.isTakeoverAllowed()) {
                log.warn("[login] denied username={} reason=active-session", username);
                String auditActor = sanitizeActor(username);
                if (shouldRecordPortalLoginAudit() && auditActor != null) {
                    Map<String, Object> failurePayload = authAuditPayload(auditActor);
                    applyIdentityMetadata(failurePayload, displayName, auditActor);
                    failurePayload.put("summary", buildSummary("业务端登录失败", displayName, auditActor));
                    failurePayload.put("operationType", "LOGIN");
                    failurePayload.put("error", "ACTIVE_SESSION");
                    audit.recordAs(
                        auditActor,
                        "AUTH LOGIN",
                        "platform",
                        "portal_user",
                        auditActor,
                        "FAILED",
                        failurePayload,
                        Map.of("audience", "platform")
                    );
                }
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponses.error("该账号已在其他浏览器登录，请先退出后再尝试"));
            }
            // Store Keycloak tokens in the adminTokens fields (reusing the DB columns for Keycloak tokens)
            AdminTokens keycloakTokens = computeAdminTokens(
                kcTokens.accessToken(),
                kcTokens.expiresIn(),
                kcTokens.refreshToken(),
                kcTokens.refreshExpiresIn(),
                null
            );
            PortalSession session;
            try {
                session = sessionRegistry.createVerifiedSession(
                    username,
                    mappedRoles,
                    permissions,
                    deptCode,
                    personnelLevel,
                    displayName,
                    browserId,
                    keycloakTokens,
                    stringValue(kcResult.user().get("id"))
                );
            } catch (PortalSessionRegistry.ActiveSessionExistsException ex) {
                log.warn("[login] denied username={} reason=race-active-session", username);
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponses.error("该账号已在其他浏览器登录，请先退出后再尝试"));
            } catch (RuntimeException ex) {
                if (isRelationMissing(ex, "portal_sessions")) {
                    String msg = "平台数据库未初始化或升级未完成（缺少 portal_sessions 表），请先执行初始化/升级脚本后重试";
                    log.error("[login] portal session store not ready (create) username={}", username);
                    String auditActor = sanitizeActor(username);
                    if (shouldRecordPortalLoginAudit() && auditActor != null) {
                        Map<String, Object> failurePayload = authAuditPayload(auditActor);
                        applyIdentityMetadata(failurePayload, displayName, auditActor);
                        failurePayload.put("summary", buildSummary("业务端登录失败", displayName, auditActor));
                        failurePayload.put("operationType", "LOGIN");
                        failurePayload.put("error", "PORTAL_SESSION_STORE_NOT_READY");
                        audit.recordAs(
                            auditActor,
                            "AUTH LOGIN",
                            "platform",
                            "portal_user",
                            auditActor,
                            "FAILED",
                            failurePayload,
                            Map.of("audience", "platform")
                        );
                    }
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponses.error(msg));
                }
                throw ex;
            }

            // Build user payload (override roles/permissions with mapped ones)
            Map<String, Object> userOut = new java.util.LinkedHashMap<>(user);
            userOut.put("roles", mappedRoles);
            userOut.put("permissions", permissions);
            userOut.putIfAbsent("enabled", Boolean.TRUE);
            userOut.putIfAbsent("id", UUID.nameUUIDFromBytes(username.getBytes()).toString());
            if (StringUtils.hasText(displayName)) {
                userOut.put("fullName", displayName);
                userOut.put("displayName", displayName);
                userOut.put("name", displayName);
            }
            if (deptCode != null && !deptCode.isBlank()) {
                userOut.put("dept_code", deptCode);
            }
            if (deptName != null && !deptName.isBlank()) {
                userOut.put("dept_name", deptName);
            }
            if (personnelLevel != null && !personnelLevel.isBlank()) {
                userOut.put("personnel_level", personnelLevel);
            }
            putLoginIp(userOut, loginIp);
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
                if (deptName != null && !deptName.isBlank()) {
                    attrs.put("dept_name", java.util.List.of(deptName));
                }
                if (personnelLevel != null && !personnelLevel.isBlank()) {
                    attrs.put("personnel_level", java.util.List.of(personnelLevel));
                }
                if (!attrs.isEmpty()) {
                    userOut.put("attributes", attrs);
                }
            } catch (Exception ignore) {}

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", Boolean.TRUE);
            putLoginIp(data, loginIp);
            // Expose Keycloak token lifetime so frontend can correctly judge expiry
            if (kcTokens.expiresIn() != null) {
                data.put("expiresIn", kcTokens.expiresIn());
            }
            if (kcTokens.refreshExpiresIn() != null) {
                data.put("refreshExpiresIn", kcTokens.refreshExpiresIn());
            }
            appendPortalSessionLifetime(data, session);
            data.put("user", userOut);
            if (log.isInfoEnabled()) {
                if (takeover && sessionRegistry.isTakeoverAllowed()) {
                    log.info("[login] success username={} roles={} perms={} takeover=true", username, mappedRoles, permissions);
                } else {
                    log.info("[login] success username={} roles={} perms={}", username, mappedRoles, permissions);
                }
            }
            String auditActor = sanitizeActor(username);
            if (shouldRecordPortalLoginAudit() && auditActor != null) {
                Map<String, Object> successPayload = authAuditPayload(auditActor);
                applyIdentityMetadata(successPayload, displayName, auditActor);
                successPayload.put("summary", buildSummary("业务端登录成功", displayName, auditActor));
                successPayload.put("operationType", "LOGIN");
                audit.recordAs(
                    auditActor,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    auditActor,
                    "SUCCESS",
                    successPayload,
                    Map.of("audience", "platform")
                );
            }
            try {
                if (inceptorRegistry.getActive().isEmpty()) {
                    inceptorRegistry.refresh();
                }
            } catch (Exception ex) {
                log.debug("[login] inceptor registry refresh skipped: {}", ex.getMessage());
            }
            if (takeover && sessionRegistry.isTakeoverAllowed()) {
                data.put("sessionNotice", "已切换到当前登录，其他会话已下线");
                data.put("sessionTakeover", Boolean.TRUE);
            }
            return okWithPortalSessionCookie(data, session);
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            log.warn("[login] unauthorized username={} reason={}", username, ex.getMessage());
            String auditActor = sanitizeActor(username);
            if (shouldRecordPortalLoginAudit() && auditActor != null) {
                Map<String, Object> failurePayload = authAuditPayload(auditActor);
                applyIdentityMetadata(failurePayload, null, auditActor);
                failurePayload.put("summary", buildSummary("业务端登录失败", null, auditActor));
                failurePayload.put("operationType", "LOGIN");
                failurePayload.put("error", ex.getMessage());
                audit.recordAs(
                    auditActor,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    auditActor,
                    "FAILED",
                    failurePayload,
                    Map.of("audience", "platform")
                );
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error(ex.getMessage()));
        } catch (Exception ex) {
            String msg;
            HttpStatus status;
            if (isRelationMissing(ex, "portal_sessions")) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                msg = "平台数据库未初始化或升级未完成（缺少 portal_sessions 表），请先执行初始化/升级脚本后重试";
            } else if (containsMessage(ex, "无法获取用户授权信息")) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                msg = "无法获取用户授权信息，请稍后重试";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                msg = "登录失败，请稍后重试";
            }
            log.error("[login] error username={} msg={}", username, msg);
            String auditActor = sanitizeActor(username);
            if (shouldRecordPortalLoginAudit() && auditActor != null) {
                Map<String, Object> failurePayload = authAuditPayload(auditActor);
                applyIdentityMetadata(failurePayload, null, auditActor);
                failurePayload.put("summary", buildSummary("业务端登录失败", null, auditActor));
                failurePayload.put("operationType", "LOGIN");
                failurePayload.put("error", msg);
                audit.recordAs(
                    auditActor,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    auditActor,
                    "FAILED",
                    failurePayload,
                    Map.of("audience", "platform")
                );
            }
            return ResponseEntity.status(status).body(ApiResponses.error(msg));
        }
    }

    ResponseEntity<ApiResponse<Map<String, Object>>> login(LoginPayload payload) {
        return login(payload, null);
    }

    private boolean containsMessage(Throwable ex, String expected) {
        if (ex == null || !StringUtils.hasText(expected)) {
            return false;
        }
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth++ < 15) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(expected)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean isRelationMissing(Throwable ex, String relation) {
        if (ex == null || !StringUtils.hasText(relation)) {
            return false;
        }
        String needle = "relation \"" + relation + "\" does not exist";
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth++ < 15) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private Map<String, Object> loadAdminUserProfile(
        String username,
        String password,
        KeycloakAuthService.LoginResult kcResult,
        KeycloakAuthService.TokenResponse kcTokens
    ) {
        try {
            var adminResult = adminAuthGateway.profile(username, kcResult.user(), kcTokens.accessToken());
            return adminResult.user();
        } catch (Exception profileEx) {
            log.warn(
                "[login] admin user profile unavailable, falling back to legacy platform login username={} reason={}",
                username,
                profileEx.getMessage()
            );
            try {
                return adminAuthGateway.login(username, password).user();
            } catch (org.springframework.security.authentication.BadCredentialsException legacyEx) {
                throw legacyEx;
            } catch (Exception legacyEx) {
                profileEx.addSuppressed(legacyEx);
                throw new IllegalStateException("无法获取用户授权信息，请稍后重试", profileEx);
            }
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshPayload payload, HttpServletRequest request) {
        String portalRefresh = payload != null ? payload.refreshToken() : null;
        PortalSession session = null;
        if (StringUtils.hasText(portalRefresh)) {
            session = sessionRegistry.invalidateByRefreshToken(portalRefresh);
        } else {
            String portalAccess = resolvePortalAccessTokenFromCookie(request);
            session = sessionRegistry.invalidateByAccessToken(portalAccess);
        }
        String requestedActor = payload != null ? sanitizeActor(payload.username()) : null;
        String sessionActor = session != null ? sanitizeActor(session.username()) : null;
        String contextActor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
        String actor = firstNonBlank(sessionActor, contextActor, requestedActor, resolveRefreshActor(portalRefresh));
        String sessionDisplayName = session != null ? stringValue(session.displayName()) : null;
        String actorDisplayName = firstNonBlank(
            sessionDisplayName,
            com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserDisplayName().orElse(null)
        );

        boolean revokeFailed = false;
        String revokeError = null;
        if (session != null) {
            AdminTokens keycloakTokens = session.adminTokens();
            if (keycloakTokens != null && StringUtils.hasText(keycloakTokens.refreshToken())) {
                // Revoke Keycloak session directly (no admin proxy)
                try {
                    keycloakAuthService.logout(keycloakTokens.refreshToken());
                } catch (Exception ex) {
                    // Best-effort: try revoke as fallback
                    try {
                        keycloakAuthService.revokeRefreshToken(keycloakTokens.refreshToken());
                    } catch (Exception ignored) {}
                    revokeFailed = true;
                    revokeError = ex.getMessage();
                }
            }
        }

        if (shouldRecordPortalLoginAudit() && StringUtils.hasText(actor)) {
            if (revokeFailed) {
                Map<String, Object> failurePayload = authAuditPayload(actor);
                applyIdentityMetadata(failurePayload, actorDisplayName, actor);
                failurePayload.put("summary", buildSummary("业务端登出失败", actorDisplayName, actor));
                failurePayload.put("operationType", "LOGOUT");
                failurePayload.put("error", revokeError == null ? "LOGOUT_ERROR" : revokeError);
                failurePayload.put("hasRefreshToken", StringUtils.hasText(portalRefresh));
                audit.recordAs(
                    actor,
                    "AUTH LOGOUT",
                    "platform",
                    "portal_user",
                    actor,
                    "FAILED",
                    failurePayload,
                    Map.of("audience", "platform")
                );
            } else {
                Map<String, Object> successPayload = authAuditPayload(actor);
                applyIdentityMetadata(successPayload, actorDisplayName, actor);
                successPayload.put("summary", buildSummary("业务端登出成功", actorDisplayName, actor));
                successPayload.put("operationType", "LOGOUT");
                successPayload.put("hasRefreshToken", StringUtils.hasText(portalRefresh));
                audit.recordAs(
                    actor,
                    "AUTH LOGOUT",
                    "platform",
                    "portal_user",
                    actor,
                    "SUCCESS",
                    successPayload,
                    Map.of("audience", "platform")
                );
            }
        }
        return okWithClearedPortalSessionCookie();
    }

    @GetMapping("/pki-challenge")
    public ResponseEntity<ApiResponse<AdminAuthGateway.PkiChallengeView>> pkiChallenge() {
        return ResponseEntity.ok(ApiResponses.ok(adminAuthGateway.getPkiChallenge()));
    }

    @PostMapping("/pki-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pkiLogin(
        @RequestBody(required = false) Map<String, Object> payload,
        HttpServletRequest request
    ) {
        Map<String, Object> data = adminAuthGateway.pkiLogin(payload);
        Map<String, Object> user = extractVerifiedPkiUser(data);
        String username = resolveVerifiedPkiUsername(user);
        if (!StringUtils.hasText(username)) {
            log.error("[pki-login] upstream response missing verified username");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponses.error("PKI 登录响应缺少已验证用户信息"));
        }
        Map<String, Object> ticketUser = new java.util.LinkedHashMap<>(user);
        ClientIpTrace clientIpTrace = resolveClientIpTrace(request);
        clientIpTrace.logInfo(log, "platform-pki-login", requestMethod(request), requestUri(request));
        String clientIp = clientIpTrace.resolved();
        if (StringUtils.hasText(clientIp)) {
            ticketUser.put(PKI_LOGIN_CLIENT_IP, clientIp);
        }
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent)) {
            ticketUser.put(PKI_LOGIN_USER_AGENT, userAgent.trim());
        }
        return ResponseEntity
            .ok()
            .header(org.springframework.http.HttpHeaders.SET_COOKIE, pkiSessionTicketService.issue(username, ticketUser, request).toString())
            .body(ApiResponses.ok(data));
    }

    /**
     * Establish a portal session after upstream PKI login succeeded on admin service.
     * This endpoint does NOT perform certificate verification; it only converts the verified identity
     * bound to the short-lived signed ticket from /pki-login into platform session tokens.
     */
    @PostMapping("/pki-session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPkiSession(
        @RequestBody(required = false) PkiSessionPayload payload,
        HttpServletRequest request
    ) {
        String requestedUsername = payload == null ? null : payload.username();
        PkiSessionTicketService.VerifiedPkiPrincipal verifiedPrincipal = pkiSessionTicketService.resolve(request, requestedUsername);
        if (verifiedPrincipal == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, pkiSessionTicketService.clearTicketCookie(request).toString())
                .body(ApiResponses.error("PKI 登录凭证已失效，请重新使用 USB-Key 登录"));
        }
        String username = verifiedPrincipal.username();
        String auditActor = sanitizeActor(username);
        String pkiLoginClientIp = null;
        String pkiLoginUserAgent = null;

        String displayName = null;
        try {
            Map<String, Object> user = new java.util.LinkedHashMap<>(verifiedPrincipal.user());
            pkiLoginClientIp = stringValue(user.get(PKI_LOGIN_CLIENT_IP));
            pkiLoginUserAgent = stringValue(user.get(PKI_LOGIN_USER_AGENT));
            ClientIpTrace clientIpTrace = resolveClientIpTrace(request);
            clientIpTrace.logInfo(log, "platform-pki-session", requestMethod(request), requestUri(request));
            if (!StringUtils.hasText(pkiLoginClientIp)) {
                pkiLoginClientIp = clientIpTrace.resolved();
            }
            displayName = resolveUserDisplayName(user);
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
            String deptCode = resolveDeptCode(user);
            String deptName = resolveDeptName(user);
            String personnelLevel = normalizePersonnelLevel(extractUserAttribute(user, "personnel_level", "person_security_level", "person_level"));

            // Issue a portal session (opaque tokens) for platform API access (no admin tokens needed for PKI path)
            String browserId = resolveBrowserId(request);
            boolean takeover = sessionRegistry.hasActiveSession(username, browserId);
            if (takeover && !sessionRegistry.isTakeoverAllowed()) {
                log.warn("[pki-login] denied username={} reason=active-session", username);
                if (shouldRecordPortalLoginAudit() && auditActor != null) {
                    Map<String, Object> failurePayload = authAuditPayload(auditActor);
                    failurePayload.put("mode", "pki");
                    applyPkiLoginClientEvidence(failurePayload, pkiLoginClientIp, pkiLoginUserAgent);
                    applyIdentityMetadata(failurePayload, displayName, auditActor);
                    failurePayload.put("summary", buildSummary("业务端登录失败", displayName, auditActor));
                    failurePayload.put("operationType", "LOGIN");
                    failurePayload.put("error", "ACTIVE_SESSION");
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("audience", "platform");
                    metadata.put("mode", "pki");
                    audit.recordAs(
                        auditActor,
                        "AUTH LOGIN",
                        "platform",
                        "portal_user",
                        auditActor,
                        "FAILED",
                        failurePayload,
                        metadata
                    );
                }
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponses.error("该账号已在其他浏览器登录，请先退出后再尝试"));
            }
            // Obtain Keycloak JWT via Token Exchange using platform's own client
            AdminTokens keycloakTokens = null;
            KeycloakAuthService.TokenResponse kcTokens = null;
            String directoryUserId = null;
            try {
                var kcResult = keycloakAuthService.loginByTokenExchange(username);
                kcTokens = kcResult.tokens();
                keycloakTokens = computeAdminTokens(
                    kcTokens.accessToken(),
                    kcTokens.expiresIn(),
                    kcTokens.refreshToken(),
                    kcTokens.refreshExpiresIn(),
                    null
                );
                // Enrich roles from Keycloak token if upstream provided more
                Map<String, Object> kcUser = kcResult.user();
                directoryUserId = stringValue(kcUser.get("id"));
                List<String> kcRoles = toStringList(kcUser.get("roles"));
                if (!kcRoles.isEmpty()) {
                    List<String> merged = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(mappedRoles));
                    for (String r : mapRoles(kcRoles)) {
                        if (!merged.contains(r)) merged.add(r);
                    }
                    mappedRoles = merged;
                }
            } catch (Exception ex) {
                log.warn("[pki-login] Keycloak token-exchange failed for username={}, proceeding without KC tokens: {}", username, ex.getMessage());
                // PKI login can still succeed without KC tokens; refresh will be limited
            }
            PortalSession session;
            try {
                session = sessionRegistry.createVerifiedSession(
                    username,
                    mappedRoles,
                    permissions,
                    deptCode,
                    personnelLevel,
                    displayName,
                    browserId,
                    keycloakTokens,
                    directoryUserId
                );
            } catch (PortalSessionRegistry.ActiveSessionExistsException ex) {
                log.warn("[pki-login] denied username={} reason=race-active-session", username);
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponses.error("该账号已在其他浏览器登录，请先退出后再尝试"));
            }

            // Build user payload (override roles/permissions with mapped ones)
            Map<String, Object> userOut = new java.util.LinkedHashMap<>(user);
            userOut.remove(PKI_LOGIN_CLIENT_IP);
            userOut.remove(PKI_LOGIN_USER_AGENT);
            userOut.put("username", userOut.getOrDefault("username", username));
            userOut.put("roles", mappedRoles);
            userOut.put("permissions", permissions);
            userOut.putIfAbsent("enabled", Boolean.TRUE);
            userOut.putIfAbsent("id", java.util.UUID.nameUUIDFromBytes(username.getBytes()).toString());
            if (StringUtils.hasText(displayName)) {
                userOut.put("fullName", displayName);
                userOut.put("displayName", displayName);
                userOut.put("name", displayName);
            }
            if (deptCode != null && !deptCode.isBlank()) userOut.put("dept_code", deptCode);
            if (deptName != null && !deptName.isBlank()) userOut.put("dept_name", deptName);
            if (personnelLevel != null && !personnelLevel.isBlank()) userOut.put("personnel_level", personnelLevel);
            putLoginIp(userOut, pkiLoginClientIp);
            try {
                Object existingAttrs = userOut.get("attributes");
                java.util.Map<String, Object> attrs = new java.util.LinkedHashMap<>();
                if (existingAttrs instanceof java.util.Map<?, ?> m) {
                    for (var e : m.entrySet()) {
                        if (e.getKey() != null) attrs.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                if (deptCode != null && !deptCode.isBlank()) attrs.put("dept_code", java.util.List.of(deptCode));
                if (deptName != null && !deptName.isBlank()) attrs.put("dept_name", java.util.List.of(deptName));
                if (personnelLevel != null && !personnelLevel.isBlank()) attrs.put("personnel_level", java.util.List.of(personnelLevel));
                if (!attrs.isEmpty()) userOut.put("attributes", attrs);
            } catch (Exception ignore) {}

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("authenticated", Boolean.TRUE);
            putLoginIp(data, pkiLoginClientIp);
            if (kcTokens != null && kcTokens.expiresIn() != null) {
                data.put("expiresIn", kcTokens.expiresIn());
            }
            if (kcTokens != null && kcTokens.refreshExpiresIn() != null) {
                data.put("refreshExpiresIn", kcTokens.refreshExpiresIn());
            }
            appendPortalSessionLifetime(data, session);
            data.put("user", userOut);
            if (takeover && sessionRegistry.isTakeoverAllowed()) {
                data.put("sessionNotice", "已切换到当前登录，其他会话已下线");
                data.put("sessionTakeover", Boolean.TRUE);
            }

            if (shouldRecordPortalLoginAudit() && auditActor != null) {
                Map<String, Object> successPayload = authAuditPayload(auditActor);
                successPayload.put("mode", "pki");
                applyPkiLoginClientEvidence(successPayload, pkiLoginClientIp, pkiLoginUserAgent);
                applyIdentityMetadata(successPayload, displayName, auditActor);
                successPayload.put("summary", buildSummary("业务端登录成功", displayName, auditActor));
                successPayload.put("operationType", "LOGIN");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("audience", "platform");
                metadata.put("mode", "pki");
                audit.recordAs(
                    auditActor,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    auditActor,
                    "SUCCESS",
                    successPayload,
                    metadata
                );
            }
            return okWithPkiClearAndPortalSessionCookie(data, session, request);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null || ex.getMessage().isBlank() ? "登录失败，请稍后重试" : ex.getMessage();
            if (shouldRecordPortalLoginAudit() && auditActor != null) {
                Map<String, Object> failurePayload = authAuditPayload(auditActor);
                failurePayload.put("mode", "pki");
                applyPkiLoginClientEvidence(failurePayload, pkiLoginClientIp, pkiLoginUserAgent);
                applyIdentityMetadata(failurePayload, displayName, auditActor);
                failurePayload.put("summary", buildSummary("业务端登录失败", displayName, auditActor));
                failurePayload.put("operationType", "LOGIN");
                failurePayload.put("error", msg);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("audience", "platform");
                metadata.put("mode", "pki");
                audit.recordAs(
                    auditActor,
                    "AUTH LOGIN",
                    "platform",
                    "portal_user",
                    auditActor,
                    "FAILED",
                    failurePayload,
                    metadata
                );
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponses.error(msg));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
        @RequestBody(required = false) RefreshPayload payload,
        HttpServletRequest request
    ) {
        String portalRefreshToken = resolveRefreshToken(payload, request);
        String actor = resolveRefreshActor(portalRefreshToken);
        try {
            PortalSession refreshed = sessionRegistry.refreshSession(
                portalRefreshToken,
                existing -> {
                    if (existing == null) return null;
                    AdminTokens tokens = existing.adminTokens();
                    String kcRefresh = tokens != null ? tokens.refreshToken() : null;
                    if (!StringUtils.hasText(kcRefresh)) {
                        return tokens;
                    }
                    // Refresh directly against Keycloak (no admin proxy)
                    try {
                        var kcTokens = keycloakAuthService.refreshTokens(kcRefresh);
                        return computeAdminTokens(
                            kcTokens.accessToken(),
                            kcTokens.expiresIn(),
                            kcTokens.refreshToken(),
                            kcTokens.refreshExpiresIn(),
                            tokens
                        );
                    } catch (Exception ex) {
                        log.warn("[refresh] Keycloak token refresh failed: {}", ex.getMessage());
                        // Keycloak refresh failed = upstream session is dead.
                        // Propagate the failure so the portal session refresh also fails → frontend gets 401.
                        throw new IllegalStateException("keycloak_session_expired", ex);
                    }
                }
            );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", Boolean.TRUE);
            // Expose Keycloak token lifetime so frontend can correctly judge expiry
            AdminTokens keycloakTokens = refreshed.adminTokens();
            if (keycloakTokens != null) {
                if (keycloakTokens.accessExpiresAt() != null) {
                    long expiresInSec = Math.max(0, keycloakTokens.accessExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
                    data.put("expiresIn", expiresInSec);
                }
                if (keycloakTokens.refreshExpiresAt() != null) {
                    long refreshExpiresInSec = Math.max(0, keycloakTokens.refreshExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
                    data.put("refreshExpiresIn", refreshExpiresInSec);
                }
            }
            appendPortalSessionLifetime(data, refreshed);
            String refreshedActor = sanitizeActor(refreshed.username());
            if (refreshedActor != null) {
                actor = refreshedActor;
            }
            if (portalRefreshAuditEnabled && StringUtils.hasText(actor)) {
                Map<String, Object> successPayload = authAuditPayload(actor);
                String actorDisplayName = com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserDisplayName().orElse(null);
                applyIdentityMetadata(successPayload, actorDisplayName, actor);
                successPayload.put("summary", buildSummary("业务端刷新会话成功", actorDisplayName, actor));
                successPayload.put("operationType", "REFRESH");
                successPayload.put("hasRefreshToken", StringUtils.hasText(portalRefreshToken));
                audit.recordAs(
                    actor,
                    "AUTH REFRESH",
                    "platform",
                    "portal_user",
                    actor,
                    "SUCCESS",
                    successPayload,
                    Map.of("audience", "platform")
                );
            }
            return okWithPortalSessionCookie(data, refreshed);
        } catch (Exception ex) {
            log.warn("[refresh] failed actor={} reason={}", actor, ex.getMessage());
            if (portalRefreshAuditEnabled && StringUtils.hasText(actor)) {
                Map<String, Object> failurePayload = authAuditPayload(actor);
                String actorDisplayName = com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserDisplayName().orElse(null);
                applyIdentityMetadata(failurePayload, actorDisplayName, actor);
                failurePayload.put("summary", buildSummary("业务端刷新会话失败", actorDisplayName, actor));
                failurePayload.put("operationType", "REFRESH");
                failurePayload.put("error", ex.getMessage());
                failurePayload.put("hasRefreshToken", StringUtils.hasText(portalRefreshToken));
                audit.recordAs(
                    actor,
                    "AUTH REFRESH",
                    "platform",
                    "portal_user",
                    actor,
                    "FAILED",
                    failurePayload,
                    Map.of("audience", "platform")
                );
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponses.error("会话已过期，请重新登录"));
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> okWithPortalSessionCookie(Map<String, Object> data, PortalSession session) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        addPortalSessionCookie(builder, session);
        return builder.body(ApiResponses.ok(data));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> okWithPkiClearAndPortalSessionCookie(
        Map<String, Object> data,
        PortalSession session,
        HttpServletRequest request
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        builder.header(HttpHeaders.SET_COOKIE, pkiSessionTicketService.clearTicketCookie(request).toString());
        addPortalSessionCookie(builder, session);
        return builder.body(ApiResponses.ok(data));
    }

    private ResponseEntity<ApiResponse<Void>> okWithClearedPortalSessionCookie() {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (portalSessionCookieService != null) {
            builder.header(HttpHeaders.SET_COOKIE, portalSessionCookieService.clearPortalSessionCookie().toString());
        }
        return builder.body(ApiResponses.ok(null));
    }

    private void addPortalSessionCookie(ResponseEntity.BodyBuilder builder, PortalSession session) {
        if (builder == null || portalSessionCookieService == null || session == null || !StringUtils.hasText(session.accessToken())) {
            return;
        }
        ResponseCookie cookie = portalSessionCookieService.buildPortalSessionCookie(session.accessToken());
        builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        if (StringUtils.hasText(session.browserId())) {
            builder.header(HttpHeaders.SET_COOKIE, portalSessionCookieService.buildBrowserIdCookie(session.browserId()).toString());
        }
    }

    private String resolveBrowserId(HttpServletRequest request) {
        if (portalSessionCookieService == null) {
            return null;
        }
        return portalSessionCookieService.resolveBrowserId(request);
    }

    private String resolveRefreshToken(RefreshPayload payload, HttpServletRequest request) {
        String refreshToken = payload == null ? null : payload.refreshToken();
        if (StringUtils.hasText(refreshToken)) {
            return refreshToken.trim();
        }
        return sessionRegistry
            .findByAccessToken(resolvePortalAccessTokenFromCookie(request))
            .map(PortalSession::refreshToken)
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    private String resolvePortalAccessTokenFromCookie(HttpServletRequest request) {
        if (portalSessionCookieService == null) {
            return null;
        }
        String token = portalSessionCookieService.resolvePortalSessionToken(request);
        return StringUtils.hasText(token) ? token.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractVerifiedPkiUser(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Object user = data.get("user");
        if (user instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    private String resolveVerifiedPkiUsername(Map<String, Object> user) {
        if (user == null || user.isEmpty()) {
            return null;
        }
        return firstNonBlank(
            stringValue(user.get("username")),
            stringValue(user.get("preferred_username")),
            stringValue(user.get("loginName"))
        );
    }

    private void appendPortalSessionLifetime(Map<String, Object> data, PortalSession session) {
        if (data == null || session == null || session.expiresAt() == null) {
            return;
        }
        Instant expiresAt = session.expiresAt();
        data.put("portalExpiresAt", expiresAt.toString());
        long expiresInSec = Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        data.put("portalExpiresIn", expiresInSec);
    }

    private Map<String, Object> authAuditPayload(String username, Object... kvPairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("audience", "platform");
        if (StringUtils.hasText(username)) {
            payload.put("username", username.trim());
        }
        if (kvPairs != null && kvPairs.length > 0) {
            if (kvPairs.length % 2 != 0) {
                throw new IllegalArgumentException("kvPairs length must be even");
            }
            for (int i = 0; i < kvPairs.length; i += 2) {
                Object key = kvPairs[i];
                Object value = kvPairs[i + 1];
                if (key != null) {
                    payload.put(String.valueOf(key), value);
                }
            }
        }
        return payload;
    }

    private boolean shouldRecordPortalLoginAudit() {
        return portalAuditEnabled;
    }

    private void applyIdentityMetadata(Map<String, Object> payload, String displayName, String fallbackName) {
        if (payload == null) {
            return;
        }
        String target = firstNonBlank(displayName, fallbackName);
        if (StringUtils.hasText(displayName)) {
            payload.put("actorName", displayName);
        }
        if (StringUtils.hasText(target)) {
            payload.put("targetName", target);
            payload.put("resourceName", target);
        }
    }

    private void applyPkiLoginClientEvidence(Map<String, Object> payload, String clientIp, String userAgent) {
        if (payload == null) {
            return;
        }
        if (StringUtils.hasText(clientIp)) {
            payload.put("clientIp", clientIp.trim());
        }
        if (StringUtils.hasText(userAgent)) {
            payload.put("clientAgent", userAgent.trim());
        }
    }

    private void putLoginIp(Map<String, Object> data, String clientIp) {
        if (data == null || !StringUtils.hasText(clientIp)) {
            return;
        }
        String normalized = clientIp.trim();
        data.put("loginIp", normalized);
        data.put("clientIp", normalized);
    }

    private ClientIpTrace resolveClientIpTrace(HttpServletRequest request) {
        if (request == null) {
            return ClientIpTrace.empty();
        }
        return ClientIpTrace.from(request::getHeader, request.getRemoteAddr());
    }

    private String requestMethod(HttpServletRequest request) {
        return request == null ? null : request.getMethod();
    }

    private String requestUri(HttpServletRequest request) {
        return request == null ? null : request.getRequestURI();
    }

    private String buildSummary(String prefix, String displayName, String fallbackName) {
        String target = firstNonBlank(displayName, fallbackName);
        if (StringUtils.hasText(target)) {
            return prefix + "：" + target;
        }
        return prefix;
    }

    private String resolveUserDisplayName(Map<String, Object> user) {
        if (user == null || user.isEmpty()) {
            return null;
        }
        return firstNonBlank(
            stringValue(user.get("fullName")),
            stringValue(user.get("name")),
            stringValue(user.get("username"))
        );
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private String resolveRefreshActor(String refreshToken) {
        String actor = sanitizeActor(com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse(null));
        if (actor != null) {
            return actor;
        }
        return sessionRegistry
            .resolveUsernameByRefreshToken(refreshToken)
            .flatMap(name -> Optional.ofNullable(sanitizeActor(name)))
            .orElse(null);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                String trimmed = value.trim();
                if ("anonymous".equalsIgnoreCase(trimmed) || "anonymoususer".equalsIgnoreCase(trimmed)) {
                    continue;
                }
                return trimmed;
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

    private String resolveDeptCode(Map<String, Object> user) {
        String dept = extractUserAttribute(
            user,
            "dept_code",
            "deptCode",
            "dept",
            "department",
            "org_code",
            "orgCode",
            "dts_org_id",
            "dtsOrgId"
        );
        if (StringUtils.hasText(dept)) {
            return dept.trim();
        }
        return null;
    }

    /**
     * Sprint-17 hotfix — surface the department display name alongside the code.
     * Falls back to a small set of common attribute keys (Keycloak is free to put
     * the human-readable label under any of them depending on the IdP mapping).
     */
    private String resolveDeptName(Map<String, Object> user) {
        String name = extractUserAttribute(
            user,
            "dept_name",
            "deptName",
            "department_name",
            "departmentName",
            "org_name",
            "orgName"
        );
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        return null;
    }

    private String normalizePersonnelLevel(String raw) {
        com.yuzhi.dts.common.security.SecurityLevelCatalog.PersonnelSecurityLevel level =
            com.yuzhi.dts.common.security.SecurityLevelCatalog.PersonnelSecurityLevel.parse(raw);
        return level != null ? level.code() : null;
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
                    mapped.add(AuthoritiesConstants.EMPLOYEE);
                    yield true;
                }
                case "DEPT_DATA_DEV", "DEPT_EDITOR", "DEPARTMENT_EDITOR" -> {
                    // legacy alias (data-dev removed) -> treat as data-owner
                    mapped.add(AuthoritiesConstants.DEPT_DATA_OWNER);
                    yield true;
                }
                case "DEPT_DATA_OWNER", "DEPT_OWNER", "DEPARTMENT_OWNER" -> {
                    mapped.add(AuthoritiesConstants.DEPT_DATA_OWNER);
                    yield true;
                }
                case "DEPT_LEADER", "DEPARTMENT_LEADER" -> {
                    mapped.add(AuthoritiesConstants.DEPT_LEADER);
                    yield true;
                }
                case "INST_DATA_VIEWER", "INST_VIEWER", "INSTITUTE_VIEWER", "INSTITUTION_VIEWER" -> {
                    mapped.add(AuthoritiesConstants.EMPLOYEE);
                    yield true;
                }
                case "INST_DATA_DEV", "INST_EDITOR", "INSTITUTE_EDITOR", "INSTITUTION_EDITOR" -> {
                    // legacy alias (data-dev removed) -> treat as data-owner
                    mapped.add(AuthoritiesConstants.INST_DATA_OWNER);
                    yield true;
                }
                case "INST_DATA_OWNER", "INST_OWNER", "INSTITUTE_OWNER", "INSTITUTION_OWNER" -> {
                    mapped.add(AuthoritiesConstants.INST_DATA_OWNER);
                    yield true;
                }
                case "INST_LEADER", "INSTITUTE_LEADER", "INSTITUTION_LEADER" -> {
                    mapped.add(AuthoritiesConstants.INST_LEADER);
                    yield true;
                }
                case "EMPLOYEE", "USER_EMPLOYEE" -> {
                    mapped.add(AuthoritiesConstants.EMPLOYEE);
                    yield true;
                }
                case "SYS_ADMIN", "SYSADMIN", "AUTH_ADMIN", "AUTHADMIN", "SECURITY_AUDITOR", "SECURITYAUDITOR", "AUDIT_ADMIN", "AUDITADMIN", "AUDITOR_ADMIN" -> {
                    // triad roles handled earlier; skip silently
                    yield true;
                }
                default -> false;
            };
            if (handled) {
                // Also preserve the role's normalized original name as its unique identifier.
                // AdminDirectoryGateway.normalizeRoleName() produces ROLE_ + sanitized(rawName),
                // so the granteeId stored in analytics_screen_access uses that same form.
                // Without this, aliases like "institute_leader" → ROLE_INST_LEADER (canonical)
                // would never match granteeId ROLE_INSTITUTE_LEADER (from normalizeRoleName).
                String original = sanitizeRoleToken(up);
                if (!original.isEmpty()) {
                    mapped.add("ROLE_" + original);
                }
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
