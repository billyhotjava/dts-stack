package com.yuzhi.dts.platform.domain.iam;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_classification_sync_log")
public class IamClassificationSyncLog extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "delta_count")
    private Integer deltaCount;

    @Lob
    @Column(name = "failure_json")
    private String failureJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Integer getDeltaCount() {
        return deltaCount;
    }

    public void setDeltaCount(Integer deltaCount) {
        this.deltaCount = deltaCount;
    }

    public String getFailureJson() {
        return failureJson;
    }

    public void setFailureJson(String failureJson) {
        this.failureJson = failureJson;
    }
}
