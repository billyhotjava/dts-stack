package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml",
        "logging.file.name=/tmp/dts-platform-classification-version-visibility-it.log",
    }
)
@Import({CatalogClassificationService.class, CatalogClassificationWriteLock.class, CatalogDatasetClassificationProjection.class, CatalogClassificationEditService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogClassificationVersionVisibilityPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("classification_version_visibility_it")
        .withUsername("classification_version_test")
        .withPassword("classification_version_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private CatalogClassificationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired private CatalogClassificationEditService edits;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired private jakarta.persistence.EntityManager entities;
    @org.springframework.boot.test.mock.mockito.MockBean private com.yuzhi.dts.platform.service.modeling.ModelingPermissionAudit permissionAudit;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentFirstSealsConvergeToTheHighestLevel() throws Exception {
        String key = "semantic-model:" + UUID.randomUUID();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var low = pool.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                try { return service.sealOrRaise(command(key,"INTERNAL")); }
                catch (CatalogClassificationService.CatalogClassificationException denied) {
                    // If the higher classification committed first, rejecting the lower request is required.
                    assertThat(denied.code()).isEqualTo("CLASSIFICATION_DOWNGRADE_FORBIDDEN"); return null;
                }
            });
            var high = pool.submit(() -> { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); return service.sealOrRaise(command(key,"SECRET")); });
            low.get(20,java.util.concurrent.TimeUnit.SECONDS); high.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(service.resolve("ASSET", key).orElseThrow().getEffectiveLevel()).isEqualTo("SECRET");
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_classification_snapshot where subject_type='ASSET' and subject_key=?",Integer.class,key)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void staleMetadataSaveCannotUndoAConcurrentClassificationRaise(boolean ordinaryEditGuard) throws Exception {
        UUID datasetId = UUID.randomUUID();
        String name = "f9_" + datasetId.toString().replace("-", "");
        jdbcTemplate.update("insert into catalog_dataset(id,name,classification,enabled,version) values (?,?,'INTERNAL',true,0)",datasetId,name);
        var source = new com.yuzhi.dts.platform.domain.catalog.CatalogDataset(); source.setId(datasetId); source.setName(name);
        String key = CatalogAssetKey.dataset(source);
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var raised = new java.util.concurrent.CountDownLatch(1);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var metadata = pool.submit(() -> tx.execute(status -> {
                var dataset = entities.find(com.yuzhi.dts.platform.domain.catalog.CatalogDataset.class,datasetId);
                assertThat(dataset.getClassification()).isEqualTo("INTERNAL"); loaded.countDown();
                try { assertThat(raised.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                if (ordinaryEditGuard) { edits.lockCurrent(dataset); edits.apply(dataset,null,false); }
                dataset.setDescription("metadata after classification raise"); entities.flush(); return null;
            }));
            var classification = pool.submit(() -> {
                assertThat(loaded.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                try { return service.sealOrRaise(new CatalogClassificationService.SealCommand("ASSET",key,"DATASET",null,null,"SECRET",List.of(),"MANUAL_FLOOR","f9-concurrent", "b".repeat(64),"{}")); }
                finally { raised.countDown(); }
            });
            metadata.get(25,java.util.concurrent.TimeUnit.SECONDS); classification.get(25,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject("select classification from catalog_dataset where id=?",String.class,datasetId)).isEqualTo("SECRET");
        assertThat(jdbcTemplate.queryForObject("select description from catalog_dataset where id=?",String.class,datasetId)).isEqualTo("metadata after classification raise");
    }
    private CatalogClassificationService.SealCommand command(String key, String level) {
        return new CatalogClassificationService.SealCommand("ASSET",key,"SEMANTIC_MODEL",null,null,level,List.of(),"MANUAL_FLOOR","f9-first-seal",(level.equals("SECRET")?"a":"b").repeat(64),"{}");
    }

    @Test
    void sealReturnsTheSamePositiveVersionThatConsumersWillReadAfterCommit() {
        String subjectKey = "semantic-model:" + UUID.randomUUID();

        var snapshot = service.seal(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                subjectKey,
                "SEMANTIC_MODEL",
                "INTERNAL",
                null,
                null,
                List.of(),
                "SOURCE_DECLARATION",
                "classification-version-visibility-it",
                "a".repeat(64),
                "{}"
            )
        );

        Long storedVersion = jdbcTemplate.queryForObject(
            "select record_version from catalog_classification_snapshot where subject_type='ASSET' and subject_key=?",
            Long.class,
            subjectKey
        );
        assertThat(snapshot.getPropagationStatus()).isEqualTo(CatalogClassificationService.STATUS_PROPAGATED);
        assertThat(snapshot.getRecordVersion()).isPositive().isEqualTo(storedVersion);
    }
}
