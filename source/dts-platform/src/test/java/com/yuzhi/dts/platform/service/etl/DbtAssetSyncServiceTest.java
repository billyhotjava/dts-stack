package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationJobService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.EvidenceChannel;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ProducerKind;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecReader;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbtAssetSyncServiceTest {

    @Mock
    private DbtConfigService configService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDatasetLineageRepository lineageRepository;

    @Mock
    private CatalogColumnLineageRepository columnLineageRepository;

    @Mock
    private CatalogLineageJobRepository lineageJobRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @Mock
    private CatalogColumnSyncService columnSyncService;

    @Mock
    private InfraOdsTableMappingRepository mappingRepository;

    @Mock
    private ModelLifecycleRepository lifecycleRepository;

    @Mock
    private ModelSpecReader modelSpecReader;

    @Mock
    private AuditService auditService;

    @Mock
    private CatalogClassificationPropagationJobService propagationJobService;

    @Mock
    private CatalogPhysicalDatasetObservationAdapter assetObservation;

    @Mock
    private CatalogSchemaDriftEventRepository schemaDriftEventRepository;

    @TempDir
    Path tempDir;

    @Test
    void syncKeepsEphemeralModelAsTechnicalEvidenceWithoutCreatingCatalogPhysicalAsset() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-ephemeral","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.ephemeral_orders":{"resource_type":"model","name":"ephemeral_orders","database":"warehouse","schema":"analytics","identifier":"ephemeral_orders","config":{"materialized":"ephemeral"},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-ephemeral","generated_at":"2026-07-24T08:00:00Z"},"results":[{"unique_id":"model.dts.ephemeral_orders","status":"success"}]}
            """
        );

        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        verify(datasetRepository, never()).save(any(CatalogDataset.class));
        verify(tableRepository, never()).save(any(CatalogTableSchema.class));
        verify(lineageJobRepository).save(any());
    }

    @Test
    void syncImportsLifecycleArtifactsWithoutCreatingCatalogPhysicalAsset() throws Exception {
        java.util.UUID targetSourceId =
            java.util.UUID.fromString(
                "90000000-0000-0000-0000-000000000001"
            );
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics","identifier":"orders_relation","original_file_path":"models/dwd/orders.sql","raw_code":"select order_id from source_orders","columns":{"order_id":{"name":"order_id","data_type":"bigint"}},"config":{"materialized":"table","meta":{"tenantId":"tenant-a","modelSpecId":"10000000-0000-0000-0000-000000000001","revision":7,"modelChecksum":"sha256:model-v7","implementationRevision":3,"implementationChecksum":"sha256:implementation-v3","projectKey":"dts","layer":"DWD"}},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:00:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        ModelLifecycleContract.ImplementationView implementation = currentImplementation();
        when(
            lifecycleRepository.findImplementation(
                "tenant-a",
                java.util.UUID.fromString("10000000-0000-0000-0000-000000000001")
            )
        ).thenReturn(Optional.of(implementation));
        ModelSpecContract.ModelSpecView modelSpec = org.mockito.Mockito.mock(ModelSpecContract.ModelSpecView.class);
        when(modelSpec.planId()).thenReturn(implementation.planId());
        when(modelSpec.revision()).thenReturn(implementation.revision());
        when(modelSpec.checksum()).thenReturn(implementation.modelChecksum());
        when(modelSpec.implementationMode()).thenReturn(ModelSpecContract.ImplementationMode.DBT_MANAGED);
        when(modelSpecReader.get("tenant-a", implementation.modelSpecId())).thenReturn(modelSpec);

        DbtAssetSyncService.DbtAssetSyncResult result = service(
            targetSourceId
        ).syncFromManifest(tempDir.toString());

        assertThat(result.stats().getCreated()).isZero();
        verify(datasetRepository, never()).save(any(CatalogDataset.class));
        verify(tableRepository, never()).save(any(CatalogTableSchema.class));
        verify(lifecycleRepository, org.mockito.Mockito.times(2)).findImplementation(
            "tenant-a",
            java.util.UUID.fromString("10000000-0000-0000-0000-000000000001")
        );
        verify(lifecycleRepository).saveDbtManagedArtifacts(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.same(modelSpec),
            org.mockito.ArgumentMatchers.same(implementation),
            org.mockito.ArgumentMatchers.eq("dbt-manifest:run-orders"),
            org.mockito.ArgumentMatchers.argThat(
                artifacts ->
                    artifacts.size() == 2 &&
                    artifacts.stream().map(ModelLifecycleContract.ArtifactWrite::artifactType).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.of("SQL", "SCHEMA"))
            ),
            org.mockito.ArgumentMatchers.any(java.time.Instant.class)
        );
    }

    @Test
    void lifecycleBoundModelReusesPublishedDatasetAndVerifiedTableLineageForColumnEvidence() throws Exception {
        java.util.UUID targetSourceId = java.util.UUID.fromString("90000000-0000-0000-0000-000000000001");
        writeArtifacts(
            """
            {
              "metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},
              "sources":{
                "source.dts.source_orders":{
                  "resource_type":"source","name":"source_orders","database":"warehouse","schema":"ods","identifier":"source_orders",
                  "columns":{"order_id":{"name":"order_id","data_type":"bigint"}}
                }
              },
              "nodes":{
                "model.dts.orders":{
                  "resource_type":"model","name":"orders","database":"warehouse","schema":"dwd","identifier":"orders_relation",
                  "original_file_path":"models/dwd/orders.sql","raw_code":"select order_id from source_orders",
                  "compiled_code":"select source_orders.order_id as order_id from source_orders",
                  "columns":{"order_id":{"name":"order_id","data_type":"bigint"}},
                  "config":{"materialized":"table","meta":{"tenantId":"tenant-a","modelSpecId":"10000000-0000-0000-0000-000000000001","revision":7,"modelChecksum":"sha256:model-v7","implementationRevision":3,"implementationChecksum":"sha256:implementation-v3","projectKey":"dts","layer":"DWD"}},
                  "depends_on":{"nodes":["source.dts.source_orders"]}
                }
              }
            }
            """,
            """
            {"metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:00:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );

        CatalogDataset source = dataset(java.util.UUID.randomUUID(), null, "ods", "source_orders");
        CatalogDataset target = dataset(java.util.UUID.randomUUID(), targetSourceId, "dwd", "orders_relation");
        CatalogTableSchema sourceTable = table(source, "source_orders");
        CatalogTableSchema targetTable = table(target, "orders_relation");
        CatalogColumnSchema sourceColumn = column(sourceTable, "order_id");
        CatalogColumnSchema targetColumn = column(targetTable, "order_id");
        CatalogDatasetLineage verified = new CatalogDatasetLineage();
        verified.setId(java.util.UUID.randomUUID());
        verified.setUpstreamDatasetId(source.getId());
        verified.setDownstreamDatasetId(target.getId());
        verified.setRelationType("MODEL_DEPENDENCY");
        verified.setVerificationStatus("VERIFIED");

        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "source_orders"))
            .thenReturn(Optional.of(source));
        when(datasetRepository.save(source)).thenReturn(source);
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(source, "source_orders"))
            .thenReturn(Optional.of(sourceTable));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "orders_relation"))
            .thenReturn(List.of(target));
        when(tableRepository.findByDataset(target)).thenReturn(List.of(targetTable));
        when(columnRepository.findByTable(sourceTable)).thenReturn(List.of(sourceColumn));
        when(columnRepository.findByTable(targetTable)).thenReturn(List.of(targetColumn));
        when(
            lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                source.getId(),
                target.getId(),
                "MODEL_DEPENDENCY"
            )
        ).thenReturn(Optional.of(verified));
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(target.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.save(any(CatalogColumnLineage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineageJobRepository.findByJobKey(any())).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any())).thenAnswer(invocation -> {
            var job = (com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob) invocation.getArgument(0);
            job.setId(java.util.UUID.randomUUID());
            return job;
        });

        ModelLifecycleContract.ImplementationView implementation = currentImplementation();
        when(lifecycleRepository.findImplementation("tenant-a", implementation.modelSpecId()))
            .thenReturn(Optional.of(implementation));
        ModelSpecContract.ModelSpecView modelSpec = org.mockito.Mockito.mock(ModelSpecContract.ModelSpecView.class);
        when(modelSpec.planId()).thenReturn(implementation.planId());
        when(modelSpec.revision()).thenReturn(implementation.revision());
        when(modelSpec.checksum()).thenReturn(implementation.modelChecksum());
        when(modelSpec.implementationMode()).thenReturn(ModelSpecContract.ImplementationMode.DBT_MANAGED);
        when(modelSpecReader.get("tenant-a", implementation.modelSpecId())).thenReturn(modelSpec);

        DbtAssetSyncService.DbtAssetSyncResult result = service(targetSourceId).syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        assertThat(result.stats().getLineageCreated()).isZero();
        assertThat(result.stats().getColumnLineageCreated()).isEqualTo(1);
        org.mockito.ArgumentCaptor<CatalogColumnLineage> captured = org.mockito.ArgumentCaptor.forClass(CatalogColumnLineage.class);
        verify(columnLineageRepository).save(captured.capture());
        assertThat(captured.getValue().getDatasetLineageId()).isEqualTo(verified.getId());
        assertThat(captured.getValue().getRelationType()).isEqualTo("DBT");
        verify(lineageRepository, never()).save(any(CatalogDatasetLineage.class));
        verify(datasetRepository, never()).save(target);
        verify(tableRepository, never()).save(targetTable);
    }

    @Test
    void replayingManifestReusesTheCurrentDbtLineageEdge() throws Exception {
        writeArtifacts(
            """
            {
              "metadata":{"invocation_id":"run-replay","generated_at":"2026-08-17T08:00:00Z","project_name":"dts"},
              "sources":{"source.dts.source_orders":{"resource_type":"source","name":"source_orders","database":"warehouse","schema":"ods","identifier":"source_orders"}},
              "nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"dwd","identifier":"orders","config":{"materialized":"table"},"depends_on":{"nodes":["source.dts.source_orders"]}}}
            }
            """,
            """
            {"metadata":{"invocation_id":"run-replay","generated_at":"2026-08-17T08:00:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset source = dataset(java.util.UUID.randomUUID(), null, "ods", "source_orders");
        CatalogDataset target = dataset(java.util.UUID.randomUUID(), null, "dwd", "orders");
        CatalogTableSchema sourceTable = table(source, "source_orders");
        CatalogTableSchema targetTable = table(target, "orders");
        CatalogDatasetLineage current = new CatalogDatasetLineage();
        current.setId(java.util.UUID.randomUUID());
        current.setUpstreamDatasetId(source.getId());
        current.setDownstreamDatasetId(target.getId());
        current.setRelationType("DBT");
        current.setVerificationStatus("DECLARED");

        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "source_orders"))
            .thenReturn(Optional.of(source));
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "orders"))
            .thenReturn(Optional.of(target));
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(source, "source_orders")).thenReturn(Optional.of(sourceTable));
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(target, "orders")).thenReturn(Optional.of(targetTable));
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(target.getId(), "DBT"))
            .thenReturn(List.of(current));
        when(
            lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                source.getId(),
                target.getId(),
                "DBT"
            )
        ).thenReturn(Optional.of(current));
        when(lineageRepository.save(current)).thenReturn(current);
        when(lineageJobRepository.findByJobKey(any())).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any())).thenAnswer(invocation -> {
            var job = (com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob) invocation.getArgument(0);
            job.setId(java.util.UUID.randomUUID());
            return job;
        });

        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        assertThat(result.stats().getLineageCreated()).isZero();
        assertThat(result.stats().getLineageRemoved()).isZero();
        verify(lineageRepository).save(current);
    }

    @Test
    void automatedManifestSyncDoesNotExpireAUserVerifiedDbtLineageEdge() throws Exception {
        writeArtifacts(
            """
            {
              "metadata":{"invocation_id":"run-without-dependency","generated_at":"2026-08-17T08:00:00Z","project_name":"dts"},
              "nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"dwd","identifier":"orders","config":{"materialized":"table"},"depends_on":{"nodes":[]}}},
              "sources":{}
            }
            """,
            """
            {"metadata":{"invocation_id":"run-without-dependency","generated_at":"2026-08-17T08:00:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset target = dataset(java.util.UUID.randomUUID(), null, "dwd", "orders");
        CatalogTableSchema targetTable = table(target, "orders");
        CatalogDatasetLineage verified = new CatalogDatasetLineage();
        verified.setId(java.util.UUID.randomUUID());
        verified.setUpstreamDatasetId(java.util.UUID.randomUUID());
        verified.setDownstreamDatasetId(target.getId());
        verified.setRelationType("DBT");
        verified.setVerificationStatus("VERIFIED");

        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "orders"))
            .thenReturn(Optional.of(target));
        when(datasetRepository.save(target)).thenReturn(target);
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(target, "orders")).thenReturn(Optional.of(targetTable));
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(target.getId(), "DBT"))
            .thenReturn(List.of(verified));
        when(lineageJobRepository.findByJobKey(any())).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        assertThat(result.stats().getLineageRemoved()).isZero();
        assertThat(verified.getValidTo()).isNull();
        verify(lineageRepository, never()).save(verified);
    }

    @Test
    void changedManifestSchemaCreatesOneOpenDriftIssueAcrossReplay() throws Exception {
        writeArtifacts(
            """
            {
              "metadata":{"invocation_id":"run-schema-drift","generated_at":"2026-08-17T08:00:00Z","project_name":"dts"},
              "nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"dwd","identifier":"orders","columns":{"order_id":{"name":"order_id","data_type":"varchar(64)"}},"config":{"materialized":"table"},"depends_on":{"nodes":[]}}},
              "sources":{}
            }
            """,
            """
            {"metadata":{"invocation_id":"run-schema-drift","generated_at":"2026-08-17T08:00:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset target = dataset(java.util.UUID.randomUUID(), null, "dwd", "orders");
        CatalogTableSchema targetTable = table(target, "orders");
        CatalogColumnSchema existingColumn = column(targetTable, "order_id");
        existingColumn.setDataType("bigint");
        existingColumn.setNullable(Boolean.FALSE);
        existingColumn.setStatus(CatalogColumnSyncService.STATUS_ACTIVE);
        CatalogSchemaDriftEvent replayMarker = new CatalogSchemaDriftEvent();
        replayMarker.setDatasetId(target.getId());
        replayMarker.setIntegration("DBT");

        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "orders"))
            .thenReturn(Optional.of(target));
        when(datasetRepository.save(target)).thenReturn(target);
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(target, "orders")).thenReturn(Optional.of(targetTable));
        when(columnRepository.findByTable(targetTable)).thenReturn(List.of(existingColumn));
        when(columnSyncService.parseManifestColumns(any())).thenReturn(
            List.of(new CatalogColumnSyncService.ColumnSpec("order_id", "varchar(64)", null, null, null, null, null, null))
        );
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(target.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(target.getId(), "DBT"))
            .thenReturn(List.of());
        when(lineageJobRepository.findByJobKey(any())).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(schemaDriftEventRepository.findTop200ByRunIdOrderByCreatedDateDesc(any()))
            .thenReturn(List.of(), List.of(replayMarker));

        DbtAssetSyncService service = service();
        DbtAssetSyncService.DbtAssetSyncResult first = service.syncFromManifest(tempDir.toString());
        DbtAssetSyncService.DbtAssetSyncResult replay = service.syncFromManifest(tempDir.toString());

        assertThat(first.stats().getSchemaDriftIssues()).isEqualTo(1);
        assertThat(replay.stats().getSchemaDriftIssues()).isZero();
        org.mockito.ArgumentCaptor<CatalogSchemaDriftEvent> captured = org.mockito.ArgumentCaptor.forClass(
            CatalogSchemaDriftEvent.class
        );
        verify(schemaDriftEventRepository).save(captured.capture());
        assertThat(captured.getValue().getIntegration()).isEqualTo("DBT");
        assertThat(captured.getValue().getDatasetId()).isEqualTo(target.getId());
        assertThat(captured.getValue().getChangedCount()).isEqualTo(1);
        assertThat(captured.getValue().getTicketStatus()).isEqualTo(CatalogSchemaDriftEvent.TICKET_OPEN);
    }

    @Test
    void successfulDbtManifestLineageEnqueuesClassificationPropagationForTheMaterializedModel() throws Exception {
        writeArtifacts(
            """
            {
              "metadata":{"invocation_id":"run-classification","generated_at":"2026-07-26T08:00:00Z","project_name":"dts"},
              "sources":{
                "source.dts.source_orders":{
                  "resource_type":"source",
                  "name":"source_orders",
                  "database":"warehouse",
                  "schema":"ods",
                  "identifier":"source_orders",
                  "columns":{"id":{"name":"id","data_type":"bigint"}}
                }
              },
              "nodes":{
                "model.dts.dwd_orders":{
                  "resource_type":"model",
                  "name":"dwd_orders",
                  "database":"warehouse",
                  "schema":"dwd",
                  "identifier":"dwd_orders",
                  "compiled_code":"select id from source_orders",
                  "columns":{"id":{"name":"id","data_type":"bigint"}},
                  "config":{"materialized":"table"},
                  "depends_on":{"nodes":["source.dts.source_orders"]}
                }
              }
            }
            """,
            """
            {
              "metadata":{"invocation_id":"run-classification","generated_at":"2026-07-26T08:00:00Z"},
              "results":[{"unique_id":"model.dts.dwd_orders","status":"success"}]
            }
            """
        );
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(any(), any()))
            .thenReturn(Optional.empty());
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> {
            CatalogDataset dataset = invocation.getArgument(0);
            if (dataset.getId() == null) {
                dataset.setId(java.util.UUID.randomUUID());
            }
            return dataset;
        });
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(any(CatalogDataset.class), any()))
            .thenReturn(Optional.empty());
        when(tableRepository.save(any(CatalogTableSchema.class))).thenAnswer(invocation -> {
            CatalogTableSchema table = invocation.getArgument(0);
            if (table.getId() == null) {
                table.setId(java.util.UUID.randomUUID());
            }
            return table;
        });
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(any(), any()))
            .thenReturn(List.of());
        when(
            lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("DBT")
            )
        ).thenReturn(Optional.empty());
        when(lineageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineageJobRepository.findByJobKey(any())).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any())).thenAnswer(invocation -> {
            var job = (com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob) invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(java.util.UUID.randomUUID());
            }
            return job;
        });
        when(propagationJobService.enqueue(
                any(),
                org.mockito.ArgumentMatchers.eq("DBT"),
                org.mockito.ArgumentMatchers.eq("dbt:model.dts.dwd_orders:run-classification")
            ))
            .thenReturn(true);

        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        assertThat(result.stats().getLineageCreated()).isEqualTo(1);
        assertThat(result.stats().getClassificationPropagationEnqueued()).isEqualTo(1);
        verify(propagationJobService).enqueue(
            any(),
            org.mockito.ArgumentMatchers.eq("DBT"),
            org.mockito.ArgumentMatchers.eq("dbt:model.dts.dwd_orders:run-classification")
        );
        verify(assetObservation).observe(
            any(CatalogDataset.class),
            org.mockito.ArgumentMatchers.argThat(observation ->
                observation.producerKind() == ProducerKind.DBT_MODEL &&
                observation.evidenceChannel() == EvidenceChannel.DBT_SYNC &&
                "model.dts.dwd_orders".equals(observation.producerId()) &&
                observation.evidenceRef().contains("schema:")
            )
        );
    }

    @Test
    void syncMarksExistingPhysicalRelationStaleAndRevokesLineageWhenRunFailed() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-failed","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.failed_orders":{"resource_type":"model","name":"failed_orders","database":"warehouse","schema":"analytics","identifier":"failed_orders_relation","config":{"materialized":"incremental","meta":{"layer":"DWD"}},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-failed","generated_at":"2026-07-24T08:00:00Z"},"results":[{"unique_id":"model.dts.failed_orders","status":"error"}]}
            """
        );
        CatalogDataset existing = new CatalogDataset();
        existing.setId(java.util.UUID.randomUUID());
        existing.setHiveDatabase("analytics");
        existing.setHiveTable("failed_orders_relation");
        existing.setEnabled(true);
        existing.setLifecycleStatus("PUBLISHED");
        existing.setTags("{\"dbtUniqueId\":\"model.dts.failed_orders\",\"runInvocationId\":\"run-before\",\"materializedTruth\":true}");
        com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage lineage =
            new com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage();
        lineage.setId(java.util.UUID.randomUUID());
        lineage.setDownstreamDatasetId(existing.getId());
        lineage.setRelationType("DBT");
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("analytics", "failed_orders_relation"))
            .thenReturn(Optional.of(existing));
        when(datasetRepository.save(existing)).thenReturn(existing);
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of(lineage));
        when(lineageRepository.save(lineage)).thenReturn(lineage);
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of());

        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isTrue();
        assertThat(existing.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(existing.getEnabled()).isFalse();
        assertThat(existing.getTags()).contains("\"materializedTruth\":false").contains("\"staleReason\":\"RUN_FAILED\"");
        assertThat(lineage.getValidTo()).isNotNull();
        verify(datasetRepository).save(existing);
        verify(tableRepository, never()).save(any(CatalogTableSchema.class));
    }

    @Test
    void syncMarksExistingPhysicalRelationStaleWhenRunResultsBelongToAnotherInvocation() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"manifest-run","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics","identifier":"orders_relation","config":{"materialized":"table","meta":{"layer":"DWD"}},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"another-run","generated_at":"2026-07-24T08:01:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset existing = new CatalogDataset();
        existing.setId(java.util.UUID.randomUUID());
        existing.setHiveDatabase("analytics");
        existing.setHiveTable("orders_relation");
        existing.setEnabled(true);
        existing.setLifecycleStatus("PUBLISHED");
        existing.setTags("{\"dbtUniqueId\":\"model.dts.orders\",\"materializedTruth\":true}");
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("analytics", "orders_relation"))
            .thenReturn(Optional.of(existing));
        when(datasetRepository.save(existing)).thenReturn(existing);
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of());

        service().syncFromManifest(tempDir.toString());

        assertThat(existing.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(existing.getTags()).contains("\"staleReason\":\"RUN_EVIDENCE_UNVERIFIED\"");
        verify(datasetRepository).save(existing);
    }

    @Test
    void syncPreservesPhysicalModelOmittedFromSelectiveRunResults() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-selected","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.customers":{"resource_type":"model","name":"customers","database":"warehouse","schema":"analytics","identifier":"customers_relation","config":{"materialized":"table"},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-selected","generated_at":"2026-07-24T08:01:00Z"},"results":[]}
            """
        );
        CatalogDataset existing = currentDbtDataset("model.dts.customers", "customers_relation", "dts", workspaceKey());
        when(datasetRepository.findAll()).thenReturn(List.of(existing));

        service().syncFromManifest(tempDir.toString());

        assertThat(existing.getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(existing.getEnabled()).isTrue();
        verify(datasetRepository, never()).save(any(CatalogDataset.class));
    }

    @Test
    void syncDoesNotInvalidateAnyCatalogAssetWhenManifestIsMissing() {
        DbtAssetSyncService.DbtAssetSyncResult result = service().syncFromManifest(tempDir.toString());

        assertThat(result.synced()).isFalse();
        verify(datasetRepository, never()).findAll();
        verify(datasetRepository, never()).save(any(CatalogDataset.class));
    }

    @Test
    void syncMarksRemovedModelFromTheSameWorkspaceAndProjectStaleAndRevokesLineage() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-empty","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-empty","generated_at":"2026-07-24T08:01:00Z"},"results":[]}
            """
        );
        CatalogDataset removed = currentDbtDataset("model.dts.removed_orders", "removed_orders", "dts", workspaceKey());
        com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage lineage =
            new com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage();
        lineage.setId(java.util.UUID.randomUUID());
        lineage.setDownstreamDatasetId(removed.getId());
        lineage.setRelationType("DBT");
        when(datasetRepository.findAll()).thenReturn(List.of(removed));
        when(datasetRepository.save(removed)).thenReturn(removed);
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(removed.getId(), "DBT"))
            .thenReturn(List.of(lineage));
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(removed.getId(), "DBT"))
            .thenReturn(List.of());

        service().syncFromManifest(tempDir.toString());

        assertThat(removed.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(removed.getTags()).contains("\"staleReason\":\"MANIFEST_MODEL_REMOVED_OR_NON_PHYSICAL\"");
        assertThat(lineage.getValidTo()).isNotNull();
    }

    @Test
    void syncMarksFormerPhysicalModelStaleWhenItBecomesEphemeral() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-ephemeral","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics","identifier":"orders_relation","config":{"materialized":"ephemeral"},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-ephemeral","generated_at":"2026-07-24T08:01:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset formerPhysical = currentDbtDataset("model.dts.orders", "orders_relation", "dts", workspaceKey());
        when(datasetRepository.findAll()).thenReturn(List.of(formerPhysical));
        when(datasetRepository.save(formerPhysical)).thenReturn(formerPhysical);
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(formerPhysical.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(formerPhysical.getId(), "DBT"))
            .thenReturn(List.of());

        service().syncFromManifest(tempDir.toString());

        assertThat(formerPhysical.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(formerPhysical.getTags()).contains("\"staleReason\":\"MANIFEST_MODEL_REMOVED_OR_NON_PHYSICAL\"");
    }

    @Test
    void syncStalesTheFormerPhysicalAssetWhenTheSameDbtNodeMovesRelation() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-moved","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics_v2","identifier":"orders_v2","config":{"materialized":"table"},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-moved","generated_at":"2026-07-24T08:01:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset formerPhysical = currentDbtDataset("model.dts.orders", "orders_relation", "dts", workspaceKey());
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("analytics_v2", "orders_v2"))
            .thenReturn(Optional.empty());
        when(datasetRepository.findAll()).thenReturn(List.of(formerPhysical));
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(any(CatalogDataset.class), any())).thenReturn(Optional.empty());
        when(tableRepository.save(any(CatalogTableSchema.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(formerPhysical.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(formerPhysical.getId(), "DBT"))
            .thenReturn(List.of());

        service().syncFromManifest(tempDir.toString());

        assertThat(formerPhysical.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(formerPhysical.getEnabled()).isFalse();
        assertThat(formerPhysical.getTags()).contains("\"staleReason\":\"MANIFEST_MODEL_REMOVED_OR_NON_PHYSICAL\"");
    }

    @Test
    void syncDoesNotTouchDbtMaterializationOwnedByAnotherWorkspaceOrProject() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-empty","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-empty","generated_at":"2026-07-24T08:01:00Z"},"results":[]}
            """
        );
        CatalogDataset otherProject = currentDbtDataset("model.other.orders", "orders_relation", "other", workspaceKey());
        CatalogDataset otherWorkspace = currentDbtDataset(
            "model.dts.customers",
            "customers_relation",
            "dts",
            java.util.UUID.randomUUID().toString()
        );
        when(datasetRepository.findAll()).thenReturn(List.of(otherProject, otherWorkspace));

        service().syncFromManifest(tempDir.toString());

        assertThat(otherProject.getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(otherWorkspace.getLifecycleStatus()).isEqualTo("PUBLISHED");
        verify(datasetRepository, never()).save(otherProject);
        verify(datasetRepository, never()).save(otherWorkspace);
    }

    @Test
    void syncMarksExistingPinnedModelStaleWhenImplementationChecksumIsMissing() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-incomplete","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics","identifier":"orders_relation","config":{"materialized":"table","meta":{"modelSpecId":"10000000-0000-0000-0000-000000000001","revision":7,"modelChecksum":"sha256:model-v7","implementationRevision":3}},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-incomplete","generated_at":"2026-07-24T08:01:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset existing = currentDbtDataset("model.dts.orders", "orders_relation", "dts", workspaceKey());
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("analytics", "orders_relation"))
            .thenReturn(Optional.of(existing));
        when(datasetRepository.save(existing)).thenReturn(existing);
        when(lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of());
        when(columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(existing.getId(), "DBT"))
            .thenReturn(List.of());

        service().syncFromManifest(tempDir.toString());

        assertThat(existing.getLifecycleStatus()).isEqualTo("STALE");
        assertThat(existing.getEnabled()).isFalse();
        assertThat(existing.getTags()).contains("\"staleReason\":\"IMPLEMENTATION_PIN_INVALID\"");
        verify(datasetRepository).save(existing);
    }

    @Test
    void syncPreservesExistingLayerWhenModelHasNoControlledLayerMetadata() throws Exception {
        writeArtifacts(
            """
            {"metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:00:00Z","project_name":"dts"},"nodes":{"model.dts.orders":{"resource_type":"model","name":"orders","database":"warehouse","schema":"analytics","identifier":"orders_relation","config":{"materialized":"table"},"depends_on":{"nodes":[]}}},"sources":{}}
            """,
            """
            {"metadata":{"invocation_id":"run-orders","generated_at":"2026-07-24T08:01:00Z"},"results":[{"unique_id":"model.dts.orders","status":"success"}]}
            """
        );
        CatalogDataset existing = new CatalogDataset();
        existing.setId(java.util.UUID.randomUUID());
        existing.setHiveDatabase("analytics");
        existing.setHiveTable("orders_relation");
        existing.setWarehouseLayer("ADS");
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("analytics", "orders_relation"))
            .thenReturn(Optional.of(existing));
        when(datasetRepository.save(existing)).thenReturn(existing);
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(existing, "orders_relation")).thenReturn(Optional.of(new CatalogTableSchema()));

        service().syncFromManifest(tempDir.toString());

        assertThat(existing.getWarehouseLayer()).isEqualTo("ADS");
    }

    private DbtAssetSyncService service() {
        return service(null);
    }

    private DbtAssetSyncService service(
        java.util.UUID targetSourceId
    ) {
        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        DbtConfigService.DbtWorkspaceConfig config = new DbtConfigService.DbtWorkspaceConfig(
            true,
            tempDir.toString(),
            tempDir.resolve("profiles").toString(),
            "dts",
            "dev",
            targetSourceId,
            null,
            null,
            Map.of()
        );
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                config,
                DbtConfigService.DbtProfileStatus.skipped("test"),
                null,
                new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
            )
        );
        org.mockito.Mockito.lenient().when(mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc()).thenReturn(List.of());
        return new DbtAssetSyncService(
            new ObjectMapper(),
            properties,
            configService,
            datasetRepository,
            lineageRepository,
            columnLineageRepository,
            lineageJobRepository,
            tableRepository,
            columnRepository,
            columnSyncService,
            mappingRepository,
            lifecycleRepository,
            modelSpecReader,
            auditService,
            propagationJobService,
            assetObservation,
            new SchemaDriftDetector(new ObjectMapper()),
            schemaDriftEventRepository
        );
    }

    private void writeArtifacts(String manifest, String runResults) throws Exception {
        Path target = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(target.resolve("manifest.json"), manifest);
        Files.writeString(target.resolve("run_results.json"), runResults);
    }

    private CatalogDataset currentDbtDataset(String uniqueId, String table, String project, String workspace) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(java.util.UUID.randomUUID());
        dataset.setHiveDatabase("analytics");
        dataset.setHiveTable(table);
        dataset.setEnabled(true);
        dataset.setLifecycleStatus("PUBLISHED");
        dataset.setTags(
            "{\"dbtUniqueId\":\"" + uniqueId + "\",\"dbtProject\":\"" + project +
            "\",\"dbtWorkspace\":\"" + workspace +
            "\",\"database\":\"warehouse\",\"schema\":\"analytics\",\"identifier\":\"" + table +
            "\",\"materializedTruth\":true,\"physicalAssetVerified\":true,\"artifactState\":\"CURRENT\"}"
        );
        return dataset;
    }

    private static CatalogDataset dataset(java.util.UUID id, java.util.UUID sourceId, String schema, String name) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setSourceId(sourceId);
        dataset.setHiveDatabase(schema);
        dataset.setHiveTable(name);
        dataset.setName(name);
        dataset.setEnabled(true);
        return dataset;
    }

    private static CatalogTableSchema table(CatalogDataset dataset, String name) {
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(java.util.UUID.randomUUID());
        table.setDataset(dataset);
        table.setName(name);
        return table;
    }

    private static CatalogColumnSchema column(CatalogTableSchema table, String name) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(java.util.UUID.randomUUID());
        column.setTable(table);
        column.setName(name);
        return column;
    }

    private String workspaceKey() {
        String normalized = tempDir.toAbsolutePath().normalize().toString();
        return java.util.UUID.nameUUIDFromBytes(
            ("dbt-workspace:" + normalized).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ).toString();
    }

    private ModelLifecycleContract.ImplementationView currentImplementation() {
        return new ModelLifecycleContract.ImplementationView(
            java.util.UUID.randomUUID(),
            java.util.UUID.fromString("10000000-0000-0000-0000-000000000001"),
            java.util.UUID.randomUUID(),
            7,
            "sha256:model-v7",
            ModelSpecContract.ImplementationMode.DBT_MANAGED,
            "dts",
            "model.dts.orders",
            "DRAFT",
            3,
            "sha256:implementation-v3",
            ModelLifecycleContract.InputMode.GENERATED,
            List.of(new ModelLifecycleContract.GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );
    }
}
