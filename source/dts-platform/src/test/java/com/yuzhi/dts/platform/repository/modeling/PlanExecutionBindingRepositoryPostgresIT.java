package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PlanExecutionBindingRepositoryPostgresIT {

    private static final UUID PLAN_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID BINDING_ID = UUID.fromString(
        "20000000-0000-0000-0000-000000000002"
    );
    private static final String TENANT = "tenant-a";
    private static final String TARGET = "postgres-primary";
    private static final String SCOPE_CHECKSUM = "a".repeat(64);
    private static final String CANONICAL_DAG =
        "dts_plan_20000000000000000000000000000002";
    private static final String CANONICAL_DEPLOYMENT_CHECKSUM =
        "1cbde059775a0b4a4e6172c753458f77c1cd815f4a4a71cf61aa8d59ddb07e97";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s104_binding_repair_[0-9a-f]{32}$"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("plan_execution_binding_repair_it")
            .withUsername("plan_execution_binding_test")
            .withPassword("plan_execution_binding_test");

    private String schema;
    private JdbcTemplate jdbc;
    private PlanExecutionBindingRepository bindings;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s104_binding_repair_" +
            UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            createTables(statement);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );
        bindings = new PlanExecutionBindingRepository(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void repairCanonicalizesLegacyManualBindingAndRejectsStaleOrActiveRows() {
        seedBinding(4, "dts_release_build_postgres_primary", "b".repeat(64));

        assertThat(
            repair(4)
        ).contains(new PlanExecutionBindingRepository.RepairResult(5));
        assertThat(bindingValue("dag_id")).isEqualTo(CANONICAL_DAG);
        assertThat(bindingValue("desired_deployment_checksum"))
            .isEqualTo(CANONICAL_DEPLOYMENT_CHECKSUM);
        assertThat(bindingValue("deployment_status")).isEqualTo("DEPLOYING");
        assertThat(bindingVersion()).isEqualTo(5);
        assertThat(bindingValue("desired_scope_checksum"))
            .isEqualTo(SCOPE_CHECKSUM);

        assertThat(
            repair(4)
        ).isEmpty();
        assertThat(bindingVersion()).isEqualTo(5);
        assertThat(bindingValue("dag_id")).isEqualTo(CANONICAL_DAG);

        jdbc.update(
            """
            update modeling_plan_execution_binding
               set version = 6, deployment_status = 'ACTIVE',
                   dag_id = 'dts_release_build_postgres_primary',
                   desired_deployment_checksum = ?
             where id = ?
            """,
            "c".repeat(64), BINDING_ID
        );
        UUID dispatchId = UUID.randomUUID();
        jdbc.update(
            """
            insert into modeling_operational_run_dispatch (
                id, tenant_id, binding_id, status
            ) values (?, ?, ?, 'SUBMITTED')
            """,
            dispatchId, TENANT, BINDING_ID
        );
        jdbc.update(
            """
            insert into modeling_pipeline_run (
                id, tenant_id, execution_binding_id, run_purpose,
                status, operational_active_claim_key
            ) values (?, ?, ?, 'OPERATIONAL_RUN', 'RUNNING', ?)
            """,
            UUID.randomUUID(), TENANT, BINDING_ID,
            "plan-operational-active:" + TENANT + ":" + BINDING_ID
        );

        assertThat(
            repair(6)
        ).isEmpty();
        assertThat(bindingVersion()).isEqualTo(6);
        assertThat(bindingValue("dag_id"))
            .isEqualTo("dts_release_build_postgres_primary");
        assertThat(bindingValue("desired_deployment_checksum"))
            .isEqualTo("c".repeat(64));
    }

    private void seedBinding(
        int version,
        String dagId,
        String deploymentChecksum
    ) {
        jdbc.update(
            """
            insert into modeling_plan_execution_binding (
                id, tenant_id, plan_id, environment, execution_target_key,
                version, schedule_mode, desired_scope_checksum,
                desired_deployment_checksum, deployed_checksum, dag_id,
                deployment_status
            ) values (?, ?, ?, 'PROD', ?, ?, 'MANUAL_ONLY', ?, ?, ?, ?, 'ACTIVE')
            """,
            BINDING_ID, TENANT, PLAN_ID, TARGET, version,
            SCOPE_CHECKSUM, deploymentChecksum, deploymentChecksum, dagId
        );
    }

    private Optional<PlanExecutionBindingRepository.RepairResult> repair(
        int expectedVersion
    ) {
        return transactions.execute(status ->
            bindings.requestRedeployment(
                TENANT,
                PLAN_ID,
                BINDING_ID,
                expectedVersion,
                "operator-a",
                now()
            )
        );
    }

    private String bindingValue(String column) {
        return jdbc.queryForObject(
            "select %s from modeling_plan_execution_binding where id = ?"
                .formatted(column),
            String.class,
            BINDING_ID
        );
    }

    private int bindingVersion() {
        return jdbc.queryForObject(
            "select version from modeling_plan_execution_binding where id = ?",
            Integer.class,
            BINDING_ID
        );
    }

    private static void createTables(Statement statement) throws Exception {
        statement.execute(
            """
            create table modeling_plan_execution_binding (
                id uuid primary key, tenant_id varchar(128) not null,
                plan_id uuid not null, environment varchar(32) not null,
                execution_target_key varchar(128) not null, version integer not null,
                schedule_mode varchar(32) not null,
                desired_scope_checksum varchar(64) not null,
                desired_deployment_checksum varchar(64) not null,
                deployed_checksum varchar(64), dag_id varchar(128) not null,
                deployment_status varchar(32) not null,
                last_error_code varchar(128), last_error_message text,
                last_modified_by varchar(128), last_modified_date timestamp
            )
            """
        );
        statement.execute(
            """
            create table modeling_operational_run_dispatch (
                id uuid primary key, tenant_id varchar(128) not null,
                binding_id uuid not null, status varchar(32) not null
            )
            """
        );
        statement.execute(
            """
            create table modeling_pipeline_run (
                id uuid primary key, tenant_id varchar(128) not null,
                execution_binding_id uuid not null, run_purpose varchar(32) not null,
                status varchar(32) not null, operational_active_claim_key varchar(256)
            )
            """
        );
    }

    private Connection openConnection() throws Exception {
        return java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
    }

    private static Instant now() {
        return Instant.parse("2026-09-07T01:00:00Z");
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Refusing to mutate an unowned schema");
        }
    }
}
