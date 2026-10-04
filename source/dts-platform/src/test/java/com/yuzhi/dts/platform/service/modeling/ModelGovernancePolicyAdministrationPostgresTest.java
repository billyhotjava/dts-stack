package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyRevisionConflictException;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import java.sql.Connection;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelGovernancePolicyAdministrationPostgresTest {

    private static final String SCHEMA_CHANGELOG =
        "config/liquibase/changelog/20260811_01_platform_model_governance_policy.xml";
    private static final String SEED_CHANGELOG =
        "config/liquibase/changelog/20260811_02_seed_platform_model_governance_policy.xml";
    private static final String ADVISORY_DEFAULT_CHANGELOG =
        "config/liquibase/changelog/20260924_01_platform_model_governance_policy_advisory_default.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_governance_policy_admin_test")
        .withUsername("model_governance_admin")
        .withPassword("model_governance_admin");

    private JdbcTemplate jdbc;
    private ModelGovernancePolicyAdministrationService policies;

    @BeforeEach
    void resetDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        jdbc.execute(
            "drop table if exists modeling_platform_governance_policy, modeling_model_release_candidate, " +
            "databasechangelog, databasechangeloglock"
        );
        policies = new ModelGovernancePolicyAdministrationService(jdbc);
    }

    @Test
    void untouchedBlockingSeedBecomesAdvisoryAndStaysASystemDefault() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(SEED_CHANGELOG);
        assertThat(gate()).isEqualTo("BLOCKING");

        apply(ADVISORY_DEFAULT_CHANGELOG);

        var view = policies.current();
        assertThat(view.qualityGate()).isEqualTo(QualityGate.ADVISORY);
        assertThat(view.revision()).isEqualTo(1);
        assertThat(view.systemDefault()).isTrue();
    }

    @Test
    void blockingChosenByAPersonIsKept() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(SEED_CHANGELOG);
        jdbc.update(
            "update modeling_platform_governance_policy set revision = 2, last_modified_by = 'alice' " +
            "where policy_key = 'PLATFORM_DEFAULT'"
        );

        apply(ADVISORY_DEFAULT_CHANGELOG);

        assertThat(gate()).isEqualTo("BLOCKING");
        assertThat(policies.current().systemDefault()).isFalse();
    }

    @Test
    void missingPolicyRowIsCreatedAsAdvisory() throws Exception {
        apply(SCHEMA_CHANGELOG);

        apply(ADVISORY_DEFAULT_CHANGELOG);

        assertThat(gate()).isEqualTo("ADVISORY");
        assertThat(jdbc.queryForObject("select count(*) from modeling_platform_governance_policy", Long.class))
            .isEqualTo(1L);
    }

    @Test
    void rollingBackTheDefaultNeverRestoresBlocking() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(SEED_CHANGELOG);
        apply(ADVISORY_DEFAULT_CHANGELOG);

        rollback(ADVISORY_DEFAULT_CHANGELOG);

        assertThat(gate()).isEqualTo("ADVISORY");
    }

    @Test
    void manualSwitchBumpsTheRevisionAndRecordsTheActor() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(ADVISORY_DEFAULT_CHANGELOG);

        var switched = policies.update(QualityGate.BLOCKING, 1, "sysadmin");

        assertThat(switched.qualityGate()).isEqualTo(QualityGate.BLOCKING);
        assertThat(switched.revision()).isEqualTo(2);
        assertThat(switched.lastModifiedBy()).isEqualTo("sysadmin");
        assertThat(switched.systemDefault()).isFalse();
        assertThat(new JdbcModelGovernancePolicyAdapter(jdbc).resolve().qualityGate()).isEqualTo(QualityGate.BLOCKING);
    }

    @Test
    void staleRevisionIsRejectedWithoutChangingThePolicy() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(ADVISORY_DEFAULT_CHANGELOG);
        policies.update(QualityGate.BLOCKING, 1, "first-admin");

        assertThatThrownBy(() -> policies.update(QualityGate.ADVISORY, 1, "second-admin"))
            .isInstanceOf(PolicyRevisionConflictException.class);

        assertThat(gate()).isEqualTo("BLOCKING");
        assertThat(policies.current().revision()).isEqualTo(2);
    }

    @Test
    void impactSeparatesCandidatesStillToBeCheckedFromFrozenOnes() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(ADVISORY_DEFAULT_CHANGELOG);
        jdbc.execute("create table modeling_model_release_candidate (id serial primary key, status varchar(32) not null)");
        jdbc.execute(
            "insert into modeling_model_release_candidate (status) values " +
            "('BUILDING'), ('BUILT'), ('QUALITY_RUNNING'), ('QUALITY_FAILED'), " +
            "('QUALITY_PASSED'), ('REVIEW_PENDING'), ('APPROVED'), ('PUBLISHING'), " +
            "('PUBLISHED'), ('CANCELLED'), ('ROLLED_BACK')"
        );

        var impact = policies.impact();

        assertThat(impact.unfrozenCandidates()).isEqualTo(4L);
        assertThat(impact.frozenCandidates()).isEqualTo(4L);
    }

    private String gate() {
        return jdbc.queryForObject(
            "select quality_gate from modeling_platform_governance_policy where policy_key = 'PLATFORM_DEFAULT'",
            String.class
        );
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void apply(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
    }

    private static void rollback(String changelog) throws Exception {
        withLiquibase(changelog, liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
    }

    private static void withLiquibase(String changelog, LiquibaseAction action) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    action.run(liquibase);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private static Connection connection() throws SQLException {
        Connection connection = java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        connection.setAutoCommit(true);
        return connection;
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }
}
