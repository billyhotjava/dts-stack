package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_revision")
public class AnalyticsRevision implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_reversion", nullable = false)
    private boolean reversion;

    @Column(name = "object_json", nullable = false, columnDefinition = "text")
    private String objectJson;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "dependency_snapshot_json", columnDefinition = "text")
    private String dependencySnapshotJson;

    @Column(name = "contract_checksum", length = 64)
    private String contractChecksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isReversion() {
        return reversion;
    }

    public void setReversion(boolean reversion) {
        this.reversion = reversion;
    }

    public String getObjectJson() {
        return objectJson;
    }

    public void setObjectJson(String objectJson) {
        this.objectJson = objectJson;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getDependencySnapshotJson() {
        return dependencySnapshotJson;
    }

    public void setDependencySnapshotJson(String dependencySnapshotJson) {
        this.dependencySnapshotJson = dependencySnapshotJson;
    }

    public String getContractChecksum() {
        return contractChecksum;
    }

    public void setContractChecksum(String contractChecksum) {
        this.contractChecksum = contractChecksum;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (versionNo == null) {
            versionNo = 1;
        }
        if (status == null || status.isBlank()) {
            status = "DRAFT";
        }
    }
}
