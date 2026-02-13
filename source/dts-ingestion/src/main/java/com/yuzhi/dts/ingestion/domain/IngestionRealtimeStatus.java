package com.yuzhi.dts.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "ingestion_realtime_status",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ingestion_realtime_task", columnNames = { "task_id" }),
    }
)
public class IngestionRealtimeStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @NotNull
    @Size(max = 64)
    @Column(name = "connector_type", nullable = false, length = 64)
    private String connectorType;

    @Size(max = 256)
    @Column(name = "topic_name", length = 256)
    private String topicName;

    @Size(max = 256)
    @Column(name = "consumer_group", length = 256)
    private String consumerGroup;

    @Size(max = 512)
    @Column(name = "checkpoint_token", length = 512)
    private String checkpointToken;

    @Column(name = "lag_ms")
    private Long lagMs;

    @Column(name = "throughput_rps", precision = 18, scale = 2)
    private BigDecimal throughputRps;

    @Column(name = "backlog_count")
    private Long backlogCount;

    @NotNull
    @Size(max = 32)
    @Column(name = "status", nullable = false, length = 32)
    private String status = "IDLE";

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getCheckpointToken() {
        return checkpointToken;
    }

    public void setCheckpointToken(String checkpointToken) {
        this.checkpointToken = checkpointToken;
    }

    public Long getLagMs() {
        return lagMs;
    }

    public void setLagMs(Long lagMs) {
        this.lagMs = lagMs;
    }

    public BigDecimal getThroughputRps() {
        return throughputRps;
    }

    public void setThroughputRps(BigDecimal throughputRps) {
        this.throughputRps = throughputRps;
    }

    public Long getBacklogCount() {
        return backlogCount;
    }

    public void setBacklogCount(Long backlogCount) {
        this.backlogCount = backlogCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
