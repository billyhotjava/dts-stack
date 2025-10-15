package com.yuzhi.dts.platform.service.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminUserDirectoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(AdminUserDirectoryClient.class);

    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<KeycloakUser>>> USER_LIST_ENVELOPE =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<PlatformUser>>> PLATFORM_USER_LIST =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<PlatformRole>>> PLATFORM_ROLE_LIST =
        new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLE_LIST_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<List<KeycloakRole>>> ROLE_LIST_ENVELOPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final DtsAdminProperties props;

    public AdminUserDirectoryClient(RestTemplateBuilder builder, DtsAdminProperties props) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.props = props;
    }

    public List<UserSummary> searchUsers(String keyword) {
        if (!props.isEnabled()) {
            LOG.debug("Admin user directory client disabled via configuration");
            return List.of();
        }
        String query = keyword == null ? "" : keyword.trim();
        List<UserSummary> platformUsers = fetchFromPlatformDirectory(query);
        if (!platformUsers.isEmpty()) {
            return platformUsers;
        }
        List<KeycloakUser> candidates = fetchUsers(query);
        if ((candidates == null || candidates.isEmpty()) && query.isBlank()) {
            candidates = fetchUsersFallback();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<UserSummary> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (KeycloakUser user : candidates) {
            UserSummary summary = toSummary(user);
            if (summary == null) {
                continue;
            }
            if (seen.add(summary.username().toLowerCase())) {
                result.add(summary);
            }
        }
        return result;
    }

    public List<RoleSummary> listRoles() {
        if (!props.isEnabled()) {
            LOG.debug("Admin role directory client disabled via configuration");
            return List.of();
        }
        List<RoleSummary> platformRoles = fetchRolesFromPlatform();
        if (!platformRoles.isEmpty()) {
            return platformRoles;
        }
        return fetchRolesLegacy();
    }

    private List<UserSummary> fetchFromPlatformDirectory(String query) {
        URI uri = buildUri(props.getApiPath(), "/platform/directory/users", query);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<ApiEnvelope<List<PlatformUser>>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                PLATFORM_USER_LIST
            );
            ApiEnvelope<List<PlatformUser>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                List<PlatformUser> raw = body.data();
                List<UserSummary> summaries = new ArrayList<>(raw.size());
                for (PlatformUser user : raw) {
                    if (user == null || !StringUtils.hasText(user.username)) {
                        continue;
                    }
                    String username = user.username.trim();
                    if (username.isEmpty()) {
                        continue;
                    }
                    String id = StringUtils.hasText(user.id) ? user.id.trim() : username;
                    String displayName = StringUtils.hasText(user.displayName) ? user.displayName.trim() : username;
                    String dept = StringUtils.hasText(user.deptCode) ? user.deptCode.trim() : null;
                    summaries.add(new UserSummary(id, username, displayName, dept));
                }
                return summaries;
            }
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "Platform directory user endpoint returned status {} body={} uri={}",
                ex.getStatusCode().value(),
                trim(ex.getResponseBodyAsString(), 256),
                uri
            );
        } catch (Exception ex) {
            LOG.debug("Platform directory user endpoint failed: {}", ex.getMessage());
            LOG.trace("Platform directory stack", ex);
        }
        return List.of();
    }

    private List<RoleSummary> fetchRolesFromPlatform() {
        URI uri = buildUri(props.getApiPath(), "/platform/directory/roles", Collections.emptyMap());
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<ApiEnvelope<List<PlatformRole>>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                PLATFORM_ROLE_LIST
            );
            ApiEnvelope<List<PlatformRole>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                List<PlatformRole> raw = body.data();
                List<RoleSummary> summaries = new ArrayList<>(raw.size());
                Set<String> seen = new HashSet<>();
                for (PlatformRole role : raw) {
                    RoleSummary summary = toSummary(role);
                    if (summary == null) {
                        continue;
                    }
                    if (seen.add(summary.name().toLowerCase(Locale.ROOT))) {
                        summaries.add(summary);
                    }
                }
                return summaries;
            }
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "Platform directory role endpoint returned status {} body={} uri={}",
                ex.getStatusCode().value(),
                trim(ex.getResponseBodyAsString(), 256),
                uri
            );
        } catch (Exception ex) {
            LOG.debug("Platform directory role endpoint failed: {}", ex.getMessage());
            LOG.trace("Platform directory role stack", ex);
        }
        return List.of();
    }

    private List<RoleSummary> fetchRolesLegacy() {
        URI uri = buildUri(props.getApiPath(), "/keycloak/platform/roles", Collections.emptyMap());
        HttpHeaders headers = defaultHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<KeycloakRole>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, ROLE_LIST_TYPE);
            List<RoleSummary> normalized = normalizeLegacyRoles(response.getBody());
            if (!normalized.isEmpty()) {
                return normalized;
            }
        } catch (HttpStatusCodeException ex) {
            LOG.debug(
                "Legacy role endpoint returned status {} body={} uri={}",
                ex.getStatusCode().value(),
                trim(ex.getResponseBodyAsString(), 256),
                uri
            );
        } catch (Exception ex) {
            LOG.debug("Legacy role endpoint failed: {}", ex.getMessage());
            LOG.trace("Legacy role stack", ex);
        }
        try {
            ResponseEntity<ApiEnvelope<List<KeycloakRole>>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, ROLE_LIST_ENVELOPE);
            ApiEnvelope<List<KeycloakRole>> body = response.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                List<RoleSummary> normalized = normalizeLegacyRoles(body.data());
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        } catch (Exception ex) {
            LOG.debug("Legacy role envelope parsing failed: {}", ex.getMessage());
        }
        return List.of();
    }

    private List<RoleSummary> normalizeLegacyRoles(List<KeycloakRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<RoleSummary> result = new ArrayList<>(roles.size());
        Set<String> seen = new HashSet<>();
        for (KeycloakRole role : roles) {
            RoleSummary summary = toSummary(role);
            if (summary == null) {
                continue;
            }
            if (seen.add(summary.name().toLowerCase(Locale.ROOT))) {
                result.add(summary);
            }
        }
        return result;
    }

    private List<KeycloakUser> fetchUsers(String query) {
        if (query.isBlank()) {
            return fetchUsersFallback();
        }
        try {
            URI uri = buildUri(props.getApiPath(), "/keycloak/users/search", "username", query);
            return exchangeList(uri);
        } catch (Exception ex) {
            LOG.warn("Admin user search call failed: {}", ex.getMessage());
            LOG.debug("Admin user search stack", ex);
            return Collections.emptyList();
        }
    }

    private List<KeycloakUser> fetchUsersFallback() {
        try {
            URI uri = buildUri(props.getApiPath(), "/keycloak/users", Map.of("first", "0", "max", "100"));
            return exchangeList(uri);
        } catch (Exception ex) {
            LOG.warn("Admin user list fallback failed: {}", ex.getMessage());
            LOG.debug("Admin user fallback stack", ex);
            return Collections.emptyList();
        }
    }

    private List<KeycloakUser> exchangeList(URI uri) {
        HttpEntity<Void> request = new HttpEntity<>(defaultHeaders());
        try {
            ResponseEntity<List<KeycloakUser>> resp = restTemplate.exchange(uri, HttpMethod.GET, request, USER_LIST_TYPE);
            List<KeycloakUser> body = resp.getBody();
            if (body != null) {
                return body;
            }
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Admin user list call failed: status={} body={} uri={}", ex.getStatusCode().value(), trim(ex.getResponseBodyAsString(), 256), uri);
            return Collections.emptyList();
        } catch (Exception ex) {
            LOG.warn("Admin user list call failed: {}", ex.getMessage());
            LOG.debug("Admin user list stack", ex);
        }
        try {
            ResponseEntity<ApiEnvelope<List<KeycloakUser>>> resp = restTemplate.exchange(uri, HttpMethod.GET, request, USER_LIST_ENVELOPE);
            ApiEnvelope<List<KeycloakUser>> body = resp.getBody();
            if (body != null && body.isSuccess() && body.data() != null) {
                return body.data();
            }
        } catch (Exception ex) {
            LOG.debug("Admin user envelope parsing failed: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(props.getServiceToken())) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.getServiceToken());
        }
        if (StringUtils.hasText(props.getServiceName())) {
            headers.set("X-DTS-Service", props.getServiceName());
        }
        return headers;
    }

    private URI buildUri(String basePath, String path, String queryName, String queryValue) {
        Map<String, String> params = queryName == null ? Collections.emptyMap() : Map.of(queryName, queryValue);
        return buildUri(basePath, path, params);
    }

    private URI buildUri(String basePath, String path, String queryValue) {
        if (!StringUtils.hasText(queryValue)) {
            return buildUri(basePath, path, Collections.emptyMap());
        }
        return buildUri(basePath, path, Map.of("keyword", queryValue));
    }

    private URI buildUri(String basePath, String path, Map<String, String> queryParams) {
        String baseUrl = props.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "http://dts-admin:8081";
        }
        String normalized = baseUrl.replaceAll("/+$", "");
        String apiPath = basePath == null ? "" : basePath.replaceAll("/+$", "");
        String finalPath = path == null ? "" : path;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalized + apiPath + finalPath);
        if (queryParams != null) {
        queryParams.forEach((k, v) -> {
            if (k != null) {
                builder.queryParam(k, v == null ? "" : v);
            }
        });
        }
        return builder.build(true).toUri();
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private UserSummary toSummary(KeycloakUser user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return null;
        }
        String username = user.getUsername().trim();
        String displayName = firstNonBlank(user.getFullName(), combine(user.getFirstName(), user.getLastName()), username);
        String dept = firstAttribute(user.getAttributes(), "dept_code", "deptCode", "department");
        return new UserSummary(
            user.getId(),
            username,
            displayName,
            StringUtils.hasText(dept) ? dept.trim() : null
        );
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

    private RoleSummary buildRoleSummary(
        String id,
        String name,
        String description,
        String scope,
        List<String> operations,
        String source
    ) {
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

    private String normalizeRoleName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String cleaned = name.trim().replace('-', '_').replace(' ', '_');
        String upper = cleaned.toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private String firstAttribute(Map<String, List<String>> attributes, String... keys) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) continue;
            List<String> values = attributes.get(key);
            if (values != null) {
                for (String value : values) {
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public Map<String, List<String>> getAttributes() {
            return attributes == null ? Collections.emptyMap() : attributes;
        }

        public void setAttributes(Map<String, List<String>> attributes) {
            this.attributes = attributes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KeycloakRole {

        private String id;
        private String name;
        private String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiEnvelope<T>(@JsonProperty("status") String status, @JsonProperty("message") String message, @JsonProperty("data") T data) {
        public boolean isSuccess() {
            return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equals(status));
        }
    }
}
