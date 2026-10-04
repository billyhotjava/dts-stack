package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookup;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookupRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogAssetTagPermissionReadAdapterIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260725_02_catalog_tag.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s71_permission_adapter_[0-9a-f]{32}$"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("catalogAssetTagPermissionReadAdapterIT")
            .withUsername("catalog_asset_permission_test")
            .withPassword("catalog_asset_permission_test");

    private String schema;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema =
            "s71_permission_adapter_" +
            UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                "create table om_asset_cache (id uuid primary key, fqn varchar(1024) not null)"
            );
            statement.execute(
                "create table catalog_asset_mapping (id uuid primary key, fqn varchar(1024) not null, legacy_dataset_id uuid)"
            );
            statement.execute(
                """
                create table catalog_dataset (
                    id uuid primary key,
                    source_id uuid,
                    hive_database varchar(128),
                    hive_table varchar(128),
                    name varchar(128),
                    owner_dept varchar(128),
                    classification varchar(64),
                    enabled boolean
                )
                """
            );
        }
        applyChangelog();
    }

    @AfterEach
    void dropSchema() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void migrationIndexesAreEligibleForBothPermissionLookupExpressions()
        throws Exception {
        try (Connection connection = connectionInSchema()) {
            insertOpenMetadataAssets(
                connection,
                List.of("service.db.schema.table_name")
            );
            try (
                PreparedStatement statement = connection.prepareStatement(
                    "insert into catalog_asset_mapping (id, fqn, legacy_dataset_id) values (?, ?, null)"
                )
            ) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, "Service.DB.Schema.Table_Name");
                statement.executeUpdate();
            }
            RecordingJdbcTemplate jdbcTemplate = recordingTemplate(connection);
            BatchLookup result = new CatalogAssetTagPermissionReadAdapter(
                jdbcTemplate
            ).loadBatch(
                new BatchLookupRequest(
                    Set.of(),
                    Set.of("om:service.db.schema.table_name"),
                    java.util.Map.of()
                )
            );

            assertThat(result.openMetadataDatasets()).hasSize(1);
            QueryInvocation invocation = jdbcTemplate.singleQuery();
            assertThat(
                explainWithSequentialScanDisabled(connection, invocation)
            )
                .contains(
                    "idx_om_asset_cache_canonical_fqn",
                    "idx_catalog_asset_mapping_fqn_ci"
                );
        }

        rollbackIndexChangeSets();

        assertThat(indexExists("idx_om_asset_cache_canonical_fqn")).isFalse();
        assertThat(indexExists("idx_catalog_asset_mapping_fqn_ci")).isFalse();
    }

    @Test
    void lookupTreatsUnderscoreLiterallyAndPreservesCanonicalCollisions()
        throws Exception {
        try (Connection connection = connectionInSchema()) {
            insertOpenMetadataAssets(
                connection,
                List.of(
                    "service.db.schema.table_name",
                    "service.db.schema.tableXname",
                    "service.db.schema.Order Detail",
                    "service.db.schema.order_detail"
                )
            );
            CatalogAssetTagPermissionReadAdapter adapter = adapter(connection);
            String underscoreKey = CatalogAssetKey.openMetadataDataset(
                "service.db.schema.table_name"
            );
            String collisionKey = CatalogAssetKey.openMetadataDataset(
                "service.db.schema.Order Detail"
            );

            BatchLookup result = adapter.loadBatch(
                new BatchLookupRequest(
                    Set.of(),
                    Set.of(underscoreKey, collisionKey),
                    java.util.Map.of()
                )
            );

            assertThat(result.openMetadataDatasets())
                .filteredOn(identity ->
                    underscoreKey.equals(
                        CatalogAssetKey.openMetadataDataset(identity.fqn())
                    )
                )
                .singleElement()
                .extracting(
                    CatalogAssetTagPermissionReadAdapter.OpenMetadataDatasetIdentity::fqn
                )
                .isEqualTo("service.db.schema.table_name");
            assertThat(result.openMetadataDatasets())
                .filteredOn(identity ->
                    collisionKey.equals(
                        CatalogAssetKey.openMetadataDataset(identity.fqn())
                    )
                )
                .hasSize(2);
        }
    }

    @Test
    void fiveHundredCanonicalKeysReturnEveryPersistedCandidate()
        throws Exception {
        List<String> fqns = IntStream
            .range(0, 500)
            .mapToObj(index -> "service.db.schema.table_" + index)
            .toList();
        Set<String> keys = fqns
            .stream()
            .map(CatalogAssetKey::openMetadataDataset)
            .collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)
            );
        try (Connection connection = connectionInSchema()) {
            insertOpenMetadataAssets(connection, fqns);

            BatchLookup result = adapter(connection).loadBatch(
                new BatchLookupRequest(Set.of(), keys, java.util.Map.of())
            );

            assertThat(result.openMetadataDatasets()).hasSize(500);
            assertThat(result.openMetadataDatasets())
                .extracting(identity ->
                    CatalogAssetKey.openMetadataDataset(identity.fqn())
                )
                .containsExactlyInAnyOrderElementsOf(keys);
        }
    }

    @Test
    void legacyDatasetLookupRetainsSecurityFieldsRequiredByReadVisibility()
        throws Exception {
        UUID datasetId = UUID.fromString(
            "6e45fd13-d781-4667-90b6-3554380500b1"
        );
        UUID sourceId = UUID.fromString(
            "a0000000-0000-0000-0000-000000000001"
        );
        try (Connection connection = connectionInSchema()) {
            try (
                PreparedStatement statement = connection.prepareStatement(
                    """
                    insert into catalog_dataset (
                        id, source_id, hive_database, hive_table, name,
                        owner_dept, classification, enabled
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """
                )
            ) {
                statement.setObject(1, datasetId);
                statement.setObject(2, sourceId);
                statement.setString(3, "public");
                statement.setString(4, "ods_risk_info_v2");
                statement.setString(5, "ods_risk_info_v2");
                statement.setString(6, null);
                statement.setString(7, "INTERNAL");
                statement.setBoolean(8, true);
                statement.executeUpdate();
            }

            BatchLookup result = adapter(connection).loadBatch(
                new BatchLookupRequest(
                    Set.of(
                        "source:a0000000-0000-0000-0000-000000000001/schema:public/table:ods_risk_info_v2"
                    ),
                    Set.of(),
                    java.util.Map.of()
                )
            );

            assertThat(result.legacyDatasets())
                .singleElement()
                .satisfies(dataset -> {
                    assertThat(dataset.getId()).isEqualTo(datasetId);
                    assertThat(dataset.getClassification())
                        .isEqualTo("INTERNAL");
                    assertThat(dataset.getEnabled()).isTrue();
                });
        }
    }

    private CatalogAssetTagPermissionReadAdapter adapter(
        Connection connection
    ) {
        return new CatalogAssetTagPermissionReadAdapter(
            new JdbcTemplate(new SingleConnectionDataSource(connection, true))
        );
    }

    private RecordingJdbcTemplate recordingTemplate(Connection connection) {
        return new RecordingJdbcTemplate(
            new SingleConnectionDataSource(connection, true)
        );
    }

    private void insertOpenMetadataAssets(
        Connection connection,
        List<String> fqns
    ) throws Exception {
        try (
            PreparedStatement statement = connection.prepareStatement(
                "insert into om_asset_cache (id, fqn) values (?, ?)"
            )
        ) {
            for (String fqn : fqns) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, fqn);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String explainWithSequentialScanDisabled(
        Connection connection,
        QueryInvocation invocation
    ) throws Exception {
        try (Statement setting = connection.createStatement()) {
            setting.execute("set enable_seqscan = off");
        }
        try (
            PreparedStatement statement = connection.prepareStatement(
                "explain (format json) " + invocation.sql()
            )
        ) {
            for (int index = 0; index < invocation.arguments().length; index++) {
                statement.setObject(index + 1, invocation.arguments()[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        } finally {
            try (Statement setting = connection.createStatement()) {
                setting.execute("reset enable_seqscan");
            }
        }
    }

    private void rollbackIndexChangeSets() throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources =
                new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(
                    new JdbcConnection(connection)
                );
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (
                    Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        database
                    )
                ) {
                    liquibase.rollback(
                        2,
                        new Contexts(),
                        new LabelExpression()
                    );
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private boolean indexExists(String indexName) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                  from pg_catalog.pg_class index_relation
                  join pg_catalog.pg_namespace index_schema
                    on index_schema.oid = index_relation.relnamespace
                 where index_schema.nspname = ?
                   and index_relation.relname = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private void applyChangelog() throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources =
                new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(
                    new JdbcConnection(connection)
                );
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (
                    Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        resources,
                        database
                    )
                ) {
                    liquibase.update(
                        new Contexts(),
                        new LabelExpression()
                    );
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
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
            throw new IllegalStateException(
                "Refusing to mutate an unowned schema: " + schema
            );
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private QueryInvocation query;

        private RecordingJdbcTemplate(
            SingleConnectionDataSource dataSource
        ) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(
            String sql,
            RowMapper<T> rowMapper,
            Object... args
        ) {
            query = new QueryInvocation(sql, args.clone());
            return super.query(sql, rowMapper, args);
        }

        private QueryInvocation singleQuery() {
            assertThat(query).isNotNull();
            return query;
        }
    }

    private record QueryInvocation(String sql, Object[] arguments) {}
}
