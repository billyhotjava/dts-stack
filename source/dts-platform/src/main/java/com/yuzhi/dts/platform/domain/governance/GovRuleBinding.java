package com.yuzhi.dts.platform.domain.governance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "gov_rule_binding")
public class GovRuleBinding extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id", nullable = false)
    @JsonIgnoreProperties(value = { "rule", "bindings" }, allowSetters = true)
    private GovRuleVersion ruleVersion;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "dataset_alias", length = 128)
    private String datasetAlias;

    @Column(name = "scope_type", length = 32)
    private String scopeType;

    @Column(name = "field_refs", length = 1024)
    private String fieldRefs;

    @Column(name = "filter_expression", length = 2048)
    private String filterExpression;

    @Column(name = "schedule_override", length = 128)
    private String scheduleOverride;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovRuleVersion getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(GovRuleVersion ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetAlias() {
        return datasetAlias;
    }

    public void setDatasetAlias(String datasetAlias) {
        this.datasetAlias = datasetAlias;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getFieldRefs() {
        return fieldRefs;
    }

    public void setFieldRefs(String fieldRefs) {
        this.fieldRefs = fieldRefs;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public String getScheduleOverride() {
        return scheduleOverride;
    }

    public void setScheduleOverride(String scheduleOverride) {
        this.scheduleOverride = scheduleOverride;
    }
}

