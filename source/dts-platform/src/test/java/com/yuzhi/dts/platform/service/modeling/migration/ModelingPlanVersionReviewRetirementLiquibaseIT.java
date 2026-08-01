package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
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
class ModelingPlanVersionReviewRetirementLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_05_retire_modeling_plan_version_review.xml";
    private static final String CHANGESET_ID = "20260801-05-retire-modeling-plan-version-review";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s81_plan_ledger_[0-9a-f]{32}$");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID REVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6")
        .withDatabaseName("modeling_plan_ledger_retirement_it")
        .withUsername("modeling_plan_ledger_retirement_test")
        .withPassword("modeling_plan_ledger_retirement_test");

    @Test
    void emptyKnownLedgersAreDroppedWithoutCascadingToThePlan() throws Exception {
        String schema = createPreMigrationSchema(null);
        try {
            updateChangelog(schema);

            assertThat(tableExists(schema, "modeling_plan_review")).isFalse();
            assertThat(tableExists(schema, "modeling_plan_version")).isFalse();
            assertThat(tableExists(schema, "modeling_plan")).isTrue();
            assertThat(rowCount(schema, "modeling_plan")).isOne();
            assertThat(changeSetApplied(schema)).isTrue();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "legacy state {0} halts retirement")
    @MethodSource("blockedStates")
    void dataOrUnknownDependenciesHaltWithoutPartialDdl(BlockedState state) throws Exception {
        String schema = createPreMigrationSchema(state.mutation());
        try {
            assertThatThrownBy(() -> updateChangelog(schema))
                .hasStackTraceContaining("MODELING_PLAN_VERSION_REVIEW_RETIREMENT_BLOCKED");

            assertThat(tableExists(schema, "modeling_plan_review")).isTrue();
            assertThat(tableExists(schema, "modeling_plan_version")).isTrue();
            assertThat(tableExists(schema, "modeling_plan")).isTrue();
            assertThat(changeSetApplied(schema)).isFalse();
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private static Stream<BlockedState> blockedStates() {
        return Stream.of(
            new BlockedState(
                "review row",
                "insert into modeling_plan_review (id, plan_id, status) values ('" +
                REVIEW_ID + "', '" + PLAN_ID + "', 'PENDING')"
            ),
            new BlockedState(
                "version row",
                "insert into modeling_plan_version (id, plan_id, version, status) values ('" +
                VERSION_ID + "', '" + PLAN_ID + "', 'v1', 'DRAFT')"
            ),
            new BlockedState(
                "incoming foreign key",
                "create table external_review_ref (id uuid primary key, review_id uuid references modeling_plan_review(id))"
            ),
            new BlockedState(
                "unknown outgoing foreign key",
                "alter table modeling_plan_review add constraint fk_review_unknown foreign key (id) references modeling_plan(id)"
            ),
            new BlockedState(
                "view dependency",
                "create view legacy_plan_version_view as select id from modeling_plan_version"
            )
        );
    }

    private String createPreMigrationSchema(String mutation) throws Exception {
        String schema = "s81_plan_ledger_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            executeInSchema(schema, "create table modeling_plan (id uuid primary key)");
            executeInSchema(
                schema,
                """
                create table modeling_plan_version (
                    id uuid primary key,
                    plan_id uuid not null,
                    version varchar(32) not null,
                    status varchar(32) not null,
                    constraint fk_modeling_plan_version_plan foreign key (plan_id) references modeling_plan(id)
                )
                """
            );
            executeInSchema(
                schema,
                """
                create table modeling_plan_review (
                    id uuid primary key,
                    plan_id uuid not null,
                    status varchar(32) not null,
                    constraint fk_modeling_plan_review_plan foreign key (plan_id) references modeling_plan(id)
                )
                """
            );
            executeInSchema(schema, "insert into modeling_plan values ('" + PLAN_ID + "')");
            if (mutation != null) executeInSchema(schema, mutation);
            return schema;
        } catch (Exception exception) {
            dropOwnedSchema(schema);
            throw exception;
        }
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
        if (!Set.of("modeling_plan", "modeling_plan_review", "modeling_plan_version").contains(table)) {
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
