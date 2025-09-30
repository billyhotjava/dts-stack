package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_execution")
public class QueryExecution extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "saved_query_id", columnDefinition = "uuid")
    private UUID savedQueryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine", nullable = false, length = 16)
    private ExecEnums.ExecEngine engine;

    @Column(name = "connection", length = 64)
    private String connection;

    @Lob
    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExecEnums.ExecStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "bytes_processed")
    private Long bytesProcessed;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "result_set_id", columnDefinition = "uuid")
    private UUID resultSetId;

    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSavedQueryId() { return savedQueryId; }
    public void setSavedQueryId(UUID savedQueryId) { this.savedQueryId = savedQueryId; }
    public ExecEnums.ExecEngine getEngine() { return engine; }
    public void setEngine(ExecEnums.ExecEngine engine) { this.engine = engine; }
    public String getConnection() { return connection; }
    public void setConnection(String connection) { this.connection = connection; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public ExecEnums.ExecStatus getStatus() { return status; }
    public void setStatus(ExecEnums.ExecStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Long getBytesProcessed() { return bytesProcessed; }
    public void setBytesProcessed(Long bytesProcessed) { this.bytesProcessed = bytesProcessed; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public UUID getResultSetId() { return resultSetId; }
    public void setResultSetId(UUID resultSetId) { this.resultSetId = resultSetId; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
}
