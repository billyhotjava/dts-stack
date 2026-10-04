package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QualityWorkflowQueryService {

    private static final int MAX_LIST_LIMIT = 200;

    private final GovQualityWorkflowRunRepository workflowRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final QualityDatasetReadGuard datasetReadGuard;
    private final QualityRunService qualityRunService;

    public QualityWorkflowQueryService(
        GovQualityWorkflowRunRepository workflowRepository,
        CatalogDatasetRepository datasetRepository,
        QualityDatasetReadGuard datasetReadGuard,
        QualityRunService qualityRunService
    ) {
        this.workflowRepository = workflowRepository;
        this.datasetRepository = datasetRepository;
        this.datasetReadGuard = datasetReadGuard;
        this.qualityRunService = qualityRunService;
    }

    public List<QualityWorkflowRunDto> list(
        UUID taskId,
        UUID datasetId,
        String status,
        int limit,
        String activeDeptHeader
    ) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
        PageRequest pageable = PageRequest.of(0, safeLimit, Sort.Direction.DESC, "createdDate");
        String normalizedStatus = StringUtils.trimToNull(status);
        if (taskId != null) {
            List<GovQualityWorkflowRun> candidates = normalizedStatus == null
                ? workflowRepository.findByTaskIdOrderByCreatedDateDesc(taskId, pageable)
                : workflowRepository.findByTaskIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
                    taskId,
                    normalizedStatus,
                    pageable
                );
            Set<UUID> readableIds = readableDatasetIds(candidates, activeDeptHeader);
            return mapReadable(candidates, readableIds);
        }
        if (datasetId != null) {
            datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
            List<GovQualityWorkflowRun> candidates = normalizedStatus == null
                ? workflowRepository.findByDatasetIdOrderByCreatedDateDesc(datasetId, pageable)
                : workflowRepository.findByDatasetIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
                    datasetId,
                    normalizedStatus,
                    pageable
                );
            return candidates.stream().map(workflow -> QualityWorkflowMapper.toDto(workflow, List.of())).toList();
        }
        List<UUID> workflowDatasetIds = workflowRepository.findDistinctDatasetIds();
        if (workflowDatasetIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> readableIds = datasetReadGuard.readableDatasetIds(
            datasetRepository.findAllById(workflowDatasetIds),
            activeDeptHeader
        );
        if (readableIds.isEmpty()) {
            return List.of();
        }
        List<GovQualityWorkflowRun> candidates = normalizedStatus == null
            ? workflowRepository.findByDatasetIdInOrderByCreatedDateDesc(readableIds, pageable)
            : workflowRepository.findByDatasetIdInAndStatusIgnoreCaseOrderByCreatedDateDesc(
                readableIds,
                normalizedStatus,
                pageable
            );
        return candidates.stream().map(workflow -> QualityWorkflowMapper.toDto(workflow, List.of())).toList();
    }

    public QualityWorkflowRunDto get(UUID workflowRunId, String activeDeptHeader) {
        GovQualityWorkflowRun workflow = workflowRepository
            .findById(workflowRunId)
            .orElseThrow(() -> new EntityNotFoundException("质量工作流不存在"));
        datasetReadGuard.requireReadable(workflow.getDatasetId(), activeDeptHeader);
        return QualityWorkflowMapper.toDto(
            workflow,
            qualityRunService.runsByWorkflow(workflowRunId, activeDeptHeader)
        );
    }

    private Set<UUID> readableDatasetIds(List<GovQualityWorkflowRun> workflows, String activeDeptHeader) {
        Set<UUID> datasetIds = workflows
            .stream()
            .map(GovQualityWorkflowRun::getDatasetId)
            .collect(java.util.stream.Collectors.toSet());
        if (datasetIds.isEmpty()) {
            return Set.of();
        }
        List<CatalogDataset> datasets = datasetRepository.findAllById(datasetIds);
        return datasetReadGuard.readableDatasetIds(datasets, activeDeptHeader);
    }

    private List<QualityWorkflowRunDto> mapReadable(
        List<GovQualityWorkflowRun> candidates,
        Set<UUID> readableIds
    ) {
        return candidates
            .stream()
            .filter(workflow -> readableIds.contains(workflow.getDatasetId()))
            .map(workflow -> QualityWorkflowMapper.toDto(workflow, List.of()))
            .toList();
    }
}
