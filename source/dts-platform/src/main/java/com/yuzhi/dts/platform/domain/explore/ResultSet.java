package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_set")
public class ResultSet extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "storage_uri", nullable = false, length = 512)
    private String storageUri;

    @Column(name = "columns", nullable = false, length = 2048)
    private String columns;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "ttl_days")
    private Integer ttlDays;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }
    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Integer getTtlDays() { return ttlDays; }
    public void setTtlDays(Integer ttlDays) { this.ttlDays = ttlDays; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}

