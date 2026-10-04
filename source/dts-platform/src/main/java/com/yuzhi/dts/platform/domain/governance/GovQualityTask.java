package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gov_quality_task")
public class GovQualityTask extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "rule_id", columnDefinition = "uuid")
    private UUID ruleId;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes = 60;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 1;

    @Column(name = "retry_backoff_seconds", nullable = false)
    private Integer retryBackoffSeconds = 0;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(Instant lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public Integer getRetryBackoffSeconds() {
        return retryBackoffSeconds;
    }

    public void setRetryBackoffSeconds(Integer retryBackoffSeconds) {
        this.retryBackoffSeconds = retryBackoffSeconds;
    }
}
