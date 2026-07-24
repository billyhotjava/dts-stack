package com.yuzhi.dts.platform.service.catalog.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionLineageWriterTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDatasetLineageRepository lineageRepository;

    @Mock
    private CatalogLineageJobRepository lineageJobRepository;

    @Mock
    private InfraOdsTableMappingRepository mappingRepository;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<CatalogDatasetLineage> lineageCaptor;

    @Test
    void writeIngestionLineage_shouldPersistApiOriginWithoutChangingAddaxRelation() {
        IngestionLineageWriter writer = new IngestionLineageWriter(
            datasetRepository,
            lineageRepository,
            lineageJobRepository,
            mappingRepository,
            dataSourceRepository,
            auditService
        );
        UUID connectionId = UUID.randomUUID();
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setConnectionId(connectionId);
        mapping.setEnabled(true);
        mapping.setStreamNamespace("api:task-api-1");
        mapping.setStreamName("orders");
        mapping.setOdsSchema("ods");
        mapping.setOdsTable("ods_api_crm_orders");

        when(datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(connectionId, "api:task-api-1", "orders"))
            .thenReturn(Optional.empty());
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("api:task-api-1", "orders"))
            .thenReturn(Optional.empty());
        when(datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(connectionId, "ods", "ods_api_crm_orders"))
            .thenReturn(Optional.empty());
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_api_crm_orders"))
            .thenReturn(Optional.empty());
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> {
            CatalogDataset dataset = invocation.getArgument(0);
            dataset.setId(UUID.randomUUID());
            return dataset;
        });
        when(lineageJobRepository.findByJobKey(org.mockito.ArgumentMatchers.startsWith("API:"))).thenReturn(Optional.empty());
        when(lineageJobRepository.save(any(CatalogLineageJob.class))).thenAnswer(invocation -> {
            CatalogLineageJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        when(lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
            any(),
            any(),
            org.mockito.ArgumentMatchers.eq(IngestionLineageWriter.RELATION_API)
        )).thenReturn(Optional.empty());
        when(lineageRepository.save(lineageCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionLineageWriter.LineageWriteResult result = writer.writeIngestionLineage(
            mapping,
            IngestionLineageWriter.LineageObservation.fromExecution("success", "run-api-1", "batch-api-1", null),
            IngestionLineageWriter.RELATION_API
        );

        assertThat(result.created()).isEqualTo(1);
        assertThat(lineageCaptor.getValue().getRelationType()).isEqualTo(IngestionLineageWriter.RELATION_API);
        assertThat(lineageCaptor.getValue().getNotes()).contains("origin=API").contains("executionId=run-api-1");
    }

    @Test
    void writeIngestionLineage_shouldRejectUnverifiedApiExecutionBeforeCreatingAssets() {
        IngestionLineageWriter writer = new IngestionLineageWriter(
            datasetRepository,
            lineageRepository,
            lineageJobRepository,
            mappingRepository,
            dataSourceRepository,
            auditService
        );
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setConnectionId(UUID.randomUUID());
        mapping.setEnabled(true);
        mapping.setStreamNamespace("api:task-api-1");
        mapping.setStreamName("orders");
        mapping.setOdsSchema("ods");
        mapping.setOdsTable("ods_api_crm_orders");

        IngestionLineageWriter.LineageWriteResult result = writer.writeIngestionLineage(
            mapping,
            IngestionLineageWriter.LineageObservation.fromExecution("failed", "run-api-1", "batch-api-1", null),
            IngestionLineageWriter.RELATION_API
        );

        assertThat(result.status()).isEqualTo("api-execution-unverified");
        verify(datasetRepository, never()).save(any());
        verify(lineageRepository, never()).save(any());
    }
}
