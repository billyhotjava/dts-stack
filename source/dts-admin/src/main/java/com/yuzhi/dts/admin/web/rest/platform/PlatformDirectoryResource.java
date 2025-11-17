package com.yuzhi.dts.admin.web.rest.platform;

import com.yuzhi.dts.admin.domain.AdminCustomRole;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.inmemory.InMemoryStores;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight directory endpoints tailored for the platform service.
 * These endpoints are whitelisted by {@link com.yuzhi.dts.admin.config.SecurityConfiguration}
 * and therefore do not require an interactive Keycloak login.
 */
@RestController
@RequestMapping("/api/platform/directory")
public class PlatformDirectoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformDirectoryResource.class);
    private static final Set<String> PROTECTED_USERNAMES = Set.of("sysadmin", "authadmin", "auditadmin", "opadmin");
    private static final Set<String> RESERVED_ROLE_KEYWORDS = Set.of("offline_access", "uma_authorization");
    private static final List<BuiltinRole> BUILTIN_ROLES = List.of(
        new BuiltinRole(
            "ROLE_DEPT_DATA_OWNER",
            "部门数据管理员",
            "负责本部门数据；可读取本部门且密级不超的资源，具备部门范围写入和授权能力；导出高敏数据需审批。",
            "DEPARTMENT",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_DEPT_DATA_DEV",
            "部门数据开发员",
            "覆盖本部门数据开发；可读取密级不超的部门数据并在部门范围内写入；不具备密级或共享策略调整、授权管理能力；导出受策略限制。",
            "DEPARTMENT",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_INST_DATA_OWNER",
            "研究所数据管理员",
            "面向全所共享区；可读取全所共享区内密级不超的数据，并写入和管理共享策略；负责编辑/查看授权；导出高敏数据需审批。",
            "INSTITUTE",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_INST_DATA_DEV",
            "研究所数据开发员",
            "在全所共享区开展数据开发；可读取共享区密级不超的数据并写入共享区；无密级或共享策略调整、授权能力；导出受策略限制。",
            "INSTITUTE",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_INST_LEADER",
            "研究所领导",
            "所级领导角色，拥有研究所数据管理员全部能力，可查看跨部门高阶指标并参与审批流程；导出高敏数据需审批。",
            "INSTITUTE",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_DEPT_LEADER",
            "部门领导",
            "部门级领导角色，具备部门数据管理员所有能力，可审批与查看部门范围内的模型和共享资源；导出高敏数据需审批。",
            "DEPARTMENT",
            List.of("read", "write", "export")
        ),
        new BuiltinRole(
            "ROLE_EMPLOYEE",
            "普通员工",
            "面向业务人员的只读角色，仅可访问授权的 BI 报表与数据产品，不具备治理或开发权限。",
            "DEPARTMENT",
            List.of("read")
        )
    );

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final InMemoryStores stores;
    private final AdminUserService adminUserService;
    private final AdminCustomRoleRepository customRoleRepository;
    private final AdminRoleAssignmentRepository roleAssignmentRepository;

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String managementClientId;

    @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}")
    private String managementClientSecret;

    public PlatformDirectoryResource(
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient,
        InMemoryStores stores,
        AdminUserService adminUserService,
        AdminCustomRoleRepository customRoleRepository,
        AdminRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.stores = stores;
        this.adminUserService = adminUserService;
        this.customRoleRepository = customRoleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserSummary>>> listUsers(@RequestParam(name = "keyword", required = false) String keyword) {
        List<KeycloakUserDTO> raw = searchUsers(keyword);
        if (raw.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        LinkedHashMap<String, UserSummary> summaries = new LinkedHashMap<>();
        for (KeycloakUserDTO user : raw) {
            UserSummary summary = toSummary(user);
            if (summary == null) {
                continue;
            }
            String key = summary.username().toLowerCase();
            summaries.putIfAbsent(key, summary);
        }
        return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>(summaries.values())));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleSummary>>> listRoles() {
        List<RoleSummary> roles = resolveRoles();
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }

    private List<KeycloakUserDTO> searchUsers(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        List<KeycloakUserDTO> candidates = List.of();
        try {
            String token = adminAccessToken();
            if (StringUtils.hasText(query)) {
                candidates = keycloakAdminClient
                    .findByUsername(query, token)
                    .map(List::of)
                    .orElseGet(List::of);
            } else {
                candidates = keycloakAdminClient.listUsers(0, 200, token);
            }
        } catch (Exception ex) {
            LOG.warn("Platform directory user lookup failed via Keycloak: {}", ex.getMessage());
            LOG.debug("Platform directory user lookup stack", ex);
        }
        List<KeycloakUserDTO> normalized = normalizeCandidates(candidates, query);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        normalized = fallbackFromSnapshots(query);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        normalized = fallbackFromStore(query);
        return normalized;
    }

    private UserSummary toSummary(KeycloakUserDTO user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return null;
        }
        String username = user.getUsername().trim();
        String displayName = firstNonBlank(
            user.getFullName(),
            combine(user.getFirstName(), user.getLastName()),
            username
        );
        String dept = firstAttribute(user.getAttributes(), "dept_code", "deptCode", "department");
        return new UserSummary(
            firstNonBlank(user.getId(), username),
            username,
            displayName,
            StringUtils.hasText(dept) ? dept.trim() : null
        );
    }

    private String adminAccessToken() {
        var tokenResponse = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken())) {
            throw new IllegalStateException("Keycloak 管理客户端未返回 access_token");
        }
        return tokenResponse.accessToken();
    }

    private List<KeycloakUserDTO> normalizeCandidates(List<KeycloakUserDTO> candidates, String query) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates
            .stream()
            .filter(user -> user != null && StringUtils.hasText(user.getUsername()))
            .filter(user -> !PROTECTED_USERNAMES.contains(user.getUsername().trim().toLowerCase()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<KeycloakUserDTO> fallbackFromStore(String query) {
        if (query != null && !query.isBlank()) {
            return List.of();
        }
        try {
            return normalizeCandidates(stores.listUsers(0, 200), query);
        } catch (Exception ex) {
            LOG.debug("Platform directory in-memory fallback failed: {}", ex.getMessage());
            LOG.trace("Platform directory in-memory stack", ex);
            return List.of();
        }
    }

    private List<KeycloakUserDTO> fallbackFromSnapshots(String query) {
        try {
            var page = adminUserService.listSnapshots(0, 200, query);
            if (page == null || page.isEmpty()) {
                return List.of();
            }
            List<KeycloakUserDTO> snapshotUsers = new ArrayList<>(page.getNumberOfElements());
            for (AdminKeycloakUser snapshot : page.getContent()) {
                if (snapshot == null || !StringUtils.hasText(snapshot.getUsername())) {
                    continue;
                }
                KeycloakUserDTO dto = new KeycloakUserDTO();
                dto.setId(StringUtils.hasText(snapshot.getKeycloakId()) ? snapshot.getKeycloakId() : snapshot.getUsername());
                dto.setUsername(snapshot.getUsername());
                dto.setFullName(snapshot.getFullName());
                snapshotUsers.add(dto);
            }
            return normalizeCandidates(snapshotUsers, query);
        } catch (Exception ex) {
            LOG.debug("Platform directory snapshot fallback failed: {}", ex.getMessage());
            LOG.trace("Platform directory snapshot stack", ex);
            return List.of();
        }
    }

    private List<RoleSummary> resolveRoles() {
        LinkedHashMap<String, RoleSummary> results = new LinkedHashMap<>();
        for (BuiltinRole builtin : BUILTIN_ROLES) {
            RoleSummary summary = new RoleSummary(
                builtin.name(),
                builtin.name(),
                builtin.displayName(),
                builtin.scope(),
                builtin.operations(),
                "builtin"
            );
            results.putIfAbsent(builtin.name().toLowerCase(Locale.ROOT), summary);
        }
        try {
            for (AdminCustomRole custom : customRoleRepository.findAll()) {
                if (custom == null || !StringUtils.hasText(custom.getName())) {
                    continue;
                }
                String rawName = custom.getName().trim();
                if (isForbiddenRoleCode(rawName)) {
                    continue;
                }
                String normalized = normalizeRoleName(rawName);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                String key = normalized.toLowerCase(Locale.ROOT);
                String description = StringUtils.hasText(custom.getDescription())
                    ? custom.getDescription().trim()
                    : ("自定义角色 - " + normalized);
                List<String> operations = List.of("read", "write", "export");
                RoleSummary summary = new RoleSummary(
                    normalized,
                    normalized,
                    description,
                    StringUtils.hasText(custom.getScope()) ? custom.getScope().trim().toUpperCase(Locale.ROOT) : null,
                    operations,
                    "custom"
                );
                results.putIfAbsent(key, summary);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to load custom roles for platform directory: {}", ex.getMessage());
            LOG.debug("Custom role lookup stack", ex);
        }
        try {
            for (AdminRoleAssignment assignment : roleAssignmentRepository.findAll()) {
                if (assignment == null || !StringUtils.hasText(assignment.getRole())) {
                    continue;
                }
                String rawName = assignment.getRole().trim();
                if (isForbiddenRoleCode(rawName)) {
                    continue;
                }
                String normalized = normalizeRoleName(rawName);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                String key = normalized.toLowerCase(Locale.ROOT);
                if (results.containsKey(key)) {
                    continue;
                }
                RoleSummary summary = new RoleSummary(
                    normalized,
                    normalized,
                    "已分配角色",
                    null,
                    List.of(),
                    "assignment"
                );
                results.putIfAbsent(key, summary);
            }
        } catch (Exception ex) {
            LOG.debug("Role assignment aggregation failed: {}", ex.getMessage());
        }
        List<RoleSummary> ordered = new ArrayList<>(results.values());
        ordered.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return ordered;
    }

    private boolean isForbiddenRoleCode(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            return true;
        }
        String lower = rawName.trim().toLowerCase(Locale.ROOT);
        String normalized = lower.startsWith("role_") ? lower.substring(5) : lower;
        if (RESERVED_ROLE_KEYWORDS.contains(lower) || RESERVED_ROLE_KEYWORDS.contains(normalized)) {
            return true;
        }
        if ("offline_access".equals(lower) || "uma_authorization".equals(lower)) {
            return true;
        }
        if (lower.startsWith("default-roles-")) {
            return true;
        }
        return lower.startsWith("role_default_roles-") || normalized.startsWith("default_roles-");
    }

    private String normalizeRoleName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String cleaned = name.trim().replace('-', '_').replace(' ', '_');
        String upper = cleaned.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private String combine(String first, String last) {
        if (!StringUtils.hasText(first) && !StringUtils.hasText(last)) {
            return null;
        }
        if (!StringUtils.hasText(first)) {
            return last;
        }
        if (!StringUtils.hasText(last)) {
            return first;
        }
        return (first + " " + last).trim();
    }

    private String firstAttribute(Map<String, List<String>> attributes, String... keys) {
        if (attributes == null || attributes.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) continue;
            List<String> values = attributes.get(key);
            if (values == null || values.isEmpty()) continue;
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
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

    public record UserSummary(String id, String username, String displayName, String deptCode) {}

    public record RoleSummary(String id, String name, String description, String scope, List<String> operations, String source) {}

    private record BuiltinRole(String name, String displayName, String description, String scope, List<String> operations) {}
}
