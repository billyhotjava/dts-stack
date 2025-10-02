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
@Table(name = "gov_compliance_batch_item")
public class GovComplianceBatchItem extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    @JsonIgnoreProperties(value = { "items" }, allowSetters = true)
    private GovComplianceBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    @JsonIgnoreProperties(value = { "versions", "latestVersion" }, allowSetters = true)
    private GovRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    @JsonIgnoreProperties(value = { "rule", "bindings" }, allowSetters = true)
    private GovRuleVersion ruleVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_run_id")
    @JsonIgnoreProperties(value = { "rule", "ruleVersion", "binding" }, allowSetters = true)
    private GovQualityRun qualityRun;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "conclusion", length = 2048)
    private String conclusion;

    @Column(name = "evidence_ref", length = 256)
    private String evidenceRef;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovComplianceBatch getBatch() {
        return batch;
    }

    public void setBatch(GovComplianceBatch batch) {
        this.batch = batch;
    }

    public GovRule getRule() {
        return rule;
    }

    public void setRule(GovRule rule) {
        this.rule = rule;
    }

    public GovRuleVersion getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(GovRuleVersion ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public GovQualityRun getQualityRun() {
        return qualityRun;
    }

    public void setQualityRun(GovQualityRun qualityRun) {
        this.qualityRun = qualityRun;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public void setEvidenceRef(String evidenceRef) {
        this.evidenceRef = evidenceRef;
    }
}

