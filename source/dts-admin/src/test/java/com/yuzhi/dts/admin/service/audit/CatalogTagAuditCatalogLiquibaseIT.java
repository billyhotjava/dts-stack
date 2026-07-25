package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.w3c.dom.Element;

@Testcontainers
class CatalogTagAuditCatalogLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260725-01_catalog_tag_audit_catalog.xml";
    private static final String MASTER_CHANGELOG = "config/liquibase/master.xml";
    private static final String ACTION_CATALOG_CHANGELOG =
        "config/liquibase/changelog/20260523-01_audit_action_catalog.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s71_audit_catalog_[0-9a-f]{32}$");

    private static final Map<String, ActionExpectation> EXPECTED_ACTIONS = Map.ofEntries(
        Map.entry("CATALOG_TAG_CATEGORY_CREATE", new ActionExpectation("CREATE", "catalog_tag_category")),
        Map.entry("CATALOG_TAG_CATEGORY_UPDATE", new ActionExpectation("UPDATE", "catalog_tag_category")),
        Map.entry("CATALOG_TAG_CATEGORY_DELETE", new ActionExpectation("DELETE", "catalog_tag_category")),
        Map.entry("CATALOG_TAG_CREATE", new ActionExpectation("CREATE", "catalog_tag")),
        Map.entry("CATALOG_TAG_UPDATE", new ActionExpectation("UPDATE", "catalog_tag")),
        Map.entry("CATALOG_TAG_DELETE", new ActionExpectation("DELETE", "catalog_tag")),
        Map.entry("CATALOG_ASSET_TAG_CREATE", new ActionExpectation("CREATE", "catalog_asset_tag")),
        Map.entry("CATALOG_ASSET_TAG_DELETE", new ActionExpectation("DELETE", "catalog_asset_tag")),
        Map.entry("CATALOG_ASSET_TAG_BATCH_CREATE", new ActionExpectation("CREATE", "catalog_asset_tag")),
        Map.entry("CATALOG_TAG_BUILTIN_INSTALL", new ActionExpectation("CREATE", "catalog_tag")),
        Map.entry("CATALOG_TAG_MIGRATION_DRY_RUN", new ActionExpectation("READ", "catalog_asset_tag")),
        Map.entry("CATALOG_TAG_MIGRATION_EXECUTE", new ActionExpectation("CREATE", "catalog_asset_tag")),
        Map.entry("CATALOG_TAG_MIGRATION_ROLLBACK", new ActionExpectation("DELETE", "catalog_asset_tag"))
    );

    private static final Map<OperationKey, String> EXPECTED_OPERATIONS = Map.ofEntries(
        Map.entry(new OperationKey("POST", "/api/catalog/tag-categories"), "CREATE"),
        Map.entry(new OperationKey("PUT", "/api/catalog/tag-categories/{id}"), "UPDATE"),
        Map.entry(new OperationKey("DELETE", "/api/catalog/tag-categories/{id}"), "DELETE"),
        Map.entry(new OperationKey("POST", "/api/catalog/tags"), "CREATE"),
        Map.entry(new OperationKey("PUT", "/api/catalog/tags/{id}"), "UPDATE"),
        Map.entry(new OperationKey("DELETE", "/api/catalog/tags/{id}"), "DELETE"),
        Map.entry(new OperationKey("POST", "/api/catalog/asset-tags"), "CREATE"),
        Map.entry(new OperationKey("DELETE", "/api/catalog/asset-tags"), "DELETE"),
        Map.entry(new OperationKey("POST", "/api/catalog/asset-tags/batch"), "CREATE"),
        Map.entry(new OperationKey("POST", "/api/catalog/tags/builtin/install"), "CREATE"),
        Map.entry(new OperationKey("POST", "/api/catalog/tag-migrations/dry-run"), "READ"),
        Map.entry(new OperationKey("POST", "/api/catalog/tag-migrations/{batchId}/execute"), "CREATE"),
        Map.entry(new OperationKey("POST", "/api/catalog/tag-migrations/{batchId}/rollback"), "DELETE")
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogTagAuditIT")
        .withUsername("catalog_tag_audit_test")
        .withPassword("catalog_tag_audit_test");

    private String schema;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema = "s71_audit_catalog_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        createPrerequisiteTables();
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
    void masterIncludesRollbackCapableCatalogTagCatalogImmediatelyAfterActionCatalog() throws Exception {
        var document = parseClasspathXml(MASTER_CHANGELOG);
        List<String> includes = new ArrayList<>();
        var nodes = document.getElementsByTagNameNS("*", "include");
        for (int index = 0; index < nodes.getLength(); index++) {
            includes.add(((Element) nodes.item(index)).getAttribute("file"));
        }

        int actionCatalogIndex = includes.indexOf(ACTION_CATALOG_CHANGELOG);
        int catalogTagIndex = includes.indexOf(CHANGELOG);
        assertThat(actionCatalogIndex).isNotNegative();
        assertThat(catalogTagIndex).isEqualTo(actionCatalogIndex + 1);

        var tagChangelog = parseClasspathXml(CHANGELOG);
        assertThat(tagChangelog.getElementsByTagNameNS("*", "rollback").getLength()).isEqualTo(1);
    }

    @Test
    void changelogRegistersCanonicalResourceModuleActionsAndHttpMappings() throws Exception {
        applyChangelog();

        assertResourceDictionary();
        assertModuleAndActions();
        assertOperationMappings();
    }

    @Test
    void rollbackRemovesSprintOwnedRowsAndAllowsCleanReapply() throws Exception {
        applyChangelog();
        rollbackChangelog();

        assertThat(count("audit_resource_dictionary", "resource_key = 'catalog_tag'")).isZero();
        assertThat(
            count(
                "audit_module_catalog",
                "source_system = 'platform' and module_key = 'catalog.tags'"
            )
        )
            .isZero();
        assertThat(
            count(
                "audit_action_catalog",
                "source_system = 'platform' and action_code like 'CATALOG%TAG%'"
            )
        )
            .isZero();
        assertThat(
            count(
                "audit_operation_mapping",
                "source_system = 'platform' and module_name = '数据标签'"
            )
        )
            .isZero();

        applyChangelog();

        assertResourceDictionary();
        assertModuleAndActions();
        assertOperationMappings();
    }

    @Test
    void changelogRefusesToOverwritePreexistingNaturalKeys() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                insert into audit_resource_dictionary (
                    resource_key, display_name, category, aliases, order_value, enabled
                ) values (
                    'catalog_tag', '客户既有标签配置', '客户扩展', 'custom.tag', 7, false
                )
                """
            );
        }

        assertThatThrownBy(this::applyChangelog)
            .hasStackTraceContaining("refusing to overwrite pre-existing data");

        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select display_name, category, aliases, order_value, enabled
                  from audit_resource_dictionary
                 where resource_key = 'catalog_tag'
                """
            );
            ResultSet result = statement.executeQuery()
        ) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("display_name")).isEqualTo("客户既有标签配置");
            assertThat(result.getString("category")).isEqualTo("客户扩展");
            assertThat(result.getString("aliases")).isEqualTo("custom.tag");
            assertThat(result.getInt("order_value")).isEqualTo(7);
            assertThat(result.getBoolean("enabled")).isFalse();
            assertThat(result.next()).isFalse();
        }
    }

    private void assertResourceDictionary() throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select display_name, category, aliases, enabled
                from audit_resource_dictionary
                where resource_key = 'catalog_tag'
                """
            );
            ResultSet result = statement.executeQuery()
        ) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("display_name")).isEqualTo("数据标签");
            assertThat(result.getString("category")).isEqualTo("数据标签");
            assertThat(Set.of(result.getString("aliases").split(",")))
                .contains(
                    "catalog_tag",
                    "catalog.tags",
                    "catalog.tag",
                    "catalog.tag-categories",
                    "catalog.asset-tags"
                );
            assertThat(result.getBoolean("enabled")).isTrue();
            assertThat(result.next()).isFalse();
        }
    }

    private void assertModuleAndActions() throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select module_name, enabled
                from audit_module_catalog
                where source_system = 'platform' and module_key = 'catalog.tags'
                """
            );
            ResultSet result = statement.executeQuery()
        ) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("module_name")).isEqualTo("数据标签");
            assertThat(result.getBoolean("enabled")).isTrue();
            assertThat(result.next()).isFalse();
        }

        Map<String, ActionRow> actual = new LinkedHashMap<>();
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select action_code, module_key, module_name, operation_kind, resource_type,
                       allow_empty_targets, enabled
                from audit_action_catalog
                where source_system = 'platform' and action_code = any (?)
                order by action_code
                """
            )
        ) {
            statement.setArray(
                1,
                connection.createArrayOf("varchar", EXPECTED_ACTIONS.keySet().toArray(String[]::new))
            );
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    actual.put(
                        result.getString("action_code"),
                        new ActionRow(
                            result.getString("module_key"),
                            result.getString("module_name"),
                            result.getString("operation_kind"),
                            result.getString("resource_type"),
                            result.getBoolean("allow_empty_targets"),
                            result.getBoolean("enabled")
                        )
                    );
                }
            }
        }

        assertThat(actual).containsOnlyKeys(EXPECTED_ACTIONS.keySet());
        EXPECTED_ACTIONS.forEach((code, expectation) -> {
            ActionRow row = actual.get(code);
            assertThat(row.moduleKey()).as(code).isEqualTo("catalog.tags").isNotEqualTo("platform.unclassified");
            assertThat(row.moduleName()).as(code).isEqualTo("数据标签");
            assertThat(row.operationKind()).as(code).isEqualTo(expectation.operationKind());
            assertThat(row.resourceType()).as(code).isEqualTo(expectation.resourceType());
            assertThat(row.allowEmptyTargets()).as(code).isFalse();
            assertThat(row.enabled()).as(code).isTrue();
        });
    }

    private void assertOperationMappings() throws Exception {
        List<OperationRow> rows = new ArrayList<>();
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select http_method, url_pattern, module_name, operation_type,
                       description_template, source_table_template, event_class,
                       order_value, enabled, source_system
                from audit_operation_mapping
                where source_system = 'platform' and module_name = '数据标签'
                order by url_pattern, http_method
                """
            );
            ResultSet result = statement.executeQuery()
        ) {
            while (result.next()) {
                rows.add(
                    new OperationRow(
                        new OperationKey(result.getString("http_method"), result.getString("url_pattern")),
                        result.getString("module_name"),
                        result.getString("operation_type"),
                        result.getString("description_template"),
                        result.getString("source_table_template"),
                        result.getString("event_class"),
                        result.getInt("order_value"),
                        result.getBoolean("enabled"),
                        result.getString("source_system")
                    )
                );
            }
        }

        assertThat(rows).hasSize(EXPECTED_OPERATIONS.size());
        Map<OperationKey, OperationRow> byKey = rows
            .stream()
            .collect(LinkedHashMap::new, (result, row) -> result.put(row.key(), row), Map::putAll);
        assertThat(byKey).containsOnlyKeys(EXPECTED_OPERATIONS.keySet());
        EXPECTED_OPERATIONS.forEach((key, operationType) -> {
            OperationRow row = byKey.get(key);
            assertThat(row.moduleName()).as(key.toString()).isEqualTo("数据标签");
            assertThat(row.operationType()).as(key.toString()).isEqualTo(operationType);
            assertThat(row.description()).as(key.toString()).isNotBlank();
            assertThat(row.sourceTable()).as(key.toString()).isEqualTo("数据标签");
            assertThat(row.eventClass()).as(key.toString()).isEqualTo("AuditEvent");
            assertThat(row.enabled()).as(key.toString()).isTrue();
            assertThat(row.sourceSystem()).as(key.toString()).isEqualTo("platform");
        });

        int tagIdRouteOrder = byKey.get(new OperationKey("PUT", "/api/catalog/tags/{id}")).orderValue();
        assertThat(byKey.get(new OperationKey("POST", "/api/catalog/tags/builtin/install")).orderValue())
            .isLessThan(tagIdRouteOrder);
        assertThat(byKey.get(new OperationKey("POST", "/api/catalog/tag-migrations/dry-run")).orderValue())
            .isLessThan(tagIdRouteOrder);
        assertThat(
            byKey.get(new OperationKey("POST", "/api/catalog/tag-migrations/{batchId}/execute")).orderValue()
        )
            .isLessThan(tagIdRouteOrder);
        assertThat(
            byKey.get(new OperationKey("POST", "/api/catalog/tag-migrations/{batchId}/rollback")).orderValue()
        )
            .isLessThan(tagIdRouteOrder);
    }

    private long count(String table, String predicate) throws Exception {
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from " + table + " where " + predicate)
        ) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void createPrerequisiteTables() throws Exception {
        try (Connection connection = connectionInSchema(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                create table audit_resource_dictionary (
                    id bigserial primary key,
                    resource_key varchar(128) not null unique,
                    display_name varchar(128) not null,
                    category varchar(64),
                    aliases text,
                    order_value int default 0,
                    enabled boolean default true,
                    updated_at timestamp default current_timestamp
                )
                """
            );
            statement.execute(
                """
                create table audit_module_catalog (
                    id bigserial primary key,
                    source_system varchar(32) not null,
                    module_key varchar(128) not null,
                    module_name varchar(128) not null,
                    parent_module_key varchar(128),
                    owner varchar(128),
                    enabled boolean not null default true,
                    order_value int default 0,
                    version varchar(32),
                    updated_at timestamptz default current_timestamp,
                    unique (source_system, module_key)
                )
                """
            );
            statement.execute(
                """
                create table audit_action_catalog (
                    id bigserial primary key,
                    source_system varchar(32) not null,
                    action_code varchar(128) not null,
                    module_key varchar(128) not null,
                    module_name varchar(128) not null,
                    operation_code varchar(128) not null,
                    operation_name varchar(256) not null,
                    operation_kind varchar(32) not null,
                    resource_type varchar(128),
                    allow_empty_targets boolean not null default false,
                    enabled boolean not null default true,
                    version varchar(32),
                    updated_at timestamptz default current_timestamp,
                    unique (source_system, action_code)
                )
                """
            );
            statement.execute(
                """
                create table audit_operation_mapping (
                    id bigserial primary key,
                    url_pattern varchar(512) not null,
                    http_method varchar(16) not null,
                    module_name varchar(128) not null,
                    operation_type varchar(32) not null,
                    description_template varchar(1024) not null,
                    source_table_template varchar(256) not null,
                    param_extractors text,
                    event_class varchar(64) default 'AuditEvent',
                    order_value int default 0,
                    enabled boolean default true,
                    source_system varchar(32)
                )
                """
            );
        }
    }

    private org.w3c.dom.Document parseClasspathXml(String resource) throws Exception {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private void applyChangelog() throws Exception {
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
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void rollbackChangelog() throws Exception {
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
                    liquibase.rollback(1, new Contexts(), new LabelExpression());
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
        return java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("拒绝操作非测试 schema：" + schema);
        }
    }

    private record ActionExpectation(String operationKind, String resourceType) {}

    private record ActionRow(
        String moduleKey,
        String moduleName,
        String operationKind,
        String resourceType,
        boolean allowEmptyTargets,
        boolean enabled
    ) {}

    private record OperationKey(String method, String path) {}

    private record OperationRow(
        OperationKey key,
        String moduleName,
        String operationType,
        String description,
        String sourceTable,
        String eventClass,
        int orderValue,
        boolean enabled,
        String sourceSystem
    ) {}
}
