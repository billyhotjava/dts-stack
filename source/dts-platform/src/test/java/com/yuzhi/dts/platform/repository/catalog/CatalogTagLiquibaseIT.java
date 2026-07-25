package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class CatalogTagLiquibaseIT {

    private static final String CHANGELOG = "config/liquibase/changelog/20260725_02_catalog_tag.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s71_catalog_tag_[0-9a-f]{32}$");
    private static final String ATOMIC_INSERT =
        """
        insert into catalog_asset_tag (
            id, tag_id, asset_type, asset_key, tagged_by, tagged_at, migration_batch,
            created_by, created_date, last_modified_by, last_modified_date
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (tag_id, asset_type, asset_key) do nothing
        """;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogTagIT")
        .withUsername("catalog_tag_test")
        .withPassword("catalog_tag_test");

    private String schema;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema = "s71_catalog_tag_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        applyChangelog();
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
    void liquibaseCreatesRealTablesForeignKeysAndUniqueConstraints() throws Exception {
        UUID categoryId = insertCategory("QUALITY");
        UUID tagId = insertTag(categoryId, "QUALITY-TRUSTED");

        assertThat(tableExists("catalog_tag_category")).isTrue();
        assertThat(tableExists("catalog_tag")).isTrue();
        assertThat(tableExists("catalog_asset_tag")).isTrue();
        assertThat(constraintExists("fk_catalog_tag_category_parent")).isTrue();
        assertThat(constraintExists("fk_catalog_tag_category")).isTrue();
        assertThat(constraintExists("fk_catalog_asset_tag_tag")).isTrue();
        assertThat(constraintExists("uk_catalog_tag_category_code")).isTrue();
        assertThat(constraintExists("uk_catalog_tag_code")).isTrue();
        assertThat(constraintExists("uk_catalog_asset_tag")).isTrue();
        assertThat(indexExists("idx_catalog_tag_category_parent")).isTrue();
        assertThat(indexExists("idx_catalog_tag_category")).isTrue();
        assertThat(indexExists("idx_catalog_asset_tag_asset")).isTrue();
        assertThat(indexExists("idx_catalog_asset_tag_tag")).isTrue();
        assertThat(indexExists("idx_catalog_asset_tag_migration_batch")).isTrue();

        assertThatThrownBy(() -> insertCategory("QUALITY"))
            .isInstanceOf(SQLException.class)
            .extracting(exception -> ((SQLException) exception).getSQLState())
            .isEqualTo("23505");
        assertThatThrownBy(() -> insertTag(UUID.randomUUID(), "BROKEN-CATEGORY"))
            .isInstanceOf(SQLException.class)
            .extracting(exception -> ((SQLException) exception).getSQLState())
            .isEqualTo("23503");
        assertThatThrownBy(() -> insertAssignment(UUID.randomUUID(), "METRIC", "metric:missing/tag"))
            .isInstanceOf(SQLException.class)
            .extracting(exception -> ((SQLException) exception).getSQLState())
            .isEqualTo("23503");

        assertThat(tagId).isNotNull();
    }

    @Test
    void categoryParentRelationPersistsAndLoadsWithANullRoot() throws Exception {
        UUID rootId = insertCategory("BUSINESS");
        UUID childId = insertChildCategory("FINANCE", rootId);

        assertThat(categoryParentId(rootId)).isNull();
        assertThat(categoryParentId(childId)).isEqualTo(rootId);
    }

    @Test
    void concurrentAtomicInsertCommitsOneRowAndSkipsTheConflict() throws Exception {
        UUID tagId = insertTag(insertCategory("QUALITY"), "QUALITY-TRUSTED");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> updates = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try (Connection connection = connectionInSchema()) {
                        connection.setAutoCommit(false);
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        updates.add(
                            atomicInsert(
                                connection,
                                tagId,
                                "METRIC",
                                "metric:core/revenue",
                                "concurrent-user"
                            )
                        );
                        connection.commit();
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(updates).containsExactlyInAnyOrder(0, 1);
        assertThat(rowCount("catalog_asset_tag")).isEqualTo(1);
    }

    @Test
    void failedBatchTransactionRollsBackRowsWrittenBeforeTheForeignKeyFailure() throws Exception {
        UUID tagId = insertTag(insertCategory("QUALITY"), "QUALITY-TRUSTED");
        try (Connection connection = connectionInSchema()) {
            connection.setAutoCommit(false);
            assertThat(atomicInsert(connection, tagId, "METRIC", "metric:core/revenue", "alice"))
                .isEqualTo(1);
            assertThatThrownBy(() ->
                    atomicInsert(
                        connection,
                        UUID.randomUUID(),
                        "DATA_PRODUCT",
                        "tenant:default/data-product:orders",
                        "alice"
                    )
                )
                .isInstanceOf(SQLException.class)
                .extracting(exception -> ((SQLException) exception).getSQLState())
                .isEqualTo("23503");
            connection.rollback();
        }

        assertThat(rowCount("catalog_asset_tag")).isZero();
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
                    liquibase.setChangeLogParameter("uuidType", "uuid");
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private UUID insertCategory(String code) throws Exception {
        UUID id = UUID.randomUUID();
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into catalog_tag_category (
                    id, code, name, sort_order, builtin, enabled, description,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, 0, false, true, null, 'it-user', current_timestamp, 'it-user', current_timestamp)
                """
            )
        ) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, code);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertTag(UUID categoryId, String code) throws Exception {
        UUID id = UUID.randomUUID();
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into catalog_tag (
                    id, category_id, code, name, color, builtin, enabled, description,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, '#1677FF', false, true, null,
                          'it-user', current_timestamp, 'it-user', current_timestamp)
                """
            )
        ) {
            statement.setObject(1, id);
            statement.setObject(2, categoryId);
            statement.setString(3, code);
            statement.setString(4, code);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertChildCategory(String code, UUID parentId) throws Exception {
        UUID id = UUID.randomUUID();
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into catalog_tag_category (
                    id, code, name, parent_id, sort_order, builtin, enabled, description,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, 0, false, true, null,
                          'it-user', current_timestamp, 'it-user', current_timestamp)
                """
            )
        ) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, code);
            statement.setObject(4, parentId);
            statement.executeUpdate();
        }
        return id;
    }

    private void insertAssignment(UUID tagId, String assetType, String assetKey) throws Exception {
        try (Connection connection = connectionInSchema()) {
            atomicInsert(connection, tagId, assetType, assetKey, "it-user");
        }
    }

    private int atomicInsert(
        Connection connection,
        UUID tagId,
        String assetType,
        String assetKey,
        String actor
    ) throws Exception {
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(ATOMIC_INSERT)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tagId);
            statement.setString(3, assetType);
            statement.setString(4, assetKey);
            statement.setString(5, actor);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setNull(7, java.sql.Types.VARCHAR);
            statement.setString(8, actor);
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setString(10, actor);
            statement.setTimestamp(11, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        return selectBoolean(
            "select to_regclass(?) is not null",
            schema + "." + tableName
        );
    }

    private boolean constraintExists(String constraintName) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from information_schema.table_constraints
                    where constraint_schema = ? and constraint_name = ?
                )
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, constraintName);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private boolean indexExists(String indexName) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                    from pg_indexes
                    where schemaname = ? and indexname = ?
                )
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, indexName);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private UUID categoryParentId(UUID id) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "select parent_id from catalog_tag_category where id = ?"
            )
        ) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private long rowCount(String tableName) throws Exception {
        requireOwnedSchema();
        try (
            Connection connection = connectionInSchema();
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from " + tableName)
        ) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private boolean selectBoolean(String sql, String value) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
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
}
