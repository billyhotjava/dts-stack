package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
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

/** Sprint-104 F10: portal menu implementation order only changes sort_order and restores exactly. */
@Testcontainers
class PortalMenuImplementationOrderLiquibaseTest {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260916_03_portal_menu_implementation_order.xml";
    private static final String SEED_HASH = "9dc18d6b69d874144f400b0f35da43243bb8cb2e645cfaef16a7cda6bdb36a0f";
    private static final String SNAPSHOT_KEY = "portal.menu.order.snapshot.20260916-03";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("portalMenuImplementationOrderTest")
        .withUsername("portal_menu_impl_order_test")
        .withPassword("portal_menu_impl_order_test");

    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = "portal_menu_impl_order_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute(
                """
                    create table %s.portal_menu (
                        id bigserial primary key,
                        name varchar(255) not null,
                        path varchar(500),
                        component varchar(255),
                        sort_order integer,
                        metadata text,
                        parent_id bigint,
                        deleted boolean not null default false,
                        created_by varchar(100),
                        created_date timestamp,
                        last_modified_by varchar(100),
                        last_modified_date timestamp,
                        icon varchar(128),
                        security_level varchar(32) not null default 'GENERAL'
                    )
                    """.formatted(schema)
            );
            statement.execute(
                """
                    create table %s.portal_menu_visibility (
                        id bigint primary key,
                        menu_id bigint not null,
                        role_code varchar(100) not null,
                        permission_code varchar(100),
                        data_level varchar(32)
                    )
                    """.formatted(schema)
            );
            statement.execute(
                """
                    create table %s.system_config (
                        id bigserial primary key,
                        cfg_key varchar(255) not null unique,
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
    void reordersImplementationSequenceWithoutTouchingStructureAndRestoresExactly() throws Exception {
        List<String> rowsBefore = fullRows();
        List<String> structureBefore = structureRows();
        List<String> bindingsBefore = visibilityBindings();
        String screensBefore = fullRow(5);
        String seedBefore = seedRow();

        runLiquibase(false);

        assertThat(activeChildKeys(null)).containsExactly(
            "workbench",
            "data-architecture",
            "resource",
            "modeling",
            "studio",
            "governance",
            "consumption",
            "screens"
        );
        assertThat(activeChildKeys(10L)).containsExactly(
            "standards",
            "dimensional-modeling",
            "data-metrics",
            "modeling-graphs",
            "modeling-home-workspace",
            "modeling-tools"
        );
        assertThat(activeChildKeys(13L)).containsExactly(
            "metrics-atomic",
            "metrics-derived",
            "metrics-composite",
            "metrics-modifiers",
            "metrics-periods"
        );
        assertThat(activeChildKeys(9L)).containsExactly("assets", "classification", "qualityRules", "qualityReport");
        assertThat(activeChildKeys(7L)).containsExactly("services", "bi-apps");
        assertThat(structureRows()).containsExactlyElementsOf(structureBefore);
        assertThat(visibilityBindings()).containsExactlyElementsOf(bindingsBefore);
        assertThat(fullRow(5)).isEqualTo(screensBefore);
        assertThat(fullRow(18)).isEqualTo(rowsBefore.get(indexOfId(rowsBefore, 18)));
        assertThat(configValue("portal.menu.seed.hash")).isEqualTo(SEED_HASH);
        assertThat(configValue(SNAPSHOT_KEY)).isNotNull();

        runLiquibase(true);

        assertThat(fullRows()).containsExactlyElementsOf(rowsBefore);
        assertThat(visibilityBindings()).containsExactlyElementsOf(bindingsBefore);
        assertThat(seedRow()).isEqualTo(seedBefore);
        assertThat(configValue(SNAPSHOT_KEY)).isNull();
    }

    @Test
    void haltsWithoutPartialWriteWhenTitleKeyMatchesTwoActiveRows() throws Exception {
        execute(
            "insert into portal_menu (id, name, path, sort_order, metadata, parent_id, deleted, last_modified_by) values " +
            "(2000, 'sys.nav.portal.serviceCenter', 'consumption/services-copy', 9, '{\"key\":\"services-copy\"}', 7, false, 'admin')"
        );
        List<String> rowsBefore = fullRows();
        String seedBefore = seedRow();

        Throwable failure = catchThrowable(() -> runLiquibase(false));

        assertThat(stackTrace(failure)).contains("sys.nav.portal.serviceCenter must match exactly one active row");
        assertThat(fullRows()).containsExactlyElementsOf(rowsBefore);
        assertThat(seedRow()).isEqualTo(seedBefore);
        assertThat(configValue(SNAPSHOT_KEY)).isNull();
    }

    @Test
    void haltsWithoutPartialWriteWhenMenuSitsUnderUnexpectedParent() throws Exception {
        execute("update portal_menu set parent_id = 62 where id = 11");
        List<String> rowsBefore = fullRows();

        Throwable failure = catchThrowable(() -> runLiquibase(false));

        assertThat(stackTrace(failure)).contains("sys.nav.portal.dataStandards must be under sys.nav.portal.studioDataModeling");
        assertThat(fullRows()).containsExactlyElementsOf(rowsBefore);
        assertThat(configValue(SNAPSHOT_KEY)).isNull();
    }

    @Test
    void haltsWhenSnapshotAlreadyExists() throws Exception {
        execute(
            "insert into system_config (cfg_key, cfg_value, last_modified_by) values ('" + SNAPSHOT_KEY + "', '{}', 'earlier-run')"
        );
        List<String> rowsBefore = fullRows();

        Throwable failure = catchThrowable(() -> runLiquibase(false));

        assertThat(stackTrace(failure)).contains("snapshot already exists");
        assertThat(fullRows()).containsExactlyElementsOf(rowsBefore);
    }

    @Test
    void refusesRollbackWhenOrderChangedAfterMigration() throws Exception {
        runLiquibase(false);
        execute("update portal_menu set sort_order = 9, last_modified_by = 'admin' where id = 62");

        Throwable failure = catchThrowable(() -> runLiquibase(true));

        assertThat(stackTrace(failure)).contains("changed after migration");
        assertThat(sortOrder(62)).isEqualTo(9);
        assertThat(configValue(SNAPSHOT_KEY)).isNotNull();
    }

    @Test
    void masterRunsImplementationOrderAfterScreenManagementRoot() throws Exception {
        String master = new ClassPathResource("config/liquibase/master.xml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(master.indexOf("20260916_01_screen_management_root.xml"))
            .isGreaterThanOrEqualTo(0)
            .isLessThan(master.indexOf("20260916_03_portal_menu_implementation_order.xml"));
    }

    private void seedMenus() throws Exception {
        String when = "timestamp '2026-09-16 08:00:00.123456'";
        String rows = String.join(
            ",\n",
            row(68, "sys.nav.portal.workbench", "workbench", 1, "workbench", null, false, "seed"),
            row(52, "sys.nav.portal.dataIntegration", "resource", 2, "resource", null, false, "seed"),
            row(62, "sys.nav.portal.dataArchitecture", "data-architecture", 3, "data-architecture", null, false, "seed"),
            row(10, "sys.nav.portal.studioDataModeling", "modeling", 4, "modeling", null, false, "seed"),
            row(8, "sys.nav.portal.studioCenter", "studio", 5, "studio", null, false, "seed"),
            row(9, "sys.nav.portal.governanceOperations", "governance", 6, "governance", null, false, "seed"),
            row(7, "sys.nav.portal.dataConsumption", "consumption", 7, "consumption", null, false, "seed"),
            row(5, "sys.nav.portal.biScreens", "bi/screens", 8, "screens", null, false, "screen-management-root-20260916"),
            row(54, "sys.nav.portal.resourceDatabaseAccess", "resource/database-access", 1, "databaseAccess", 52L, false, "seed"),
            row(18, "sys.nav.portal.modelingHome", "modeling/home", 1, "modeling-home", 10L, true, "retired"),
            row(6, "sys.nav.portal.warehousePlanning", "modeling/planning", 2, "warehouse-planning", 10L, true, "retired"),
            row(23, "sys.nav.portal.modelingHomeWorkspace", "modeling/overview", 1, "modeling-home-workspace", 10L, false, "seed"),
            row(11, "sys.nav.portal.dataStandards", "modeling/standards", 3, "standards", 10L, false, "seed"),
            row(12, "sys.nav.portal.dimensionalModeling", "modeling/dimensions", 4, "dimensional-modeling", 10L, false, "seed"),
            row(13, "sys.nav.portal.dataMetrics", "modeling/metrics", 5, "data-metrics", 10L, false, "seed"),
            row(19, "sys.nav.portal.modelingTools", "modeling/tools", 6, "modeling-tools", 10L, false, "seed"),
            row(20, "sys.nav.portal.modelingGraphs", "modeling/graphs", 7, "modeling-graphs", 10L, false, "seed"),
            row(41, "sys.nav.portal.metricComposite", "modeling/metrics/composite", 1, "metrics-composite", 13L, false, "seed"),
            row(42, "sys.nav.portal.metricDerived", "modeling/metrics/derived", 2, "metrics-derived", 13L, false, "seed"),
            row(43, "sys.nav.portal.metricAtomic", "modeling/metrics/atomic", 3, "metrics-atomic", 13L, false, "seed"),
            row(44, "sys.nav.portal.metricModifiers", "modeling/metrics/modifiers", 4, "metrics-modifiers", 13L, false, "seed"),
            row(45, "sys.nav.portal.metricPeriods", "modeling/metrics/periods", 5, "metrics-periods", 13L, false, "seed"),
            row(16, "sys.nav.portal.governanceAssets", "governance/assets", 1, "assets", 9L, false, "seed"),
            row(1069, "sys.nav.portal.governanceQualityRules", "governance/quality-rules", 2, "qualityRules", 9L, false, "seed"),
            row(1070, "sys.nav.portal.governanceQualityReport", "governance/quality-report", 3, "qualityReport", 9L, false, "seed"),
            row(1071, "sys.nav.portal.governanceClassification", "governance/classification", 4, "classification", 9L, false, "seed"),
            row(69, "sys.nav.portal.businessIntelligenceApps", "consumption/bi-apps", 1, "bi-apps", 7L, false, "seed"),
            row(1075, "sys.nav.portal.serviceCenter", "consumption/services", 3, "services", 7L, false, "seed")
        ).replace("__WHEN__", when);
        execute(
            "insert into portal_menu (id, name, path, sort_order, metadata, parent_id, deleted, last_modified_by, last_modified_date) values\n" + rows
        );
        execute(
            """
                insert into portal_menu_visibility (id, menu_id, role_code, permission_code, data_level) values
                (1, 5, 'ROLE_EMPLOYEE', null, 'DATA_INTERNAL'),
                (2, 5, 'ROLE_DEPT_LEADER', null, null),
                (3, 10, 'ROLE_DEPT_DATA_OWNER', null, null),
                (4, 1071, 'ROLE_INST_DATA_OWNER', 'governance.manage', null),
                (5, 54, 'ROLE_OP_ADMIN', null, null)
                """
        );
        execute(
            """
                insert into system_config (
                    cfg_key, cfg_value, description, category, sensitive, data_type, editable, sort_order,
                    display_name, config_scope, restart_required, owner, created_by, created_date,
                    last_modified_by, last_modified_date
                ) values (
                    'portal.menu.seed.hash', '4c807a4176742b74f0054314006d3e37512e85ea7c4fc8aabb21e8903172276a',
                    'Portal menu seed hash', 'SYSTEM', false, 'STRING', false, 0, 'Portal menu seed hash', 'RUNTIME',
                    false, 'platform', 'screen-management-root-20260916', timestamp '2026-09-16 07:00:00',
                    'screen-management-root-20260916', timestamp '2026-09-16 07:30:00.654321'
                )
                """
        );
        execute("select setval(pg_get_serial_sequence('portal_menu', 'id'), (select max(id) from portal_menu), true)");
    }

    private static String row(
        long id,
        String titleKey,
        String path,
        int sortOrder,
        String key,
        Long parentId,
        boolean deleted,
        String modifiedBy
    ) {
        String metadata = "{\"key\":\"" + key + "\",\"titleKey\":\"" + titleKey + "\"}";
        return "(" + id + ", '" + titleKey + "', '" + path + "', " + sortOrder + ", '" + metadata + "', " +
            (parentId == null ? "null" : parentId) + ", " + deleted + ", '" + modifiedBy + "', __WHEN__)";
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

    private List<String> activeChildKeys(Long parentId) throws Exception {
        String parentFilter = parentId == null ? "parent_id is null" : "parent_id = " + parentId;
        return strings(
            "select metadata::jsonb ->> 'key' from portal_menu where deleted = false and " + parentFilter +
            " order by sort_order, id"
        );
    }

    private List<String> fullRows() throws Exception {
        return strings("select row_to_json(m)::text from portal_menu m order by id");
    }

    private List<String> structureRows() throws Exception {
        return strings(
            "select concat_ws('|', id, name, path, component, metadata, parent_id, deleted, icon, security_level) " +
            "from portal_menu order by id"
        );
    }

    private List<String> visibilityBindings() throws Exception {
        return strings("select row_to_json(v)::text from portal_menu_visibility v order by id");
    }

    private String fullRow(long id) throws Exception {
        List<String> rows = strings("select row_to_json(m)::text from portal_menu m where id = " + id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String seedRow() throws Exception {
        List<String> rows = strings(
            "select concat_ws('|', cfg_value, last_modified_by, last_modified_date) from system_config " +
            "where cfg_key = 'portal.menu.seed.hash'"
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int sortOrder(long id) throws Exception {
        return Integer.parseInt(strings("select sort_order from portal_menu where id = " + id).get(0));
    }

    private String configValue(String key) throws Exception {
        List<String> rows = strings(
            "select cfg_value from system_config where cfg_key = '" + key.replace("'", "''") + "'"
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static int indexOfId(List<String> rows, long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).startsWith("{\"id\":" + id + ",")) return i;
        }
        throw new IllegalStateException("row " + id + " missing");
    }

    private List<String> strings(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            while (result.next()) values.add(result.getString(1));
        }
        return values;
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String stackTrace(Throwable failure) {
        assertThat(failure).isNotNull();
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        return writer.toString();
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
