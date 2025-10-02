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
import com.yuzhi.dts.admin.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "keycloak")
public class KeycloakApiResource {

    private final InMemoryStores stores;
    private final AdminAuditService auditService;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminUserService adminUserService;

    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";
    private static final Map<String, String> DATA_LEVEL_ALIASES = Map.ofEntries(
        Map.entry("PUBLIC", "DATA_PUBLIC"),
        Map.entry("DATA_PUBLIC", "DATA_PUBLIC"),
        Map.entry("INTERNAL", "DATA_INTERNAL"),
        Map.entry("DATA_INTERNAL", "DATA_INTERNAL"),
        Map.entry("SECRET", "DATA_SECRET"),
        Map.entry("DATA_SECRET", "DATA_SECRET"),
        Map.entry("TOP_SECRET", "DATA_TOP_SECRET"),
        Map.entry("DATA_TOP_SECRET", "DATA_TOP_SECRET")
    );
    private static final Set<String> PROTECTED_USERNAMES = Set.of("sysadmin", "syadmin", "authadmin", "auditadmin", "opadmin");

    public KeycloakApiResource(
        InMemoryStores stores,
        AdminAuditService auditService,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient,
        AdminUserService adminUserService
    ) {
        this.stores = stores;
        this.auditService = auditService;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.adminUserService = adminUserService;
    }

