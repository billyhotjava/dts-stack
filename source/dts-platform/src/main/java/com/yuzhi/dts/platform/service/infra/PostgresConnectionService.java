package com.yuzhi.dts.platform.service.infra;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PostgresConnectionService {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresConnectionService.class);

    private final DataSource dataSource;
    private final DataSourceProperties dataSourceProperties;
    private final CatalogFeatureProperties catalogFeatureProperties;

    public PostgresConnectionService(
        DataSource dataSource,
        DataSourceProperties dataSourceProperties,
        CatalogFeatureProperties catalogFeatureProperties
    ) {
        this.dataSource = dataSource;
        this.dataSourceProperties = dataSourceProperties;
        this.catalogFeatureProperties = catalogFeatureProperties;
    }

    public PostgresConnectionResult verifyPlatformDatabase() {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");

            DatabaseMetaData metaData = connection.getMetaData();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            String jdbcUrl = safe(metaData.getURL());
            String username = safe(metaData.getUserName());
            String databaseProduct = safe(metaData.getDatabaseProductName());
            String databaseVersion = safe(metaData.getDatabaseProductVersion());
            String driverName = safe(metaData.getDriverName());
            String driverVersion = safe(metaData.getDriverVersion());
            String catalog = safe(connection.getCatalog());

            String configuredSchema = safe(catalogFeatureProperties.getPostgresSchema());
            String schema = configuredSchema;
            if (!StringUtils.hasText(schema)) {
                try {
                    schema = safe(connection.getSchema());
                } catch (AbstractMethodError | SQLFeatureNotSupportedException ignored) {
                    schema = null;
                }
            } else {
                try {
                    connection.setSchema(schema);
                } catch (SQLException ex) {
                    LOG.warn("Failed to set PostgreSQL schema to {}: {}", schema, ex.getMessage());
                }
            }

            schema = safe(schema);

            String password = safe(dataSourceProperties.getPassword());
            return new PostgresConnectionResult(
                jdbcUrl,
                username,
                password,
                databaseProduct,
                databaseVersion,
                driverName,
                driverVersion,
                catalog,
                schema,
                elapsed
            );
        } catch (SQLException ex) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            LOG.warn("Failed to validate platform PostgreSQL connection: {}", ex.getMessage());
            throw new IllegalStateException("无法连接平台 PostgreSQL 数据库：" + ex.getMessage(), ex);
        }
    }

    public record PostgresConnectionResult(
        String jdbcUrl,
        String username,
        String password,
        String databaseProduct,
        String databaseVersion,
        String driverName,
        String driverVersion,
        String catalog,
        String schema,
        long elapsedMillis
    ) {
        public Map<String, Object> props() {
            return Map.ofEntries(
                Map.entry("managedBy", "PLATFORM"),
                Map.entry("databaseProduct", defaultString(databaseProduct)),
                Map.entry("databaseVersion", defaultString(databaseVersion)),
                Map.entry("driverName", defaultString(driverName)),
                Map.entry("driverVersion", defaultString(driverVersion)),
                Map.entry("catalog", defaultString(catalog)),
                Map.entry("schema", defaultString(schema)),
                Map.entry("jdbcUrl", defaultString(jdbcUrl)),
                Map.entry("username", defaultString(username))
            );
        }

        public Map<String, Object> secrets() {
            if (!StringUtils.hasText(password)) {
                return Collections.emptyMap();
            }
            return Map.of("password", password);
        }
    }

    private static String safe(String value) {
        return value != null ? value.trim() : null;
    }

    private static String defaultString(String value) {
        return value != null ? value : "";
    }
}
