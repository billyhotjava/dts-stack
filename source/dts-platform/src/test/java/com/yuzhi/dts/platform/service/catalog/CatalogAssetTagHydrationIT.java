package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.liquibase.change-log=classpath:config/liquibase/catalog-tag-service-it.xml",
        "spring.liquibase.parameters.uuidType=uuid",
        "spring.datasource.hikari.auto-commit=false",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ CatalogAssetTagService.class, CatalogAssetTagBatchWriter.class })
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogAssetTagHydrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogAssetTagHydrationIT")
        .withUsername("catalog_asset_tag_hydration_test")
        .withPassword("catalog_asset_tag_hydration_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogAssetTagService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID firstTagId;
    private UUID secondTagId;
    private List<AssetRef> datasetRefs;

    @BeforeEach
    void setUpRows() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> setUpCommittedRows());
    }

    private void setUpCommittedRows() {
        jdbcTemplate.update("delete from catalog_asset_tag");
        jdbcTemplate.update("delete from catalog_tag");
        jdbcTemplate.update("delete from catalog_tag_category");
        UUID categoryId = UUID.randomUUID();
        firstTagId = UUID.randomUUID();
        secondTagId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into catalog_tag_category (
                id, code, name, sort_order, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, 'QUALITY', '数据质量', 0, false, true,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
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
        insertTag(firstTagId, categoryId, "FIRST", "标签一");
        insertTag(secondTagId, categoryId, "SECOND", "标签二");
        datasetRefs = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            AssetRef ref = new AssetRef("DATASET", "dataset:item-" + index);
            datasetRefs.add(ref);
            insertAssignment(ref, firstTagId, Instant.parse("2026-07-25T00:00:02Z"));
        }
        insertAssignment(datasetRefs.get(0), secondTagId, Instant.parse("2026-07-25T00:00:01Z"));
        insertAssignment(
            new AssetRef("METRIC", "metric:core/revenue"),
            firstTagId,
            Instant.parse("2026-07-25T00:00:03Z")
        );
    }

    @Test
    void oneAndManyRowsUseTheSameThreeQueriesForOneAssetType() {
        Statistics statistics = statistics();

        statistics.clear();
        service.listAssetTags(List.of(datasetRefs.get(0)));
        long oneRowStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        Map<AssetRef, List<com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto>> many = service.listAssetTags(datasetRefs);
        long manyRowStatements = statistics.getPrepareStatementCount();

        assertThat(oneRowStatements).isEqualTo(3);
        assertThat(manyRowStatements).isEqualTo(3);
        assertThat(many.get(datasetRefs.get(0)))
            .extracting(tag -> Map.entry(tag.code(), tag.usageCount()))
            .containsExactly(Map.entry("SECOND", 1L), Map.entry("FIRST", 11L));
    }

    @Test
    void addingAnotherAssetTypeAddsOneAssignmentQueryOnly() {
        Statistics statistics = statistics();
        statistics.clear();

        service.listAssetTags(List.of(datasetRefs.get(0), new AssetRef("METRIC", "metric:core/revenue")));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void insertTag(UUID id, UUID categoryId, String code, String name) {
        jdbcTemplate.update(
            """
            insert into catalog_tag (
                id, category_id, code, name, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, false, true,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
            """,
            id,
            categoryId,
            code,
            name
        );
    }

    private void insertAssignment(AssetRef ref, UUID tagId, Instant taggedAt) {
        jdbcTemplate.update(
            """
            insert into catalog_asset_tag (
                id, tag_id, asset_type, asset_key, tagged_by, tagged_at,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, 'it-user', ?,
                      'it-user', ?, 'it-user', ?)
            """,
            UUID.randomUUID(),
            tagId,
            ref.assetType(),
            ref.assetKey(),
            Timestamp.from(taggedAt),
            Timestamp.from(taggedAt),
            Timestamp.from(taggedAt)
        );
    }
}
