package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CatalogDbtLineageServiceTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDatasetLineageRepository lineageRepository;

    @Mock
    private CatalogLineageJobRepository lineageJobRepository;

    @Mock
    private CatalogClassificationPropagationJobService propagationJobService;

    @Test
    void manifestImportWritesDbtLineageAndEnqueuesIdempotentClassificationPropagation() throws Exception {
        UUID upstreamId = UUID.randomUUID();
        UUID downstreamId = UUID.randomUUID();
        when(datasetRepository.findHiveTableAndIdProjection())
            .thenReturn(List.of(new Object[] { "source_orders", upstreamId }, new Object[] { "dwd_orders", downstreamId }));
        when(lineageRepository.findAll()).thenReturn(List.of());
        when(lineageJobRepository.findByJobKey("DBT:model.s72.dwd_orders"))
            .thenReturn(Optional.empty());
        when(lineageJobRepository.save(any(CatalogLineageJob.class))).thenAnswer(invocation -> {
            CatalogLineageJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        when(propagationJobService.enqueue(
                org.mockito.ArgumentMatchers.eq(downstreamId),
                org.mockito.ArgumentMatchers.eq("DBT"),
                org.mockito.ArgumentMatchers.startsWith("dbt-manifest:")
            ))
            .thenReturn(true);
        MockMultipartFile manifest = new MockMultipartFile(
            "file",
            "manifest.json",
            "application/json",
            """
            {
              "nodes":{
                "model.s72.dwd_orders":{
                  "name":"dwd_orders",
                  "schema":"dwd",
                  "original_file_path":"models/dwd_orders.sql",
                  "depends_on":{"nodes":["source.s72.source_orders"]}
                }
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var result = service().importManifest(manifest);

        assertThat(result)
            .containsEntry("created", 1)
            .containsEntry("propagationEnqueued", 1);
        ArgumentCaptor<List<com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage>> lineages =
            ArgumentCaptor.forClass(List.class);
        verify(lineageRepository).saveAll(lineages.capture());
        assertThat(lineages.getValue()).singleElement().satisfies(lineage -> {
            assertThat(lineage.getUpstreamDatasetId()).isEqualTo(upstreamId);
            assertThat(lineage.getDownstreamDatasetId()).isEqualTo(downstreamId);
            assertThat(lineage.getRelationType()).isEqualTo("DBT");
        });
        verify(propagationJobService).enqueue(
            org.mockito.ArgumentMatchers.eq(downstreamId),
            org.mockito.ArgumentMatchers.eq("DBT"),
            org.mockito.ArgumentMatchers.startsWith("dbt-manifest:")
        );
    }

    private CatalogDbtLineageService service() {
        return new CatalogDbtLineageService(
            datasetRepository,
            lineageRepository,
            lineageJobRepository,
            new ObjectMapper(),
            propagationJobService
        );
    }
}
