package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.service.governance.dto.DimensionDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.DimensionUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import org.springframework.util.StringUtils;

final class IndicatorMapper {

    private IndicatorMapper() {}

    static IndicatorDto toDto(GovIndicatorDefinition entity) {
        if (entity == null) return null;
        IndicatorDto dto = new IndicatorDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setCategory(entity.getCategory());
        dto.setDefinition(entity.getDefinition());
        dto.setExpressionSql(entity.getExpressionSql());
        dto.setOwner(entity.getOwner());
        dto.setOwnerDept(entity.getOwnerDept());
        dto.setDataLevel(entity.getDataLevel());
        dto.setStatus(entity.getStatus());
        dto.setVersion(entity.getVersion());
        dto.setVersionNotes(entity.getVersionNotes());
        dto.setTags(entity.getTags());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        return dto;
    }

    static void apply(GovIndicatorDefinition entity, IndicatorUpsertRequest request) {
        if (entity == null || request == null) return;
        entity.setCode(trimToNull(request.getCode()));
        entity.setName(trimToNull(request.getName()));
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setDefinition(trimToNull(request.getDefinition()));
        entity.setExpressionSql(trimToNull(request.getExpressionSql()));
        entity.setOwner(trimToNull(request.getOwner()));
        entity.setOwnerDept(trimToNull(request.getOwnerDept()));
        entity.setDataLevel(trimToNull(request.getDataLevel()));
        entity.setStatus(trimToNull(request.getStatus()));
        entity.setVersion(trimToNull(request.getVersion()));
        entity.setVersionNotes(trimToNull(request.getVersionNotes()));
        entity.setTags(trimToNull(request.getTags()));
    }

    static DimensionDto toDto(GovDimensionDictionary entity) {
        if (entity == null) return null;
        DimensionDto dto = new DimensionDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setOwner(entity.getOwner());
        dto.setOwnerDept(entity.getOwnerDept());
        dto.setDataLevel(entity.getDataLevel());
        dto.setStatus(entity.getStatus());
        dto.setTags(entity.getTags());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        return dto;
    }

    static void apply(GovDimensionDictionary entity, DimensionUpsertRequest request) {
        if (entity == null || request == null) return;
        entity.setCode(trimToNull(request.getCode()));
        entity.setName(trimToNull(request.getName()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setOwner(trimToNull(request.getOwner()));
        entity.setOwnerDept(trimToNull(request.getOwnerDept()));
        entity.setDataLevel(trimToNull(request.getDataLevel()));
        entity.setStatus(trimToNull(request.getStatus()));
        entity.setTags(trimToNull(request.getTags()));
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

