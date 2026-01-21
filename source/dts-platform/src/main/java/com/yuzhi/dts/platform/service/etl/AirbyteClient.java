package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.AirbyteProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AirbyteClient {

    private static final Logger LOG = LoggerFactory.getLogger(AirbyteClient.class);

    private final RestTemplate restTemplate;
    private final AirbyteProperties properties;

    public AirbyteClient(RestTemplateBuilder builder, AirbyteProperties properties) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.properties = properties;
    }

    public Optional<Map<String, Object>> listSourceDefinitions() {
        return post("/source_definitions/list", Map.of());
    }

    public Optional<Map<String, Object>> listWorkspaces() {
        return post("/workspaces/list", Map.of());
    }

    public Optional<Map<String, Object>> createWorkspace(String name) {
        String workspaceName = StringUtils.hasText(name) ? name.trim() : "dts-platform";
        Map<String, Object> payload = Map.of(
            "name",
            workspaceName,
            "anonymousDataCollection",
            Boolean.TRUE
        );
        return post("/workspaces/create", payload);
    }

    public Optional<Map<String, Object>> listSources(String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            return Optional.empty();
        }
        return post("/sources/list", Map.of("workspaceId", workspaceId));
    }

    public Optional<Map<String, Object>> listDestinationDefinitions() {
        return post("/destination_definitions/list", Map.of());
    }

    public Optional<Map<String, Object>> listConnections(String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            return Optional.empty();
        }
        return post("/connections/list", Map.of("workspaceId", workspaceId));
    }

    public Optional<Map<String, Object>> createSource(String workspaceId, String sourceDefinitionId, String name, Map<String, Object> config) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(sourceDefinitionId)) {
            return Optional.empty();
        }
        Map<String, Object> payload = Map.of(
            "workspaceId",
            workspaceId,
            "sourceDefinitionId",
            sourceDefinitionId,
            "name",
            name == null ? "source" : name,
            "connectionConfiguration",
            config
        );
        return post("/sources/create", payload);
    }

    public Optional<Map<String, Object>> updateSource(String sourceId, String sourceDefinitionId, String name, Map<String, Object> config) {
        if (!StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        Map<String, Object> payload = Map.of(
            "sourceId",
            sourceId,
            "sourceDefinitionId",
            sourceDefinitionId,
            "name",
            name == null ? "source" : name,
            "connectionConfiguration",
            config == null ? Map.of() : config
        );
        return post("/sources/update", payload);
    }

    public Optional<Map<String, Object>> checkSourceConnection(String sourceId) {
        if (!StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        return post("/sources/check_connection", Map.of("sourceId", sourceId));
    }

    public Optional<Map<String, Object>> discoverSchema(String sourceId) {
        if (!StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        return post("/sources/discover_schema", Map.of("sourceId", sourceId));
    }

    public Optional<Map<String, Object>> createDestination(
        String workspaceId,
        String destinationDefinitionId,
        String name,
        Map<String, Object> config
    ) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(destinationDefinitionId)) {
            return Optional.empty();
        }
        Map<String, Object> payload = Map.of(
            "workspaceId",
            workspaceId,
            "destinationDefinitionId",
            destinationDefinitionId,
            "name",
            name == null ? "destination" : name,
            "connectionConfiguration",
            config
        );
        return post("/destinations/create", payload);
    }

    public Optional<Map<String, Object>> createConnection(Map<String, Object> payload) {
        return post("/connections/create", payload);
    }

    public Optional<Map<String, Object>> updateConnection(Map<String, Object> payload) {
        return post("/connections/update", payload);
    }

    public Optional<Map<String, Object>> syncConnection(String connectionId) {
        if (!StringUtils.hasText(connectionId)) {
            return Optional.empty();
        }
        return post("/connections/sync", Map.of("connectionId", connectionId));
    }

    public Optional<Map<String, Object>> listJobs(String connectionId, int limit) {
        if (!StringUtils.hasText(connectionId)) {
            return Optional.empty();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Map<String, Object> payload = Map.of("configType", "sync", "configId", connectionId, "pagination", Map.of("pageSize", safeLimit));
        return post("/jobs/list", payload);
    }

    private Optional<Map<String, Object>> post(String path, Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        URI uri = buildUri(path);
        try {
            HttpHeaders headers = defaultHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(uri, entity, Map.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpStatusCodeException ex) {
            LOG.warn("Airbyte API {} failed status={} body={}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            LOG.warn("Airbyte API {} error: {}", path, ex.getMessage());
        }
        return Optional.empty();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getUsername())) {
            String token = properties.getUsername() + ":" + String.valueOf(properties.getPassword());
            String encoded = java.util.Base64.getEncoder().encodeToString(token.getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return headers;
    }

    private URI buildUri(String path) {
        String base = properties.getBaseUrl();
        String apiPath = properties.getApiPath();
        String normalizedApi = StringUtils.hasText(apiPath) ? apiPath.trim() : "/api/v1";
        if (!normalizedApi.startsWith("/")) {
            normalizedApi = "/" + normalizedApi;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base).path(normalizedApi).path(path);
        return builder.build(true).toUri();
    }
}
