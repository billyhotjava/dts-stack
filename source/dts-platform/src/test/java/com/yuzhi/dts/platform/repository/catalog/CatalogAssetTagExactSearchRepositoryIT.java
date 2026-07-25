package com.yuzhi.dts.platform.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:config/liquibase/changelog/20260725_02_catalog_tag.xml",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional
class CatalogAssetTagExactSearchRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogAssetTagExactSearchRepositoryIT")
        .withUsername("catalog_asset_tag_search_test")
        .withPassword("catalog_asset_tag_search_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogAssetTagRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID firstTagId;
    private UUID secondTagId;
    private UUID firstDatasetId;
    private UUID firstSourceId;
    private UUID firstTableId;
    private UUID firstColumnId;

    @BeforeEach
    void setUpRows() {
        createCatalogSearchFixtureTables();
        jdbcTemplate.update("delete from catalog_column_schema");
        jdbcTemplate.update("delete from catalog_table_schema");
        jdbcTemplate.update("delete from catalog_dataset");
        jdbcTemplate.update("delete from catalog_asset_tag");
        jdbcTemplate.update("delete from catalog_tag");
        jdbcTemplate.update("delete from catalog_tag_category");
        UUID categoryId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        firstTagId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        secondTagId = UUID.fromString("20000000-0000-0000-0000-000000000002");
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
        insertTag(firstTagId, categoryId, "QUALITY-FIRST", "标签一");
        insertTag(secondTagId, categoryId, "QUALITY-SECOND", "标签二");
        insertAssignment("30000000-0000-0000-0000-000000000001", firstTagId, "DATASET", "dataset:orders");
        insertAssignment("30000000-0000-0000-0000-000000000002", secondTagId, "DATASET", "dataset:orders");
        insertAssignment("30000000-0000-0000-0000-000000000003", firstTagId, "DATASET", "dataset:customers");
        insertAssignment("30000000-0000-0000-0000-000000000004", firstTagId, "METRIC", "metric:core/revenue");
        insertAssignment("30000000-0000-0000-0000-000000000005", secondTagId, "METRIC", "metric:core/revenue");

        firstDatasetId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID secondDatasetId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        firstSourceId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        UUID secondSourceId = UUID.fromString("50000000-0000-0000-0000-000000000002");
        firstTableId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        firstColumnId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        insertDataset(firstDatasetId, firstSourceId, "orders");
        insertDataset(secondDatasetId, secondSourceId, "customers");
        jdbcTemplate.update(
            "insert into catalog_table_schema (id, dataset_id, name) values (?, ?, 'orders_table')",
            firstTableId,
            firstDatasetId
        );
        jdbcTemplate.update(
            "insert into catalog_column_schema (id, table_id, name) values (?, ?, 'order_id')",
            firstColumnId,
            firstTableId
        );
    }

    @Test
    void datasetQueryRequiresEveryDistinctTag() {
        List<String> keys = repository.findAssetKeysHavingAllTagsWithin(
            "DATASET",
            List.of("dataset:orders", "dataset:customers"),
            List.of(firstTagId, secondTagId, firstTagId),
            2
        );

        assertThat(keys).containsExactly("dataset:orders");
    }

    @Test
    void genericQueryReturnsDatasetAndMetricInStableOrder() {
        Page<CatalogAssetTagRepository.AssetRefProjection> page = repository.findAssetRefsHavingAllTags(
            List.of(firstTagId, secondTagId),
            2,
            null,
            PageRequest.of(0, 1)
        );
        List<AssetRef> refs = page
            .getContent()
            .stream()
            .map(row -> new AssetRef(row.getAssetType(), row.getAssetKey()))
            .toList();

        assertThat(refs).containsExactly(new AssetRef("DATASET", "dataset:orders"));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void genericQueryCanRestrictOneAssetType() {
        List<AssetRef> refs = repository
            .findAssetRefsHavingAllTags(List.of(firstTagId), 1, "METRIC", PageRequest.of(0, 10))
            .getContent()
            .stream()
            .map(row -> new AssetRef(row.getAssetType(), row.getAssetKey()))
            .toList();

        assertThat(refs).containsExactly(new AssetRef("METRIC", "metric:core/revenue"));
    }

    @Test
    void catalogSearchCandidateQueryPagesAndKeepsAllTagSemantics() {
        insertCatalogSearchAssignments();

        Slice<CatalogAssetTagRepository.CatalogSearchCandidateProjection> firstPage =
            repository.findCatalogSearchCandidatesHavingAllTags(
                List.of(firstTagId, secondTagId, firstTagId),
                2,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 1)
            );

        assertThat(firstPage.getContent())
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.getDatasetId()).isEqualTo(firstDatasetId);
                assertThat(candidate.getEntityId()).isEqualTo(firstDatasetId);
                assertThat(candidate.getAssetKey()).isEqualTo("source:" + firstSourceId + "/schema:dwd/table:orders");
            });
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    void catalogSearchCandidateQueryReturnsOnlyScopedTableAndColumnRows() {
        insertCatalogSearchAssignments();

        List<CatalogAssetTagRepository.CatalogSearchCandidateProjection> tables = repository
            .findCatalogSearchCandidatesHavingAllTags(
                List.of(firstTagId, secondTagId),
                2,
                "TABLE",
                null,
                firstSourceId,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 10)
            )
            .getContent();
        List<CatalogAssetTagRepository.CatalogSearchCandidateProjection> columns = repository
            .findCatalogSearchCandidatesHavingAllTags(
                List.of(firstTagId, secondTagId),
                2,
                "COLUMN",
                null,
                firstSourceId,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 10)
            )
            .getContent();

        assertThat(tables).singleElement().satisfies(candidate -> assertThat(candidate.getEntityId()).isEqualTo(firstTableId));
        assertThat(columns)
            .singleElement()
            .satisfies(candidate -> assertThat(candidate.getEntityId()).isEqualTo(firstColumnId));
    }

    private void createCatalogSearchFixtureTables() {
        jdbcTemplate.execute(
            """
            create table if not exists catalog_dataset (
                id uuid primary key,
                name varchar(128),
                domain_id uuid,
                type varchar(32),
                source_id uuid,
                classification varchar(32),
                owner_dept varchar(64),
                hive_database varchar(128),
                hive_table varchar(128),
                warehouse_layer varchar(16),
                exposed_by varchar(16),
                enabled boolean,
                created_date timestamp
            )
            """
        );
        jdbcTemplate.execute(
            """
            create table if not exists catalog_table_schema (
                id uuid primary key,
                dataset_id uuid,
                name varchar(128)
            )
            """
        );
        jdbcTemplate.execute(
            """
            create table if not exists catalog_column_schema (
                id uuid primary key,
                table_id uuid,
                name varchar(128)
            )
            """
        );
    }

    private void insertDataset(UUID id, UUID sourceId, String tableName) {
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, type, source_id, classification, owner_dept,
                hive_database, hive_table, warehouse_layer, exposed_by, enabled, created_date
            ) values (?, ?, 'jdbc', ?, 'INTERNAL', 'DATA',
                      'dwd', ?, 'DWD', 'API', true, timestamp '2026-07-25 00:00:00')
            """,
            id,
            tableName,
            sourceId,
            tableName
        );
    }

    private void insertCatalogSearchAssignments() {
        UUID secondSourceId = UUID.fromString("50000000-0000-0000-0000-000000000002");
        insertAssignment(
            "80000000-0000-0000-0000-000000000001",
            firstTagId,
            "DATASET",
            "source:" + firstSourceId + "/schema:dwd/table:orders"
        );
        insertAssignment(
            "80000000-0000-0000-0000-000000000002",
            secondTagId,
            "DATASET",
            "source:" + firstSourceId + "/schema:dwd/table:orders"
        );
        insertAssignment(
            "80000000-0000-0000-0000-000000000003",
            firstTagId,
            "DATASET",
            "source:" + secondSourceId + "/schema:dwd/table:customers"
        );
        insertAssignment(
            "80000000-0000-0000-0000-000000000004",
            secondTagId,
            "DATASET",
            "source:" + secondSourceId + "/schema:dwd/table:customers"
        );
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

    private void insertAssignment(String id, UUID tagId, String assetType, String assetKey) {
        jdbcTemplate.update(
            """
            insert into catalog_asset_tag (
                id, tag_id, asset_type, asset_key, tagged_by, tagged_at,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, 'it-user', current_timestamp,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
            """,
            UUID.fromString(id),
            tagId,
            assetType,
            assetKey
        );
    }
}
