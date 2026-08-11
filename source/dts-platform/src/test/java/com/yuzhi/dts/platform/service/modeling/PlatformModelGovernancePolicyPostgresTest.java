package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PlatformModelGovernancePolicyPostgresTest {

    private static final String SCHEMA_CHANGELOG =
        "config/liquibase/changelog/20260811_01_platform_model_governance_policy.xml";
    private static final String SEED_CHANGELOG =
        "config/liquibase/changelog/20260811_02_seed_platform_model_governance_policy.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("platform_model_governance_policy_test")
        .withUsername("model_governance_test")
        .withPassword("model_governance_test");

    @Test
    void appliesReadsConstrainsAndRollsBackThePlatformGlobalPolicy() throws Exception {
        apply(SCHEMA_CHANGELOG);
        apply(SEED_CHANGELOG);

        ModelGovernancePolicyPort.Policy policy = new JdbcModelGovernancePolicyAdapter(
            new JdbcTemplate(dataSource())
        ).resolve();
        assertThat(policy.available()).isTrue();
        assertThat(policy.standardCoverage()).isEqualTo(ModelGovernancePolicyPort.StandardCoverage.KEY_AND_MEASURE);
        assertThat(policy.qualityGate()).isEqualTo(ModelGovernancePolicyPort.QualityGate.BLOCKING);
        assertThat(queryLong("select count(*) from modeling_platform_governance_policy")).isEqualTo(1L);

        assertThatThrownBy(() ->
            execute(
                "insert into modeling_platform_governance_policy " +
                "(policy_key, standard_coverage, quality_gate, revision, created_by, last_modified_by) values " +
                "('ANOTHER_POLICY', 'KEY_AND_MEASURE', 'BLOCKING', 1, 'test', 'test')"
            )
        )
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() ->
            execute(
                "update modeling_platform_governance_policy set standard_coverage = 'UNKNOWN' " +
                "where policy_key = 'PLATFORM_DEFAULT'"
            )
        )
            .isInstanceOf(SQLException.class);

        rollback(SEED_CHANGELOG);
        assertThat(queryLong("select count(*) from modeling_platform_governance_policy")).isZero();
        rollback(SCHEMA_CHANGELOG);
        assertThat(queryLong(
            "select count(*) from information_schema.tables " +
            "where table_schema = 'public' and table_name = 'modeling_platform_governance_policy'"
        )).isZero();
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

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (
            Connection connection = connection();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }
}
