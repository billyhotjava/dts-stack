package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class QualityWorkflowQueryServiceTest {

    @Mock private GovQualityWorkflowRunRepository workflowRepository;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private QualityDatasetReadGuard datasetReadGuard;
    @Mock private QualityRunService qualityRunService;

    @Test
    void appliesAssetVisibilityBeforeTheGlobalResultLimit() {
        UUID readableId = UUID.fromString("20000000-0000-0000-0000-000000000120");
        UUID hiddenId = UUID.fromString("20000000-0000-0000-0000-000000000121");
        CatalogDataset readable = new CatalogDataset();
        readable.setId(readableId);
        CatalogDataset hidden = new CatalogDataset();
        hidden.setId(hiddenId);
        GovQualityWorkflowRun visibleWorkflow = new GovQualityWorkflowRun();
        visibleWorkflow.setId(UUID.fromString("40000000-0000-0000-0000-000000000120"));
        visibleWorkflow.setDatasetId(readableId);
        visibleWorkflow.setStatus("FAILED");
        when(workflowRepository.findDistinctDatasetIds()).thenReturn(List.of(hiddenId, readableId));
        when(datasetRepository.findAllById(List.of(hiddenId, readableId))).thenReturn(List.of(hidden, readable));
        when(datasetReadGuard.readableDatasetIds(any(), eq("DEPT-A"))).thenReturn(Set.of(readableId));
        when(
            workflowRepository.findByDatasetIdInAndStatusIgnoreCaseOrderByCreatedDateDesc(
                eq(Set.of(readableId)),
                eq("FAILED"),
                any(Pageable.class)
            )
        )
            .thenReturn(List.of(visibleWorkflow));
        QualityWorkflowQueryService service = new QualityWorkflowQueryService(
            workflowRepository,
            datasetRepository,
            datasetReadGuard,
            qualityRunService
        );

        var result = service.list(null, null, "FAILED", 1, "DEPT-A");

        assertThat(result).singleElement().extracting(item -> item.datasetId()).isEqualTo(readableId);
        verify(workflowRepository, never()).findAll(any(Pageable.class));
    }
}
