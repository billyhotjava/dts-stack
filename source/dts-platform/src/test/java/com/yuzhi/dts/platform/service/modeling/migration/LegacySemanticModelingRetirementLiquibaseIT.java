package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LegacySemanticModelingRetirementLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_01_retire_legacy_semantic_modeling.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s81_legacy_retirement_[0-9a-f]{32}$"
    );
    private static final List<String> LEGACY_TABLES = List.of(
        "semantic_model_dimension",
        "semantic_model_metric",
        "semantic_generated_artifact",
        "semantic_model_review_log",
        "semantic_model_run",
        "semantic_object_table_mapping",
        "semantic_dimension",
        "semantic_metric",
        "semantic_model",
        "semantic_business_object",
        "semantic_subject_domain",
        "modeling_legacy_object_migration",
        "modeling_legacy_object_migration_batch",
        "modeling_legacy_api_usage",
        "modeling_model_registration_step"
    );
    private static final List<String> RETAINED_PARENT_TABLES = List.of(
        "catalog_domain",
        "modeling_model_spec",
        "modeling_model_lifecycle_event"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("legacy_semantic_retirement_it")
            .withUsername("legacy_semantic_retirement_test")
            .withPassword("legacy_semantic_retirement_test");

    @Test
    void emptyLegacyTablesAreDroppedInForeignKeySafeOrder() throws Exception {
        String schema = createLegacySchema();
        try {
            assertThat(foreignKeyCount(schema)).isEqualTo(16);

            updateChangelog(schema);

            for (String table : LEGACY_TABLES) {
                assertThat(tableExists(schema, table)).as(table).isFalse();
            }
            for (String table : RETAINED_PARENT_TABLES) {
                assertThat(tableExists(schema, table)).as(table).isTrue();
            }
            assertThat(changeSetApplied(schema)).isTrue();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "non-empty {0} halts retirement")
    @MethodSource("legacyTables")
    void anyNonEmptyLegacyTableHaltsWithoutChangingDataOrStructure(String nonEmptyTable)
        throws Exception {
        String schema = createLegacySchema();
        try {
            executeInSchema(schema, "insert into " + requireLegacyTable(nonEmptyTable) + " default values");

            assertThatThrownBy(() -> updateChangelog(schema))
                .hasStackTraceContaining("LEGACY_SEMANTIC_MODELING_DATA_REQUIRES_MIGRATION")
                .hasStackTraceContaining("LEGACY_MODELING_RETIREMENT_EVIDENCE_REQUIRES_ARCHIVE");

            for (String table : LEGACY_TABLES) {
                assertThat(tableExists(schema, table)).as(table).isTrue();
            }
            for (String table : RETAINED_PARENT_TABLES) {
                assertThat(tableExists(schema, table)).as(table).isTrue();
            }
            assertThat(rowCount(schema, nonEmptyTable)).isEqualTo(1);
            assertThat(foreignKeyCount(schema)).isEqualTo(16);
            assertThat(changeSetApplied(schema)).isFalse();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @Test
    void concurrentLegacyWriteAfterPrecheckIsCaughtByTheLockedRecheck() throws Exception {
        String schema = createLegacySchema();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection writer = connectionInSchema(schema)) {
            writer.setAutoCommit(false);
            try (Statement statement = writer.createStatement()) {
                statement.execute("insert into semantic_metric default values");
            }
            Future<?> migration = executor.submit(() -> {
                try {
                    updateChangelog(schema);
                } catch (Exception exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            });

            org.awaitility.Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> waitingForRetirementLock(schema));
            writer.commit();

            assertThatThrownBy(() -> migration.get(30, TimeUnit.SECONDS))
                .hasStackTraceContaining("LEGACY_SEMANTIC_MODELING_RETIREMENT_LOCKED_RECHECK_FAILED");
            assertThat(tableExists(schema, "semantic_metric")).isTrue();
            assertThat(rowCount(schema, "semantic_metric")).isEqualTo(1);
            assertThat(changeSetApplied(schema)).isFalse();
        } finally {
            executor.shutdownNow();
            dropOwnedSchema(schema);
        }
    }

    private static Stream<String> legacyTables() {
        return LEGACY_TABLES.stream();
    }

    private String createLegacySchema() throws Exception {
        String schema = "s81_legacy_retirement_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            for (String statement : legacySchemaStatements()) {
                executeInSchema(schema, statement);
            }
            return schema;
        } catch (Exception exception) {
            dropOwnedSchema(schema);
            throw exception;
        }
    }

    private List<String> legacySchemaStatements() {
        return List.of(
            "create table catalog_domain (id uuid primary key default gen_random_uuid())",
            "create table modeling_model_spec (id uuid primary key default gen_random_uuid())",
            "create table modeling_model_lifecycle_event (id uuid primary key default gen_random_uuid())",
            "create table semantic_subject_domain (id uuid primary key default gen_random_uuid())",
            """
            create table semantic_business_object (
                id uuid primary key default gen_random_uuid(),
                domain_id uuid references semantic_subject_domain(id)
            )
            """,
            """
            create table semantic_object_table_mapping (
                id uuid primary key default gen_random_uuid(),
                object_id uuid references semantic_business_object(id)
            )
            """,
            """
            create table semantic_dimension (
                id uuid primary key default gen_random_uuid(),
                object_id uuid references semantic_business_object(id)
            )
            """,
            """
            create table semantic_metric (
                id uuid primary key default gen_random_uuid(),
                object_id uuid references semantic_business_object(id)
            )
            """,
            """
            create table semantic_model (
                id uuid primary key default gen_random_uuid(),
                object_id uuid references semantic_business_object(id)
            )
            """,
            """
            create table semantic_generated_artifact (
                id uuid primary key default gen_random_uuid(),
                model_id uuid references semantic_model(id)
            )
            """,
            """
            create table semantic_model_dimension (
                id uuid primary key default gen_random_uuid(),
                model_id uuid references semantic_model(id),
                dimension_id uuid references semantic_dimension(id)
            )
            """,
            """
            create table semantic_model_metric (
                id uuid primary key default gen_random_uuid(),
                model_id uuid references semantic_model(id),
                metric_id uuid references semantic_metric(id)
            )
            """,
            """
            create table semantic_model_review_log (
                id uuid primary key default gen_random_uuid(),
                model_id uuid references semantic_model(id)
            )
            """,
            """
            create table semantic_model_run (
                id uuid primary key default gen_random_uuid(),
                model_id uuid references semantic_model(id)
            )
            """,
            """
            create table modeling_legacy_object_migration_batch (
                batch_id varchar(128) default gen_random_uuid()::text,
                tenant_id varchar(128) default 'tenant',
                primary key (batch_id, tenant_id)
            )
            """,
            """
            create table modeling_legacy_object_migration (
                id uuid primary key default gen_random_uuid(),
                batch_id varchar(128),
                tenant_id varchar(128),
                catalog_domain_id uuid references catalog_domain(id),
                target_model_spec_id uuid references modeling_model_spec(id),
                foreign key (batch_id, tenant_id)
                    references modeling_legacy_object_migration_batch(batch_id, tenant_id)
            )
            """,
            "create table modeling_legacy_api_usage (id uuid primary key default gen_random_uuid())",
            """
            create table modeling_model_registration_step (
                id uuid primary key default gen_random_uuid(),
                release_event_id uuid references modeling_model_lifecycle_event(id)
            )
            """
        );
    }

    private void updateChangelog(String schema) throws Exception {
        try (
            Connection connection = connectionInSchema(schema);
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void executeInSchema(String schema, String sql) throws SQLException {
        try (Connection connection = connectionInSchema(schema); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int rowCount(String schema, String table) throws SQLException {
        requireLegacyTable(table);
        try (
            Connection connection = connectionInSchema(schema);
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from " + table)
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private int foreignKeyCount(String schema) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = ? and constraint_type = 'FOREIGN KEY'
                """
            )
        ) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                from information_schema.tables
                where table_schema = ? and table_name = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
        }
    }

    private boolean waitingForRetirementLock(String schema) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                  from pg_locks lock_state
                  join pg_class locked_table on locked_table.oid = lock_state.relation
                  join pg_namespace locked_schema on locked_schema.oid = locked_table.relnamespace
                 where locked_schema.nspname = ?
                   and locked_table.relname = 'semantic_metric'
                   and lock_state.mode = 'AccessExclusiveLock'
                   and not lock_state.granted
                """
            )
        ) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) > 0;
            }
        }
    }

    private boolean changeSetApplied(String schema) throws SQLException {
        if (!tableExists(schema, "databasechangelog")) {
            return false;
        }
        try (
            Connection connection = connectionInSchema(schema);
            PreparedStatement statement = connection.prepareStatement(
                "select count(*) from databasechangelog where id = '20260801-01-retire-legacy-semantic-modeling'"
            );
            ResultSet result = statement.executeQuery()
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1) == 1;
        }
    }

    private Connection connectionInSchema(String schema) throws SQLException {
        requireOwnedSchema(schema);
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private Connection openConnection() throws SQLException {
        Connection connection = POSTGRES.createConnection("");
        connection.setAutoCommit(true);
        return connection;
    }

    private void dropOwnedSchema(String schema) throws SQLException {
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private String requireLegacyTable(String table) {
        if (!LEGACY_TABLES.contains(table)) {
            throw new IllegalArgumentException("Unexpected legacy table: " + table);
        }
        return table;
    }

    private void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to modify unowned schema: " + schema);
        }
    }
}
