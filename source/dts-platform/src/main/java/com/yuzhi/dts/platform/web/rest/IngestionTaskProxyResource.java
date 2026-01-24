package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskProxyResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final IngestionServiceClient ingestionClient;
    private final DefaultDestinationSyncService destinationSyncService;

    public IngestionTaskProxyResource(
        IngestionServiceClient ingestionClient,
        DefaultDestinationSyncService destinationSyncService
    ) {
        this.ingestionClient = ingestionClient;
        this.destinationSyncService = destinationSyncService;
    }

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = destinationSyncService.ensureDefaultDestination();
        Map<String, Object> resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
        ApiResponse<Map<String, Object>> response = ingestionClient.createIngestionTask(resolvedPayload);
        if (response != null && response.getStatus() == 200 && response.getData() instanceof Map<?, ?> data) {
            Object connectionObj = data.get("connection");
            if (connectionObj instanceof Map<?, ?> connection) {
                Object destinationId = connection.get("destinationId");
                destinationSyncService.updateAdminDestinationIfNeeded(snapshot, destinationId != null ? destinationId.toString() : null);
            }
        }
        return response;
    }

    private Map<String, Object> applyDefaultDestinationPayload(
        Map<String, Object> payload,
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot
    ) {
        if (payload == null || snapshot == null || snapshot.isEmpty()) {
            return payload;
        }
        Object destinationObj = payload.get("destination");
        if (!(destinationObj instanceof Map<?, ?> destinationMap)) {
            return payload;
        }
        Map<String, Object> destination = new java.util.LinkedHashMap<>();
        destinationMap.forEach((key, value) -> destination.put(String.valueOf(key), value));
        Object useDefaultObj = destination.get("usePlatformDefault");
        if (Boolean.FALSE.equals(asBoolean(useDefaultObj))) {
            return payload;
        }
        boolean hasDefinition = hasText(destination.get("definitionId"));
        boolean hasConfig = destination.get("config") instanceof Map<?, ?> config && !config.isEmpty();
        if (!hasDefinition && hasText(snapshot.destinationDefinitionId())) {
            destination.put("definitionId", snapshot.destinationDefinitionId());
        }
        if (!hasConfig && snapshot.destinationConfig() != null && !snapshot.destinationConfig().isEmpty()) {
            destination.put("config", snapshot.destinationConfig());
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(payload);
        merged.put("destination", destination);
        return merged;
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }
}
