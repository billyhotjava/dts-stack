package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.service.governance.dto.DimensionDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
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
        dto.setDatasetId(entity.getDatasetId());
        dto.setOwner(entity.getOwner());
        dto.setOwnerDept(entity.getOwnerDept());
        dto.setDataLevel(entity.getDataLevel());
        dto.setStatus(entity.getStatus());
        dto.setVersion(entity.getVersion());
        dto.setVersionNotes(entity.getVersionNotes());
        dto.setTags(entity.getTags());
        dto.setLastValidationStatus(entity.getLastValidationStatus());
        dto.setLastValidationMessage(entity.getLastValidationMessage());
        dto.setLastValidatedAt(entity.getLastValidatedAt());
        dto.setLastValidationSignature(entity.getLastValidationSignature());
        // 计算定义
        dto.setAggregationType(entity.getAggregationType());
        dto.setMeasureField(entity.getMeasureField());
        dto.setNumeratorExpression(entity.getNumeratorExpression());
        dto.setDenominatorExpression(entity.getDenominatorExpression());
        dto.setStaticFilter(entity.getStaticFilter());
        dto.setDynamicFilterConfig(entity.getDynamicFilterConfig());
        dto.setIsDerived(entity.getIsDerived());
        dto.setDependencyIndicators(entity.getDependencyIndicators());
        dto.setWindowFunction(entity.getWindowFunction());
        // 维度与粒度
        dto.setDimensionFields(entity.getDimensionFields());
        dto.setDateColumn(entity.getDateColumn());
        dto.setTimeGrain(entity.getTimeGrain());
        dto.setGranularity(entity.getGranularity());
        // 数据绑定
        dto.setSourceTable(entity.getSourceTable());
        dto.setJoinConfig(entity.getJoinConfig());
        dto.setSourceLayer(entity.getSourceLayer());
        dto.setTargetLayer(entity.getTargetLayer());
        dto.setTargetModelName(entity.getTargetModelName());
        // 业务属性
        dto.setUnit(entity.getUnit());
        dto.setPrecisionScale(entity.getPrecisionScale());
        dto.setThresholdMin(entity.getThresholdMin());
        dto.setThresholdMax(entity.getThresholdMax());
        dto.setDirection(entity.getDirection());
        dto.setBusinessOwner(entity.getBusinessOwner());
        dto.setDataPrivacy(entity.getDataPrivacy());
        // LLM 预留
        dto.setLlmGenerated(entity.getLlmGenerated());
        dto.setLlmConfidence(entity.getLlmConfidence());
        dto.setLlmSourceRef(entity.getLlmSourceRef());
        dto.setHumanVerified(entity.getHumanVerified());
        // 管理
        dto.setDomain(entity.getDomain());
        dto.setIcon(entity.getIcon());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        return dto;
    }

    static IndicatorVersionDto toDto(GovIndicatorVersion entity) {
        if (entity == null) return null;
        IndicatorVersionDto dto = new IndicatorVersionDto();
        dto.setId(entity.getId());
        dto.setIndicatorId(entity.getIndicator() != null ? entity.getIndicator().getId() : null);
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus());
        dto.setChangeSummary(entity.getChangeSummary());
        dto.setReleasedAt(entity.getReleasedAt());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setSnapshotJson(entity.getSnapshotJson());
        return dto;
    }

    static void apply(GovIndicatorDefinition entity, IndicatorUpsertRequest request) {
        if (entity == null || request == null) return;
        entity.setCode(trimToNull(request.getCode()));
        entity.setName(trimToNull(request.getName()));
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setDefinition(trimToNull(request.getDefinition()));
        entity.setExpressionSql(trimToNull(request.getExpressionSql()));
        entity.setDatasetId(trimToNull(request.getDatasetId()));
        entity.setOwner(trimToNull(request.getOwner()));
        entity.setOwnerDept(trimToNull(request.getOwnerDept()));
        entity.setDataLevel(trimToNull(request.getDataLevel()));
        entity.setStatus(trimToNull(request.getStatus()));
        entity.setVersion(trimToNull(request.getVersion()));
        entity.setVersionNotes(trimToNull(request.getVersionNotes()));
        entity.setTags(trimToNull(request.getTags()));
        // 计算定义
        entity.setAggregationType(trimToNull(request.getAggregationType()));
        entity.setMeasureField(trimToNull(request.getMeasureField()));
        entity.setNumeratorExpression(trimToNull(request.getNumeratorExpression()));
        entity.setDenominatorExpression(trimToNull(request.getDenominatorExpression()));
        entity.setStaticFilter(trimToNull(request.getStaticFilter()));
        entity.setDynamicFilterConfig(trimToNull(request.getDynamicFilterConfig()));
        entity.setIsDerived(request.getIsDerived());
        entity.setDependencyIndicators(trimToNull(request.getDependencyIndicators()));
        entity.setWindowFunction(trimToNull(request.getWindowFunction()));
        // 维度与粒度
        entity.setDimensionFields(trimToNull(request.getDimensionFields()));
        entity.setDateColumn(trimToNull(request.getDateColumn()));
        entity.setTimeGrain(trimToNull(request.getTimeGrain()));
        entity.setGranularity(trimToNull(request.getGranularity()));
        // 数据绑定
        entity.setSourceTable(trimToNull(request.getSourceTable()));
        entity.setJoinConfig(trimToNull(request.getJoinConfig()));
        entity.setSourceLayer(trimToNull(request.getSourceLayer()));
        entity.setTargetLayer(trimToNull(request.getTargetLayer()));
        entity.setTargetModelName(trimToNull(request.getTargetModelName()));
        // 业务属性
        entity.setUnit(trimToNull(request.getUnit()));
        entity.setPrecisionScale(request.getPrecisionScale());
        entity.setThresholdMin(request.getThresholdMin());
        entity.setThresholdMax(request.getThresholdMax());
        entity.setDirection(trimToNull(request.getDirection()));
        entity.setBusinessOwner(trimToNull(request.getBusinessOwner()));
        entity.setDataPrivacy(trimToNull(request.getDataPrivacy()));
        // LLM 预留
        entity.setLlmGenerated(request.getLlmGenerated());
        entity.setLlmConfidence(request.getLlmConfidence());
        entity.setLlmSourceRef(trimToNull(request.getLlmSourceRef()));
        entity.setHumanVerified(request.getHumanVerified());
        // 管理
        entity.setDomain(trimToNull(request.getDomain()));
        entity.setIcon(trimToNull(request.getIcon()));
        entity.setDisplayOrder(request.getDisplayOrder());
        entity.setTemplateId(request.getTemplateId());
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
