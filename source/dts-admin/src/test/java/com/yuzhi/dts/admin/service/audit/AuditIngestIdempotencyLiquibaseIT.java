package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AuditIngestIdempotencyLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260731-05_audit_ingest_idempotency.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s81_audit_ingest_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("audit_ingest_idempotency_it")
            .withUsername("audit_ingest_test")
            .withPassword("audit_ingest_test");

    private String schema;

    @BeforeEach
    void createSchema() throws Exception {
        schema = "s81_audit_ingest_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                "create table " + schema + ".audit_entry (id uuid primary key, module_key varchar(64))"
            );
        }
    }

    @AfterEach
    void dropSchema() throws Exception {
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void migrationEnforcesCompleteCanonicalIdentityAndSupportsLegacyRows() throws Exception {
        update();

        insert(null, null, null);
        insert(null, null, null);
        insert("dts-platform", "event-1", "a".repeat(64));
        insert("dts-analytics", "event-1", "a".repeat(64));

        assertThatThrownBy(() -> insert("dts-platform", "event-1", "a".repeat(64)))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insert("dts-platform", "event-2", null))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insert("dts-platform", "event-3", "not-a-sha256"))
            .isInstanceOf(SQLException.class);

        assertThat(columnLength("module_key")).isEqualTo(128);
        assertThat(countRows()).isEqualTo(4);
    }

    @Test
    void uniqueIndexSerializesConcurrentFirstWriters() throws Exception {
        update();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> insert = () -> {
                try {
                    insert("dts-platform", "concurrent-event", "b".repeat(64));
                    return true;
                } catch (SQLException expectedConflict) {
                    return false;
                }
            };
            List<Future<Boolean>> outcomes = executor.invokeAll(List.of(insert, insert));

            assertThat(List.of(outcomes.get(0).get(), outcomes.get(1).get()))
                .containsExactlyInAnyOrder(true, false);
            assertThat(countByIdentity("dts-platform", "concurrent-event")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollbackRemovesOnlyIngestIdentityExtension() throws Exception {
        update();
        rollback();

        assertThat(columnExists("ingest_producer")).isFalse();
        assertThat(columnExists("ingest_event_id")).isFalse();
        assertThat(columnExists("ingest_payload_hash")).isFalse();
        assertThat(columnLength("module_key")).isEqualTo(64);
        assertThat(columnExists("id")).isTrue();
    }

    private void insert(String producer, String eventId, String hash) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "insert into audit_entry (id, module_key, ingest_producer, ingest_event_id, ingest_payload_hash) values (?, 'modeling', ?, ?, ?)"
            )
        ) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, producer);
            statement.setString(3, eventId);
            statement.setString(4, hash);
            statement.executeUpdate();
        }
    }

    private int countRows() throws SQLException {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from audit_entry")
        ) {
            result.next();
            return result.getInt(1);
        }
    }

    private int countByIdentity(String producer, String eventId) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "select count(*) from audit_entry where ingest_producer=? and ingest_event_id=?"
            )
        ) {
            statement.setString(1, producer);
            statement.setString(2, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int columnLength(String column) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "select character_maximum_length from information_schema.columns where table_schema=? and table_name='audit_entry' and column_name=?"
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private boolean columnExists(String column) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "select exists(select 1 from information_schema.columns where table_schema=? and table_name='audit_entry' and column_name=?)"
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private void update() throws Exception {
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
    }

    private void rollback() throws Exception {
        withLiquibase(liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
    }

    private void withLiquibase(LiquibaseAction action) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connectionInSchema()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    action.run(liquibase);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private Connection connectionInSchema() throws SQLException {
        Connection connection = openConnection();
        connection.setSchema(schema);
        return connection;
    }

    private Connection openConnection() throws SQLException {
        return POSTGRES.createConnection("");
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Refusing to operate on non-owned schema");
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }
}
