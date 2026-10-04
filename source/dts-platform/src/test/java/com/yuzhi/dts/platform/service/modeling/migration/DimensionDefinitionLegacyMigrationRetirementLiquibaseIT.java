package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
class DimensionDefinitionLegacyMigrationRetirementLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_03_retire_dimension_definition_legacy_migration.xml";
    private static final String CHANGESET_ID =
        "20260801-03-retire-dimension-definition-legacy-migration";
    private static final String HEAD_CONSTRAINT = "ck_model_spec_dimension_definition_ref";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s81_dimension_migration_[0-9a-f]{32}$"
    );
    private static final UUID DIMENSION_MODEL_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000201"
    );
    private static final UUID FACT_MODEL_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000202"
    );
    private static final UUID DEFINITION_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000203"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("dimension_migration_retirement_it")
            .withUsername("dimension_migration_retirement_test")
            .withPassword("dimension_migration_retirement_test");

    @Test
    void emptyLedgersAreDroppedAndCanonicalHeadsRemainProtected() throws Exception {
        String schema = createPreMigrationSchema(null);
        try {
            assertPreMigrationStructure(schema);

            updateChangelog(schema);

            assertThat(tableExists(schema, "modeling_dimension_definition_legacy_map")).isFalse();
            assertThat(tableExists(schema, "modeling_dimension_definition_migration_batch")).isFalse();
            assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(2);
            assertThat(constraintExists(schema, HEAD_CONSTRAINT)).isTrue();
            assertThat(constraintDefinition(schema, HEAD_CONSTRAINT))
                .contains("contract_version = 2")
                .contains("model_type", "DIMENSION")
                .contains("dimension_definition_id IS NOT NULL")
                .contains("dimension_definition_revision IS NOT NULL");
            assertThat(changeSetApplied(schema)).isTrue();

            assertThatThrownBy(() ->
                executeInSchema(
                    schema,
                    "update modeling_model_spec set dimension_definition_id = null, " +
                    "dimension_definition_revision = null where id = '" + DIMENSION_MODEL_ID + "'"
                )
            ).isInstanceOf(SQLException.class);
            assertThatThrownBy(() ->
                executeInSchema(
                    schema,
                    "update modeling_model_spec set dimension_definition_id = '" + DEFINITION_ID +
                    "', dimension_definition_revision = 1 where id = '" + FACT_MODEL_ID + "'"
                )
            ).isInstanceOf(SQLException.class);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "legacy state {0} halts retirement")
    @MethodSource("blockedStates")
    void anyLegacyLedgerRowOrUnpinnedCanonicalDimensionHaltsWithoutPartialDdl(BlockedState state)
        throws Exception {
        String schema = createPreMigrationSchema(state.mutation());
        try {
            assertThatThrownBy(() -> updateChangelog(schema))
                .hasStackTraceContaining("DIMENSION_DEFINITION_LEGACY_MIGRATION_RETIREMENT_BLOCKED");

            assertThat(changeSetApplied(schema)).isFalse();
            assertThat(tableExists(schema, "modeling_dimension_definition_legacy_map")).isTrue();
            assertThat(tableExists(schema, "modeling_dimension_definition_migration_batch")).isTrue();
            assertThat(constraintExists(schema, HEAD_CONSTRAINT)).isTrue();
            assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(2);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private static Stream<BlockedState> blockedStates() {
        return Stream.of(
            new BlockedState(
                "migration batch",
                "insert into modeling_dimension_definition_migration_batch " +
                "(tenant_id, migration_batch_id, migration_checksum) values " +
                "('tenant-a', 'batch-1', '" + "a".repeat(64) + "')"
            ),
            new BlockedState(
                "legacy map",
                "with batch as (insert into modeling_dimension_definition_migration_batch " +
                "(tenant_id, migration_batch_id, migration_checksum) values " +
                "('tenant-a', 'batch-1', '" + "a".repeat(64) + "') returning *) " +
                "insert into modeling_dimension_definition_legacy_map " +
                "(tenant_id, migration_batch_id, migration_checksum) " +
                "select tenant_id, migration_batch_id, migration_checksum from batch"
            ),
            new BlockedState(
                "unpinned canonical dimension",
                "update modeling_model_spec set dimension_definition_id = null, " +
                "dimension_definition_revision = null where id = '" + DIMENSION_MODEL_ID + "'"
            ),
            new BlockedState(
                "non-canonical contract version",
                "update modeling_model_spec set contract_version = 1 where id = '" + FACT_MODEL_ID + "'"
            )
        );
    }

    private String createPreMigrationSchema(String mutation) throws Exception {
        String schema = "s81_dimension_migration_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            executeInSchema(
                schema,
                """
                create table modeling_model_spec (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    contract_version int not null,
                    model_type varchar(32) not null,
                    dimension_definition_id uuid,
                    dimension_definition_revision int
                )
                """
            );
            executeInSchema(
                schema,
                """
                create table modeling_dimension_definition_migration_batch (
                    tenant_id varchar(128) not null,
                    migration_batch_id varchar(128) not null,
                    migration_checksum varchar(64) not null,
                    primary key (tenant_id, migration_batch_id),
                    unique (tenant_id, migration_batch_id, migration_checksum)
                )
                """
            );
            executeInSchema(
                schema,
                """
                create table modeling_dimension_definition_legacy_map (
                    tenant_id varchar(128) not null,
                    migration_batch_id varchar(128) not null,
                    migration_checksum varchar(64) not null,
                    constraint fk_dimension_definition_legacy_batch
                        foreign key (tenant_id, migration_batch_id, migration_checksum)
                        references modeling_dimension_definition_migration_batch
                            (tenant_id, migration_batch_id, migration_checksum)
                )
                """
            );
            executeInSchema(
                schema,
                """
                alter table modeling_model_spec
                    add constraint ck_model_spec_dimension_definition_ref
                    check (
                        (dimension_definition_id is null and dimension_definition_revision is null)
                        or (
                            dimension_definition_id is not null
                            and dimension_definition_revision is not null
                            and model_type = 'DIMENSION'
                        )
                    )
                """
            );
            executeInSchema(
                schema,
                "insert into modeling_model_spec values ('" + DIMENSION_MODEL_ID +
                "', 'tenant-a', 2, 'DIMENSION', '" + DEFINITION_ID + "', 1)"
            );
            executeInSchema(
                schema,
                "insert into modeling_model_spec values ('" + FACT_MODEL_ID +
                "', 'tenant-a', 2, 'FACT', null, null)"
            );
            if (mutation != null) executeInSchema(schema, mutation);
            return schema;
        } catch (Exception exception) {
            dropOwnedSchema(schema);
            throw exception;
        }
    }

    private void assertPreMigrationStructure(String schema) throws SQLException {
        assertThat(tableExists(schema, "modeling_dimension_definition_legacy_map")).isTrue();
        assertThat(tableExists(schema, "modeling_dimension_definition_migration_batch")).isTrue();
        assertThat(rowCount(schema, "modeling_dimension_definition_legacy_map")).isZero();
        assertThat(rowCount(schema, "modeling_dimension_definition_migration_batch")).isZero();
        assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(2);
        assertThat(constraintExists(schema, "fk_dimension_definition_legacy_batch")).isTrue();
        assertThat(constraintExists(schema, HEAD_CONSTRAINT)).isTrue();
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
                if (!database.getConnection().isClosed()) database.close();
            }
        }
    }

    private void executeInSchema(String schema, String sql) throws SQLException {
        try (Connection connection = connectionInSchema(schema); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int rowCount(String schema, String table) throws SQLException {
        if (
            !java.util.Set
                .of(
                    "modeling_model_spec",
                    "modeling_dimension_definition_legacy_map",
                    "modeling_dimension_definition_migration_batch"
                )
                .contains(table)
        ) {
            throw new IllegalArgumentException("Unexpected test table: " + table);
        }
        try (
            Connection connection = connectionInSchema(schema);
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from " + table)
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        return objectExists(
            "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
            schema,
            table
        );
    }

    private boolean constraintExists(String schema, String constraint) throws SQLException {
        return objectExists(
            "select count(*) from information_schema.table_constraints " +
            "where constraint_schema = ? and constraint_name = ?",
            schema,
            constraint
        );
    }

    private boolean objectExists(String sql, String schema, String object) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, object);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
        }
    }

    private String constraintDefinition(String schema, String constraint) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select pg_get_constraintdef(c.oid)
                  from pg_constraint c
                  join pg_namespace n on n.oid = c.connamespace
                 where n.nspname = ? and c.conname = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private boolean changeSetApplied(String schema) throws SQLException {
        if (!tableExists(schema, "databasechangelog")) return false;
        try (
            Connection connection = connectionInSchema(schema);
            PreparedStatement statement = connection.prepareStatement(
                "select count(*) from databasechangelog where id = ?"
            )
        ) {
            statement.setString(1, CHANGESET_ID);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
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

    private void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to modify unowned schema: " + schema);
        }
    }

    private record BlockedState(String name, String mutation) {
        @Override
        public String toString() {
            return name;
        }
    }
}
