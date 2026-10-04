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

    @Column(name = "name", length = 128)
    private String name;

    public enum StorageFormat {
        JSON,
        CSV,
        PARQUET,
        ARROW
    }

    @Column(name = "storage_uri", nullable = false, length = 512)
    private String storageUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_format", length = 16)
    private StorageFormat storageFormat;

    @Column(name = "columns", nullable = false, length = 2048)
    private String columns;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Lob
    @Column(name = "preview_columns")
    private String previewColumns;

    @Column(name = "ttl_days")
    private Integer ttlDays;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }
    public StorageFormat getStorageFormat() { return storageFormat; }
    public void setStorageFormat(StorageFormat storageFormat) { this.storageFormat = storageFormat; }
    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getPreviewColumns() { return previewColumns; }
    public void setPreviewColumns(String previewColumns) { this.previewColumns = previewColumns; }
    public Integer getTtlDays() { return ttlDays; }
    public void setTtlDays(Integer ttlDays) { this.ttlDays = ttlDays; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
