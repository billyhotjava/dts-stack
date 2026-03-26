package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "screen_uploaded_dataset")
public class AnalyticsScreenDataset implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "columns_meta", columnDefinition = "text", nullable = false)
    private String columnsMeta;

    @Column(name = "encrypted_data", columnDefinition = "bytea", nullable = false)
    private byte[] encryptedData;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getColumnsMeta() { return columnsMeta; }
    public void setColumnsMeta(String columnsMeta) { this.columnsMeta = columnsMeta; }
    public byte[] getEncryptedData() { return encryptedData; }
    public void setEncryptedData(byte[] encryptedData) { this.encryptedData = encryptedData; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
