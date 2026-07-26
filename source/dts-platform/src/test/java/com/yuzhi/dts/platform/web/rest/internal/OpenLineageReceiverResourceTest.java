package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationJobService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenLineageReceiverResourceTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDatasetLineageRepository lineageRepository;

    @Mock
    private CatalogLineageJobRepository lineageJobRepository;

    @Mock
    private CatalogClassificationService classificationService;

    @Mock
    private CatalogClassificationPropagationJobService propagationJobService;

    @Test
    void completedAirflowOpenLineageRunPersistsEdgeAndEnqueuesDownstreamClassificationPropagation() {
        CatalogDataset input = dataset("ods", "ods_customer", "SECRET");
        CatalogDataset output = dataset("dwd", "dwd_customer", "INTERNAL");
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_customer"))
            .thenReturn(List.of(input));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "dwd_customer"))
            .thenReturn(List.of(output));
        when(lineageJobRepository.findByJobKey("OPENLINEAGE:airflow:s72_classification_dag"))
            .thenReturn(Optional.empty());
        when(lineageJobRepository.save(any(CatalogLineageJob.class))).thenAnswer(invocation -> {
            CatalogLineageJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        when(
            lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                input.getId(),
                output.getId(),
                "AIRFLOW"
            )
        ).thenReturn(Optional.empty());
        when(lineageRepository.save(any(CatalogDatasetLineage.class))).thenAnswer(invocation -> {
            CatalogDatasetLineage edge = invocation.getArgument(0);
            edge.setId(UUID.randomUUID());
            return edge;
        });
        when(propagationJobService.enqueue(output.getId(), "OPENLINEAGE", "run-s72"))
            .thenReturn(true);

        var response = resource().receive(
            Map.of(
                "eventType",
                "COMPLETE",
                "eventTime",
                "2026-07-26T08:00:00Z",
                "run",
                Map.of("runId", "run-s72"),
                "job",
                Map.of("namespace", "airflow", "name", "s72_classification_dag"),
                "inputs",
                List.of(datasetFacet("ods.ods_customer", "SECRET")),
                "outputs",
                List.of(datasetFacet("dwd.dwd_customer", "INTERNAL"))
            )
        );

        assertThat(response.getBody())
            .containsEntry("created", 1)
            .containsEntry("propagationEnqueued", 1)
            .containsEntry("eventType", "COMPLETE")
            .containsEntry("runId", "run-s72");
        ArgumentCaptor<CatalogDatasetLineage> edge =
            ArgumentCaptor.forClass(CatalogDatasetLineage.class);
        verify(lineageRepository).save(edge.capture());
        assertThat(edge.getValue()).satisfies(saved -> {
            assertThat(saved.getUpstreamDatasetId()).isEqualTo(input.getId());
            assertThat(saved.getDownstreamDatasetId()).isEqualTo(output.getId());
            assertThat(saved.getRelationType()).isEqualTo("AIRFLOW");
            assertThat(saved.getVerificationStatus()).isEqualTo("VERIFIED");
        });
        verify(propagationJobService).enqueue(output.getId(), "OPENLINEAGE", "run-s72");
    }

    @Test
    void failedAirflowRunRecordsLineageButCannotTriggerClassificationPropagation() {
        CatalogDataset input = dataset("ods", "ods_customer", "SECRET");
        CatalogDataset output = dataset("dwd", "dwd_customer", "INTERNAL");
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_customer"))
            .thenReturn(List.of(input));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "dwd_customer"))
            .thenReturn(List.of(output));
        when(lineageJobRepository.findByJobKey("OPENLINEAGE:airflow:s72_failed_dag"))
            .thenReturn(Optional.empty());
        when(lineageJobRepository.save(any(CatalogLineageJob.class))).thenAnswer(invocation -> {
            CatalogLineageJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        when(
            lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                input.getId(),
                output.getId(),
                "AIRFLOW"
            )
        ).thenReturn(Optional.empty());

        var response = resource().receive(
            Map.of(
                "eventType",
                "FAIL",
                "run",
                Map.of("runId", "run-failed"),
                "job",
                Map.of("namespace", "airflow", "name", "s72_failed_dag"),
                "inputs",
                List.of(datasetFacet("ods.ods_customer", "SECRET")),
                "outputs",
                List.of(datasetFacet("dwd.dwd_customer", "INTERNAL"))
            )
        );

        assertThat(response.getBody()).containsEntry("propagationEnqueued", 0);
        org.mockito.Mockito.verify(propagationJobService, org.mockito.Mockito.never())
            .enqueue(any(), eq("OPENLINEAGE"), any());
    }

    private OpenLineageReceiverResource resource() {
        return new OpenLineageReceiverResource(
            datasetRepository,
            lineageRepository,
            lineageJobRepository,
            classificationService,
            propagationJobService
        );
    }

    private CatalogDataset dataset(String schema, String table, String classification) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName(table);
        dataset.setType("POSTGRESQL");
        dataset.setHiveDatabase(schema);
        dataset.setHiveTable(table);
        dataset.setClassification(classification);
        dataset.setEnabled(Boolean.TRUE);
        return dataset;
    }

    private Map<String, Object> datasetFacet(String name, String classification) {
        return Map.of(
            "namespace",
            "postgresql",
            "name",
            name,
            "facets",
            Map.of("classification", Map.of("level", classification))
        );
    }
}
