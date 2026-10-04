package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract;
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
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "spring.liquibase.change-log=classpath:config/liquibase/catalog-tag-service-it.xml",
        "spring.liquibase.parameters.uuidType=uuid",
        "spring.datasource.hikari.auto-commit=false",
        "spring.datasource.hikari.maximum-pool-size=2",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    {
        CatalogTagSeedService.class,
        StandardPackageManifestContract.class,
        CatalogTagSeedServiceIT.JsonTestConfiguration.class,
    }
)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogTagSeedServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogTagSeedServiceIT")
        .withUsername("catalog_tag_seed_test")
        .withPassword("catalog_tag_seed_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogTagSeedService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanTablesAndTriggers() {
        inTransaction(this::cleanCommittedTablesAndTriggers);
    }

    private void cleanCommittedTablesAndTriggers() {
        jdbcTemplate.execute("drop trigger if exists trg_catalog_tag_seed_failure on catalog_tag");
        jdbcTemplate.execute(
            "drop trigger if exists trg_catalog_tag_seed_failure on std_pkg_import_run"
        );
        jdbcTemplate.execute("drop function if exists catalog_tag_seed_failure()");
        jdbcTemplate.update("delete from catalog_asset_tag");
        jdbcTemplate.update("delete from catalog_tag");
        jdbcTemplate.update("delete from catalog_tag_category");
        jdbcTemplate.update("delete from std_pkg_import_run");
    }

    @Test
    void firstAndRepeatedInstallCommitOneRevisionAndPreserveAllBuiltinRows() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();

        var first = service.install();
        var second = service.install();

        assertThat(first.applied()).isTrue();
        assertThat(first.conflicts()).isEmpty();
        assertThat(first.categoriesCreated()).isEqualTo(5);
        assertThat(first.tagsCreated()).isEqualTo(20);
        assertThat(second.applied()).isTrue();
        assertThat(second.conflicts()).isEmpty();
        assertThat(second.categoriesSkipped()).isEqualTo(5);
        assertThat(second.tagsSkipped()).isEqualTo(20);
        assertCounts(5, 20, 1);
    }

    @Test
    void customerCodeConflictReturnsNotAppliedAndWritesNoCatalogOrRunRows() {
        inTransaction(() ->
            jdbcTemplate.update(
                """
                insert into catalog_tag_category (
                    id, code, name, sort_order, builtin, enabled,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, 'BUSINESS-DOMAIN', '客户业务域', 0, false, false,
                          'customer', current_timestamp, 'customer', current_timestamp)
                """,
                UUID.randomUUID()
            )
        );

        var report = service.install();

        assertThat(report.applied()).isFalse();
        assertThat(report.conflicts()).extracting(conflict -> conflict.code()).containsExactly("BUSINESS-DOMAIN");
        assertThat(report.categoriesCreated()).isZero();
        assertThat(report.tagsCreated()).isZero();
        assertThat(report.categoriesSkipped()).isZero();
        assertThat(report.tagsSkipped()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_tag_category", Integer.class))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_tag", Integer.class))
            .isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from std_pkg_import_run", Integer.class))
            .isZero();
    }

    @Test
    void concurrentFirstInstallSerializesOneAppliedRevisionWithoutServerErrors() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> reports = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        reports.add(service.install());
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

        assertThat(reports).hasSize(2);
        assertCounts(5, 20, 1);
    }

    @Test
    void triggerFailureRollsBackCategoriesTagsAndAppliedRunTogether() {
        inTransaction(() -> {
            jdbcTemplate.execute(
                """
                create function catalog_tag_seed_failure() returns trigger
                language plpgsql as $$
                begin
                    if new.status = 'APPLIED' then
                        raise exception 'forced builtin catalog seed failure';
                    end if;
                    return new;
                end
                $$
                """
            );
            jdbcTemplate.execute(
                """
                create trigger trg_catalog_tag_seed_failure
                after insert on std_pkg_import_run
                for each row execute function catalog_tag_seed_failure()
                """
            );
        });

        assertThatThrownBy(service::install)
            .isInstanceOfAny(DataAccessException.class, IllegalStateException.class)
            .hasStackTraceContaining("forced builtin catalog seed failure");

        assertCounts(0, 0, 0);
    }

    private void assertCounts(int categories, int tags, int runs) {
        assertThat(
            jdbcTemplate.queryForObject("select count(*) from catalog_tag_category", Integer.class)
        ).isEqualTo(categories);
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_tag", Integer.class))
            .isEqualTo(tags);
        assertThat(jdbcTemplate.queryForObject("select count(*) from std_pkg_import_run", Integer.class))
            .isEqualTo(runs);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @TestConfiguration
    static class JsonTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
