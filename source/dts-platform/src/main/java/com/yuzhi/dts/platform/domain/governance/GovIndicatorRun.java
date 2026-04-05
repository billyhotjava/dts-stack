package com.yuzhi.dts.platform.domain.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gov_indicator_run")
public class GovIndicatorRun implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "indicator_id", nullable = false)
    private UUID indicatorId;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "computed_value")
    private BigDecimal computedValue;

    @Column(name = "previous_value")
    private BigDecimal previousValue;

    @Column(name = "change_rate")
    private BigDecimal changeRate;

    @Column(name = "rows_processed")
    private Integer rowsProcessed;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "threshold_hit")
    private Boolean thresholdHit;

    @Column(name = "alert_level", length = 8)
    private String alertLevel;

    @Column(name = "alert_reason")
    private String alertReason;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "dbt_run_id", length = 64)
    private String dbtRunId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIndicatorId() {
        return indicatorId;
    }

    public void setIndicatorId(UUID indicatorId) {
        this.indicatorId = indicatorId;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getComputedValue() {
        return computedValue;
    }

    public void setComputedValue(BigDecimal computedValue) {
        this.computedValue = computedValue;
    }

    public BigDecimal getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(BigDecimal previousValue) {
        this.previousValue = previousValue;
    }

    public BigDecimal getChangeRate() {
        return changeRate;
    }

    public void setChangeRate(BigDecimal changeRate) {
        this.changeRate = changeRate;
    }

    public Integer getRowsProcessed() {
        return rowsProcessed;
    }

    public void setRowsProcessed(Integer rowsProcessed) {
        this.rowsProcessed = rowsProcessed;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Boolean getThresholdHit() {
        return thresholdHit;
    }

    public void setThresholdHit(Boolean thresholdHit) {
        this.thresholdHit = thresholdHit;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getAlertReason() {
        return alertReason;
    }

    public void setAlertReason(String alertReason) {
        this.alertReason = alertReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDbtRunId() {
        return dbtRunId;
    }

    public void setDbtRunId(String dbtRunId) {
        this.dbtRunId = dbtRunId;
    }
}