    // ---- Users ----
    @GetMapping("/keycloak/users")
    public ResponseEntity<List<KeycloakUserDTO>> listUsers(@RequestParam(defaultValue = "0") int first, @RequestParam(defaultValue = "100") int max) {
        String token = currentAccessToken();
        List<KeycloakUserDTO> list = filterProtectedUsers(keycloakAdminClient.listUsers(first, max, token));
        if (list.isEmpty()) {
            list = filterProtectedUsers(stores.listUsers(first, max));
        } else {
            list.forEach(this::cacheUser);
        }
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USERS_LIST", "KC_USER", "list", "SUCCESS", null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/search")
    public ResponseEntity<List<KeycloakUserDTO>> searchUsers(@RequestParam String username) {
        String q = username == null ? "" : username.toLowerCase();
        List<KeycloakUserDTO> list = filterProtectedUsers(keycloakAdminClient
            .findByUsername(username, currentAccessToken())
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
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USERS_SEARCH", "KC_USER", q, "SUCCESS", null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        KeycloakUserDTO u = stores.findUserById(id);
        if (u == null) {
            u = keycloakAdminClient.findById(id, currentAccessToken()).orElse(null);
            if (u != null) {
                cacheUser(u);
            }
        }
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USER_DETAIL", "KC_USER", id, "SUCCESS", null);
        return ResponseEntity.ok(u);
    }

    @PostMapping("/keycloak/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@RequestBody KeycloakUserDTO payload, HttpServletRequest request) {
        if (payload.getUsername() == null || payload.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名不能为空"));
        }
        try {
            UserOperationRequest command = toOperationRequest(payload);
            ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitCreate(
                command,
                currentUser(),
                clientIp(request)
            );
            auditService.record(currentUser(), "USER_CREATE_REQUEST", "KC_USER", command.getUsername(), "SUCCESS", null);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "requestId",
                approval.id,
                "status",
                approval.status,
                "message",
                "操作已提交，等待审批"
            )));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(
        @PathVariable String id,
        @RequestBody KeycloakUserDTO patch,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, patch.getUsername(), currentAccessToken());
        if (username == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        }
        UserOperationRequest command = toOperationRequest(patch);
        command.setUsername(username);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitUpdate(
            username,
            command,
            currentUser(),
            clientIp(request)
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "requestId",
            approval.id,
            "status",
            approval.status,
            "message",
            "用户信息更新请求已提交，等待审批"
        )));
    }

    @DeleteMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteUser(@PathVariable String id, HttpServletRequest request) {
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("用户删除功能已禁用，请改用停用操作"));
    }

    @PostMapping("/keycloak/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String password = Objects.toString(body.get("password"), null);
        boolean temporary = Boolean.TRUE.equals(body.get("temporary"));
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("密码不能为空"));
        }
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitResetPassword(
            username,
            password,
            temporary,
            currentUser(),
            clientIp(request)
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
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Object val = body.get("enabled");
        boolean enabled = Boolean.TRUE.equals(val) || (val instanceof Boolean b && b);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetEnabled(
            username,
            enabled,
            currentUser(),
            clientIp(request)
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

    // ---- ABAC person_level + data_levels management (dev helper) ----
    @PutMapping("/keycloak/users/{id}/person-level")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setPersonLevel(
        @PathVariable String id,
        @RequestBody Map<String, String> body,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String level = String.valueOf(body.getOrDefault("person_level", "")).toUpperCase();
        if (!List.of("NON_SECRET", "GENERAL", "IMPORTANT", "CORE").contains(level)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的人员密级"));
        }
        List<String> levels = body.containsKey("data_levels")
            ? List.of(body.get("data_levels").split(","))
            : computeDataLevels(level);
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitSetPersonLevel(
            username,
            level,
            levels,
            currentUser(),
            clientIp(request),
            null
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
        List<String> levels = u.getAttributes().getOrDefault("data_levels", computeDataLevels(person));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_ABAC_CLAIMS", "KC_USER", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("person_level", person, "data_levels", levels)));
    }

    private static List<String> computeDataLevels(String personLevel) {
        return List.of(defaultDataLevel(personLevel));
    }

    private static String defaultDataLevel(String personLevel) {
        return switch (personLevel == null ? "" : personLevel) {
            case "CORE" -> "DATA_TOP_SECRET";
            case "IMPORTANT" -> "DATA_SECRET";
            case "GENERAL" -> "DATA_INTERNAL";
            case "NON_SECRET" -> "DATA_PUBLIC";
            default -> "DATA_PUBLIC";
        };
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
        List<String> dataLevels = resolveDataLevels(attributes, personLevel);
        command.setDataLevels(dataLevels);
        command.setReason(extractFirst(attributes, "reason", "approval_reason"));

        if (!attributes.containsKey("person_level") && personLevel != null) {
            attributes.put("person_level", List.of(personLevel));
        }
        if (!attributes.containsKey("data_levels") && !dataLevels.isEmpty()) {
            attributes.put("data_levels", new ArrayList<>(dataLevels));
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
            Optional<KeycloakUserDTO> remote = keycloakAdminClient.findById(id, accessToken);
            if (remote.isPresent()) {
                KeycloakUserDTO user = remote.get();
                cacheUser(user);
                return user.getUsername();
            }
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

    private List<String> resolveDataLevels(Map<String, List<String>> attributes, String personLevel) {
        List<String> values = attributeList(attributes, "data_levels", "dataLevels", "data_levels[]");
        if (!values.isEmpty()) {
            for (String value : values) {
                String cleaned = normalizeDataLevelValue(value);
                if (cleaned != null) {
                    return new ArrayList<>(List.of(cleaned));
                }
            }
        }
        return new ArrayList<>(computeDataLevels(personLevel == null ? DEFAULT_PERSON_LEVEL : personLevel));
    }

    private String normalizeDataLevelValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (cleaned.isEmpty()) {
            return null;
        }
        String mapped = DATA_LEVEL_ALIASES.get(cleaned);
        if (mapped != null) {
            return mapped;
        }
        return cleaned.startsWith("DATA_") ? cleaned : null;
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

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> getUserRoles(@PathVariable String id) {
        KeycloakUserDTO u = stores.findUserById(id);
        if (u == null) return ResponseEntity.ok(List.of());
        List<KeycloakRoleDTO> roles = u
            .getRealmRoles()
            .stream()
            .map(stores.roles::get)
            .filter(Objects::nonNull)
            .toList();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USER_ROLES_LIST", "KC_USER", id, "SUCCESS", null);
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignRoles(
        @PathVariable String id,
        @RequestBody List<KeycloakRoleDTO> roles,
        HttpServletRequest request
    ) {
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        List<String> roleNames = roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitGrantRoles(
            username,
            roleNames,
            currentUser(),
            clientIp(request)
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
        String username = resolveUsername(id, null, currentAccessToken());
        if (username == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        List<String> roleNames = roles.stream().map(KeycloakRoleDTO::getName).filter(Objects::nonNull).toList();
        ApprovalDTOs.ApprovalRequestDetail approval = adminUserService.submitRevokeRoles(
            username,
            roleNames,
            currentUser(),
            clientIp(request)
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
    @GetMapping("/keycloak/roles")
    public ResponseEntity<List<KeycloakRoleDTO>> listRoles() {
        var list = stores.listRoles();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_ROLES_LIST", "KC_ROLE", "list", "SUCCESS", null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/roles/{name}")
    public ResponseEntity<?> getRole(@PathVariable String name) {
        KeycloakRoleDTO role = stores.roles.get(name);
        if (role == null) return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_ROLE_DETAIL", "KC_ROLE", name, "SUCCESS", null);
        return ResponseEntity.ok(role);
    }

    @PostMapping("/keycloak/roles")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> createRole(@RequestBody KeycloakRoleDTO payload) {
        if (payload.getName() == null || payload.getName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        }
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ROLE_CREATE", "ROLE", saved.getName(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<KeycloakRoleDTO>> updateRole(@PathVariable String name, @RequestBody KeycloakRoleDTO payload) {
        payload.setName(Optional.ofNullable(payload.getName()).orElse(name));
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ROLE_UPDATE", "ROLE", saved.getName(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @DeleteMapping("/keycloak/roles/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable String name) {
        boolean removed = stores.deleteRole(name);
        if (!removed) return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ROLE_DELETE", "ROLE", name, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- UserProfile (mock) ----
    @GetMapping("/keycloak/userprofile/config")
    public ResponseEntity<Map<String, Object>> userProfileConfig() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("attributes", List.of());
        cfg.put("groups", List.of());
        cfg.put("unmanagedAttributePolicy", "ENABLED");
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USERPROFILE_CONFIG", "KC_USERPROFILE", "config", "SUCCESS", null);
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
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_LOCALIZATION", "KC_I18N", "zh-CN", "SUCCESS", null);
        return ResponseEntity.ok(t);
    }

    // ---- Groups ----
    @GetMapping("/keycloak/groups")
    public ResponseEntity<List<KeycloakGroupDTO>> listGroups() {
        var list = new ArrayList<>(stores.groups.values());
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "GROUP_LIST", "GROUP", "list", "SUCCESS", null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/groups/{id}")
    public ResponseEntity<?> getGroup(@PathVariable String id) {
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "GROUP_DETAIL", "GROUP", id, "SUCCESS", null);
        return ResponseEntity.ok(g);
    }

    @GetMapping("/keycloak/groups/{id}/members")
    public ResponseEntity<List<String>> groupMembers(@PathVariable String id) {
        // In-memory placeholder: no persisted memberships; return empty
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "GROUP_MEMBERS_LIST", "GROUP", id, "SUCCESS", null);
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/keycloak/groups")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> createGroup(@RequestBody KeycloakGroupDTO payload) {
        if (payload.getName() == null || payload.getName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("组名称不能为空"));
        }
        payload.setId(UUID.randomUUID().toString());
        if (payload.getPath() == null) payload.setPath("/" + payload.getName());
        stores.groups.put(payload.getId(), payload);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "GROUP_CREATE", "GROUP", payload.getName(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PutMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<KeycloakGroupDTO>> updateGroup(@PathVariable String id, @RequestBody KeycloakGroupDTO patch) {
        KeycloakGroupDTO g = stores.groups.get(id);
        if (g == null) return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        if (patch.getName() != null) g.setName(patch.getName());
        if (patch.getAttributes() != null && !patch.getAttributes().isEmpty()) g.setAttributes(patch.getAttributes());
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "GROUP_UPDATE", "GROUP", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(g));
    }

    @DeleteMapping("/keycloak/groups/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String id) {
        KeycloakGroupDTO removed = stores.groups.remove(id);
        if (removed == null) return ResponseEntity.status(404).body(ApiResponse.error("用户组不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "GROUP_DELETE", "GROUP", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> addMember(@PathVariable String id, @PathVariable String userId) {
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "GROUP_ADD_MEMBER", "GROUP", id + ":" + userId, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/keycloak/groups/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable String id, @PathVariable String userId) {
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "GROUP_REMOVE_MEMBER", "GROUP", id + ":" + userId, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/keycloak/groups/user/{userId}")
    public ResponseEntity<List<KeycloakGroupDTO>> groupsByUser(@PathVariable String userId) {
        // Placeholder: return empty
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "GROUPS_BY_USER_LIST", "GROUP", userId, "SUCCESS", null);
        return ResponseEntity.ok(List.of());
    }

    // ---- Approvals ----
    @GetMapping("/approval-requests")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals() {
        List<ApprovalDTOs.ApprovalRequest> list = adminUserService.listApprovals();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "APPROVAL_LIST", "APPROVAL", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/approval-requests/{id}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> getApproval(@PathVariable long id) {
        Optional<ApprovalDTOs.ApprovalRequestDetail> detail = adminUserService.findApprovalDetail(id);
        if (detail.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在"));
        }
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "APPROVAL_DETAIL", "APPROVAL", String.valueOf(id), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(detail.get()));
    }

    @GetMapping("/keycloak/approvals")
    public ResponseEntity<ApiResponse<List<ApprovalDTOs.ApprovalRequest>>> listApprovals2() {
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "APPROVAL_LIST", "APPROVAL", "list2", "SUCCESS", null);
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
        try {
            return switch (normalized) {
                case "approve" -> {
                    String token = currentAccessToken();
                    if (token == null || token.isBlank()) {
                        yield ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("缺少授权令牌，无法执行审批操作"));
                    }
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.approve(id, approver, note, token);
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "reject" -> {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.reject(id, approver, note);
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                case "process" -> {
                    ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.delay(id, approver, note);
                    yield ResponseEntity.ok(ApiResponse.ok(detail));
                }
                default -> ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
            };
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/keycloak/user-sync/process/{id}")
    public ResponseEntity<ApiResponse<Void>> syncApproved(@PathVariable long id) {
        Optional<ApprovalDTOs.ApprovalRequestDetail> detail = adminUserService.findApprovalDetail(id);
        if (detail.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在"));
        }
        ApprovalDTOs.ApprovalRequestDetail current = detail.get();
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
    @PostMapping("/keycloak/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        try {
            KeycloakAuthService.LoginResult loginResult = keycloakAuthService.login(username, password);
            Map<String, Object> data = new HashMap<>();
            data.put("user", loginResult.user());
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
            auditService.record(username, "KC_AUTH_LOGIN", "KC_AUTH", username, "SUCCESS", null);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            auditService.record(username, "KC_AUTH_LOGIN", "KC_AUTH", username, "FAILURE", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            auditService.record(username, "KC_AUTH_LOGIN", "KC_AUTH", username, "FAILURE", message);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    @PostMapping("/keycloak/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/keycloak/auth/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("accessToken", UUID.randomUUID().toString())));
    }
}
