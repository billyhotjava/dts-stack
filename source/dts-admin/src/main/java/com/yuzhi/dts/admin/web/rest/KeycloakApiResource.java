package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.service.user.UserOperationRequest;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.domain.AdminRoleMember;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.security.session.AdminSessionCloseReason;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry;
import com.yuzhi.dts.admin.web.rest.dto.PkiChallengeView;
import com.yuzhi.dts.common.net.IpAddressUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "keycloak")
public class KeycloakApiResource {

    private final InMemoryStores stores;
    private final AuditV2Service auditV2Service;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminUserService adminUserService;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final AdminRoleMemberRepository roleMemberRepo;
    private final AdminSessionRegistry adminSessionRegistry;
    private final AdminKeycloakUserRepository userRepository;
    private final ConcurrentMap<String, Instant> recentRoleAudits = new ConcurrentHashMap<>();
    private static final Duration ROLE_AUDIT_DUP_WINDOW = Duration.ofSeconds(2);

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String managementClientId;

    @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}")
    private String managementClientSecret;

    @org.springframework.beans.factory.annotation.Value("${dts.admin.userlist.exclude-roles:ROLE_SYS_ADMIN,ROLE_AUTH_ADMIN,ROLE_SECURITY_AUDITOR,ROLE_OP_ADMIN}")
    private String excludeRolesForUserList;

    @org.springframework.beans.factory.annotation.Value("${dts.admin.login.allowed-roles:ROLE_SYS_ADMIN,ROLE_AUTH_ADMIN,ROLE_SECURITY_AUDITOR}")
    private String allowedRolesForAdminLogin;

    // Config: triad IP allowlist (password login only; PKI unaffected)
    @org.springframework.beans.factory.annotation.Value("${dts.security.ip-allowlist.enabled:true}")
    private boolean ipAllowEnabled;

    @org.springframework.beans.factory.annotation.Value("${dts.security.ip-allowlist.triad-usernames:sysadmin,authadmin,auditadmin}")
    private String ipAllowTriadCsv;
    private java.util.Set<String> triadConfigured;

    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";
    private static final Set<String> PROTECTED_USERNAMES = Set.of("sysadmin",  "authadmin", "auditadmin", "opadmin");
    private static final Map<String, String> BUILTIN_ADMIN_LABELS = Map.of(
        "sysadmin",
        "系统管理员",
        "authadmin",
        "授权管理员",
        "auditadmin",
        "安全审计员",
        "opadmin",
        "运维管理员"
    );

    public KeycloakApiResource(
        InMemoryStores stores,
        AuditV2Service auditV2Service,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient,
        AdminUserService adminUserService,
        AdminRoleAssignmentRepository roleAssignRepo,
        AdminRoleMemberRepository roleMemberRepo,
        AdminSessionRegistry adminSessionRegistry,
        AdminKeycloakUserRepository userRepository
    ) {
        this.stores = stores;
        this.auditV2Service = auditV2Service;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.adminUserService = adminUserService;
        this.roleAssignRepo = roleAssignRepo;
        this.roleMemberRepo = roleMemberRepo;
        this.adminSessionRegistry = adminSessionRegistry;
        this.userRepository = userRepository;
    }

    // ---- Users ----
    @GetMapping("/keycloak/users")
    public ResponseEntity<List<KeycloakUserDTO>> listUsers(
        @RequestParam(defaultValue = "0") int first,
        @RequestParam(defaultValue = "100") int max,
        HttpServletRequest request
    ) {
        String token = adminAccessToken();
        List<KeycloakUserDTO> list = filterProtectedUsers(keycloakAdminClient.listUsers(first, max, token));
        // Exclude users who have any of the configured admin roles
        Set<String> excludedIdsByRole = resolveExcludedUserIdsByRoles(token);
        if (!excludedIdsByRole.isEmpty() && list != null && !list.isEmpty()) {
            list = list.stream().filter(u -> u.getId() == null || !excludedIdsByRole.contains(u.getId())).toList();
        }
        boolean fromCache = false;
        if (!list.isEmpty()) {
            list.forEach(this::cacheUser);
        } else {
            list = filterProtectedUsers(stores.listUsers(first, max));
            if (list != null && !list.isEmpty() && !excludedIdsByRole.isEmpty()) {
                list = list.stream().filter(u -> u.getId() == null || !excludedIdsByRole.contains(u.getId())).toList();
            }
            fromCache = true;
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("count", list.size());
        auditDetail.put("source", fromCache ? "cache" : "keycloak");
        auditDetail.put("excludedByRoleCount", excludedIdsByRole.size());
        String actor = currentUser();
        if (!isAuditSuppressed()) {
            recordUserActionV2(
                actor,
                ButtonCodes.USER_LIST,
                AuditResultStatus.SUCCESS,
                null,
                null,
                null,
                new LinkedHashMap<>(auditDetail),
                request,
                "/api/keycloak/users",
                "GET",
                "查看用户列表（共 " + list.size() + " 个）"
            );
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/search")
    public ResponseEntity<List<KeycloakUserDTO>> searchUsers(@RequestParam String username, HttpServletRequest request) {
        String q = username == null ? "" : username.toLowerCase();
        String token = adminAccessToken();
        List<KeycloakUserDTO> list = filterProtectedUsers(keycloakAdminClient
            .findByUsername(username, adminAccessToken())
            .map(List::of)
            .orElseGet(List::of));
        if (list.isEmpty()) {
            list = filterProtectedUsers(stores
                .users
                .values()
                .stream()
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                .toList());
        } else {
            list.forEach(this::cacheUser);
        }
        // Exclude by admin roles
        Set<String> excludedIdsByRole = resolveExcludedUserIdsByRoles(token);
        if (!excludedIdsByRole.isEmpty() && list != null && !list.isEmpty()) {
            list = list.stream().filter(u -> u.getId() == null || !excludedIdsByRole.contains(u.getId())).toList();
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("query", q);
        auditDetail.put("count", list.size());
        auditDetail.put("excludedByRoleCount", excludedIdsByRole.size());
        String actor = currentUser();
        recordUserActionV2(
            actor,
            ButtonCodes.USER_SEARCH,
            AuditResultStatus.SUCCESS,
            null,
            null,
            null,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/search",
            "GET",
            "搜索用户：" + q
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, HttpServletRequest request) {
        String token = adminAccessToken();
        KeycloakUserDTO u = keycloakAdminClient.findById(id, token).orElse(stores.findUserById(id));
        if (u != null) {
            try {
                // Enrich with current group memberships as full paths for UI pre-selection
                List<com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO> groups = keycloakAdminClient.listUserGroups(u.getId(), token);
                if (groups != null && !groups.isEmpty()) {
                    List<String> paths = new java.util.ArrayList<>();
                    for (var g : groups) {
                        if (g.getPath() != null && !g.getPath().isBlank()) paths.add(g.getPath());
                    }
                    if (!paths.isEmpty()) {
                        u.setGroups(paths);
                    }
                }
                // Fallback: if groups are empty but dept_code attribute exists, map by dts_org_id -> group path
                if ((u.getGroups() == null || u.getGroups().isEmpty()) && u.getAttributes() != null) {
                    String deptCode = null;
                    java.util.List<String> vals = u.getAttributes().get("dept_code");
                    if (vals != null && !vals.isEmpty()) deptCode = vals.get(0);
                    if (deptCode != null && !deptCode.isBlank()) {
                        for (var root : keycloakAdminClient.listGroups(token)) {
                            java.util.Optional<String> matchPath = findGroupPathByOrgId(root, deptCode);
                            if (matchPath.isPresent()) {
                                // Avoid Optional.get(); prefer orElseThrow per modernizer rule
                                u.setGroups(java.util.List.of(matchPath.orElseThrow()));
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            cacheUser(u);
        }
        String actor = currentUser();
        if (u == null) {
            Map<String, Object> detail = Map.of("error", "NOT_FOUND");
            if (!isAuditSuppressed()) {
                recordUserActionV2(
                    actor,
                    ButtonCodes.USER_VIEW,
                    AuditResultStatus.FAILED,
                    id,
                    null,
                    null,
                    new LinkedHashMap<>(detail),
                    request,
                    "/api/keycloak/users/" + id,
                    "GET",
                    "查看用户失败：" + id
                );
            }
            return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", u.getUsername());
        auditDetail.put("status", Boolean.TRUE.equals(u.getEnabled()) ? "ENABLED" : "DISABLED");
        if (!isAuditSuppressed()) {
            recordUserActionV2(
                actor,
                ButtonCodes.USER_VIEW,
                AuditResultStatus.SUCCESS,
                u.getId(),
                u.getUsername(),
                null,
                new LinkedHashMap<>(auditDetail),
                request,
                "/api/keycloak/users/" + id,
                "GET",
                "查看用户：" + Optional.ofNullable(u.getUsername()).orElse(id)
            );
        }
        return ResponseEntity.ok(u);
    }

    @PostMapping("/keycloak/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@RequestBody KeycloakUserDTO payload, HttpServletRequest request) {
        try {
            LOG.info("FE payload(createUser): username={}, email={}, enabled={}, hasAttributes={}, groupsCount={}",
                payload.getUsername(), payload.getEmail(), payload.getEnabled(),
                payload.getAttributes() != null && !payload.getAttributes().isEmpty(),
                payload.getGroups() == null ? 0 : payload.getGroups().size());
            LOG.info("FE payload(createUser) names: firstName={}, lastName={}, fullName={}",
                payload.getFirstName(), payload.getLastName(), payload.getFullName());
            if (payload.getAttributes() != null) {
                String pl = extractFirst(payload.getAttributes(), "person_level", "person_security_level", "personnel_security_level");
                LOG.info("FE payload(createUser) attributes: person_level={} ", pl);
            }
        } catch (Exception ignored) {}
        String actor = currentUser();
        String requestedUsername = payload.getUsername();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", requestedUsername);
        auditDetail.put("email", payload.getEmail());
        auditDetail.put("enabled", payload.getEnabled());
        if (payload.getFullName() != null) {
            auditDetail.put("fullName", payload.getFullName());
        }
        if (payload.getUsername() == null || payload.getUsername().isBlank()) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "用户名不能为空");
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名不能为空"));
        }
        try {
            UserOperationRequest command = toOperationRequest(payload);
            LOG.info("Resolved(createUser) command: username={}, fullName={}, email={}, groupsCount={}",
                command.getUsername(), command.getFullName(), command.getEmail(),
                command.getGroupPaths() == null ? 0 : command.getGroupPaths().size());
            ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitCreate(
                command,
                currentUser(),
                clientIp(request)
            );
            String changeRequestRef = changeRequestRef(approval);
            if (changeRequestRef != null) {
                auditDetail.put("changeRequestRef", changeRequestRef);
            }
            recordUserActionV2(
                actor,
                ButtonCodes.USER_CREATE,
                AuditResultStatus.SUCCESS,
                null,
                requestedUsername,
                approval,
                new LinkedHashMap<>(auditDetail),
                request,
                "/api/keycloak/users",
                "POST",
                "提交新增用户审批：" + requestedUsername
            );
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "requestId",
                approval.id,
                "status",
                approval.status,
                "message",
                "操作已提交，等待审批"
            )));
        } catch (IllegalStateException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordUserActionV2(
                actor,
                ButtonCodes.USER_CREATE,
                AuditResultStatus.FAILED,
                null,
                requestedUsername,
                null,
                new LinkedHashMap<>(failure),
                request,
                "/api/keycloak/users",
                "POST",
                "新增用户失败：" + requestedUsername
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordUserActionV2(
                actor,
                ButtonCodes.USER_CREATE,
                AuditResultStatus.FAILED,
                null,
                requestedUsername,
                null,
                new LinkedHashMap<>(failure),
                request,
                "/api/keycloak/users",
                "POST",
                "新增用户失败：" + requestedUsername
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(
        @PathVariable String id,
        @RequestBody KeycloakUserDTO patch,
        HttpServletRequest request
    ) {
        try {
            LOG.info("FE payload(updateUser): id={}, username={}, email={}, enabled={}, hasAttributes={}, groupsCount={}, realmRolesCount={}",
                id, patch.getUsername(), patch.getEmail(), patch.getEnabled(),
                patch.getAttributes() != null && !patch.getAttributes().isEmpty(),
                patch.getGroups() == null ? 0 : patch.getGroups().size(),
                patch.getRealmRoles() == null ? 0 : patch.getRealmRoles().size());
            LOG.info("FE payload(updateUser) names: firstName={}, lastName={}, fullName={}",
                patch.getFirstName(), patch.getLastName(), patch.getFullName());
            if (patch.getAttributes() != null) {
                String pl = extractFirst(patch.getAttributes(), "person_level", "person_security_level", "personnel_security_level");
                LOG.info("FE payload(updateUser) attributes: person_level={}", pl);
            }
        } catch (Exception ignored) {}
        String username = resolveUsername(id, patch.getUsername(), adminAccessToken());
        String actor = currentUser();
        if (username == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        }
        UserOperationRequest command = toOperationRequest(patch);
        command.setUsername(username);
        LOG.info("Resolved(updateUser) command: id={}, username={}, fullName={}, email={}, groupsCount={}",
            id, command.getUsername(), command.getFullName(), command.getEmail(),
            command.getGroupPaths() == null ? 0 : command.getGroupPaths().size());
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        Map<String, Object> req = new LinkedHashMap<>();
        // 允许为空值，避免 Map.of 在遇到 null 时抛出 NPE
        if (command.getFullName() != null) req.put("fullName", command.getFullName());
        if (command.getEmail() != null) req.put("email", command.getEmail());
        req.put("groups", command.getGroupPaths() == null ? 0 : command.getGroupPaths().size());
        auditDetail.put("request", req);
        try {
            ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitUpdate(
                username,
                command,
                actor,
                clientIp(request)
            );
            String changeRequestRef = changeRequestRef(approval);
            if (changeRequestRef != null) {
                auditDetail.put("changeRequestRef", changeRequestRef);
            }
            recordUserActionV2(
                actor,
                ButtonCodes.USER_UPDATE,
                AuditResultStatus.SUCCESS,
                id,
                username,
                approval,
                new LinkedHashMap<>(auditDetail),
                request,
                "/api/keycloak/users/" + id,
                "PUT",
                "提交修改用户审批：" + username
            );
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "requestId",
                approval.id,
                "status",
                approval.status,
                "message",
                "用户信息更新请求已提交，等待审批"
            )));
        } catch (IllegalStateException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordUserActionV2(
                actor,
                ButtonCodes.USER_UPDATE,
                AuditResultStatus.FAILED,
                id,
                username,
                null,
                new LinkedHashMap<>(failure),
                request,
                "/api/keycloak/users/" + id,
                "PUT",
                "修改用户失败：" + username
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordUserActionV2(
                actor,
                ButtonCodes.USER_UPDATE,
                AuditResultStatus.FAILED,
                id,
                username,
                null,
                new LinkedHashMap<>(failure),
                request,
                "/api/keycloak/users/" + id,
                "PUT",
                "修改用户失败：" + username
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteUser(@PathVariable String id, HttpServletRequest request) {
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("用户删除功能已禁用，请改用停用操作"));
    }

    @PostMapping("/keycloak/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String password = Objects.toString(body.get("password"), null);
        boolean temporary = Boolean.TRUE.equals(body.get("temporary"));
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("密码不能为空"));
        }
        String actor = currentUser();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("temporary", temporary);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitResetPassword(
            username,
            password,
            temporary,
            actor,
            clientIp(request)
        );
        String changeRequestRef = changeRequestRef(approval);
        if (changeRequestRef != null) {
            auditDetail.put("changeRequestRef", changeRequestRef);
        }
        recordUserActionV2(
            actor,
            ButtonCodes.USER_RESET_PASSWORD,
            AuditResultStatus.SUCCESS,
            id,
            username,
            approval,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/" + id + "/reset-password",
            "POST",
            "提交重置密码审批：" + username
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "重置密码请求已提交，等待审批"
        )));
    }

    @PutMapping("/keycloak/users/{id}/enabled")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setEnabled(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Object val = body.get("enabled");
        boolean enabled = Boolean.TRUE.equals(val) || (val instanceof Boolean b && b);
        String actor = currentUser();
        String buttonCode = enabled ? ButtonCodes.USER_ENABLE : ButtonCodes.USER_DISABLE;
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("enabled", enabled);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetEnabled(
            username,
            enabled,
            actor,
            clientIp(request)
        );
        String changeRequestRef = changeRequestRef(approval);
        if (changeRequestRef != null) {
            auditDetail.put("changeRequestRef", changeRequestRef);
        }
        recordUserActionV2(
            actor,
            buttonCode,
            AuditResultStatus.SUCCESS,
            id,
            username,
            approval,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/" + id + "/enabled",
            "PUT",
            (enabled ? "提交启用用户审批：" : "提交停用用户审批：") + username
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "启用/禁用请求已提交，等待审批"
        )));
    }

    // ---- ABAC person_level management (dev helper) ----
    @PutMapping("/keycloak/users/{id}/person-level")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setPersonLevel(
        @PathVariable String id,
        @RequestBody Map<String, String> body,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String level = String.valueOf(body.getOrDefault("person_level", "")).toUpperCase();
        if (!List.of("NON_SECRET", "GENERAL", "IMPORTANT", "CORE").contains(level)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的人员密级"));
        }
        String actor = currentUser();
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetPersonLevel(
            username,
            level,
            actor,
            clientIp(request),
            null
        );
        String changeRequestRef = changeRequestRef(approval);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("level", level);
        if (changeRequestRef != null) {
            auditDetail.put("changeRequestRef", changeRequestRef);
        }
        recordUserActionV2(
            actor,
            ButtonCodes.USER_SET_PERSON_LEVEL,
            AuditResultStatus.SUCCESS,
            id,
            username,
            approval,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/" + id + "/person-level",
            "PUT",
            "提交人员密级调整审批：" + username
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "密级调整请求已提交，等待审批"
        )));
    }

    @GetMapping("/keycloak/users/{id}/abac-claims")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAbacClaims(@PathVariable String id, HttpServletRequest request) {
        KeycloakUserDTO u = stores.findUserById(id);
        String actor = currentUser();
        if (u == null) {
            Map<String, Object> detail = Map.of("error", "NOT_FOUND");
            recordUserActionV2(
                actor,
                ButtonCodes.USER_VIEW,
                AuditResultStatus.FAILED,
                id,
                null,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/users/" + id + "/abac-claims",
                "GET",
                "查看用户 ABAC 信息失败：" + id
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        }
        String person = u.getAttributes().getOrDefault("person_level", List.of("NON_SECRET")).stream().findFirst().orElse("NON_SECRET");
        List<String> levels = List.of();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("personLevel", person);
        detail.put("dataLevelCount", levels.size());
        recordUserActionV2(
            actor,
            ButtonCodes.USER_VIEW,
            AuditResultStatus.SUCCESS,
            u.getId(),
            u.getUsername(),
            null,
            new LinkedHashMap<>(detail),
            request,
            "/api/keycloak/users/" + id + "/abac-claims",
            "GET",
            "查看用户 ABAC 信息：" + Optional.ofNullable(u.getUsername()).orElse(id)
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of("person_level", person, "data_levels", levels)));
    }

    private UserOperationRequest toOperationRequest(KeycloakUserDTO payload) {
        Map<String, List<String>> attributes = sanitizeAttributes(payload.getAttributes());

        UserOperationRequest command = new UserOperationRequest();
        command.setUsername(trim(payload.getUsername()));
        command.setFullName(resolveFullName(payload, attributes));
        command.setEmail(trim(payload.getEmail()));
        command.setPhone(resolvePhone(attributes));
        Boolean enabled = payload.getEnabled();
        if (enabled == null && trim(payload.getId()) == null) {
            enabled = Boolean.TRUE;
        }
        command.setEnabled(enabled);
        if (payload.isRealmRolesSpecified()) {
            command.setRealmRoles(normalizeList(payload.getRealmRoles()));
        } else {
            command.markRealmRolesUnspecified();
        }
        command.setGroupPaths(normalizeList(payload.getGroups()));
        command.setAttributes(attributes);

        String personLevel = resolvePersonLevel(attributes);
        command.setPersonSecurityLevel(personLevel);
        command.setReason(extractFirst(attributes, "reason", "approval_reason"));

        if (!attributes.containsKey("person_level") && personLevel != null) {
            attributes.put("person_level", List.of(personLevel));
        }
        return command;
    }

    private String resolveUsername(String id, String suppliedUsername, String accessToken) {
        String candidate = trim(suppliedUsername);
        if (candidate != null) {
            return candidate;
        }
        if (id != null && !id.isBlank()) {
            KeycloakUserDTO cached = stores.findUserById(id);
            if (cached != null && cached.getUsername() != null) {
                return cached.getUsername();
            }
            return keycloakAdminClient
                .findById(id, accessToken)
                .map(user -> {
                    cacheUser(user);
                    return user.getUsername();
                })
                .orElse(null);
        }
        return null;
    }

    private Map<String, List<String>> sanitizeAttributes(Map<String, List<String>> raw) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<String> values = normalizeList(entry.getValue());
                if (!values.isEmpty()) {
                    sanitized.put(entry.getKey(), values);
                }
            }
        }
        return sanitized;
    }

    private String resolveFullName(KeycloakUserDTO payload, Map<String, List<String>> attributes) {
        String fullName = trim(payload.getFullName());
        if (fullName != null) {
            return fullName;
        }
        String attrName = extractFirst(attributes, "fullName", "fullname", "display_name", "displayName");
        if (attrName != null) {
            return attrName;
        }
        String firstName = trim(payload.getFirstName());
        String lastName = trim(payload.getLastName());
        if (firstName != null || lastName != null) {
            StringBuilder builder = new StringBuilder();
            if (lastName != null) {
                builder.append(lastName);
            }
            if (firstName != null) {
                if (builder.length() > 0) builder.append(' ');
                builder.append(firstName);
            }
            return builder.toString();
        }
        return trim(payload.getUsername());
    }

    private String resolvePhone(Map<String, List<String>> attributes) {
        String direct = extractFirst(
            attributes,
            "phone",
            "phone_number",
            "phoneNumber",
            "mobile",
            "mobile_number",
            "mobileNumber",
            "telephone"
        );
        if (direct != null) {
            return direct;
        }
        return null;
    }

    private String resolvePersonLevel(Map<String, List<String>> attributes) {
        String value = extractFirst(attributes, "person_level", "personLevel", "person_security_level", "personSecurityLevel");
        if (value == null) {
            return DEFAULT_PERSON_LEVEL;
        }
        return value.trim().toUpperCase().replace('-', '_');
    }

    private List<String> attributeList(Map<String, List<String>> attributes, String... keys) {
        for (String key : keys) {
            List<String> values = attributes.get(key);
            if (values != null && !values.isEmpty()) {
                return new ArrayList<>(values);
            }
        }
        return new ArrayList<>();
    }

    private List<KeycloakUserDTO> filterProtectedUsers(List<KeycloakUserDTO> users) {
        if (users == null || users.isEmpty()) {
            return users;
        }
        return users
            .stream()
            .filter(u -> u.getUsername() == null || PROTECTED_USERNAMES.stream().noneMatch(name -> name.equalsIgnoreCase(u.getUsername())))
            .toList();
    }

    private Set<String> parseRoleList(String csv) {
        if (csv == null || csv.isBlank()) return java.util.Set.of();
        String[] parts = csv.split(",");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            if (p == null) continue;
            String s = p.trim();
            if (!s.isBlank()) set.add(s);
        }
        return set;
    }

    private Set<String> resolveExcludedUserIdsByRoles(String accessToken) {
        Set<String> excluded = new java.util.HashSet<>();
        try {
            Set<String> roles = parseRoleList(this.excludeRolesForUserList);
            for (String role : roles) {
                try {
                    List<KeycloakUserDTO> members = keycloakAdminClient.listUsersByRealmRole(role, accessToken);
                    if (members != null && !members.isEmpty()) {
                        for (KeycloakUserDTO u : members) {
                            if (u != null && u.getId() != null && !u.getId().isBlank()) excluded.add(u.getId());
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("Failed to load users for role {} when filtering list: {}", role, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to resolve excluded user ids by roles: {}", ex.getMessage());
        }
        return excluded;
    }

    private List<String> normalizeList(Collection<?> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private String extractFirst(Map<String, List<String>> attributes, String... keys) {
        for (String key : keys) {
            List<String> values = attributes.get(key);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                if (value != null) {
                    String text = value.trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private void cacheUser(KeycloakUserDTO dto) {
        if (dto == null || dto.getId() == null) {
            return;
        }
        stores.users.put(dto.getId(), dto);
    }

    private String currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        if (authentication instanceof BearerTokenAuthentication bearer) {
            return bearer.getToken().getTokenValue();
        }
        return null;
    }

    private String adminAccessToken() {
        String fallbackToken = currentAccessToken();
        try {
            var tokenResponse = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
            String token = tokenResponse == null ? null : tokenResponse.accessToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Keycloak 管理客户端未返回 access_token");
            }
            // Lightweight trace to confirm service account token flow works
            org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class)
                .debug("Obtained admin access token using clientId={}", managementClientId);
            return token;
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class)
                .warn("Failed to obtain admin access token: {}", ex.getMessage());
            if (StringUtils.hasText(fallbackToken)) {
                org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class)
                    .debug("Falling back to current request access token after admin credentials failure");
                return fallbackToken;
            }
            throw new IllegalStateException("获取 Keycloak 管理客户端访问令牌失败: " + ex.getMessage(), ex);
        }
    }

    private String currentUser() {
        String actor = sanitizeActor(currentUserLogin().orElse(null));
        if (!StringUtils.hasText(actor)) {
            actor = sanitizeActor(extractUsernameFromToken(currentAccessToken()));
        }
        if (!StringUtils.hasText(actor)) {
            actor = "system";
        }
        return actor;
    }

    private String changeRequestRef(ApprovalDTOs.ApprovalRequest approval) {
        if (approval == null) {
            return null;
        }
        return changeRequestRefFrom(approval.id);
    }

    private String changeRequestRefFrom(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value <= 0) {
                return null;
            }
            return "CR-" + value;
        }
        String text = raw.toString();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("CR-")) {
            return trimmed;
        }
        boolean digits = trimmed.chars().allMatch(Character::isDigit);
        if (digits) {
            return "CR-" + trimmed;
        }
        return trimmed;
    }

    private void recordUserActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        String userId,
        String username,
        ApprovalDTOs.ApprovalRequest approval,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary
    ) {
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(buttonCode)) {
            return;
        }
        try {
            Map<String, Object> detailMap = detail != null ? new LinkedHashMap<>(detail) : new LinkedHashMap<>();
            String roleName = resolveRoleNameFromDetail(detail);
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            String normalizedMethod = method != null ? method.trim().toUpperCase(Locale.ROOT) : fallbackMethod;
            if (ButtonCodes.ROLE_CREATE.equals(buttonCode) && "PUT".equalsIgnoreCase(normalizedMethod)) {
                buttonCode = ButtonCodes.ROLE_UPDATE;
            }
            if (isDuplicateRoleAudit(actor, buttonCode, summary, roleName)) {
                return;
            }
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(resolveActorDisplayName(actor))
                .actorRoles(currentUserAuthorities())
                .summary(summary)
                .result(result)
                .client(clientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method);
            Long adminUserId = resolveAdminUserId(userId, username);
            if (adminUserId != null) {
                detailMap.putIfAbsent("sourcePrimaryKey", adminUserId);
                detailMap.putIfAbsent("sourceTable", "admin_keycloak_user");
                builder.metadata("sourcePrimaryKey", adminUserId);
                builder.metadata("sourceTable", "admin_keycloak_user");
            }
            if (!detailMap.isEmpty()) {
                builder.detail("detail", detailMap);
            }
            if (StringUtils.hasText(username)) {
                builder.metadata("username", username);
            }
            String label = StringUtils.hasText(username) ? username : userId;
            if (adminUserId != null) {
                builder.target("admin_keycloak_user", String.valueOf(adminUserId), label);
            }
            if (StringUtils.hasText(userId)) {
                builder.target("keycloak_user", userId, label);
            }
            if (approval != null) {
                long approvalId = approval.id;
                if (approvalId > 0) {
                    String changeRequestRef = changeRequestRef(approval);
                    if (StringUtils.hasText(changeRequestRef)) {
                        builder.changeRequestRef(changeRequestRef);
                        builder.target("change_request", approvalId, changeRequestRef);
                    } else {
                        builder.target("change_request", approvalId, "CR-" + approvalId);
                    }
                }
            } else if (detail != null && detail.containsKey("changeRequestRef")) {
                String ref = Objects.toString(detail.get("changeRequestRef"), null);
                if (StringUtils.hasText(ref)) {
                    builder.changeRequestRef(ref);
                }
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 audit for user action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private Long resolveAdminUserId(String keycloakId, String username) {
        Long primaryKey = null;
        if (StringUtils.hasText(keycloakId)) {
            primaryKey =
                userRepository
                    .findByKeycloakId(keycloakId.trim())
                    .map(AdminKeycloakUser::getId)
                    .orElse(null);
        }
        if (primaryKey == null && StringUtils.hasText(username)) {
            primaryKey =
                userRepository
                    .findByUsernameIgnoreCase(username.trim())
                    .map(AdminKeycloakUser::getId)
                    .orElse(null);
        }
        return primaryKey;
    }

    private String resolveRoleNameFromDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        for (String key : List.of("roleName", "role", "roleCode")) {
            Object candidate = detail.get(key);
            String text = normalizeRoleName(candidate);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        Object roles = detail.get("roles");
        String text = normalizeRoleName(roles);
        return StringUtils.hasText(text) ? text : null;
    }

    private String normalizeRoleName(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof String s) {
            return StringUtils.hasText(s) ? s.trim() : null;
        }
        if (source instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element == null) {
                    continue;
                }
                if (element instanceof String s && StringUtils.hasText(s)) {
                    return s.trim();
                }
                String text = element.toString();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        String fallback = source.toString();
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private void recordGroupActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        String groupId,
        String groupName,
        List<AuditActionRequest.AuditTarget> extraTargets,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary,
        boolean allowEmptyTargets
    ) {
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(buttonCode)) {
            return;
        }
        try {
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(resolveActorDisplayName(actor))
                .actorRoles(currentUserAuthorities())
                .summary(summary)
                .result(result)
                .client(clientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method);
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            boolean hasTarget = false;
            if (StringUtils.hasText(groupId)) {
                String label = StringUtils.hasText(groupName) ? groupName : resolveGroupName(groupId);
                builder.target("keycloak_group", groupId, label);
                hasTarget = true;
            }
            if (extraTargets != null) {
                for (AuditActionRequest.AuditTarget target : extraTargets) {
                    if (target == null || target.id() == null || !StringUtils.hasText(target.table())) {
                        continue;
                    }
                    builder.target(target.table(), target.id(), target.label());
                    hasTarget = true;
                }
            }
            if (!hasTarget) {
                if (allowEmptyTargets) {
                    builder.allowEmptyTargets();
                } else if (detail != null && !detail.isEmpty()) {
                    builder.allowEmptyTargets();
                } else {
                    builder.allowEmptyTargets();
                }
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 audit for group action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private void recordRoleActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        String roleName,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary,
        boolean allowEmptyTargets
    ) {
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(buttonCode)) {
            return;
        }
        try {
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(resolveActorDisplayName(actor))
                .actorRoles(currentUserAuthorities())
                .summary(summary)
                .result(result)
                .client(clientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method);
            applyRoleOperationOverride(builder, buttonCode);
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            boolean hasTarget = false;
            if (StringUtils.hasText(roleName)) {
                String trimmed = roleName.trim();
                builder.target("keycloak_role", trimmed, trimmed);
                hasTarget = true;
            }
            if (!hasTarget && allowEmptyTargets) {
                builder.allowEmptyTargets();
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 audit for role action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private boolean isDuplicateRoleAudit(String actor, String buttonCode, String summary, String roleName) {
        String key = String.join(
            "|",
            buttonCode != null ? buttonCode.trim().toUpperCase(Locale.ROOT) : "UNKNOWN",
            actor != null ? actor.trim().toLowerCase(Locale.ROOT) : "anonymous",
            summary != null ? summary.trim() : "",
            roleName != null ? roleName.trim().toLowerCase(Locale.ROOT) : "-"
        );
        Instant now = Instant.now();
        AtomicBoolean duplicate = new AtomicBoolean(false);
        recentRoleAudits.compute(key, (k, last) -> {
            if (last != null && Duration.between(last, now).abs().compareTo(ROLE_AUDIT_DUP_WINDOW) <= 0) {
                duplicate.set(true);
                return last;
            }
            return now;
        });
        return duplicate.get();
    }

    private void applyRoleOperationOverride(AuditActionRequest.Builder builder, String buttonCode) {
        if (builder == null || !StringUtils.hasText(buttonCode)) {
            return;
        }
        AuditOperationKind kind;
        String normalized = buttonCode.trim().toUpperCase(Locale.ROOT);
        String operationName;
        switch (normalized) {
            case ButtonCodes.ROLE_CREATE -> {
                kind = AuditOperationKind.CREATE;
                operationName = "新增角色";
            }
            case ButtonCodes.ROLE_UPDATE -> {
                kind = AuditOperationKind.UPDATE;
                operationName = "修改角色";
            }
            case ButtonCodes.ROLE_DELETE -> {
                kind = AuditOperationKind.DELETE;
                operationName = "删除角色";
            }
            default -> {
                return;
            }
        }
        builder.operationOverride(normalized, operationName, kind);
    }

    private void recordApprovalActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        Long approvalId,
        String changeRequestRef,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary,
        boolean allowEmptyTargets
    ) {
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(buttonCode)) {
            return;
        }
        try {
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(resolveActorDisplayName(actor))
                .actorRoles(currentUserAuthorities())
                .summary(summary)
                .result(result)
                .client(clientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method);
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            String ref = changeRequestRef;
            if (!StringUtils.hasText(ref) && approvalId != null && approvalId > 0) {
                ref = changeRequestRefFrom(approvalId);
            }
            if (StringUtils.hasText(ref)) {
                builder.changeRequestRef(ref);
            }
            if (approvalId != null && approvalId > 0) {
                String label = StringUtils.hasText(ref) ? ref : String.valueOf(approvalId);
                builder.target("approval_request", approvalId, label);
            } else if (allowEmptyTargets) {
                builder.allowEmptyTargets();
            }
            if (!StringUtils.hasText(ref) && approvalId == null && allowEmptyTargets) {
                builder.allowEmptyTargets();
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 audit for approval action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private void recordAuthActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary
    ) {
        if (isAuditSuppressed()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Skip auth audit buttonCode={} summary={} due to audit suppression flag", buttonCode, summary);
            }
            return;
        }
        if (ButtonCodes.AUTH_ADMIN_REFRESH.equals(buttonCode)) {
            // Token refresh happens automatically; suppress audit noise.
            return;
        }
        String normalizedActor = sanitizeActor(actor);
        if (!StringUtils.hasText(normalizedActor)) {
            normalizedActor = "system";
        }
        if (!StringUtils.hasText(buttonCode)) {
            return;
        }
        try {
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, buttonCode)
                .actorName(resolveActorDisplayName(normalizedActor))
                .actorRoles(currentUserAuthorities())
                .summary(summary)
                .result(result)
                .client(clientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method)
                .allowEmptyTargets();
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 audit for auth action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private String resolveGroupName(String groupId) {
        if (!StringUtils.hasText(groupId)) {
            return null;
        }
        KeycloakGroupDTO dto = stores.groups.get(groupId);
        if (dto != null && StringUtils.hasText(dto.getName())) {
            return dto.getName().trim();
        }
        return groupId;
    }

    private String resolveActorDisplayName(String actor) {
        if (!StringUtils.hasText(actor)) {
            return null;
        }
        String trimmed = actor.trim();
        String builtin = BUILTIN_ADMIN_LABELS.get(trimmed.toLowerCase(Locale.ROOT));
        try {
            Map<String, String> resolved = adminUserService.resolveDisplayNames(List.of(trimmed));
            String display = resolved.get(trimmed);
            if (!StringUtils.hasText(display)) {
                display = resolved.get(trimmed.toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(display)) {
                return display;
            }
        } catch (Exception ex) {
            LOG.debug("Failed to resolve display name for {}: {}", actor, ex.getMessage());
        }
        if (StringUtils.hasText(builtin)) {
            return builtin;
        }
        return trimmed;
    }

    private boolean registerSession(String username, KeycloakAuthService.TokenResponse tokens) {
        if (tokens == null) {
            return false;
        }
        String actor = sanitizeActor(username);
        if (!StringUtils.hasText(actor)) {
            actor = username;
        }
        if (!StringUtils.hasText(actor)) {
            actor = currentUser();
        }
        AdminSessionRegistry.SessionRegistration registration = adminSessionRegistry.registerLogin(
            actor,
            tokens.sessionState(),
            tokens.accessToken(),
            tokens.refreshToken(),
            resolveExpiry(tokens.expiresIn()),
            resolveExpiry(tokens.refreshExpiresIn())
        );
        if (registration.takeover() && LOG.isInfoEnabled()) {
            LOG.info(
                "User {} took over {} active session(s); new session id = {}",
                actor,
                registration.terminatedSessions(),
                registration.session().getSessionId()
            );
        }
        return registration.takeover();
    }

    private String resolveActorForLogout(Map<String, String> body, String refreshToken) {
        String actor = sanitizeActor(currentUserLogin().orElse(null));
        if (!StringUtils.hasText(actor) && body != null) {
            actor = firstNonBlank(
                body.get("username"),
                body.get("user"),
                body.get("account"),
                body.get("principal"),
                body.get("operator")
            );
            actor = sanitizeActor(actor);
        }
        if (!StringUtils.hasText(actor)) {
            actor = extractUsernameFromToken(refreshToken);
        }
        if (!StringUtils.hasText(actor)) {
            actor = "system";
        }
        return actor;
    }

    private String sanitizeActor(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if ("anonymous".equals(lowered) || "anonymoususer".equals(lowered)) {
            return null;
        }
        return trimmed;
    }

    private String resolveActorForRefresh(String refreshToken) {
        String actor = sanitizeActor(currentUserLogin().orElse(null));
        if (!StringUtils.hasText(actor)) {
            actor = sanitizeActor(extractUsernameFromToken(refreshToken));
        }
        if (!StringUtils.hasText(actor)) {
            actor = sanitizeActor(extractUsernameFromToken(currentAccessToken()));
        }
        if (!StringUtils.hasText(actor)) {
            actor = "system";
        }
        return actor;
    }

    private Optional<String> currentUserLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        String direct = extractUsernameFromAuthentication(authentication);
        if (StringUtils.hasText(direct)) {
            return Optional.of(direct.trim());
        }
        return Optional.empty();
    }

    private List<String> currentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of();
        }
        return authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toList());
    }

    private String currentAuditableLogin() {
        String candidate = sanitizeActor(currentUserLogin().orElse(null));
        return StringUtils.hasText(candidate) ? candidate : "system";
    }

    private String extractUsernameFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String name = authentication.getName();
        if (StringUtils.hasText(name)) {
            return name;
        }
        return extractUsernameFromPrincipal(authentication.getPrincipal());
    }

    private String extractUsernameFromPrincipal(Object principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getName();
        }
        if (principal instanceof BearerTokenAuthentication bearer) {
            return bearer.getName();
        }
        if (principal instanceof Map<?, ?> map) {
            Object preferred = map.get("preferred_username");
            if (preferred instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object username = map.get("username");
            if (username instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object legacy = map.get("user_name");
            if (legacy instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
            Object sub = map.get("sub");
            if (sub instanceof String s && StringUtils.hasText(s)) {
                return s;
            }
        }
        if (principal instanceof String str) {
            return str;
        }
        return null;
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

    private String extractUsernameFromToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(decoded);
            if (node.hasNonNull("preferred_username")) {
                return node.get("preferred_username").asText();
            }
            if (node.hasNonNull("username")) {
                return node.get("username").asText();
            }
            if (node.hasNonNull("sub")) {
                return node.get("sub").asText();
            }
        } catch (Exception ex) {
            LOG.debug("Failed to extract username from refresh token: {}", ex.getMessage());
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String resolved = IpAddressUtils.resolveClientIp(
            request.getHeader("X-Forwarded-For"),
            request.getHeader("X-Real-IP"),
            request.getRemoteAddr()
        );
        return resolved != null ? resolved : "unknown";
    }

    private boolean isAuditSuppressed() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                if (request != null) {
                    String header = request.getHeader("X-Audit-Silent");
                    if (header != null && header.trim().equalsIgnoreCase("true")) {
                        return true;
                    }
                    String param = request.getParameter("auditSilent");
                    if (param != null && param.trim().equalsIgnoreCase("true")) {
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.debug("Audit suppression check failed: {}", ex.getMessage());
        }
        return false;
    }

    private Instant resolveExpiry(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return null;
        }
        return Instant.now().plusSeconds(seconds);
    }

    @GetMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> getUserRoles(@PathVariable String id) {
        String adminToken = adminAccessToken();
        List<String> names = Optional.ofNullable(keycloakAdminClient.listUserRealmRoles(id, adminToken)).orElse(List.of());
        org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class)
            .info("Fetched user realm roles from Keycloak: userId={}, roles={}", id, names);
        Map<String, KeycloakRoleDTO> catalog = new LinkedHashMap<>();
        for (KeycloakRoleDTO role : adminUserService.listRealmRoles()) {
            if (role.getName() != null) catalog.put(role.getName(), role);
        }
        List<KeycloakRoleDTO> roles = names
            .stream()
            .filter(name -> !isKeycloakDefaultRealmRole(name))
            .map(n -> catalog.getOrDefault(n, fallbackRole(n)))
            .toList();
        String targetPrincipal = null;
        try {
            targetPrincipal = resolveUsername(id, null, adminToken);
        } catch (Exception ex) {
            LOG.debug("Failed to resolve username for id {}: {}", id, ex.getMessage());
        }
        LinkedHashMap<String, KeycloakRoleDTO> merged = new LinkedHashMap<>();
        for (KeycloakRoleDTO role : roles) {
            String key = normalizeRoleKey(role != null ? role.getName() : null);
            if (key != null) {
                merged.putIfAbsent(key, role);
            }
        }
        if (StringUtils.hasText(targetPrincipal)) {
            List<String> aggregated = adminUserService.aggregateRealmRoles(targetPrincipal, names);
            for (String roleName : aggregated) {
                if (!StringUtils.hasText(roleName) || isKeycloakDefaultRealmRole(roleName)) {
                    continue;
                }
                String key = normalizeRoleKey(roleName);
                if (key == null || merged.containsKey(key)) {
                    continue;
                }
                KeycloakRoleDTO dto = resolveRoleDto(catalog, roleName);
                merged.put(key, dto);
            }
        }
        List<KeycloakRoleDTO> resultRoles = new ArrayList<>(merged.values());
        String displayTarget = StringUtils.hasText(targetPrincipal) ? targetPrincipal : id;
        Map<String, Object> payload = new HashMap<>();
        payload.put("roleCount", resultRoles.size());
        payload.put("userId", id);
        if (StringUtils.hasText(targetPrincipal)) {
            payload.put("username", targetPrincipal);
        }
        if (!isAuditSuppressed()) {
        }
        return ResponseEntity.ok(resultRoles);
    }

    private KeycloakRoleDTO fallbackRole(String name) {
        KeycloakRoleDTO dto = new KeycloakRoleDTO();
        dto.setName(name);
        return dto;
    }

    private KeycloakRoleDTO resolveRoleDto(Map<String, KeycloakRoleDTO> catalog, String roleName) {
        if (catalog == null) {
            return fallbackRole(roleName);
        }
        KeycloakRoleDTO direct = catalog.get(roleName);
        if (direct != null) {
            return direct;
        }
        String canonical = canonicalRoleValue(roleName);
        if (canonical != null) {
            KeycloakRoleDTO candidate = catalog.get(canonical);
            if (candidate != null) {
                return candidate;
            }
            return fallbackRole(canonical);
        }
        return fallbackRole(roleName);
    }

    private String normalizeRoleKey(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return null;
        }
        String base = stripRolePrefix(roleName);
        if (!StringUtils.hasText(base)) {
            return null;
        }
        return base.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private String canonicalRoleValue(String roleName) {
        String base = stripRolePrefix(roleName);
        if (!StringUtils.hasText(base)) {
            return null;
        }
        return "ROLE_" + base.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String stripRolePrefix(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return roleName;
        }
        String text = roleName.trim();
        if (text.startsWith("ROLE_") || text.startsWith("ROLE-")) {
            return text.substring(5);
        }
        if (text.startsWith("ROLE")) {
            return text.substring(4);
        }
        return text;
    }

    private boolean isKeycloakDefaultRealmRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if ("offline_access".equals(lower) || "uma_authorization".equals(lower)) {
            return true;
        }
        return lower.startsWith("default-roles-");
    }

    private java.util.Optional<String> findGroupPathByOrgId(com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO node, String orgId) {
        if (node == null) return java.util.Optional.empty();
        try {
            if (node.getAttributes() != null) {
                java.util.List<String> orgIds = node.getAttributes().get("dts_org_id");
                if (orgIds != null) {
                    for (String v : orgIds) {
                        if (v != null && v.equals(orgId)) {
                            return java.util.Optional.ofNullable(node.getPath());
                        }
                    }
                }
            }
            if (node.getSubGroups() != null) {
                for (var child : node.getSubGroups()) {
                    var m = findGroupPathByOrgId(child, orgId);
                    if (m.isPresent()) return m;
                }
            }
        } catch (Exception ignored) {}
        return java.util.Optional.empty();
    }

    @PostMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignRoles(
        @PathVariable String id,
        @RequestBody List<KeycloakRoleDTO> roles,
        HttpServletRequest request
    ) {
        try {
            List<String> names = roles == null ? List.of() : roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
            LOG.info("FE payload(assignRoles): id={}, names={}", id, names);
        } catch (Exception ignored) {}
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        List<String> roleNames = roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
        String actor = currentUser();
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitGrantRoles(
            username,
            roleNames,
            id,
            actor,
            clientIp(request)
        );
        String changeRequestRef = changeRequestRef(approval);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("roles", roleNames);
        if (changeRequestRef != null) {
            auditDetail.put("changeRequestRef", changeRequestRef);
        }
        String assignSummary = "提交角色分配审批：" + username + (roleNames.isEmpty() ? "" : " -> " + String.join(",", roleNames));
        recordUserActionV2(
            actor,
            ButtonCodes.USER_ASSIGN_ROLES,
            AuditResultStatus.SUCCESS,
            id,
            username,
            approval,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/" + id + "/roles",
            "POST",
            assignSummary
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "角色分配请求已提交，等待审批"
        )));
    }

    @DeleteMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeRoles(
        @PathVariable String id,
        @RequestBody List<KeycloakRoleDTO> roles,
        HttpServletRequest request
    ) {
        try {
            List<String> names = roles == null ? List.of() : roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
            LOG.info("FE payload(removeRoles): id={}, names={}", id, names);
        } catch (Exception ignored) {}
        String username = resolveUsername(id, null, adminAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        List<String> roleNames = roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
        String actor = currentUser();
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitRevokeRoles(
            username,
            roleNames,
            id,
            actor,
            clientIp(request)
        );
        String changeRequestRef = changeRequestRef(approval);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("roles", roleNames);
        auditDetail.put("operation", "revoke");
        if (changeRequestRef != null) {
            auditDetail.put("changeRequestRef", changeRequestRef);
        }
        String revokeSummary = "提交角色回收审批：" + username + (roleNames.isEmpty() ? "" : " -> " + String.join(",", roleNames));
        recordUserActionV2(
            actor,
            ButtonCodes.USER_REMOVE_ROLES,
            AuditResultStatus.SUCCESS,
            id,
            username,
            approval,
            new LinkedHashMap<>(auditDetail),
            request,
            "/api/keycloak/users/" + id + "/roles",
            "DELETE",
            revokeSummary
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "角色移除请求已提交，等待审批"
        )));
    }

    // ---- Roles ----
    @GetMapping("/keycloak/platform/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> listRolesForPlatform(HttpServletRequest request) {
        // Return realm roles via admin service; no triad token required (permitted in security)
        var list = adminUserService.listRealmRoles();
        String actor = currentUser();
        Map<String, Object> detail = Map.of("count", list.size());
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_PLATFORM_LIST,
            AuditResultStatus.SUCCESS,
            null,
            new LinkedHashMap<>(detail),
            request,
            "/api/keycloak/platform/roles",
            "GET",
            "查看平台角色列表（共 " + list.size() + " 个）",
            true
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> listRoles(HttpServletRequest request) {
        var list = stores.listRoles();
        String actor = currentUser();
        Map<String, Object> detail = Map.of("count", list.size());
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_LIST,
            AuditResultStatus.SUCCESS,
            null,
            new LinkedHashMap<>(detail),
            request,
            "/api/keycloak/roles",
            "GET",
            "查看角色列表（共 " + list.size() + " 个）",
            true
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/roles/{name}")
    public ResponseEntity<?> getRole(@PathVariable String name, HttpServletRequest request) {
        KeycloakRoleDTO role = stores.roles.get(name);
        String actor = currentUser();
        if (role == null) {
            Map<String, Object> detail = Map.of("error", "NOT_FOUND");
            recordRoleActionV2(
                actor,
                ButtonCodes.ROLE_VIEW,
                AuditResultStatus.FAILED,
                name,
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/roles/" + name,
                "GET",
                "查看角色失败：" + name,
                false
            );
            return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        }
        Map<String, Object> detail = Map.of("permissions", role.getComposite() != null && role.getComposite());
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_VIEW,
            AuditResultStatus.SUCCESS,
            role.getName(),
            new LinkedHashMap<>(detail),
            request,
            "/api/keycloak/roles/" + name,
            "GET",
            "查看角色：" + role.getName(),
            false
        );
        return ResponseEntity.ok(role);
    }

    @GetMapping("/keycloak/roles/{name}/users")
    public ResponseEntity<List<Map<String, Object>>> getRoleUsers(@PathVariable String name, HttpServletRequest request) {
        String token = adminAccessToken();
        List<com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO> users = keycloakAdminClient.listUsersByRealmRole(name, token);
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (var u : users) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("fullName", u.getFullName());
            results.add(m);
        }
        String actor = currentUser();
        Map<String, Object> detail = Map.of("users", results.size());
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_USERS_VIEW,
            AuditResultStatus.SUCCESS,
            name,
            new LinkedHashMap<>(detail),
            request,
            "/api/keycloak/roles/" + name + "/users",
            "GET",
            "查看角色关联用户：" + name,
            false
        );
        return ResponseEntity.ok(results);
    }

    @PostMapping("/keycloak/roles")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> createRole(@RequestBody KeycloakRoleDTO payload, HttpServletRequest request) {
        String actor = currentUser();
        if (!StringUtils.hasText(payload.getName())) {
            Map<String, Object> detail = Map.of("error", "角色名称不能为空");
            recordRoleActionV2(
                actor,
                ButtonCodes.ROLE_CREATE,
                AuditResultStatus.FAILED,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/roles",
                "POST",
                "新增角色失败：名称为空",
                true
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        }
        String trimmedName = payload.getName().trim();
        payload.setName(trimmedName);
        KeycloakRoleDTO existing = stores.roles.get(trimmedName);
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        if (existing != null) {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("name", existing.getName());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", saved.getName());
            recordRoleActionV2(
                actor,
                ButtonCodes.ROLE_UPDATE,
                AuditResultStatus.SUCCESS,
                saved.getName(),
                detail,
                request,
                "/api/keycloak/roles",
                "POST",
                "修改角色：" + saved.getName(),
                false
            );
        } else {
            Map<String, Object> detail = Map.of("name", saved.getName());
            recordRoleActionV2(
                actor,
                ButtonCodes.ROLE_CREATE,
                AuditResultStatus.SUCCESS,
                saved.getName(),
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/roles",
                "POST",
                "新增角色：" + saved.getName(),
                false
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> updateRole(@PathVariable String name, @RequestBody KeycloakRoleDTO payload, HttpServletRequest request) {
        payload.setName(Optional.ofNullable(payload.getName()).orElse(name));
        Map<String, Object> before = new LinkedHashMap<>();
        KeycloakRoleDTO existing = stores.roles.get(name);
        if (existing != null) {
            before.put("name", existing.getName());
        }
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("before", before);
        success.put("after", saved.getName());
        String actor = currentUser();
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_UPDATE,
            AuditResultStatus.SUCCESS,
            saved.getName(),
            new LinkedHashMap<>(success),
            request,
            "/api/keycloak/roles/" + name,
            "PUT",
            "修改角色：" + saved.getName(),
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @DeleteMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable String name, HttpServletRequest request) {
        String actor = currentUser();
        boolean removed = stores.deleteRole(name);
        if (!removed) {
            Map<String, Object> detail = Map.of("error", "NOT_FOUND");
            recordRoleActionV2(
                actor,
                ButtonCodes.ROLE_DELETE,
                AuditResultStatus.FAILED,
                name,
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/roles/" + name,
                "DELETE",
                "删除角色失败：" + name,
                false
            );
            return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        }
        recordRoleActionV2(
            actor,
            ButtonCodes.ROLE_DELETE,
            AuditResultStatus.SUCCESS,
            name,
            Map.of(),
            request,
            "/api/keycloak/roles/" + name,
            "DELETE",
            "删除角色：" + name,
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- UserProfile (mock) ----
    @GetMapping("/keycloak/userprofile/config")
    public ResponseEntity<Map<String, Object>> userProfileConfig() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("attributes", List.of());
        cfg.put("groups", List.of());
        cfg.put("unmanagedAttributePolicy", "ENABLED");
        return ResponseEntity.ok(cfg);
    }

    @PutMapping("/keycloak/userprofile/config")
    public ResponseEntity<ApiResponse<Void>> updateUserProfileConfig(@RequestBody Map<String, Object> cfg) {
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/keycloak/userprofile/configured")
    public ResponseEntity<Map<String, Object>> userProfileConfigured() {
        return ResponseEntity.ok(Map.of("configured", Boolean.FALSE));
    }

    @GetMapping("/keycloak/userprofile/attributes")
    public ResponseEntity<List<String>> userProfileAttributeNames() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/keycloak/userprofile/test")
    public ResponseEntity<Map<String, Object>> userProfileTest() {
        return ResponseEntity.ok(Map.of("configured", false, "message", "ok", "attributeCount", 0));
    }

    // ---- Localization ----
    @GetMapping("/keycloak/localization/zh-CN")
    public ResponseEntity<Map<String, Object>> keycloakZhCN() {
        Map<String, Object> t = new HashMap<>();
        t.put("userManagement", Map.of("title", "用户管理"));
        t.put("roleManagement", Map.of("title", "角色管理"));
        t.put("groupManagement", Map.of("title", "组管理"));
        t.put("commonActions", Map.of("save", "保存", "cancel", "取消", "delete", "删除"));
        t.put("statusMessages", Map.of("success", "成功", "error", "错误"));
        t.put("formLabels", Map.of("username", "用户名", "email", "邮箱"));
        t.put("pagination", Map.of("prev", "上一页", "next", "下一页"));
        return ResponseEntity.ok(t);
    }

    // ---- Groups ----
    @GetMapping("/keycloak/groups")
    public ResponseEntity<List<KeycloakGroupDTO>> listGroups(HttpServletRequest request) {
        var list = new ArrayList<>(stores.groups.values());
        String actor = currentUser();
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_LIST,
            AuditResultStatus.SUCCESS,
            null,
            null,
            null,
            Map.of("count", list.size()),
            request,
            "/api/keycloak/groups",
            "GET",
            "查看用户组列表（共 " + list.size() + " 个）",
            true
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/groups/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id, HttpServletRequest request) {
        String actor = currentUser();
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) {
            recordGroupActionV2(
                actor,
                ButtonCodes.GROUP_VIEW,
                AuditResultStatus.FAILED,
                id,
                null,
                null,
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/keycloak/groups/" + id,
                "GET",
                "查看用户组失败：" + id,
                true
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        Map<String, Object> detail = Map.of("attributes", g.getAttributes() == null ? 0 : g.getAttributes().size());
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_VIEW,
            AuditResultStatus.SUCCESS,
            id,
            g.getName(),
            null,
            detail,
            request,
            "/api/keycloak/groups/" + id,
            "GET",
            "查看用户组详情：" + Optional.ofNullable(g.getName()).orElse(id),
            false
        );
        return ResponseEntity.ok(g);
    }

    @GetMapping("/keycloak/groups/{id}/members")
    public ResponseEntity<List<String>> groupMembers(@PathVariable String id, HttpServletRequest request) {
        // In-memory placeholder: no persisted memberships; return empty
        String actor = currentUser();
        Map<String, Object> detail = Map.of("members", 0);
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_MEMBERS_VIEW,
            AuditResultStatus.SUCCESS,
            id,
            resolveGroupName(id),
            null,
            detail,
            request,
            "/api/keycloak/groups/" + id + "/members",
            "GET",
            "查看用户组成员：" + id,
            false
        );
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/keycloak/groups")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> createGroup(@RequestBody KeycloakGroupDTO payload, HttpServletRequest request) {
        String actor = currentAuditableLogin();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("name", payload.getName());
        if (!StringUtils.hasText(payload.getName())) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "组名称不能为空");
            recordGroupActionV2(
                actor,
                ButtonCodes.GROUP_CREATE,
                AuditResultStatus.FAILED,
                null,
                null,
                null,
                failure,
                request,
                "/api/keycloak/groups",
                "POST",
                "新增用户组失败：名称不能为空",
                true
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("组名称不能为空"));
        }
        payload.setId(UUID.randomUUID().toString());
        if (payload.getPath() == null) payload.setPath("/" + payload.getName());
        stores.groups.put(payload.getId(), payload);
        Map<String, Object> success = new LinkedHashMap<>(auditDetail);
        success.put("groupId", payload.getId());
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_CREATE,
            AuditResultStatus.SUCCESS,
            payload.getId(),
            payload.getName(),
            null,
            success,
            request,
            "/api/keycloak/groups",
            "POST",
            "新增用户组：" + payload.getName(),
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PutMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> updateGroup(@PathVariable String id, @RequestBody KeycloakGroupDTO patch, HttpServletRequest request) {
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) {
            recordGroupActionV2(
                currentUser(),
                ButtonCodes.GROUP_UPDATE,
                AuditResultStatus.FAILED,
                id,
                null,
                null,
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/keycloak/groups/" + id,
                "PUT",
                "修改用户组失败：" + id,
                true
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", g.getName());
        Map<String, Object> change = new LinkedHashMap<>();
        if (patch.getName() != null) {
            g.setName(patch.getName());
            change.put("name", patch.getName());
        }
        if (patch.getAttributes() != null && !patch.getAttributes().isEmpty()) {
            g.setAttributes(patch.getAttributes());
            change.put("attributes", patch.getAttributes().size());
        }
        Map<String, Object> detail = Map.of("before", before, "after", change);
        recordGroupActionV2(
            currentUser(),
            ButtonCodes.GROUP_UPDATE,
            AuditResultStatus.SUCCESS,
            id,
            g.getName(),
            null,
            detail,
            request,
            "/api/keycloak/groups/" + id,
            "PUT",
            "修改用户组：" + g.getName(),
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(g));
    }

    @DeleteMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String id, HttpServletRequest request) {
        String actor = currentAuditableLogin();
        KeycloakGroupDTO removed = stores.groups.remove(id);
        if (removed == null) {
            recordGroupActionV2(
                actor,
                ButtonCodes.GROUP_DELETE,
                AuditResultStatus.FAILED,
                id,
                null,
                null,
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/keycloak/groups/" + id,
                "DELETE",
                "删除用户组失败：" + id,
                true
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_DELETE,
            AuditResultStatus.SUCCESS,
            id,
            removed.getName(),
            null,
            Map.of("name", removed.getName()),
            request,
            "/api/keycloak/groups/" + id,
            "DELETE",
            "删除用户组：" + removed.getName(),
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> addMember(@PathVariable String id, @PathVariable String userId, HttpServletRequest request) {
        String actor = currentUser();
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_ADD_MEMBER,
            AuditResultStatus.SUCCESS,
            id,
            resolveGroupName(id),
            List.of(new AuditActionRequest.AuditTarget("keycloak_user", userId, userId)),
            Map.of("userId", userId, "operation", "add"),
            request,
            "/api/keycloak/groups/" + id + "/members/" + userId,
            "POST",
            "将用户加入用户组：" + userId + " -> " + id,
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable String id, @PathVariable String userId, HttpServletRequest request) {
        String actor = currentUser();
        recordGroupActionV2(
            actor,
            ButtonCodes.GROUP_REMOVE_MEMBER,
            AuditResultStatus.SUCCESS,
            id,
            resolveGroupName(id),
            List.of(new AuditActionRequest.AuditTarget("keycloak_user", userId, userId)),
            Map.of("userId", userId, "operation", "remove"),
            request,
            "/api/keycloak/groups/" + id + "/members/" + userId,
            "DELETE",
            "从用户组移除用户：" + userId + " -> " + id,
            false
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/keycloak/groups/user/{userId}")
    public ResponseEntity<List<KeycloakGroupDTO>> groupsByUser(@PathVariable String userId, HttpServletRequest request) {
        // Placeholder: return empty
        String actor = currentUser();
        Map<String, Object> detail = Map.of("targetUserId", userId, "membership", 0);
        if (!isAuditSuppressed()) {
            recordUserActionV2(
                actor,
                ButtonCodes.USER_GROUP_MEMBERSHIPS_VIEW,
                AuditResultStatus.SUCCESS,
                userId,
                userId,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/keycloak/groups/user/" + userId,
                "GET",
                "查看用户所属用户组：" + userId
            );
        }
        return ResponseEntity.ok(List.of());
    }

    // ---- Approvals ----
    @GetMapping("/approval-requests")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals(HttpServletRequest request) {
        List<ApprovalDTOs.ApprovalRequest> list = adminUserService.listApprovals();
        String actor = currentUser();
        Map<String, Object> detail = Map.of("count", list.size());
        if (!isAuditSuppressed()) {
            recordApprovalActionV2(
                actor,
                ButtonCodes.APPROVAL_LIST,
                AuditResultStatus.SUCCESS,
                null,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/approval-requests",
                "GET",
                "查看审批请求列表（共 " + list.size() + " 个）",
                true
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/approval-requests/{id}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> getApproval(@PathVariable long id, HttpServletRequest request) {
        Optional<ApprovalDTOs.ApprovalRequestDetail> detailOpt = adminUserService.findApprovalDetail(id);
        String actor = currentUser();
        if (detailOpt.isEmpty()) {
            if (!isAuditSuppressed()) {
                Map<String, Object> failure = Map.of("error", "NOT_FOUND");
                recordApprovalActionV2(
                    actor,
                    ButtonCodes.APPROVAL_VIEW,
                    AuditResultStatus.FAILED,
                    id,
                    changeRequestRefFrom(id),
                    new LinkedHashMap<>(failure),
                    request,
                    "/api/approval-requests/" + id,
                    "GET",
                    "查看审批请求失败：" + changeRequestRefFrom(id),
                    true
                );
            }
            return ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在"));
        }
        ApprovalDTOs.ApprovalRequestDetail detail = detailOpt.orElse(null);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("status", detail.status);
        if (!isAuditSuppressed()) {
        }
        String ref = changeRequestRefFrom(id);
        if (!isAuditSuppressed()) {
            recordApprovalActionV2(
                actor,
                ButtonCodes.APPROVAL_VIEW,
                AuditResultStatus.SUCCESS,
                id,
                ref,
                new LinkedHashMap<>(auditDetail),
                request,
                "/api/approval-requests/" + id,
                "GET",
                "查看审批请求：" + (ref != null ? ref : id),
                false
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @GetMapping("/keycloak/approvals")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals2(HttpServletRequest request) {
        return listApprovals(request);
    }

    @PostMapping("/keycloak/approvals/{id}/{action}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> actApproval(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody(required = false) ApprovalDTOs.ApprovalActionRequest body,
        HttpServletRequest request
    ) {
        String normalized = action == null ? "" : action.trim().toLowerCase();
        String approver = Optional
            .ofNullable(body)
            .map(b -> b.approver)
            .filter(val -> val != null && !val.isBlank())
            .orElse(currentUser());
        String note = Optional.ofNullable(body).map(b -> b.note).orElse(null);
        String actor = currentAuditableLogin();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("action", normalized);
        auditDetail.put("approver", approver);
        auditDetail.put("note", note);
        String clientIp = request != null
            ? IpAddressUtils.resolveClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            )
            : null;
        try {
            switch (normalized) {
                case "approve": {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.approve(
                        id,
                        approver,
                        note,
                        currentAccessToken(),
                        clientIp
                    );
                    return ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "reject": {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.reject(id, approver, note, clientIp);
                    return ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "process": {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.delay(id, approver, note, clientIp);
                    return ResponseEntity.ok(ApiResponse.ok(detail));
                }
                default: {
                    Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
                    failure.put("error", "UNSUPPORTED_ACTION");
                    recordApprovalActionV2(
                        actor,
                        ButtonCodes.APPROVAL_VIEW,
                        AuditResultStatus.FAILED,
                        id,
                        changeRequestRefFrom(id),
                        failure,
                        request,
                        Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/approvals/" + id + "/" + normalized),
                        request != null ? request.getMethod() : "POST",
                        "执行审批操作失败：" + normalized,
                        false
                    );
                    return ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
                }
            }
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordApprovalActionV2(
                actor,
                ButtonCodes.APPROVAL_VIEW,
                AuditResultStatus.FAILED,
                id,
                changeRequestRefFrom(id),
                failure,
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/approvals/" + id + "/" + normalized),
                request != null ? request.getMethod() : "POST",
                "执行审批操作失败：" + normalized,
                false
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordApprovalActionV2(
                actor,
                ButtonCodes.APPROVAL_VIEW,
                AuditResultStatus.FAILED,
                id,
                changeRequestRefFrom(id),
                failure,
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/approvals/" + id + "/" + normalized),
                request != null ? request.getMethod() : "POST",
                "执行审批操作失败：" + normalized,
                false
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/keycloak/user-sync/process/{id}")
    public ResponseEntity<ApiResponse<Void>> syncApproved(@PathVariable long id) {
        ApprovalDTOs.ApprovalRequestDetail current;
        try {
            current = adminUserService
                .findApprovalDetail(id)
                .orElseThrow(() -> new IllegalArgumentException("审批请求不存在"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(ApiResponse.error(ex.getMessage()));
        }
        if (!"PENDING".equalsIgnoreCase(current.status)) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        String token = currentAccessToken();
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("缺少授权令牌，无法执行审批操作"));
        }
        adminUserService.approve(id, currentUser(), "同步触发", token, null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- Auth endpoints (minimal, for UI bootstrap; real auth via Keycloak SSO) ----
    @PostMapping("/keycloak/auth/platform/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> platformLogin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            recordAuthActionV2(
                StringUtils.hasText(username) ? username : "unknown",
                ButtonCodes.AUTH_PLATFORM_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", "MISSING_CREDENTIALS"),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/platform/login"),
                request != null ? request.getMethod() : "POST",
                "业务端登录失败（缺少凭证）"
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        try {
            // 平台端登录：不做 IP 白名单校验（普通用户通过 PKI 登录；此处保持原平台直登逻辑）
            // Note: Do NOT enforce triad-only here. This endpoint serves the business platform audience.
            KeycloakAuthService.LoginResult loginResult = keycloakAuthService.login(username, password);

            // Gate login: built-ins may always log in; others must exist and be enabled in admin snapshot
            String uname = username.toLowerCase();
            boolean isProtected = PROTECTED_USERNAMES.contains(uname);
            if (!isProtected) {
                boolean allowed = adminUserService
                    .findSnapshotByUsername(username)
                    .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::isEnabled)
                    .orElse(false);
                if (!allowed) {
                    recordAuthActionV2(
                        username,
                        ButtonCodes.AUTH_PLATFORM_LOGIN,
                        AuditResultStatus.FAILED,
                        Map.of("error", "NOT_ENABLED"),
                        request,
                        Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/platform/login"),
                        request != null ? request.getMethod() : "POST",
                        "业务端登录失败（未启用）"
                    );
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("用户尚未审批启用，请联系授权管理员"));
                }
            }
            Map<String, Object> data = new HashMap<>();
            // Enrich roles with DB assignments so platform can work without Keycloak client roles
            Map<String, Object> userOut = new HashMap<>(loginResult.user());
            @SuppressWarnings("unchecked")
            List<String> kcRoles = (List<String>) userOut.getOrDefault("roles", java.util.Collections.emptyList());
            java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
            for (String r : kcRoles) {
                String normalized = normalizeAuthority(r);
                if (normalized != null) {
                    roles.add(normalized);
                }
            }
            String principal = java.util.Objects.toString(userOut.getOrDefault("preferred_username", userOut.get("username")), username);
            try {
                if (principal != null && !principal.isBlank()) {
                    String lookup = principal.trim();
                    for (AdminRoleAssignment assignment : roleAssignRepo.findByUsernameIgnoreCase(lookup)) {
                        String authority = normalizeAuthority(assignment.getRole());
                        if (authority != null) {
                            roles.add(authority);
                        }
                    }
                    for (AdminRoleMember member : roleMemberRepo.findByUsernameIgnoreCase(lookup)) {
                        String authority = normalizeAuthority(member.getRole());
                        if (authority != null) {
                            roles.add(authority);
                        }
                    }
                }
            } catch (Exception ex) {
                if (LOG.isDebugEnabled()) LOG.debug("DB role enrichment failed for {}: {}", principal, ex.getMessage());
            }
            userOut.put("roles", java.util.List.copyOf(roles));
            data.put("user", userOut);
            data.put("accessToken", loginResult.tokens().accessToken());
            if (loginResult.tokens().refreshToken() != null) data.put("refreshToken", loginResult.tokens().refreshToken());
            if (loginResult.tokens().idToken() != null) data.put("idToken", loginResult.tokens().idToken());
            if (loginResult.tokens().tokenType() != null) data.put("tokenType", loginResult.tokens().tokenType());
            if (loginResult.tokens().scope() != null) data.put("scope", loginResult.tokens().scope());
            if (loginResult.tokens().sessionState() != null) data.put("sessionState", loginResult.tokens().sessionState());
            if (loginResult.tokens().expiresIn() != null) data.put("expiresIn", loginResult.tokens().expiresIn());
            if (loginResult.tokens().refreshExpiresIn() != null) data.put("refreshExpiresIn", loginResult.tokens().refreshExpiresIn());
            String sessionUser = (principal != null && !principal.isBlank()) ? principal : username;
            boolean takeover = registerSession(sessionUser, loginResult.tokens());
            if (takeover) {
                data.put("sessionTakeover", Boolean.TRUE);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("roles", roles.size());
            recordAuthActionV2(
                sessionUser,
                ButtonCodes.AUTH_PLATFORM_LOGIN,
                AuditResultStatus.SUCCESS,
                detail,
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/platform/login"),
                request != null ? request.getMethod() : "POST",
                "业务端登录成功"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            recordAuthActionV2(
                username,
                ButtonCodes.AUTH_PLATFORM_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", ex.getMessage()),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/platform/login"),
                request != null ? request.getMethod() : "POST",
                "业务端登录失败（凭证错误）"
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            recordAuthActionV2(
                username,
                ButtonCodes.AUTH_PLATFORM_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", message),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/platform/login"),
                request != null ? request.getMethod() : "POST",
                "业务端登录失败"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }
    @PostMapping("/keycloak/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            if (!username.isBlank()) {
            }
            recordAuthActionV2(
                StringUtils.hasText(username) ? username : "unknown",
                ButtonCodes.AUTH_ADMIN_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", "MISSING_CREDENTIALS"),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                request != null ? request.getMethod() : "POST",
                "系统端登录失败（缺少凭证）"
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        try {
            // IP allowlist (single IP) for triad accounts only (sysadmin/authadmin/auditadmin)
            String ipCheckError = enforceTriadIpAllowlist(username, request);
            if (ipCheckError != null) {
                recordAuthActionV2(
                    username,
                    ButtonCodes.AUTH_ADMIN_LOGIN,
                    AuditResultStatus.FAILED,
                    Map.of("error", ipCheckError),
                    request,
                    Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                    request != null ? request.getMethod() : "POST",
                    "系统端登录失败（IP 白名单不匹配）"
                );
                String msg = "MISSING_ALLOWED_IP".equals(ipCheckError) ? "该账号未绑定登录 IP" : "登录 IP 不在白名单";
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(msg));
            }
            KeycloakAuthService.LoginResult loginResult = keycloakAuthService.login(username, password);
            // Enforce triad-only for admin console
            Map<String, Object> user = loginResult.user();
            @SuppressWarnings("unchecked")
            java.util.List<String> roles = user == null ? java.util.List.of() : (java.util.List<String>) user.getOrDefault("roles", java.util.List.of());
            java.util.Set<String> set = new java.util.HashSet<>();
            for (String r : roles) if (r != null) set.add(r.toUpperCase());
            boolean triad = set.contains("ROLE_SYS_ADMIN") || set.contains("SYS_ADMIN") ||
                            set.contains("ROLE_AUTH_ADMIN") || set.contains("AUTH_ADMIN") ||
                            set.contains("ROLE_SECURITY_AUDITOR") || set.contains("SECURITY_AUDITOR") ||
                            set.contains("ROLE_AUDITOR_ADMIN") || set.contains("AUDITOR_ADMIN") ||
                            set.contains("ROLE_AUDIT_ADMIN") || set.contains("AUDIT_ADMIN") ||
                            set.contains("ROLE_AUDITADMIN") || set.contains("AUDITADMIN");
            if (!triad) {
                recordAuthActionV2(
                    username,
                    ButtonCodes.AUTH_ADMIN_LOGIN,
                    AuditResultStatus.FAILED,
                    Map.of("error", "ROLE_NOT_ALLOWED"),
                    request,
                    Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                    request != null ? request.getMethod() : "POST",
                    "系统端登录失败（角色不允许）"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("仅系统管理角色可登录系统端"));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("user", user);
            data.put("accessToken", loginResult.tokens().accessToken());
            data.put("refreshToken", loginResult.tokens().refreshToken());
            if (loginResult.tokens().idToken() != null) {
                data.put("idToken", loginResult.tokens().idToken());
            }
            if (loginResult.tokens().tokenType() != null) {
                data.put("tokenType", loginResult.tokens().tokenType());
            }
            if (loginResult.tokens().scope() != null) {
                data.put("scope", loginResult.tokens().scope());
            }
            if (loginResult.tokens().sessionState() != null) {
                data.put("sessionState", loginResult.tokens().sessionState());
            }
            if (loginResult.tokens().expiresIn() != null) {
                data.put("expiresIn", loginResult.tokens().expiresIn());
            }
            if (loginResult.tokens().refreshExpiresIn() != null) {
                data.put("refreshExpiresIn", loginResult.tokens().refreshExpiresIn());
            }
            String sessionUser = Optional.ofNullable(user)
                .map(u -> u.getOrDefault("preferred_username", u.get("username")))
                .filter(v -> v instanceof String)
                .map(v -> (String) v)
                .filter(StringUtils::hasText)
                .orElse(username);
            boolean takeover = registerSession(sessionUser, loginResult.tokens());
            if (takeover) {
                data.put("sessionTakeover", Boolean.TRUE);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("roles", set.size());
            recordAuthActionV2(
                sessionUser,
                ButtonCodes.AUTH_ADMIN_LOGIN,
                AuditResultStatus.SUCCESS,
                detail,
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                request != null ? request.getMethod() : "POST",
                "系统端登录成功"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            recordAuthActionV2(
                username,
                ButtonCodes.AUTH_ADMIN_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", ex.getMessage() == null ? "BAD_CREDENTIALS" : ex.getMessage()),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                request != null ? request.getMethod() : "POST",
                "系统端登录失败（凭证错误）"
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            recordAuthActionV2(
                username,
                ButtonCodes.AUTH_ADMIN_LOGIN,
                AuditResultStatus.FAILED,
                Map.of("error", message),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/login"),
                request != null ? request.getMethod() : "POST",
                "系统端登录失败"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    @PostMapping("/keycloak/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String refreshToken = Optional.ofNullable(body).map(b -> b.get("refreshToken")).orElse(null);
        String actor = resolveActorForLogout(body, refreshToken);
        String reason = Optional.ofNullable(body).map(b -> b.get("reason")).map(String::trim).orElse(null);
        boolean autoTimeout = reason != null && reason.equalsIgnoreCase("IDLE_TIMEOUT");
        String authHeader = request == null ? null : request.getHeader("Authorization");
        String bearer = authHeader == null ? null : authHeader.trim();
        if (bearer != null && bearer.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            bearer = bearer.substring("Bearer ".length()).trim();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("audience", "admin");
        detail.put("hasRefreshToken", StringUtils.hasText(refreshToken));
        detail.put("trigger", autoTimeout ? "IDLE_TIMEOUT" : "MANUAL");
        if (StringUtils.hasText(reason)) {
            detail.put("reason", reason);
        }
        String fallbackUri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/logout");
        String fallbackMethod = request != null ? request.getMethod() : "POST";
        AdminSessionCloseReason closeReason = autoTimeout ? AdminSessionCloseReason.EXPIRED : AdminSessionCloseReason.LOGOUT;
        adminSessionRegistry.invalidateByAccessToken(bearer, closeReason);
        adminSessionRegistry.invalidateByRefreshToken(refreshToken, closeReason);
        try {
            if (refreshToken != null && !refreshToken.isBlank()) {
                try {
                    keycloakAuthService.logout(refreshToken);
                } catch (Exception ex) {
                    // attempt best-effort revoke as a fallback
                    try { keycloakAuthService.revokeRefreshToken(refreshToken); } catch (Exception ignore) {}
                    throw ex;
                }
            }
            recordAuthActionV2(
                actor,
                ButtonCodes.AUTH_ADMIN_LOGOUT,
                AuditResultStatus.SUCCESS,
                detail,
                request,
                fallbackUri,
                fallbackMethod,
                autoTimeout ? "系统会话超时自动退出" : "系统端登出成功"
            );
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception ex) {
            // From client perspective, even if Keycloak-side logout fails, we clear local session; return 200 to avoid blocking UX.
            Map<String, Object> failureDetail = new LinkedHashMap<>(detail);
            failureDetail.put("error", Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("LOGOUT_ERROR"));
            recordAuthActionV2(
                actor,
                ButtonCodes.AUTH_ADMIN_LOGOUT,
                AuditResultStatus.FAILED,
                failureDetail,
                request,
                fallbackUri,
                fallbackMethod,
                autoTimeout ? "系统会话超时自动退出失败" : "系统端登出失败"
            );
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
    }

    @PostMapping("/keycloak/auth/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String refreshToken = Optional.ofNullable(body).map(b -> b.get("refreshToken")).orElse(null);
        if (refreshToken == null || refreshToken.isBlank()) {
            recordAuthActionV2(
                resolveActorForRefresh(refreshToken),
                ButtonCodes.AUTH_ADMIN_REFRESH,
                AuditResultStatus.FAILED,
                Map.of("error", "MISSING_REFRESH_TOKEN"),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/refresh"),
                request != null ? request.getMethod() : "POST",
                "刷新系统端令牌失败（缺少 refreshToken）"
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("缺少 refreshToken"));
        }
        try {
            KeycloakAuthService.TokenResponse tokens = keycloakAuthService.refreshTokens(refreshToken);
            adminSessionRegistry.refreshSession(
                refreshToken,
                tokens.sessionState(),
                tokens.accessToken(),
                tokens.refreshToken(),
                resolveExpiry(tokens.expiresIn()),
                resolveExpiry(tokens.refreshExpiresIn())
            );
            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", tokens.accessToken());
            if (tokens.refreshToken() != null) {
                data.put("refreshToken", tokens.refreshToken());
            }
            if (tokens.expiresIn() != null) {
                data.put("expiresIn", tokens.expiresIn());
            }
            if (tokens.refreshExpiresIn() != null) {
                data.put("refreshExpiresIn", tokens.refreshExpiresIn());
            }
            if (tokens.tokenType() != null) {
                data.put("tokenType", tokens.tokenType());
            }
            if (tokens.scope() != null) {
                data.put("scope", tokens.scope());
            }
            if (tokens.sessionState() != null) {
                data.put("sessionState", tokens.sessionState());
            }
            recordAuthActionV2(
                resolveActorForRefresh(refreshToken),
                ButtonCodes.AUTH_ADMIN_REFRESH,
                AuditResultStatus.SUCCESS,
                Map.of("refreshTokenRotated", tokens.refreshToken() != null && !tokens.refreshToken().isBlank()),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/refresh"),
                request != null ? request.getMethod() : "POST",
                "刷新系统端令牌成功"
            );
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            recordAuthActionV2(
                resolveActorForRefresh(refreshToken),
                ButtonCodes.AUTH_ADMIN_REFRESH,
                AuditResultStatus.FAILED,
                Map.of("error", ex.getMessage()),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/refresh"),
                request != null ? request.getMethod() : "POST",
                "刷新系统端令牌失败（凭证错误）"
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("刷新失败，请稍后再试");
            recordAuthActionV2(
                resolveActorForRefresh(refreshToken),
                ButtonCodes.AUTH_ADMIN_REFRESH,
                AuditResultStatus.FAILED,
                Map.of("error", message),
                request,
                Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/keycloak/auth/refresh"),
                request != null ? request.getMethod() : "POST",
                "刷新系统端令牌失败"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    // ---- PKI login (placeholder, disabled by default) ----
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.yuzhi.dts.admin.config.PkiAuthProperties pkiProps;

    public record PkiLoginPayload(
        String challengeId,
        String nonce,               // challenge nonce (plain string)
        String plain,               // legacy: plain text (fallback)
        String originDataB64,       // Base64 of data actually signed
        String p7Signature,         // legacy signature field
        String signDataB64,         // preferred signature field
        String certB64,             // legacy certificate field
        String certContentB64,      // preferred certificate field
        String mode,                // agent|gateway (informational)
        String username,            // optional: mock username for allow-mock
        String devId,
        String appName,
        String conName,
        String signType,
        String dupCertB64            // optional: PM-BD duplicated cert payload
    ) {}

    @PostMapping("/keycloak/auth/pki-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pkiLogin(
        @RequestBody(required = false) PkiLoginPayload payload,
        jakarta.servlet.http.HttpServletRequest request
    ) {
        boolean enabled = pkiProps != null && pkiProps.isEnabled();
        if (!enabled) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(ApiResponse.error("PKI 登录未启用"));
        }

        try {
            if (payload == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("请求体不能为空"));
            }

            String clientIp = IpAddressUtils.resolveClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );
            if (!org.springframework.util.StringUtils.hasText(clientIp)) {
                clientIp = request.getRemoteAddr();
            }
            String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");

            com.yuzhi.dts.admin.service.pki.PkiChallengeService challengeService = this.ctx.getBean(com.yuzhi.dts.admin.service.pki.PkiChallengeService.class);
            String challengeId = Optional.ofNullable(payload.challengeId()).map(String::trim).orElse("");
            String nonce = Optional.ofNullable(payload.nonce()).map(String::trim).orElse("");
            if (!org.springframework.util.StringUtils.hasText(challengeId) || !org.springframework.util.StringUtils.hasText(nonce)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("缺少 challenge 或 nonce"));
            }
            String originDataB64 = Optional.ofNullable(payload.originDataB64()).map(String::trim).orElse("");
            if (!org.springframework.util.StringUtils.hasText(originDataB64)) {
                String legacyPlain = payload.plain();
                if (org.springframework.util.StringUtils.hasText(legacyPlain)) {
                    originDataB64 = Base64.getEncoder().encodeToString(legacyPlain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            if (!org.springframework.util.StringUtils.hasText(originDataB64)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("缺少签名原文"));
            }

            var challenge = challengeService.validateAndConsume(challengeId, nonce, originDataB64);
            if (challenge == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("挑战校验失败或已过期"));
            }

            String signatureB64 = Optional.ofNullable(payload.signDataB64()).map(String::trim).orElse("");
            if (!org.springframework.util.StringUtils.hasText(signatureB64)) {
                signatureB64 = Optional.ofNullable(payload.p7Signature()).map(String::trim).orElse("");
            }
            if (!org.springframework.util.StringUtils.hasText(signatureB64)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("缺少签名数据"));
            }

            String certB64 = Optional.ofNullable(payload.certContentB64()).map(String::trim).orElse("");
            if (!org.springframework.util.StringUtils.hasText(certB64)) {
                certB64 = Optional.ofNullable(payload.certB64()).map(String::trim).orElse("");
            }
            if (!org.springframework.util.StringUtils.hasText(certB64)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("缺少证书数据"));
            }

            String devId = Optional.ofNullable(payload.devId()).map(String::trim).orElse("");
            String appName = Optional.ofNullable(payload.appName()).map(String::trim).orElse("");
            String conName = Optional.ofNullable(payload.conName()).map(String::trim).orElse("");
            String signType = Optional.ofNullable(payload.signType()).map(String::trim).orElse("");
            String dupCertB64 = Optional.ofNullable(payload.dupCertB64()).map(String::trim).orElse("");
            byte[] originBytes = null;
            String originHash = null;
            try {
                originBytes = Base64.getDecoder().decode(originDataB64.replaceAll("\\s+", ""));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                originHash = Base64.getEncoder().encodeToString(digest.digest(originBytes));
            } catch (IllegalArgumentException | java.security.NoSuchAlgorithmException ignore) {}

            if (LOG.isDebugEnabled()) {
                int signatureLength = signatureB64.length();
                int certLength = certB64.length();
                int dupCertLength = dupCertB64.isEmpty() ? 0 : dupCertB64.length();
                int originB64Length = originDataB64.length();
                int originLength = originBytes == null ? -1 : originBytes.length;
                String originPreview = originBytes == null ? "<decode-failed>" : new String(originBytes, StandardCharsets.UTF_8);
                if (originPreview.length() > 128) {
                    originPreview = originPreview.substring(0, 128) + "...";
                }
                LOG.debug(
                    "[pki-login] verify request stats challengeId={} originLen={} originB64Len={} signatureB64Len={} certB64Len={} dupCertB64Len={} devId={} appName={} conName={} signType={}",
                    challengeId,
                    originLength,
                    originB64Length,
                    signatureLength,
                    certLength,
                    dupCertLength,
                    devId,
                    appName,
                    conName,
                    signType
                );
                LOG.debug("[pki-login] origin preview='{}'", originPreview);
                LOG.debug("[pki-login] originB64='{}'", originDataB64);
                LOG.debug("[pki-login] signatureB64='{}'", signatureB64);
                LOG.debug("[pki-login] certB64='{}'", certB64);
            }

            // Verify signature via gateway/vendor
            com.yuzhi.dts.admin.service.pki.PkiVerificationService verifier = this.ctx.getBean(com.yuzhi.dts.admin.service.pki.PkiVerificationService.class);
            var vr = verifier.verifyPkcs7(originDataB64, signatureB64, certB64);
            if (!vr.ok()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(vr.message()));
            }

            Map<String, Object> data = new HashMap<>();
            if (vr.identity() != null) data.putAll(vr.identity());
            data.put("mode", payload.mode == null ? (pkiProps.getMode() == null ? "gateway" : pkiProps.getMode()) : payload.mode);
            if (org.springframework.util.StringUtils.hasText(signType)) data.put("signType", signType);
            if (org.springframework.util.StringUtils.hasText(devId)) data.put("devId", devId);
            if (org.springframework.util.StringUtils.hasText(appName)) data.put("appName", appName);
            if (org.springframework.util.StringUtils.hasText(conName)) data.put("conName", conName);
            if (org.springframework.util.StringUtils.hasText(dupCertB64)) data.put("dupCertB64", dupCertB64);

            Object subjectDnObj = vr.identity() == null ? null : vr.identity().get("subjectDn");
            String subjectDn = subjectDnObj == null ? null : subjectDnObj.toString();
            Object issuerDnObj = vr.identity() == null ? null : vr.identity().get("issuerDn");
            String issuerDn = issuerDnObj == null ? null : issuerDnObj.toString();
            String certCn = extractFromDn(subjectDn, "CN");
            String issuerCn = extractFromDn(issuerDn, "CN");
            if (org.springframework.util.StringUtils.hasText(certCn)) data.put("certCn", certCn);
            if (org.springframework.util.StringUtils.hasText(issuerCn)) data.put("issuerCn", issuerCn);
            Object serialObj = vr.identity() == null ? null : vr.identity().get("serialNumber");
            if (serialObj != null) data.put("serialNumber", serialObj.toString());
            Object signedAtObj = vr.identity() == null ? null : vr.identity().get("signedAt");
            if (signedAtObj != null) data.put("signedAt", signedAtObj);

            // Resolve username mapping: prefer dev-provided username when allow-mock enabled; otherwise derive from certificate subject DN
            String mappedUsername = null;
            boolean allowMock = Boolean.TRUE.equals(pkiProps.isAllowMock());
            if (allowMock && payload.username != null && !payload.username.isBlank()) {
                mappedUsername = payload.username.trim();
            }
            if ((mappedUsername == null || mappedUsername.isBlank()) && org.springframework.util.StringUtils.hasText(subjectDn)) {
                mappedUsername = extractFromDn(subjectDn, "UID", "CN");
            }
            if (mappedUsername == null || mappedUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("无法从证书映射用户名"));
            }

            // Determine login audience: platform (default) or admin (admin console)
            String loginAudience = Optional
                .ofNullable(request.getHeader("X-Login-Audience"))
                .orElse(Optional.ofNullable(request.getParameter("audience")).orElse("platform"));
            boolean adminAudience = "admin".equalsIgnoreCase(loginAudience);

            Map<String, Object> auditContext = new LinkedHashMap<>();
            auditContext.put("audience", loginAudience);
            auditContext.put("mode", "pki");
            auditContext.put("challengeId", challengeId);
            auditContext.put("nonce", nonce);
            auditContext.put("clientIp", clientIp);
            auditContext.put("userAgent", userAgent);
            if (challenge != null) {
                auditContext.put("challengeIssuedAt", challenge.ts);
                auditContext.put("challengeExpiresAt", challenge.exp);
                auditContext.put("challengeIp", challenge.ip);
                auditContext.put("challengeUa", challenge.ua);
            }
            if (originHash != null) auditContext.put("originHash", originHash);
            if (org.springframework.util.StringUtils.hasText(signType)) auditContext.put("signType", signType);
            if (org.springframework.util.StringUtils.hasText(devId)) auditContext.put("devId", devId);
            if (org.springframework.util.StringUtils.hasText(appName)) auditContext.put("appName", appName);
            if (org.springframework.util.StringUtils.hasText(conName)) auditContext.put("conName", conName);
            if (org.springframework.util.StringUtils.hasText(certCn)) auditContext.put("certCn", certCn);
            if (org.springframework.util.StringUtils.hasText(issuerCn)) auditContext.put("issuerCn", issuerCn);
            if (serialObj != null) auditContext.put("serialNumber", serialObj.toString());
            if (signedAtObj != null) auditContext.put("signedAt", signedAtObj);
            if (org.springframework.util.StringUtils.hasText(dupCertB64)) auditContext.put("dupCertB64", dupCertB64);

            // Platform audience: forbid triad built-ins via PKI and ensure user snapshot enabled
            String normalized = mappedUsername.toLowerCase();
            if (!adminAudience) {
                if (normalized.equals("sysadmin") || normalized.equals("authadmin") || normalized.equals("auditadmin")) {
                    Map<String, Object> failAudit = new HashMap<>(auditContext);
                    failAudit.put("error", "forbidden_role");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("系统管理角色用户不能登录业务平台"));
                }
                boolean isProtected = PROTECTED_USERNAMES.contains(normalized);
                if (!isProtected) {
                    boolean allowed = adminUserService
                        .findSnapshotByUsername(mappedUsername)
                        .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::isEnabled)
                        .orElse(false);
                    if (!allowed) {
                        Map<String, Object> failAudit = new HashMap<>(auditContext);
                        failAudit.put("error", "not_approved");
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("用户尚未审批启用，请联系授权管理员"));
                    }
                }
            }

            // Issue tokens via Keycloak Token Exchange (impersonation)
            KeycloakAuthService.LoginResult loginResult;
            try {
                loginResult = keycloakAuthService.loginByTokenExchange(mappedUsername);
            } catch (org.springframework.security.authentication.BadCredentialsException | IllegalStateException ex) {
                // Some Keycloak setups require requested_subject=userId instead of username.
                // Fallback: resolve userId via admin REST using service account, then retry.
                try {
                    if (managementClientId != null && !managementClientId.isBlank()) {
                        var sa = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
                        var kcUser = keycloakAdminClient.findByUsername(mappedUsername, sa.accessToken()).orElse(null);
                        String userId = kcUser != null ? kcUser.getId() : null;
                        if (userId != null && !userId.isBlank()) {
                            loginResult = keycloakAuthService.loginByTokenExchange(userId);
                        } else {
                            throw ex;
                        }
                    } else {
                        throw ex;
                    }
                } catch (Exception inner) {
                    // Preserve the original error message for client troubleshooting
                    throw ex;
                }
            }

            Map<String, Object> userOut = new HashMap<>(loginResult.user());
            @SuppressWarnings("unchecked")
            List<String> kcRoles = (List<String>) userOut.getOrDefault("roles", java.util.Collections.emptyList());
            java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
            for (String r : kcRoles) {
                String normalizedRole = normalizeAuthority(r);
                if (normalizedRole != null) {
                    roles.add(normalizedRole);
                }
            }
            String principal = java.util.Objects.toString(userOut.getOrDefault("preferred_username", userOut.get("username")), mappedUsername);
            try {
                if (principal != null && !principal.isBlank()) {
                    for (AdminRoleAssignment assignment : roleAssignRepo.findByUsernameIgnoreCase(principal)) {
                        String authority = normalizeAuthority(assignment.getRole());
                        if (authority != null) {
                            roles.add(authority);
                        }
                    }
                    for (AdminRoleMember member : roleMemberRepo.findByUsernameIgnoreCase(principal)) {
                        String authority = normalizeAuthority(member.getRole());
                        if (authority != null) {
                            roles.add(authority);
                        }
                    }
                }
            } catch (Exception ex) {
                if (LOG.isDebugEnabled()) LOG.debug("DB role enrichment failed for {}: {}", principal, ex.getMessage());
            }
            userOut.put("roles", java.util.List.copyOf(roles));

            // Admin audience: require allowed admin roles
            if (adminAudience) {
                java.util.Set<String> allowedRoles = parseRoleList(this.allowedRolesForAdminLogin);
                boolean hasAllowed = false;
                for (String r : roles) {
                    if (allowedRoles.contains(r)) { hasAllowed = true; break; }
                }
                if (!hasAllowed) {
                    Map<String, Object> failAudit = new HashMap<>(auditContext);
                    failAudit.put("error", "role_not_allowed");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("仅系统管理角色可登录系统端"));
                }
            }

            data.put("user", userOut);
            data.put("accessToken", loginResult.tokens().accessToken());
            if (loginResult.tokens().refreshToken() != null) data.put("refreshToken", loginResult.tokens().refreshToken());
            if (loginResult.tokens().idToken() != null) data.put("idToken", loginResult.tokens().idToken());
            if (loginResult.tokens().tokenType() != null) data.put("tokenType", loginResult.tokens().tokenType());
            if (loginResult.tokens().scope() != null) data.put("scope", loginResult.tokens().scope());
            if (loginResult.tokens().sessionState() != null) data.put("sessionState", loginResult.tokens().sessionState());
            if (loginResult.tokens().expiresIn() != null) data.put("expiresIn", loginResult.tokens().expiresIn());
            if (loginResult.tokens().refreshExpiresIn() != null) data.put("refreshExpiresIn", loginResult.tokens().refreshExpiresIn());
            String sessionUser = (principal != null && !principal.isBlank()) ? principal : mappedUsername;
            boolean takeover = registerSession(sessionUser, loginResult.tokens());
            if (takeover) {
                data.put("sessionTakeover", Boolean.TRUE);
            }

            Map<String, Object> successAudit = new HashMap<>(auditContext);
            successAudit.put("principal", principal);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("PKI 登录失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext ctx;

    @GetMapping("/keycloak/auth/pki-challenge")
    public ResponseEntity<ApiResponse<PkiChallengeView>> pkiChallenge(jakarta.servlet.http.HttpServletRequest request) {
        boolean enabled = pkiProps != null && pkiProps.isEnabled();
        if (!enabled) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(ApiResponse.error("PKI 登录未启用"));
        }
        com.yuzhi.dts.admin.service.pki.PkiChallengeService svc = this.ctx.getBean(com.yuzhi.dts.admin.service.pki.PkiChallengeService.class);
        String ip = IpAddressUtils.resolveClientIp(
            request.getHeader("X-Forwarded-For"),
            request.getHeader("X-Real-IP"),
            request.getRemoteAddr()
        );
        if (!org.springframework.util.StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        String ua = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
        var c = svc.issue("dts-admin", ip, ua, java.time.Duration.ofMinutes(10));
        var view = new PkiChallengeView(c.id, c.nonce, c.aud, c.ts.toEpochMilli(), c.exp.toEpochMilli());
        return ResponseEntity.ok(ApiResponse.ok(view));
    }

    private String normalizeAuthority(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String upper = role.trim().toUpperCase(Locale.ROOT);
        if (!upper.startsWith("ROLE_")) {
            upper = "ROLE_" + upper;
        }
        return upper;
    }

    private static String extractFromDn(String dn, String... keys) {
        if (dn == null || dn.isBlank() || keys == null || keys.length == 0) return "";
        try {
            String[] parts = dn.split(",");
            for (String key : keys) {
                String k = key.toUpperCase();
                for (String p : parts) {
                    String s = p.trim();
                    int idx = s.indexOf('=');
                    if (idx > 0) {
                        String name = s.substring(0, idx).trim().toUpperCase();
                        if (name.equals(k)) {
                            return s.substring(idx + 1).trim();
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return "";
    }

    /**
     * Enforce single-IP allowlist for triad accounts (sysadmin/authadmin/auditadmin) on password login endpoints.
     * Returns null if OK/not applicable; otherwise returns an error code:
     *  - MISSING_ALLOWED_IP: triad user has no allowed-ip attribute
     *  - IP_NOT_ALLOWED: client IP does not match allowed-ip
     *  - IP_CHECK_ERROR: internal error while resolving or fetching attributes
     */
    private String enforceTriadIpAllowlist(String username, jakarta.servlet.http.HttpServletRequest request) {
        try {
            if (!org.springframework.util.StringUtils.hasText(username)) return null;
            if (!ipAllowEnabled) return null;
            String uname = username.toLowerCase(java.util.Locale.ROOT).trim();
            if (!triadUsernamesConfigured().contains(uname)) return null; // only configured triad accounts

            String clientIp = IpAddressUtils.resolveClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );
            if (!org.springframework.util.StringUtils.hasText(clientIp)) clientIp = request.getRemoteAddr();
            if (clientIp == null) clientIp = "";
            clientIp = stripPort(clientIp.trim());

            // Fetch allowed-ip from Keycloak via admin client
            String adminToken = adminAccessToken();
            java.util.Optional<com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO> userOpt =
                keycloakAdminClient.findByUsername(username, adminToken);
            com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO user = userOpt.orElse(null);
            if (user == null || user.getAttributes() == null) {
                return "MISSING_ALLOWED_IP";
            }
            java.util.List<String> list = user.getAttributes().get("allowed-ip");
            String allowed = null;
            if (list != null) {
                for (String v : list) { if (v != null && !v.trim().isEmpty()) { allowed = v.trim(); break; } }
            }
            if (!org.springframework.util.StringUtils.hasText(allowed)) {
                return "MISSING_ALLOWED_IP";
            }
            allowed = stripPort(allowed);
            if (allowed.equals(clientIp)) return null;
            return "IP_NOT_ALLOWED";
        } catch (Exception ex) {
            return "IP_CHECK_ERROR";
        }
    }

    private static String stripPort(String ip) {
        if (ip == null) return "";
        String s = ip.trim();
        // If looks like IPv4 with trailing :port, drop port
        int idx = s.lastIndexOf(':');
        if (idx > 0 && s.indexOf('.') >= 0) {
            String candidate = s.substring(0, idx);
            String portPart = s.substring(idx + 1);
            boolean digits = true;
            for (int i = 0; i < portPart.length(); i++) { char c = portPart.charAt(i); if (c < '0' || c > '9') { digits = false; break; } }
            if (digits) return candidate;
        }
        return s;
    }

    private java.util.Set<String> triadUsernamesConfigured() {
        if (this.triadConfigured != null) return this.triadConfigured;
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        String raw = this.ipAllowTriadCsv == null ? "" : this.ipAllowTriadCsv;
        for (String part : raw.split(",")) {
            if (part != null) {
                String v = part.trim().toLowerCase(java.util.Locale.ROOT);
                if (!v.isEmpty()) out.add(v);
            }
        }
        if (out.isEmpty()) {
            out.add("sysadmin"); out.add("authadmin"); out.add("auditadmin");
        }
        this.triadConfigured = java.util.Collections.unmodifiableSet(out);
        return this.triadConfigured;
    }
}
