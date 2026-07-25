package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry.Registration;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@Testcontainers
class CatalogExternalAssetIdentityLiquibaseIT {

    private static final String IDENTITY_CHANGELOG =
        "config/liquibase/changelog/20260725_21_catalog_external_asset_identity.xml";
    private static final String SYNC_CHANGELOG =
        "config/liquibase/changelog/20260725_22_catalog_external_asset_sync_state.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s71_external_identity_[0-9a-f]{32}$"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("catalogExternalIdentityIT")
            .withUsername("catalog_external_identity_test")
            .withPassword("catalog_external_identity_test");

    private String schema;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema =
            "s71_external_identity_" +
            UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (
            Connection connection = openConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute("create schema " + schema);
        }
        update();
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
    void enforcesRemoteIdentityUniquenessAndSynchronizationScopePairing()
        throws Exception {
        String scope =
            "tenant:tenant-a/env:prod/dialect:generic/metric-pack:core";
        UUID runId = UUID.randomUUID();
        insertIdentity(
            "tenant:tenant-a/env:prod/dialect:generic/metric-pack:core/metric:revenue",
            "shared-remote-id",
            scope,
            runId
        );

        assertThat(
            constraintExists(
                "uk_catalog_external_asset_identity_remote"
            )
        )
            .isTrue();
        assertThat(
            constraintExists(
                "ck_catalog_external_asset_identity_sync_scope"
            )
        )
            .isTrue();
        assertThat(
            constraintExists("ck_catalog_external_asset_sync_batch")
        )
            .isTrue();
        assertThatThrownBy(() ->
            insertIdentity(
                "tenant:tenant-b/env:prod/dialect:generic/metric-pack:core/metric:revenue",
                "shared-remote-id",
                "tenant:tenant-b/env:prod/dialect:generic/metric-pack:core",
                UUID.randomUUID()
            )
        )
            .isInstanceOf(SQLException.class)
            .extracting(exception -> ((SQLException) exception).getSQLState())
            .isEqualTo("23505");
        assertThatThrownBy(() ->
            insertIdentity(
                "tenant:tenant-a/env:prod/dialect:generic/metric-pack:core/metric:margin",
                "margin-remote-id",
                scope,
                null
            )
        )
            .isInstanceOf(SQLException.class)
            .extracting(exception -> ((SQLException) exception).getSQLState())
            .isEqualTo("23514");
    }

