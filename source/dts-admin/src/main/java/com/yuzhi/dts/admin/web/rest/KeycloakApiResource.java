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
import com.yuzhi.dts.common.audit.AuditStage;
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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class);

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String managementClientId;

    @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}")
    private String managementClientSecret;

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
    private static final Set<String> PROTECTED_USERNAMES = Set.of("sysadmin",  "authadmin", "auditadmin", "opadmin");

    public KeycloakApiResource(
        InMemoryStores stores,
        AdminAuditService auditService,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient,
        AdminUserService adminUserService,
        AdminRoleAssignmentRepository roleAssignRepo
    ) {
        this.stores = stores;
        this.auditService = auditService;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.adminUserService = adminUserService;
        this.roleAssignRepo = roleAssignRepo;
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
                String dl = extractFirst(payload.getAttributes(), "data_levels", "dataLevels");
                LOG.info("FE payload(createUser) attributes: person_level={}, data_levels(first)={} ", pl, dl);
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
                String dl = extractFirst(patch.getAttributes(), "data_levels", "dataLevels");
                LOG.info("FE payload(updateUser) attributes: person_level={}, data_levels(first)={}", pl, dl);
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
        auditDetail.put("request", Map.of(
            "fullName",
            command.getFullName(),
            "email",
            command.getEmail(),
            "groups",
            command.getGroupPaths() == null ? 0 : command.getGroupPaths().size()
        ));
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

    // ---- ABAC person_level + data_levels management (dev helper) ----
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
        auditService.recordAction(
            currentUser(),
            "ADMIN_USER_UPDATE",
            AuditStage.SUCCESS,
            username,
            Map.of("requestId", approval.id, "level", level, "dataLevels", levels)
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("personLevel", person, "dataLevelCount", levels.size())
        );
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
        List<String> names = keycloakAdminClient.listUserRealmRoles(id, adminAccessToken());
        org.slf4j.LoggerFactory.getLogger(KeycloakApiResource.class)
            .info("Fetched user realm roles from Keycloak: userId={}, roles={}", id, names);
        if (names.isEmpty()) return ResponseEntity.ok(List.of());
        Map<String, KeycloakRoleDTO> catalog = new LinkedHashMap<>();
        for (KeycloakRoleDTO role : adminUserService.listRealmRoles()) {
            if (role.getName() != null) catalog.put(role.getName(), role);
        }
        List<KeycloakRoleDTO> roles = names.stream().map(n -> catalog.getOrDefault(n, fallbackRole(n))).toList();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_USER_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("roleCount", roles.size())
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
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("payload", payload.getName());
        auditService.recordAction(actor, "ADMIN_ROLE_CREATE", AuditStage.BEGIN, Optional.ofNullable(payload.getName()).orElse("pending"), auditDetail);
        if (!StringUtils.hasText(payload.getName())) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "角色名称不能为空");
            auditService.recordAction(actor, "ADMIN_ROLE_CREATE", AuditStage.FAIL, "pending", failure);
            return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        }
        KeycloakRoleDTO saved = stores.upsertRole(payload);
        Map<String, Object> success = new LinkedHashMap<>(auditDetail);
        success.put("role", saved.getName());
        auditService.recordAction(actor, "ADMIN_ROLE_CREATE", AuditStage.SUCCESS, saved.getName(), success);
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
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        auditService.recordAction(actor, "ADMIN_ROLE_DELETE", AuditStage.BEGIN, name, Map.of());
        boolean removed = stores.deleteRole(name);
        if (!removed) {
            auditService.recordAction(actor, "ADMIN_ROLE_DELETE", AuditStage.FAIL, name, Map.of("error", "NOT_FOUND"));
            return ResponseEntity.status(404).body(ApiResponse.error("角色不存在"));
        }
        auditService.recordAction(actor, "ADMIN_ROLE_DELETE", AuditStage.SUCCESS, name, Map.of());
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
                    auditService.recordAction(actor, "ADMIN_APPROVAL_DECIDE", AuditStage.SUCCESS, String.valueOf(id), Map.of("result", "APPROVED"));
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
        Optional<ApprovalDTOs.ApprovalRequestDetail> detail = adminUserService.findApprovalDetail(id);
        ApprovalDTOs.ApprovalRequestDetail current = detail.orElse(null);
        if (current == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在"));
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
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("username", username);
        auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.BEGIN, username, auditDetail);
        try {
            // Note: Do NOT enforce triad-only here. This endpoint serves the business platform audience.
            KeycloakAuthService.LoginResult loginResult = keycloakAuthService.login(username, password);
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
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.SUCCESS, username, Map.of("audience", "platform"));
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, username, Map.of("error", ex.getMessage(), "audience", "platform"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, username, Map.of("error", message, "audience", "platform"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }
    @PostMapping("/keycloak/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body).map(b -> b.getOrDefault("username", "")).map(String::trim).orElse("");
        String password = Optional.ofNullable(body).map(b -> b.getOrDefault("password", "")).orElse("");
        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名或密码不能为空"));
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("username", username);
        auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.BEGIN, username, auditPayload);
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
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.SUCCESS, username, Map.of("audience", "admin"));
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (BadCredentialsException ex) {
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, username, Map.of("error", ex.getMessage(), "audience", "admin"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String message = Optional.ofNullable(ex.getMessage()).filter(m -> !m.isBlank()).orElse("登录失败，请稍后重试");
            auditService.recordAction(username, "ADMIN_AUTH_LOGIN", AuditStage.FAIL, username, Map.of("error", message, "audience", "admin"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
        }
    }

    @PostMapping("/keycloak/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = Optional.ofNullable(body).map(b -> b.get("refreshToken")).orElse(null);
        auditService.recordAction(currentUser(), "ADMIN_AUTH_LOGOUT", AuditStage.BEGIN, "self", Map.of("hasRefreshToken", refreshToken != null && !refreshToken.isBlank()));
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
            auditService.recordAction(currentUser(), "ADMIN_AUTH_LOGOUT", AuditStage.SUCCESS, "self", Map.of());
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception ex) {
            auditService.recordAction(currentUser(), "ADMIN_AUTH_LOGOUT", AuditStage.FAIL, "self", Map.of("error", Optional.ofNullable(ex.getMessage()).orElse("logout failed")));
            // From client perspective, even if Keycloak-side logout fails, we clear local session; return 200 to avoid blocking UX.
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

    public record PkiLoginPayload(String assertion, String username) {}

    @PostMapping("/keycloak/auth/pki-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pkiLogin(
        @RequestBody(required = false) PkiLoginPayload payload,
        jakarta.servlet.http.HttpServletRequest request
    ) {
        boolean enabled = pkiProps != null && pkiProps.isEnabled();
        if (!enabled) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(ApiResponse.error("PKI 登录未启用"));
        }

        // Placeholder implementation: return 501 until PKI integration is configured.
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error("PKI 登录尚未集成，请稍后再试"));
    }
}
