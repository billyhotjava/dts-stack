package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "data_standard_version",
    uniqueConstraints = { @UniqueConstraint(name = "uk_data_standard_version", columnNames = { "standard_id", "version" }) }
)
public class DataStandardVersion extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_id", nullable = false)
    private DataStandard standard;

    @Column(name = "version", length = 32, nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DataStandardVersionStatus status = DataStandardVersionStatus.DRAFT;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    @Column(name = "snapshot_json", columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DataStandard getStandard() {
        return standard;
    }

    public void setStandard(DataStandard standard) {
        this.standard = standard;
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
}

