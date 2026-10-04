package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.catalog.JdbcCatalogAssetSemanticStore;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetNormalizationMigrationService.Resolution;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.RelationType;
import java.sql.Connection;
import java.sql.DriverManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogAssetNormalizationMigrationServiceIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810_02_catalog_asset_semantics_projection.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s87_asset_normalization_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("assetNormalizationMigrationIT")
        .withUsername("asset_normalization_test")
        .withPassword("asset_normalization_test");

    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private CatalogAssetNormalizationMigrationService service;
    private AuditService auditService;
    private UUID sourceDatasetId;
    private UUID dimensionDatasetId;
    private UUID sourceId;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s87_asset_normalization_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        sourceDatasetId = UUID.randomUUID();
        dimensionDatasetId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute("create table catalog_domain(id uuid primary key)");
            statement.execute("insert into catalog_domain values ('" + domainId + "')");
            statement.execute(
                """
                create table catalog_dataset(
                    id uuid primary key, name varchar(128) not null, domain_id uuid,
                    type varchar(32), source_id uuid, hive_database varchar(128), hive_table varchar(128),
                    warehouse_layer varchar(16), classification varchar(32), owner varchar(64),
                    owner_dept varchar(64), lifecycle_status varchar(32), enabled boolean not null default true
                )
                """
            );
            statement.execute(
                "insert into catalog_dataset(id,name,domain_id,type,source_id,hive_database,hive_table,warehouse_layer,enabled) values ('" + sourceDatasetId +
                "', '源系统客户', '" + domainId + "', 'jdbc', '" + sourceId +
                "', 'crm', 'customer', 'SOURCE', true)"
            );
            statement.execute(
                "insert into catalog_dataset(id,name,domain_id,type,source_id,hive_database,hive_table,warehouse_layer,enabled) values ('" + dimensionDatasetId +
                "', '客户维度', '" + domainId + "', 'jdbc', null, 'dwd', 'dim_customer', 'DIM', true)"
            );
        }
        applyMigration();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var store = new JdbcCatalogAssetSemanticStore(jdbc, new ObjectMapper());
        auditService = mock(AuditService.class);
        service = new CatalogAssetNormalizationMigrationService(
            jdbc,
            new CatalogAssetRegistrationService(store),
            auditService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) return;
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void previewApplyAndRollbackKeepLegacyValuesAndRequireExplicitDimensionEvidence() {
        var preview = service.preview(100);

        assertThat(service.preview(100).previewHash()).isEqualTo(preview.previewHash());

        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows()).filteredOn(row -> row.resourceId().equals(sourceDatasetId)).singleElement().satisfies(row -> {
            assertThat(row.automatic()).isTrue();
            assertThat(row.issueCode()).isNull();
        });
        assertThat(preview.rows()).filteredOn(row -> row.resourceId().equals(dimensionDatasetId)).singleElement().satisfies(row -> {
            assertThat(row.automatic()).isFalse();
            assertThat(row.issueCode()).isEqualTo("DIMENSION_CONFIRMATION_REQUIRED");
        });

        Resolution dimensionResolution = new Resolution(
            dimensionDatasetId,
            true,
            ProducerKind.MODELING,
            "model-spec-customer",
            "r2",
            RelationType.TABLE
        );
        var applied = transaction.execute(status ->
            service.apply(preview.previewHash(), 100, List.of(dimensionResolution), "it-correlation")
        );
        assertThat(applied.applied()).isEqualTo(2);
        assertThat(jdbc.queryForMap(
            "select canonical_layer, legacy_layer_code, asset_role from catalog_asset_semantic_projection where resource_id = ?",
            sourceDatasetId
        ))
            .containsEntry("canonical_layer", null)
            .containsEntry("legacy_layer_code", "SOURCE")
            .containsEntry("asset_role", "RELATION");
        assertThat(jdbc.queryForMap(
            "select canonical_layer, legacy_layer_code, asset_role from catalog_asset_semantic_projection where resource_id = ?",
            dimensionDatasetId
        ))
            .containsEntry("canonical_layer", "DWD")
            .containsEntry("legacy_layer_code", "DIM")
            .containsEntry("asset_role", "DIMENSION_TABLE");
        assertThat(jdbc.queryForList("select warehouse_layer from catalog_dataset order by warehouse_layer"))
            .extracting(row -> row.get("warehouse_layer"))
            .containsExactly("DIM", "SOURCE");

        var rolledBack = transaction.execute(status -> service.rollback(applied.batchId()));
        assertThat(rolledBack.removed()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_semantic_projection", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from catalog_asset_normalization_issue where status = 'PENDING'",
            Long.class
        )).isEqualTo(2);
        verify(auditService).auditActionStrict(
            eq("CATALOG_ASSET_SEMANTIC_BACKFILL_APPLY"),
            eq(AuditStage.SUCCESS),
            eq(applied.batchId().toString()),
            argThat(payload -> payload instanceof java.util.Map<?, ?> map && "it-correlation".equals(map.get("correlationId")))
        );
        verify(auditService).auditActionStrict(
            eq("CATALOG_ASSET_SEMANTIC_BACKFILL_ROLLBACK"),
            eq(AuditStage.SUCCESS),
            eq(applied.batchId().toString()),
            argThat(payload -> payload instanceof java.util.Map<?, ?> map && Integer.valueOf(2).equals(map.get("removed")))
        );
    }

    @Test
    void previewsEveryMissingProjectionButRequiresExplicitProducerForCanonicalLayers() {
        UUID odsDatasetId = UUID.randomUUID();
        jdbc.update(
            """
            insert into catalog_dataset(
                id, name, domain_id, type, source_id, hive_database, hive_table,
                warehouse_layer, classification, owner, lifecycle_status, enabled
            ) values (?, '订单接入表', null, 'POSTGRESQL', ?, 'public', 'ods_orders',
                      'ODS', null, null, 'PENDING_GOVERNANCE', true)
            """,
            odsDatasetId,
            sourceId
        );

        var preview = service.preview(100);

        assertThat(preview.totalPending()).isEqualTo(3);
        assertThat(preview.rows()).filteredOn(row -> row.resourceId().equals(odsDatasetId)).singleElement().satisfies(row -> {
            assertThat(row.automatic()).isFalse();
            assertThat(row.issueCode()).isEqualTo("PRODUCER_RESOLUTION_REQUIRED");
            assertThat(row.resolutionStatus()).isEqualTo("MANUAL_RESOLUTION_REQUIRED");
            assertThat(row.currentGovernance()).isEqualTo("UNASSIGNED");
            assertThat(row.currentLifecycle()).isEqualTo("PENDING_GOVERNANCE");
        });

        Resolution odsResolution = new Resolution(
            odsDatasetId,
            false,
            ProducerKind.INGESTION_JOB,
            "ingestion-job-orders",
            "run-7",
            RelationType.TABLE
        );
        var applied = transaction.execute(status ->
            service.apply(preview.previewHash(), 100, List.of(odsResolution), "it-canonical-layer")
        );

        assertThat(applied.applied()).isEqualTo(2);
        assertThat(applied.skipped()).isEqualTo(1);
        assertThat(applied.issueCount()).isEqualTo(1);
        assertThat(jdbc.queryForMap(
            "select canonical_layer, producer_kind from catalog_asset_semantic_projection p join catalog_asset_producer_ref r using(asset_type, asset_key) where p.resource_id = ? and r.valid_to is null",
            odsDatasetId
        ))
            .containsEntry("canonical_layer", "ODS")
            .containsEntry("producer_kind", "INGESTION_JOB");
    }

    @Test
    void rejectsOversizedBatchAndFailsClosedWhenPreviewDriftsOrApplyIsReplayed() {
        assertThatThrownBy(() -> service.preview(501))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CATALOG_ASSET_NORMALIZATION_LIMIT_INVALID");

        var preview = service.preview(100);
        jdbc.update(
            """
            insert into catalog_dataset(
                id, name, type, source_id, hive_database, hive_table, warehouse_layer, enabled
            ) values (?, '新增源表', 'POSTGRESQL', ?, 'public', 'new_source', 'SOURCE', true)
            """,
            UUID.randomUUID(),
            sourceId
        );
        assertThatThrownBy(() -> service.apply(preview.previewHash(), 100, List.of(), "it-drift"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CATALOG_ASSET_NORMALIZATION_PREVIEW_STALE");

        var current = service.preview(100);
        var applied = transaction.execute(status -> service.apply(current.previewHash(), 100, List.of(), "it-replay"));
        long projectionCount = jdbc.queryForObject("select count(*) from catalog_asset_semantic_projection", Long.class);
        assertThatThrownBy(() -> service.apply(current.previewHash(), 100, List.of(), "it-replay"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CATALOG_ASSET_NORMALIZATION_PREVIEW_STALE");
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_semantic_projection", Long.class))
            .isEqualTo(projectionCount);
        assertThat(applied.applied()).isPositive();
    }

    private void applyMigration() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private DriverManagerDataSource dataSourceInSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        dataSource.setUrl(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("refusing to mutate unowned schema: " + schema);
        }
    }
}
