package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
class WarehousePlanRelationshipGraphInboundIndexLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260731_01_relationship_graph_dependency_index.xml";
    private static final String DEPENDS_ON_INDEX =
        "idx_modeling_model_spec_depends_on_gin";
    private static final String DIMENSION_REFS_INDEX =
        "idx_modeling_model_spec_dimension_refs_gin";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s79_relationship_graph_[0-9a-f]{32}$"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("relationship_graph_index_it")
            .withUsername("relationship_graph_index_test")
            .withPassword("relationship_graph_index_test");

    private String schema;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        schema =
            "s79_relationship_graph_" +
            UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                """
                create table modeling_model_spec (
                    id uuid primary key,
                    depends_on jsonb not null default '[]'::jsonb,
                    dimension_refs jsonb not null default '[]'::jsonb
                )
                """
            );
        }
    }

    @AfterEach
    void dropIsolatedSchema() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void firstUpdateReplacesInvalidNotReadyIndexesWithValidReadyIndexes()
        throws Exception {
        createEquivalentIndexes();
        markIndexInvalidAndNotReady(DEPENDS_ON_INDEX);
        markIndexInvalidAndNotReady(DIMENSION_REFS_INDEX);

        updateChangelog();

        assertEquivalentIndexIsValidAndReady(
            DEPENDS_ON_INDEX,
            "depends_on"
        );
        assertEquivalentIndexIsValidAndReady(
            DIMENSION_REFS_INDEX,
            "dimension_refs"
        );

        rollbackAllAppliedChangeSets();

        assertThat(indexExists(DEPENDS_ON_INDEX)).isFalse();
        assertThat(indexExists(DIMENSION_REFS_INDEX)).isFalse();
    }

    @Test
    void laterUpdateRestoresIndexesThatBecameInvalidAfterChangelogWasRecorded()
        throws Exception {
        updateChangelog();
        markIndexInvalidAndNotReady(DEPENDS_ON_INDEX);
        markIndexInvalidAndNotReady(DIMENSION_REFS_INDEX);

        updateChangelog();

        assertEquivalentIndexIsValidAndReady(
            DEPENDS_ON_INDEX,
            "depends_on"
        );
        assertEquivalentIndexIsValidAndReady(
            DIMENSION_REFS_INDEX,
            "dimension_refs"
        );
    }

    @Test
    void rollbackKeepsEquivalentIndexesThatPredatedTheMigration()
        throws Exception {
        createEquivalentIndexes();

        updateChangelog();
        rollbackAllAppliedChangeSets();

        assertEquivalentIndexIsValidAndReady(
            DEPENDS_ON_INDEX,
            "depends_on"
        );
        assertEquivalentIndexIsValidAndReady(
            DIMENSION_REFS_INDEX,
            "dimension_refs"
        );
    }

    @Test
    void updateHaltsForAValidSameNameIndexWithMismatchedDefinition()
        throws Exception {
        executeInSchema(
            "create index " +
            DEPENDS_ON_INDEX +
            " on modeling_model_spec (id)"
        );

        assertThatThrownBy(this::updateChangelog)
            .hasStackTraceContaining(
                "RELATIONSHIP_GRAPH_DEPENDS_ON_GIN_INDEX_MISMATCH"
            );
        assertThat(indexDefinition(DEPENDS_ON_INDEX))
            .contains("USING btree (id)");
    }

    private void createEquivalentIndexes() throws SQLException {
        executeInSchema(
            "create index " +
            DEPENDS_ON_INDEX +
            " on modeling_model_spec using gin (depends_on jsonb_path_ops)"
        );
        executeInSchema(
            "create index " +
            DIMENSION_REFS_INDEX +
            " on modeling_model_spec using gin (dimension_refs jsonb_path_ops)"
        );
    }

    private void markIndexInvalidAndNotReady(String indexName)
        throws SQLException {
        executeInSchema(
            """
            update pg_catalog.pg_index
               set indisvalid = false,
                   indisready = false
             where indexrelid = '%s'::regclass
            """.formatted(indexName)
        );
    }

    private void assertEquivalentIndexIsValidAndReady(
        String indexName,
        String columnName
    ) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                """
                select index_state.indisvalid,
                       index_state.indisready,
                       access_method.amname,
                       operator_class.opcname,
                       pg_get_indexdef(index_relation.oid, 1, true)
                  from pg_catalog.pg_class index_relation
                  join pg_catalog.pg_index index_state
                    on index_state.indexrelid = index_relation.oid
                  join pg_catalog.pg_namespace index_schema
                    on index_schema.oid = index_relation.relnamespace
                  join pg_catalog.pg_am access_method
                    on access_method.oid = index_relation.relam
                  join pg_catalog.pg_opclass operator_class
                    on operator_class.oid = index_state.indclass[0]
                 where index_schema.nspname = current_schema()
                   and index_relation.relname = '%s'
                """.formatted(indexName)
            )
        ) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getString(3)).isEqualTo("gin");
            assertThat(result.getString(4))
                .isEqualTo("jsonb_path_ops");
            assertThat(result.getString(5)).isEqualTo(columnName);
            assertThat(result.next()).isFalse();
        }
    }

    private String indexDefinition(String indexName) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                """
                select pg_get_indexdef(index_relation.oid)
                  from pg_catalog.pg_class index_relation
                  join pg_catalog.pg_namespace index_schema
                    on index_schema.oid = index_relation.relnamespace
                 where index_schema.nspname = current_schema()
                   and index_relation.relname = '%s'
                """.formatted(indexName)
            )
        ) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                """
                select count(*)
                  from pg_catalog.pg_class index_relation
                  join pg_catalog.pg_namespace index_schema
                    on index_schema.oid = index_relation.relnamespace
                 where index_schema.nspname = current_schema()
                   and index_relation.relname = '%s'
                """.formatted(indexName)
            )
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1) == 1;
        }
    }

    private void updateChangelog() throws Exception {
        withLiquibase(liquibase ->
            liquibase.update(new Contexts(), new LabelExpression())
        );
    }

    private void rollbackAllAppliedChangeSets() throws Exception {
        withLiquibase(liquibase ->
            liquibase.rollback(100, new Contexts(), new LabelExpression())
        );
    }

    private void withLiquibase(LiquibaseAction action) throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources =
                new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(
                    new JdbcConnection(connection)
                );
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (
                    Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        database
                    )
                ) {
                    action.run(liquibase);
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void executeInSchema(String sql) throws SQLException {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement()
        ) {
            statement.execute(sql);
        }
    }

    private Connection connectionInSchema() throws SQLException {
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
        Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        connection.setAutoCommit(true);
        return connection;
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException(
                "Refusing schema operation outside the relationship-graph IT"
            );
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }
}
