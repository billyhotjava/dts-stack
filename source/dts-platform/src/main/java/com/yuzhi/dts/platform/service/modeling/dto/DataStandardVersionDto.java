package com.yuzhi.dts.platform.service.modeling.dto;

import com.yuzhi.dts.platform.domain.modeling.DataStandardVersionStatus;
import java.time.Instant;
import java.util.UUID;

public class DataStandardVersionDto {

    private UUID id;
    private String version;
    private DataStandardVersionStatus status;
    private String changeSummary;
    private String snapshotJson;
    private Instant releasedAt;
    private Instant createdDate;
    private String createdBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public DataStandardVersionStatus getStatus() {
        return status;
    }

    public void setStatus(DataStandardVersionStatus status) {
        this.status = status;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}

