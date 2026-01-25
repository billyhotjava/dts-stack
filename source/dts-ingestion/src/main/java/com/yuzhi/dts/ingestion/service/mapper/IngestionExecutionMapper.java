package com.yuzhi.dts.ingestion.service.mapper;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import org.springframework.stereotype.Component;

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
        dto.setLogPath(entity.getLogPath());
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
        entity.setCreatedAt(dto.getCreatedAt());

        return entity;
    }
}
