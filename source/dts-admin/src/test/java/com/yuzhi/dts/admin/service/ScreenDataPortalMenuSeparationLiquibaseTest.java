package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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
class ScreenDataPortalMenuSeparationLiquibaseTest {

    private static final String BOOTSTRAP_CHANGELOG =
        "config/liquibase/changelog/20260901_02_consumption_bi_hierarchy_bootstrap.xml";
    private static final String SEPARATION_CHANGELOG =
        "config/liquibase/changelog/20260830-01_separate_screen_management_and_data_portal.xml";
    private static final String EXPECTED_SEED_HASH = "586df4b4dc526042dcfc4e2e9ba49511e75a92753b5934768f08b9f9bd548f2f";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("screenDataPortalSeparationTest")
        .withUsername("screen_portal_test")
        .withPassword("screen_portal_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "screen_portal_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("create sequence " + schema + ".portal_menu_seq start with 20");
            statement.execute("create sequence " + schema + ".portal_menu_visibility_seq start with 20");
            statement.execute("create sequence " + schema + ".system_config_seq start with 20");
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigint primary key default nextval('%s.portal_menu_seq'),
                        name varchar(255) not null,
                        path varchar(255) not null,
                        component varchar(255),
                        sort_order integer,
                        metadata text,
                        parent_id bigint references %s.portal_menu(id),
                        icon varchar(255),
                        security_level varchar(32) not null default 'GENERAL',
                        deleted boolean not null default false,
                        created_by varchar(50),
                        created_date timestamp,
                        last_modified_by varchar(50),
                        last_modified_date timestamp
                    )
                    """.formatted(schema, schema, schema)
            );
            statement.execute(
                """
                    create table %s.portal_menu_visibility (
                        id bigint primary key default nextval('%s.portal_menu_visibility_seq'),
                        menu_id bigint not null references %s.portal_menu(id),
                        role_code varchar(128) not null,
                        permission_code varchar(128),
                        data_level varchar(32) not null default 'INTERNAL',
                        created_by varchar(50),
                        created_date timestamp,
                        last_modified_by varchar(50),
                        last_modified_date timestamp
                    )
                    """.formatted(schema, schema, schema)
            );
            statement.execute(
                """
                    create table %s.system_config (
                        id bigint primary key default nextval('%s.system_config_seq'),
                        cfg_key varchar(255) not null unique,
                        cfg_value varchar(2000),
                        description varchar(1000),
                        category varchar(100),
                        sensitive boolean,
                        data_type varchar(50),
                        editable boolean,
                        sort_order integer,
                        display_name varchar(255),
                        config_scope varchar(50),
                        restart_required boolean,
                        owner varchar(100),
                        created_by varchar(50),
                        created_date timestamp,
                        last_modified_by varchar(50),
                        last_modified_date timestamp
                    )
                    """.formatted(schema, schema)
            );
        }
        seedMenusAndAudiences();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void restoresLeaderScreenManagementAndPlacesAnalystPortalUnderBiAnalysisWithSafeRollback() throws Exception {
        runLiquibase(SEPARATION_CHANGELOG, false);

        assertThat(menuId("sys.nav.portal.biScreens")).isEqualTo(5L);
        assertThat(menuTitle(5)).isEqualTo("大屏管理");
        assertThat(menuExternalLink(5)).isEqualTo("/bi/screens");
        assertThat(parentId(5)).isEqualTo(1L);
        assertThat(rolesForMenu(5)).containsExactly("ROLE_INST_LEADER");

        long portalId = menuId("sys.nav.portal.biPortal");
        assertThat(parentId(portalId)).isEqualTo(3L);
        assertThat(menuTitle(portalId)).isEqualTo("数据门户");
        assertThat(menuExternalLink(portalId)).isEqualTo("/bi/portal");
        assertThat(rolesForMenu(portalId)).containsExactly("ROLE_BI_ANALYST");
        assertThat(configValue("portal.menu.seed.hash")).isEqualTo(EXPECTED_SEED_HASH);

        runLiquibase(SEPARATION_CHANGELOG, true);

        assertThat(menuId("sys.nav.portal.biScreens")).isEqualTo(5L);
        assertThat(menuTitle(5)).isEqualTo("数据门户");
        assertThat(menuExternalLink(5)).isEqualTo("/bi/portal");
        assertThat(rolesForMenu(5)).containsExactly("ROLE_INST_LEADER");
        assertThat(menuCount("sys.nav.portal.biPortal")).isZero();
        assertThat(configValue("portal.menu.seed.hash")).isEqualTo("old-seed-hash");
        assertThat(configCount("portal.menu.screen-portal-separation.snapshot.20260830-01")).isZero();
    }

    @Test
    void bootstrapsMissingBiHierarchyBeforeFreshInstallSeparation() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute("delete from portal_menu_visibility where menu_id = 4");
            statement.execute("delete from portal_menu where id = 4");
            statement.execute("delete from portal_menu where id = 3");
            statement.execute("delete from portal_menu where id = 2");
        }

        runLiquibase(BOOTSTRAP_CHANGELOG, false);
        runLiquibase(SEPARATION_CHANGELOG, false);

        long biAppsId = menuId("sys.nav.portal.businessIntelligenceApps");
        long biAnalysisId = menuId("sys.nav.portal.bi");
        assertThat(parentId(biAppsId)).isEqualTo(1L);
        assertThat(parentId(biAnalysisId)).isEqualTo(biAppsId);
        assertThat(menuCreatedBy(biAppsId)).isEqualTo("consumption-bi-hierarchy-bootstrap");
        assertThat(menuCreatedBy(biAnalysisId)).isEqualTo("consumption-bi-hierarchy-bootstrap");
        assertThat(parentId(menuId("sys.nav.portal.biPortal"))).isEqualTo(biAnalysisId);

        runLiquibase(SEPARATION_CHANGELOG, true);
        runLiquibase(BOOTSTRAP_CHANGELOG, true);

        assertThat(menuCount("sys.nav.portal.businessIntelligenceApps")).isZero();
        assertThat(menuCount("sys.nav.portal.bi")).isZero();
    }

    @Test
    void masterRunsBiHierarchyBootstrapBeforeScreenPortalSeparation() throws Exception {
        String master = new ClassPathResource("config/liquibase/master.xml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(master.indexOf("20260901_02_consumption_bi_hierarchy_bootstrap.xml"))
            .isGreaterThanOrEqualTo(0)
            .isLessThan(master.indexOf("20260830-01_separate_screen_management_and_data_portal.xml"));
    }

    private void seedMenusAndAudiences() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                    insert into portal_menu (
                        id, name, path, component, sort_order, metadata, parent_id, icon, security_level, deleted,
                        created_by, created_date, last_modified_by, last_modified_date
                    ) values
                    (1, 'sys.nav.portal.dataConsumption', 'consumption', null, 4,
                        '{"key":"consumption","titleKey":"sys.nav.portal.dataConsumption","title":"数据分析与服务"}',
                        null, 'consumption-icon', 'GENERAL', false, 'seed', now(), 'seed', now()),
                    (2, 'sys.nav.portal.businessIntelligenceApps', 'consumption/bi-apps', null, 1,
                        '{"key":"bi-apps","titleKey":"sys.nav.portal.businessIntelligenceApps","title":"商业智能应用"}',
                        1, 'bi-apps-icon', 'GENERAL', false, 'seed', now(), 'seed', now()),
                    (3, 'sys.nav.portal.bi', 'consumption/bi-apps/bi', null, 1,
                        '{"key":"bi","titleKey":"sys.nav.portal.bi","title":"BI 分析"}',
                        2, 'bi-icon', 'GENERAL', false, 'seed', now(), 'seed', now()),
                    (4, 'sys.nav.portal.biDashboards', 'consumption/bi-apps/bi/dashboards', null, 1,
                        '{"key":"dashboards","titleKey":"sys.nav.portal.biDashboards","title":"分析看板","externalLink":"/bi/dashboards"}',
                        3, 'dashboard-icon', 'GENERAL', false, 'seed', now(), 'seed', now()),
                    (5, 'sys.nav.portal.biScreens', 'consumption/screens', null, 2,
                        '{"key":"screens","titleKey":"sys.nav.portal.biScreens","title":"数据门户","externalLink":"/bi/portal"}',
                        1, 'screen-icon', 'GENERAL', false, 'seed', now(), 'sprint96-data-portal', now())
                    """
            );
            statement.execute(
                """
                    insert into portal_menu_visibility (
                        id, menu_id, role_code, permission_code, data_level,
                        created_by, created_date, last_modified_by, last_modified_date
                    ) values
                    (10, 5, 'ROLE_INST_LEADER', null, 'INTERNAL', 'seed', now(), 'seed', now()),
                    (11, 4, 'ROLE_BI_ANALYST', null, 'INTERNAL', 'seed', now(), 'seed', now())
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

    private void runLiquibase(String changelog, boolean rollback) throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
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

    private long menuId(String name) throws Exception {
        return longQuery("select id from portal_menu where name = '" + name + "' and deleted = false");
    }

    private long menuCount(String name) throws Exception {
        return longQuery("select count(*) from portal_menu where name = '" + name + "'");
    }

    private long configCount(String key) throws Exception {
        return longQuery("select count(*) from system_config where cfg_key = '" + key + "'");
    }

    private long parentId(long menuId) throws Exception {
        return longQuery("select parent_id from portal_menu where id = " + menuId);
    }

    private String menuTitle(long menuId) throws Exception {
        return stringQuery("select metadata::jsonb ->> 'title' from portal_menu where id = " + menuId);
    }

    private String menuExternalLink(long menuId) throws Exception {
        return stringQuery("select metadata::jsonb ->> 'externalLink' from portal_menu where id = " + menuId);
    }

    private String menuCreatedBy(long menuId) throws Exception {
        return stringQuery("select created_by from portal_menu where id = " + menuId);
    }

    private String configValue(String key) throws Exception {
        return stringQuery("select cfg_value from system_config where cfg_key = '" + key + "'");
    }

    private List<String> rolesForMenu(long menuId) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(
                "select role_code from portal_menu_visibility where menu_id = " + menuId + " order by role_code"
            )
        ) {
            List<String> roles = new java.util.ArrayList<>();
            while (result.next()) roles.add(result.getString(1));
            return roles;
        }
    }

    private long longQuery(String sql) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private String stringQuery(String sql) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
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
