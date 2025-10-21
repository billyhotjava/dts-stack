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
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.security.session.SessionControlService;
import com.yuzhi.dts.admin.security.session.SessionKeyGenerator;
import com.yuzhi.dts.admin.web.rest.dto.PkiChallengeView;
import com.yuzhi.dts.common.net.IpAddressUtils;
import com.yuzhi.dts.common.audit.AuditStage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "keycloak")
public class KeycloakApiResource {

    private final InMemoryStores stores;
    private final AdminAuditService auditService;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminUserService adminUserService;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final SessionControlService sessionControlService;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String managementClientId;

    @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}")
    private String managementClientSecret;

    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";
    private static final Set<String> PROTECTED_USERNAMES = Set.of("sysadmin",  "authadmin", "auditadmin", "opadmin");

    public KeycloakApiResource(
        InMemoryStores stores,
        AdminAuditService auditService,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient,
        AdminUserService adminUserService,
        AdminRoleAssignmentRepository roleAssignRepo,
        SessionControlService sessionControlService
    ) {
        this.stores = stores;
        this.auditService = auditService;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.adminUserService = adminUserService;
        this.roleAssignRepo = roleAssignRepo;
        this.sessionControlService = sessionControlService;
    }

    // ---- Users ----
    @GetMapping("/keycloak/users")
    public ResponseEntity<List<KeycloakUserDTO>> listUsers(@RequestParam(defaultValue = "0") int first, @RequestParam(defaultValue = "100") int max) {
        String token = adminAccessToken();
        List<KeycloakUserDTO> list = filterProtectedUsers(keycloakAdminClient.listUsers(first, max, token));
        boolean fromCache = false;
        if (!list.isEmpty()) {
            list.forEach(this::cacheUser);
        } else {
            list = filterProtectedUsers(stores.listUsers(first, max));
            fromCache = true;
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("count", list.size());
        auditDetail.put("source", fromCache ? "cache" : "keycloak");
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            "list",
            auditDetail
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/search")
    public ResponseEntity<List<KeycloakUserDTO>> searchUsers(@RequestParam String username) {
        String q = username == null ? "" : username.toLowerCase();
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
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("query", q);
        auditDetail.put("count", list.size());
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            "search",
            auditDetail
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
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
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", u.getUsername());
        auditDetail.put("status", Boolean.TRUE.equals(u.getEnabled()) ? "ENABLED" : "DISABLED");
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            id,
            auditDetail
        );
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
            auditService.recordAction(actor, "ADMIN_USER_CREATE", AuditStage.FAIL, "pending", failure);
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名不能为空"));
        }
        auditService.recordAction(actor, "ADMIN_USER_CREATE", AuditStage.BEGIN, requestedUsername, auditDetail);
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
            Map<String, Object> success = new LinkedHashMap<>(auditDetail);
            success.put("requestId", approval.id);
            success.put("status", approval.status);
            auditService.recordAction(actor, "ADMIN_USER_CREATE", AuditStage.SUCCESS, command.getUsername(), success);
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
            auditService.recordAction(actor, "ADMIN_USER_CREATE", AuditStage.FAIL, requestedUsername, failure);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_USER_CREATE", AuditStage.FAIL, requestedUsername, failure);
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
            auditService.recordAction(actor, "ADMIN_USER_UPDATE", AuditStage.FAIL, id, Map.of("error", "NOT_FOUND"));
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
        auditService.recordAction(actor, "ADMIN_USER_UPDATE", AuditStage.BEGIN, username, auditDetail);
        try {
            ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitUpdate(
                username,
                command,
                actor,
                clientIp(request)
            );
            Map<String, Object> success = new LinkedHashMap<>(auditDetail);
            success.put("requestId", approval.id);
            success.put("status", approval.status);
            auditService.recordAction(actor, "ADMIN_USER_UPDATE", AuditStage.SUCCESS, username, success);
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
            auditService.recordAction(actor, "ADMIN_USER_UPDATE", AuditStage.FAIL, username, failure);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_USER_UPDATE", AuditStage.FAIL, username, failure);
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
        auditService.recordAction(actor, "ADMIN_USER_RESET_PASSWORD", AuditStage.BEGIN, username, auditDetail);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitResetPassword(
            username,
            password,
            temporary,
            actor,
            clientIp(request)
        );
        Map<String, Object> success = new LinkedHashMap<>(auditDetail);
        success.put("requestId", approval.id);
        success.put("status", approval.status);
        auditService.recordAction(actor, "ADMIN_USER_RESET_PASSWORD", AuditStage.SUCCESS, username, success);
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
        String actionCode = enabled ? "ADMIN_USER_ENABLE" : "ADMIN_USER_DISABLE";
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditDetail.put("enabled", enabled);
        auditService.recordAction(actor, actionCode, AuditStage.BEGIN, username, auditDetail);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetEnabled(
            username,
            enabled,
            actor,
            clientIp(request)
        );
        Map<String, Object> success = new LinkedHashMap<>(auditDetail);
        success.put("requestId", approval.id);
        success.put("status", approval.status);
        auditService.recordAction(actor, actionCode, AuditStage.SUCCESS, username, success);
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
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetPersonLevel(
            username,
            level,
            currentUser(),
            clientIp(request),
            null
        );
        auditService.recordAction(
            currentUser(),
            "ADMIN_USER_UPDATE",
            AuditStage.SUCCESS,
            username,
            Map.of("requestId", approval.id, "level", level)
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAbacClaims(@PathVariable String id) {
        KeycloakUserDTO u = stores.findUserById(id);
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String person = u.getAttributes().getOrDefault("person_level", List.of("NON_SECRET")).stream().findFirst().orElse("NON_SECRET");
        List<String> levels = List.of();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("personLevel", person, "dataLevelCount", levels.size())
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
        command.setRealmRoles(normalizeList(payload.getRealmRoles()));
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
            throw new IllegalStateException("获取 Keycloak 管理客户端访问令牌失败: " + ex.getMessage(), ex);
        }
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }

    private void registerSession(String username, KeycloakAuthService.TokenResponse tokens) {
        if (tokens == null) {
            return;
        }
        String sessionState = tokens.sessionState();
        String sessionKey = SessionKeyGenerator.fromToken(sessionState, tokens.accessToken());
        if (sessionKey == null) {
            return;
        }
        String actor = sanitizeActor(username);
        if (!StringUtils.hasText(actor)) {
            actor = username;
        }
        if (!StringUtils.hasText(actor)) {
            actor = currentUser();
        }
        sessionControlService.register(actor, sessionState, sessionKey, Instant.now());
    }

    private String resolveActorForLogout(Map<String, String> body, String refreshToken) {
        String actor = sanitizeActor(SecurityUtils.getCurrentUserLogin().orElse(null));
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
        String header = request.getHeader("X-Forwarded-For");
        String xfip = StringUtils.hasText(header) ? header.split(",")[0].trim() : null;
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        String resolved = IpAddressUtils.resolveClientIp(xfip, realIp, remote);
        return resolved != null ? resolved : "unknown";
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
        List<KeycloakRoleDTO> roles = names.stream().map(n -> catalog.getOrDefault(n, fallbackRole(n))).toList();
        String targetPrincipal = null;
        try {
            targetPrincipal = resolveUsername(id, null, adminToken);
        } catch (Exception ex) {
            LOG.debug("Failed to resolve username for id {}: {}", id, ex.getMessage());
        }
        String displayTarget = StringUtils.hasText(targetPrincipal) ? targetPrincipal : id;
        Map<String, Object> payload = new HashMap<>();
        payload.put("roleCount", roles.size());
        payload.put("userId", id);
        if (StringUtils.hasText(targetPrincipal)) {
            payload.put("username", targetPrincipal);
        }
        auditService.recordAction(
            currentUser(),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            displayTarget,
            payload
        );
        return ResponseEntity.ok(roles);
    }

    private KeycloakRoleDTO fallbackRole(String name) {
        KeycloakRoleDTO dto = new KeycloakRoleDTO();
        dto.setName(name);
        return dto;
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
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitGrantRoles(
            username,
            roleNames,
            id,
            currentUser(),
            clientIp(request)
        );
        auditService.recordAction(
            currentUser(),
            "ADMIN_USER_ASSIGN_ROLE",
            AuditStage.SUCCESS,
            username,
            Map.of("requestId", approval.id, "roles", roleNames)
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
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitRevokeRoles(
            username,
            roleNames,
            id,
            currentUser(),
            clientIp(request)
        );
        auditService.recordAction(
            currentUser(),
            "ADMIN_USER_ASSIGN_ROLE",
            AuditStage.SUCCESS,
            username,
            Map.of("requestId", approval.id, "roles", roleNames, "operation", "revoke")
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
    public ResponseEntity<List<KeycloakRoleDTO>> listRolesForPlatform() {
        // Return realm roles via admin service; no triad token required (permitted in security)
        var list = adminUserService.listRealmRoles();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            "platform",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> listRoles() {
        var list = stores.listRoles();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            "list",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/roles/{name}")
    public ResponseEntity<?> getRole(@PathVariable String name) {
        KeycloakRoleDTO role = stores.roles.get(name);
        if (role == null) {
            auditService.recordAction(
                SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
                "ADMIN_ROLE_VIEW",
                AuditStage.FAIL,
                name,
                Map.of("error", "NOT_FOUND")
            );
            return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        }
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            name,
            Map.of("permissions", role.getComposite() != null && role.getComposite())
        );
        return ResponseEntity.ok(role);
    }

    @GetMapping("/keycloak/roles/{name}/users")
    public ResponseEntity<List<Map<String, Object>>> getRoleUsers(@PathVariable String name) {
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            name,
            Map.of("users", results.size())
        );
        return ResponseEntity.ok(results);
    }

    @PostMapping("/keycloak/roles")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> createRole(@RequestBody KeycloakRoleDTO payload) {
        if (!StringUtils.hasText(payload.getName())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        }
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> updateRole(@PathVariable String name, @RequestBody KeycloakRoleDTO payload) {
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
        auditService.recordAction(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ADMIN_ROLE_UPDATE", AuditStage.SUCCESS, saved.getName(), success);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @DeleteMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable String name) {
        boolean removed = stores.deleteRole(name);
        if (!removed) {
            return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- UserProfile (mock) ----
    @GetMapping("/keycloak/userprofile/config")
    public ResponseEntity<Map<String, Object>> userProfileConfig() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("attributes", List.of());
        cfg.put("groups", List.of());
        cfg.put("unmanagedAttributePolicy", "ENABLED");
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            "keycloak-userprofile",
            Map.of()
        );
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            "keycloak-localization",
            Map.of("locale", "zh-CN")
        );
        return ResponseEntity.ok(t);
    }

    // ---- Groups ----
    @GetMapping("/keycloak/groups")
    public ResponseEntity<List<KeycloakGroupDTO>> listGroups() {
        var list = new ArrayList<>(stores.groups.values());
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_GROUP_VIEW",
            AuditStage.SUCCESS,
            "list",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/groups/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id) {
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) {
            auditService.recordAction(
                SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
                "ADMIN_GROUP_VIEW",
                AuditStage.FAIL,
                id,
                Map.of("error", "NOT_FOUND")
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_GROUP_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("attributes", g.getAttributes() == null ? 0 : g.getAttributes().size())
        );
        return ResponseEntity.ok(g);
    }

    @GetMapping("/keycloak/groups/{id}/members")
    public ResponseEntity<List<String>> groupMembers(@PathVariable String id) {
        // In-memory placeholder: no persisted memberships; return empty
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_GROUP_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("members", 0)
        );
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/keycloak/groups")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> createGroup(@RequestBody KeycloakGroupDTO payload) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("name", payload.getName());
        auditService.recordAction(actor, "ADMIN_GROUP_CREATE", AuditStage.BEGIN, Optional.ofNullable(payload.getId()).orElse("pending"), auditDetail);
        if (!StringUtils.hasText(payload.getName())) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "组名称不能为空");
            auditService.recordAction(actor, "ADMIN_GROUP_CREATE", AuditStage.FAIL, "pending", failure);
            return ResponseEntity.badRequest().body(ApiResponse.error("组名称不能为空"));
        }
        payload.setId(UUID.randomUUID().toString());
        if (payload.getPath() == null) payload.setPath("/" + payload.getName());
        stores.groups.put(payload.getId(), payload);
        Map<String, Object> success = new LinkedHashMap<>(auditDetail);
        success.put("groupId", payload.getId());
        auditService.recordAction(actor, "ADMIN_GROUP_CREATE", AuditStage.SUCCESS, payload.getId(), success);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PutMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> updateGroup(@PathVariable String id, @RequestBody KeycloakGroupDTO patch) {
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) {
            auditService.recordAction(
                SecurityUtils.getCurrentUserLogin().orElse("unknown"),
                "ADMIN_GROUP_UPDATE",
                AuditStage.FAIL,
                id,
                Map.of("error", "NOT_FOUND")
            );
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", g.getName());
        Map<String, Object> request = new LinkedHashMap<>();
        if (patch.getName() != null) {
            g.setName(patch.getName());
            request.put("name", patch.getName());
        }
        if (patch.getAttributes() != null && !patch.getAttributes().isEmpty()) {
            g.setAttributes(patch.getAttributes());
            request.put("attributes", patch.getAttributes().size());
        }
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_GROUP_UPDATE",
            AuditStage.SUCCESS,
            id,
            Map.of("before", before, "after", request)
        );
        return ResponseEntity.ok(ApiResponse.ok(g));
    }

    @DeleteMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String id) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        auditService.recordAction(actor, "ADMIN_GROUP_DELETE", AuditStage.BEGIN, id, Map.of());
        KeycloakGroupDTO removed = stores.groups.remove(id);
        if (removed == null) {
            auditService.recordAction(actor, "ADMIN_GROUP_DELETE", AuditStage.FAIL, id, Map.of("error", "NOT_FOUND"));
            return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        }
        auditService.recordAction(actor, "ADMIN_GROUP_DELETE", AuditStage.SUCCESS, id, Map.of("name", removed.getName()));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> addMember(@PathVariable String id, @PathVariable String userId) {
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_GROUP_ASSIGN",
            AuditStage.SUCCESS,
            id,
            Map.of("userId", userId, "operation", "add")
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable String id, @PathVariable String userId) {
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_GROUP_ASSIGN",
            AuditStage.SUCCESS,
            id,
            Map.of("userId", userId, "operation", "remove")
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/keycloak/groups/user/{userId}")
    public ResponseEntity<List<KeycloakGroupDTO>> groupsByUser(@PathVariable String userId) {
        // Placeholder: return empty
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_GROUP_VIEW",
            AuditStage.SUCCESS,
            userId,
            Map.of("membership", 0)
        );
        return ResponseEntity.ok(List.of());
    }

    // ---- Approvals ----
    @GetMapping("/approval-requests")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals() {
        List<ApprovalDTOs.ApprovalRequest> list = adminUserService.listApprovals();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_APPROVAL_VIEW",
            AuditStage.SUCCESS,
            "list",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/approval-requests/{id}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> getApproval(@PathVariable long id) {
        Optional<ApprovalDTOs.ApprovalRequestDetail> detail = adminUserService.findApprovalDetail(id);
        return detail
            .map(found -> {
                auditService.recordAction(
                    SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
                    "ADMIN_APPROVAL_VIEW",
                    AuditStage.SUCCESS,
                    String.valueOf(id),
                    Map.of("status", found.status)
                );
                return ResponseEntity.ok(ApiResponse.ok(found));
            })
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在")));
    }

    @GetMapping("/keycloak/approvals")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals2() {
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_APPROVAL_VIEW",
            AuditStage.SUCCESS,
            "list-proxy",
            Map.of()
        );
        return listApprovals();
    }

    @PostMapping("/keycloak/approvals/{id}/{action}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> actApproval(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody(required = false) ApprovalDTOs.ApprovalActionRequest body
    ) {
        String normalized = action == null ? "" : action.trim().toLowerCase();
        String approver = Optional
            .ofNullable(body)
            .map(b -> b.approver)
            .filter(val -> val != null && !val.isBlank())
            .orElse(currentUser());
        String note = Optional.ofNullable(body).map(b -> b.note).orElse(null);
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("action", normalized);
        auditDetail.put("approver", approver);
        auditDetail.put("note", note);
        auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.BEGIN, String.valueOf(id), auditDetail);
        try {
            return switch (normalized) {
                case "approve" -> {
                    // Provide caller token for best effort; service falls back to service-account if needed
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.approve(id, approver, note, currentAccessToken());
                    // If only one item, record decision with item.id as resourceId for target_id precision
                    String resourceId = String.valueOf(id);
                    try {
                        var entityOpt = adminUserService.findApprovalEntity(id);
                        resourceId = entityOpt
                            .map(e -> e.getItems())
                            .filter(java.util.Objects::nonNull)
                            .filter(list -> list.size() == 1)
                            .map(list -> list.get(0))
                            .map(item -> item != null ? item.getId() : null)
                            .filter(java.util.Objects::nonNull)
                            .map(String::valueOf)
                            .orElse(resourceId);
                    } catch (Exception ignore) {}
                    auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.SUCCESS, resourceId, Map.of("result", "APPROVED"));
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "reject" -> {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.reject(id, approver, note);
                    auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.SUCCESS, String.valueOf(id), Map.of("result", "REJECTED"));
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "process" -> {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.delay(id, approver, note);
                    auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.SUCCESS, String.valueOf(id), Map.of("result", "DELAYED"));
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                default -> ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
            };
        } catch (IllegalArgumentException ex) {
            auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.FAIL, String.valueOf(id), Map.of("error", ex.getMessage()));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.FAIL, String.valueOf(id), Map.of("error", ex.getMessage()));
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
        adminUserService.approve(id, currentUser(), "同步触发", token);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- Auth endpoints (minimal, for UI bootstrap; real auth via Keycloak SSO) ----
    @PostMapping("/keycloak/auth/platform/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> platformLogin(@RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        try {
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
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("用户尚未审批启用，请联系授权管理员"));
                }
            }
            Map<String, Object> data = new HashMap<>();
            // Enrich roles with DB assignments so platform can work without Keycloak client roles
            Map<String, Object> userOut = new HashMap<>(loginResult.user());
            @SuppressWarnings("unchecked")
            List<String> kcRoles = (List<String>) userOut.getOrDefault("roles", java.util.Collections.emptyList());
            java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
            for (String r : kcRoles) if (r != null && !r.isBlank()) roles.add(r);
            String principal = java.util.Objects.toString(userOut.getOrDefault("preferred_username", userOut.get("username")), username);
            try {
                if (principal != null && !principal.isBlank()) {
                    for (AdminRoleAssignment a : roleAssignRepo.findByUsernameIgnoreCase(principal)) {
                        String role = a.getRole();
                        if (role != null && !role.isBlank()) roles.add(role.trim());
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
            registerSession(sessionUser, loginResult.tokens());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }
    @PostMapping("/keycloak/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            if (!username.isBlank()) {
                auditService.recordAction(
                    username,
                    "ADMIN_AUTH_LOGIN",
                    AuditStage.FAIL,
                    username,
                    Map.of("audience", "admin", "mode", "password", "error", "MISSING_CREDENTIALS")
                );
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        auditService.recordAction(
            username,
            "ADMIN_AUTH_LOGIN",
            AuditStage.BEGIN,
            username,
            Map.of("audience", "admin", "mode", "password")
        );
        try {
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
                auditService.recordAction(
                    username,
                    "ADMIN_AUTH_LOGIN",
                    AuditStage.FAIL,
                    username,
                    Map.of("audience", "admin", "mode", "password", "error", "ROLE_NOT_ALLOWED")
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
            registerSession(sessionUser, loginResult.tokens());
            auditService.recordAction(
                username,
                "ADMIN_AUTH_LOGIN",
                AuditStage.SUCCESS,
                username,
                Map.of("audience", "admin", "mode", "password")
            );
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            auditService.recordAction(
                username,
                "ADMIN_AUTH_LOGIN",
                AuditStage.FAIL,
                username,
                Map.of("audience", "admin", "mode", "password", "error", ex.getMessage() == null ? "BAD_CREDENTIALS" : ex.getMessage())
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            auditService.recordAction(
                username,
                "ADMIN_AUTH_LOGIN",
                AuditStage.FAIL,
                username,
                Map.of("audience", "admin", "mode", "password", "error", Optional.ofNullable(ex.getMessage()).orElse("UNKNOWN"))
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    @PostMapping("/keycloak/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String refreshToken = Optional.ofNullable(body).map(b -> b.get("refreshToken")).orElse(null);
        String sessionState = Optional.ofNullable(body).map(b -> b.get("sessionState")).orElse(null);
        String actor = resolveActorForLogout(body, refreshToken);
        String authHeader = request == null ? null : request.getHeader("Authorization");
        String bearer = authHeader == null ? null : authHeader.trim();
        if (bearer != null && bearer.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            bearer = bearer.substring("Bearer ".length()).trim();
        }
        String sessionKey = SessionKeyGenerator.fromToken(sessionState, bearer);
        if (sessionKey != null && !sessionKey.isBlank()) {
            sessionControlService.invalidate(sessionKey);
        }
        auditService.recordAction(
            actor,
            "ADMIN_AUTH_LOGOUT",
            AuditStage.BEGIN,
            actor,
            Map.of("audience", "admin")
        );
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
            auditService.recordAction(
                actor,
                "ADMIN_AUTH_LOGOUT",
                AuditStage.SUCCESS,
                actor,
                Map.of("audience", "admin")
            );
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception ex) {
            // From client perspective, even if Keycloak-side logout fails, we clear local session; return 200 to avoid blocking UX.
            auditService.recordAction(
                actor,
                "ADMIN_AUTH_LOGOUT",
                AuditStage.FAIL,
                actor,
                Map.of("audience", "admin", "error", Optional.ofNullable(ex.getMessage()).orElse("LOGOUT_ERROR"))
            );
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
    }

    @PostMapping("/keycloak/auth/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = Optional.ofNullable(body).map(b -> b.get("refreshToken")).orElse(null);
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("缺少 refreshToken"));
        }
        auditService.recordAction(currentUser(), "ADMIN_AUTH_REFRESH", AuditStage.BEGIN, "self", Map.of());
        try {
            KeycloakAuthService.TokenResponse tokens = keycloakAuthService.refreshTokens(refreshToken);
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
            registerSession(currentUser(), tokens);
            auditService.recordAction(currentUser(), "ADMIN_AUTH_REFRESH", AuditStage.SUCCESS, "self", Map.of());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            auditService.recordAction(currentUser(), "ADMIN_AUTH_REFRESH", AuditStage.FAIL, "self", Map.of("error", ex.getMessage()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("刷新失败，请稍后再试");
            auditService.recordAction(currentUser(), "ADMIN_AUTH_REFRESH", AuditStage.FAIL, "self", Map.of("error", message));
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

            String clientIp = Optional
                .ofNullable(request.getHeader("X-Forwarded-For"))
                .map(String::trim)
                .filter(org.springframework.util.StringUtils::hasText)
                .orElse(request.getRemoteAddr());
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

            Map<String, Object> auditContext = new LinkedHashMap<>();
            auditContext.put("audience", "platform");
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

            // Enforce: admin-only users must NOT log into platform via PKI
            String normalized = mappedUsername.toLowerCase();
            if (normalized.equals("sysadmin") || normalized.equals("authadmin") || normalized.equals("auditadmin")) {
                Map<String, Object> failAudit = new HashMap<>(auditContext);
                failAudit.put("error", "forbidden_role");
                auditService.recordAction(mappedUsername, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, mappedUsername, failAudit);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("系统管理角色用户不能登录业务平台"));
            }

            // Gate login: built-ins may always log in; others must exist and be enabled in admin snapshot
            boolean isProtected = PROTECTED_USERNAMES.contains(normalized);
            if (!isProtected) {
                boolean allowed = adminUserService
                    .findSnapshotByUsername(mappedUsername)
                    .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::isEnabled)
                    .orElse(false);
                if (!allowed) {
                    Map<String, Object> failAudit = new HashMap<>(auditContext);
                    failAudit.put("error", "not_approved");
                    auditService.recordAction(mappedUsername, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, mappedUsername, failAudit);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("用户尚未审批启用，请联系授权管理员"));
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
            for (String r : kcRoles) if (r != null && !r.isBlank()) roles.add(r);
            String principal = java.util.Objects.toString(userOut.getOrDefault("preferred_username", userOut.get("username")), mappedUsername);
            try {
                if (principal != null && !principal.isBlank()) {
                    for (AdminRoleAssignment a : roleAssignRepo.findByUsernameIgnoreCase(principal)) {
                        String role = a.getRole();
                        if (role != null && !role.isBlank()) roles.add(role.trim());
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
            String sessionUser = (principal != null && !principal.isBlank()) ? principal : mappedUsername;
            registerSession(sessionUser, loginResult.tokens());

            Map<String, Object> successAudit = new HashMap<>(auditContext);
            successAudit.put("principal", principal);
            auditService.recordAction(mappedUsername, "ADMIN_AUTH_LOGIN", AuditStage.SUCCESS, mappedUsername, successAudit);
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
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(request.getRemoteAddr());
        String ua = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
        var c = svc.issue("dts-admin", ip, ua, java.time.Duration.ofMinutes(10));
        var view = new PkiChallengeView(c.id, c.nonce, c.aud, c.ts.toEpochMilli(), c.exp.toEpochMilli());
        return ResponseEntity.ok(ApiResponse.ok(view));
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
}
