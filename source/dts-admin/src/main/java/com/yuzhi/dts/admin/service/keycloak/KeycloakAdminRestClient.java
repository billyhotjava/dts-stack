package com.yuzhi.dts.admin.service.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.StringUtils;

@Service
@Primary
public class KeycloakAdminRestClient implements KeycloakAdminClient {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminRestClient.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final URI usersEndpoint;
    private final URI rolesEndpoint;
    private final URI groupsEndpoint;
    private final URI clientsEndpoint;

    public KeycloakAdminRestClient(
        @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}") String issuerUri,
        RestTemplateBuilder restTemplateBuilder,
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .errorHandler(new NoOpErrorHandler())
            .build();
        this.usersEndpoint = resolveUsersEndpoint(issuerUri);
        this.rolesEndpoint = resolveRolesEndpoint(issuerUri);
        this.groupsEndpoint = resolveGroupsEndpoint(issuerUri);
        this.clientsEndpoint = resolveClientsEndpoint(issuerUri);
        LOG.info("Keycloak admin endpoints: users={}, roles={}, groups={}", usersEndpoint, rolesEndpoint, groupsEndpoint);
    }

    @Override
    public List<KeycloakUserDTO> listUsers(int first, int max, String accessToken) {
        URI uri = UriComponentsBuilder
            .fromUri(usersEndpoint)
            .queryParam("first", first)
            .queryParam("max", max)
            .build(true)
            .toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            return body.stream().map(this::toUserDto).toList();
        } catch (Exception ex) {
            LOG.warn("Failed to list Keycloak users: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<KeycloakUserDTO> findByUsername(String username, String accessToken) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        URI uri = UriComponentsBuilder
            .fromUri(usersEndpoint)
            .queryParam("username", username)
            .queryParam("exact", true)
            .build(true)
            .toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            return body.stream().findFirst().map(this::toUserDto);
        } catch (Exception ex) {
            LOG.warn("Failed to search Keycloak user {}: {}", username, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public KeycloakUserDTO createUser(KeycloakUserDTO payload, String accessToken) {
        Map<String, Object> representation = toRepresentation(payload);
        ResponseEntity<String> response = exchange(usersEndpoint, HttpMethod.POST, accessToken, representation);
        if (response.getStatusCode().is2xxSuccessful()) {
            Optional<KeycloakUserDTO> created = locateCreatedUser(response, payload, accessToken);
            return created.orElseGet(() -> copyDto(payload));
        }
        throw toRuntime("创建 Keycloak 用户失败", response);
    }

    @Override
    public KeycloakUserDTO updateUser(String userId, KeycloakUserDTO payload, String accessToken) {
        URI uri = userUri(userId);
        Map<String, Object> representation = toRepresentation(payload);
        ResponseEntity<String> response = exchange(uri, HttpMethod.PUT, accessToken, representation);
        if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is2xxSuccessful()) {
            return findById(userId, accessToken).orElseGet(() -> copyDto(payload));
        }
        throw toRuntime("更新 Keycloak 用户失败", response);
    }

    @Override
    public void deleteUser(String userId, String accessToken) {
        URI uri = userUri(userId);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, null);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204 && response.getStatusCode().value() != 202) {
            throw toRuntime("删除 Keycloak 用户失败", response);
        }
    }

    @Override
    public List<KeycloakGroupDTO> listGroups(String accessToken) {
        try {
            // Need full representations so that 'path' and 'attributes' (e.g., dts_org_id) are present
            URI uri = UriComponentsBuilder.fromUri(groupsEndpoint).queryParam("briefRepresentation", false).build(true).toUri();
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            return body.stream().map(this::toGroupDto).toList();
        } catch (Exception ex) {
            LOG.warn("Failed to list Keycloak groups: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<KeycloakGroupDTO> findGroup(String groupId, String accessToken) {
        if (groupId == null || groupId.isBlank()) {
            return Optional.empty();
        }
        URI uri = groupUri(groupId);
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return Optional.of(toGroupDto(body));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            LOG.warn("Failed to fetch Keycloak group {}: {}", groupId, ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            LOG.warn("Failed to fetch Keycloak group {}: {}", groupId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public KeycloakGroupDTO createGroup(KeycloakGroupDTO payload, String parentGroupId, String accessToken) {
        URI target = parentGroupId == null || parentGroupId.isBlank() ? groupsEndpoint : groupUri(parentGroupId, "children");
        Map<String, Object> representation = toGroupRepresentation(payload);
        ResponseEntity<String> response = exchange(target, HttpMethod.POST, accessToken, representation);
        if (!response.getStatusCode().is2xxSuccessful()) {
            Optional<KeycloakGroupDTO> fallback = findGroupByAttributes(payload, accessToken)
                .or(() -> findGroupByName(payload.getName(), accessToken));
            return fallback
                .map(existing -> {
                    LOG.warn(
                        "Keycloak returned {} while creating group {}, resolved existing group {} instead",
                        response.getStatusCode(),
                        payload.getName(),
                        existing.getId()
                    );
                    return existing;
                })
                .orElseGet(() -> {
                    LOG.error(
                        "Keycloak failed to create group {}: status={} body={}",
                        payload.getName(),
                        response.getStatusCode(),
                        truncate(response.getBody())
                    );
                    KeycloakGroupDTO phantom = copyGroup(payload);
                    phantom.setId(null);
                    return phantom;
                });
        }
        return findGroupByLocation(response, accessToken)
            .or(() -> findGroupByAttributes(payload, accessToken))
            .or(() -> findGroupByName(payload.getName(), accessToken))
            .orElseGet(() -> copyGroup(payload));
    }

    @Override
    public KeycloakGroupDTO updateGroup(String groupId, KeycloakGroupDTO payload, String accessToken) {
        URI uri = groupUri(groupId);
        Map<String, Object> representation = toGroupRepresentation(payload);
        ResponseEntity<String> response = exchange(uri, HttpMethod.PUT, accessToken, representation);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw toRuntime("更新 Keycloak 组失败", response);
        }
        return findGroup(groupId, accessToken).orElseGet(() -> copyGroup(payload));
    }

    @Override
    public void moveGroup(String groupId, String groupName, String parentGroupId, String accessToken) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId 不能为空");
        }
        URI target = parentGroupId == null || parentGroupId.isBlank() ? groupsEndpoint : groupUri(parentGroupId, "children");
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("id", groupId);
        representation.put("name", groupName == null ? groupId : groupName);
        ResponseEntity<String> response = exchange(target, HttpMethod.POST, accessToken, representation);
        int status = response.getStatusCode().value();
        if (status != 201 && status != 204) {
            throw toRuntime("移动 Keycloak 组失败", response);
        }
    }

    @Override
    public void deleteGroup(String groupId, String accessToken) {
        URI uri = groupUri(groupId);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, null);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204) {
            throw toRuntime("删除 Keycloak 组失败", response);
        }
    }

    @Override
    public void resetPassword(String userId, String newPassword, boolean temporary, String accessToken) {
        URI uri = userUri(userId, "reset-password");
        Map<String, Object> payload = Map.of(
            "type",
            "password",
            "value",
            newPassword,
            "temporary",
            temporary
        );
        ResponseEntity<String> response = exchange(uri, HttpMethod.PUT, accessToken, payload);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204) {
            throw toRuntime("重置密码失败", response);
        }
    }

    @Override
    public Optional<KeycloakUserDTO> findById(String userId, String accessToken) {
        return fetchById(userId, accessToken);
    }

    private Optional<KeycloakUserDTO> fetchById(String userId, String accessToken) {
        URI uri = userUri(userId);
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return Optional.of(toUserDto(body));
        } catch (Exception ex) {
            LOG.warn("Failed to fetch Keycloak user {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<KeycloakUserDTO> locateCreatedUser(ResponseEntity<String> response, KeycloakUserDTO payload, String accessToken) {
        List<String> locations = response.getHeaders().get(HttpHeaders.LOCATION);
        if (locations != null) {
            for (String loc : locations) {
                Optional<KeycloakUserDTO> dto = findByLocation(loc, accessToken);
                if (dto.isPresent()) {
                    return dto;
                }
            }
        }
        return findByUsername(payload.getUsername(), accessToken);
    }

    private Optional<KeycloakGroupDTO> findGroupByLocation(ResponseEntity<String> response, String accessToken) {
        List<String> locations = response.getHeaders().get(HttpHeaders.LOCATION);
        if (locations != null) {
            for (String location : locations) {
                Optional<KeycloakGroupDTO> dto = findGroupByLocation(location, accessToken);
                if (dto.isPresent()) {
                    return dto;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<KeycloakGroupDTO> findGroupByLocation(String location, String accessToken) {
        try {
            URI uri = new URI(location);
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return Optional.of(toGroupDto(body));
        } catch (URISyntaxException | RestClientException | java.io.IOException ex) {
            LOG.warn("Failed to resolve created Keycloak group from location {}: {}", location, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<KeycloakGroupDTO> findGroupByName(String name, String accessToken) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        for (KeycloakGroupDTO root : listGroups(accessToken)) {
            Optional<KeycloakGroupDTO> match = findGroupByName(root, name);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<KeycloakGroupDTO> findGroupByPath(String path, String accessToken) {
        if (!StringUtils.hasText(path)) {
            return Optional.empty();
        }
        for (KeycloakGroupDTO root : listGroups(accessToken)) {
            Optional<KeycloakGroupDTO> match = findGroupByPath(root, path);
            if (match.isPresent()) return match;
        }
        return Optional.empty();
    }

    private Optional<KeycloakGroupDTO> findGroupByPath(KeycloakGroupDTO node, String path) {
        if (node == null) return Optional.empty();
        if (path.equalsIgnoreCase(node.getPath())) {
            return Optional.of(copyGroup(node));
        }
        if (node.getSubGroups() != null) {
            for (KeycloakGroupDTO child : node.getSubGroups()) {
                Optional<KeycloakGroupDTO> match = findGroupByPath(child, path);
                if (match.isPresent()) return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<KeycloakGroupDTO> listUserGroups(String userId, String accessToken) {
        if (!StringUtils.hasText(userId)) return List.of();
        URI uri = UriComponentsBuilder.fromUri(userUri(userId, "groups")).queryParam("briefRepresentation", false).build(true).toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            List<KeycloakGroupDTO> out = new ArrayList<>();
            for (Map<String, Object> it : body) {
                try {
                    out.add(toGroupDto(it));
                } catch (Exception ignore) {}
            }
            return out;
        } catch (Exception ex) {
            LOG.warn("Failed to list groups for user {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void addUserToGroup(String userId, String groupId, String accessToken) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(groupId)) return;
        URI uri = userUri(userId, "groups", groupId);
        ResponseEntity<String> response = exchange(uri, HttpMethod.PUT, accessToken, null);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204) {
            throw toRuntime("加入 Keycloak 组失败", response);
        }
    }

    @Override
    public void removeUserFromGroup(String userId, String groupId, String accessToken) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(groupId)) return;
        URI uri = userUri(userId, "groups", groupId);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, null);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204) {
            throw toRuntime("移除 Keycloak 组失败", response);
        }
    }

    private Optional<KeycloakGroupDTO> findGroupByName(KeycloakGroupDTO node, String name) {
        if (node == null) {
            return Optional.empty();
        }
        if (name.equalsIgnoreCase(node.getName())) {
            return Optional.of(copyGroup(node));
        }
        if (node.getSubGroups() != null) {
            for (KeycloakGroupDTO child : node.getSubGroups()) {
                Optional<KeycloakGroupDTO> match = findGroupByName(child, name);
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<KeycloakGroupDTO> findGroupByAttributes(KeycloakGroupDTO payload, String accessToken) {
        if (payload == null || payload.getAttributes() == null || payload.getAttributes().isEmpty()) {
            return Optional.empty();
        }
        List<String> orgIds = payload.getAttributes().get("dts_org_id");
        if (orgIds == null || orgIds.isEmpty()) {
            return Optional.empty();
        }
        String expected = null;
        for (String candidate : orgIds) {
            if (StringUtils.hasText(candidate)) {
                expected = candidate;
                break;
            }
        }
        if (!StringUtils.hasText(expected)) {
            return Optional.empty();
        }
        for (KeycloakGroupDTO root : listGroups(accessToken)) {
            Optional<KeycloakGroupDTO> match = findGroupByAttribute(root, "dts_org_id", expected);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private Optional<KeycloakGroupDTO> findGroupByAttribute(KeycloakGroupDTO node, String key, String expected) {
        if (node == null) {
            return Optional.empty();
        }
        if (attributeMatches(node, key, expected)) {
            return Optional.of(copyGroup(node));
        }
        if (node.getSubGroups() != null) {
            for (KeycloakGroupDTO child : node.getSubGroups()) {
                Optional<KeycloakGroupDTO> match = findGroupByAttribute(child, key, expected);
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    private boolean attributeMatches(KeycloakGroupDTO node, String key, String expected) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(expected) || node.getAttributes() == null) {
            return false;
        }
        List<String> values = node.getAttributes().get(key);
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && Objects.equals(expected, value)) {
                return true;
            }
        }
        return false;
    }

    private KeycloakGroupDTO copyGroup(KeycloakGroupDTO source) {
        KeycloakGroupDTO target = new KeycloakGroupDTO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setPath(source.getPath());
        target.setDescription(source.getDescription());
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        if (source.getAttributes() != null) {
            source.getAttributes().forEach((key, value) -> attrs.put(key, value == null ? List.of() : new ArrayList<>(value)));
        }
        target.setAttributes(attrs);
        if (source.getRealmRoles() != null) {
            target.setRealmRoles(new ArrayList<>(source.getRealmRoles()));
        }
        Map<String, List<String>> clientRoles = new LinkedHashMap<>();
        if (source.getClientRoles() != null) {
            source.getClientRoles().forEach((key, value) -> clientRoles.put(key, value == null ? List.of() : new ArrayList<>(value)));
        }
        target.setClientRoles(clientRoles);
        List<KeycloakGroupDTO> children = new ArrayList<>();
        if (source.getSubGroups() != null) {
            for (KeycloakGroupDTO child : source.getSubGroups()) {
                children.add(copyGroup(child));
            }
        }
        target.setSubGroups(children);
        return target;
    }

    private Optional<KeycloakUserDTO> findByLocation(String location, String accessToken) {
        try {
            URI uri = new URI(location);
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return Optional.of(toUserDto(body));
        } catch (URISyntaxException | RestClientException | java.io.IOException ex) {
            LOG.warn("Failed to resolve created Keycloak user from location {}: {}", location, ex.getMessage());
            return Optional.empty();
        }
    }

    private URI userUri(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(usersEndpoint);
        if (segments != null) {
            for (String segment : segments) {
                if (segment != null && !segment.isBlank()) {
                    builder.pathSegment(segment);
                }
            }
        }
        return builder.build(true).toUri();
    }

    private URI groupUri(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(groupsEndpoint);
        if (segments != null) {
            for (String segment : segments) {
                if (segment != null && !segment.isBlank()) {
                    builder.pathSegment(segment);
                }
            }
        }
        return builder.build(true).toUri();
    }

    private ResponseEntity<String> exchange(URI uri, HttpMethod method, String accessToken, Object payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
        if (payload != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
        try {
            if (LOG.isDebugEnabled()) {
                String bodyPreview;
                try {
                    bodyPreview = payload == null ? "<none>" : objectMapper.writeValueAsString(payload);
                } catch (Exception e) {
                    bodyPreview = String.valueOf(payload);
                }
                LOG.debug("KC ADMIN REQ method={}, uri={}, payload={}", method, uri, truncate(bodyPreview));
            }
            ResponseEntity<String> resp = restTemplate.exchange(uri, method, entity, String.class);
            int code = resp.getStatusCode().value();
            if (code >= 400) {
                String respBody = resp.getBody();
                LOG.warn("KC ADMIN RESP status={} uri={} body={}", code, uri, truncate(respBody));
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("KC ADMIN RESP status={} uri={}", code, uri);
            }
            return resp;
        } catch (HttpStatusCodeException ex) {
            String respBody = ex.getResponseBodyAsString();
            LOG.warn("KC ADMIN EX status={} uri={} body={} error={}", ex.getStatusCode().value(), uri, truncate(respBody), ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode()).headers(ex.getResponseHeaders()).body(respBody);
        }
    }

    private String truncate(String text) {
        if (text == null) return "<null>";
        String t = text.replaceAll("\n", " ");
        return t.length() > 500 ? t.substring(0, 500) + "..." : t;
    }

    private Map<String, Object> toGroupRepresentation(KeycloakGroupDTO dto) {
        Map<String, Object> rep = new LinkedHashMap<>();
        if (dto.getName() != null) {
            rep.put("name", dto.getName());
        }
        rep.put("description", dto.getDescription() == null ? "" : dto.getDescription());
        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
            Map<String, List<String>> attributes = new LinkedHashMap<>();
            dto.getAttributes().forEach((key, value) -> {
                if (key != null) {
                    attributes.put(key, value == null ? List.of() : new ArrayList<>(value));
                }
            });
            if (!attributes.isEmpty()) {
                rep.put("attributes", attributes);
            }
        }
        return rep;
    }

    private Map<String, Object> toRepresentation(KeycloakUserDTO dto) {
        Map<String, Object> rep = new LinkedHashMap<>();
        if (dto.getUsername() != null) rep.put("username", dto.getUsername());
        if (dto.getEmail() != null) rep.put("email", dto.getEmail());
        if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
            rep.put("firstName", dto.getFullName());
        } else {
            if (dto.getFirstName() != null) rep.put("firstName", dto.getFirstName());
            if (dto.getLastName() != null) rep.put("lastName", dto.getLastName());
        }
        if (dto.getEnabled() != null) rep.put("enabled", dto.getEnabled());
        if (dto.getEmailVerified() != null) rep.put("emailVerified", dto.getEmailVerified());
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
            dto
                .getAttributes()
                .forEach((key, value) -> {
                    if (key != null) {
                        attributes.put(key, value == null ? List.of() : new ArrayList<>(value));
                    }
                });
        }
        if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
            attributes.put("fullname", List.of(dto.getFullName()));
        }
        if (!attributes.isEmpty()) rep.put("attributes", attributes);
        // Important: Do NOT include role/group assignments in the user representation payload.
        // Keycloak expects realm/client role mappings and group memberships to be managed via dedicated endpoints
        // (role-mappings and group membership APIs). Including these fields here can cause 4xx/5xx errors during
        // create/update in some Keycloak versions and configurations.

        return rep;
    }

    @SuppressWarnings("unchecked")
    private KeycloakGroupDTO toGroupDto(Map<String, Object> map) {
        KeycloakGroupDTO dto = new KeycloakGroupDTO();
        dto.setId(stringValue(map.get("id")));
        dto.setName(stringValue(map.get("name")));
        dto.setPath(stringValue(map.get("path")));
        dto.setDescription(stringValue(map.get("description")));
        dto.setAttributes(stringListMap(map.get("attributes")));
        dto.setRealmRoles(stringList(map.get("realmRoles")));
        dto.setClientRoles(stringListMap(map.get("clientRoles")));
        Object subGroups = map.get("subGroups");
        if (subGroups instanceof Collection<?> collection) {
            List<KeycloakGroupDTO> children = new ArrayList<>();
            for (Object child : collection) {
                if (child instanceof Map<?, ?> childMap) {
                    children.add(toGroupDto((Map<String, Object>) childMap));
                }
            }
            dto.setSubGroups(children);
        }
        return dto;
    }

    private KeycloakUserDTO toUserDto(Map<String, Object> map) {
        KeycloakUserDTO dto = new KeycloakUserDTO();
        dto.setId(stringValue(map.get("id")));
        dto.setUsername(stringValue(map.get("username")));
        dto.setEmail(stringValue(map.get("email")));
        String first = stringValue(map.get("firstName"));
        String last = stringValue(map.get("lastName"));
        dto.setFirstName(first);
        dto.setLastName(last);
        dto.setFullName(stringValue(map.get("fullName")));
        if ((dto.getFullName() == null || dto.getFullName().isBlank()) && (first != null || last != null)) {
            StringBuilder sb = new StringBuilder();
            if (first != null) sb.append(first);
            if (last != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(last);
            }
            dto.setFullName(sb.length() > 0 ? sb.toString() : first);
        }
        dto.setEnabled(booleanValue(map.get("enabled")));
        dto.setEmailVerified(booleanValue(map.get("emailVerified")));
        dto.setCreatedTimestamp(longValue(map.get("createdTimestamp")));
        dto.setAttributes(stringListMap(map.get("attributes")));
        dto.setGroups(stringList(map.get("groups")));
        dto.setRealmRoles(stringList(map.get("realmRoles")));
        dto.setClientRoles(stringListMap(map.get("clientRoles")));
        return dto;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> stringListMap(Object value) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                if (key == null) continue;
                result.put(key, stringList(entry.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) list.add(item.toString());
            }
        } else if (value != null) {
            list.add(value.toString());
        }
        return list;
    }

    @Override
    public List<KeycloakRoleDTO> listRealmRoles(String accessToken) {
        try {
            ResponseEntity<String> response = exchange(rolesEndpoint, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            return body.stream().map(this::toRoleDto).toList();
        } catch (Exception ex) {
            LOG.warn("Failed to list Keycloak roles: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<KeycloakRoleDTO> findRealmRole(String roleName, String accessToken) {
        if (roleName == null || roleName.isBlank()) {
            return Optional.empty();
        }
        // roleName may contain non-ASCII (e.g., Chinese). Build unencoded and encode at the end.
        URI uri = UriComponentsBuilder.fromUri(rolesEndpoint).pathSegment(roleName).build().encode().toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return Optional.of(toRoleDto(body));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            LOG.warn("Failed to fetch Keycloak role {}: {}", roleName, ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            LOG.warn("Failed to fetch Keycloak role {}: {}", roleName, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public KeycloakRoleDTO upsertRealmRole(KeycloakRoleDTO role, String accessToken) {
        if (role == null || role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        Map<String, Object> representation = toRoleRepresentation(role);
        Optional<KeycloakRoleDTO> existing = findRealmRole(role.getName(), accessToken);
        ResponseEntity<String> response;
        if (existing.isPresent()) {
            URI uri = UriComponentsBuilder.fromUri(rolesEndpoint).pathSegment(role.getName()).build().encode().toUri();
            response = exchange(uri, HttpMethod.PUT, accessToken, representation);
        } else {
            response = exchange(rolesEndpoint, HttpMethod.POST, accessToken, representation);
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw toRuntime("同步 Keycloak 角色失败", response);
        }
        return findRealmRole(role.getName(), accessToken).orElse(role);
    }

    @Override
    public void addRealmRolesToUser(String userId, List<String> roleNames, String accessToken) {
        if (userId == null || userId.isBlank() || roleNames == null || roleNames.isEmpty()) {
            return;
        }
        // Ensure roles exist prior to assignment to avoid Keycloak 404/409
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            try {
                if (findRealmRole(name, accessToken).isEmpty()) {
                    KeycloakRoleDTO dto = new KeycloakRoleDTO();
                    dto.setName(name);
                    try {
                        upsertRealmRole(dto, accessToken);
                    } catch (RuntimeException ex) {
                        LOG.warn("Upsert realm role '{}' before assignment failed: {}", name, ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                LOG.warn("Role existence check failed for '{}': {}", name, ex.getMessage());
            }
        }
        URI uri = userUri(userId, "role-mappings", "realm");
        List<Map<String, Object>> payload = toRealmRoleRepresentations(roleNames, accessToken);
        if (payload.isEmpty()) return;
        LOG.info("KC ADMIN add roles to user: userId={}, roles={}", userId, roleNames);
        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, accessToken, payload);
        int status = response.getStatusCode().value();
        if (status != 200 && status != 201 && status != 204) {
            throw toRuntime("分配用户 Realm 角色失败", response);
        }
    }

    @Override
    public void removeRealmRolesFromUser(String userId, List<String> roleNames, String accessToken) {
        if (userId == null || userId.isBlank() || roleNames == null || roleNames.isEmpty()) {
            return;
        }
        URI uri = userUri(userId, "role-mappings", "realm");
        // Only include roles that actually exist to avoid 404 on removal
        List<String> existingNames = new ArrayList<>();
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            try {
                if (findRealmRole(name, accessToken).isPresent()) {
                    existingNames.add(name);
                }
            } catch (Exception ex) {
                LOG.warn("Role existence check failed for '{}' during removal: {}", name, ex.getMessage());
            }
        }
        List<Map<String, Object>> payload = toRealmRoleRepresentations(existingNames, accessToken);
        if (payload.isEmpty()) return;
        LOG.info("KC ADMIN remove roles from user: userId={}, roles={}", userId, roleNames);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, payload);
        int status = response.getStatusCode().value();
        if (status != 200 && status != 204) {
            throw toRuntime("移除用户 Realm 角色失败", response);
        }
    }

    @Override
    public List<String> listUserRealmRoles(String userId, String accessToken) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        URI uri = userUri(userId, "role-mappings", "realm");
        try {
            LOG.debug("KC ADMIN list user realm roles: userId={}", userId);
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            List<String> names = new ArrayList<>();
            for (Map<String, Object> m : body) {
                String name = stringValue(m.get("name"));
                if (name != null && !name.isBlank()) names.add(name);
            }
            return names;
        } catch (Exception ex) {
            LOG.warn("Failed to list realm roles for user {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public List<KeycloakUserDTO> listUsersByRealmRole(String roleName, String accessToken) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        URI uri = UriComponentsBuilder
            .fromUri(rolesEndpoint)
            .pathSegment(roleName, "users")
            .queryParam("briefRepresentation", true)
            .build()
            .encode()
            .toUri();
        try {
            LOG.debug("KC ADMIN list users by realm role: {}", roleName);
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> body = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            List<KeycloakUserDTO> out = new ArrayList<>();
            for (Map<String, Object> it : body) {
                try {
                    out.add(toUserDto(it));
                } catch (Exception ignore) {}
            }
            return out;
        } catch (Exception ex) {
            LOG.warn("Failed to list users by realm role {}: {}", roleName, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteRealmRole(String roleName, String accessToken) {
        if (!org.springframework.util.StringUtils.hasText(roleName)) {
            return;
        }
        URI uri = UriComponentsBuilder.fromUri(rolesEndpoint).pathSegment(roleName).build().encode().toUri();
        LOG.info("KC ADMIN delete realm role: {}", roleName);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, null);
        int status = response.getStatusCode().value();
        if (status != 200 && status != 204) {
            throw toRuntime("删除 Keycloak 角色失败", response);
        }
    }

    private List<Map<String, Object>> toRealmRoleRepresentations(List<String> roleNames, String accessToken) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            Optional<KeycloakRoleDTO> role = findRealmRole(name, accessToken);
            Map<String, Object> rep = new LinkedHashMap<>();
            rep.put("name", name);
            role.map(KeycloakRoleDTO::getId).filter(id -> id != null && !id.isBlank()).ifPresent(id -> rep.put("id", id));
            if (role.isEmpty()) {
                LOG.warn("Realm role not found in Keycloak by name='{}' when building role-mappings payload; proceeding with name only", name);
            }
            list.add(rep);
        }
        return list;
    }

    private Map<String, Object> toRoleRepresentation(KeycloakRoleDTO dto) {
        Map<String, Object> rep = new LinkedHashMap<>();
        if (dto.getName() != null) {
            rep.put("name", dto.getName());
        }
        if (dto.getDescription() != null) {
            rep.put("description", dto.getDescription());
        }
        if (dto.getComposite() != null) {
            rep.put("composite", dto.getComposite());
        }
        if (dto.getClientRole() != null) {
            rep.put("clientRole", dto.getClientRole());
        }
        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) {
            Map<String, List<String>> attributes = new LinkedHashMap<>();
            dto.getAttributes().forEach((key, value) -> {
                if (key != null && value != null) {
                    attributes.put(key, List.of(value));
                }
            });
            if (!attributes.isEmpty()) {
                rep.put("attributes", attributes);
            }
        }
        return rep;
    }

    private KeycloakRoleDTO toRoleDto(Map<String, Object> map) {
        KeycloakRoleDTO dto = new KeycloakRoleDTO();
        dto.setId(stringValue(map.get("id")));
        dto.setName(stringValue(map.get("name")));
        dto.setDescription(stringValue(map.get("description")));
        dto.setComposite(booleanValue(map.get("composite")));
        dto.setClientRole(booleanValue(map.get("clientRole")));
        dto.setContainerId(stringValue(map.get("containerId")));
        dto.setAttributes(flattenRoleAttributes(map.get("attributes")));
        return dto;
    }

    private Map<String, String> flattenRoleAttributes(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? null : entry.getKey().toString();
            if (key == null) {
                continue;
            }
            List<String> list = stringList(entry.getValue());
            if (!list.isEmpty()) {
                attributes.put(key, list.get(0));
            }
        }
        return attributes;
    }

    private KeycloakUserDTO copyDto(KeycloakUserDTO source) {
        KeycloakUserDTO dto = new KeycloakUserDTO();
        dto.setId(source.getId());
        dto.setUsername(source.getUsername());
        dto.setEmail(source.getEmail());
        dto.setFirstName(source.getFirstName());
        dto.setLastName(source.getLastName());
        dto.setEnabled(source.getEnabled());
        dto.setEmailVerified(source.getEmailVerified());
        dto.setAttributes(source.getAttributes());
        dto.setGroups(source.getGroups());
        dto.setRealmRoles(source.getRealmRoles());
        dto.setClientRoles(source.getClientRoles());
        dto.setCreatedTimestamp(source.getCreatedTimestamp());
        return dto;
    }

    private RuntimeException toRuntime(String message, ResponseEntity<String> response) {
        String body = response.getBody();
        if (body != null && !body.isBlank()) {
            return new IllegalStateException(message + ": " + body);
        }
        return new IllegalStateException(message + ": HTTP " + response.getStatusCode().value());
    }

    private static URI resolveRolesEndpoint(String issuerUri) {
        URI issuer = URI.create(issuerUri);
        String path = issuer.getPath();
        if (path == null || !path.toLowerCase(Locale.ROOT).startsWith("/realms/")) {
            throw new IllegalArgumentException("Unsupported issuer URI for Keycloak: " + issuerUri);
        }
        String realm = path.substring("/realms/".length());
        if (realm.isEmpty()) {
            throw new IllegalArgumentException("Keycloak realm cannot be resolved from issuer URI " + issuerUri);
        }
        String base = issuer.getScheme() + "://" + issuer.getAuthority() + "/admin/realms/" + realm + "/roles";
        return URI.create(base);
    }

    private static URI resolveGroupsEndpoint(String issuerUri) {
        URI issuer = URI.create(issuerUri);
        String path = issuer.getPath();
        if (path == null || !path.toLowerCase(Locale.ROOT).startsWith("/realms/")) {
            throw new IllegalArgumentException("Unsupported issuer URI for Keycloak: " + issuerUri);
        }
        String realm = path.substring("/realms/".length());
        if (realm.isEmpty()) {
            throw new IllegalArgumentException("Keycloak realm cannot be resolved from issuer URI " + issuerUri);
        }
        String base = issuer.getScheme() + "://" + issuer.getAuthority() + "/admin/realms/" + realm + "/groups";
        return URI.create(base);
    }

    private static URI resolveClientsEndpoint(String issuerUri) {
        URI issuer = URI.create(issuerUri);
        String path = issuer.getPath();
        if (path == null || !path.toLowerCase(java.util.Locale.ROOT).startsWith("/realms/")) {
            throw new IllegalArgumentException("Unsupported issuer URI for Keycloak: " + issuerUri);
        }
        String realm = path.substring("/realms/".length());
        if (realm.isEmpty()) {
            throw new IllegalArgumentException("Keycloak realm cannot be resolved from issuer URI " + issuerUri);
        }
        String base = issuer.getScheme() + "://" + issuer.getAuthority() + "/admin/realms/" + realm + "/clients";
        return URI.create(base);
    }

    // ---- Client roles helpers ----

    @Override
    public java.util.Optional<String> resolveClientUuid(String clientId, String accessToken) {
        if (clientId == null || clientId.isBlank()) return java.util.Optional.empty();
        URI uri = org.springframework.web.util.UriComponentsBuilder
            .fromUri(clientsEndpoint)
            .queryParam("clientId", clientId)
            .build()
            .toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return java.util.Optional.empty();
            }
            java.util.List<java.util.Map<String, Object>> list = objectMapper.readValue(response.getBody(), LIST_OF_MAP);
            for (var m : list) {
                String id = stringValue(m.get("id"));
                if (id != null && !id.isBlank()) return java.util.Optional.of(id);
            }
            return java.util.Optional.empty();
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }

    private URI clientRolesEndpoint(String clientUuid) {
        return org.springframework.web.util.UriComponentsBuilder.fromUri(clientsEndpoint).pathSegment(clientUuid, "roles").build().toUri();
    }

    @Override
    public java.util.Optional<KeycloakRoleDTO> findClientRole(String clientId, String roleName, String accessToken) {
        if (clientId == null || roleName == null) return java.util.Optional.empty();
        java.util.Optional<String> uuid = resolveClientUuid(clientId, accessToken);
        String clientUuid = uuid.orElse(null);
        if (clientUuid == null) return java.util.Optional.empty();
        URI uri = org.springframework.web.util.UriComponentsBuilder
            .fromUri(clientRolesEndpoint(clientUuid))
            .pathSegment(roleName)
            .build()
            .encode()
            .toUri();
        try {
            ResponseEntity<String> response = exchange(uri, HttpMethod.GET, accessToken, null);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return java.util.Optional.empty();
            }
            java.util.Map<String, Object> body = objectMapper.readValue(response.getBody(), MAP_TYPE);
            return java.util.Optional.of(toRoleDto(body));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return java.util.Optional.empty();
            return java.util.Optional.empty();
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public KeycloakRoleDTO upsertClientRole(String clientId, KeycloakRoleDTO role, String accessToken) {
        if (clientId == null || role == null || role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("clientId/roleName 不能为空");
        }
        java.util.Optional<String> uuid = resolveClientUuid(clientId, accessToken);
        String clientUuid = uuid.orElseThrow(() -> new IllegalStateException("找不到 Keycloak 客户端: " + clientId));
        java.util.Optional<KeycloakRoleDTO> existing = findClientRole(clientId, role.getName(), accessToken);
        ResponseEntity<String> response;
        if (existing.isPresent()) {
            // PUT roles-by-id/{id}
            String rid = existing.map(KeycloakRoleDTO::getId).orElseThrow(() -> new IllegalStateException("缺少角色ID: " + role.getName()));
            URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUri(clientRolesEndpoint(clientUuid))
                .path("-by-id/")
                .path(rid)
                .build()
                .toUri();
            response = exchange(uri, HttpMethod.PUT, accessToken, toRoleRepresentation(role));
        } else {
            URI uri = clientRolesEndpoint(clientUuid);
            response = exchange(uri, HttpMethod.POST, accessToken, toRoleRepresentation(role));
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw toRuntime("同步 Keycloak Client 角色失败", response);
        }
        return findClientRole(clientId, role.getName(), accessToken).orElse(role);
    }

    @Override
    public void addClientRolesToUser(String userId, String clientId, java.util.List<String> roleNames, String accessToken) {
        if (userId == null || clientId == null || roleNames == null || roleNames.isEmpty()) return;
        java.util.Optional<String> uuid = resolveClientUuid(clientId, accessToken);
        String clientUuid = uuid.orElseThrow(() -> new IllegalStateException("找不到 Keycloak 客户端: " + clientId));
        java.util.List<java.util.Map<String, Object>> payload = new java.util.ArrayList<>();
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            java.util.Optional<KeycloakRoleDTO> existing = findClientRole(clientId, name, accessToken);
            if (existing.isEmpty()) {
                KeycloakRoleDTO dto = new KeycloakRoleDTO();
                dto.setName(name);
                try { upsertClientRole(clientId, dto, accessToken); } catch (RuntimeException ignore) {}
                existing = findClientRole(clientId, name, accessToken);
            }
            java.util.Map<String, Object> rep = new java.util.LinkedHashMap<>();
            rep.put("name", name);
            existing.map(KeycloakRoleDTO::getId).filter(id -> id != null && !id.isBlank()).ifPresent(id -> rep.put("id", id));
            payload.add(rep);
        }
        if (payload.isEmpty()) return;
        URI uri = userUri(userId, "role-mappings", "clients", clientUuid);
        ResponseEntity<String> response = exchange(uri, HttpMethod.POST, accessToken, payload);
        int status = response.getStatusCode().value();
        if (status != 200 && status != 201 && status != 204) {
            throw toRuntime("分配用户 Client 角色失败", response);
        }
    }

    @Override
    public void removeClientRolesFromUser(String userId, String clientId, java.util.List<String> roleNames, String accessToken) {
        if (userId == null || clientId == null || roleNames == null || roleNames.isEmpty()) return;
        java.util.Optional<String> uuid = resolveClientUuid(clientId, accessToken);
        String clientUuid = uuid.orElseThrow(() -> new IllegalStateException("找不到 Keycloak 客户端: " + clientId));
        java.util.List<java.util.Map<String, Object>> payload = new java.util.ArrayList<>();
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            java.util.Map<String, Object> rep = new java.util.LinkedHashMap<>();
            rep.put("name", name);
            findClientRole(clientId, name, accessToken).map(KeycloakRoleDTO::getId).filter(id -> id != null && !id.isBlank()).ifPresent(id -> rep.put("id", id));
            payload.add(rep);
        }
        if (payload.isEmpty()) return;
        URI uri = userUri(userId, "role-mappings", "clients", clientUuid);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, payload);
        int status = response.getStatusCode().value();
        if (status != 200 && status != 204) {
            throw toRuntime("移除用户 Client 角色失败", response);
        }
    }
    private static URI resolveUsersEndpoint(String issuerUri) {
        URI issuer = URI.create(issuerUri);
        String path = issuer.getPath();
        if (path == null || !path.toLowerCase(Locale.ROOT).startsWith("/realms/")) {
            throw new IllegalArgumentException("Unsupported issuer URI for Keycloak: " + issuerUri);
        }
        String realm = path.substring("/realms/".length());
        if (realm.isEmpty()) {
            throw new IllegalArgumentException("Keycloak realm cannot be resolved from issuer URI " + issuerUri);
        }
        String base = issuer.getScheme() + "://" + issuer.getAuthority() + "/admin/realms/" + realm + "/users";
        return URI.create(base);
    }

    private static class NoOpErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) {}
    }
}
