package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "infra_project_cockpit_batch")
public class InfraProjectCockpitBatch extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "external_exchange_file_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID externalExchangeFileId;

    @Column(name = "batch_code", length = 128, nullable = false)
    private String batchCode;

    @Column(name = "source_file_name", length = 512, nullable = false)
    private String sourceFileName;

    @Column(name = "sheet_name", length = 255)
    private String sheetName;

    @Column(name = "delimiter", length = 8)
    private String delimiter;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "uploaded_by", length = 128)
    private String uploadedBy;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "LOADED";

    @Column(name = "csv_path", length = 2048)
    private String csvPath;

    @Column(name = "error_path", length = 2048)
    private String errorPath;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "issue_count")
    private Integer issueCount;

    @Column(name = "issue_row_count")
    private Integer issueRowCount;

    @Column(name = "loaded_at")
    private Instant loadedAt;

    @Column(name = "props", columnDefinition = "text")
    private String props;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExternalExchangeFileId() {
        return externalExchangeFileId;
    }

    public void setExternalExchangeFileId(UUID externalExchangeFileId) {
        this.externalExchangeFileId = externalExchangeFileId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCsvPath() {
        return csvPath;
    }

    public void setCsvPath(String csvPath) {
        this.csvPath = csvPath;
    }

    public String getErrorPath() {
        return errorPath;
    }

    public void setErrorPath(String errorPath) {
        this.errorPath = errorPath;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public Integer getIssueRowCount() {
        return issueRowCount;
    }

    public void setIssueRowCount(Integer issueRowCount) {
        this.issueRowCount = issueRowCount;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public void setLoadedAt(Instant loadedAt) {
        this.loadedAt = loadedAt;
    }

    public String getProps() {
        return props;
    }

    public void setProps(String props) {
        this.props = props;
    }
}
