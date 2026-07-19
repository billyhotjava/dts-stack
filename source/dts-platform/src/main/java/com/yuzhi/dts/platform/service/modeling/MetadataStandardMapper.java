package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;

final class MetadataStandardMapper {

    private MetadataStandardMapper() {}

    static MetadataStandardDto toDto(MetadataStandard entity) {
        MetadataStandardDto dto = new MetadataStandardDto();
        dto.setId(entity.getId());
        dto.setFieldNameCn(entity.getFieldNameCn());
        dto.setFieldNameEn(entity.getFieldNameEn());
        dto.setDataType(entity.getDataType());
        dto.setDataLength(entity.getDataLength());
        dto.setDataPrecision(entity.getDataPrecision());
        dto.setDataScale(entity.getDataScale());
        dto.setNullable(entity.getNullable());
        dto.setDomain(entity.getDomain());
        dto.setDescription(entity.getDescription());
        dto.setSourceSystem(entity.getSourceSystem());
        dto.setCodeSet(entity.getCodeSet());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setIsPk(entity.getIsPk());
        dto.setSecurityLevel(entity.getSecurityLevel());
        dto.setVersion(entity.getVersion());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        return dto;
    }
}
