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
class ModelReleaseCandidateCommandLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260724_04_model_release_candidate_command.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s69_candidate_command_[0-9a-f]{32}$");

    @Autowired
    private DataSource dataSource;

    @Test
    void upgradesRollsBackEmptyReappliesAndBlocksCommandLedgerDataLoss() throws Exception {
        String schema = "s69_candidate_command_" + UUID.randomUUID().toString().replace("-", "");
        createSchema(schema);
        try {
            createCandidatePrerequisite(schema);
            try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
                Connection connection = dataSource.getConnection();
                Database database = null;
                try {
                    connection.setAutoCommit(true);
                    setSchema(connection, schema);
                    database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                    database.setDefaultSchemaName(schema);
                    database.setLiquibaseSchemaName(schema);
                    try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                        liquibase.setChangeLogParameter("uuidType", "uuid");
                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(tableExists(schema, "modeling_model_release_candidate_command")).isTrue();
                        assertThat(constraintExists(schema, "uk_model_release_candidate_command_idempotency")).isTrue();
                        assertThat(constraintExists(schema, "uk_model_release_candidate_command_version")).isTrue();

                        liquibase.rollback(1, new Contexts(), new LabelExpression());
                        assertThat(tableExists(schema, "modeling_model_release_candidate_command")).isFalse();

                        liquibase.update(new Contexts(), new LabelExpression());
                        insertCandidateAndCommand(schema);
                        assertThatThrownBy(() ->
                            liquibase.rollback(1, new Contexts(), new LabelExpression())
                        )
                            .hasStackTraceContaining(
                                "ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_COMMAND_DATA_EXISTS"
                            );
                        assertThat(tableExists(schema, "modeling_model_release_candidate_command")).isTrue();
                        assertThat(changeSetApplied(schema)).isTrue();
                    }
                } finally {
                    try {
                        if (database != null && !database.getConnection().isClosed()) database.close();
                    } finally {
                        if (!connection.isClosed()) connection.close();
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
                    constraint uk_model_release_candidate_scope unique (tenant_id, id, plan_id)
                )
                """
            );
        }
    }

    private void insertCandidateAndCommand(String schema) throws Exception {
        String tenant = "tenant-command-it";
        UUID candidateId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            try (
                PreparedStatement candidate = connection.prepareStatement(
                    "insert into modeling_model_release_candidate (id, tenant_id, plan_id) values (?, ?, ?)"
                );
                PreparedStatement command = connection.prepareStatement(
                    """
                    insert into modeling_model_release_candidate_command (
                        id, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                        from_status, to_status, actor_id, occurred_at, reason, idempotency_key,
                        request_hash, response_snapshot
                    ) values (?, ?, ?, ?, 1, 'CREATED', null, 'DRAFT', 'owner-1', now(),
                              'create fixture', 'command-key', ?, cast(? as jsonb))
                    """
                )
            ) {
                candidate.setObject(1, candidateId);
                candidate.setString(2, tenant);
                candidate.setObject(3, planId);
                assertThat(candidate.executeUpdate()).isEqualTo(1);
                command.setObject(1, UUID.randomUUID());
                command.setString(2, tenant);
                command.setObject(3, candidateId);
                command.setObject(4, planId);
                command.setString(5, "a".repeat(64));
                command.setString(6, "{\"candidate\":{\"status\":\"DRAFT\"}}");
                assertThat(command.executeUpdate()).isEqualTo(1);
            }
            try (
                PreparedStatement update = connection.prepareStatement(
                    "update modeling_model_release_candidate_command set reason = ? where tenant_id = ?"
                );
                PreparedStatement delete = connection.prepareStatement(
                    "delete from modeling_model_release_candidate_command where tenant_id = ?"
                )
            ) {
                update.setString(1, "tampered");
                update.setString(2, tenant);
                assertThatThrownBy(update::executeUpdate)
                    .hasStackTraceContaining("MODEL_RELEASE_CANDIDATE_COMMAND_APPEND_ONLY");
                delete.setString(1, tenant);
                assertThatThrownBy(delete::executeUpdate)
                    .hasStackTraceContaining("MODEL_RELEASE_CANDIDATE_COMMAND_APPEND_ONLY");
            }
            try (Statement truncate = connection.createStatement()) {
                assertThatThrownBy(() ->
                    truncate.executeUpdate("truncate table modeling_model_release_candidate_command")
                )
                    .hasStackTraceContaining("MODEL_RELEASE_CANDIDATE_COMMAND_APPEND_ONLY");
            }
            try (
                PreparedStatement duplicateVersion = connection.prepareStatement(
                    """
                    insert into modeling_model_release_candidate_command (
                        id, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                        from_status, to_status, actor_id, occurred_at, reason, idempotency_key,
                        request_hash, response_snapshot
                    )
                    select ?, tenant_id, candidate_id, plan_id, candidate_version, event_type,
                           from_status, to_status, actor_id, occurred_at, reason, ?,
                           request_hash, response_snapshot
                      from modeling_model_release_candidate_command
                     where tenant_id = ?
                    """
                )
            ) {
                duplicateVersion.setObject(1, UUID.randomUUID());
                duplicateVersion.setString(2, "different-command-key");
                duplicateVersion.setString(3, tenant);
                assertThatThrownBy(() -> duplicateVersion.executeUpdate())
                    .hasStackTraceContaining("uk_model_release_candidate_command_version");
            }
        }
    }

    private boolean tableExists(String schema, String table) throws Exception {
        return exists(
            "select exists(select 1 from information_schema.tables where table_schema = ? and table_name = ?)",
            schema,
            table
        );
    }

    private boolean constraintExists(String schema, String constraint) throws Exception {
        return exists(
            "select exists(select 1 from information_schema.table_constraints where constraint_schema = ? and constraint_name = ?)",
            schema,
            constraint
        );
    }

    private boolean exists(String sql, String schema, String value) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, schema);
            statement.setString(2, value);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private boolean changeSetApplied(String schema) throws Exception {
        return exists(
            "select exists(select 1 from " +
            schema +
            ".databasechangelog where id = ? and author = 'codex')",
            "20260724-04-model-release-candidate-command"
        );
    }

    private boolean exists(String sql, String value) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, value);
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
