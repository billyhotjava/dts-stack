package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.config.DefaultDatabaseProperties;
import com.yuzhi.dts.copilot.analytics.config.DefaultDatabaseProperties.DatabaseEntry;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultDatabaseInitService implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDatabaseInitService.class);

    private final DefaultDatabaseProperties properties;
    private final AnalyticsDatabaseRepository databaseRepository;
    private final MetadataSyncService metadataSyncService;
    private final PlatformInfraClient platformInfraClient;
    private final ObjectMapper objectMapper;

    public DefaultDatabaseInitService(
            DefaultDatabaseProperties properties,
            AnalyticsDatabaseRepository databaseRepository,
            MetadataSyncService metadataSyncService,
            PlatformInfraClient platformInfraClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.databaseRepository = databaseRepository;
        this.metadataSyncService = metadataSyncService;
        this.platformInfraClient = platformInfraClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DatabaseEntry> entries = properties.defaultDatabases();
        if (entries.isEmpty()) {
            LOG.debug("No default databases configured");
            return;
        }

        LOG.info("Checking {} default database(s) for auto-registration", entries.size());

        Map<String, AnalyticsDatabase> existingByName = databaseRepository.findAll().stream()
                .collect(Collectors.toMap(AnalyticsDatabase::getName, db -> db, (a, b) -> a));

        for (DatabaseEntry entry : entries) {
            if (entry.name() == null || entry.name().isBlank()) {
                LOG.warn("Skipping default database entry with empty name");
                continue;
            }
            if (isSystemRuntimeDatabase(entry)) {
                AnalyticsDatabase existing = existingByName.get(entry.name());
                if (existing != null) {
                    synchronizeDatabaseRole(existing, entry);
                }
                LOG.info("Skipping system runtime database '{}' from auto-registration", entry.name());
                continue;
            }

            AnalyticsDatabase existing = existingByName.get(entry.name());
            if (existing != null) {
                synchronizeDatabaseRole(existing, entry);
                ensureCopilotDataSourceLinked(existing, entry);
                continue;
            }

            try {
                registerDatabase(entry);
            } catch (Exception e) {
                LOG.error("Failed to register default database '{}': {}", entry.name(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void registerDatabase(DatabaseEntry entry) {
        Long aiDataSourceId = createCopilotAiDataSource(entry);
        String detailsJson = buildDetailsJson(aiDataSourceId);

        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setName(entry.name());
        database.setEngine(mapEngine(entry.engine()));
        database.setDatabaseRole(resolveDatabaseRole(entry));
        database.setDetailsJson(detailsJson);
        database.setSample(false);
        database.setAutoRunQueries(true);
        database.setFullSync(true);
        database.setOnDemand(false);

        database = databaseRepository.save(database);
        LOG.info("Registered default database '{}' with id={}, linked to copilot-ai dataSourceId={}",
                entry.name(), database.getId(), aiDataSourceId);

        if (entry.autoSyncMetadata()) {
            triggerMetadataSync(database);
        }
    }

    /**
     * For existing databases that lack a copilot-ai dataSourceId link,
     * create the AI datasource and update the details_json.
     */
    private void ensureCopilotDataSourceLinked(AnalyticsDatabase database, DatabaseEntry entry) {
        if (hasDataSourceId(database.getDetailsJson())) {
            LOG.debug("Default database '{}' (id={}) already linked to copilot-ai datasource, skipping",
                    database.getName(), database.getId());
            return;
        }

        LOG.info("Default database '{}' (id={}) missing copilot-ai datasource link, creating now",
                database.getName(), database.getId());
        try {
            Long aiDataSourceId = createCopilotAiDataSource(entry);
            database.setDetailsJson(buildDetailsJson(aiDataSourceId));
            databaseRepository.save(database);
            LOG.info("Linked default database '{}' (id={}) to copilot-ai dataSourceId={}",
                    database.getName(), database.getId(), aiDataSourceId);
        } catch (Exception e) {
            LOG.error("Failed to link default database '{}' (id={}) to copilot-ai datasource: {}",
                    database.getName(), database.getId(), e.getMessage(), e);
        }
    }

    private Long createCopilotAiDataSource(DatabaseEntry entry) {
        String engine = mapEngine(entry.engine());
        String jdbcUrl = buildJdbcUrl(engine, entry);

        PlatformInfraClient.DataSourceSummary created = platformInfraClient.createDataSource(
                new PlatformInfraClient.CreateDataSourceRequest(
                        entry.name(),
                        engine,
                        jdbcUrl,
                        entry.host(),
                        entry.port(),
                        entry.db(),
                        null,
                        null,
                        entry.user(),
                        entry.password(),
                        null));

        Long aiId = parseLong(created.id());
        if (aiId == null) {
            throw new IllegalStateException(
                    "Copilot-ai returned non-numeric datasource id: " + created.id());
        }
        return aiId;
    }

    private String buildJdbcUrl(String engine, DatabaseEntry entry) {
        return switch (engine) {
            case "postgres" -> "jdbc:postgresql://%s:%d/%s".formatted(entry.host(), entry.port(), entry.db());
            case "mysql" -> "jdbc:mysql://%s:%d/%s".formatted(entry.host(), entry.port(), entry.db());
            default -> "jdbc:%s://%s:%d/%s".formatted(engine, entry.host(), entry.port(), entry.db());
        };
    }

    private boolean hasDataSourceId(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return false;
        }
        try {
            JsonNode details = objectMapper.readTree(detailsJson);
            JsonNode value = details.get("dataSourceId");
            return value != null && !value.isNull();
        } catch (IOException e) {
            return false;
        }
    }

    private String buildDetailsJson(Long aiDataSourceId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("dataSourceId", aiDataSourceId);
        return node.toString();
    }

    private static Long parseLong(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void triggerMetadataSync(AnalyticsDatabase database) {
        try {
            MetadataSyncService.SyncSummary summary = metadataSyncService.syncDatabaseSchema(database.getId());
            LOG.info(
                    "Metadata sync completed for '{}': tables(created={}, updated={}, disabled={}), fields(created={}, updated={}, disabled={})",
                    database.getName(),
                    summary.createdTables(),
                    summary.updatedTables(),
                    summary.disabledTables(),
                    summary.createdFields(),
                    summary.updatedFields(),
                    summary.disabledFields());
        } catch (Exception e) {
            LOG.warn("Metadata sync failed for default database '{}' (id={}). Will retry on next restart or manual trigger: {}",
                    database.getName(), database.getId(), e.getMessage());
        }
    }

    private void synchronizeDatabaseRole(AnalyticsDatabase database, DatabaseEntry entry) {
        AnalyticsDatabaseRole resolvedRole = resolveDatabaseRole(entry);
        if (database.getDatabaseRole() == resolvedRole) {
            return;
        }
        database.setDatabaseRole(resolvedRole);
        databaseRepository.save(database);
    }

    private static boolean isSystemRuntimeDatabase(DatabaseEntry entry) {
        return resolveDatabaseRole(entry) == AnalyticsDatabaseRole.SYSTEM_RUNTIME;
    }

    private static String mapEngine(String engine) {
        if (engine == null) {
            return "postgres";
        }
        return switch (engine.toLowerCase()) {
            case "postgresql", "pg" -> "postgres";
            default -> engine.toLowerCase();
        };
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static AnalyticsDatabaseRole resolveDatabaseRole(DatabaseEntry entry) {
        AnalyticsDatabaseRole configuredRole = parseDatabaseRole(entry.role());
        if (configuredRole != null) {
            return configuredRole;
        }
        String engine = mapEngine(entry.engine());
        String host = normalize(entry.host());
        String db = normalize(entry.db());
        if ("postgres".equals(engine)
                && ("localhost".equals(host) || "127.0.0.1".equals(host) || "copilot-postgres".equals(host))
                && ("garden".equals(db) || "copilot".equals(db))) {
            return AnalyticsDatabaseRole.SYSTEM_RUNTIME;
        }
        return AnalyticsDatabaseRole.BUSINESS_PRIMARY;
    }

    private static AnalyticsDatabaseRole parseDatabaseRole(String role) {
        String normalized = normalize(role);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return AnalyticsDatabaseRole.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
