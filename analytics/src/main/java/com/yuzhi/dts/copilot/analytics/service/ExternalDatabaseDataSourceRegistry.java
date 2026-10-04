package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ExternalDatabaseDataSourceRegistry {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;
    private final JdbcDetailsResolver jdbcDetailsResolver;

    private final Map<Long, DataSourceEntry> dataSources = new ConcurrentHashMap<>();

    public ExternalDatabaseDataSourceRegistry(
            AnalyticsDatabaseRepository databaseRepository, ObjectMapper objectMapper, JdbcDetailsResolver jdbcDetailsResolver) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
        this.jdbcDetailsResolver = jdbcDetailsResolver;
    }

    public HikariDataSource get(long databaseId) {
        AnalyticsDatabase database = databaseRepository
                .findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException("Database not found: " + databaseId));
        String fingerprint = database.getEngine() + ":" + database.getDetailsJson();

        DataSourceEntry cached = dataSources.get(databaseId);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.dataSource();
        }

        JdbcDetailsResolver.JdbcDetails jdbcDetails = parseJdbcDetails(database);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcDetails.jdbcUrl());
        if (jdbcDetails.username() != null) {
            config.setUsername(jdbcDetails.username());
        }
        if (jdbcDetails.password() != null) {
            config.setPassword(jdbcDetails.password());
        }
        config.setPoolName("analytics-db-" + databaseId);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(5 * 60_000L);
        // Avoid fail-fast startup exceptions when external DB is temporarily unavailable.
        // Connection failures should surface on query execution as SQL exceptions.
        config.setInitializationFailTimeout(-1);

        HikariDataSource created = new HikariDataSource(config);
        DataSourceEntry entry = new DataSourceEntry(fingerprint, created);

        DataSourceEntry previous = dataSources.put(databaseId, entry);
        if (previous != null) {
            previous.dataSource().close();
        }

        return created;
    }

    private JdbcDetailsResolver.JdbcDetails parseJdbcDetails(AnalyticsDatabase database) {
        JsonNode details;
        try {
            details = objectMapper.readTree(database.getDetailsJson());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid database details_json for db " + database.getId(), e);
        }
        return jdbcDetailsResolver.resolve(database.getEngine(), details);
    }

    @PreDestroy
    public void shutdown() {
        for (DataSourceEntry entry : dataSources.values()) {
            entry.dataSource().close();
        }
        dataSources.clear();
    }

    private record DataSourceEntry(String fingerprint, HikariDataSource dataSource) {}
}
