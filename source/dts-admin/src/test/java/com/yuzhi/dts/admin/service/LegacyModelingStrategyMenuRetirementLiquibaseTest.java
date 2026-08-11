package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
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
class LegacyModelingStrategyMenuRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260811-01_retire_legacy_modeling_strategy_menu.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("legacyModelingStrategyMenuRetirementTest")
        .withUsername("modeling_strategy_menu_test")
        .withPassword("modeling_strategy_menu_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "modeling_strategy_menu_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigint primary key,
                        name varchar(255) not null,
                        metadata text,
                        deleted boolean not null default false,
                        last_modified_by varchar(100),
                        last_modified_date timestamp
                    )
                    """.formatted(schema)
            );
        }
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                    insert into portal_menu (id, name, metadata, deleted, last_modified_by) values
                    (1, 'sys.nav.portal.planningSystem',
                        '{"key":"planning-system","externalLink":"/data-modeling/planning/system"}', false, 'seed'),
                    (2, 'sys.nav.portal.modelingHomeWorkspace',
                        '{"key":"modeling-home-workspace","externalLink":"/data-modeling/home/workspace"}', false, 'seed')
                    """
            );
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void retiresOnlyTheLegacyStrategyEntryAndRestoresOnlyItsOwnChange() throws Exception {
        runLiquibase(false);

        assertThat(deleted(1)).isTrue();
        assertThat(lastModifiedBy(1)).isEqualTo("20260811-retire-legacy-modeling-strategy-menu");
        assertThat(deleted(2)).isFalse();

        runLiquibase(true);

        assertThat(deleted(1)).isFalse();
        assertThat(lastModifiedBy(1)).isEqualTo("20260811-retire-legacy-modeling-strategy-menu-rollback");
        assertThat(deleted(2)).isFalse();
    }

    private void runLiquibase(boolean rollback) throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    if (rollback) {
                        liquibase.rollback(1, new Contexts(), new LabelExpression());
                    } else {
                        liquibase.update(new Contexts(), new LabelExpression());
                    }
                }
            } finally {
                if (!database.getConnection().isClosed()) database.close();
            }
        }
    }

    private boolean deleted(long id) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select deleted from portal_menu where id = " + id)
        ) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private String lastModifiedBy(long id) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select last_modified_by from portal_menu where id = " + id)
        ) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private Connection connectionInSchema() throws Exception {
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
