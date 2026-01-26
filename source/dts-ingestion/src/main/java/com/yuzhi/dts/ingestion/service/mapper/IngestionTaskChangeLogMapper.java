package com.yuzhi.dts.ingestion.service.mapper;

import com.yuzhi.dts.ingestion.domain.IngestionTaskChangeLog;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskChangeLogDTO;
import org.springframework.stereotype.Component;

@Component
public class IngestionTaskChangeLogMapper {

    public IngestionTaskChangeLogDTO toDto(IngestionTaskChangeLog entity) {
        if (entity == null) {
            return null;
        }
        IngestionTaskChangeLogDTO dto = new IngestionTaskChangeLogDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setTaskName(entity.getTaskName());
        dto.setObjType(entity.getObjType());
        dto.setChangeType(entity.getChangeType());
        dto.setSummary(entity.getSummary());
        dto.setDetail(entity.getDetail());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setStatus(entity.getStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        return dto;
    }
}
