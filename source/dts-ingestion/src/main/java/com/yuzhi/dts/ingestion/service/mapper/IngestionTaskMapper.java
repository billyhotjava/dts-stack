package com.yuzhi.dts.ingestion.service.mapper;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper for the entity {@link IngestionTask} and its DTO {@link IngestionTaskDTO}.
 */
@Component
public class IngestionTaskMapper {

    public IngestionTaskDTO toDto(IngestionTask entity) {
        if (entity == null) {
            return null;
        }

        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceConfig(entity.getSourceConfig());
        dto.setSourceDataSourceId(entity.getSourceDataSourceId());
        dto.setDestinationType(entity.getDestinationType());
        dto.setDestinationConfig(entity.getDestinationConfig());
        dto.setSyncMode(entity.getSyncMode());
        dto.setSyncSchedule(entity.getSyncSchedule());
        dto.setTableMapping(entity.getTableMapping());
        dto.setSyncConfig(entity.getSyncConfig());
        dto.setAddaxJobPath(entity.getAddaxJobPath());
        dto.setAddaxConfig(entity.getAddaxConfig());
        dto.setAirflowEnabled(entity.getAirflowEnabled());
        dto.setAirflowDagId(entity.getAirflowDagId());
        dto.setDbtModelSelector(entity.getDbtModelSelector());
        dto.setDbtDagSelector(entity.getDbtDagSelector());
        dto.setStatus(entity.getStatus());
        dto.setLastExecutedAt(entity.getLastExecutedAt());
        dto.setLastExecutionStatus(entity.getLastExecutionStatus());
        
        // 审计字段
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());

        return dto;
    }

    public IngestionTask toEntity(IngestionTaskDTO dto) {
        if (dto == null) {
            return null;
        }

        IngestionTask entity = new IngestionTask();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSourceType(dto.getSourceType());
        entity.setSourceConfig(dto.getSourceConfig());
        entity.setSourceDataSourceId(dto.getSourceDataSourceId());
        entity.setDestinationType(dto.getDestinationType());
        entity.setDestinationConfig(dto.getDestinationConfig());
        entity.setSyncMode(dto.getSyncMode());
        entity.setSyncSchedule(dto.getSyncSchedule());
        entity.setTableMapping(dto.getTableMapping());
        entity.setSyncConfig(dto.getSyncConfig());
        entity.setAddaxJobPath(dto.getAddaxJobPath());
        entity.setAddaxConfig(dto.getAddaxConfig());
        entity.setAirflowEnabled(dto.getAirflowEnabled());
        entity.setAirflowDagId(dto.getAirflowDagId());
        entity.setDbtModelSelector(dto.getDbtModelSelector());
        entity.setDbtDagSelector(dto.getDbtDagSelector());
        entity.setStatus(dto.getStatus());
        entity.setLastExecutedAt(dto.getLastExecutedAt());
        entity.setLastExecutionStatus(dto.getLastExecutionStatus());

        return entity;
    }

    public void partialUpdate(IngestionTask entity, IngestionTaskDTO dto) {
        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getSourceType() != null) {
            entity.setSourceType(dto.getSourceType());
        }
        if (dto.getSourceConfig() != null) {
            entity.setSourceConfig(dto.getSourceConfig());
        }
        if (dto.getSourceDataSourceId() != null) {
            entity.setSourceDataSourceId(dto.getSourceDataSourceId());
        }
        if (dto.getDestinationType() != null) {
            entity.setDestinationType(dto.getDestinationType());
        }
        if (dto.getDestinationConfig() != null) {
            entity.setDestinationConfig(dto.getDestinationConfig());
        }
        if (dto.getSyncMode() != null) {
            entity.setSyncMode(dto.getSyncMode());
        }
        if (dto.getSyncSchedule() != null) {
            entity.setSyncSchedule(dto.getSyncSchedule());
        }
        if (dto.getTableMapping() != null) {
            entity.setTableMapping(dto.getTableMapping());
        }
        if (dto.getSyncConfig() != null) {
            entity.setSyncConfig(dto.getSyncConfig());
        }
        if (dto.getAddaxConfig() != null) {
            entity.setAddaxConfig(dto.getAddaxConfig());
        }
        if (dto.getAirflowEnabled() != null) {
            entity.setAirflowEnabled(dto.getAirflowEnabled());
        }
        if (dto.getAirflowDagId() != null) {
            entity.setAirflowDagId(dto.getAirflowDagId());
        }
        if (dto.getDbtModelSelector() != null) {
            entity.setDbtModelSelector(dto.getDbtModelSelector());
        }
        if (dto.getDbtDagSelector() != null) {
            entity.setDbtDagSelector(dto.getDbtDagSelector());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }
}
