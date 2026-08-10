package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@IntegrationTest
class Sprint89JdbcCatalogSyncRollbackIT {

    private static final String SOURCE_TABLE = "sprint89_partial_sync";

    @Container
    private static final PostgreSQLContainer<?> SOURCE_DATABASE =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("sprint89_sync")
            .withUsername("sprint89")
            .withPassword("sprint89");

    @Autowired
    private JdbcCatalogSyncService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private InfraSecretService secretService;

    @MockBean
    private CatalogColumnSyncService columnSyncService;

    private UUID sourceId;

    @BeforeEach
    void setUp() throws Exception {
        sourceId = UUID.randomUUID();
        try (
            Connection connection = DriverManager.getConnection(
                SOURCE_DATABASE.getJdbcUrl(),
                SOURCE_DATABASE.getUsername(),
                SOURCE_DATABASE.getPassword()
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists public." + SOURCE_TABLE);
            statement.execute("create table public." + SOURCE_TABLE + " (id bigint primary key, note varchar(64))");
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        jdbcTemplate.update(
            "delete from catalog_column_schema where table_id in (select id from catalog_table_schema where dataset_id in (select id from catalog_dataset where source_id = ?))",
            sourceId
        );
        jdbcTemplate.update(
            "delete from catalog_schema_drift_event where dataset_id in (select id from catalog_dataset where source_id = ?)",
            sourceId
        );
        jdbcTemplate.update(
            "delete from catalog_table_schema where dataset_id in (select id from catalog_dataset where source_id = ?)",
            sourceId
        );
        jdbcTemplate.update("delete from catalog_dataset where source_id = ?", sourceId);
        try (
            Connection connection = DriverManager.getConnection(
                SOURCE_DATABASE.getJdbcUrl(),
                SOURCE_DATABASE.getUsername(),
                SOURCE_DATABASE.getPassword()
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop table if exists public." + SOURCE_TABLE);
        }
    }

    @Test
    void rollsBackDatasetAndTableWritesWhenColumnSynchronizationFails() {
        InfraDataSource source = source();
        when(secretService.readSecrets(source)).thenReturn(Map.of("password", SOURCE_DATABASE.getPassword()));
        when(columnSyncService.synchronizeSnapshot(any(CatalogTableSchema.class), any()))
            .thenThrow(new IllegalStateException("column-sync-failed"));

        JdbcCatalogSyncService.JdbcSyncResult result = service.synchronize(source);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.error()).contains("column-sync-failed");
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_dataset where source_id = ?",
                Long.class,
                sourceId
            )
        )
            .as("a failed source harvest must not commit a partial catalog snapshot")
            .isZero();
    }

    private InfraDataSource source() {
        InfraDataSource source = new InfraDataSource();
        source.setId(sourceId);
        source.setName("sprint89-source");
        source.setType("POSTGRESQL");
        source.setJdbcUrl(SOURCE_DATABASE.getJdbcUrl());
        source.setUsername(SOURCE_DATABASE.getUsername());
        source.setStatus("ACTIVE");
        source.setProps(
            """
            {
              "schemas":["public"],
              "tablePattern":"%s",
              "catalogCleanupStale":false
            }
            """.formatted(SOURCE_TABLE)
        );
        return source;
    }
}
