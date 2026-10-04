package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
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
class ModelReleaseCandidateLiquibaseIT {

    private static final String CHANGELOG = "config/liquibase/changelog/20260724_03_model_release_candidate.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s69_candidate_[0-9a-f]{32}$");

    @Autowired
    private DataSource dataSource;

    @Test
    void upgradesExistingSchemaRollsBackEmptyReappliesAndFailsClosedWithCandidateData() throws Exception {
        String schema = "s69_candidate_" + UUID.randomUUID().toString().replace("-", "");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        String checksum = checksum(modelId);
        createSchema(schema);
        try {
            createPrerequisites(schema);
            insertExistingBaseData(schema, planId, modelId, implementationId, checksum);

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

                        assertThat(tableExists(schema, "modeling_model_release_candidate")).isTrue();
                        assertThat(tableExists(schema, "modeling_model_release_candidate_entry")).isTrue();
                        assertSupportingConstraints(schema, true);
                        assertThat(changeSetApplied(schema)).isTrue();
                        assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(1);

                        liquibase.rollback(1, new Contexts(), new LabelExpression());
                        assertThat(tableExists(schema, "modeling_model_release_candidate")).isFalse();
                        assertThat(tableExists(schema, "modeling_model_release_candidate_entry")).isFalse();
                        assertSupportingConstraints(schema, false);
                        assertThat(changeSetApplied(schema)).isFalse();
                        assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(1);

                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(tableExists(schema, "modeling_model_release_candidate")).isTrue();
                        assertSupportingConstraints(schema, true);
                        assertThat(changeSetApplied(schema)).isTrue();
                        insertCandidate(schema, planId);

                        assertThatThrownBy(() ->
                            liquibase.rollback(1, new Contexts(), new LabelExpression())
                        )
                            .hasStackTraceContaining("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_DATA_EXISTS");
                        assertThat(tableExists(schema, "modeling_model_release_candidate")).isTrue();
                        assertSupportingConstraints(schema, true);
                        assertThat(changeSetApplied(schema)).isTrue();
                    }
                } finally {
                    try {
                        if (database != null && !database.getConnection().isClosed()) {
                            database.close();
                        }
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

    private void createPrerequisites(String schema) throws Exception {
        List<String> statements = List.of(
            """
            create table modeling_warehouse_plan (
                id uuid primary key,
                tenant_id varchar(128) not null,
                constraint uk_modeling_warehouse_plan_tenant_id unique (tenant_id, id)
            )
            """,
            """
            create table modeling_model_spec (
                id uuid primary key,
                tenant_id varchar(128) not null,
                plan_id uuid not null,
                implementation_mode varchar(32) not null,
                constraint uk_model_spec_tenant_id unique (tenant_id, id)
            )
            """,
            """
            create table modeling_model_spec_revision (
                id uuid primary key,
                tenant_id varchar(128) not null,
                model_spec_id uuid not null,
                revision int not null,
                content_checksum varchar(128)
            )
            """,
            """
            create table modeling_model_implementation (
                id uuid primary key,
                tenant_id varchar(128) not null,
                plan_id uuid not null,
                model_spec_id uuid not null,
                model_revision int not null,
                model_checksum varchar(128) not null,
                ownership varchar(32) not null,
                constraint uk_modeling_implementation_tenant_id unique (tenant_id, id)
            )
            """
        );
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            for (String sql : statements) statement.execute(sql);
        }
    }

    private void insertExistingBaseData(
        String schema,
        UUID planId,
        UUID modelId,
        UUID implementationId,
        String checksum
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            try (
                PreparedStatement plan = connection.prepareStatement(
                    "insert into modeling_warehouse_plan (id, tenant_id) values (?, 'existing-tenant')"
                );
                PreparedStatement model = connection.prepareStatement(
                    """
                    insert into modeling_model_spec (id, tenant_id, plan_id, implementation_mode)
                    values (?, 'existing-tenant', ?, 'DESIGNER_GENERATED')
                    """
                );
                PreparedStatement revision = connection.prepareStatement(
                    """
                    insert into modeling_model_spec_revision (
                        id, tenant_id, model_spec_id, revision, content_checksum
                    ) values (?, 'existing-tenant', ?, 1, ?)
                    """
                );
                PreparedStatement implementation = connection.prepareStatement(
                    """
                    insert into modeling_model_implementation (
                        id, tenant_id, plan_id, model_spec_id, model_revision, model_checksum, ownership
                    ) values (?, 'existing-tenant', ?, ?, 1, ?, 'DESIGNER_GENERATED')
                    """
                )
            ) {
                plan.setObject(1, planId);
                plan.executeUpdate();
                model.setObject(1, modelId);
                model.setObject(2, planId);
                model.executeUpdate();
                revision.setObject(1, UUID.randomUUID());
                revision.setObject(2, modelId);
                revision.setString(3, checksum);
                revision.executeUpdate();
                implementation.setObject(1, implementationId);
                implementation.setObject(2, planId);
                implementation.setObject(3, modelId);
                implementation.setString(4, checksum);
                implementation.executeUpdate();
            }
        }
    }

    private void insertCandidate(String schema, UUID planId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            try (
                PreparedStatement statement = connection.prepareStatement(
                    """
                    insert into modeling_model_release_candidate (
                        id, tenant_id, plan_id, environment, status, version,
                        created_by, created_date, last_modified_by, last_modified_date
                    ) values (?, 'existing-tenant', ?, 'TEST', 'DRAFT', 1,
                              'owner-1', current_timestamp, 'owner-1', current_timestamp)
                    """
                )
            ) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, planId);
                statement.executeUpdate();
            }
        }
    }

    private boolean tableExists(String schema, String table) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (
                PreparedStatement statement = connection.prepareStatement(
                    "select to_regclass(?) is not null"
                )
            ) {
                statement.setString(1, schema + "." + table);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                }
            }
        }
    }

    private boolean constraintExists(String schema, String constraint) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                      from pg_constraint c
                      join pg_namespace n on n.oid = c.connamespace
                     where n.nspname = ? and c.conname = ?
                )
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private void assertSupportingConstraints(String schema, boolean expected) throws Exception {
        assertThat(constraintExists(schema, "uk_model_spec_release_scope")).isEqualTo(expected);
        assertThat(constraintExists(schema, "uk_model_spec_revision_release_scope")).isEqualTo(expected);
        assertThat(constraintExists(schema, "uk_modeling_implementation_release_scope")).isEqualTo(expected);
    }

    private boolean changeSetApplied(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            try (
                PreparedStatement statement = connection.prepareStatement(
                    """
                    select exists (
                        select 1
                          from databasechangelog
                         where id = ? and author = ?
                    )
                    """
                )
            ) {
                statement.setString(1, "20260724-03-model-release-candidate");
                statement.setString(2, "codex");
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                }
            }
        }
    }

    private int rowCount(String schema, String table) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            setSchema(connection, schema);
            try (ResultSet result = statement.executeQuery("select count(*) from " + table)) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void dropOwnedSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private static void setSchema(Connection connection, String schema) throws Exception {
        requireOwnedSchema(schema);
        connection.setSchema(schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
    }

    private static void requireOwnedSchema(String schema) {
        if (!OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to operate on a non-owned schema");
        }
    }

    private static String checksum(UUID id) {
        String value = id.toString().replace("-", "");
        return value + value;
    }
}
