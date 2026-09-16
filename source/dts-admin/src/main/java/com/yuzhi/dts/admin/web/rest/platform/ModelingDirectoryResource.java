package com.yuzhi.dts.admin.web.rest.platform;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Strict, service-authenticated view of the existing directory. Never uses local snapshots. */
@RestController
@RequestMapping("/api/platform/directory")
public class ModelingDirectoryResource {
    private final AdminInboundServiceAuthenticator authenticator;
    private final KeycloakAuthService auth;
    private final KeycloakAdminClient users;
    private final OrganizationRepository organizations;
    private final com.yuzhi.dts.admin.repository.PersonProfileRepository profiles;
    private final AdminRoleMemberRepository roleMembers;
    private final AdminRoleAssignmentRepository roleAssignments;

    private static final List<String> MODELING_ROLES = List.of(AuthoritiesConstants.DEPT_DATA_OWNER, AuthoritiesConstants.DEPT_LEADER,
        AuthoritiesConstants.INST_DATA_OWNER, AuthoritiesConstants.INST_LEADER);

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String clientId;
    @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}")
    private String clientSecret;

    public ModelingDirectoryResource(AdminInboundServiceAuthenticator authenticator, KeycloakAuthService auth,
        KeycloakAdminClient users, OrganizationRepository organizations, com.yuzhi.dts.admin.repository.PersonProfileRepository profiles,
        AdminRoleMemberRepository roleMembers, AdminRoleAssignmentRepository roleAssignments) {
        this.authenticator = authenticator;
        this.auth = auth;
        this.users = users;
        this.organizations = organizations;
        this.profiles = profiles;
        this.roleMembers = roleMembers;
        this.roleAssignments = roleAssignments;
    }

    @GetMapping(value = "/users/resolve", params = "purpose=modeling")
    public ApiResponse<Identity> resolve(@RequestParam String principalKey, HttpServletRequest request) {
        requireService(request);
        if (principalKey == null || principalKey.isBlank() || principalKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            String token = token();
            var current = users.currentUser(principalKey, token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            KeycloakUserDTO user = current.user();
            if (current.roles() == null || user.getEnabled() == null || user.getUsername() == null) throw new IllegalStateException("Incomplete identity");
            // Data roles are granted in DTS, not in Keycloak; merge them the same way login does.
            Set<String> roles = new LinkedHashSet<>();
            current.roles().forEach(role -> addRole(roles, role));
            roles.addAll(localRoles(user.getUsername()));
            return ApiResponse.ok(identity(user, List.copyOf(roles), organizations.findAll(), currentProfiles(List.of(user))));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前用户目录不可用");
        }
    }

    @GetMapping(value = "/departments", params = "purpose=modeling")
    public ApiResponse<List<Department>> departments(HttpServletRequest request) {
        requireService(request);
        return ApiResponse.ok(organizations.findAll().stream().filter(this::activeDepartment)
            .map(node -> new Department(code(node), node.getName())).sorted(Comparator.comparing(Department::code)).toList());
    }

    @GetMapping(value = "/users", params = "purpose=modeling")
    public ApiResponse<List<Identity>> candidates(@RequestParam(defaultValue = "") String keyword,
        @RequestParam String departmentCode, HttpServletRequest request) {
        requireService(request);
        if (keyword.length() > 128 || departmentCode.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            String token = token();
            List<OrganizationNode> departments = organizations.findAll();
            Map<String, KeycloakUserDTO> candidates = new LinkedHashMap<>();
            Map<String, Set<String>> rolesById = new HashMap<>();
            // Bounded by the role set, never a roles lookup for each candidate.
            for (String role : MODELING_ROLES) {
                List<KeycloakUserDTO> members = users.currentRoleMembers(role, token);
                if (members == null) throw new IllegalStateException("Incomplete membership");
                for (KeycloakUserDTO user : members) {
                    if (user.getId() == null) continue;
                    candidates.put(user.getId(), user);
                    rolesById.computeIfAbsent(user.getId(), ignored -> new HashSet<>()).add(role);
                }
            }
            // DTS-granted data roles: resolve each local member to its Keycloak identity by exact username.
            Map<String, Set<String>> localRolesByUsername = localRoleMembers();
            if (localRolesByUsername.size() > 1000) throw new IllegalStateException("Directory selection exceeds bounded capacity");
            for (var entry : localRolesByUsername.entrySet()) {
                KeycloakUserDTO user = candidates.values().stream()
                    .filter(known -> entry.getKey().equalsIgnoreCase(known.getUsername())).findFirst()
                    .orElseGet(() -> users.findByUsernameStrict(entry.getKey(), token).orElse(null));
                if (user == null || user.getId() == null || !entry.getKey().equalsIgnoreCase(user.getUsername())) continue;
                candidates.putIfAbsent(user.getId(), user);
                rolesById.computeIfAbsent(user.getId(), ignored -> new HashSet<>()).addAll(entry.getValue());
            }
            var currentProfiles = currentProfiles(candidates.values());
            String query = keyword.trim().toLowerCase(Locale.ROOT);
            return ApiResponse.ok(candidates.values().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(user -> identity(user, List.copyOf(rolesById.get(user.getId())), departments, currentProfiles))
                .filter(user -> Boolean.TRUE.equals(user.enabled()) && departmentCode.equals(user.deptCode()))
                .filter(user -> (user.username() + " " + user.displayName()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Identity::username)).limit(100).toList());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前用户目录不可用");
        }
    }

    private Identity identity(KeycloakUserDTO user, List<String> roles, List<OrganizationNode> nodes,
        Map<String, com.yuzhi.dts.admin.domain.PersonProfile> currentProfiles) {
        var matches = identifiers(user).stream().map(currentProfiles::get).filter(Objects::nonNull).distinct().toList();
        if (matches.size() > 1) throw new IllegalStateException("Ambiguous current personnel identity");
        var profile = matches.isEmpty() ? null : matches.getFirst();
        String rawDepartment = profile != null && profile.getDeptCode() != null && !profile.getDeptCode().isBlank()
            ? profile.getDeptCode().trim() : attribute(user, "dept_code", "deptCode", "department");
        // A numeric node ID is a verified directory alias only; no suffix/ancestor matching.
        OrganizationNode department = nodes.stream().filter(this::activeDepartment)
            .filter(node -> Objects.equals(code(node), rawDepartment)).findFirst().orElse(null);
        if (department == null && rawDepartment != null) {
            department = nodes.stream().filter(this::activeDepartment)
                .filter(node -> Objects.equals(String.valueOf(node.getId()), rawDepartment)).findFirst().orElse(null);
        }
        return new Identity(user.getId(), user.getUsername(),
            profile != null && profile.getFullName() != null && !profile.getFullName().isBlank() ? profile.getFullName() : (user.getFullName() == null ? user.getUsername() : user.getFullName()),
            department == null ? null : code(department), department == null ? null : department.getName(),
            roles.stream().map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(Locale.ROOT)).distinct().toList(),
            Boolean.TRUE.equals(user.getEnabled()) && (profile == null || profile.getLifecycleStatus() == com.yuzhi.dts.admin.domain.enumeration.PersonLifecycleStatus.ACTIVE), attribute(user, "personnel_level", "person_security_level", "person_level"));
    }

    /** Same personnel-directory priority as the existing directory API, with no swallowed lookup failure. */
    private Map<String, com.yuzhi.dts.admin.domain.PersonProfile> currentProfiles(Collection<KeycloakUserDTO> candidates) {
        var keys = candidates.stream().flatMap(user -> identifiers(user).stream()).collect(java.util.stream.Collectors.toSet());
        if (keys.isEmpty()) return Map.of();
        Map<String, com.yuzhi.dts.admin.domain.PersonProfile> result = new HashMap<>();
        for (var profile : profiles.findByAnyIdentifierLowerIn(keys)) {
            for (String key : java.util.stream.Stream.of(profile.getAccount(), profile.getPersonCode(), profile.getExternalId())
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT)).toList()) {
                var previous = result.putIfAbsent(key, profile);
                if (previous != null && !Objects.equals(previous.getId(), profile.getId())) throw new IllegalStateException("Ambiguous personnel directory identifier");
            }
        }
        return result;
    }
    private static Set<String> identifiers(KeycloakUserDTO user) {
        return java.util.stream.Stream.of(user.getId(), user.getUsername(), attribute(user,"person_code","personCode"), attribute(user,"external_id","externalId"))
            .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    private Set<String> localRoles(String username) {
        Set<String> roles = new LinkedHashSet<>();
        roleMembers.findByUsernameIgnoreCase(username).forEach(member -> addRole(roles, member.getRole()));
        roleAssignments.findByUsernameIgnoreCase(username).forEach(assignment -> addRole(roles, assignment.getRole()));
        return roles;
    }

    private Map<String, Set<String>> localRoleMembers() {
        Map<String, Set<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String role : MODELING_ROLES) {
            // Grants may be stored with or without the ROLE_ prefix.
            for (String stored : List.of(role, role.substring("ROLE_".length()))) {
                roleMembers.findByRoleIgnoreCase(stored).forEach(member -> addMember(result, member.getUsername(), role));
                roleAssignments.findByRoleIgnoreCase(stored).forEach(assignment -> addMember(result, assignment.getUsername(), role));
            }
        }
        return result;
    }

    private static void addMember(Map<String, Set<String>> result, String username, String role) {
        if (username == null || username.isBlank()) return;
        result.computeIfAbsent(username.trim(), ignored -> new LinkedHashSet<>()).add(role);
    }

    private static void addRole(Set<String> roles, String role) {
        if (role == null || role.isBlank()) return;
        String upper = role.trim().toUpperCase(Locale.ROOT);
        roles.add(upper.startsWith("ROLE_") ? upper : "ROLE_" + upper);
    }

    private static String code(OrganizationNode node) {
        return node.getDeptCode() == null || node.getDeptCode().isBlank() ? String.valueOf(node.getId()) : node.getDeptCode();
    }

    private boolean activeDepartment(OrganizationNode node) {
        return node.getId() != null && !node.isRoot() &&
            !Set.of("DISABLED", "INACTIVE", "DELETED").contains(Objects.toString(node.getStatus(), "").toUpperCase(Locale.ROOT));
    }

    private static String attribute(KeycloakUserDTO user, String... keys) {
        for (String key : keys) {
            List<String> values = user.getAttributes() == null ? null : user.getAttributes().get(key);
            if (values != null && !values.isEmpty() && values.getFirst() != null && !values.getFirst().isBlank()) return values.getFirst().trim();
        }
        return null;
    }

    private String token() {
        var response = auth.obtainClientCredentialsToken(clientId, clientSecret);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) throw new IllegalStateException("No directory token");
        return response.accessToken();
    }

    private void requireService(HttpServletRequest request) {
        if (!authenticator.authenticate(request, "dts-platform").accepted()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public record Identity(String id, String username, String displayName, String deptCode, String deptName,
        List<String> roles, Boolean enabled, String personnelLevel) {}
    public record Department(String code, String name) {}
}
