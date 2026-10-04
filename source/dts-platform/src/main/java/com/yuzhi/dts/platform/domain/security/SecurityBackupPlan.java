package com.yuzhi.dts.platform.domain.security;

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
@Table(name = "security_backup_plan")
public class SecurityBackupPlan extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "target_key", length = 64, nullable = false)
    private String targetKey;

    @Column(name = "title", length = 128, nullable = false)
    private String title;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "schedule", length = 128)
    private String schedule;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_result", length = 32)
    private String lastResult;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void setLastResult(String lastResult) {
        this.lastResult = lastResult;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

