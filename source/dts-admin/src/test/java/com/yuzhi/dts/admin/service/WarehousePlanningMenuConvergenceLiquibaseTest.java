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
class WarehousePlanningMenuConvergenceLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810-03_converge_warehouse_planning_menu.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("warehousePlanningMenuConvergenceTest")
        .withUsername("warehouse_planning_menu_test")
        .withPassword("warehouse_planning_menu_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "warehouse_planning_menu_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigint primary key,
                        name varchar(255) not null,
                        path varchar(500),
                        component varchar(500),
                        sort_order integer,
                        metadata text,
                        parent_id bigint,
                        icon varchar(255),
                        security_level varchar(50),
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
    void convergesVisibleMenusAndRestoresOnlyMigrationOwnedRowsOnRollback() throws Exception {
        runLiquibase(false);

        assertThat(metadataTitle(2)).isEqualTo("数仓规划");
        assertThat(parentId(6)).isEqualTo(1L);
        assertThat(metadataTitle(6)).isEqualTo("建模策略");
        assertThat(path(6)).isEqualTo("modeling/planning/system");
        assertThat(sortOrder(6)).isEqualTo(2);
        assertThat(deleted(3)).isTrue();
        assertThat(deleted(4)).isTrue();
        assertThat(deleted(5)).isTrue();
        assertThat(deleted(6)).isFalse();
        assertThat(deleted(7)).isFalse();

        runLiquibase(true);

        assertThat(metadataTitle(2)).isEqualTo("数据架构");
        assertThat(parentId(6)).isEqualTo(3L);
        assertThat(metadataTitle(6)).isEqualTo("规划参数配置");
        assertThat(sortOrder(6)).isEqualTo(6);
        assertThat(deleted(3)).isFalse();
        assertThat(deleted(4)).isFalse();
        assertThat(deleted(5)).isFalse();
        assertThat(deleted(7)).isFalse();
    }

    private void seedMenus() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                    insert into portal_menu (
                        id, name, path, sort_order, metadata, parent_id, icon, security_level, deleted, last_modified_by
                    ) values
                    (1, 'sys.nav.portal.studioDataModeling', 'modeling', 5,
                        '{"key":"modeling","title":"数据建模"}', null, 'modeling-icon', 'GENERAL', false, 'seed'),
                    (2, 'sys.nav.portal.dataArchitecture', 'data-architecture', 4,
                        '{"key":"data-architecture","title":"数据架构"}', null, 'architecture-icon', 'GENERAL', false, 'seed'),
                    (3, 'sys.nav.portal.warehousePlanning', 'modeling/planning', 2,
                        '{"key":"warehouse-planning","title":"数仓规划"}', 1, 'planning-icon', 'GENERAL', false, 'seed'),
                    (4, 'sys.nav.portal.planningSpaces', 'modeling/planning/spaces', 5,
                        '{"key":"planning-spaces","title":"建模空间"}', 3, 'spaces-icon', 'GENERAL', false, 'seed'),
                    (5, 'customer.planningSpaceChild', 'modeling/planning/spaces/child', 1,
                        '{"key":"planning-space-child","title":"空间子项"}', 4, 'child-icon', 'GENERAL', false, 'seed'),
                    (6, 'sys.nav.portal.planningSystem', 'modeling/planning/system', 6,
                        '{"key":"planning-system","title":"规划参数配置"}', 3, 'settings-icon', 'GENERAL', false, 'seed'),
                    (7, 'sys.nav.portal.dataStandards', 'modeling/standards', 3,
                        '{"key":"standards","title":"数据标准"}', 1, 'standards-icon', 'GENERAL', false, 'seed')
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
        return booleanValue(id, "deleted");
    }

    private long parentId(long id) throws Exception {
        return longValue(id, "parent_id");
    }

    private int sortOrder(long id) throws Exception {
        return Math.toIntExact(longValue(id, "sort_order"));
    }

    private String path(long id) throws Exception {
        return stringValue(id, "path");
    }

    private String metadataTitle(long id) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "select metadata::jsonb ->> 'title' from portal_menu where id = " + id
            )
        ) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private boolean booleanValue(long id, String column) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select " + column + " from portal_menu where id = " + id)
        ) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private long longValue(long id, String column) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select " + column + " from portal_menu where id = " + id)
        ) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private String stringValue(long id, String column) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select " + column + " from portal_menu where id = " + id)
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
