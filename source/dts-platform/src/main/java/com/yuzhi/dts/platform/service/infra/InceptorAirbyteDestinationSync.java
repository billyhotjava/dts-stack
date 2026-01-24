package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient.AdminDataLakeConfig;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.service.infra.event.InceptorDataSourcePublishedEvent;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InceptorAirbyteDestinationSync {

    private static final Logger log = LoggerFactory.getLogger(InceptorAirbyteDestinationSync.class);
    private static final List<String> DESTINATION_KEYWORDS = List.of("hive", "jdbc");
    private static final List<String> DESTINATION_BLOCKLIST = List.of(
        "vector",
        "milvus",
        "weaviate",
        "pinecone",
        "qdrant",
        "chroma",
        "embedding"
    );

    private final InceptorDataSourceRegistry registry;
    private final IngestionServiceClient ingestionClient;
    private final AdminInfraClient adminInfraClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public InceptorAirbyteDestinationSync(
        InceptorDataSourceRegistry registry,
        IngestionServiceClient ingestionClient,
        AdminInfraClient adminInfraClient,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.ingestionClient = ingestionClient;
        this.adminInfraClient = adminInfraClient;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handlePublished(InceptorDataSourcePublishedEvent event) {
        syncDefaultDestination("publish");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncDefaultDestination("startup");
    }

    @Scheduled(
        initialDelayString = "${dts.ingestion.default-destination-sync-initial-delay-ms:15000}",
        fixedDelayString = "${dts.ingestion.default-destination-sync-delay-ms:60000}"
    )
    public void scheduledSync() {
        syncDefaultDestination("scheduled");
    }

    private void syncDefaultDestination(String reason) {
        if (!ingestionClient.isEnabled()) {
            return;
        }
        if (!syncing.compareAndSet(false, true)) {
            return;
        }
        try {
            DestinationProfile profile = resolveProfile();
            if (profile == null || profile.config().isEmpty()) {
                log.info("Skip Airbyte default destination sync (no active data lake). reason={}", reason);
                return;
            }
            String destinationDefinitionId = profile.definitionId();
            if (!StringUtils.hasText(destinationDefinitionId)) {
                destinationDefinitionId = resolveDestinationDefinitionId();
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            if (StringUtils.hasText(destinationDefinitionId)) {
                payload.put("destinationDefinitionId", destinationDefinitionId);
            }
            if (StringUtils.hasText(profile.name())) {
                payload.put("destinationName", profile.name());
            }
            payload.put("destinationConfig", profile.config());
            payload.put("resetDestinationId", Boolean.TRUE);
            String signature = buildSignature(payload);
            if (StringUtils.hasText(signature) && signature.equals(lastSignature.get())) {
                return;
            }
            ApiResponse<Map<String, Object>> resp = ingestionClient.updateDefaultDestination(payload);
            if (resp == null || resp.getStatus() != 200) {
                log.warn(
                    "Failed to sync Airbyte default destination (status={}, message={})",
                    resp == null ? null : resp.getStatus(),
                    resp == null ? null : resp.getMessage()
                );
                return;
            }
            if (StringUtils.hasText(signature)) {
                lastSignature.set(signature);
            }
            log.info("Synced Airbyte default destination for data lake. reason={}", reason);
        } finally {
            syncing.set(false);
        }
    }

    private String resolveDestinationDefinitionId() {
        try {
            ApiResponse<List<Map<String, Object>>> resp = ingestionClient.listDestinationDefinitions();
            if (resp == null || resp.getStatus() != 200 || resp.getData() == null) {
                return null;
            }
            List<Map<String, Object>> defs = resp.getData();
            return findDefinitionIdByKeywords(defs, DESTINATION_KEYWORDS);
        } catch (Exception ex) {
            log.debug("Failed to resolve destination definition id: {}", ex.getMessage());
        }
        return null;
    }

    private String findDefinitionIdByKeywords(List<Map<String, Object>> defs, List<String> keywords) {
        if (defs == null || defs.isEmpty()) {
            return null;
        }
        List<String> normalized = keywords == null ? List.of() : keywords.stream().filter(StringUtils::hasText).toList();
        for (String keyword : normalized) {
            String repoHint = "destination-" + keyword.toLowerCase(Locale.ROOT);
            for (Map<String, Object> def : defs) {
                String id = stringVal(def.get("destinationDefinitionId"));
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                String hay = buildDefinitionHaystack(def);
                if (isBlockedDefinition(hay)) {
                    continue;
                }
                String repo = normalize(def.get("dockerRepository")).toLowerCase(Locale.ROOT);
                if (repo.contains(repoHint)) {
                    return id;
                }
            }
        }
        for (String keyword : normalized) {
            for (Map<String, Object> def : defs) {
                String id = stringVal(def.get("destinationDefinitionId"));
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                String hay = buildDefinitionHaystack(def);
                if (isBlockedDefinition(hay)) {
                    continue;
                }
                if (hay.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return id;
                }
            }
        }
        return null;
    }

    private String buildDefinitionHaystack(Map<String, Object> def) {
        String name = normalize(def.get("name"));
        String repo = normalize(def.get("dockerRepository"));
        return (name + " " + repo).toLowerCase(Locale.ROOT);
    }

    private boolean isBlockedDefinition(String haystack) {
        if (!StringUtils.hasText(haystack)) {
            return false;
        }
        return DESTINATION_BLOCKLIST.stream().anyMatch(haystack::contains);
    }

    private Map<String, Object> buildDestinationConfig(InceptorDataSourceState state) {
        Map<String, Object> config = new LinkedHashMap<>();
        String jdbcUrl = StringUtils.hasText(state.customJdbcUrl()) ? state.customJdbcUrl() : state.jdbcUrl();
        putIfText(config, "jdbc_url", jdbcUrl);
        putIfText(config, "username", state.loginPrincipal());
        putIfText(config, "password", state.password());
        putIfText(config, "database", state.database());
        putIfText(config, "schema", state.database());
        if (state.useSsl()) {
            config.put("ssl", true);
        }
        if (state.jdbcProperties() != null && !state.jdbcProperties().isEmpty()) {
            config.put("jdbc_properties", state.jdbcProperties());
        }
        return config;
    }

    private Map<String, Object> buildDestinationConfig(AdminDataLakeConfig config) {
        if (config == null) {
            return Map.of();
        }
        if (config.getDestinationConfig() != null && !config.getDestinationConfig().isEmpty()) {
            return new LinkedHashMap<>(config.getDestinationConfig());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfText(payload, "jdbc_url", config.getJdbcUrl());
        putIfText(payload, "username", config.getUsername());
        putIfText(payload, "password", config.getPassword());
        String database = extractDatabase(config.getJdbcUrl());
        putIfText(payload, "database", database);
        putIfText(payload, "schema", database);
        if (config.getJdbcProperties() != null && !config.getJdbcProperties().isEmpty()) {
            payload.put("jdbc_properties", config.getJdbcProperties());
        }
        return payload;
    }

    private String resolveDestinationName(InceptorDataSourceState state) {
        if (StringUtils.hasText(state.name())) {
            return state.name();
        }
        return "dts-ods-destination";
    }

    private DestinationProfile resolveProfile() {
        AdminDataLakeConfig lake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (lake == null) {
            Optional<InceptorDataSourceState> optional = registry.getActive();
            if (optional.isPresent()) {
                InceptorDataSourceState state = optional.orElseThrow();
                return new DestinationProfile(resolveDestinationName(state), null, buildDestinationConfig(state));
            }
            return null;
        }
        String name = StringUtils.hasText(lake.getDestinationName()) ? lake.getDestinationName() : lake.getName();
        return new DestinationProfile(name, lake.getDestinationDefinitionId(), buildDestinationConfig(lake));
    }

    private String extractDatabase(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        int scheme = jdbcUrl.indexOf("://");
        int start = scheme > -1 ? jdbcUrl.indexOf("/", scheme + 3) : jdbcUrl.indexOf("/");
        if (start < 0 || start + 1 >= jdbcUrl.length()) {
            return null;
        }
        String tail = jdbcUrl.substring(start + 1);
        int cut = tail.indexOf("?");
        if (cut < 0) {
            cut = tail.indexOf(";");
        }
        if (cut > -1) {
            tail = tail.substring(0, cut);
        }
        String trimmed = tail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildSignature(Map<String, Object> payload) {
        try {
            Map<String, Object> copy = new LinkedHashMap<>(payload);
            copy.remove("resetDestinationId");
            return objectMapper.writeValueAsString(copy);
        } catch (Exception ex) {
            return null;
        }
    }

    private void putIfText(Map<String, Object> config, String key, String value) {
        if (StringUtils.hasText(value)) {
            config.put(key, value.trim());
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String stringVal(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private record DestinationProfile(String name, String definitionId, Map<String, Object> config) {}
}
