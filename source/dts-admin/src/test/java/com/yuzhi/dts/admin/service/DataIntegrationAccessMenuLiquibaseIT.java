package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
class DataIntegrationAccessMenuLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260731-04_data_integration_access_menu_convergence.xml";
    private static final String MIGRATION_ACTOR = "data-integration-access-menu-convergence";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^data_integration_access_[0-9a-f]{32}$");

    private static final long RESOURCE_ID = 1L;
    private static final long SOURCES_ID = 10L;
    private static final long INGESTION_ID = 11L;
    private static final long METADATA_ID = 12L;
    private static final long CHANGES_ID = 13L;
    private static final long CONNECTORS_ID = 14L;
    private static final long JDBC_ID = 15L;
    private static final long PREDELETED_CHANGES_ID = 16L;
    private static final long PREDELETED_DEFAULTS_ID = 17L;
    private static final long PREDELETED_CONNECTORS_ID = 18L;
    private static final long EXISTING_DATABASE_ACCESS_ID = 19L;
    private static final long EXISTING_RUNTIME_ID = 20L;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("dataIntegrationAccessIT")
        .withUsername("data_integration_access_test")
        .withPassword("data_integration_access_test");

    private String schema;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema = "data_integration_access_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        createPrerequisites();
        insertLegacyFixture();
    }

    @AfterEach
    void dropSchema() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void forwardMapsOnlyDirectCapabilityTriplesAndKeepsPredeletedRowsHidden() throws Exception {
        applyChangelog();

        assertThat(activeMenuId("sys.nav.portal.resourceAccessOverview")).isEqualTo(SOURCES_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceDatabaseAccess")).isEqualTo(EXISTING_DATABASE_ACCESS_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceRuntime")).isEqualTo(EXISTING_RUNTIME_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceConnectors")).isEqualTo(CONNECTORS_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceJdbcDrivers")).isEqualTo(JDBC_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceAccessDefaults")).isNotEqualTo(PREDELETED_DEFAULTS_ID);

        assertThat(triplesFor(activeMenuId("sys.nav.portal.resourceAccessOverview")))
            .containsExactlyInAnyOrder(
                new VisibilityTriple("ROLE_SOURCES", "source.read", "INTERNAL"),
                new VisibilityTriple("ROLE_INGESTION", "ingestion.run", "CONFIDENTIAL"),
                new VisibilityTriple("ROLE_METADATA", "metadata.read", "SECRET"),
                new VisibilityTriple("ROLE_CHANGES", "changes.read", "INTERNAL")
            );
        for (String titleKey : List.of(
            "sys.nav.portal.resourceDatabaseAccess",
            "sys.nav.portal.resourceApiAccess",
            "sys.nav.portal.resourceFileAccess"
        )) {
            assertThat(triplesFor(activeMenuId(titleKey)))
                .containsExactlyInAnyOrder(
                    new VisibilityTriple("ROLE_SOURCES", "source.read", "INTERNAL"),
                    new VisibilityTriple("ROLE_INGESTION", "ingestion.run", "CONFIDENTIAL")
                );
        }
        assertThat(triplesFor(activeMenuId("sys.nav.portal.resourceAccessDefaults"))).isEmpty();
        assertThat(triplesFor(activeMenuId("sys.nav.portal.resourceConnectors")))
            .containsExactly(new VisibilityTriple("ROLE_CONNECTORS", "connectors.read", "INTERNAL"));
        assertThat(triplesFor(activeMenuId("sys.nav.portal.resourceJdbcDrivers")))
            .containsExactly(new VisibilityTriple("ROLE_JDBC", "jdbc.manage", "CONFIDENTIAL"));
        assertThat(triplesFor(activeMenuId("sys.nav.portal.resourceRuntime"))).isEmpty();

        assertThat(parentId(CONNECTORS_ID)).isEqualTo(activeMenuId("sys.nav.portal.resourceRuntime"));
        assertThat(parentId(JDBC_ID)).isEqualTo(activeMenuId("sys.nav.portal.resourceRuntime"));
        assertThat(isDeleted(PREDELETED_CHANGES_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_DEFAULTS_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_CONNECTORS_ID)).isTrue();
        assertThat(lastModifiedBy(PREDELETED_CHANGES_ID)).isEqualTo("fixture");
        assertThat(lastModifiedBy(PREDELETED_DEFAULTS_ID)).isEqualTo("fixture");
        assertThat(lastModifiedBy(PREDELETED_CONNECTORS_ID)).isEqualTo("fixture");
        assertThat(allRolesOnActiveAccessMenus())
            .doesNotContain("ROLE_PARENT", "ROLE_STALE_CHANGES", "ROLE_STALE_DEFAULTS", "ROLE_STALE_CONNECTORS");
    }

    @Test
    void populatedTreeWithoutExactResourceMetadataKeyFailsClosed() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute("update portal_menu set metadata = '{\"key\":\"resource-center\"}' where id = " + RESOURCE_ID);
        }

        assertThatThrownBy(this::applyChangelog)
            .hasStackTraceContaining("data-integration menu root metadata key resource is missing");
    }

    @Test
    void rollbackRestoresOnlyRowsThatWereEnabledBeforeForwardMigration() throws Exception {
        MenuState databaseBefore = menuState(EXISTING_DATABASE_ACCESS_ID);
        MenuState runtimeBefore = menuState(EXISTING_RUNTIME_ID);
        java.sql.Timestamp databaseModifiedBefore = lastModifiedDate(EXISTING_DATABASE_ACCESS_ID);
        java.sql.Timestamp runtimeModifiedBefore = lastModifiedDate(EXISTING_RUNTIME_ID);
        applyChangelog();
        rollbackChangelog();

        assertThat(menuName(SOURCES_ID)).isEqualTo("sys.nav.portal.resourceSources");
        assertThat(parentId(CONNECTORS_ID)).isEqualTo(RESOURCE_ID);
        assertThat(parentId(JDBC_ID)).isEqualTo(RESOURCE_ID);
        assertThat(isDeleted(SOURCES_ID)).isFalse();
        assertThat(isDeleted(INGESTION_ID)).isFalse();
        assertThat(isDeleted(METADATA_ID)).isFalse();
        assertThat(isDeleted(CHANGES_ID)).isFalse();
        assertThat(isDeleted(CONNECTORS_ID)).isFalse();
        assertThat(isDeleted(JDBC_ID)).isFalse();

        assertThat(isDeleted(PREDELETED_CHANGES_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_DEFAULTS_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_CONNECTORS_ID)).isTrue();
        assertThat(lastModifiedBy(PREDELETED_CHANGES_ID)).isEqualTo("fixture");
        assertThat(lastModifiedBy(PREDELETED_DEFAULTS_ID)).isEqualTo("fixture");
        assertThat(lastModifiedBy(PREDELETED_CONNECTORS_ID)).isEqualTo("fixture");

        assertThat(countMigrationVisibilityRows()).isZero();
        assertThat(triplesFor(SOURCES_ID)).containsExactly(new VisibilityTriple("ROLE_SOURCES", "source.read", "INTERNAL"));
        assertThat(triplesFor(CONNECTORS_ID))
            .containsExactly(new VisibilityTriple("ROLE_CONNECTORS", "connectors.read", "INTERNAL"));
        assertThat(triplesFor(JDBC_ID)).containsExactly(new VisibilityTriple("ROLE_JDBC", "jdbc.manage", "CONFIDENTIAL"));
        assertThat(activeMenuCount("sys.nav.portal.resourceDatabaseAccess")).isZero();
        assertThat(activeMenuCount("sys.nav.portal.resourceApiAccess")).isZero();
        assertThat(activeMenuCount("sys.nav.portal.resourceFileAccess")).isZero();
        assertThat(activeMenuCount("sys.nav.portal.resourceAccessDefaults")).isZero();
        assertThat(activeMenuCount("sys.nav.portal.resourceRuntime")).isEqualTo(1L);
        assertThat(menuState(EXISTING_DATABASE_ACCESS_ID)).isEqualTo(databaseBefore);
        assertThat(menuState(EXISTING_RUNTIME_ID)).isEqualTo(runtimeBefore);
        assertThat(lastModifiedDate(EXISTING_DATABASE_ACCESS_ID)).isEqualTo(databaseModifiedBefore);
        assertThat(lastModifiedDate(EXISTING_RUNTIME_ID)).isEqualTo(runtimeModifiedBefore);
    }

    @Test
    void rollbackWithoutSnapshotFailsAtomicallyWithoutChangingMenusOrMigrationVisibility() throws Exception {
        applyChangelog();
        Map<Long, MenuState> menusBeforeRollback = allMenuStates();
        Map<Long, java.sql.Timestamp> modifiedDatesBeforeRollback = allLastModifiedDates();
        long migrationVisibilityBeforeRollback = countMigrationVisibilityRows();
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(
            "delete from system_config where cfg_key = ?"
        )) {
            statement.setString(1, "portal.menu.access.convergence.snapshot.20260731-04");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(this::rollbackChangelog)
            .hasStackTraceContaining("data-integration menu convergence rollback snapshot is missing");

        assertThat(allMenuStates()).isEqualTo(menusBeforeRollback);
        assertThat(allLastModifiedDates()).isEqualTo(modifiedDatesBeforeRollback);
        assertThat(countMigrationVisibilityRows()).isEqualTo(migrationVisibilityBeforeRollback);
    }

    @Test
    void rollbackThenReapplyReusesMigrationOwnedRowsWithoutChangingCanonicalIds() throws Exception {
        MenuState databaseBefore = menuState(EXISTING_DATABASE_ACCESS_ID);
        MenuState runtimeBefore = menuState(EXISTING_RUNTIME_ID);
        applyChangelog();
        Map<String, Long> firstIds = canonicalActiveIds();
        long firstMenuCount = count("portal_menu", "true");
        long firstVisibilityCount = count("portal_menu_visibility", "true");
        MenuState firstForwardDatabase = menuState(EXISTING_DATABASE_ACCESS_ID);
        MenuState firstForwardRuntime = menuState(EXISTING_RUNTIME_ID);
        assertThat(menuState(EXISTING_DATABASE_ACCESS_ID)).isNotEqualTo(databaseBefore);
        assertThat(menuState(EXISTING_RUNTIME_ID)).isNotEqualTo(runtimeBefore);

        rollbackChangelog();
        assertThat(menuState(EXISTING_DATABASE_ACCESS_ID)).isEqualTo(databaseBefore);
        assertThat(menuState(EXISTING_RUNTIME_ID)).isEqualTo(runtimeBefore);
        applyChangelog();

        assertThat(canonicalActiveIds()).isEqualTo(firstIds);
        assertThat(activeMenuId("sys.nav.portal.resourceDatabaseAccess")).isEqualTo(EXISTING_DATABASE_ACCESS_ID);
        assertThat(activeMenuId("sys.nav.portal.resourceRuntime")).isEqualTo(EXISTING_RUNTIME_ID);
        assertThat(menuState(EXISTING_DATABASE_ACCESS_ID)).isEqualTo(firstForwardDatabase);
        assertThat(menuState(EXISTING_RUNTIME_ID)).isEqualTo(firstForwardRuntime);
        assertThat(count("portal_menu", "true")).isEqualTo(firstMenuCount);
        assertThat(count("portal_menu_visibility", "true")).isEqualTo(firstVisibilityCount);
        assertThat(isDeleted(PREDELETED_CHANGES_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_DEFAULTS_ID)).isTrue();
        assertThat(isDeleted(PREDELETED_CONNECTORS_ID)).isTrue();
        assertThat(allRolesOnActiveAccessMenus())
            .doesNotContain("ROLE_PARENT", "ROLE_STALE_CHANGES", "ROLE_STALE_DEFAULTS", "ROLE_STALE_CONNECTORS");
    }

    private void createPrerequisites() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute("create sequence portal_menu_visibility_seq start with 1000");
            statement.execute(
                """
                create table portal_menu (
                    id bigint generated by default as identity (start with 1000) primary key,
                    name varchar(255) not null,
                    path varchar(512),
                    component varchar(512),
                    sort_order integer,
                    metadata text,
                    parent_id bigint,
                    icon varchar(255),
                    security_level varchar(64),
                    deleted boolean not null default false,
                    created_by varchar(100),
                    created_date timestamp,
                    last_modified_by varchar(100),
                    last_modified_date timestamp
                )
                """
            );
            statement.execute(
                """
                create table portal_menu_visibility (
                    id bigint primary key,
                    menu_id bigint not null,
                    role_code varchar(100) not null,
                    permission_code varchar(100),
                    data_level varchar(64),
                    created_by varchar(100),
                    created_date timestamp,
                    last_modified_by varchar(100),
                    last_modified_date timestamp
                )
                """
            );
            statement.execute(
                """
                create table system_config (
                    cfg_key varchar(255) primary key,
                    cfg_value text,
                    description varchar(512),
                    category varchar(100),
                    sensitive boolean,
                    data_type varchar(64),
                    editable boolean,
                    sort_order integer,
                    display_name varchar(255),
                    config_scope varchar(64),
                    restart_required boolean,
                    owner varchar(100),
                    created_by varchar(100),
                    created_date timestamp,
                    last_modified_by varchar(100),
                    last_modified_date timestamp
                )
                """
            );
        }
    }

    private void insertLegacyFixture() throws Exception {
        insertMenu(RESOURCE_ID, null, "sys.nav.portal.dataIntegration", "resource", false);
        insertMenu(SOURCES_ID, RESOURCE_ID, "sys.nav.portal.resourceSources", "sources", false);
        insertMenu(INGESTION_ID, RESOURCE_ID, "sys.nav.portal.resourceIngestion", "ingestion", false);
        insertMenu(METADATA_ID, RESOURCE_ID, "sys.nav.portal.resourceMetadata", "metadata", false);
        insertMenu(CHANGES_ID, RESOURCE_ID, "sys.nav.portal.resourceChanges", "changes", false);
        insertMenu(CONNECTORS_ID, RESOURCE_ID, "sys.nav.portal.resourceConnectors", "connectors", false);
        insertMenu(JDBC_ID, RESOURCE_ID, "sys.nav.portal.resourceJdbcDrivers", "jdbcDrivers", false);
        insertMenu(PREDELETED_CHANGES_ID, RESOURCE_ID, "sys.nav.portal.resourceChanges", "changes-old", true);
        insertMenu(PREDELETED_DEFAULTS_ID, RESOURCE_ID, "sys.nav.portal.resourceAccessDefaults", "accessDefaults-old", true);
        insertMenu(PREDELETED_CONNECTORS_ID, RESOURCE_ID, "sys.nav.portal.resourceConnectors", "connectors-old", true);
        insertMenu(
            EXISTING_DATABASE_ACCESS_ID,
            RESOURCE_ID,
            "sys.nav.portal.resourceDatabaseAccess",
            "databaseAccess",
            false
        );
        insertMenu(EXISTING_RUNTIME_ID, RESOURCE_ID, "sys.nav.portal.resourceRuntime", "runtime", false);
        customizeMenu(EXISTING_DATABASE_ACCESS_ID, "custom/database", "custom.DatabaseComponent", 71, "custom-database-icon");
        customizeMenu(EXISTING_RUNTIME_ID, "custom/runtime", "custom.RuntimeComponent", 72, "custom-runtime-icon");

        insertVisibility(100L, RESOURCE_ID, "ROLE_PARENT", "resource.root", "INTERNAL");
        insertVisibility(101L, SOURCES_ID, "ROLE_SOURCES", "source.read", "INTERNAL");
        insertVisibility(102L, INGESTION_ID, "ROLE_INGESTION", "ingestion.run", "CONFIDENTIAL");
        insertVisibility(103L, METADATA_ID, "ROLE_METADATA", "metadata.read", "SECRET");
        insertVisibility(104L, CHANGES_ID, "ROLE_CHANGES", "changes.read", "INTERNAL");
        insertVisibility(105L, CONNECTORS_ID, "ROLE_CONNECTORS", "connectors.read", "INTERNAL");
        insertVisibility(106L, JDBC_ID, "ROLE_JDBC", "jdbc.manage", "CONFIDENTIAL");
        insertVisibility(107L, PREDELETED_CHANGES_ID, "ROLE_STALE_CHANGES", "changes.stale", "INTERNAL");
        insertVisibility(108L, PREDELETED_DEFAULTS_ID, "ROLE_STALE_DEFAULTS", "defaults.stale", "INTERNAL");
        insertVisibility(109L, PREDELETED_CONNECTORS_ID, "ROLE_STALE_CONNECTORS", "connectors.stale", "INTERNAL");
    }

    private void insertMenu(long id, Long parentId, String titleKey, String key, boolean deleted) throws Exception {
        String sql =
            "insert into portal_menu (id, name, path, component, sort_order, metadata, parent_id, icon, security_level, deleted, " +
            "created_by, created_date, last_modified_by, last_modified_date) values (?, ?, ?, null, ?, ?::jsonb::text, ?, ?, " +
            "'GENERAL', ?, 'fixture', now(), 'fixture', now())";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, titleKey);
            statement.setString(3, "resource/" + key);
            statement.setInt(4, (int) id);
            statement.setString(
                5,
                "{\"key\":\"" + key + "\",\"sectionKey\":\"resource\",\"titleKey\":\"" + titleKey + "\"}"
            );
            if (parentId == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, parentId);
            }
            statement.setString(7, "fixture");
            statement.setBoolean(8, deleted);
            statement.executeUpdate();
        }
    }

    private void insertVisibility(long id, long menuId, String role, String permission, String dataLevel) throws Exception {
        String sql =
            "insert into portal_menu_visibility (id, menu_id, role_code, permission_code, data_level, created_by, created_date, " +
            "last_modified_by, last_modified_date) values (?, ?, ?, ?, ?, 'fixture', now(), 'fixture', now())";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setLong(2, menuId);
            statement.setString(3, role);
            statement.setString(4, permission);
            statement.setString(5, dataLevel);
            statement.executeUpdate();
        }
    }

    private void customizeMenu(long menuId, String path, String component, int sortOrder, String icon) throws Exception {
        String sql =
            "update portal_menu set path = ?, component = ?, sort_order = ?, " +
            "metadata = jsonb_set(metadata::jsonb, '{customMarker}', to_jsonb(?::text))::text, " +
            "icon = ?, security_level = 'CUSTOM', last_modified_by = 'customer-customization', " +
            "last_modified_date = timestamp '2026-07-01 08:09:10' where id = ?";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, path);
            statement.setString(2, component);
            statement.setInt(3, sortOrder);
            statement.setString(4, "marker-" + menuId);
            statement.setString(5, icon);
            statement.setLong(6, menuId);
            statement.executeUpdate();
        }
    }

    private Map<String, Long> canonicalActiveIds() throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String titleKey : List.of(
            "sys.nav.portal.resourceAccessOverview",
            "sys.nav.portal.resourceDatabaseAccess",
            "sys.nav.portal.resourceApiAccess",
            "sys.nav.portal.resourceFileAccess",
            "sys.nav.portal.resourceAccessDefaults",
            "sys.nav.portal.resourceRuntime",
            "sys.nav.portal.resourceConnectors",
            "sys.nav.portal.resourceJdbcDrivers"
        )) {
            result.put(titleKey, activeMenuId(titleKey));
        }
        return result;
    }

    private long activeMenuId(String titleKey) throws Exception {
        String sql = "select id from portal_menu where name = ? and deleted = false order by id";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, titleKey);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as("active menu %s", titleKey).isTrue();
                long id = result.getLong(1);
                assertThat(result.next()).as("single active menu %s", titleKey).isFalse();
                return id;
            }
        }
    }

    private long activeMenuCount(String titleKey) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement("select count(*) from portal_menu where name = ? and deleted = false")
        ) {
            statement.setString(1, titleKey);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private List<VisibilityTriple> triplesFor(long menuId) throws Exception {
        String sql =
            "select role_code, permission_code, data_level from portal_menu_visibility where menu_id = ? " +
            "order by role_code, permission_code, data_level";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, menuId);
            try (ResultSet result = statement.executeQuery()) {
                var triples = new java.util.ArrayList<VisibilityTriple>();
                while (result.next()) {
                    triples.add(new VisibilityTriple(result.getString(1), result.getString(2), result.getString(3)));
                }
                return triples;
            }
        }
    }

    private List<String> allRolesOnActiveAccessMenus() throws Exception {
        String sql =
            "select distinct visibility.role_code from portal_menu_visibility visibility " +
            "join portal_menu menu on menu.id = visibility.menu_id " +
            "where menu.deleted = false and (menu.parent_id = ? or menu.parent_id in " +
            "(select id from portal_menu where parent_id = ? and name = 'sys.nav.portal.resourceRuntime' and deleted = false)) " +
            "order by visibility.role_code";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, RESOURCE_ID);
            statement.setLong(2, RESOURCE_ID);
            try (ResultSet result = statement.executeQuery()) {
                var roles = new java.util.ArrayList<String>();
                while (result.next()) {
                    roles.add(result.getString(1));
                }
                return roles;
            }
        }
    }

    private long parentId(long menuId) throws Exception {
        return longValue("select parent_id from portal_menu where id = " + menuId);
    }

    private boolean isDeleted(long menuId) throws Exception {
        return booleanValue("select deleted from portal_menu where id = " + menuId);
    }

    private String menuName(long menuId) throws Exception {
        return stringValue("select name from portal_menu where id = " + menuId);
    }

    private String lastModifiedBy(long menuId) throws Exception {
        return stringValue("select last_modified_by from portal_menu where id = " + menuId);
    }

    private MenuState menuState(long menuId) throws Exception {
        String sql =
            "select name, path, component, sort_order, metadata, parent_id, icon, security_level, deleted, " +
            "last_modified_by from portal_menu where id = ?";
        try (Connection connection = connectionInSchema(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, menuId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new MenuState(
                    result.getString(1),
                    result.getString(2),
                    result.getString(3),
                    result.getObject(4, Integer.class),
                    result.getString(5),
                    result.getObject(6, Long.class),
                    result.getString(7),
                    result.getString(8),
                    result.getBoolean(9),
                    result.getString(10)
                );
            }
        }
    }

    private Map<Long, MenuState> allMenuStates() throws Exception {
        Map<Long, MenuState> states = new LinkedHashMap<>();
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
            "select id from portal_menu order by id"
        )) {
            while (result.next()) {
                long menuId = result.getLong(1);
                states.put(menuId, menuState(menuId));
            }
        }
        return states;
    }

    private Map<Long, java.sql.Timestamp> allLastModifiedDates() throws Exception {
        Map<Long, java.sql.Timestamp> timestamps = new LinkedHashMap<>();
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
            "select id, last_modified_date from portal_menu order by id"
        )) {
            while (result.next()) {
                timestamps.put(result.getLong(1), result.getTimestamp(2));
            }
        }
        return timestamps;
    }

    private long countMigrationVisibilityRows() throws Exception {
        return count("portal_menu_visibility", "created_by = '" + MIGRATION_ACTOR + "'");
    }

    private java.sql.Timestamp lastModifiedDate(long menuId) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement("select last_modified_date from portal_menu where id = ?")
        ) {
            statement.setLong(1, menuId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getTimestamp(1);
            }
        }
    }

    private long count(String table, String predicate) throws Exception {
        return longValue("select count(*) from " + table + " where " + predicate);
    }

    private long longValue(String sql) throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private boolean booleanValue(String sql) throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private String stringValue(String sql) throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private void applyChangelog() throws Exception {
        runLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
    }

    private void rollbackChangelog() throws Exception {
        runLiquibase(liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
    }

    private void runLiquibase(LiquibaseOperation operation) throws Exception {
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
                    operation.run(liquibase);
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private Connection connectionInSchema() throws Exception {
        Connection connection = openConnection();
        connection.setSchema(schema);
        return connection;
    }

    private Connection openConnection() throws Exception {
        return java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("拒绝操作非测试 schema：" + schema);
        }
    }

    @FunctionalInterface
    private interface LiquibaseOperation {
        void run(Liquibase liquibase) throws Exception;
    }

    private record VisibilityTriple(String roleCode, String permissionCode, String dataLevel) {}

    private record MenuState(
        String name,
        String path,
        String component,
        Integer sortOrder,
        String metadata,
        Long parentId,
        String icon,
        String securityLevel,
        boolean deleted,
        String lastModifiedBy
    ) {}
}
