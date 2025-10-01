package com.yuzhi.dts.admin.service.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

@Service
@Primary
public class KeycloakAdminRestClient implements KeycloakAdminClient {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminRestClient.class);
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final URI usersEndpoint;

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
        URI uri = UriComponentsBuilder.fromUri(usersEndpoint).queryParam("username", username).build(true).toUri();
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
        URI uri = usersEndpoint.resolve("./" + userId);
        Map<String, Object> representation = toRepresentation(payload);
        ResponseEntity<String> response = exchange(uri, HttpMethod.PUT, accessToken, representation);
        if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is2xxSuccessful()) {
            return findById(userId, accessToken).orElseGet(() -> copyDto(payload));
        }
        throw toRuntime("更新 Keycloak 用户失败", response);
    }

    @Override
    public void deleteUser(String userId, String accessToken) {
        URI uri = usersEndpoint.resolve("./" + userId);
        ResponseEntity<String> response = exchange(uri, HttpMethod.DELETE, accessToken, null);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 204 && response.getStatusCode().value() != 202) {
            throw toRuntime("删除 Keycloak 用户失败", response);
        }
    }

    @Override
    public Optional<KeycloakUserDTO> findById(String userId, String accessToken) {
        return fetchById(userId, accessToken);
    }

    private Optional<KeycloakUserDTO> fetchById(String userId, String accessToken) {
        URI uri = usersEndpoint.resolve("./" + userId);
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
            return restTemplate.exchange(uri, method, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).headers(ex.getResponseHeaders()).body(ex.getResponseBodyAsString());
        }
    }

    private Map<String, Object> toRepresentation(KeycloakUserDTO dto) {
        Map<String, Object> rep = new LinkedHashMap<>();
        if (dto.getUsername() != null) rep.put("username", dto.getUsername());
        if (dto.getEmail() != null) rep.put("email", dto.getEmail());
        if (dto.getFirstName() != null) rep.put("firstName", dto.getFirstName());
        if (dto.getLastName() != null) rep.put("lastName", dto.getLastName());
        if (dto.getEnabled() != null) rep.put("enabled", dto.getEnabled());
        if (dto.getEmailVerified() != null) rep.put("emailVerified", dto.getEmailVerified());
        if (dto.getAttributes() != null && !dto.getAttributes().isEmpty()) rep.put("attributes", dto.getAttributes());
        if (dto.getRealmRoles() != null && !dto.getRealmRoles().isEmpty()) rep.put("realmRoles", dto.getRealmRoles());
        if (dto.getGroups() != null && !dto.getGroups().isEmpty()) rep.put("groups", dto.getGroups());
        if (dto.getClientRoles() != null && !dto.getClientRoles().isEmpty()) rep.put("clientRoles", dto.getClientRoles());

        return rep;
    }

    private KeycloakUserDTO toUserDto(Map<String, Object> map) {
        KeycloakUserDTO dto = new KeycloakUserDTO();
        dto.setId(stringValue(map.get("id")));
        dto.setUsername(stringValue(map.get("username")));
        dto.setEmail(stringValue(map.get("email")));
        dto.setFirstName(stringValue(map.get("firstName")));
        dto.setLastName(stringValue(map.get("lastName")));
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
