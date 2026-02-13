package com.yuzhi.dts.ingestion.service.mapper;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
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
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setRowsRead(entity.getRowsRead());
        dto.setRowsWritten(entity.getRowsWritten());
        dto.setErrorMessage(entity.getErrorMessage());
        if (StringUtils.hasText(entity.getErrorMessage())) {
            String category = ExecutionFailureClassifier.classify(entity.getErrorMessage());
            dto.setFailureCategory(category);
            dto.setFailureAdvice(ExecutionFailureClassifier.advice(category));
        }
        dto.setLogPath(entity.getLogPath());
        dto.setReplaceMode(entity.getReplaceMode());
        dto.setDroppedTables(entity.getDroppedTables());
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
        entity.setStatus(dto.getStatus());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setRowsRead(dto.getRowsRead());
        entity.setRowsWritten(dto.getRowsWritten());
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setLogPath(dto.getLogPath());
        entity.setReplaceMode(dto.getReplaceMode());
        entity.setDroppedTables(dto.getDroppedTables());
        entity.setCreatedAt(dto.getCreatedAt());

        return entity;
    }
}
