package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationAdmissionService;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.SealReference;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OdsTableMappingSyncServiceTest {

    @Mock
    private InfraOdsTableMappingRepository mappingRepository;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSyncService columnSyncService;

    @Mock
    private DbtSourceService dbtSourceService;

    @Mock
    private IngestionLineageWriter ingestionLineageWriter;

    @Mock
    private AuditService auditService;

    @Mock
    private CatalogClassificationAdmissionService classificationAdmissionService;

    @Mock
    private EntityManager entityManager;

    @Captor
    private ArgumentCaptor<InfraOdsTableMapping> mappingCaptor;

    @Captor
    private ArgumentCaptor<com.yuzhi.dts.platform.domain.catalog.CatalogDataset> datasetCaptor;

    @Captor
    private ArgumentCaptor<IngestionLineageWriter.LineageObservation> observationCaptor;

    private OdsTableMappingSyncService service;

    @BeforeEach
    void setUp() {
        service = new OdsTableMappingSyncService(
            mappingRepository,
            dataSourceRepository,
            datasetRepository,
            tableRepository,
            columnSyncService,
            dbtSourceService,
            ingestionLineageWriter,
            auditService,
            classificationAdmissionService,
            new ObjectMapper(),
            entityManager
        );
    }

    @Test
    void syncFromIngestionPayload_shouldDefaultPostgresDestinationSchemaToPublicWhenMissing() {
        UUID sourceDataSourceId = UUID.randomUUID();
        when(mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(sourceDataSourceId, "project_subject_domain", ""))
            .thenReturn(Optional.empty());
        when(mappingRepository.findByConnectionIdOrderByCreatedDateDesc(sourceDataSourceId)).thenReturn(List.of());
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(mappingRepository.save(mappingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(
            ingestionLineageWriter.writeAddaxLineage(
                org.mockito.ArgumentMatchers.any(InfraOdsTableMapping.class),
                org.mockito.ArgumentMatchers.any(IngestionLineageWriter.LineageObservation.class)
            )
        )
            .thenReturn(new IngestionLineageWriter.LineageWriteResult(1, 0, 0, 0, 1, "created"));

        Map<String, Object> payload = Map.of(
            "task",
            Map.of(
                "id", "task-1",
                "name", "prjtest2000",
                "sourceDataSourceId", sourceDataSourceId.toString(),
                "tableMapping", List.of(Map.of("source", "project_subject_domain", "target", "ods_prj_prjtest2000")),
                "destinationConfig", Map.of(
                    "writerType", "postgres",
                    "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin"
                )
            ),
            "execution",
            Map.of(
                "schemaSnapshots",
                List.of(
                    Map.of(
                        "sourceTable", "project_subject_domain",
                        "odsSchema", "public",
                        "odsTable", "ods_prj_prjtest2000",
                        "schemaFingerprint", "schema-fingerprint-project-domain"
                    )
                )
            )
        );

        OdsTableMappingSyncService.SyncResult result = service.syncFromIngestionPayload(payload);

        assertThat(result.tables()).isEqualTo(1);
        assertThat(mappingCaptor.getValue().getOdsSchema()).isEqualTo("public");
        assertThat(mappingCaptor.getValue().getOdsTable()).isEqualTo("ods_prj_prjtest2000");
        verify(ingestionLineageWriter).writeAddaxLineage(any(), observationCaptor.capture());
        assertThat(observationCaptor.getValue().schemaFingerprint()).isEqualTo("schema-fingerprint-project-domain");
    }

    @Test
    void syncFromIngestionPayload_shouldProjectSealedFileClassificationToOdsAssetWithoutColumns() {
        UUID syntheticConnectionId = UUID.nameUUIDFromBytes("ingestion-task:file-task-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CatalogDataset dataset = catalogDataset(syntheticConnectionId, null);
        prepareFileMappingSync(syntheticConnectionId, dataset, "CONFIDENTIAL");
        when(datasetRepository.save(datasetCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        OdsTableMappingSyncService.SyncResult result = service.syncFromIngestionPayload(
            filePayload("file-task-1", "INTERNAL")
        );

        assertThat(result.tables()).isEqualTo(1);
        assertThat(datasetCaptor.getValue().getClassification()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void syncFromIngestionPayload_shouldNeverDowngradeExistingOdsAssetClassification() {
        UUID syntheticConnectionId = UUID.nameUUIDFromBytes("ingestion-task:file-task-2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CatalogDataset dataset = catalogDataset(syntheticConnectionId, "SECRET");
        prepareFileMappingSync(syntheticConnectionId, dataset, "INTERNAL");

        service.syncFromIngestionPayload(filePayload("file-task-2", "INTERNAL"));

        assertThat(dataset.getClassification()).isEqualTo("SECRET");
        verify(datasetRepository, never()).save(any());
    }

    @Test
    void syncFromIngestionPayload_shouldRegisterApiLandingTargetColumnsAndApiLineage() {
        UUID sourceDataSourceId = UUID.randomUUID();
        UUID datasetId = apiDatasetId(sourceDataSourceId, "task-api-1", "orders");
        UUID tableId = apiTableId(datasetId);
        when(entityManager.find(InfraDataSource.class, sourceDataSourceId, LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(new InfraDataSource());
        when(mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
            sourceDataSourceId,
            "orders",
            "api:task-api-1"
        )).thenReturn(Optional.empty());
        when(mappingRepository.findByConnectionIdOrderByCreatedDateDesc(sourceDataSourceId)).thenReturn(List.of());
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());
        when(tableRepository.findById(tableId)).thenReturn(Optional.empty());
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(mappingRepository.save(mappingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(datasetRepository.save(datasetCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(
            ingestionLineageWriter.writeIngestionLineage(
                any(InfraOdsTableMapping.class),
                any(IngestionLineageWriter.LineageObservation.class),
                org.mockito.ArgumentMatchers.eq(IngestionLineageWriter.RELATION_API)
            )
        ).thenReturn(new IngestionLineageWriter.LineageWriteResult(1, 0, 0, 0, 1, "created"));

        Map<String, Object> payload = apiPayload(sourceDataSourceId, 101L, "2026-07-24T08:00:00Z", "sha256:api-v1", "sha256:fields-v1");

        OdsTableMappingSyncService.SyncResult result = service.syncFromIngestionPayload(
            payload,
            IngestionLineageWriter.LineageObservation.fromExecution(
                "SUCCESS",
                "run-api-101",
                "batch-api-1",
                java.time.Instant.parse("2026-07-24T08:00:00Z")
            )
        );

        assertThat(result.tables()).isEqualTo(1);
        assertThat(datasetCaptor.getValue().getId()).isEqualTo(datasetId);
        assertThat(mappingCaptor.getValue().getStreamNamespace()).isEqualTo("api:task-api-1");
        assertThat(mappingCaptor.getValue().getStreamName()).isEqualTo("orders");
        assertThat(mappingCaptor.getValue().getOdsSchema()).isEqualTo("ods");
        assertThat(mappingCaptor.getValue().getOdsTable()).isEqualTo("ods_api_crm_orders");
        assertThat(datasetCaptor.getValue().getTags()).contains("sha256:api-v1").contains("run-api-101");
        verify(datasetRepository, never()).findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(any(), any());
        verify(columnSyncService).upsertColumns(
            any(),
            org.mockito.ArgumentMatchers.argThat(columns -> {
                List<String> names = columns.stream().map(CatalogColumnSyncService.ColumnSpec::name).toList();
                return names.size() == 4 && names.containsAll(List.of("order_id", "__raw_record", "_dts_ingested_at", "_dts_cursor"));
            }),
            org.mockito.ArgumentMatchers.eq(CatalogColumnSyncService.STATUS_ACTIVE)
        );
        verify(ingestionLineageWriter).writeIngestionLineage(
            any(),
            observationCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(IngestionLineageWriter.RELATION_API)
        );
        assertThat(observationCaptor.getValue().schemaFingerprint()).isEqualTo("sha256:fields-v1");
    }

    @Test
    void syncFromIngestionPayload_shouldReplayApiLandingAgainstTheSameCatalogAssets() {
        UUID sourceDataSourceId = UUID.randomUUID();
        UUID datasetId = apiDatasetId(sourceDataSourceId, "task-api-1", "orders");
        UUID tableId = apiTableId(datasetId);
        when(entityManager.find(InfraDataSource.class, sourceDataSourceId, LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(new InfraDataSource());
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setConnectionId(sourceDataSourceId);
        mapping.setStreamNamespace("api:task-api-1");
        mapping.setStreamName("orders");
        mapping.setDescription("[task:task-api-1] crm-api: api:task-api-1.orders -> ods.ods_api_crm_orders");
        com.yuzhi.dts.platform.domain.catalog.CatalogDataset dataset = new com.yuzhi.dts.platform.domain.catalog.CatalogDataset();
        dataset.setName("ods_api_crm_orders");
        dataset.setHiveDatabase("ods");
        dataset.setHiveTable("ods_api_crm_orders");
        dataset.setSourceId(sourceDataSourceId);
        dataset.setId(datasetId);
        com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema table = new com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema();
        table.setId(tableId);
        table.setDataset(dataset);
        table.setName("ods_api_crm_orders");
        when(mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
            sourceDataSourceId,
            "orders",
            "api:task-api-1"
        )).thenReturn(Optional.of(mapping));
        when(mappingRepository.findByConnectionIdOrderByCreatedDateDesc(sourceDataSourceId)).thenReturn(List.of(mapping));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(mappingRepository.save(mapping)).thenReturn(mapping);
        when(datasetRepository.save(dataset)).thenReturn(dataset);
        when(tableRepository.save(table)).thenReturn(table);
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(
            ingestionLineageWriter.writeIngestionLineage(
                org.mockito.ArgumentMatchers.eq(mapping),
                org.mockito.ArgumentMatchers.any(IngestionLineageWriter.LineageObservation.class),
                org.mockito.ArgumentMatchers.eq(IngestionLineageWriter.RELATION_API)
            )
        ).thenReturn(new IngestionLineageWriter.LineageWriteResult(0, 1, 0, 0, 1, "updated"));
        Map<String, Object> payload = apiPayload(sourceDataSourceId, 101L, "2026-07-24T08:00:00Z", "sha256:api-v1", "sha256:fields-v1");
        IngestionLineageWriter.LineageObservation observation = IngestionLineageWriter.LineageObservation.fromExecution(
            "SUCCESS",
            "run-api-101",
            "batch-api-1",
            java.time.Instant.parse("2026-07-24T08:00:00Z")
        );

        service.syncFromIngestionPayload(payload, observation);
        service.syncFromIngestionPayload(payload, observation);

        org.mockito.Mockito.verify(mappingRepository, org.mockito.Mockito.times(2)).save(mapping);
        org.mockito.Mockito.verify(datasetRepository, org.mockito.Mockito.times(2)).save(dataset);
        org.mockito.Mockito.verify(tableRepository, org.mockito.Mockito.times(2)).save(table);
    }

    @Test
    void syncFromIngestionPayload_shouldRejectOlderApiExecutionWithoutOverwritingCurrentEvidence() {
        UUID sourceDataSourceId = UUID.randomUUID();
        UUID datasetId = apiDatasetId(sourceDataSourceId, "task-api-1", "orders");
        when(entityManager.find(InfraDataSource.class, sourceDataSourceId, LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(new InfraDataSource());
        com.yuzhi.dts.platform.domain.catalog.CatalogDataset dataset = new com.yuzhi.dts.platform.domain.catalog.CatalogDataset();
        dataset.setId(datasetId);
        dataset.setSourceId(sourceDataSourceId);
        dataset.setTags(
            "origin=API;connectionId=" + sourceDataSourceId +
            ";taskId=task-api-1;resourceId=orders;taskRevision=2026-07-24T07:00:00Z;executionSequence=102;" +
            "executionId=run-api-102;executionStatus=success;configChecksum=sha256:api-v2;fieldSnapshotChecksum=sha256:fields-v2"
        );
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        Map<String, Object> payload = apiPayload(sourceDataSourceId, 101L, "2026-07-24T08:00:00Z", "sha256:api-v1", "sha256:fields-v1");
        OdsTableMappingSyncService.SyncResult result = service.syncFromIngestionPayload(
            payload,
            IngestionLineageWriter.LineageObservation.fromExecution(
                "SUCCESS",
                "run-api-101",
                "batch-api-1",
                java.time.Instant.parse("2026-07-24T08:00:00Z")
            )
        );

        assertThat(result.synced()).isFalse();
        assertThat(result.message()).contains("旧");
        InOrder lockThenRead = org.mockito.Mockito.inOrder(entityManager, datasetRepository);
        lockThenRead.verify(entityManager).find(InfraDataSource.class, sourceDataSourceId, LockModeType.PESSIMISTIC_WRITE);
        lockThenRead.verify(datasetRepository).findById(datasetId);
        verify(mappingRepository, never()).save(any());
        verify(datasetRepository, never()).save(any());
        verify(ingestionLineageWriter, never()).writeIngestionLineage(any(), any(), any());
    }

    private static UUID apiDatasetId(UUID connectionId, String taskId, String resourceId) {
        return UUID.nameUUIDFromBytes(
            ("api-landing-dataset:" + connectionId + ":api:" + taskId + ":" + resourceId).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private void prepareFileMappingSync(UUID connectionId, CatalogDataset dataset, String admittedClassification) {
        when(classificationAdmissionService.requireValid(any(SealReference.class))).thenAnswer(invocation -> {
            CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
            snapshot.setEffectiveLevel(admittedClassification);
            return snapshot;
        });
        when(mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(connectionId, "uploaded_file", ""))
            .thenReturn(Optional.empty());
        when(mappingRepository.findByConnectionIdOrderByCreatedDateDesc(connectionId)).thenReturn(List.of());
        when(mappingRepository.save(mappingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(
            ingestionLineageWriter.writeAddaxLineage(
                any(InfraOdsTableMapping.class),
                any(IngestionLineageWriter.LineageObservation.class)
            )
        ).thenReturn(new IngestionLineageWriter.LineageWriteResult(1, 0, 0, 0, 1, "created"));
        when(
            datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                connectionId,
                "public",
                "ods_file_orders"
            )
        ).thenReturn(Optional.of(dataset));
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
    }

    private static CatalogDataset catalogDataset(UUID connectionId, String classification) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName("ods_file_orders");
        dataset.setSourceId(connectionId);
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("ods_file_orders");
        dataset.setClassification(classification);
        return dataset;
    }

    private static Map<String, Object> filePayload(String taskId, String effectiveLevel) {
        return Map.of(
            "task",
            Map.of(
                "id", taskId,
                "name", "file-orders",
                "sourceType", "txtfilereader",
                "classificationSeal",
                Map.of(
                    "sealId", UUID.randomUUID().toString(),
                    "subjectType", "ASSET",
                    "subjectKey", "ingestion-file:sealed-file",
                    "assetType", "DATASET",
                    "effectiveLevel", effectiveLevel,
                    "snapshotVersion", 1L,
                    "checksum", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "sealedAt", "2026-09-02T10:39:28Z",
                    "propagationStatus", "PROPAGATED"
                ),
                "tableMapping", List.of(Map.of("source", "uploaded_file", "target", "public.ods_file_orders")),
                "destinationConfig", Map.of("writerType", "postgres")
            )
        );
    }

    private static UUID apiTableId(UUID datasetId) {
        return UUID.nameUUIDFromBytes(("api-landing-table:" + datasetId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<String, Object> apiPayload(
        UUID sourceDataSourceId,
        long executionSequence,
        String taskRevision,
        String configChecksum,
        String fieldSnapshotChecksum
    ) {
        return Map.of(
            "task",
            Map.of(
                "id", "task-api-1",
                "name", "crm-api",
                "sourceType", "httpreader",
                "sourceKind", "API",
                "sourceDataSourceId", sourceDataSourceId.toString(),
                "taskRevision", taskRevision
            ),
            "execution",
            Map.of(
                "id", executionSequence,
                "executionSequence", executionSequence,
                "status", "SUCCESS",
                "executionId", "run-api-" + executionSequence,
                "batchId", "batch-api-1",
                "endTime", "2026-07-24T08:00:00Z",
                "targetTables",
                List.of(
                    Map.ofEntries(
                        Map.entry("qualifiedName", "ods.ods_api_crm_orders"),
                        Map.entry("resourceId", "orders"),
                        Map.entry("executionId", "run-api-" + executionSequence),
                        Map.entry("landingStatus", "SUCCESS"),
                        Map.entry("rowsWritten", 4L),
                        Map.entry("rawRecordColumn", "__raw_record"),
                        Map.entry("technicalColumns", List.of("_dts_ingested_at", "_dts_cursor")),
                        Map.entry("fieldSnapshot", List.of(Map.of("name", "order_id", "type", "string"))),
                        Map.entry("configChecksum", configChecksum),
                        Map.entry("fieldSnapshotChecksum", fieldSnapshotChecksum),
                        Map.entry("cursorValue", "2026-07-24T00:00:00Z")
                    )
                )
            )
        );
    }
}
