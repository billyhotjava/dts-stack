package com.yuzhi.dts.platform.service.admin.gateway.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminDirectoryGateway {

    private static final Logger LOG = LoggerFactory.getLogger(AdminDirectoryGateway.class);

    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<OrgNode>>> ORG_TREE_ENVELOPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<PlatformUser>>> PLATFORM_USER_LIST =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<PlatformRole>>> PLATFORM_ROLE_LIST =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<KeycloakUser>> KEYCLOAK_USER_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<KeycloakUser>>> KEYCLOAK_USER_ENVELOPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<KeycloakRole>> KEYCLOAK_ROLE_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<AdminGatewayEnvelope<List<KeycloakRole>>> KEYCLOAK_ROLE_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    private final AdminGatewayTransport transport;
    private final DtsAdminProperties properties;

    public AdminDirectoryGateway(AdminGatewayTransport transport, DtsAdminProperties properties) {
        this.transport = transport;
        this.properties = properties;
    }

    public List<OrgNode> fetchOrgTree() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            List<OrgNode> tree = transport.exchangeEnvelopeData(
                AdminGatewayTarget.ADMIN_API,
                HttpMethod.GET,
                "/platform/orgs",
                null,
                ORG_TREE_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
            if (tree != null && !tree.isEmpty()) {
                return tree;
            }
        } catch (AdminGatewayException ex) {
            LOG.warn("Failed to fetch org tree from dts-admin: {}", ex.getMessage());
        }
        try {
            List<OrgNode> synced = transport.exchangeEnvelopeData(
                AdminGatewayTarget.ADMIN_API,
                HttpMethod.POST,
                "/platform/orgs/sync",
                null,
                ORG_TREE_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
            return synced != null ? synced : List.of();
        } catch (AdminGatewayException ex) {
            LOG.warn("Failed to sync org tree from dts-admin: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<UserSummary> searchUsers(String keyword) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String query = keyword == null ? "" : keyword.trim();
        try {
            List<PlatformUser> users = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.GET,
                buildQuerySuffix("/platform/directory/users", "keyword", query),
                null,
                PLATFORM_USER_LIST,
                AdminGatewayRequestOptions.defaults()
            );
            List<UserSummary> summaries = normalizePlatformUsers(users);
            if (!summaries.isEmpty()) {
                return summaries;
            }
        } catch (AdminGatewayException ex) {
            LOG.debug("Platform directory users endpoint failed: {}", ex.getMessage());
        }
        List<KeycloakUser> legacy = query.isBlank() ? fetchLegacyUsers("/keycloak/users?first=0&max=100") : fetchLegacyUsers(buildQuerySuffix("/keycloak/users/search", "username", query));
        if (legacy.isEmpty()) {
            return List.of();
        }
        List<UserSummary> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (KeycloakUser user : legacy) {
            UserSummary summary = toSummary(user);
            if (summary != null && seen.add(summary.username().toLowerCase(Locale.ROOT))) {
                result.add(summary);
            }
        }
        return result;
    }

    public List<RoleSummary> listRoles() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            List<PlatformRole> roles = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.GET,
                "/platform/directory/roles",
                null,
                PLATFORM_ROLE_LIST,
                AdminGatewayRequestOptions.defaults()
            );
            List<RoleSummary> summaries = normalizePlatformRoles(roles);
            if (!summaries.isEmpty()) {
                return summaries;
            }
        } catch (AdminGatewayException ex) {
            LOG.debug("Platform directory roles endpoint failed: {}", ex.getMessage());
        }

        try {
            List<KeycloakRole> roles = transport.exchangeRaw(
                AdminGatewayTarget.API,
                HttpMethod.GET,
                "/keycloak/platform/roles",
                null,
                KEYCLOAK_ROLE_LIST,
                AdminGatewayRequestOptions.defaults()
            );
            List<RoleSummary> summaries = normalizeLegacyRoles(roles);
            if (!summaries.isEmpty()) {
                return summaries;
            }
        } catch (AdminGatewayException ex) {
            LOG.debug("Legacy platform role list failed: {}", ex.getMessage());
        }

        try {
            List<KeycloakRole> roles = transport.exchangeEnvelopeData(
                AdminGatewayTarget.API,
                HttpMethod.GET,
                "/keycloak/platform/roles",
                null,
                KEYCLOAK_ROLE_ENVELOPE,
                AdminGatewayRequestOptions.defaults()
            );
            return normalizeLegacyRoles(roles);
        } catch (AdminGatewayException ex) {
            LOG.debug("Legacy platform role envelope failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<KeycloakUser> fetchLegacyUsers(String suffix) {
        try {
            return safeList(
                transport.exchangeRaw(
                    AdminGatewayTarget.API,
                    HttpMethod.GET,
                    suffix,
                    null,
                    KEYCLOAK_USER_LIST,
                    AdminGatewayRequestOptions.defaults()
                )
            );
        } catch (AdminGatewayException ex) {
            LOG.debug("Legacy keycloak user list failed: {}", ex.getMessage());
        }
        try {
            return safeList(
                transport.exchangeEnvelopeData(
                    AdminGatewayTarget.API,
                    HttpMethod.GET,
                    suffix,
                    null,
                    KEYCLOAK_USER_ENVELOPE,
                    AdminGatewayRequestOptions.defaults()
                )
            );
        } catch (AdminGatewayException ex) {
            LOG.debug("Legacy keycloak user envelope failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<UserSummary> normalizePlatformUsers(List<PlatformUser> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<UserSummary> summaries = new ArrayList<>(users.size());
        for (PlatformUser user : users) {
            if (user == null || !StringUtils.hasText(user.username)) {
                continue;
            }
            String username = user.username.trim();
            String id = StringUtils.hasText(user.id) ? user.id.trim() : username;
            String displayName = StringUtils.hasText(user.displayName) ? user.displayName.trim() : username;
            String dept = StringUtils.hasText(user.deptCode) ? user.deptCode.trim() : null;
            summaries.add(new UserSummary(id, username, displayName, dept));
        }
        return summaries;
    }

    private List<RoleSummary> normalizePlatformRoles(List<PlatformRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<RoleSummary> result = new ArrayList<>(roles.size());
        Set<String> seen = new HashSet<>();
        for (PlatformRole role : roles) {
            RoleSummary summary = toSummary(role);
            if (summary != null && seen.add(summary.name().toLowerCase(Locale.ROOT))) {
                result.add(summary);
            }
        }
        return result;
    }

    private List<RoleSummary> normalizeLegacyRoles(List<KeycloakRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<RoleSummary> result = new ArrayList<>(roles.size());
        Set<String> seen = new HashSet<>();
        for (KeycloakRole role : roles) {
            RoleSummary summary = toSummary(role);
            if (summary != null && seen.add(summary.name().toLowerCase(Locale.ROOT))) {
                result.add(summary);
            }
        }
        return result;
    }

    private <T> List<T> safeList(List<T> input) {
        return input == null ? List.of() : input;
    }

    private UserSummary toSummary(KeycloakUser user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return null;
        }
        String username = user.getUsername().trim();
        String displayName = firstNonBlank(user.getFullName(), combine(user.getFirstName(), user.getLastName()), username);
        String dept = firstAttribute(user.getAttributes(), "dept_code", "deptCode", "department");
        return new UserSummary(user.getId(), username, displayName, StringUtils.hasText(dept) ? dept.trim() : null);
    }

    private RoleSummary toSummary(PlatformRole role) {
        if (role == null) {
            return null;
        }
        return buildRoleSummary(role.id, role.name, role.description, role.scope, role.operations, role.source);
    }

    private RoleSummary toSummary(KeycloakRole role) {
        if (role == null) {
            return null;
        }
        return buildRoleSummary(role.id, role.name, role.description, null, Collections.emptyList(), "legacy");
    }

    private RoleSummary buildRoleSummary(String id, String name, String description, String scope, List<String> operations, String source) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalizedName = normalizeRoleName(name);
        if (!StringUtils.hasText(normalizedName)) {
            return null;
        }
        String resolvedId = StringUtils.hasText(id) ? id.trim() : normalizedName;
        String desc = StringUtils.hasText(description) ? description.trim() : null;
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : null;
        List<String> safeOps = operations == null
            ? List.of()
            : operations.stream().filter(StringUtils::hasText).map(op -> op.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        return new RoleSummary(resolvedId, normalizedName, desc, normalizedScope, safeOps, source);
    }

    private String normalizeRoleName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String cleaned = name.trim().replace('-', '_').replace(' ', '_');
        String upper = cleaned.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private String buildQuerySuffix(String path, String queryName, String queryValue) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (StringUtils.hasText(queryName)) {
            builder.queryParam(queryName, queryValue == null ? "" : queryValue);
        }
        return builder.build(true).toUriString();
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstAttribute(Map<String, List<String>> attributes, String... keys) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> values = attributes.get(key);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrgNode {
        private Long id;
        private String name;
        @JsonProperty("deptCode")
        private String deptCode;
        private Long parentId;
        private List<OrgNode> children;
        @JsonProperty("isRoot")
        private Boolean isRoot;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDeptCode() { return deptCode; }
        public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public List<OrgNode> getChildren() { return children; }
        public void setChildren(List<OrgNode> children) { this.children = children; }
        public Boolean getIsRoot() { return isRoot; }
        public void setIsRoot(Boolean isRoot) { this.isRoot = isRoot; }
    }

    public record UserSummary(String id, String username, String displayName, String deptCode) {}

    public record RoleSummary(String id, String name, String description, String scope, List<String> operations, String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PlatformUser {
        @JsonProperty("id")
        private String id;

        @JsonProperty("username")
        private String username;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("deptCode")
        private String deptCode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PlatformRole {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("operations")
        private List<String> operations;

        @JsonProperty("source")
        private String source;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KeycloakUser {
        private String id;
        private String username;
        private String fullName;
        private String firstName;
        private String lastName;
        private Map<String, List<String>> attributes = Collections.emptyMap();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public Map<String, List<String>> getAttributes() { return attributes == null ? Collections.emptyMap() : attributes; }
        public void setAttributes(Map<String, List<String>> attributes) { this.attributes = attributes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KeycloakRole {
        private String id;
        private String name;
        private String description;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
