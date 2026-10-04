package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationExecution;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.liquibase.change-log=classpath:config/liquibase/catalog-tag-migration-service-it.xml",
        "spring.liquibase.parameters.uuidType=uuid",
        "spring.datasource.hikari.auto-commit=false",
        "spring.datasource.hikari.maximum-pool-size=5",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    {
        CatalogTagMigrationService.class,
        JdbcCatalogTagMigrationStore.class,
        CatalogTagMigrationServiceIT.JsonTestConfiguration.class,
    }
)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogTagMigrationServiceIT {

    private static final UUID CATEGORY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED_TAG_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CORE_TAG_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID DUPLICATE_TAG_A = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID DUPLICATE_TAG_B = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID DATASET_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_B = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID DATASET_C = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SOURCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogTagMigrationServiceIT")
        .withUsername("catalog_tag_migration_test")
        .withPassword("catalog_tag_migration_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogTagMigrationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CatalogTagMigrationStore migrationStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        inTransaction(() -> {
            jdbcTemplate.execute("drop trigger if exists trg_catalog_tag_migration_failure on catalog_asset_tag");
            jdbcTemplate.execute("drop function if exists catalog_tag_migration_failure()");
            jdbcTemplate.update("delete from catalog_asset_tag");
            jdbcTemplate.update("delete from catalog_tag_migration_batch");
            jdbcTemplate.update("delete from catalog_tag");
            jdbcTemplate.update("delete from catalog_tag_category");
            jdbcTemplate.update("delete from catalog_dataset");
            insertCategory();
        });
    }

    @Test
    void liquibaseCreatesGuardedLedgerSchemaAndProductionMasterIncludesIt() throws Exception {
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from pg_constraint
                 where conname in (
                     'ck_catalog_tag_migration_batch_checksum',
                     'ck_catalog_tag_migration_batch_status',
                     'ck_catalog_tag_migration_batch_evidence'
                 )
                """,
                Long.class
            )
        ).isEqualTo(3);
        assertThat(
            jdbcTemplate.queryForObject(
                "select to_regclass('idx_catalog_tag_migration_batch_status') is not null",
                Boolean.class
            )
        ).isTrue();
        assertThat(
            jdbcTemplate.queryForList(
                """
                select data_type
                  from information_schema.columns
                 where table_schema = current_schema()
                   and table_name = 'catalog_tag_migration_batch'
                   and column_name in ('created_date', 'last_modified_date')
                 order by column_name
                """,
                String.class
            )
        ).containsExactly("timestamp with time zone", "timestamp with time zone");
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                    """
                    insert into catalog_tag_migration_batch (
                        batch_id, checksum, status, plan_json, created_by, created_date
                    ) values ('catalog-tags-invalid', 'ABC', 'EXECUTED', '{}'::jsonb,
                              'it-user', current_timestamp)
                    """
                )
            )
            .isInstanceOf(DataIntegrityViolationException.class);
        String master = new ClassPathResource(
            "config/liquibase/master.xml"
        ).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(master).contains(
            "config/liquibase/changelog/20260725_20_catalog_tag_migration_batch.xml"
        );
    }

    @Test
    void liquibaseRollbackDropsTheUnpublishedMigrationLedger() throws Exception {
        String schema = "s71_tag_migration_rollback_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(schema).matches("s71_tag_migration_rollback_[0-9a-f]{32}");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
        }
        try {
            try (
                Connection connection = dataSource.getConnection();
                ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
            ) {
                connection.setAutoCommit(true);
                Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try {
                    database.setDefaultSchemaName(schema);
                    database.setLiquibaseSchemaName(schema);
                    try (
                        Liquibase liquibase = new Liquibase(
                            "config/liquibase/changelog/20260725_20_catalog_tag_migration_batch.xml",
                            resources,
                            database
                        )
                    ) {
                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(tableExists(schema, "catalog_tag_migration_batch")).isTrue();

                        liquibase.rollback(1, new Contexts(), new LabelExpression());

                        assertThat(tableExists(schema, "catalog_tag_migration_batch")).isFalse();
                    }
                } finally {
                    if (!database.getConnection().isClosed()) {
                        database.close();
                    }
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("drop schema " + schema + " cascade");
            }
        }
    }

    @Test
    void dryRunIsZeroWriteAndClassifiesControlledTokensAmbiguityAndMachineEvidence() {
        String mixed = " 高可信，核心; 高可信  未命中；重名 ";
        String json = "{\"materializedTruth\":true,\"runId\":\"r-1\"}";
        String api = "origin=API;connectionId=c-1;taskId=t-1;landingStatus=SUCCESS";
        insertDataset(DATASET_A, "orders", mixed);
        insertDataset(DATASET_B, "dbt_orders", json);
        insertDataset(DATASET_C, "api_orders", api);
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        insertTag(CORE_TAG_ID, "DOMAIN-CORE", "核心");
        insertTag(DUPLICATE_TAG_A, "DUPLICATE-A", "重名");
        insertTag(DUPLICATE_TAG_B, "DUPLICATE-B", "重名");
        long relationCount = count("catalog_asset_tag");
        long ledgerCount = count("catalog_tag_migration_batch");

        var report = service.dryRun();

        assertThat(report.datasetCount()).isEqualTo(3);
        assertThat(report.nonEmptyDatasetCount()).isEqualTo(3);
        assertThat(report.tokenCount()).isEqualTo(4);
        assertThat(report.matchedTokenCount()).isEqualTo(2);
        assertThat(report.unmatchedTokens()).extracting(issue -> issue.token()).containsExactly("未命中");
        assertThat(report.ambiguousTokens()).extracting(issue -> issue.token()).containsExactly("重名");
        assertThat(report.protectedEvidence()).extracting(item -> item.format()).containsExactly(
            "JSON_OBJECT",
            "KEY_VALUE_EVIDENCE"
        );
        assertThat(report.checksum()).matches("[0-9a-f]{64}");
        assertThat(count("catalog_asset_tag")).isEqualTo(relationCount);
        assertThat(count("catalog_tag_migration_batch")).isEqualTo(ledgerCount);
        assertLegacyTags(DATASET_A, mixed);
        assertLegacyTags(DATASET_B, json);
        assertLegacyTags(DATASET_C, api);
    }

    @Test
    void disabledExactNameProducesConflictEvidenceAndExecuteCreatesNoRelation() {
        insertDataset(DATASET_A, "orders", "高可信");
        insertTag(
            TRUSTED_TAG_ID,
            "QUALITY-TRUSTED",
            "高可信",
            false
        );

        var report = service.dryRun();

        assertThat(report.matchedTokenCount()).isZero();
        assertThat(report.relations()).isEmpty();
        assertThat(report.unmatchedTokens()).singleElement().satisfies(issue -> {
            assertThat(issue.token()).isEqualTo("高可信");
            assertThat(issue.reason()).isEqualTo("DISABLED_NAME");
            assertThat(issue.candidateTagIds()).containsExactly(TRUSTED_TAG_ID);
        });

        CatalogTagMigrationExecution execution = service.execute(
            report.batchId(),
            report.checksum(),
            "alice"
        );
        assertThat(execution.created()).isZero();
        assertThat(count("catalog_asset_tag")).isZero();
        assertThat(count("catalog_tag_migration_batch")).isEqualTo(1);
    }

    @Test
    void concurrentExecuteCreatesRelationsOnceAndSubsequentExecuteIsReplay() throws Exception {
        String originalTags = "高可信, 核心";
        insertDataset(DATASET_A, "orders", originalTags);
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        insertTag(CORE_TAG_ID, "DOMAIN-CORE", "核心");
        var dryRun = service.dryRun();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CatalogTagMigrationExecution> results = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        results.add(service.execute(dryRun.batchId(), dryRun.checksum(), "alice"));
                        return null;
                    })
                );
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(results).hasSize(2);
        assertThat(results).extracting(CatalogTagMigrationExecution::replayed).containsExactlyInAnyOrder(false, true);
        assertThat(results).extracting(CatalogTagMigrationExecution::created).containsOnly(2L);
        assertThat(count("catalog_asset_tag")).isEqualTo(2);
        assertThat(count("catalog_tag_migration_batch")).isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_asset_tag where migration_batch = ?",
                Long.class,
                dryRun.batchId()
            )
        ).isEqualTo(2);
        assertThat(service.execute(dryRun.batchId(), dryRun.checksum(), "alice").replayed()).isTrue();
        assertLegacyTags(DATASET_A, originalTags);
    }

    @Test
    void advisoryLockSerializesDifferentMigrationBatches() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> {
                inTransaction(() -> {
                    migrationStore.acquireMigrationLock();
                    firstLocked.countDown();
                    await(releaseFirst);
                });
                return null;
            });
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> {
                inTransaction(() -> {
                    secondAttempting.countDown();
                    migrationStore.acquireMigrationLock();
                    secondLocked.countDown();
                });
                return null;
            });
            assertThat(secondAttempting.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(secondLocked.await(500, TimeUnit.MILLISECONDS))
                    .as("different migration batches must share one transaction-scoped lock")
                    .isFalse();
            } finally {
                releaseFirst.countDown();
            }

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondLocked.getCount()).isZero();
        }
    }

    @Test
    void inputLocksHoldDatasetTagAndRelationDecisionsStableUntilCommit() throws Exception {
        insertDataset(DATASET_A, "orders", "高可信");
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        String assetKey = CatalogAssetKey.dataset(
            SOURCE_ID,
            "dwd",
            "dwd",
            "orders",
            "orders"
        );
        insertRelation(TRUSTED_TAG_ID, assetKey, null);
        CountDownLatch inputsLocked = new CountDownLatch(1);
        CountDownLatch releaseInputs = new CountDownLatch(1);
        ConcurrentLinkedQueue<Integer> writerPids = new ConcurrentLinkedQueue<>();

        try (var executor = Executors.newFixedThreadPool(4)) {
            Future<?> holder = executor.submit(() -> {
                inTransaction(() -> {
                    migrationStore.acquireMigrationLock();
                    migrationStore.lockMigrationInputs();
                    inputsLocked.countDown();
                    await(releaseInputs);
                });
                return null;
            });
            assertThat(inputsLocked.await(10, TimeUnit.SECONDS)).isTrue();

            List<Future<Integer>> writers = List.of(
                executor.submit(() ->
                    inTransactionResult(() -> {
                        writerPids.add(backendPid());
                        return jdbcTemplate.update(
                            "update catalog_dataset set tags = '核心' where id = ?",
                            DATASET_A
                        );
                    })
                ),
                executor.submit(() ->
                    inTransactionResult(() -> {
                        writerPids.add(backendPid());
                        return jdbcTemplate.update(
                            "update catalog_tag set name = '核心' where id = ?",
                            TRUSTED_TAG_ID
                        );
                    })
                ),
                executor.submit(() ->
                    inTransactionResult(() -> {
                        writerPids.add(backendPid());
                        return jdbcTemplate.update(
                            "update catalog_asset_tag set tagged_by = 'concurrent-user' where asset_key = ?",
                            assetKey
                        );
                    })
                )
            );

            try {
                org.awaitility.Awaitility.await()
                    .pollInterval(Duration.ofMillis(25))
                    .atMost(10, TimeUnit.SECONDS)
                    .until(() ->
                        writerPids.size() == 3 &&
                        writerPids.stream().allMatch(this::isWaitingForLock)
                    );
                assertThat(writers).allMatch(future -> !future.isDone());
            } finally {
                releaseInputs.countDown();
            }

            holder.get(10, TimeUnit.SECONDS);
            for (Future<Integer> writer : writers) {
                assertThat(writer.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            }
        }

        assertLegacyTags(DATASET_A, "核心");
        assertThat(
            jdbcTemplate.queryForObject(
                "select name from catalog_tag where id = ?",
                String.class,
                TRUSTED_TAG_ID
            )
        ).isEqualTo("核心");
        assertThat(
            jdbcTemplate.queryForObject(
                "select tagged_by from catalog_asset_tag where asset_key = ?",
                String.class,
                assetKey
            )
        ).isEqualTo("concurrent-user");
    }

    @Test
    void sourceOrTagDefinitionDriftBlocksExecutionBeforeAnyWrite() {
        insertDataset(DATASET_A, "orders", "高可信");
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        var sourcePlan = service.dryRun();
        inTransaction(() ->
            jdbcTemplate.update("update catalog_dataset set tags = ? where id = ?", " 高可信 ", DATASET_A)
        );

        assertThatThrownBy(() ->
                service.execute(sourcePlan.batchId(), sourcePlan.checksum(), "alice")
            )
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("MIGRATION_PLAN_DRIFT");
        assertThat(count("catalog_asset_tag")).isZero();
        assertThat(count("catalog_tag_migration_batch")).isZero();

        inTransaction(() ->
            jdbcTemplate.update("update catalog_dataset set tags = ? where id = ?", "高可信", DATASET_A)
        );
        var definitionPlan = service.dryRun();
        inTransaction(() ->
            jdbcTemplate.update("update catalog_tag set name = ? where id = ?", "高可信-v2", TRUSTED_TAG_ID)
        );

        assertThatThrownBy(() ->
                service.execute(definitionPlan.batchId(), definitionPlan.checksum(), "alice")
            )
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("MIGRATION_PLAN_DRIFT");
        assertThat(count("catalog_asset_tag")).isZero();
        assertThat(count("catalog_tag_migration_batch")).isZero();
    }

    @Test
    void insertFailureRollsBackEarlierRelationAndLedgerInTheSameProductionTransaction() {
        insertDataset(DATASET_A, "orders_a", "高可信");
        insertDataset(DATASET_B, "orders_b", "核心");
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        insertTag(CORE_TAG_ID, "DOMAIN-CORE", "核心");
        var dryRun = service.dryRun();
        String secondAssetKey = dryRun
            .relations()
            .stream()
            .filter(relation -> relation.datasetId().equals(DATASET_B))
            .findFirst()
            .orElseThrow()
            .assetKey();
        createFailureTrigger(secondAssetKey);

        assertThatThrownBy(() ->
                service.execute(dryRun.batchId(), dryRun.checksum(), "alice")
            )
            .isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("forced catalog tag migration failure");

        assertThat(count("catalog_asset_tag")).isZero();
        assertThat(count("catalog_tag_migration_batch")).isZero();
        assertLegacyTags(DATASET_A, "高可信");
        assertLegacyTags(DATASET_B, "核心");
    }

    @Test
    void rollbackDeletesOnlyRelationsCreatedByTheBatchAndIsReplaySafe() {
        insertDataset(DATASET_A, "orders_a", "高可信");
        insertDataset(DATASET_B, "orders_b", "核心");
        insertDataset(DATASET_C, "orders_c", null);
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        insertTag(CORE_TAG_ID, "DOMAIN-CORE", "核心");
        String datasetAKey = CatalogAssetKey.dataset(
            SOURCE_ID,
            "dwd",
            "dwd",
            "orders_a",
            "orders_a"
        );
        String datasetCKey = CatalogAssetKey.dataset(
            SOURCE_ID,
            "dwd",
            "dwd",
            "orders_c",
            "orders_c"
        );
        insertRelation(TRUSTED_TAG_ID, datasetAKey, null);
        insertRelation(CORE_TAG_ID, datasetCKey, "other-batch");
        var dryRun = service.dryRun();
        var execution = service.execute(dryRun.batchId(), dryRun.checksum(), "alice");

        assertThat(execution.created()).isEqualTo(1);
        assertThat(execution.skipped()).isEqualTo(1);
        assertThat(count("catalog_asset_tag")).isEqualTo(3);

        var rollback = service.rollback(dryRun.batchId(), dryRun.checksum(), "alice");

        assertThat(rollback.replayed()).isFalse();
        assertThat(rollback.deleted()).isEqualTo(1);
        assertThat(count("catalog_asset_tag")).isEqualTo(2);
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_asset_tag where migration_batch = ?",
                Long.class,
                "other-batch"
            )
        ).isEqualTo(1);
        assertThat(service.rollback(dryRun.batchId(), dryRun.checksum(), "alice").replayed()).isTrue();
        assertThat(count("catalog_asset_tag")).isEqualTo(2);
        assertLegacyTags(DATASET_A, "高可信");
        assertLegacyTags(DATASET_B, "核心");
    }

    @Test
    void rollbackEvidenceDriftReturnsConflictAndDoesNotDeleteAnything() {
        insertDataset(DATASET_A, "orders", "高可信");
        insertTag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        var dryRun = service.dryRun();
        service.execute(dryRun.batchId(), dryRun.checksum(), "alice");
        inTransaction(() ->
            jdbcTemplate.update(
                "update catalog_asset_tag set migration_batch = 'tampered-batch' where migration_batch = ?",
                dryRun.batchId()
            )
        );

        assertThatThrownBy(() ->
                service.rollback(dryRun.batchId(), dryRun.checksum(), "alice")
            )
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_DRIFT");

        assertThat(count("catalog_asset_tag")).isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject(
                "select status from catalog_tag_migration_batch where batch_id = ?",
                String.class,
                dryRun.batchId()
            )
        ).isEqualTo("EXECUTED");
        assertLegacyTags(DATASET_A, "高可信");
    }

    private void insertCategory() {
        jdbcTemplate.update(
            """
            insert into catalog_tag_category (
                id, code, name, sort_order, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, 'MIGRATION-TEST', '迁移测试', 0, false, true,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
            """,
            CATEGORY_ID
        );
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_tag_category where id = ?",
                Integer.class,
                CATEGORY_ID
            )
        ).isEqualTo(1);
    }

    private void insertTag(UUID id, String code, String name) {
        insertTag(id, code, name, true);
    }

    private void insertTag(
        UUID id,
        String code,
        String name,
        boolean enabled
    ) {
        inTransaction(() ->
            jdbcTemplate.update(
                """
                insert into catalog_tag (
                    id, category_id, code, name, builtin, enabled,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, ?, false, ?,
                          'it-user', current_timestamp, 'it-user', current_timestamp)
                """,
                id,
                CATEGORY_ID,
                code,
                name,
                enabled
            )
        );
    }

    private void insertDataset(UUID id, String table, String tags) {
        inTransaction(() ->
            jdbcTemplate.update(
                """
                insert into catalog_dataset (
                    id, name, source_id, hive_database, hive_table, tags, enabled,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, 'dwd', ?, ?, true,
                          'it-user', current_timestamp, 'it-user', current_timestamp)
                """,
                id,
                table,
                SOURCE_ID,
                table,
                tags
            )
        );
    }

    private void insertRelation(UUID tagId, String assetKey, String migrationBatch) {
        Instant now = Instant.parse("2026-07-25T08:00:00Z");
        inTransaction(() ->
            jdbcTemplate.update(
                """
                insert into catalog_asset_tag (
                    id, tag_id, asset_type, asset_key, tagged_by, tagged_at, migration_batch,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, 'DATASET', ?, 'manual-user', ?, ?,
                          'manual-user', ?, 'manual-user', ?)
                """,
                UUID.randomUUID(),
                tagId,
                assetKey,
                java.sql.Timestamp.from(now),
                migrationBatch,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
            )
        );
    }

    private void createFailureTrigger(String rejectedAssetKey) {
        String sqlAssetKey = rejectedAssetKey.replace("'", "''");
        inTransaction(() -> {
            jdbcTemplate.execute(
                """
                create function catalog_tag_migration_failure() returns trigger
                language plpgsql as $$
                begin
                    if new.asset_key = '%s' then
                        raise exception 'forced catalog tag migration failure';
                    end if;
                    return new;
                end
                $$
                """.formatted(sqlAssetKey)
            );
            jdbcTemplate.execute(
                """
                create trigger trg_catalog_tag_migration_failure
                before insert on catalog_asset_tag
                for each row execute function catalog_tag_migration_failure()
                """
            );
        });
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private boolean tableExists(String schema, String table) {
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                      from information_schema.tables
                     where table_schema = ?
                       and table_name = ?
                )
                """,
                Boolean.class,
                schema,
                table
            )
        );
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private int backendPid() {
        return jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class);
    }

    private boolean isWaitingForLock(int pid) {
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                "select exists (select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
                Boolean.class,
                pid
            )
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for migration concurrency fixture");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("migration concurrency fixture interrupted", exception);
        }
    }

    private void assertLegacyTags(UUID datasetId, String expected) {
        assertThat(
            jdbcTemplate.queryForObject(
                "select tags from catalog_dataset where id = ?",
                String.class,
                datasetId
            )
        ).isEqualTo(expected);
    }

    @TestConfiguration
    static class JsonTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
