package com.yuzhi.dts.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Durable replacement for the {@code MetricGraphDraftService} in-memory {@code ConcurrentHashMap drafts}.
 *
 * <p>The {@code id} is the UUID-string draft key (was the map key and {@code draft.get("id")}). The
 * legacy public draft Map shape {@code {id,status,graph,diagnostics,meta:{createdAt,updatedAt,source}}}
 * is reassembled by the service from these columns (T03) for byte-identical reads.
 */
@Entity
@Table(name = "graph_draft")
public class GraphDraft extends AbstractAuditingEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> graph;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagnostics", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> diagnostics;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "source", nullable = false, length = 128)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GraphDraft() {}

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getGraph() {
        return graph;
    }

    public void setGraph(Map<String, Object> graph) {
        this.graph = graph;
    }

    public List<Map<String, Object>> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(List<Map<String, Object>> diagnostics) {
        this.diagnostics = diagnostics;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
