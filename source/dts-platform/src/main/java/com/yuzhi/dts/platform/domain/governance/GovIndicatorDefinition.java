package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gov_indicator_definition")
public class GovIndicatorDefinition extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, unique = true)
    private String code;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "business_category_id", columnDefinition = "uuid")
    private UUID businessCategoryId;

    @Column(name = "data_domain_id", columnDefinition = "uuid")
    private UUID dataDomainId;

    @Column(name = "business_process_id", columnDefinition = "uuid")
    private UUID businessProcessId;

    @Column(name = "metric_type", length = 16)
    private String metricType;

    @Column(name = "metric_group_code", length = 64)
    private String metricGroupCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", columnDefinition = "jsonb")
    private String sourceRefs;

    @Column(name = "definition")
    private String definition;

    @Column(name = "expression_sql")
    private String expressionSql;

    @Column(name = "dataset_id", length = 64)
    private String datasetId;

    @Column(name = "owner", length = 128)
    private String owner;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "data_level", length = 32)
    private String dataLevel;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "version_notes")
    private String versionNotes;

    @Column(name = "tags")
    private String tags;

    @Column(name = "last_validation_status", length = 32)
    private String lastValidationStatus;

    @Column(name = "last_validation_message")
    private String lastValidationMessage;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_validation_signature", length = 64)
    private String lastValidationSignature;

    // --- 计算定义 ---

    @Column(name = "aggregation_type", length = 16)
    private String aggregationType;

    @Column(name = "measure_field", length = 200)
    private String measureField;

    @Column(name = "numerator_expression", columnDefinition = "TEXT")
    private String numeratorExpression;

    @Column(name = "denominator_expression", columnDefinition = "TEXT")
    private String denominatorExpression;

    @Column(name = "static_filter", columnDefinition = "TEXT")
    private String staticFilter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dynamic_filter_config", columnDefinition = "jsonb")
    private String dynamicFilterConfig;

    @Column(name = "is_derived")
    private Boolean isDerived;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependency_indicators", columnDefinition = "jsonb")
    private String dependencyIndicators;

    @Column(name = "window_function", length = 16)
    private String windowFunction;

    // --- 维度与粒度 ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimension_fields", columnDefinition = "jsonb")
    private String dimensionFields;

    @Column(name = "date_column", length = 200)
    private String dateColumn;

    @Column(name = "time_grain", length = 16)
    private String timeGrain;

    @Column(name = "granularity", length = 64)
    private String granularity;

    // --- 数据绑定 ---

    @Column(name = "source_table", length = 200)
    private String sourceTable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "join_config", columnDefinition = "jsonb")
    private String joinConfig;

    @Column(name = "source_layer", length = 8)
    private String sourceLayer;

    @Column(name = "target_layer", length = 8)
    private String targetLayer;

    @Column(name = "target_model_name", length = 200)
    private String targetModelName;

    // --- 业务属性 ---

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "precision_scale")
    private Integer precisionScale;

    @Column(name = "threshold_min")
    private BigDecimal thresholdMin;

    @Column(name = "threshold_max")
    private BigDecimal thresholdMax;

    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "business_owner", length = 64)
    private String businessOwner;

    @Column(name = "data_privacy", length = 16)
    private String dataPrivacy;

    // --- LLM 预留 ---

    @Column(name = "llm_generated")
    private Boolean llmGenerated;

    @Column(name = "llm_confidence")
    private Float llmConfidence;

    @Column(name = "llm_source_ref", columnDefinition = "TEXT")
    private String llmSourceRef;

    @Column(name = "human_verified")
    private Boolean humanVerified;

    // --- 管理 ---

    @Column(name = "domain", length = 32)
    private String domain;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "template_id", columnDefinition = "uuid")
    private UUID templateId;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public UUID getBusinessCategoryId() {
        return businessCategoryId;
    }

    public void setBusinessCategoryId(UUID businessCategoryId) {
        this.businessCategoryId = businessCategoryId;
    }

    public UUID getDataDomainId() {
        return dataDomainId;
    }

    public void setDataDomainId(UUID dataDomainId) {
        this.dataDomainId = dataDomainId;
    }

    public UUID getBusinessProcessId() {
        return businessProcessId;
    }

    public void setBusinessProcessId(UUID businessProcessId) {
        this.businessProcessId = businessProcessId;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getMetricGroupCode() {
        return metricGroupCode;
    }

    public void setMetricGroupCode(String metricGroupCode) {
        this.metricGroupCode = metricGroupCode;
    }

    public String getSourceRefs() {
        return sourceRefs;
    }

    public void setSourceRefs(String sourceRefs) {
        this.sourceRefs = sourceRefs;
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

    public String getLastValidationStatus() {
        return lastValidationStatus;
    }

    public void setLastValidationStatus(String lastValidationStatus) {
        this.lastValidationStatus = lastValidationStatus;
    }

    public String getLastValidationMessage() {
        return lastValidationMessage;
    }

    public void setLastValidationMessage(String lastValidationMessage) {
        this.lastValidationMessage = lastValidationMessage;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getLastValidationSignature() {
        return lastValidationSignature;
    }

    public void setLastValidationSignature(String lastValidationSignature) {
        this.lastValidationSignature = lastValidationSignature;
    }

    // --- 计算定义 getters/setters ---

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

    // --- 维度与粒度 getters/setters ---

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

    // --- 数据绑定 getters/setters ---

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

    // --- 业务属性 getters/setters ---

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

    // --- LLM 预留 getters/setters ---

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

    // --- 管理 getters/setters ---

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
}
