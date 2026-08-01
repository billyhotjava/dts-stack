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
class LegacyModelingPlanSqlModelRetirementLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_07_retire_legacy_modeling_plan_sql_model.xml";
    private static final String CHANGESET_ID = "20260801-07-retire-legacy-modeling-plan-sql-model";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s81_legacy_model_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6")
        .withDatabaseName("legacy_modeling_retirement_it")
        .withUsername("legacy_modeling_retirement_test")
        .withPassword("legacy_modeling_retirement_test");

    @Test
    void emptyLegacyStoresAreDroppedWithoutCascadingIntoCanonicalStores() throws Exception {
        String schema = createPreMigrationSchema(null);
        try {
            updateChangelog(schema);

            assertThat(tableExists(schema, "modeling_plan")).isFalse();
            assertThat(tableExists(schema, "modeling_sql_model")).isFalse();
            assertThat(tableExists(schema, "catalog_domain")).isTrue();
            assertThat(tableExists(schema, "modeling_model_spec")).isTrue();
            assertThat(changeSetApplied(schema)).isTrue();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "legacy state {0} halts retirement")
    @MethodSource("blockedStates")
    void rowsCatalogIdentitiesAndUnknownDependenciesHaltWithoutPartialDdl(BlockedState state) throws Exception {
        String schema = createPreMigrationSchema(state.mutation());
        try {
            assertThatThrownBy(() -> updateChangelog(schema))
                .hasStackTraceContaining("LEGACY_MODELING_PLAN_SQL_MODEL_RETIREMENT_BLOCKED");

            assertThat(tableExists(schema, "modeling_plan")).isTrue();
            assertThat(tableExists(schema, "modeling_sql_model")).isTrue();
            assertThat(changeSetApplied(schema)).isFalse();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private static Stream<BlockedState> blockedStates() {
        return Stream.of(
            new BlockedState(
                "legacy plan row",
                "insert into modeling_plan (id, domain_id) values " +
                "('00000000-0000-0000-0000-000000000711', '00000000-0000-0000-0000-000000000701')"
            ),
            new BlockedState(
                "legacy SQL model row",
                "insert into modeling_sql_model (id, model_spec_id) values " +
                "('00000000-0000-0000-0000-000000000712', '00000000-0000-0000-0000-000000000702')"
            ),
            new BlockedState(
                "legacy catalog identity",
                "insert into catalog_asset_tag (asset_type) values ('MODELING_PLAN')"
            ),
            new BlockedState(
                "incoming foreign key",
                "create table external_sql_model_ref " +
                "(id uuid primary key, model_id uuid references modeling_sql_model(id))"
            ),
            new BlockedState(
                "view dependency",
                "create view legacy_modeling_plan_view as select id from modeling_plan"
            )
        );
    }

    private String createPreMigrationSchema(String mutation) throws Exception {
        String schema = "s81_legacy_model_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            executeInSchema(schema, "create table catalog_domain (id uuid primary key)");
            executeInSchema(schema, "create table modeling_model_spec (id uuid primary key)");
            executeInSchema(
                schema,
                """
                create table modeling_plan (
                    id uuid primary key,
                    domain_id uuid,
                    constraint fk_modeling_plan_domain foreign key (domain_id) references catalog_domain(id)
                )
                """
            );
            executeInSchema(
                schema,
                """
                create table modeling_sql_model (
                    id uuid primary key,
                    model_spec_id uuid,
                    constraint fk_modeling_sql_model_spec foreign key (model_spec_id) references modeling_model_spec(id)
                )
                """
            );
            createCatalogIdentityTables(schema);
            executeInSchema(
                schema,
                "insert into catalog_domain values ('00000000-0000-0000-0000-000000000701')"
            );
            executeInSchema(
                schema,
                "insert into modeling_model_spec values ('00000000-0000-0000-0000-000000000702')"
            );
            if (mutation != null) executeInSchema(schema, mutation);
            return schema;
        } catch (Exception exception) {
            dropOwnedSchema(schema);
            throw exception;
        }
    }

    private void createCatalogIdentityTables(String schema) throws SQLException {
        for (
            String table : new String[] {
                "asset_grant",
                "asset_ownership",
                "asset_permission_audit",
                "asset_permission_policy_injection",
                "catalog_asset_tag",
                "catalog_classification_event",
                "catalog_classification_migration_item",
                "catalog_classification_snapshot",
                "catalog_external_asset_identity",
                "catalog_external_asset_sync_item",
            }
        ) {
            executeInSchema(schema, "create table " + table + " (asset_type varchar(64))");
        }
        executeInSchema(
            schema,
            "create table catalog_dataset_lineage " +
            "(upstream_asset_type varchar(64), downstream_asset_type varchar(64))"
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
                if (!database.getConnection().isClosed()) database.close();
            }
        }
    }

    private void executeInSchema(String schema, String sql) throws SQLException {
        try (Connection connection = connectionInSchema(schema); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = ?"
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
