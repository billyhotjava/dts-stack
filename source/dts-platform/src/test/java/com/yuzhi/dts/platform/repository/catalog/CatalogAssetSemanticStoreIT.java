package com.yuzhi.dts.platform.repository.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetSemanticsView;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.RegistrationReceipt;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogAssetSemanticStoreIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260810_02_catalog_asset_semantics_projection.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s87_asset_semantics_[0-9a-f]{32}$");
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final UUID RESOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("catalogAssetSemanticStoreIT")
        .withUsername("asset_semantics_test")
        .withPassword("asset_semantics_test");

    private String schema;
    private JdbcTemplate jdbc;
    private JdbcCatalogAssetSemanticStore store;
    private TransactionTemplate transaction;
    private UUID domainId;

    @BeforeEach
    void setUp() throws Exception {
        schema = "s87_asset_semantics_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute("create table catalog_domain(id uuid primary key)");
            statement.execute("create table catalog_dataset(id uuid primary key, warehouse_layer varchar(16))");
            statement.execute("insert into catalog_dataset values (gen_random_uuid(), 'SOURCE'), (gen_random_uuid(), 'DIM')");
        }
        applyMigration();
        DriverManagerDataSource dataSource = dataSourceInSchema();
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcCatalogAssetSemanticStore(jdbc, new ObjectMapper());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        domainId = UUID.randomUUID();
        jdbc.update("insert into catalog_domain(id) values (?)", domainId);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void migrationQueuesLegacySourceAndDimWithoutChangingThem() {
        assertThat(jdbc.queryForList("select warehouse_layer from catalog_dataset order by warehouse_layer", String.class))
            .containsExactly("DIM", "SOURCE");
        assertThat(jdbc.queryForList("select reason_code from catalog_asset_normalization_issue order by reason_code", String.class))
            .containsExactly("LAYER_NORMALIZATION_REQUIRED", "SOURCE_PRODUCER_UNRESOLVED");
    }

    @Test
    void repeatedDiscoveryMergesOneIdentityAndRefreshesEvidenceWithoutInflatingStats() {
        RegistrationPlan plan = admittedPlan("scan-1", GovernanceReadiness.UNASSIGNED, null);

        RegistrationReceipt first = transaction.execute(status -> store.register(plan, NOW));
        RegistrationReceipt repeated = transaction.execute(status -> store.register(plan, NOW.plusSeconds(30)));

        assertThat(first.created()).isTrue();
        assertThat(first.evidenceCreated()).isTrue();
        assertThat(first.assetType()).isEqualTo(CatalogAssetType.DATASET);
        assertThat(first.assetKey()).isEqualTo(assetKey());
        assertThat(first.resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.evidenceCreated()).isFalse();
        assertThat(repeated.projectionVersion()).isEqualTo(first.projectionVersion());
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_semantic_projection", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_producer_ref", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_registration_evidence", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select sum(asset_count) from catalog_asset_stats_projection", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_projection_event", Long.class)).isEqualTo(1L);
    }

    @Test
    void rejectsTheSameAssetKeyWhenItPointsAtAnotherDurableResource() {
        RegistrationPlan original = admittedPlan("scan-1", GovernanceReadiness.UNASSIGNED, null);
        RegistrationPlan conflicting = admittedPlan(
            "scan-2",
            GovernanceReadiness.UNASSIGNED,
            null,
            UUID.fromString("33333333-3333-3333-3333-333333333333")
        );
        transaction.execute(status -> store.register(original, NOW));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            transaction.execute(status -> store.register(conflicting, NOW.plusSeconds(30)))
        )
            .isInstanceOf(com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticConflictException.class)
            .hasMessageContaining("different durable resource");
        assertThat(jdbc.queryForObject("select count(*) from catalog_asset_semantic_projection", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select resource_id from catalog_asset_semantic_projection", UUID.class)).isEqualTo(RESOURCE_ID);
    }

    @Test
    void governanceChangeMovesOneAssetBetweenBucketsAndReconciliationKeepsExactCounts() {
        transaction.execute(status -> store.register(admittedPlan("scan-1", GovernanceReadiness.UNASSIGNED, null), NOW));
        transaction.execute(status ->
            store.updateGovernance(
                CatalogAssetType.DATASET,
                assetKey(),
                domainId,
                GovernanceReadiness.GOVERNED,
                NOW.plusSeconds(60)
            )
        );

        StatsSnapshot stats = store.stats(domainId, NOW.plusSeconds(61));
        AssetSemanticsView view = store.find(CatalogAssetType.DATASET, assetKey(), NOW.plusSeconds(61)).orElseThrow();

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.approximate()).isFalse();
        assertThat(stats.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.domainId()).isEqualTo(domainId);
            assertThat(bucket.governance()).isEqualTo(GovernanceReadiness.GOVERNED);
        });
        assertThat(view.evidence()).hasSize(1);
        assertThat(view.currentProducer().producerKind()).isEqualTo(ProducerKind.DBT_MODEL);
        assertThat(view.statusAxes().governance()).isEqualTo(GovernanceReadiness.GOVERNED);

        var reconciliation = transaction.execute(status -> store.reconcile(NOW.plusSeconds(120)));
        assertThat(reconciliation.assetCount()).isEqualTo(1);
        assertThat(reconciliation.bucketCount()).isEqualTo(1);
        assertThat(store.stats(null, NOW.plusSeconds(121)).total()).isEqualTo(1);
    }

    @Test
    void batchStatusReadReturnsOnlyRegisteredKeysWithoutPerAssetLookups() {
        transaction.execute(status -> store.register(admittedPlan("scan-1", GovernanceReadiness.GOVERNED, domainId), NOW));

        var snapshots = store.findStatusSnapshots(
            CatalogAssetType.DATASET,
            List.of(assetKey(), "source:missing/schema:public/table:missing"),
            NOW.plusSeconds(1)
        );

        assertThat(snapshots).containsOnlyKeys(assetKey());
        assertThat(snapshots.get(assetKey()).statusAxes().publication()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(snapshots.get(assetKey()).eligibility().decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
        assertThat(snapshots.get(assetKey()).qualityGatePassed()).isTrue();
    }

    private RegistrationPlan admittedPlan(String evidenceRef, GovernanceReadiness governance, UUID domain) {
        return admittedPlan(evidenceRef, governance, domain, RESOURCE_ID);
    }

    private RegistrationPlan admittedPlan(
        String evidenceRef,
        GovernanceReadiness governance,
        UUID domain,
        UUID resourceId
    ) {
        ObservationCommand command = new ObservationCommand(
            CatalogAssetType.DATASET,
            assetKey(),
            resourceId,
            RelationType.TABLE,
            false,
            false,
            domain,
            "DWD",
            AssetRole.RELATION,
            ProducerKind.DBT_MODEL,
            "model.orders",
            "v1",
            EvidenceChannel.SCANNER,
            evidenceRef,
            NOW,
            EvidenceStatus.ACTIVE,
            new StatusAxes(
                DiscoveryState.VERIFIED,
                governance,
                PublicationState.PUBLISHED,
                ServingHealth.HEALTHY,
                LifecycleState.ACTIVE
            ),
            true,
            true
        );
        return CatalogAssetSemanticsContract.admit(command, NOW).plan();
    }

    private String assetKey() {
        return "source:22222222-2222-2222-2222-222222222222/schema:public/table:orders";
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
