package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PortalPrimaryMenuOrderLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810-04_portal_primary_menu_order.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("portalPrimaryMenuOrderTest")
        .withUsername("portal_menu_order_test")
        .withPassword("portal_menu_order_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "portal_primary_menu_order_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigint primary key,
                        name varchar(255) not null,
                        path varchar(500),
                        sort_order integer,
                        metadata text,
                        parent_id bigint,
                        deleted boolean not null default false,
                        last_modified_by varchar(100),
                        last_modified_date timestamp
                    )
                    """.formatted(schema)
            );
            statement.execute(
                """
                    create table %s.portal_menu_visibility (
                        id bigint primary key,
                        menu_id bigint not null,
                        role_code varchar(100) not null
                    )
                    """.formatted(schema)
            );
            statement.execute(
                """
                    create table %s.system_config (
                        cfg_key varchar(255) primary key,
                        cfg_value text,
                        description text,
                        category varchar(50),
                        sensitive boolean,
                        data_type varchar(50),
                        editable boolean,
                        sort_order integer,
                        display_name varchar(255),
                        config_scope varchar(50),
                        restart_required boolean,
                        owner varchar(100),
                        created_by varchar(100),
                        created_date timestamp,
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
    void reordersBothPortalConsumersWithoutChangingIdsHierarchyOrVisibilityBindings() throws Exception {
        List<String> previousOrder = rootKeys();
        int previousChildParent = parentId(8);
        List<String> previousBindings = visibilityBindings();

        runLiquibase(false);

        assertThat(rootKeys()).containsExactly(
            "workbench",
            "resource",
            "data-architecture",
            "modeling",
            "studio",
            "governance",
            "consumption"
        );
        assertThat(parentId(8)).isEqualTo(previousChildParent);
        assertThat(visibilityBindings()).containsExactlyElementsOf(previousBindings);
        assertThat(configValue("portal.menu.seed.hash")).isEqualTo(expectedSeedHash());

        runLiquibase(true);

        assertThat(rootKeys()).containsExactlyElementsOf(previousOrder);
        assertThat(parentId(8)).isEqualTo(previousChildParent);
        assertThat(visibilityBindings()).containsExactlyElementsOf(previousBindings);
        assertThat(configValue("portal.menu.seed.hash")).isEqualTo("old-seed-hash");
        assertThat(configValue("portal.menu.primary.order.snapshot.20260810-04")).isNull();
    }

    private void seedMenus() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                    insert into portal_menu (
                        id, name, path, sort_order, metadata, parent_id, deleted, last_modified_by
                    ) values
                    (1, 'sys.nav.portal.workbench', 'workbench', 1, '{"key":"workbench"}', null, false, 'seed'),
                    (2, 'sys.nav.portal.dataIntegration', 'resource', 2, '{"key":"resource"}', null, false, 'seed'),
                    (3, 'sys.nav.portal.studioCenter', 'studio', 3, '{"key":"studio"}', null, false, 'seed'),
                    (4, 'sys.nav.portal.studioDataModeling', 'modeling', 4, '{"key":"modeling"}', null, false, 'seed'),
                    (5, 'sys.nav.portal.dataArchitecture', 'data-architecture', 4, '{"key":"data-architecture"}', null, false, 'seed'),
                    (6, 'sys.nav.portal.governanceOperations', 'governance', 5, '{"key":"governance"}', null, false, 'seed'),
                    (7, 'sys.nav.portal.dataConsumption', 'consumption', 6, '{"key":"consumption"}', null, false, 'seed'),
                    (8, 'sys.nav.portal.resourceDatabaseAccess', 'resource/database-access', 1,
                        '{"key":"databaseAccess"}', 2, false, 'seed')
                    """
            );
            statement.execute(
                """
                    insert into portal_menu_visibility (id, menu_id, role_code) values
                    (1, 2, 'ROLE_OP_ADMIN'),
                    (2, 5, 'ROLE_INST_DATA_OWNER'),
                    (3, 8, 'ROLE_OP_ADMIN')
                    """
            );
            statement.execute(
                """
                    insert into system_config (
                        cfg_key, cfg_value, description, category, sensitive, data_type, editable, sort_order,
                        display_name, config_scope, restart_required, owner, created_by, created_date,
                        last_modified_by, last_modified_date
                    ) values (
                        'portal.menu.seed.hash', 'old-seed-hash', 'Portal menu seed hash', 'SYSTEM', false,
                        'STRING', false, 0, 'Portal menu seed hash', 'RUNTIME', false, 'platform',
                        'seed', now(), 'seed', now()
                    )
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

    private List<String> rootKeys() throws Exception {
        List<String> keys = new ArrayList<>();
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "select metadata::jsonb ->> 'key' from portal_menu " +
                "where parent_id is null and deleted = false order by sort_order, id"
            )
        ) {
            while (result.next()) keys.add(result.getString(1));
        }
        return keys;
    }

    private List<String> visibilityBindings() throws Exception {
        List<String> bindings = new ArrayList<>();
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "select menu_id || ':' || role_code from portal_menu_visibility order by id"
            )
        ) {
            while (result.next()) bindings.add(result.getString(1));
        }
        return bindings;
    }

    private int parentId(long id) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select parent_id from portal_menu where id = " + id)
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private String configValue(String key) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "select cfg_value from system_config where cfg_key = '" + key.replace("'", "''") + "'"
            )
        ) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private String expectedSeedHash() throws Exception {
        ClassPathResource resource = new ClassPathResource("config/data/portal-menu-seed.json");
        byte[] bytes = resource.getContentAsByteArray();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Connection connectionInSchema() throws Exception {
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }
}
