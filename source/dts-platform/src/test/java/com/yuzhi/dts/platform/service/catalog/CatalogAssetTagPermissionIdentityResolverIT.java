package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest(properties = "spring.liquibase.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    {
        CatalogAssetTagPermissionReadAdapter.class,
        CatalogAssetTagPermissionIdentityResolver.class,
    }
)
@Testcontainers
class CatalogAssetTagPermissionIdentityResolverIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("catalogAssetTagPermissionIdentityResolverIT")
            .withUsername("catalog_asset_identity_test")
            .withPassword("catalog_asset_identity_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CatalogAssetTagPermissionIdentityResolver resolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID datasetId = uuid(1);
    private final UUID sourceId = uuid(2);
    private final UUID biDatasetId = uuid(3);
    private final UUID semanticModelId = uuid(4);
    private final UUID metadataStandardId = uuid(6);
    private final UUID backfillRequestId = uuid(7);

    @BeforeEach
    void setUpPersistedIdentityFixtures() {
        createFixtureTables();
        insertUuidCode("catalog_domain", uuid(10), "finance");
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, source_id, hive_database, hive_table, name, owner_dept,
                row_security_policy
            ) values (?, ?, 'dwd', 'orders', 'orders', 'D01', 'dept_id = D01')
            """,
            datasetId,
            sourceId
        );
        jdbcTemplate.update(
            "insert into modeling_model_implementation (id, dbt_unique_id) values (?, 'model.core.orders')",
            uuid(11)
        );
        insertUuidOnly("query_dataset_asset", biDatasetId);
        jdbcTemplate.update(
            "insert into bi_report_link (id, code, enabled, report_type) values (?, 'screen-dashboard-1', true, 'SCREEN')",
            uuid(12)
        );
        String metricKey = CatalogAssetKey.metric(
            "default",
            "core",
            "revenue"
        );
        String metricPackKey = CatalogAssetKey.metricPack(
            "default",
            "core",
            "v1"
        );
        insertExternalIdentity("METRIC", metricKey, "metric-revenue");
        insertExternalIdentity("METRIC_PACK", metricPackKey, "core-v1");
        insertUuidOnly("modeling_model_spec", semanticModelId);
        insertUuidCode("catalog_data_product", uuid(13), "orders-product");
        insertUuidCode("data_standard", uuid(15), "std-customer-id");
        insertUuidOnly("metadata_standard", metadataStandardId);
        insertUuidCode("modeling_glossary_term", uuid(16), "customer");
        insertUuidCode("gov_indicator_definition", uuid(17), "revenue");
        insertUuidCode("gov_indicator_template", uuid(18), "ratio-template");
        insertUuidCode("gov_rule", uuid(19), "not-null");
        insertUuidCode("svc_api", uuid(20), "orders-api");
        insertUuidOnly("ops_backfill_request", backfillRequestId);
    }

    @Test
    void resolvesAllTwentyCatalogAssetTypesAgainstPersistedIdentities() {
        List<AssetRef> requests = allTypeRequests();

        List<ResolvedPermissionIdentity> resolved = resolver.resolveAll(
            requests
        );

        assertThat(resolved).hasSize(CatalogAssetType.values().length);
        assertThat(resolved)
            .extracting(ResolvedPermissionIdentity::requestedType)
            .containsExactly(CatalogAssetType.values());
        assertThat(resolved)
            .extracting(ResolvedPermissionIdentity::canonicalAssetKey)
            .containsExactlyElementsOf(
                requests.stream().map(AssetRef::assetKey).toList()
            );
        assertThat(
            resolved
                .stream()
                .filter(item ->
                    item.requestedType() == CatalogAssetType.SECURITY_POLICY
                )
                .findFirst()
        )
            .hasValueSatisfying(item -> {
                assertThat(item.grantAssetType()).isEqualTo("DATASET");
                assertThat(item.grantAssetId())
                    .isEqualTo(datasetId.toString());
                assertThat(item.dataset()).isNotNull();
            });
    }

    private List<AssetRef> allTypeRequests() {
        List<AssetRef> requests = new ArrayList<>();
        requests.add(codeRef(CatalogAssetType.CATALOG_DOMAIN, "finance"));
        requests.add(
            new AssetRef(
                CatalogAssetType.DATASET.name(),
                CatalogAssetKey.dataset(
                    sourceId,
                    "dwd",
                    "dwd",
                    "orders",
                    "orders"
                )
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.DBT_MODEL.name(),
                CatalogAssetKey.dbtModel("model.core.orders", null)
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.BI_DATASET.name(),
                CatalogAssetKey.biDataset(biDatasetId)
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.SCREEN.name(),
                CatalogAssetKey.screen("dashboard-1")
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.METRIC.name(),
                CatalogAssetKey.metric(
                    "default",
                    "core",
                    "revenue"
                )
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.METRIC_PACK.name(),
                CatalogAssetKey.metricPack("default", "core", "v1")
            )
        );
        requests.add(
            new AssetRef(
                CatalogAssetType.SEMANTIC_MODEL.name(),
                CatalogAssetKey.semanticModel(semanticModelId.toString())
            )
        );
        requests.add(
            codeRef(CatalogAssetType.DATA_PRODUCT, "orders-product")
        );
        requests.add(
            codeRef(CatalogAssetType.DATA_STANDARD, "std-customer-id")
        );
        requests.add(
            codeRef(
                CatalogAssetType.METADATA_STANDARD,
                metadataStandardId.toString()
            )
        );
        requests.add(
            codeRef(CatalogAssetType.GLOSSARY_TERM, "customer")
        );
        requests.add(
            codeRef(CatalogAssetType.GOV_INDICATOR, "revenue")
        );
        requests.add(
            codeRef(
                CatalogAssetType.GOV_INDICATOR_TEMPLATE,
                "ratio-template"
            )
        );
        requests.add(
            codeRef(CatalogAssetType.QUALITY_RULE, "not-null")
        );
        requests.add(
            codeRef(
                CatalogAssetType.SECURITY_POLICY,
                datasetId.toString()
            )
        );
        requests.add(codeRef(CatalogAssetType.API_SERVICE, "orders-api"));
        requests.add(
            codeRef(
                CatalogAssetType.BACKFILL_REQUEST,
                backfillRequestId.toString()
            )
        );
        return List.copyOf(requests);
    }

    private AssetRef codeRef(CatalogAssetType type, String naturalKey) {
        return new AssetRef(
            type.name(),
            CatalogAssetKey.codeAsset(type, "default", naturalKey)
        );
    }

    private void createFixtureTables() {
        for (String ddl : List.of(
            "create table catalog_domain (id uuid primary key, code varchar(128) not null)",
            """
            create table catalog_dataset (
                id uuid primary key,
                source_id uuid,
                hive_database varchar(128),
                hive_table varchar(128),
                name varchar(128),
                owner_dept varchar(128),
                row_security_policy text
            )
            """,
            "create table modeling_model_implementation (id uuid primary key, dbt_unique_id varchar(256) not null)",
            "create table query_dataset_asset (id uuid primary key)",
            """
            create table bi_report_link (
                id uuid primary key,
                code varchar(256) not null,
                enabled boolean not null,
                report_type varchar(32) not null
            )
            """,
            """
            create table catalog_external_asset_identity (
                id uuid primary key,
                asset_type varchar(32) not null,
                canonical_asset_key varchar(512) not null,
                remote_asset_id varchar(128) not null,
                active boolean not null
            )
            """,
            "create table modeling_model_spec (id uuid primary key)",
            "create table catalog_data_product (id uuid primary key, code varchar(128) not null)",
            "create table data_standard (id uuid primary key, code varchar(128) not null)",
            "create table metadata_standard (id uuid primary key)",
            "create table modeling_glossary_term (id uuid primary key, code varchar(128) not null)",
            "create table gov_indicator_definition (id uuid primary key, code varchar(128) not null)",
            "create table gov_indicator_template (id uuid primary key, code varchar(128) not null)",
            "create table gov_rule (id uuid primary key, code varchar(128) not null)",
            "create table svc_api (id uuid primary key, code varchar(128) not null)",
            "create table ops_backfill_request (id uuid primary key)"
        )) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void insertUuidCode(String table, UUID id, String code) {
        jdbcTemplate.update(
            "insert into " + table + " (id, code) values (?, ?)",
            id,
            code
        );
    }

    private void insertUuidOnly(String table, UUID id) {
        jdbcTemplate.update("insert into " + table + " (id) values (?)", id);
    }

    private void insertExternalIdentity(
        String type,
        String canonicalKey,
        String remoteId
    ) {
        jdbcTemplate.update(
            """
            insert into catalog_external_asset_identity (
                id, asset_type, canonical_asset_key, remote_asset_id, active
            ) values (?, ?, ?, ?, true)
            """,
            UUID.randomUUID(),
            type,
            canonicalKey,
            remoteId
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString(
            "00000000-0000-0000-0000-" + String.format("%012d", suffix)
        );
    }
}
