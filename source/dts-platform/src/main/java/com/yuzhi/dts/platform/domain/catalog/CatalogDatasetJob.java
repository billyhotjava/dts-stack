package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset_job")
public class CatalogDatasetJob extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private CatalogDataset dataset;

    @Column(name = "job_type", length = 64, nullable = false)
    private String jobType;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "submitted_by", length = 64)
    private String submittedBy;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Lob
    @Column(name = "detail_payload")
    private String detailPayload;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CatalogDataset getDataset() {
        return dataset;
    }

    public void setDataset(CatalogDataset dataset) {
        this.dataset = dataset;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public String getDetailPayload() {
        return detailPayload;
    }

    public void setDetailPayload(String detailPayload) {
        this.detailPayload = detailPayload;
    }
}
