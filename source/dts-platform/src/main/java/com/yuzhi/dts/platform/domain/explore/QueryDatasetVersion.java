package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_dataset_version")
public class QueryDatasetVersion extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private QueryDatasetAsset dataset;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "DRAFT";

    @Column(name = "sql_text", columnDefinition = "text", nullable = false)
    private String sqlText;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    @Column(name = "result_set_id", columnDefinition = "uuid")
    private UUID resultSetId;

    @Column(name = "execution_id", columnDefinition = "uuid")
    private UUID executionId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "semantic_contract_schema", length = 64)
    private String semanticContractSchema;

    @Column(name = "semantic_contract_version", length = 64)
    private String semanticContractVersion;

    @Column(name = "semantic_contract_json", columnDefinition = "jsonb")
    private String semanticContractJson;

    @Column(name = "semantic_contract_checksum", length = 64)
    private String semanticContractChecksum;

    @Column(name = "contract_snapshot_status", length = 32, nullable = false)
    private String contractSnapshotStatus = "UNRESOLVED";

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QueryDatasetAsset getDataset() {
        return dataset;
    }

    public void setDataset(QueryDatasetAsset dataset) {
        this.dataset = dataset;
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

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public UUID getResultSetId() {
        return resultSetId;
    }

    public void setResultSetId(UUID resultSetId) {
        this.resultSetId = resultSetId;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public void setExecutionId(UUID executionId) {
        this.executionId = executionId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSemanticContractSchema() {
        return semanticContractSchema;
    }

    public void setSemanticContractSchema(String semanticContractSchema) {
        this.semanticContractSchema = semanticContractSchema;
    }

    public String getSemanticContractVersion() {
        return semanticContractVersion;
    }

    public void setSemanticContractVersion(String semanticContractVersion) {
        this.semanticContractVersion = semanticContractVersion;
    }

    public String getSemanticContractJson() {
        return semanticContractJson;
    }

    public void setSemanticContractJson(String semanticContractJson) {
        this.semanticContractJson = semanticContractJson;
    }

    public String getSemanticContractChecksum() {
        return semanticContractChecksum;
    }

    public void setSemanticContractChecksum(String semanticContractChecksum) {
        this.semanticContractChecksum = semanticContractChecksum;
    }

    public String getContractSnapshotStatus() {
        return contractSnapshotStatus;
    }

    public void setContractSnapshotStatus(String contractSnapshotStatus) {
        this.contractSnapshotStatus = contractSnapshotStatus;
    }
}
