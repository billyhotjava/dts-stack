package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    @Captor
    private ArgumentCaptor<InfraOdsTableMapping> mappingCaptor;

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
            new ObjectMapper()
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
            )
        );

        OdsTableMappingSyncService.SyncResult result = service.syncFromIngestionPayload(payload);

        assertThat(result.tables()).isEqualTo(1);
        assertThat(mappingCaptor.getValue().getOdsSchema()).isEqualTo("public");
        assertThat(mappingCaptor.getValue().getOdsTable()).isEqualTo("ods_prj_prjtest2000");
    }
}
