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
class PlanningLayerGroupRetirementLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810-02_retire_legacy_modeling_layer_groups.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("planningLayerGroupRetirementTest")
        .withUsername("planning_layer_group_test")
        .withPassword("planning_layer_group_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "planning_layer_group_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigint primary key,
                        name varchar(255) not null,
                        metadata text,
                        parent_id bigint,
                        deleted boolean not null default false,
                        last_modified_by varchar(100),
                        last_modified_date timestamp
                    )
                    """.formatted(schema)
            );
        }
        seedMenus();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void retiresOnlyActiveLegacyLayerGroupsAndRestoresOnlyRowsOwnedByTheBatch() throws Exception {
        runLiquibase(false);

        assertThat(deleted(1)).isTrue();
        assertThat(deleted(2)).isTrue();
        assertThat(deleted(3)).isTrue();
        assertThat(deleted(4)).isTrue();
        assertThat(deleted(5)).isTrue();
        assertThat(deleted(6)).isFalse();
        assertThat(lastModifiedBy(1)).isEqualTo("sprint87-retire-modeling-layer-groups-retired");
        assertThat(lastModifiedBy(5)).isEqualTo("sprint87-data-architecture-navigation-retired");

        runLiquibase(true);

        assertThat(deleted(1)).isFalse();
        assertThat(deleted(2)).isFalse();
        assertThat(deleted(3)).isFalse();
        assertThat(deleted(4)).isFalse();
        assertThat(deleted(5)).isTrue();
        assertThat(deleted(6)).isFalse();
        assertThat(lastModifiedBy(1)).isEqualTo("sprint87-retire-modeling-layer-groups-rollback");
    }

    private void seedMenus() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                    insert into portal_menu (id, name, metadata, parent_id, deleted, last_modified_by) values
                    (1, 'sys.nav.portal.planningPublicLayer', '{"key":"planning-public"}', null, false, 'seed'),
                    (2, 'sys.nav.portal.planningApplicationLayer', '{"key":"planning-application"}', null, false, 'seed'),
                    (3, 'sys.nav.portal.planningDomains', '{"key":"planning-domains"}', 1, false, 'seed'),
                    (4, 'sys.nav.portal.planningMarts', '{"key":"planning-marts"}', 2, false, 'seed'),
                    (5, 'sys.nav.portal.planningProcesses', '{"key":"planning-processes"}', 1, true,
                        'sprint87-data-architecture-navigation-retired'),
                    (6, 'sys.nav.portal.planningSystem', '{"key":"planning-system"}', null, false, 'seed')
                    """
            );
        }
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
        connection.setSchema(schema);
        return connection;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
