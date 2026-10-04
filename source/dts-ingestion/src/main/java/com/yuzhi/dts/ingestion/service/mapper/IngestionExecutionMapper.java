package com.yuzhi.dts.ingestion.service.mapper;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mapper for the entity {@link IngestionExecution} and its DTO {@link IngestionExecutionDTO}.
 */
@Component
public class IngestionExecutionMapper {

    public IngestionExecutionDTO toDto(IngestionExecution entity) {
        if (entity == null) {
            return null;
        }

        IngestionExecutionDTO dto = new IngestionExecutionDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTask() != null ? entity.getTask().getId() : null);
        dto.setTaskName(entity.getTask() != null ? entity.getTask().getName() : null);
        dto.setExecutionId(entity.getExecutionId());
        dto.setRevisionNumber(entity.getRevisionNumber());
        dto.setEffectiveConfigChecksum(entity.getEffectiveConfigChecksum());
        dto.setQualityPolicyRef(entity.getQualityPolicyRef());
        dto.setTargetDatasetId(entity.getTargetDatasetId());
        dto.setQualityRunId(entity.getQualityRunId());
        dto.setQualityWorkflowId(entity.getQualityWorkflowId());
        dto.setQualityWorkflowStatus(entity.getQualityWorkflowStatus());
        dto.setQualityWorkflowAttemptCount(entity.getQualityWorkflowAttemptCount());
        dto.setQualityWorkflowNextRetryAt(entity.getQualityWorkflowNextRetryAt());
        dto.setQualityWorkflowError(entity.getQualityWorkflowError());
        dto.setAirflowDagId(entity.getAirflowDagId());
        dto.setBatchId(entity.getBatchId());
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setRowsRead(entity.getRowsRead());
        dto.setRowsWritten(entity.getRowsWritten());
        dto.setErrorMessage(entity.getErrorMessage());

        String failureCategory = entity.getFailureCategory();
        String failureAdvice = entity.getFailureAdvice();
        if (!StringUtils.hasText(failureCategory) && StringUtils.hasText(entity.getErrorMessage())) {
            failureCategory = ExecutionFailureClassifier.classify(entity.getErrorMessage());
        }
        if (!StringUtils.hasText(failureAdvice) && StringUtils.hasText(failureCategory)) {
            failureAdvice = ExecutionFailureClassifier.advice(failureCategory);
        }
        dto.setFailureCategory(failureCategory);
        dto.setFailureAdvice(failureAdvice);

        dto.setLogPath(entity.getLogPath());
        dto.setReplaceMode(entity.getReplaceMode());
        dto.setTriggerMode(entity.getTriggerMode());
        dto.setBackfillWindowStart(entity.getBackfillWindowStart());
        dto.setBackfillWindowEnd(entity.getBackfillWindowEnd());
        dto.setBackfillColumn(entity.getBackfillColumn());
        dto.setDroppedTables(entity.getDroppedTables());
        dto.setSourceTables(entity.getSourceTables());
        dto.setTargetTables(entity.getTargetTables());
        dto.setQueueWaitSeconds(resolveQueueWaitSeconds(entity));
        dto.setParentExecutionId(entity.getParentExecutionId());
        dto.setRetryCount(entity.getRetryCount());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setCreatedAt(entity.getCreatedAt());

        return dto;
    }

    public IngestionExecution toEntity(IngestionExecutionDTO dto) {
        if (dto == null) {
            return null;
        }

        IngestionExecution entity = new IngestionExecution();
        entity.setId(dto.getId());
        entity.setExecutionId(dto.getExecutionId());
        entity.setRevisionNumber(dto.getRevisionNumber());
        entity.setEffectiveConfigChecksum(dto.getEffectiveConfigChecksum());
        entity.setQualityPolicyRef(dto.getQualityPolicyRef());
        entity.setTargetDatasetId(dto.getTargetDatasetId());
        entity.setQualityRunId(dto.getQualityRunId());
        entity.setQualityWorkflowId(dto.getQualityWorkflowId());
        entity.setQualityWorkflowStatus(dto.getQualityWorkflowStatus());
        entity.setQualityWorkflowAttemptCount(dto.getQualityWorkflowAttemptCount());
        entity.setQualityWorkflowNextRetryAt(dto.getQualityWorkflowNextRetryAt());
        entity.setQualityWorkflowError(dto.getQualityWorkflowError());
        entity.setAirflowDagId(dto.getAirflowDagId());
        entity.setBatchId(dto.getBatchId());
        entity.setStatus(dto.getStatus());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setRowsRead(dto.getRowsRead());
        entity.setRowsWritten(dto.getRowsWritten());
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setFailureCategory(dto.getFailureCategory());
        entity.setFailureAdvice(dto.getFailureAdvice());
        entity.setLogPath(dto.getLogPath());
        entity.setReplaceMode(dto.getReplaceMode());
        entity.setTriggerMode(dto.getTriggerMode());
        entity.setBackfillWindowStart(dto.getBackfillWindowStart());
        entity.setBackfillWindowEnd(dto.getBackfillWindowEnd());
        entity.setBackfillColumn(dto.getBackfillColumn());
        entity.setDroppedTables(dto.getDroppedTables());
        entity.setSourceTables(dto.getSourceTables());
        entity.setTargetTables(dto.getTargetTables());
        entity.setParentExecutionId(dto.getParentExecutionId());
        entity.setRetryCount(dto.getRetryCount());
        entity.setMaxRetries(dto.getMaxRetries());
        entity.setCreatedAt(dto.getCreatedAt());

        return entity;
    }

    private Long resolveQueueWaitSeconds(IngestionExecution entity) {
        if (entity == null || entity.getCreatedAt() == null) {
            return null;
        }
        Instant start = entity.getStartTime();
        if (start != null) {
            long seconds = Duration.between(entity.getCreatedAt(), start).getSeconds();
            return Math.max(0L, seconds);
        }
        if ("preparing".equalsIgnoreCase(entity.getStatus())) {
            long seconds = Duration.between(entity.getCreatedAt(), Instant.now()).getSeconds();
            return Math.max(0L, seconds);
        }
        return null;
    }
}