    @Test
    void stagesPartialRunsAndRejectsSupersededCompletion()
        throws Exception {
        String source = "dts-metrics";
        String scope =
            "tenant:tenant-a/env:prod/dialect:generic/metric-pack:core";
        String baselineKey = scope + "/metric:legacy_revenue";
        String stagedKey = scope + "/metric:revenue";
        String winningKey = scope + "/metric:margin";
        UUID baselineRunId = UUID.randomUUID();
        UUID stagedRunId = UUID.randomUUID();
        UUID winningRunId = UUID.randomUUID();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        CodeAssetGrantWriter grantWriter = mock(
            CodeAssetGrantWriter.class
        );
        CatalogExternalAssetIdentityRegistry registry =
            new CatalogExternalAssetIdentityRegistry(
                jdbcTemplate,
                grantWriter
            );
        TransactionTemplate transaction = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                baselineRunId,
                0,
                1,
                true,
                List.of(
                    new Registration(
                        "METRIC",
                        baselineKey,
                        baselineKey,
                        "D00"
                    )
                )
            )
        );
        assertThat(activeKeys(jdbcTemplate, scope))
            .containsExactly(baselineKey);
        reset(grantWriter);

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                stagedRunId,
                0,
                2,
                false,
                List.of(
                    new Registration(
                        "METRIC",
                        baselineKey,
                        "changed-before-complete",
                        "D01"
                    ),
                    new Registration(
                        "METRIC",
                        stagedKey,
                        stagedKey,
                        "D01"
                    )
                )
            )
        );

        assertThat(identityExists(jdbcTemplate, stagedKey)).isFalse();
        assertThat(identityActive(jdbcTemplate, baselineKey)).isTrue();
        assertThat(identityRemoteId(jdbcTemplate, baselineKey))
            .isEqualTo(baselineKey);
        assertThat(identityOwnerDept(jdbcTemplate, baselineKey))
            .isEqualTo("D00");
        assertThat(activeKeys(jdbcTemplate, scope))
            .containsExactly(baselineKey);
        assertThat(
            syncState(jdbcTemplate, source, scope)
        )
            .containsEntry("active_run_id", stagedRunId)
            .containsEntry("next_batch_index", 1)
            .containsEntry("batch_count", 2);
        verifyNoInteractions(grantWriter);

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                winningRunId,
                0,
                1,
                true,
                List.of(
                    new Registration(
                        "METRIC",
                        winningKey,
                        winningKey,
                        "D02"
                    )
                )
            )
        );

        assertThat(activeKeys(jdbcTemplate, scope))
            .containsExactly(winningKey);
        assertThat(identityExists(jdbcTemplate, stagedKey)).isFalse();
        assertThat(identityActive(jdbcTemplate, baselineKey)).isFalse();
        assertThat(
            syncRunStatus(
                jdbcTemplate,
                source,
                scope,
                stagedRunId
            )
        )
            .isEqualTo("SUPERSEDED");
        assertThat(
            syncRunStatus(
                jdbcTemplate,
                source,
                scope,
                winningRunId
            )
        )
            .isEqualTo("COMPLETED");
        assertThat(
            syncState(jdbcTemplate, source, scope)
        )
            .containsEntry("last_completed_run_id", winningRunId)
            .containsEntry("next_batch_index", 0)
            .containsEntry("active_run_id", null)
            .containsEntry("batch_count", null);
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    winningKey,
                    winningKey,
                    source + ":" + winningKey
                ),
                "D02",
                source,
                "INTERNAL",
                "ACTIVE"
            );
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    baselineKey,
                    baselineKey,
                    source + ":" + baselineKey
                ),
                null,
                source,
                "INTERNAL",
                "INACTIVE"
            );

        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status ->
                registry.register(
                    source,
                    scope,
                    stagedRunId,
                    1,
                    2,
                    true,
                    List.of(
                        new Registration(
                            "METRIC",
                            scope + "/metric:orders",
                            scope + "/metric:orders",
                            "D01"
                        )
                    )
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("superseded");
        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status ->
                registry.register(
                    source,
                    scope,
                    stagedRunId,
                    0,
                    2,
                    false,
                    List.of(
                        new Registration(
                            "METRIC",
                            stagedKey,
                            stagedKey,
                            "D01"
                        )
                    )
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("superseded");
        assertThat(activeKeys(jdbcTemplate, scope))
            .containsExactly(winningKey);

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                baselineRunId,
                0,
                1,
                true,
                List.of(
                    new Registration(
                        "METRIC",
                        baselineKey,
                        baselineKey,
                        "D00"
                    )
                )
            )
        );
        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                winningRunId,
                0,
                1,
                true,
                List.of(
                    new Registration(
                        "METRIC",
                        winningKey,
                        winningKey,
                        "D02"
                    )
                )
            )
        );
        verifyNoMoreInteractions(grantWriter);
    }

    @Test
    void legacyUnknownCountBatchesRemainStagedUntilComplete()
        throws Exception {
        String source = "dts-metrics";
        String scope =
            "tenant:tenant-a/env:prod/dialect:generic/metric-pack:legacy";
        String firstKey = scope + "/metric:revenue";
        String finalKey = scope + "/metric:margin";
        UUID syncRunId = UUID.randomUUID();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        CodeAssetGrantWriter grantWriter = mock(
            CodeAssetGrantWriter.class
        );
        CatalogExternalAssetIdentityRegistry registry =
            new CatalogExternalAssetIdentityRegistry(
                jdbcTemplate,
                grantWriter
            );
        TransactionTemplate transaction = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                syncRunId,
                0,
                1,
                true,
                false,
                List.of(
                    new Registration(
                        "METRIC",
                        firstKey,
                        firstKey,
                        "D01"
                    )
                )
            )
        );

        assertThat(identityExists(jdbcTemplate, firstKey)).isFalse();
        assertThat(
            stagedItemCount(
                jdbcTemplate,
                source,
                scope,
                syncRunId
            )
        )
            .isEqualTo(1);
        verifyNoInteractions(grantWriter);

        transaction.executeWithoutResult(status ->
            registry.register(
                source,
                scope,
                syncRunId,
                0,
                1,
                true,
                true,
                List.of(
                    new Registration(
                        "METRIC",
                        finalKey,
                        finalKey,
                        "D01"
                    )
                )
            )
        );

        assertThat(activeKeys(jdbcTemplate, scope))
            .containsExactlyInAnyOrder(firstKey, finalKey);
        assertThat(
            stagedItemCount(
                jdbcTemplate,
                source,
                scope,
                syncRunId
            )
        )
            .isZero();
        assertThat(
            syncRunStatus(
                jdbcTemplate,
                source,
                scope,
                syncRunId
            )
        )
            .isEqualTo("COMPLETED");
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    firstKey,
                    firstKey,
                    source + ":" + firstKey
                ),
                "D01",
                source,
                "INTERNAL",
                "ACTIVE"
            );
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    finalKey,
                    finalKey,
                    source + ":" + finalKey
                ),
                "D01",
                source,
                "INTERNAL",
                "ACTIVE"
            );
    }

    @Test
    void upgradesAfterIdentityChangeSetWasAlreadyApplied()
        throws Exception {
        rollbackChangeLog(SYNC_CHANGELOG);

        assertThat(
            tableExists("catalog_external_asset_identity")
        ).isTrue();
        assertThat(
            tableExists("catalog_external_asset_sync_state")
        ).isFalse();

        update();

        assertThat(
            tableExists("catalog_external_asset_identity")
        ).isTrue();
        assertThat(
            tableExists("catalog_external_asset_sync_state")
        ).isTrue();
        assertThat(
            tableExists("catalog_external_asset_sync_run")
        ).isTrue();
        assertThat(
            tableExists("catalog_external_asset_sync_item")
        ).isTrue();
    }

    @Test
    void rollbackDropsExternalIdentityAndSynchronizationTables()
        throws Exception {
        rollback();

        assertThat(
            tableExists("catalog_external_asset_identity")
        ).isFalse();
        assertThat(
            tableExists("catalog_external_asset_sync_state")
        ).isFalse();
        assertThat(
            tableExists("catalog_external_asset_sync_run")
        ).isFalse();
        assertThat(
            tableExists("catalog_external_asset_sync_item")
        ).isFalse();
    }

    private void update() throws Exception {
        updateChangeLog(IDENTITY_CHANGELOG);
        updateChangeLog(SYNC_CHANGELOG);
    }

    private void updateChangeLog(String changeLog) throws Exception {
        withLiquibase(changeLog, liquibase ->
            liquibase.update(
                new Contexts(),
                new LabelExpression()
            )
        );
    }

    private void rollback() throws Exception {
        rollbackChangeLog(SYNC_CHANGELOG);
        rollbackChangeLog(IDENTITY_CHANGELOG);
    }

    private void rollbackChangeLog(String changeLog)
        throws Exception {
        withLiquibase(changeLog, liquibase ->
            liquibase.rollback(
                1,
                new Contexts(),
                new LabelExpression()
            )
        );
    }

    private void withLiquibase(
        String changeLog,
        LiquibaseAction action
    ) throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources =
                new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory
                .getInstance()
                .findCorrectDatabaseImplementation(
                    new JdbcConnection(connection)
                );
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (
                    Liquibase liquibase = new Liquibase(
                        changeLog,
                        resources,
                        database
                    )
                ) {
                    action.run(liquibase);
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void insertIdentity(
        String canonicalKey,
        String remoteId,
        String registrationScope,
        UUID syncRunId
    ) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into catalog_external_asset_identity (
                    id, asset_type, canonical_asset_key, remote_asset_id,
                    source_service, registration_scope, sync_run_id, active,
                    created_by, created_date
                ) values (?, 'METRIC', ?, ?, 'dts-metrics', ?, ?, true,
                          'it-user', current_timestamp)
                """
            )
        ) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, canonicalKey);
            statement.setString(3, remoteId);
            statement.setString(4, registrationScope);
            statement.setObject(5, syncRunId);
            statement.executeUpdate();
        }
    }

    private boolean constraintExists(String constraintName)
        throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                """
                select exists (
                    select 1
                      from information_schema.table_constraints
                     where constraint_schema = ?
                       and constraint_name = ?
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

    private DriverManagerDataSource dataSourceInSchema() {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return new DriverManagerDataSource(
            jdbcUrl + separator + "currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private boolean identityActive(
        JdbcTemplate jdbcTemplate,
        String canonicalKey
    ) {
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                """
                select active
                  from catalog_external_asset_identity
                 where canonical_asset_key = ?
                """,
                Boolean.class,
                canonicalKey
            )
        );
    }

    private boolean identityExists(
        JdbcTemplate jdbcTemplate,
        String canonicalKey
    ) {
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                      from catalog_external_asset_identity
                     where canonical_asset_key = ?
                )
                """,
                Boolean.class,
                canonicalKey
            )
        );
    }

    private String identityRemoteId(
        JdbcTemplate jdbcTemplate,
        String canonicalKey
    ) {
        return jdbcTemplate.queryForObject(
            """
            select remote_asset_id
              from catalog_external_asset_identity
             where canonical_asset_key = ?
            """,
            String.class,
            canonicalKey
        );
    }

    private String identityOwnerDept(
        JdbcTemplate jdbcTemplate,
        String canonicalKey
    ) {
        return jdbcTemplate.queryForObject(
            """
            select owner_dept
              from catalog_external_asset_identity
             where canonical_asset_key = ?
            """,
            String.class,
            canonicalKey
        );
    }

    private String syncRunStatus(
        JdbcTemplate jdbcTemplate,
        String source,
        String scope,
        UUID syncRunId
    ) {
        return jdbcTemplate.queryForObject(
            """
            select status
              from catalog_external_asset_sync_run
             where source_service = ?
               and registration_scope = ?
               and sync_run_id = ?
            """,
            String.class,
            source,
            scope,
            syncRunId
        );
    }

    private int stagedItemCount(
        JdbcTemplate jdbcTemplate,
        String source,
        String scope,
        UUID syncRunId
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from catalog_external_asset_sync_item
             where source_service = ?
               and registration_scope = ?
               and sync_run_id = ?
            """,
            Integer.class,
            source,
            scope,
            syncRunId
        );
        return count == null ? 0 : count;
    }

    private List<String> activeKeys(
        JdbcTemplate jdbcTemplate,
        String scope
    ) {
        return jdbcTemplate.queryForList(
            """
            select canonical_asset_key
              from catalog_external_asset_identity
             where registration_scope = ?
               and active = true
             order by canonical_asset_key
            """,
            String.class,
            scope
        );
    }

    private java.util.Map<String, Object> syncState(
        JdbcTemplate jdbcTemplate,
        String source,
        String scope
    ) {
        return jdbcTemplate.queryForMap(
            """
            select active_run_id,
                   next_batch_index,
                   batch_count,
                   last_completed_run_id
              from catalog_external_asset_sync_state
             where source_service = ?
               and registration_scope = ?
            """,
            source,
            scope
        );
    }

    private boolean tableExists(String tableName) throws Exception {
        try (
            Connection connection = connectionInSchema();
            PreparedStatement statement = connection.prepareStatement(
                "select to_regclass(?) is not null"
            )
        ) {
            statement.setString(
                1,
                schema + "." + tableName
            );
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
        if (
            schema == null ||
            !OWNED_SCHEMA.matcher(schema).matches()
        ) {
            throw new IllegalStateException(
                "拒绝操作非测试 schema：" + schema
            );
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }
}
