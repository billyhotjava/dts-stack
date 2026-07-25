package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ CatalogAssetTagService.class, CatalogAssetTagBatchWriter.class })
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogAssetTagServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogAssetTagServiceIT")
        .withUsername("catalog_asset_tag_service_test")
        .withPassword("catalog_asset_tag_service_test");

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
    private PlatformTransactionManager transactionManager;

    private UUID tagId;
    private UUID secondTagId;

    @BeforeEach
    void setUpRowsAndTrigger() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            setUpCommittedRowsAndTrigger()
        );
    }

    private void setUpCommittedRowsAndTrigger() {
        jdbcTemplate.execute("drop trigger if exists trg_catalog_asset_tag_fail_second on catalog_asset_tag");
        jdbcTemplate.execute("drop function if exists catalog_asset_tag_fail_second()");
        jdbcTemplate.update("delete from catalog_asset_tag");
        jdbcTemplate.update("delete from catalog_tag");
        jdbcTemplate.update("delete from catalog_tag_category");
        UUID categoryId = UUID.randomUUID();
        tagId = UUID.randomUUID();
        secondTagId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into catalog_tag_category (
                id, code, name, sort_order, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, 'QUALITY', '数据质量', 0, true, true,
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
        jdbcTemplate.update(
            """
            insert into catalog_tag (
                id, category_id, code, name, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, 'QUALITY-TRUSTED', '高可信', true, true,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
            """,
            tagId,
            categoryId
        );
        jdbcTemplate.update(
            """
            insert into catalog_tag (
                id, category_id, code, name, builtin, enabled,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, 'QUALITY-CERTIFIED', '已认证', true, true,
                      'it-user', current_timestamp, 'it-user', current_timestamp)
            """,
            secondTagId,
            categoryId
        );
        jdbcTemplate.execute(
            """
            create function catalog_asset_tag_fail_second() returns trigger
            language plpgsql as $$
            begin
                if new.asset_key = 'metric:core/second' then
                    raise exception 'forced second catalog asset tag failure';
                end if;
                return new;
            end
            $$
            """
        );
        jdbcTemplate.execute(
            """
            create trigger trg_catalog_asset_tag_fail_second
            before insert on catalog_asset_tag
            for each row execute function catalog_asset_tag_fail_second()
            """
        );
    }

    @Test
    void productionServiceProxyRollsBackEarlierChunksWhenASecondChunkInsertFails() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        List<AssetRef> assets = new ArrayList<>();
        for (int index = 0; index < CatalogAssetTagService.MAX_BATCH_ASSETS - 1; index++) {
            assets.add(new AssetRef("METRIC", "metric:core/m" + index));
        }
        assets.add(new AssetRef("METRIC", "metric:core/second"));
        BatchAssetTagRequest request = new BatchAssetTagRequest(
            assets,
            List.of(tagId, secondTagId)
        );

        assertThatThrownBy(() -> service.batchTag(request, "alice"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("forced second catalog asset tag failure");

        assertThat(
            jdbcTemplate.queryForObject("select count(*) from catalog_asset_tag", Integer.class)
        ).isZero();
    }

    @Test
    void productionBatchTagSupportsEveryCatalogAssetTypeAndIsIdempotent() {
        List<String> expectedAssetTypes = List.of(CatalogAssetType.values())
            .stream()
            .map(Enum::name)
            .toList();
        List<AssetRef> assets = allCanonicalAssetRefs();
        assertThat(assets).hasSize(20);
        assertThat(assets)
            .extracting(AssetRef::assetType)
            .containsExactlyElementsOf(expectedAssetTypes);
        BatchAssetTagRequest request = new BatchAssetTagRequest(
            assets,
            List.of(tagId)
        );

        BatchAssetTagResult first = service.batchTag(request, "alice");

        assertThat(first.assetCount()).isEqualTo(20);
        assertThat(first.created()).isEqualTo(20);
        assertThat(first.skipped()).isZero();
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_asset_tag",
                Integer.class
            )
        ).isEqualTo(20);
        assertThat(
            jdbcTemplate.queryForList(
                "select distinct asset_type from catalog_asset_tag",
                String.class
            )
        ).containsExactlyInAnyOrderElementsOf(expectedAssetTypes);

        BatchAssetTagResult repeated = service.batchTag(request, "alice");

        assertThat(repeated.assetCount()).isEqualTo(20);
        assertThat(repeated.created()).isZero();
        assertThat(repeated.skipped()).isEqualTo(20);
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from catalog_asset_tag",
                Integer.class
            )
        ).isEqualTo(20);
    }

    private List<AssetRef> allCanonicalAssetRefs() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return List.of(CatalogAssetType.values())
            .stream()
            .map(type ->
                new AssetRef(
                    type.name(),
                    canonicalAssetKey(type, sourceId, entityId)
                )
            )
            .toList();
    }

    private String canonicalAssetKey(
        CatalogAssetType type,
        UUID sourceId,
        UUID entityId
    ) {
        return switch (type) {
            case CATALOG_DOMAIN ->
                CatalogAssetKey.codeAsset(type, "default", "finance");
            case DATASET ->
                CatalogAssetKey.dataset(
                    sourceId,
                    "dwd",
                    "dwd",
                    "orders",
                    "orders"
                );
            case DBT_MODEL ->
                CatalogAssetKey.dbtModel("model.core.orders", null);
            case BI_DATASET -> CatalogAssetKey.biDataset(entityId);
            case SCREEN -> CatalogAssetKey.screen("dashboard-1");
            case METRIC -> CatalogAssetKey.metric("core", "revenue");
            case METRIC_PACK ->
                CatalogAssetKey.metricPack("default", "core", "v1");
            case SEMANTIC_MODEL ->
                CatalogAssetKey.semanticModel(entityId.toString());
            case DATA_PRODUCT ->
                CatalogAssetKey.codeAsset(type, "default", "orders-product");
            case MODELING_SQL_MODEL ->
                CatalogAssetKey.codeAsset(type, "default", "dws_orders");
            case MODELING_PLAN ->
                CatalogAssetKey.codeAsset(
                    type,
                    "default",
                    entityId.toString()
                );
            case DATA_STANDARD ->
                CatalogAssetKey.codeAsset(
                    type,
                    "default",
                    "std-customer-id"
                );
            case METADATA_STANDARD ->
                CatalogAssetKey.codeAsset(
                    type,
                    "default",
                    entityId.toString()
                );
            case GLOSSARY_TERM ->
                CatalogAssetKey.codeAsset(type, "default", "customer");
            case GOV_INDICATOR ->
                CatalogAssetKey.codeAsset(type, "default", "revenue");
            case GOV_INDICATOR_TEMPLATE ->
                CatalogAssetKey.codeAsset(type, "default", "ratio-template");
            case QUALITY_RULE ->
                CatalogAssetKey.codeAsset(type, "default", "not-null");
            case SECURITY_POLICY ->
                CatalogAssetKey.codeAsset(
                    type,
                    "default",
                    entityId.toString()
                );
            case API_SERVICE ->
                CatalogAssetKey.codeAsset(type, "default", "orders-api");
            case BACKFILL_REQUEST ->
                CatalogAssetKey.codeAsset(
                    type,
                    "default",
                    entityId.toString()
                );
        };
    }
}
