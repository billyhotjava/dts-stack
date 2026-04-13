package com.yuzhi.dts.platform.domain.explore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_execution_chunk")
public class QueryExecutionChunk implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "execution_id", nullable = false, columnDefinition = "uuid")
    private UUID executionId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "rows_json", nullable = false, columnDefinition = "text")
    private String rowsJson;

    @Column(name = "row_start", nullable = false)
    private Long rowStart;

    @Column(name = "row_end", nullable = false)
    private Long rowEnd;

    @Column(name = "created_date")
    private Instant createdDate;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getRowsJson() { return rowsJson; }
    public void setRowsJson(String rowsJson) { this.rowsJson = rowsJson; }

    public Long getRowStart() { return rowStart; }
    public void setRowStart(Long rowStart) { this.rowStart = rowStart; }

    public Long getRowEnd() { return rowEnd; }
    public void setRowEnd(Long rowEnd) { this.rowEnd = rowEnd; }

    public Instant getCreatedDate() { return createdDate; }
    public void setCreatedDate(Instant createdDate) { this.createdDate = createdDate; }
}
