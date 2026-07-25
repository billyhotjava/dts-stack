package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter.AssignmentKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:config/liquibase/changelog/20260725_02_catalog_tag.xml",
        "spring.datasource.hikari.auto-commit=false",
        "spring.datasource.hikari.maximum-pool-size=2",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CatalogAssetTagBatchWriter.class)
class CatalogAssetTagRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogAssetTagRepositoryIT")
        .withUsername("catalog_asset_tag_test")
        .withPassword("catalog_asset_tag_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogAssetTagRepository repository;

    @Autowired
    private CatalogAssetTagBatchWriter batchWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tagId;

    @BeforeEach
    void setUpRows() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("delete from catalog_asset_tag");
            jdbcTemplate.update("delete from catalog_tag");
            jdbcTemplate.update("delete from catalog_tag_category");
            UUID categoryId = UUID.randomUUID();
            tagId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into catalog_tag_category (
                    id, code, name, sort_order, builtin, enabled,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, 'QUALITY', '数据质量', 0, false, true, 'it-user', current_timestamp, 'it-user', current_timestamp)
                """,
                categoryId
            );
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from catalog_tag_category where id = ?",
                    Integer.class,
                    categoryId
                )
            ).isEqualTo(1);
            jdbcTemplate.update(
                """
                insert into catalog_tag (
                    id, category_id, code, name, builtin, enabled,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, 'QUALITY-TRUSTED', '高可信', false, true,
                          'it-user', current_timestamp, 'it-user', current_timestamp)
                """,
                tagId,
                categoryId
            );
        });
    }

    @Test
    void productionRepositoryAtomicallyCreatesOneRowAndSkipsConcurrentConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> updates = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                            updates.add(invokeInsert());
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
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

        assertThat(updates).containsExactlyInAnyOrder(0, 1);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*) from catalog_asset_tag
                where tag_id = ? and asset_type = 'METRIC' and asset_key = 'metric:core/revenue'
                """,
                Long.class,
                tagId
            )
        ).isEqualTo(1L);
    }

    @Test
    void batchWriterReturnsOnlyCreatedAssignmentsAcrossChunksAndIsIdempotent() {
        List<AssignmentKey> assignments = java.util.stream.IntStream
            .range(0, CatalogAssetTagBatchWriter.MAX_ROWS_PER_STATEMENT + 1)
            .mapToObj(index -> new AssignmentKey("METRIC", "metric:core/m" + index, tagId))
            .toList();

        var first = invokeBatchInsert(assignments);
        var repeated = invokeBatchInsert(assignments);

        assertThat(first).containsExactlyInAnyOrderElementsOf(assignments);
        assertThat(repeated).isEmpty();
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_asset_tag where tag_id = ?",
                Long.class,
                tagId
            )
        ).isEqualTo((long) assignments.size());
    }

    @Test
    void concurrentBatchWritersReturnEachCreatedAssignmentExactlyOnce() throws Exception {
        List<AssignmentKey> assignments = List.of(
            new AssignmentKey("METRIC", "metric:core/revenue", tagId),
            new AssignmentKey("METRIC", "metric:core/margin", tagId)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.Set<AssignmentKey>> results = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                            results.add(invokeBatchInsert(assignments));
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
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

        assertThat(results).hasSize(2);
        assertThat(results.stream().flatMap(java.util.Set::stream).toList())
            .containsExactlyInAnyOrderElementsOf(assignments);
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_asset_tag", Long.class)).isEqualTo(2L);
    }

    private int invokeInsert() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
            Instant now = Instant.now();
            return repository.insertIgnore(
                UUID.randomUUID(),
                tagId,
                "METRIC",
                "metric:core/revenue",
                "alice",
                now,
                null,
                now
            );
        });
    }

    private java.util.Set<AssignmentKey> invokeBatchInsert(List<AssignmentKey> assignments) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> batchWriter.insertIgnore(assignments, "alice", Instant.now()));
    }
}
