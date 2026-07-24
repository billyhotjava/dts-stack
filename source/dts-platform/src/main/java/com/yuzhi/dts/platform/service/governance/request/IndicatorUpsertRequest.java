package com.yuzhi.dts.platform.service.governance.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class IndicatorUpsertRequest {

    private String code;
    private String name;
    private String category;
    private String definition;
    private String expressionSql;
    private String datasetId;
    private String owner;
    private String ownerDept;
    private String dataLevel;
    private String status;
    private String version;
    private String versionNotes;
    private String tags;

    // 计算定义
    private String aggregationType;
    private String measureField;
    private String numeratorExpression;
    private String denominatorExpression;
    private String staticFilter;
    private String dynamicFilterConfig;
    private Boolean isDerived;
    private String dependencyIndicators;
    private String windowFunction;

    // 维度与粒度
    private String dimensionFields;
    private String dateColumn;
    private String timeGrain;
    private String granularity;

    // 数据绑定
    private String sourceTable;
    private String joinConfig;
    private String sourceLayer;
    private String targetLayer;
    private String targetModelName;

    // 业务属性
    private String unit;
    private Integer precisionScale;
    private BigDecimal thresholdMin;
    private BigDecimal thresholdMax;
    private String direction;
    private String businessOwner;
    private String dataPrivacy;

    // LLM 预留
    private Boolean llmGenerated;
    private Float llmConfidence;
    private String llmSourceRef;
    private Boolean humanVerified;

    // 管理
    private String domain;
    private String icon;
    private Integer displayOrder;
    private UUID templateId;
    private Instant expectedLastModifiedDate;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getExpressionSql() {
        return expressionSql;
    }

    public void setExpressionSql(String expressionSql) {
        this.expressionSql = expressionSql;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionNotes() {
        return versionNotes;
    }

    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    // --- 计算定义 ---

    public String getAggregationType() {
        return aggregationType;
    }

    public void setAggregationType(String aggregationType) {
        this.aggregationType = aggregationType;
    }

    public String getMeasureField() {
        return measureField;
    }

    public void setMeasureField(String measureField) {
        this.measureField = measureField;
    }

    public String getNumeratorExpression() {
        return numeratorExpression;
    }

    public void setNumeratorExpression(String numeratorExpression) {
        this.numeratorExpression = numeratorExpression;
    }

    public String getDenominatorExpression() {
        return denominatorExpression;
    }

    public void setDenominatorExpression(String denominatorExpression) {
        this.denominatorExpression = denominatorExpression;
    }

    public String getStaticFilter() {
        return staticFilter;
    }

    public void setStaticFilter(String staticFilter) {
        this.staticFilter = staticFilter;
    }

    public String getDynamicFilterConfig() {
        return dynamicFilterConfig;
    }

    public void setDynamicFilterConfig(String dynamicFilterConfig) {
        this.dynamicFilterConfig = dynamicFilterConfig;
    }

    public Boolean getIsDerived() {
        return isDerived;
    }

    public void setIsDerived(Boolean isDerived) {
        this.isDerived = isDerived;
    }

    public String getDependencyIndicators() {
        return dependencyIndicators;
    }

    public void setDependencyIndicators(String dependencyIndicators) {
        this.dependencyIndicators = dependencyIndicators;
    }

    public String getWindowFunction() {
        return windowFunction;
    }

    public void setWindowFunction(String windowFunction) {
        this.windowFunction = windowFunction;
    }

    // --- 维度与粒度 ---

    public String getDimensionFields() {
        return dimensionFields;
    }

    public void setDimensionFields(String dimensionFields) {
        this.dimensionFields = dimensionFields;
    }

    public String getDateColumn() {
        return dateColumn;
    }

    public void setDateColumn(String dateColumn) {
        this.dateColumn = dateColumn;
    }

    public String getTimeGrain() {
        return timeGrain;
    }

    public void setTimeGrain(String timeGrain) {
        this.timeGrain = timeGrain;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }

    // --- 数据绑定 ---

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getJoinConfig() {
        return joinConfig;
    }

    public void setJoinConfig(String joinConfig) {
        this.joinConfig = joinConfig;
    }

    public String getSourceLayer() {
        return sourceLayer;
    }

    public void setSourceLayer(String sourceLayer) {
        this.sourceLayer = sourceLayer;
    }

    public String getTargetLayer() {
        return targetLayer;
    }

    public void setTargetLayer(String targetLayer) {
        this.targetLayer = targetLayer;
    }

    public String getTargetModelName() {
        return targetModelName;
    }

    public void setTargetModelName(String targetModelName) {
        this.targetModelName = targetModelName;
    }

    // --- 业务属性 ---

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getPrecisionScale() {
        return precisionScale;
    }

    public void setPrecisionScale(Integer precisionScale) {
        this.precisionScale = precisionScale;
    }

    public BigDecimal getThresholdMin() {
        return thresholdMin;
    }

    public void setThresholdMin(BigDecimal thresholdMin) {
        this.thresholdMin = thresholdMin;
    }

    public BigDecimal getThresholdMax() {
        return thresholdMax;
    }

    public void setThresholdMax(BigDecimal thresholdMax) {
        this.thresholdMax = thresholdMax;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getBusinessOwner() {
        return businessOwner;
    }

    public void setBusinessOwner(String businessOwner) {
        this.businessOwner = businessOwner;
    }

    public String getDataPrivacy() {
        return dataPrivacy;
    }

    public void setDataPrivacy(String dataPrivacy) {
        this.dataPrivacy = dataPrivacy;
    }

    // --- LLM 预留 ---

    public Boolean getLlmGenerated() {
        return llmGenerated;
    }

    public void setLlmGenerated(Boolean llmGenerated) {
        this.llmGenerated = llmGenerated;
    }

    public Float getLlmConfidence() {
        return llmConfidence;
    }

    public void setLlmConfidence(Float llmConfidence) {
        this.llmConfidence = llmConfidence;
    }

    public String getLlmSourceRef() {
        return llmSourceRef;
    }

    public void setLlmSourceRef(String llmSourceRef) {
        this.llmSourceRef = llmSourceRef;
    }

    public Boolean getHumanVerified() {
        return humanVerified;
    }

    public void setHumanVerified(Boolean humanVerified) {
        this.humanVerified = humanVerified;
    }

    // --- 管理 ---

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public Instant getExpectedLastModifiedDate() {
        return expectedLastModifiedDate;
    }

    public void setExpectedLastModifiedDate(Instant expectedLastModifiedDate) {
        this.expectedLastModifiedDate = expectedLastModifiedDate;
    }
}
