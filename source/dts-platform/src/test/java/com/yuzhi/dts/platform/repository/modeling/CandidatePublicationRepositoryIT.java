package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceKind;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRole;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalLocator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class CandidatePublicationRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-07-28T15:00:00Z");
    private static final String TARGET_KEY = "postgres:warehouse/prod";
    private static final UUID SOURCE_ID =
        UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Autowired
    private CandidatePublicationRepository publications;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogDatasetRepository catalogDatasets;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlatformEventOutboxService outbox;

    @Test
    void rebuildsOnlyTheRequestedEnvironmentRollsBackAtomicallyAndRejectsLegacyFacts() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(scope, scope.prodModelId(), scope.prodReleaseId(), "PROD", true);
                seedPublishedModel(scope, scope.secondProdModelId(), scope.secondProdReleaseId(), "PROD", true);
                seedPublishedModel(scope, scope.testModelId(), scope.testReleaseId(), "TEST", true);
            });
            CandidateView candidate = candidate(scope);
            PublishedModelBinding committed = new PublishedModelBinding(
                scope.prodModelId(),
                scope.prodReleaseId(),
                1,
                dbtUniqueId(scope.prodModelId()),
                targetIdentifier(scope.prodModelId()),
                "c".repeat(64),
                "d".repeat(64)
            );

            assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    publications.rebuildManualBinding(
                        candidate,
                        List.of(committed),
                        "release-operator",
                        NOW
                    );
                    throw new IllegalStateException("inject-after-binding");
                })
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inject-after-binding");
            assertThat(bindingCount(scope)).isZero();

            transaction.executeWithoutResult(status ->
                publications.rebuildManualBinding(
                    candidate,
                    List.of(committed),
                    "release-operator",
                    NOW
                )
            );
            assertThat(bindingCount(scope)).isEqualTo(1);
            assertThat(bindingDagId(scope))
                .matches("dts_plan_[0-9a-f]{32}")
                .doesNotContain("release_build");
            assertThat(bindingModelIds(scope))
                .containsExactlyInAnyOrder(scope.prodModelId(), scope.secondProdModelId());

            String operationalChecksum = bindingDeploymentChecksum(scope);
            transaction.executeWithoutResult(status ->
                jdbcTemplate.update(
                    """
                    update modeling_plan_execution_binding
                       set dag_id = 'dts_release_build_postgres_primary',
                           desired_deployment_checksum = ?,
                           deployed_checksum = ?,
                           deployment_status = 'ACTIVE'
                     where tenant_id = ? and plan_id = ? and environment = 'PROD'
                       and execution_target_key = ?
                    """,
                    "e".repeat(64),
                    "e".repeat(64),
                    scope.tenantId(),
                    scope.planId(),
                    TARGET_KEY
                )
            );
            transaction.executeWithoutResult(status ->
                publications.rebuildManualBinding(
                    candidate,
                    List.of(committed),
                    "release-operator",
                    NOW.plusSeconds(1)
                )
            );
            assertThat(bindingDagId(scope)).matches("dts_plan_[0-9a-f]{32}");
            assertThat(bindingDeploymentChecksum(scope))
                .isEqualTo(operationalChecksum)
                .isNotEqualTo("e".repeat(64));
            assertThat(bindingVersion(scope)).isEqualTo(2);
            assertThat(bindingDeploymentStatus(scope)).isEqualTo("DEPLOYING");

            transaction.executeWithoutResult(status ->
                jdbcTemplate.update(
                    "update modeling_model_lifecycle_event set details_json = '{}'::jsonb where id = ?",
                    scope.testReleaseId()
                )
            );
            assertThatThrownBy(() ->
                transaction.executeWithoutResult(status ->
                    publications.rebuildManualBinding(
                        candidate,
                        List.of(committed),
                        "release-operator",
                        NOW.plusSeconds(1)
                    )
                )
            )
                .isInstanceOf(ModelReleaseCandidateException.class)
                .satisfies(error ->
                    assertThat(((ModelReleaseCandidateException) error).code())
                        .isEqualTo("MODEL_PLAN_BINDING_RELEASE_FACTS_REQUIRED")
                );
            assertThat(bindingModelIds(scope))
                .containsExactlyInAnyOrder(scope.prodModelId(), scope.secondProdModelId());
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void preparesHarvestedPhysicalAssetBeforeQualityAndReusesItAtPublication() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        UUID harvestedAssetId = UUID.randomUUID();
        String tableName =
            "fct_quality_" +
            scope.prodModelId().toString().replace("-", "").substring(0, 12);
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedCompiledArtifact(scope);
                jdbcTemplate.update(
                    """
                    insert into catalog_dataset (
                        id, name, type, source_id,
                        hive_database, hive_table, enabled,
                        lifecycle_status, created_date, last_modified_date
                    ) values (
                        ?, ?, 'jdbc', ?,
                        'finance', ?, true,
                        'PENDING_GOVERNANCE', ?, ?
                    )
                    """,
                    harvestedAssetId,
                    tableName,
                    SOURCE_ID,
                    tableName,
                    Timestamp.from(NOW.minusSeconds(60)),
                    Timestamp.from(NOW.minusSeconds(60))
                );
            });
            CandidateView candidate = candidate(scope);
            ResolvedCatalogTarget target = new ResolvedCatalogTarget(
                TARGET_KEY,
                SOURCE_ID,
                "postgres"
            );
            PublicationEntryEvidence observation =
                publicationObservation(scope, tableName);
            ModelSpecView model = publicationModel(scope);

            UUID preparedAssetId = transaction.execute(status ->
                publications.prepareQualityDataset(
                    candidate,
                    target,
                    observation,
                    model,
                    "CONFIDENTIAL",
                    "quality-service",
                    NOW
                )
            );

            assertThat(preparedAssetId).isEqualTo(harvestedAssetId);
            assertThat(catalogDatasetCount(tableName)).isEqualTo(1);
            assertThat(catalogDatasetValue(harvestedAssetId, "classification"))
                .isEqualTo("CONFIDENTIAL");
            assertThat(catalogDatasetValue(harvestedAssetId, "warehouse_layer"))
                .isEqualTo("DWD");
            assertThat(catalogDatasetValue(harvestedAssetId, "lifecycle_status"))
                .isEqualTo("PENDING_GOVERNANCE");
            assertThat(
                jdbcTemplate.queryForObject(
                    "select domain_id from catalog_dataset where id = ?",
                    UUID.class,
                    harvestedAssetId
                )
            ).isEqualTo(scope.domainId());

            transaction.executeWithoutResult(status -> jdbcTemplate.update(
                "update catalog_dataset set owner = ?, description = ?, tags = ?, version = version + 1 where id = ?",
                "business-owner", "manually maintained", "{\"businessTag\":\"retained\"}", harvestedAssetId
            ));
            Long maintainedVersion = jdbcTemplate.queryForObject(
                "select version from catalog_dataset where id = ?", Long.class, harvestedAssetId
            );
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());
            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW.plusSeconds(1)
                )
            );

            assertThat(catalogDatasetCount(tableName)).isEqualTo(1);
            assertThat(catalogDatasetValue(harvestedAssetId, "classification"))
                .isEqualTo("CONFIDENTIAL");
            assertThat(catalogDatasetValue(harvestedAssetId, "lifecycle_status"))
                .isEqualTo("ACTIVE");
            assertThat(jdbcTemplate.queryForObject("select owner from catalog_dataset where id = ?", String.class, harvestedAssetId)).isEqualTo("business-owner");
            assertThat(jdbcTemplate.queryForObject("select description from catalog_dataset where id = ?", String.class, harvestedAssetId)).isEqualTo("manually maintained");
            assertThat(jdbcTemplate.queryForObject(
                "select cast(tags as jsonb) ->> 'businessTag' from catalog_dataset where id = ?",
                String.class, harvestedAssetId
            )).isEqualTo("retained");
            assertThat(jdbcTemplate.queryForObject(
                "select version from catalog_dataset where id = ?", Long.class, harvestedAssetId
            )).isEqualTo(maintainedVersion + 1);
            // A stale editor must not be able to overwrite a publication's version.
            assertThat(transaction.<Integer>execute(status -> jdbcTemplate.update(
                "update catalog_dataset set description = 'stale' where id = ? and version = ?",
                harvestedAssetId, maintainedVersion
            ))).isZero();
            transaction.executeWithoutResult(status -> jdbcTemplate.update(
                "update catalog_dataset set owner = null, description = null, version = version + 1 where id = ?",
                harvestedAssetId
            ));
            transaction.executeWithoutResult(status -> publications.registerModel(
                candidate, target, observation, model, release, "another-operator", NOW.plusSeconds(2)
            ));
            assertThat(jdbcTemplate.queryForObject("select owner from catalog_dataset where id = ?", String.class, harvestedAssetId)).isNull();
            assertThat(jdbcTemplate.queryForObject("select description from catalog_dataset where id = ?", String.class, harvestedAssetId)).isNull();
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void registersOneSourceBoundPhysicalAssetAndProjectsObservedColumns() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        UUID legacyAssetId = UUID.randomUUID();
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedCompiledArtifact(scope);
                jdbcTemplate.update(
                    """
                    insert into catalog_dataset (
                        id, name, type, classification,
                        hive_database, hive_table, enabled,
                        lifecycle_status, created_date, last_modified_date
                    ) values (
                        ?, 'legacy dbt asset', 'POSTGRES', 'L2',
                        'Finance', 'FCT_PAYMENT', true,
                        'DISCOVERED', ?, ?
                    )
                    """,
                    legacyAssetId,
                    Timestamp.from(NOW.minusSeconds(60)),
                    Timestamp.from(NOW.minusSeconds(60))
                );
            });
            CandidateView candidate = candidate(scope);
            PublicationEntryEvidence observation =
                new PublicationEntryEvidence(
                    scope.entryId(),
                    scope.prodModelId(),
                    1,
                    "a".repeat(64),
                    1,
                    "b".repeat(64),
                    dbtUniqueId(scope.prodModelId()),
                    "FCT_PAYMENT",
                    "c".repeat(64),
                    "d".repeat(64),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "postgres",
                    "warehouse",
                    "finance",
                    "fct_payment",
                    ExpectedRelationType.TABLE,
                    List.of(
                        new PhysicalColumn(
                            1,
                            "payment_id",
                            "bigint",
                            false
                        ),
                        new PhysicalColumn(
                            2,
                            "settled_at",
                            "timestamp without time zone",
                            true
                        )
                    ),
                    "f".repeat(64),
                    NOW.minusSeconds(10)
                );
            ModelSpecView model = org.mockito.Mockito.mock(
                ModelSpecView.class
            );
            org.mockito.Mockito.when(model.id())
                .thenReturn(scope.prodModelId());
            org.mockito.Mockito.when(model.revision()).thenReturn(1);
            org.mockito.Mockito.when(model.checksum())
                .thenReturn("a".repeat(64));
            org.mockito.Mockito.when(model.name())
                .thenReturn("Finance payment fact");
            org.mockito.Mockito.when(model.description())
                .thenReturn("Observed payment output");
            org.mockito.Mockito.when(model.domainId())
                .thenReturn(scope.domainId());
            org.mockito.Mockito.when(model.layer()).thenReturn(Layer.DWD);
            org.mockito.Mockito.when(model.dependsOn())
                .thenReturn(List.of());
            org.mockito.Mockito.when(model.fields())
                .thenReturn(
                    List.of(
                        new ModelField(
                            "payment_id",
                            "Payment ID",
                            "integer",
                            false,
                            null,
                            FieldRole.KEY,
                            "L2",
                            null,
                            false,
                            null
                        )
                    )
                );
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());

            PublishedModelBinding binding = transaction.execute(status ->
                publications.registerModel(
                    candidate,
                    new ResolvedCatalogTarget(
                        TARGET_KEY,
                        SOURCE_ID,
                        "postgres"
                    ),
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW
                )
            );

            assertThat(binding).isNotNull();
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_dataset
                     where source_id = ?
                       and lower(btrim(hive_database)) = 'finance'
                       and lower(btrim(hive_table)) = 'fct_payment'
                    """,
                    Integer.class,
                    SOURCE_ID
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    "select lifecycle_status from catalog_dataset where id = ?",
                    String.class,
                    legacyAssetId
                )
            ).isEqualTo("ACTIVE");
            assertThat(
                jdbcTemplate.queryForObject(
                    "select source_id from catalog_dataset where id = ?",
                    UUID.class,
                    legacyAssetId
                )
            ).isEqualTo(SOURCE_ID);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select physical_asset_ref
                      from modeling_dbt_artifact
                     where model_spec_id = ?
                    """,
                    UUID.class,
                    scope.prodModelId()
                )
            ).isEqualTo(legacyAssetId);
            assertThat(
                jdbcTemplate.queryForList(
                    """
                    select column_schema.name || ':' ||
                           column_schema.data_type
                      from catalog_column_schema column_schema
                      join catalog_table_schema table_schema
                        on table_schema.id = column_schema.table_id
                     where table_schema.dataset_id = ?
                     order by column_schema.name
                    """,
                    String.class,
                    legacyAssetId
                )
            )
                .containsExactly(
                    "payment_id:bigint",
                    "settled_at:timestamp without time zone"
                );

            assertThatThrownBy(() ->
                transaction.executeWithoutResult(status ->
                    jdbcTemplate.update(
                        """
                        insert into catalog_dataset (
                            id, name, source_id,
                            hive_database, hive_table
                        ) values (?, 'duplicate', ?, 'FINANCE', 'fct_payment')
                        """,
                        UUID.randomUUID(),
                        SOURCE_ID
                    )
                )
            ).isInstanceOf(
                org.springframework.dao.DataIntegrityViolationException.class
            );
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void concurrentCandidateAndGenericDbtWriterConvergeToOneDataset() throws Exception {
        TestScope scope = TestScope.create();
        TransactionTemplate setup = new TransactionTemplate(
            transactionManager
        );
        String tableName =
            "fct_concurrent_" +
            scope.prodModelId().toString().replace("-", "").substring(0, 12);
        try {
            setup.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedCompiledArtifact(scope);
            });
            CandidateView candidate = candidate(scope);
            PublicationEntryEvidence observation =
                publicationObservation(scope, tableName);
            ModelSpecView model = publicationModel(scope);
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());
            ResolvedCatalogTarget target = new ResolvedCatalogTarget(
                TARGET_KEY,
                SOURCE_ID,
                "postgres"
            );
            UUID expectedAssetId = new CatalogPhysicalLocator(
                SOURCE_ID,
                "finance",
                tableName
            ).assetId();
            CyclicBarrier start = new CyclicBarrier(2);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var candidateWrite = executor.submit(() -> {
                    start.await();
                    return new TransactionTemplate(
                        transactionManager
                    ).execute(status ->
                        publications.registerModel(
                            candidate,
                            target,
                            observation,
                            model,
                            release,
                            "release-operator",
                            NOW
                        )
                    );
                });
                var genericDbtWrite = executor.submit(() -> {
                    start.await();
                    boolean lostRace = false;
                    try {
                        new TransactionTemplate(
                            transactionManager
                        ).executeWithoutResult(status -> {
                            CatalogDataset dataset =
                                new CatalogDataset();
                            dataset.setId(expectedAssetId);
                            dataset.setName(tableName);
                            dataset.setType("POSTGRES");
                            dataset.setSourceId(SOURCE_ID);
                            dataset.setHiveDatabase("finance");
                            dataset.setHiveTable(tableName);
                            dataset.setEnabled(Boolean.TRUE);
                            dataset.setLifecycleStatus("DISCOVERED");
                            catalogDatasets.saveAndFlush(dataset);
                        });
                    } catch (
                        org.springframework.dao.DataIntegrityViolationException expectedRace
                    ) {
                        lostRace = true;
                    } catch (
                        org.springframework.orm.ObjectOptimisticLockingFailureException expectedRace
                    ) {
                        lostRace = true;
                    }
                    if (lostRace) {
                        return null;
                    }
                    return new TransactionTemplate(
                        transactionManager
                    ).execute(status ->
                        catalogDatasets
                            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                                SOURCE_ID,
                                "finance",
                                tableName
                            )
                            .orElseThrow()
                            .getId()
                    );
                });

                assertThat(candidateWrite.get()).isNotNull();
                UUID genericAssetId = genericDbtWrite.get();
                if (genericAssetId == null) {
                    // Simulate the next idempotent dbt tick after the publication winner commits.
                    genericAssetId = new TransactionTemplate(
                        transactionManager
                    ).execute(status ->
                        catalogDatasets
                            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                                SOURCE_ID,
                                "finance",
                                tableName
                            )
                            .orElseThrow()
                            .getId()
                    );
                }
                assertThat(genericAssetId)
                    .isEqualTo(expectedAssetId);
            } finally {
                executor.shutdownNow();
            }

            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_dataset
                     where source_id = ?
                       and lower(btrim(hive_database)) = 'finance'
                       and lower(btrim(hive_table)) = lower(?)
                    """,
                    Integer.class,
                    SOURCE_ID,
                    tableName
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select physical_asset_ref
                      from modeling_dbt_artifact
                     where model_spec_id = ?
                    """,
                    UUID.class,
                    scope.prodModelId()
                )
            ).isEqualTo(expectedAssetId);
        } finally {
            setup.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void dbtManagedRegistrationRetryKeepsFieldAndLineageCountsStable() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        String tableName =
            "fct_dbt_retry_" +
            scope.prodModelId().toString().replace("-", "").substring(0, 12);
        UUID upstreamAssetId = UUID.randomUUID();
        UUID upstreamTableId = UUID.randomUUID();
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedPublishedModel(
                    scope,
                    scope.secondProdModelId(),
                    scope.secondProdReleaseId(),
                    "PROD",
                    true
                );
                jdbcTemplate.update(
                    """
                    update modeling_model_spec
                       set implementation_mode = 'DBT_MANAGED'
                     where id = ?
                    """,
                    scope.prodModelId()
                );
                seedCompiledArtifact(scope);
                jdbcTemplate.update(
                    """
                    update modeling_dbt_artifact
                       set ownership = 'DBT_MANAGED',
                           content = ?
                     where model_spec_id = ?
                    """,
                    "select u.payment_id as payment_id from dim_upstream_" +
                        scope.secondProdModelId().toString().replace("-", "").substring(0, 12) + " u",
                    scope.prodModelId()
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_dataset (
                        id, name, type, source_id, classification,
                        hive_database, hive_table, enabled,
                        lifecycle_status, created_date, last_modified_date
                    ) values (
                        ?, 'upstream dimension', 'POSTGRES', ?, 'L2',
                        'finance', ?, true, 'PUBLISHED', ?, ?
                    )
                    """,
                    upstreamAssetId,
                    SOURCE_ID,
                    "dim_upstream_" +
                    scope.secondProdModelId()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12),
                    Timestamp.from(NOW.minusSeconds(30)),
                    Timestamp.from(NOW.minusSeconds(30))
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_table_schema (
                        id, dataset_id, name, classification,
                        created_by, created_date, last_modified_date
                    ) values (?, ?, 'dim_upstream', 'L2', 'it', ?, ?)
                    """,
                    upstreamTableId,
                    upstreamAssetId,
                    Timestamp.from(NOW.minusSeconds(30)),
                    Timestamp.from(NOW.minusSeconds(30))
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_column_schema (
                        id, table_id, name, data_type, nullable, status,
                        created_by, created_date, last_modified_date
                    ) values (
                        ?, ?, 'payment_id', 'bigint', false, 'ACTIVE',
                        'it', ?, ?
                    )
                    """,
                    UUID.randomUUID(),
                    upstreamTableId,
                    Timestamp.from(NOW.minusSeconds(30)),
                    Timestamp.from(NOW.minusSeconds(30))
                );
                jdbcTemplate.update(
                    """
                    update modeling_model_lifecycle_event
                       set details_json = details_json ||
                           jsonb_build_object(
                               'physicalAssetId',
                               cast(? as text)
                           )
                     where id = ?
                    """,
                    upstreamAssetId,
                    scope.secondProdReleaseId()
                );
            });
            CandidateView candidate = candidate(scope);
            PublicationEntryEvidence observation =
                publicationObservation(scope, tableName);
            ModelSpecView model = publicationModel(scope);
            org.mockito.Mockito.when(model.dependsOn())
                .thenReturn(
                    List.of(
                        new ModelRevisionRef(
                            scope.secondProdModelId(),
                            1
                        )
                    )
                );
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());
            ResolvedCatalogTarget target = new ResolvedCatalogTarget(
                TARGET_KEY,
                SOURCE_ID,
                "postgres"
            );

            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW
                )
            );
            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW.plusSeconds(1)
                )
            );

            UUID downstreamAssetId = jdbcTemplate.queryForObject(
                """
                select id
                  from catalog_dataset
                 where source_id = ?
                   and lower(btrim(hive_database)) = 'finance'
                   and lower(btrim(hive_table)) = lower(?)
                """,
                UUID.class,
                SOURCE_ID,
                tableName
            );
            assertThat(downstreamAssetId).isNotNull();
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_column_schema column_schema
                      join catalog_table_schema table_schema
                        on table_schema.id = column_schema.table_id
                     where table_schema.dataset_id = ?
                    """,
                    Integer.class,
                    downstreamAssetId
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForMap(
                    """
                    select upstream_column, downstream_column, confidence
                      from catalog_column_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and relation_type = 'DBT'
                       and valid_to is null
                    """,
                    upstreamAssetId,
                    downstreamAssetId
                )
            )
                .containsEntry("upstream_column", "payment_id")
                .containsEntry("downstream_column", "payment_id")
                .containsEntry("confidence", "PARSED");
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_lineage_edge
                     where from_model_spec_id = ?
                       and to_model_spec_id = ?
                       and edge_type = 'MODEL_DEPENDENCY'
                    """,
                    Integer.class,
                    scope.secondProdModelId(),
                    scope.prodModelId()
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_dataset_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and relation_type = 'MODEL_DEPENDENCY'
                    """,
                    Integer.class,
                    upstreamAssetId,
                    downstreamAssetId
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_dataset_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and relation_type = 'MODEL_DEPENDENCY'
                       and valid_to is null
                    """,
                    Integer.class,
                    upstreamAssetId,
                    downstreamAssetId
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select physical_asset_ref
                      from modeling_dbt_artifact
                     where model_spec_id = ?
                       and ownership = 'DBT_MANAGED'
                    """,
                    UUID.class,
                    scope.prodModelId()
                )
            ).isEqualTo(downstreamAssetId);
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void dbtManagedDwdPublicationProjectsConfirmedOdsTableAndTransitiveColumnLineage() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        UUID sourceDatasetId = UUID.randomUUID();
        UUID sourceTableId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        String sourceVersion = "source-v1";
        String tableName =
            "fct_ods_lineage_" +
            scope.prodModelId().toString().replace("-", "").substring(0, 12);
        String rootUniqueId = dbtUniqueId(scope.prodModelId());
        String stgUniqueId =
            "model.finance.stg_budget_" +
            scope.prodModelId().toString().replace("-", "");
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedCompiledArtifact(scope);
                jdbcTemplate.update(
                    """
                    update modeling_dbt_artifact
                       set ownership = 'DBT_MANAGED',
                           content = ?,
                           content_checksum = ?
                     where model_spec_id = ?
                       and artifact_type = 'SQL'
                       and node_kind = 'MODEL'
                    """,
                    "select s.budget_no as budget_no, s.budget_amount as budget_amount from {{ ref('stg_budget') }} s",
                    "1".repeat(64),
                    scope.prodModelId()
                );
                jdbcTemplate.update(
                    """
                    insert into modeling_dbt_artifact (
                        id, model_spec_id, project_key, dbt_unique_id,
                        artifact_type, artifact_key, path, content_checksum,
                        content, status, revision, plan_id, model_checksum,
                        ownership, implementation_revision, node_kind,
                        materialization, created_date, last_modified_date
                    ) values (
                        ?, ?, 'finance', ?, 'DEPENDENCY', ?, ?, ?, ?,
                        'COMPILED', 1, ?, ?, 'DBT_MANAGED', 1, 'MODEL',
                        'table', ?, ?
                    ), (
                        ?, ?, 'finance', ?, 'SQL', ?, ?, ?, ?,
                        'COMPILED', 1, ?, ?, 'DBT_MANAGED', 1, 'STG',
                        'view', ?, ?
                    ), (
                        ?, ?, 'finance', ?, 'DEPENDENCY', ?, ?, ?, ?,
                        'COMPILED', 1, ?, ?, 'DBT_MANAGED', 1, 'STG',
                        'view', ?, ?
                    )
                    """,
                    UUID.randomUUID(),
                    scope.prodModelId(),
                    rootUniqueId,
                    "DEPENDENCY:" + rootUniqueId,
                    "models/fct_payment.sql#dependency",
                    "2".repeat(64),
                    "[\"" + stgUniqueId + "\"]",
                    scope.planId(),
                    "a".repeat(64),
                    Timestamp.from(NOW.minusSeconds(20)),
                    Timestamp.from(NOW.minusSeconds(20)),
                    UUID.randomUUID(),
                    scope.prodModelId(),
                    stgUniqueId,
                    "SQL:" + stgUniqueId,
                    "models/stg/stg_budget.sql",
                    "3".repeat(64),
                    "select o.budget_no as budget_no, o.budget_amount_adjusted as budget_amount from {{ source('ods', 'budget') }} o",
                    scope.planId(),
                    "a".repeat(64),
                    Timestamp.from(NOW.minusSeconds(20)),
                    Timestamp.from(NOW.minusSeconds(20)),
                    UUID.randomUUID(),
                    scope.prodModelId(),
                    stgUniqueId,
                    "DEPENDENCY:" + stgUniqueId,
                    "models/stg/stg_budget.sql#dependency",
                    "4".repeat(64),
                    "[\"source.finance.ods.budget\"]",
                    scope.planId(),
                    "a".repeat(64),
                    Timestamp.from(NOW.minusSeconds(20)),
                    Timestamp.from(NOW.minusSeconds(20))
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_dataset (
                        id, name, type, source_id, classification,
                        hive_database, hive_table, warehouse_layer,
                        enabled, lifecycle_status, created_date,
                        last_modified_date
                    ) values (
                        ?, 'ODS budget', 'POSTGRES', ?, 'L2',
                        'public', 'ods_budget', 'ODS', true, 'ACTIVE', ?, ?
                    )
                    """,
                    sourceDatasetId,
                    SOURCE_ID,
                    Timestamp.from(NOW.minusSeconds(40)),
                    Timestamp.from(NOW.minusSeconds(40))
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_table_schema (
                        id, dataset_id, name, classification,
                        created_by, created_date, last_modified_date
                    ) values (?, ?, 'ods_budget', 'L2', 'it', ?, ?)
                    """,
                    sourceTableId,
                    sourceDatasetId,
                    Timestamp.from(NOW.minusSeconds(40)),
                    Timestamp.from(NOW.minusSeconds(40))
                );
                jdbcTemplate.update(
                    """
                    insert into catalog_column_schema (
                        id, table_id, name, data_type, nullable, status,
                        created_by, created_date, last_modified_date
                    ) values
                        (?, ?, 'budget_no', 'varchar', false, 'ACTIVE', 'it', ?, ?),
                        (?, ?, 'budget_amount_adjusted', 'numeric', true, 'ACTIVE', 'it', ?, ?)
                    """,
                    UUID.randomUUID(),
                    sourceTableId,
                    Timestamp.from(NOW.minusSeconds(40)),
                    Timestamp.from(NOW.minusSeconds(40)),
                    UUID.randomUUID(),
                    sourceTableId,
                    Timestamp.from(NOW.minusSeconds(40)),
                    Timestamp.from(NOW.minusSeconds(40))
                );
                jdbcTemplate.update(
                    """
                    insert into modeling_warehouse_plan_source (
                        id, tenant_id, plan_id, source_type, source_id,
                        source_version, locator_json, confirmation_status,
                        created_date, last_modified_date
                    ) values (
                        ?, ?, ?, 'CATALOG_TABLE', ?, ?,
                        jsonb_build_object('assetId', cast(? as text)),
                        'CONFIRMED', ?, ?
                    )
                    """,
                    sourceBindingId,
                    scope.tenantId(),
                    scope.planId(),
                    sourceTableId.toString(),
                    sourceVersion,
                    sourceTableId,
                    Timestamp.from(NOW.minusSeconds(30)),
                    Timestamp.from(NOW.minusSeconds(30))
                );
            });

            ModelSpecView model = publicationModel(scope);
            org.mockito.Mockito.when(model.fields())
                .thenReturn(
                    List.of(
                        new ModelField(
                            "budget_no",
                            "Budget number",
                            "varchar",
                            false,
                            null,
                            FieldRole.KEY,
                            "L2",
                            null,
                            false,
                            null
                        ),
                        new ModelField(
                            "budget_amount",
                            "Budget amount",
                            "decimal",
                            true,
                            null,
                            FieldRole.MEASURE,
                            "L2",
                            null,
                            false,
                            null
                        )
                    )
                );
            org.mockito.Mockito.when(model.sourceRefs())
                .thenReturn(
                    List.of(
                        new SourceRef(
                            SourceKind.TABLE,
                            sourceTableId.toString(),
                            Layer.ODS,
                            SourceRole.PRIMARY,
                            "src",
                            null,
                            null,
                            0,
                            sourceBindingId,
                            sourceVersion
                        )
                    )
                );
            PublicationEntryEvidence observation = new PublicationEntryEvidence(
                scope.entryId(),
                scope.prodModelId(),
                1,
                "a".repeat(64),
                1,
                "b".repeat(64),
                rootUniqueId,
                tableName,
                "c".repeat(64),
                "d".repeat(64),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "postgres",
                "warehouse",
                "finance",
                tableName,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(1, "budget_no", "varchar", false),
                    new PhysicalColumn(2, "budget_amount", "numeric", true)
                ),
                "f".repeat(64),
                NOW.minusSeconds(10)
            );
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());
            CandidateView candidate = candidate(scope);
            ResolvedCatalogTarget target = new ResolvedCatalogTarget(
                TARGET_KEY,
                SOURCE_ID,
                "postgres"
            );

            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW
                )
            );
            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW.plusSeconds(1)
                )
            );

            UUID downstreamAssetId = jdbcTemplate.queryForObject(
                "select physical_asset_ref from modeling_dbt_artifact where model_spec_id = ? and node_kind = 'MODEL' and artifact_type = 'SQL'",
                UUID.class,
                scope.prodModelId()
            );
            UUID tableLineageId = jdbcTemplate.queryForObject(
                """
                select id
                  from catalog_dataset_lineage
                 where upstream_dataset_id = ?
                   and downstream_dataset_id = ?
                   and relation_type = 'MODEL_DEPENDENCY'
                   and verification_status = 'VERIFIED'
                   and valid_to is null
                """,
                UUID.class,
                sourceDatasetId,
                downstreamAssetId
            );
            assertThat(tableLineageId).isNotNull();
            assertThat(
                jdbcTemplate.queryForList(
                    """
                    select upstream_column, downstream_column,
                           confidence, dataset_lineage_id
                      from catalog_column_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and valid_to is null
                     order by downstream_column
                    """,
                    sourceDatasetId,
                    downstreamAssetId
                )
            )
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.get("confidence")).isEqualTo("PARSED");
                    assertThat(row.get("dataset_lineage_id")).isEqualTo(tableLineageId);
                })
                .extracting(
                    row -> row.get("upstream_column") + "->" + row.get("downstream_column")
                )
                .containsExactly(
                    "budget_amount_adjusted->budget_amount",
                    "budget_no->budget_no"
                );

            LifecycleEventView rollback = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(rollback.id()).thenReturn(UUID.randomUUID());
            transaction.executeWithoutResult(status ->
                publications.rollbackModel(
                    publishedCandidate(scope),
                    observation,
                    model,
                    rollback,
                    "release-operator",
                    NOW.plusSeconds(2)
                )
            );
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from catalog_dataset_lineage where downstream_dataset_id = ? and valid_to is null",
                    Integer.class,
                    downstreamAssetId
                )
            ).isZero();
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from catalog_column_lineage where downstream_dataset_id = ? and valid_to is null",
                    Integer.class,
                    downstreamAssetId
                )
            ).isZero();
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_dataset_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and valid_from < valid_to
                    """,
                    Integer.class,
                    sourceDatasetId,
                    downstreamAssetId
                )
            ).isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from catalog_column_lineage
                     where upstream_dataset_id = ?
                       and downstream_dataset_id = ?
                       and valid_from < valid_to
                    """,
                    Integer.class,
                    sourceDatasetId,
                    downstreamAssetId
                )
            ).isEqualTo(2);
        } finally {
            transaction.executeWithoutResult(status -> {
                jdbcTemplate.update(
                    "delete from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?",
                    scope.tenantId(),
                    scope.planId()
                );
                cleanup(scope);
            });
        }
    }

    @Test
    void rollbackArchivesTheExactCandidateAssetAndDetachesItsArtifact() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        String tableName =
            "fct_rollback_" +
            scope.prodModelId().toString().replace("-", "").substring(0, 12);
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                seedPublishedModel(
                    scope,
                    scope.prodModelId(),
                    scope.prodReleaseId(),
                    "PROD",
                    true
                );
                seedCompiledArtifact(scope);
            });
            CandidateView publishing = candidate(scope);
            PublicationEntryEvidence observation =
                publicationObservation(scope, tableName);
            ModelSpecView model = publicationModel(scope);
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(scope.prodReleaseId());
            transaction.executeWithoutResult(status ->
                publications.registerModel(
                    publishing,
                    new ResolvedCatalogTarget(
                        TARGET_KEY,
                        SOURCE_ID,
                        "postgres"
                    ),
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW
                )
            );
            UUID assetId = jdbcTemplate.queryForObject(
                """
                select physical_asset_ref
                  from modeling_dbt_artifact
                 where model_spec_id = ?
                """,
                UUID.class,
                scope.prodModelId()
            );
            Long versionBeforeRollback = jdbcTemplate.queryForObject(
                "select version from catalog_dataset where id = ?", Long.class, assetId
            );
            LifecycleEventView rollback = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            UUID rollbackId = UUID.randomUUID();
            org.mockito.Mockito.when(rollback.id()).thenReturn(rollbackId);

            transaction.executeWithoutResult(status ->
                publications.rollbackModel(
                    publishedCandidate(scope),
                    observation,
                    model,
                    rollback,
                    "release-operator",
                    NOW.plusSeconds(1)
                )
            );

            assertThat(jdbcTemplate.queryForObject(
                "select version from catalog_dataset where id = ?", Long.class, assetId
            )).isEqualTo(versionBeforeRollback + 1);
            assertThat(
                jdbcTemplate.queryForMap(
                    """
                    select enabled, lifecycle_status
                      from catalog_dataset
                     where id = ?
                    """,
                    assetId
                )
            )
                .containsEntry("enabled", false)
                .containsEntry("lifecycle_status", "ARCHIVED");
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select physical_asset_ref is null
                      from modeling_dbt_artifact
                     where model_spec_id = ?
                    """,
                    Boolean.class,
                    scope.prodModelId()
                )
            ).isTrue();
            assertThat(
                jdbcTemplate.queryForMap(
                    """
                    select status,
                           details_json ->> 'rollbackEventId' as rollback_event_id
                      from modeling_model_lifecycle_event
                     where id = ?
                    """,
                    scope.prodReleaseId()
                )
            )
                .containsEntry("status", "ROLLED_BACK")
                .containsEntry("rollback_event_id", rollbackId.toString());
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    @Test
    void oneHundredEntryPublicationIsInvisibleAfterFailureAndVisibleAfterOneRetry() {
        TestScope scope = TestScope.create();
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        List<BatchItem> items = batchItems(scope, 100);
        String eventId =
            "model-release-candidate-published:" +
            scope.candidateId() +
            ":v10";
        try {
            transaction.executeWithoutResult(status -> {
                seedPlanAndCandidate(scope);
                for (BatchItem item : items) {
                    seedPublishedModel(
                        scope,
                        item.modelId(),
                        item.releaseId(),
                        "PROD",
                        true
                    );
                    seedCompiledArtifact(
                        scope,
                        item.modelId(),
                        item.tableName()
                    );
                    seedCandidateEntry(scope, item);
                }
            });
            CandidateView candidate = batchCandidate(scope, items);

            assertBatchVisibility(scope, eventId, 0, "PUBLISHING");

            assertThatThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    publishBatch(candidate, scope, items, eventId);
                    transitionBatchRows(
                        scope,
                        "PUBLISHING",
                        "PUBLISHED",
                        11
                    );
                    throw new IllegalStateException(
                        "inject-after-100-entry-commit"
                    );
                })
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inject-after-100-entry-commit");

            assertBatchVisibility(scope, eventId, 0, "PUBLISHING");

            transaction.executeWithoutResult(status -> {
                transitionBatchRows(
                    scope,
                    "PUBLISHING",
                    "PARTIAL",
                    11
                );
            });
            assertBatchVisibility(scope, eventId, 0, "PARTIAL");

            CandidateView partial = batchCandidate(
                scope,
                items,
                DeliveryStatus.PARTIAL,
                11
            );
            transaction.executeWithoutResult(status -> {
                publishBatch(partial, scope, items, eventId);
                transitionBatchRows(
                    scope,
                    "PARTIAL",
                    "PUBLISHED",
                    12
                );
            });

            assertBatchVisibility(scope, eventId, 100, "PUBLISHED");
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    private void seedPlanAndCandidate(TestScope scope) {
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode,
                lifecycle_status, status, version, created_date, last_modified_date
            ) values (?, ?, 'release-operator', ?, 'Sprint 76 publication IT',
                      'release-operator', 'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1,
                      current_timestamp, current_timestamp)
            """,
            scope.planId(),
            scope.tenantId(),
            "s76_pub_" + scope.planId().toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into catalog_domain (id, name, code, lifecycle_status, access_policy)
            values (?, 'Sprint 76 publication domain', ?, 'ACTIVE', 'PUBLIC')
            """,
            scope.domainId(),
            "S76_PUB_" + scope.domainId().toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate (
                id, tenant_id, plan_id, environment, status, version,
                idempotency_key, request_hash, created_by, created_date,
                submitted_by, submitted_date, approved_by, approved_date,
                last_modified_by, last_modified_date, origin,
                execution_target_key, adapter, profile_key, target_name
            ) values (
                ?, ?, ?, 'PROD', 'PUBLISHING', 10, ?, ?,
                'creator', ?, 'submitter', ?, 'approver', ?,
                'release-operator', ?, 'BATCH_WORKBENCH',
                ?, 'postgres', 'warehouse', 'prod'
            )
            """,
            scope.candidateId(),
            scope.tenantId(),
            scope.planId(),
            "candidate-" + scope.candidateId(),
            "e".repeat(64),
            Timestamp.from(NOW.minusSeconds(180)),
            Timestamp.from(NOW.minusSeconds(120)),
            Timestamp.from(NOW.minusSeconds(60)),
            Timestamp.from(NOW),
            TARGET_KEY
        );
        jdbcTemplate.update(
            """
            insert into modeling_materialization_dispatch (
                id, tenant_id, candidate_id, candidate_version, attempt,
                execution_target_key, airflow_dag_id, airflow_run_id,
                artifact_bundle_checksum, status, dispatch_attempts,
                recovered, created_at, last_modified_at
            ) values (?, ?, ?, 10, 1, ?, 'dts_model_release_build',
                      'airflow-run-1', ?, 'COMPLETED', 1, false, ?, ?)
            """,
            scope.dispatchId(),
            scope.tenantId(),
            scope.candidateId(),
            TARGET_KEY,
            "f".repeat(64),
            Timestamp.from(NOW.minusSeconds(30)),
            Timestamp.from(NOW)
        );
    }

    private void seedPublishedModel(
        TestScope scope,
        UUID modelId,
        UUID releaseId,
        String environment,
        boolean completeFacts
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, plan_id, layer, warehouse_layer_code, model_type,
                implementation_mode, name, status, revision, version, created_date,
                last_modified_date, contract_version, domain_id, current_checksum,
                idempotency_key, idempotency_request_hash, idempotency_response_snapshot
            ) values (
                ?, ?, ?, 'DWD', 'DWD', 'FACT', 'DESIGNER_GENERATED',
                ?, 'PUBLISHED', 1, 1, current_timestamp, current_timestamp,
                2, ?, ?, ?, ?, cast('{}' as jsonb)
            )
            """,
            modelId,
            scope.tenantId(),
            scope.planId(),
            "Sprint 76 " + environment + " " + modelId,
            scope.domainId(),
            "a".repeat(64),
            "model-" + modelId,
            "b".repeat(64)
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, status,
                content_checksum, created_date, last_modified_date,
                tenant_id, contract_version, snapshot_json, created_by
            ) values (
                ?, ?, 1, 'PUBLISHED',
                ?, current_timestamp, current_timestamp,
                ?, 2, cast('{}' as jsonb), 'release-operator'
            )
            """,
            UUID.randomUUID(),
            modelId,
            "a".repeat(64),
            scope.tenantId()
        );
        String details = completeFacts
            ? """
              {"executionTargetKey":"%s","environment":"%s","dbtUniqueId":"%s",
               "targetIdentifier":"%s","artifactChecksum":"%s",
               "dependencySnapshotChecksum":"%s"}
              """.formatted(
                TARGET_KEY,
                environment,
                dbtUniqueId(modelId),
                targetIdentifier(modelId),
                "c".repeat(64),
                "d".repeat(64)
            )
            : "{}";
        jdbcTemplate.update(
            """
            insert into modeling_model_lifecycle_event (
                id, tenant_id, model_spec_id, plan_id, model_revision,
                model_checksum, event_type, status, idempotency_key,
                actor_id, details_json, created_date
            ) values (?, ?, ?, ?, 1, ?, 'RELEASE', 'PUBLISHED', ?,
                      'release-operator', cast(? as jsonb), ?)
            """,
            releaseId,
            scope.tenantId(),
            modelId,
            scope.planId(),
            "a".repeat(64),
            "release-" + modelId,
            details,
            Timestamp.from(NOW.minusSeconds(10))
        );
    }

    private CandidateView candidate(TestScope scope) {
        return new CandidateView(
            scope.candidateId(),
            scope.tenantId(),
            scope.planId(),
            "PROD",
            DeliveryStatus.PUBLISHING,
            10,
            "candidate-" + scope.candidateId(),
            "e".repeat(64),
            new DeliveryAuditView(
                "creator",
                NOW.minusSeconds(180),
                "submitter",
                NOW.minusSeconds(120),
                "approver",
                NOW.minusSeconds(60),
                null,
                null
            ),
            "release-operator",
            NOW,
            List.of(
                new EntryView(
                    scope.entryId(),
                    scope.tenantId(),
                    scope.candidateId(),
                    scope.planId(),
                    scope.prodModelId(),
                    1,
                    "a".repeat(64),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    DeliveryStatus.PUBLISHING,
                    0,
                    "publication IT"
                )
            ),
            CandidateOrigin.BATCH_WORKBENCH,
            TARGET_KEY,
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private CandidateView publishedCandidate(TestScope scope) {
        return new CandidateView(
            scope.candidateId(),
            scope.tenantId(),
            scope.planId(),
            "PROD",
            DeliveryStatus.PUBLISHED,
            11,
            "candidate-" + scope.candidateId(),
            "e".repeat(64),
            new DeliveryAuditView(
                "creator",
                NOW.minusSeconds(180),
                "submitter",
                NOW.minusSeconds(120),
                "approver",
                NOW.minusSeconds(60),
                "release-operator",
                NOW
            ),
            "release-operator",
            NOW,
            List.of(
                new EntryView(
                    scope.entryId(),
                    scope.tenantId(),
                    scope.candidateId(),
                    scope.planId(),
                    scope.prodModelId(),
                    1,
                    "a".repeat(64),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    DeliveryStatus.PUBLISHED,
                    0,
                    "publication IT"
                )
            ),
            CandidateOrigin.BATCH_WORKBENCH,
            TARGET_KEY,
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private CandidateView batchCandidate(
        TestScope scope,
        List<BatchItem> items
    ) {
        return batchCandidate(
            scope,
            items,
            DeliveryStatus.PUBLISHING,
            10
        );
    }

    private CandidateView batchCandidate(
        TestScope scope,
        List<BatchItem> items,
        DeliveryStatus status,
        int version
    ) {
        List<EntryView> entries = items
            .stream()
            .map(item ->
                new EntryView(
                    item.entryId(),
                    scope.tenantId(),
                    scope.candidateId(),
                    scope.planId(),
                    item.modelId(),
                    1,
                    "a".repeat(64),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    status,
                    item.sortOrder(),
                    "100-entry atomicity IT"
                )
            )
            .toList();
        return new CandidateView(
            scope.candidateId(),
            scope.tenantId(),
            scope.planId(),
            "PROD",
            status,
            version,
            "candidate-" + scope.candidateId(),
            "e".repeat(64),
            new DeliveryAuditView(
                "creator",
                NOW.minusSeconds(180),
                "submitter",
                NOW.minusSeconds(120),
                "approver",
                NOW.minusSeconds(60),
                status == DeliveryStatus.PUBLISHING
                    ? null
                    : "release-operator",
                status == DeliveryStatus.PUBLISHING
                    ? null
                    : NOW
            ),
            "release-operator",
            NOW,
            entries,
            CandidateOrigin.BATCH_WORKBENCH,
            TARGET_KEY,
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private PublicationEntryEvidence publicationObservation(
        TestScope scope,
        String tableName
    ) {
        return new PublicationEntryEvidence(
            scope.entryId(),
            scope.prodModelId(),
            1,
            "a".repeat(64),
            1,
            "b".repeat(64),
            dbtUniqueId(scope.prodModelId()),
            tableName,
            "c".repeat(64),
            "d".repeat(64),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "postgres",
            "warehouse",
            "finance",
            tableName,
            ExpectedRelationType.TABLE,
            List.of(
                new PhysicalColumn(
                    1,
                    "payment_id",
                    "bigint",
                    false
                )
            ),
            "f".repeat(64),
            NOW.minusSeconds(10)
        );
    }

    private ModelSpecView publicationModel(TestScope scope) {
        return publicationModel(scope, scope.prodModelId());
    }

    private ModelSpecView publicationModel(
        TestScope scope,
        UUID modelId
    ) {
        ModelSpecView model = org.mockito.Mockito.mock(
            ModelSpecView.class
        );
        org.mockito.Mockito.when(model.id())
            .thenReturn(modelId);
        org.mockito.Mockito.when(model.revision()).thenReturn(1);
        org.mockito.Mockito.when(model.checksum())
            .thenReturn("a".repeat(64));
        org.mockito.Mockito.when(model.name())
            .thenReturn("Finance payment fact");
        org.mockito.Mockito.when(model.description())
            .thenReturn("Observed payment output");
        org.mockito.Mockito.when(model.domainId())
            .thenReturn(scope.domainId());
        org.mockito.Mockito.when(model.layer()).thenReturn(Layer.DWD);
        org.mockito.Mockito.when(model.dependsOn())
            .thenReturn(List.of());
        org.mockito.Mockito.when(model.fields())
            .thenReturn(
                List.of(
                    new ModelField(
                        "payment_id",
                        "Payment ID",
                        "integer",
                        false,
                        null,
                        FieldRole.KEY,
                        "L2",
                        null,
                        false,
                        null
                    )
                )
            );
        return model;
    }

    private void seedCompiledArtifact(TestScope scope) {
        seedCompiledArtifact(
            scope,
            scope.prodModelId(),
            "fct_payment"
        );
    }

    private void seedCompiledArtifact(
        TestScope scope,
        UUID modelId,
        String tableName
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_dbt_artifact (
                id, model_spec_id, project_key, dbt_unique_id,
                artifact_type, path, content_checksum, content,
                status, revision, created_date, last_modified_date,
                plan_id, model_checksum, ownership,
                implementation_revision, node_kind, materialization,
                artifact_key
            ) values (
                ?, ?, 'finance', ?, 'SQL', 'models/fct_payment.sql',
                ?, 'select 1', 'COMPILED', 1, ?, ?, ?, ?,
                'DESIGNER_GENERATED', 1, 'MODEL', 'table',
                'SQL:models/fct_payment.sql'
            )
            """,
            UUID.randomUUID(),
            modelId,
            dbtUniqueId(modelId),
            "e".repeat(64),
            Timestamp.from(NOW.minusSeconds(20)),
            Timestamp.from(NOW.minusSeconds(20)),
            scope.planId(),
            "a".repeat(64)
        );
    }

    private void seedCandidateEntry(TestScope scope, BatchItem item) {
        jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate_entry (
                id, tenant_id, candidate_id, plan_id, model_spec_id,
                revision, checksum, implementation_mode, status, sort_order
            ) values (
                ?, ?, ?, ?, ?, 1, ?, 'DESIGNER_GENERATED',
                'PUBLISHING', ?
            )
            """,
            item.entryId(),
            scope.tenantId(),
            scope.candidateId(),
            scope.planId(),
            item.modelId(),
            "a".repeat(64),
            item.sortOrder()
        );
    }

    private void publishBatch(
        CandidateView candidate,
        TestScope scope,
        List<BatchItem> items,
        String eventId
    ) {
        ResolvedCatalogTarget target = new ResolvedCatalogTarget(
            TARGET_KEY,
            SOURCE_ID,
            "postgres"
        );
        List<PublishedModelBinding> bindings = new ArrayList<>(items.size());
        for (BatchItem item : items) {
            PublicationEntryEvidence observation =
                new PublicationEntryEvidence(
                    item.entryId(),
                    item.modelId(),
                    1,
                    "a".repeat(64),
                    1,
                    "b".repeat(64),
                    dbtUniqueId(item.modelId()),
                    item.tableName(),
                    "c".repeat(64),
                    "d".repeat(64),
                    UUID.nameUUIDFromBytes(
                        ("run-group:" + item.modelId()).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    UUID.nameUUIDFromBytes(
                        ("run:" + item.modelId()).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    UUID.nameUUIDFromBytes(
                        ("invocation:" + item.modelId()).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    "postgres",
                    "warehouse",
                    "finance",
                    item.tableName(),
                    ExpectedRelationType.TABLE,
                    List.of(
                        new PhysicalColumn(
                            1,
                            "business_key",
                            "bigint",
                            false
                        )
                    ),
                    "f".repeat(64),
                    NOW.minusSeconds(10)
                );
            ModelSpecView model = publicationModel(
                scope,
                item.modelId()
            );
            LifecycleEventView release = org.mockito.Mockito.mock(
                LifecycleEventView.class
            );
            org.mockito.Mockito.when(release.id())
                .thenReturn(item.releaseId());
            bindings.add(
                publications.registerModel(
                    candidate,
                    target,
                    observation,
                    model,
                    release,
                    "release-operator",
                    NOW
                )
            );
        }
        publications.rebuildManualBinding(
            candidate,
            List.copyOf(bindings),
            "release-operator",
            NOW
        );
        outbox.publishInternal(
            new PlatformEventRequest(
                eventId,
                "MODEL_RELEASE_CANDIDATE_PUBLISHED",
                "modeling",
                "dts-platform",
                "MODEL_RELEASE_CANDIDATE",
                scope.candidateId().toString(),
                scope.planId().toString(),
                "PUBLISH",
                "INFO",
                "SUCCESS",
                NOW,
                "release-operator",
                "100-entry-publication",
                null,
                "MODEL_RELEASE_CANDIDATE_PUBLISH",
                "SPRINT_36_F3_ASSET_ACTION",
                Map.of(
                    "tenantId",
                    scope.tenantId(),
                    "planId",
                    scope.planId(),
                    "entryCount",
                    items.size()
                )
            )
        );
    }

    private void transitionBatchRows(
        TestScope scope,
        String expectedStatus,
        String targetStatus,
        int version
    ) {
        assertThat(
            jdbcTemplate.update(
                """
                update modeling_model_release_candidate
                   set status = ?, version = ?,
                       published_by = 'release-operator',
                       published_date = ?,
                       last_modified_by = 'release-operator',
                       last_modified_date = ?
                 where tenant_id = ?
                   and id = ?
                   and status = ?
                """,
                targetStatus,
                version,
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                scope.tenantId(),
                scope.candidateId(),
                expectedStatus
            )
        ).isEqualTo(1);
        assertThat(
            jdbcTemplate.update(
                """
                update modeling_model_release_candidate_entry
                   set status = ?
                 where tenant_id = ?
                   and candidate_id = ?
                   and status = ?
                """,
                targetStatus,
                scope.tenantId(),
                scope.candidateId(),
                expectedStatus
            )
        ).isEqualTo(100);
    }

    private void assertBatchVisibility(
        TestScope scope,
        String eventId,
        int visibleCount,
        String candidateStatus
    ) {
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from catalog_dataset
                 where source_id = ?
                   and cast(tags as jsonb) ->> 'tenantId' = ?
                """,
                Integer.class,
                SOURCE_ID,
                scope.tenantId()
            )
        ).isEqualTo(visibleCount);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_dbt_artifact
                 where plan_id = ?
                   and physical_asset_ref is not null
                """,
                Integer.class,
                scope.planId()
            )
        ).isEqualTo(visibleCount);
        assertThat(bindingCount(scope))
            .isEqualTo(visibleCount == 0 ? 0 : 1);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_plan_execution_binding_entry entry
                  join modeling_plan_execution_binding binding
                    on binding.id = entry.binding_id
                 where binding.tenant_id = ?
                   and binding.plan_id = ?
                   and binding.environment = 'PROD'
                   and binding.execution_target_key = ?
                """,
                Integer.class,
                scope.tenantId(),
                scope.planId(),
                TARGET_KEY
            )
        ).isEqualTo(visibleCount);
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from platform_event_outbox where event_id = ?",
                Integer.class,
                eventId
            )
        ).isEqualTo(visibleCount == 0 ? 0 : 1);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                 from modeling_model_lifecycle_event
                 where tenant_id = ?
                   and plan_id = ?
                   and jsonb_exists(details_json, 'physicalAssetId')
                """,
                Integer.class,
                scope.tenantId(),
                scope.planId()
            )
        ).isEqualTo(visibleCount);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select status
                  from modeling_model_release_candidate
                 where tenant_id = ?
                   and id = ?
                """,
                String.class,
                scope.tenantId(),
                scope.candidateId()
            )
        ).isEqualTo(candidateStatus);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_model_release_candidate_entry
                 where tenant_id = ?
                   and candidate_id = ?
                   and status = ?
                """,
                Integer.class,
                scope.tenantId(),
                scope.candidateId(),
                candidateStatus
            )
        ).isEqualTo(100);
    }

    private static List<BatchItem> batchItems(
        TestScope scope,
        int count
    ) {
        List<BatchItem> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String identity = scope.candidateId() + ":" + index;
            items.add(
                new BatchItem(
                    UUID.nameUUIDFromBytes(
                        ("entry:" + identity).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    UUID.nameUUIDFromBytes(
                        ("model:" + identity).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    UUID.nameUUIDFromBytes(
                        ("release:" + identity).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    ),
                    "fct_batch_" +
                    scope.candidateId()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8) +
                    "_" +
                    index,
                    index
                )
            );
        }
        return List.copyOf(items);
    }


    private int bindingCount(TestScope scope) {
        return jdbcTemplate.queryForObject(
            """
            select count(*) from modeling_plan_execution_binding
             where tenant_id = ? and plan_id = ? and environment = 'PROD'
               and execution_target_key = ?
            """,
            Integer.class,
            scope.tenantId(),
            scope.planId(),
            TARGET_KEY
        );
    }

    private String bindingDagId(TestScope scope) {
        return bindingValue(scope, "dag_id");
    }

    private String bindingDeploymentChecksum(TestScope scope) {
        return bindingValue(scope, "desired_deployment_checksum");
    }

    private String bindingDeploymentStatus(TestScope scope) {
        return bindingValue(scope, "deployment_status");
    }

    private int bindingVersion(TestScope scope) {
        return jdbcTemplate.queryForObject(
            """
            select version
              from modeling_plan_execution_binding
             where tenant_id = ? and plan_id = ? and environment = 'PROD'
               and execution_target_key = ?
            """,
            Integer.class,
            scope.tenantId(),
            scope.planId(),
            TARGET_KEY
        );
    }

    private String bindingValue(TestScope scope, String column) {
        return jdbcTemplate.queryForObject(
            """
            select %s
              from modeling_plan_execution_binding
             where tenant_id = ? and plan_id = ? and environment = 'PROD'
               and execution_target_key = ?
            """.formatted(column),
            String.class,
            scope.tenantId(),
            scope.planId(),
            TARGET_KEY
        );
    }

    private List<UUID> bindingModelIds(TestScope scope) {
        return jdbcTemplate.queryForList(
            """
            select entry.model_spec_id
              from modeling_plan_execution_binding_entry entry
              join modeling_plan_execution_binding binding on binding.id = entry.binding_id
             where binding.tenant_id = ? and binding.plan_id = ?
               and binding.environment = 'PROD'
               and binding.execution_target_key = ?
             order by entry.model_spec_id
            """,
            UUID.class,
            scope.tenantId(),
            scope.planId(),
            TARGET_KEY
        );
    }

    private Integer catalogDatasetCount(String tableName) {
        return jdbcTemplate.queryForObject(
            """
            select count(*)
              from catalog_dataset
             where source_id = ?
               and lower(btrim(hive_database)) = 'finance'
               and lower(btrim(hive_table)) = lower(btrim(?))
            """,
            Integer.class,
            SOURCE_ID,
            tableName
        );
    }

    private String catalogDatasetValue(UUID assetId, String column) {
        if (
            !List.of(
                "classification",
                "warehouse_layer",
                "lifecycle_status"
            ).contains(column)
        ) {
            throw new IllegalArgumentException("Unsupported Catalog column");
        }
        return jdbcTemplate.queryForObject(
            "select " + column + " from catalog_dataset where id = ?",
            String.class,
            assetId
        );
    }

    private void cleanup(TestScope scope) {
        jdbcTemplate.update(
            """
            delete from catalog_column_lineage
             where upstream_dataset_id in (
                       select id from catalog_dataset where source_id = ?
                   )
                or downstream_dataset_id in (
                       select id from catalog_dataset where source_id = ?
                   )
            """,
            SOURCE_ID,
            SOURCE_ID
        );
        jdbcTemplate.update(
            """
            delete from catalog_dataset_lineage
             where upstream_dataset_id in (
                       select id from catalog_dataset where source_id = ?
                   )
                or downstream_dataset_id in (
                       select id from catalog_dataset where source_id = ?
                   )
            """,
            SOURCE_ID,
            SOURCE_ID
        );
        jdbcTemplate.update(
            """
            delete from modeling_lineage_edge
             where from_model_spec_id in (?, ?, ?)
                or to_model_spec_id in (?, ?, ?)
            """,
            scope.prodModelId(),
            scope.secondProdModelId(),
            scope.testModelId(),
            scope.prodModelId(),
            scope.secondProdModelId(),
            scope.testModelId()
        );
        jdbcTemplate.update(
            """
            delete from catalog_column_schema
             where table_id in (
                 select table_schema.id
                  from catalog_table_schema table_schema
                  join catalog_dataset dataset
                     on dataset.id = table_schema.dataset_id
                  where cast(dataset.tags as jsonb) ->> 'tenantId' = ?
                     or dataset.source_id = ?
             )
            """,
            scope.tenantId(),
            SOURCE_ID
        );
        jdbcTemplate.update(
            """
            delete from catalog_table_schema
             where dataset_id in (
                 select id
                   from catalog_dataset
                  where cast(tags as jsonb) ->> 'tenantId' = ?
                     or source_id = ?
             )
            """,
            scope.tenantId(),
            SOURCE_ID
        );
        jdbcTemplate.update(
            """
            delete from catalog_dataset
             where cast(tags as jsonb) ->> 'tenantId' = ?
                or source_id = ?
            """,
            scope.tenantId(),
            SOURCE_ID
        );
        jdbcTemplate.update(
            """
            delete from modeling_plan_execution_binding_entry
             where binding_id in (
                 select id from modeling_plan_execution_binding
                  where tenant_id = ? and plan_id = ?
             )
            """,
            scope.tenantId(),
            scope.planId()
        );
        jdbcTemplate.update(
            "delete from modeling_plan_execution_binding where tenant_id = ? and plan_id = ?",
            scope.tenantId(),
            scope.planId()
        );
        jdbcTemplate.update(
            "delete from modeling_materialization_dispatch where id = ?",
            scope.dispatchId()
        );
        jdbcTemplate.update(
            """
            delete from platform_event_outbox
             where aggregate_id = ?
            """,
            scope.candidateId().toString()
        );
        jdbcTemplate.update(
            """
            delete from modeling_model_release_candidate_entry
             where tenant_id = ? and candidate_id = ?
            """,
            scope.tenantId(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_release_candidate where tenant_id = ? and id = ?",
            scope.tenantId(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_lifecycle_event where tenant_id = ? and plan_id = ?",
            scope.tenantId(),
            scope.planId()
        );
        jdbcTemplate.update(
            "delete from modeling_dbt_artifact where plan_id = ?",
            scope.planId()
        );
        jdbcTemplate.update(
            """
            delete from modeling_model_spec_revision revision
             where revision.tenant_id = ?
               and revision.model_spec_id in (
                   select id
                     from modeling_model_spec
                    where tenant_id = ?
                      and plan_id = ?
               )
            """,
            scope.tenantId(),
            scope.tenantId(),
            scope.planId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_spec where tenant_id = ? and plan_id = ?",
            scope.tenantId(),
            scope.planId()
        );
        jdbcTemplate.update("delete from catalog_domain where id = ?", scope.domainId());
        jdbcTemplate.update(
            "delete from modeling_warehouse_plan where tenant_id = ? and id = ?",
            scope.tenantId(),
            scope.planId()
        );
    }

    private static String dbtUniqueId(UUID modelId) {
        return "model.finance.model_" + modelId.toString().replace("-", "");
    }

    private static String targetIdentifier(UUID modelId) {
        return "dwd_" + modelId.toString().replace("-", "").substring(0, 20);
    }

    private record BatchItem(
        UUID entryId,
        UUID modelId,
        UUID releaseId,
        String tableName,
        int sortOrder
    ) {}

    private record TestScope(
        String tenantId,
        UUID planId,
        UUID domainId,
        UUID candidateId,
        UUID dispatchId,
        UUID entryId,
        UUID prodModelId,
        UUID prodReleaseId,
        UUID secondProdModelId,
        UUID secondProdReleaseId,
        UUID testModelId,
        UUID testReleaseId
    ) {
        static TestScope create() {
            return new TestScope(
                "s76-publication-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
            );
        }
    }
}
