package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ModelReleaseCandidateActivePlanLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260724_06_model_release_candidate_active_plan.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s69_active_plan_[0-9a-f]{32}$");

    @Autowired
    private DataSource dataSource;

    @Test
    void haltsOnDuplicateActiveRowsAndProtectsRollbackBeforeReapply() throws Exception {
        String schema = "s69_active_plan_" + UUID.randomUUID().toString().replace("-", "");
        createSchema(schema);
        try {
            createCandidatePrerequisite(schema);
            UUID planId = UUID.randomUUID();
            insertCandidate(schema, "tenant-a", planId, "DRAFT");
            insertCandidate(schema, "tenant-a", planId, "BUILDING");

            try (
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
                Connection connection = dataSource.getConnection()
            ) {
                connection.setAutoCommit(true);
                setSchema(connection, schema);
                Database database = null;
                try {
                    database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                    database.setDefaultSchemaName(schema);
                    database.setLiquibaseSchemaName(schema);
                    try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                        assertThatThrownBy(() ->
                            liquibase.update(new Contexts(), new LabelExpression())
                        )
                            .hasStackTraceContaining(
                                "Duplicate active release candidates must be resolved"
                            );
                        assertThat(indexExists(schema)).isFalse();

                        deleteOneCandidate(schema);
                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(indexExists(schema)).isTrue();

                        assertThatThrownBy(() ->
                            liquibase.rollback(1, new Contexts(), new LabelExpression())
                        )
                            .hasStackTraceContaining(
                                "ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_DATA_EXISTS"
                            );
                        assertThat(indexExists(schema)).isTrue();

                        deleteAllCandidates(schema);
                        liquibase.rollback(1, new Contexts(), new LabelExpression());
                        assertThat(indexExists(schema)).isFalse();

                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(indexExists(schema)).isTrue();
                    }
                } finally {
                    if (database != null && !database.getConnection().isClosed()) {
                        database.close();
                    }
                }
            }
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private void createSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
        }
    }

    private void createCandidatePrerequisite(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            statement.execute(
                """
                create table modeling_model_release_candidate (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    plan_id uuid not null,
                    status varchar(32) not null
                )
                """
            );
        }
    }

    private void insertCandidate(String schema, String tenant, UUID planId, String status) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                "insert into " +
                schema +
                ".modeling_model_release_candidate (id, tenant_id, plan_id, status) values (?, ?, ?, ?)"
            )
        ) {
            connection.setAutoCommit(true);
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, tenant);
            statement.setObject(3, planId);
            statement.setString(4, status);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void deleteOneCandidate(String schema) throws Exception {
        executeOwned(
            schema,
            "delete from " +
            schema +
            ".modeling_model_release_candidate where id in " +
            "(select id from " +
            schema +
            ".modeling_model_release_candidate limit 1)"
        );
    }

    private void deleteAllCandidates(String schema) throws Exception {
        executeOwned(schema, "delete from " + schema + ".modeling_model_release_candidate");
    }

    private void executeOwned(String schema, String sql) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.executeUpdate(sql);
        }
    }

    private boolean indexExists(String schema) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                "select exists(select 1 from pg_indexes where schemaname = ? and indexname = ?)"
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, "uk_model_release_candidate_active_plan");
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void setSchema(Connection connection, String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
    }

    private void dropOwnedSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private static void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("refusing non-owned schema: " + schema);
        }
    }
}
