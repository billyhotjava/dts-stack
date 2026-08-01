package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IngestionRuntimeResiliencePostgresMigrationIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_05_ingestion_runtime_resilience.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("ingestion_runtime_resilience_it")
        .withUsername("ingestion_runtime_resilience_test")
        .withPassword("ingestion_runtime_resilience_test");

    @BeforeEach
    void createSchema() throws SQLException {
        execute("DROP SCHEMA public CASCADE");
        execute("CREATE SCHEMA public");
        execute("""
            CREATE TABLE ingestion_execution (
                id bigint PRIMARY KEY,
                parent_execution_id bigint,
                next_retry_at timestamp with time zone,
                status varchar(50) NOT NULL DEFAULT 'failed',
                retry_exhausted boolean NOT NULL DEFAULT false
            )
            """);
    }

    @Test
    void failedConcurrentBuildIsRemovedOnlyWhenInvalidAndRecreatedOnRetry() throws Exception {
        execute("""
            INSERT INTO ingestion_execution(id, parent_execution_id)
            VALUES (1, 41), (2, 41)
            """);
        assertThatThrownBy(() -> execute("""
            CREATE UNIQUE INDEX CONCURRENTLY uk_ingestion_execution_retry_parent
            ON ingestion_execution(parent_execution_id)
            WHERE parent_execution_id IS NOT NULL
            """))
            .isInstanceOf(SQLException.class);
        assertThat(queryLong("""
            SELECT count(*)
            FROM pg_catalog.pg_index index_state
            JOIN pg_catalog.pg_class index_relation ON index_relation.oid = index_state.indexrelid
            WHERE index_relation.relname = 'uk_ingestion_execution_retry_parent'
              AND NOT index_state.indisvalid
            """))
            .isEqualTo(1L);

        execute("DELETE FROM ingestion_execution WHERE id = 2");
        applyMigration();

        assertRetryParentIndexValid();
    }

    @Test
    void validSameNameIndexIsPreserved() throws Exception {
        execute("INSERT INTO ingestion_execution(id, parent_execution_id) VALUES (1, 41)");
        execute("""
            CREATE UNIQUE INDEX CONCURRENTLY uk_ingestion_execution_retry_parent
            ON ingestion_execution(parent_execution_id)
            WHERE parent_execution_id IS NOT NULL
            """);
        long originalOid = queryLong("""
            SELECT indexrelid::bigint
            FROM pg_catalog.pg_index
            WHERE indexrelid = 'uk_ingestion_execution_retry_parent'::regclass
            """);

        applyMigration();

        assertThat(queryLong("""
            SELECT indexrelid::bigint
            FROM pg_catalog.pg_index
            WHERE indexrelid = 'uk_ingestion_execution_retry_parent'::regclass
            """))
            .isEqualTo(originalOid);
        assertRetryParentIndexValid();
    }

    private void assertRetryParentIndexValid() throws SQLException {
        assertThat(queryLong("""
            SELECT count(*)
            FROM pg_catalog.pg_index index_state
            WHERE index_state.indexrelid = 'uk_ingestion_execution_retry_parent'::regclass
              AND index_state.indisvalid
              AND index_state.indisunique
            """))
            .isEqualTo(1L);
    }

    private void applyMigration() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        try (
            Connection connection = dataSource().getConnection();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            if (!result.next()) {
                throw new SQLException("query returned no rows");
            }
            return result.getLong(1);
        }
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
