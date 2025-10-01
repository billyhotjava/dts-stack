package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.security.SecurityUtils;
import java.time.Instant;
import java.util.*;
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

    public KeycloakApiResource(
        InMemoryStores stores,
        AdminAuditService auditService,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient
    ) {
        this.stores = stores;
        this.auditService = auditService;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    // ---- Users ----
    @GetMapping("/keycloak/users")
    public ResponseEntity<List<KeycloakUserDTO>> listUsers(@RequestParam(defaultValue = "0") int first, @RequestParam(defaultValue = "100") int max) {
        String token = currentAccessToken();
        List<KeycloakUserDTO> list = keycloakAdminClient.listUsers(first, max, token);
        if (list.isEmpty()) {
            list = stores.listUsers(first, max);
        } else {
            list.forEach(this::cacheUser);
        }
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "KC_USERS_LIST", "KC_USER", "list", "SUCCESS", null);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/keycloak/users/search")
    public ResponseEntity<List<KeycloakUserDTO>> searchUsers(@RequestParam String username) {
        String q = username == null ? "" : username.toLowerCase();
        List<KeycloakUserDTO> list = keycloakAdminClient
            .findByUsername(username, currentAccessToken())
            .map(List::of)
            .orElseGet(List::of);
        if (list.isEmpty()) {
            list = stores
                .users
                .values()
                .stream()
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                .toList();
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
    public ResponseEntity<ApiResponse<KeycloakUserDTO>> createUser(@RequestBody KeycloakUserDTO payload) {
        if (payload.getUsername() == null || payload.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("用户名不能为空"));
        }
        KeycloakUserDTO created = keycloakAdminClient.createUser(payload, currentAccessToken());
        cacheUser(created);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "USER_CREATE", "USER", created.getUsername(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PutMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<KeycloakUserDTO>> updateUser(@PathVariable String id, @RequestBody KeycloakUserDTO patch) {
        KeycloakUserDTO current = keycloakAdminClient.findById(id, currentAccessToken()).orElse(stores.findUserById(id));
        if (current == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(id, mergeForUpdate(current, patch), currentAccessToken());
        cacheUser(updated);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "USER_UPDATE", "USER", updated.getUsername(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/keycloak/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        KeycloakUserDTO removed = stores.findUserById(id);
        keycloakAdminClient.deleteUser(id, currentAccessToken());
        stores.users.remove(id);
        if (removed == null) {
            removed = keycloakAdminClient.findById(id, currentAccessToken()).orElse(null);
        }
        auditService.record(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "USER_DELETE",
            "USER",
            removed != null ? removed.getUsername() : id,
            "SUCCESS",
            null
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/keycloak/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String id) {
        if (!stores.users.containsKey(id)) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "USER_RESET_PASSWORD", "USER", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/keycloak/users/{id}/enabled")
    public ResponseEntity<ApiResponse<KeycloakUserDTO>> setEnabled(@PathVariable String id, @RequestBody Map<String, Object> body) {
        KeycloakUserDTO u = keycloakAdminClient.findById(id, currentAccessToken()).orElse(stores.findUserById(id));
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Object val = body.get("enabled");
        boolean enabled = Boolean.TRUE.equals(val) || (val instanceof Boolean b && b);
        u.setEnabled(enabled);
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(id, u, currentAccessToken());
        cacheUser(updated);
        auditService.record(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            enabled ? "USER_ENABLE" : "USER_DISABLE",
            "USER",
            updated.getUsername(),
            "SUCCESS",
            null
        );
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    // ---- ABAC person_level + data_levels management (dev helper) ----
    @PutMapping("/keycloak/users/{id}/person-level")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setPersonLevel(@PathVariable String id, @RequestBody Map<String, String> body) {
        KeycloakUserDTO u = keycloakAdminClient.findById(id, currentAccessToken()).orElse(stores.findUserById(id));
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        String level = String.valueOf(body.getOrDefault("person_level", "")).toUpperCase();
        if (!List.of("NON_SECRET", "GENERAL", "IMPORTANT", "CORE").contains(level)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的人员密级"));
        }
        u.getAttributes().put("person_level", List.of(level));
        u.getAttributes().put("data_levels", computeDataLevels(level));
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(id, u, currentAccessToken());
        cacheUser(updated);
        auditService.record(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "USER_SET_PERSON_LEVEL",
            "USER",
            updated.getUsername(),
            "SUCCESS",
            null
        );
        Map<String, Object> resp = Map.of("person_level", level, "data_levels", updated.getAttributes().get("data_levels"));
        return ResponseEntity.ok(ApiResponse.ok(resp));
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
        return switch (personLevel) {
            case "NON_SECRET" -> List.of("DATA_PUBLIC");
            case "GENERAL" -> List.of("DATA_PUBLIC", "DATA_INTERNAL");
            case "IMPORTANT" -> List.of("DATA_PUBLIC", "DATA_INTERNAL", "DATA_SECRET");
            case "CORE" -> List.of("DATA_PUBLIC", "DATA_INTERNAL", "DATA_SECRET", "DATA_TOP_SECRET");
            default -> List.of("DATA_PUBLIC");
        };
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

    private KeycloakUserDTO mergeForUpdate(KeycloakUserDTO current, KeycloakUserDTO patch) {
        KeycloakUserDTO merged = new KeycloakUserDTO();
        merged.setId(current.getId());
        merged.setUsername(patch.getUsername() != null ? patch.getUsername() : current.getUsername());
        merged.setEmail(patch.getEmail() != null ? patch.getEmail() : current.getEmail());
        merged.setFirstName(patch.getFirstName() != null ? patch.getFirstName() : current.getFirstName());
        merged.setLastName(patch.getLastName() != null ? patch.getLastName() : current.getLastName());
        merged.setEnabled(patch.getEnabled() != null ? patch.getEnabled() : current.getEnabled());
        merged.setEmailVerified(patch.getEmailVerified() != null ? patch.getEmailVerified() : current.getEmailVerified());
        merged.setAttributes(
            patch.getAttributes() != null && !patch.getAttributes().isEmpty() ? patch.getAttributes() : current.getAttributes()
        );
        merged.setGroups(patch.getGroups() != null && !patch.getGroups().isEmpty() ? patch.getGroups() : current.getGroups());
        merged.setRealmRoles(
            patch.getRealmRoles() != null && !patch.getRealmRoles().isEmpty() ? patch.getRealmRoles() : current.getRealmRoles()
        );
        merged.setClientRoles(
            patch.getClientRoles() != null && !patch.getClientRoles().isEmpty() ? patch.getClientRoles() : current.getClientRoles()
        );
        merged.setCreatedTimestamp(current.getCreatedTimestamp());
        return merged;
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
    public ResponseEntity<ApiResponse<Void>> assignRoles(@PathVariable String id, @RequestBody List<KeycloakRoleDTO> roles) {
        KeycloakUserDTO u = stores.findUserById(id);
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Set<String> names = new HashSet<>(u.getRealmRoles());
        for (KeycloakRoleDTO r : roles) names.add(r.getName());
        u.setRealmRoles(new ArrayList<>(names));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "USER_ASSIGN_ROLES", "USER", u.getUsername(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/keycloak/users/{id}/roles")
    public ResponseEntity<ApiResponse<Void>> removeRoles(@PathVariable String id, @RequestBody List<KeycloakRoleDTO> roles) {
        KeycloakUserDTO u = stores.findUserById(id);
        if (u == null) return ResponseEntity.status(404).body(ApiResponse.error("用户不存在"));
        Set<String> toRemove = new HashSet<>();
        for (KeycloakRoleDTO r : roles) toRemove.add(r.getName());
        u.setRealmRoles(u.getRealmRoles().stream().filter(name -> !toRemove.contains(name)).toList());
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "USER_REMOVE_ROLES", "USER", u.getUsername(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(null));
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
        List<ApprovalDTOs.ApprovalRequest> list = stores
            .approvals
            .values()
            .stream()
            .map(d -> {
                ApprovalDTOs.ApprovalRequest a = new ApprovalDTOs.ApprovalRequest();
                a.id = d.id;
                a.requester = d.requester;
                a.type = d.type;
                a.reason = d.reason;
                a.createdAt = d.createdAt;
                a.decidedAt = d.decidedAt;
                a.status = d.status;
                a.approver = d.approver;
                a.decisionNote = d.decisionNote;
                a.errorMessage = d.errorMessage;
                return a;
            })
            .toList();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "APPROVAL_LIST", "APPROVAL", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/approval-requests/{id}")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> getApproval(@PathVariable long id) {
        ApprovalDTOs.ApprovalRequestDetail d = stores.approvals.get(id);
        if (d == null) return ResponseEntity.status(404).body(ApiResponse.error("审批请求不存在"));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "APPROVAL_DETAIL", "APPROVAL", String.valueOf(id), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(d));
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
        ApprovalDTOs.ApprovalRequestDetail d = stores.approvals.get(id);
        if (d == null) return ResponseEntity.status(404).body(ApiResponse.error("请求不存在"));
        String now = Instant.now().toString();
        String approver = Optional.ofNullable(body).map(b -> b.approver).orElse(d.approver != null ? d.approver : "authadmin");
        switch (action.toLowerCase()) {
            case "approve" -> {
                d.status = "APPROVED";
                d.decidedAt = now;
                d.approver = approver;
                d.decisionNote = body != null ? body.note : d.decisionNote;
                d.errorMessage = null;
                auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "APPROVAL_APPROVE", "APPROVAL", String.valueOf(id), "SUCCESS", null);
            }
            case "reject" -> {
                d.status = "REJECTED";
                d.decidedAt = now;
                d.approver = approver;
                d.decisionNote = body != null ? body.note : d.decisionNote;
                auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "APPROVAL_REJECT", "APPROVAL", String.valueOf(id), "SUCCESS", null);
            }
            case "process" -> {
                d.status = "APPLIED";
                d.decidedAt = now;
                d.approver = approver;
                if (body != null && body.note != null) d.decisionNote = body.note;
                d.errorMessage = null;
                auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "APPROVAL_PROCESS", "APPROVAL", String.valueOf(id), "SUCCESS", null);
            }
            default -> {
                return ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(d));
    }

    @PostMapping("/keycloak/user-sync/process/{id}")
    public ResponseEntity<ApiResponse<Void>> syncApproved(@PathVariable long id) {
        ApprovalDTOs.ApprovalRequestDetail d = stores.approvals.get(id);
        if (d == null) return ResponseEntity.status(404).body(ApiResponse.error("请求不存在"));
        d.status = "APPLIED";
        d.decidedAt = Optional.ofNullable(d.decidedAt).orElse(Instant.now().toString());
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
