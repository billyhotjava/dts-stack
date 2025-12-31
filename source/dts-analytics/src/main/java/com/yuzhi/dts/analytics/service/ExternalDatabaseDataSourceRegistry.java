package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalDatabaseDataSourceRegistry {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, DataSourceEntry> dataSources = new ConcurrentHashMap<>();

    public ExternalDatabaseDataSourceRegistry(AnalyticsDatabaseRepository databaseRepository, ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public HikariDataSource get(long databaseId) {
        AnalyticsDatabase database = databaseRepository
                .findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException("Database not found: " + databaseId));
        String fingerprint = database.getEngine() + ":" + database.getDetailsJson();

        DataSourceEntry cached = dataSources.get(databaseId);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.dataSource();
        }

        JdbcDetails jdbcDetails = parseJdbcDetails(database);
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

        HikariDataSource created = new HikariDataSource(config);
        DataSourceEntry entry = new DataSourceEntry(fingerprint, created);

        DataSourceEntry previous = dataSources.put(databaseId, entry);
        if (previous != null) {
            previous.dataSource().close();
        }

        return created;
    }

    private JdbcDetails parseJdbcDetails(AnalyticsDatabase database) {
        JsonNode details;
        try {
            details = objectMapper.readTree(database.getDetailsJson());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid database details_json for db " + database.getId(), e);
        }

        String jdbcUrl = firstText(details, "jdbc-url", "jdbc_url", "jdbcUrl", "url");
        String username = firstText(details, "user", "username");
        String password = firstText(details, "password");

        if (jdbcUrl == null && "postgres".equalsIgnoreCase(database.getEngine())) {
            String host = firstText(details, "host");
            Integer port = firstInt(details, "port").orElse(5432);
            String dbName = firstText(details, "dbname", "db", "database");
            if (host == null || dbName == null) {
                throw new IllegalArgumentException("Postgres database requires details.host and details.dbname");
            }
            jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(host, port, dbName);
        }

        if (jdbcUrl == null) {
            throw new IllegalArgumentException(
                    "Unsupported database engine or missing details.jdbc-url for engine=" + database.getEngine());
        }

        return new JdbcDetails(jdbcUrl, username, password);
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Optional<Integer> firstInt(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        for (String name : fieldNames) {
            JsonNode value = node.get(name);
            if (value == null) {
                continue;
            }
            if (value.canConvertToInt()) {
                return Optional.of(value.asInt());
            }
            if (value.isTextual()) {
                try {
                    return Optional.of(Integer.parseInt(value.asText()));
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return Optional.empty();
    }

    @PreDestroy
    public void shutdown() {
        for (DataSourceEntry entry : dataSources.values()) {
            entry.dataSource().close();
        }
        dataSources.clear();
    }

    private record JdbcDetails(String jdbcUrl, String username, String password) {}

    private record DataSourceEntry(String fingerprint, HikariDataSource dataSource) {}
}

