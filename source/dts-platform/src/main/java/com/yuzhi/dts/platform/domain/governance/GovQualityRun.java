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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gov_quality_run")
public class GovQualityRun extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    @JsonIgnoreProperties(value = { "versions", "latestVersion" }, allowSetters = true)
    private GovRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    @JsonIgnoreProperties(value = { "rule", "bindings" }, allowSetters = true)
    private GovRuleVersion ruleVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binding_id")
    @JsonIgnoreProperties(value = { "ruleVersion" }, allowSetters = true)
    private GovRuleBinding binding;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "trigger_type", length = 32)
    private String triggerType;

    @Column(name = "trigger_ref", length = 128)
    private String triggerRef;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "data_level", length = 32)
    private String dataLevel;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "message", length = 4096)
    private String message;

    @Column(name = "metrics_json", columnDefinition = "jsonb")
    private String metricsJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public GovRuleBinding getBinding() {
        return binding;
    }

    public void setBinding(GovRuleBinding binding) {
        this.binding = binding;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerRef() {
        return triggerRef;
    }

    public void setTriggerRef(String triggerRef) {
        this.triggerRef = triggerRef;
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

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }
}

