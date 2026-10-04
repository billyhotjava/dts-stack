package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "infra_task_schedule")
public class InfraTaskSchedule extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "cron", length = 64)
    private String cron;

    @Column(name = "status", length = 32)
    private String status; // ACTIVE/PAUSED

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "description", length = 512)
    private String description;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

