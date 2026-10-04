package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "viz_spec")
public class VizSpec extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ExecEnums.VizType type;

    @Column(name = "config", nullable = false, length = 4096)
    private String config;

    @Column(name = "query_id", columnDefinition = "uuid")
    private UUID queryId;

    @Column(name = "result_set_id", columnDefinition = "uuid")
    private UUID resultSetId;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ExecEnums.VizType getType() { return type; }
    public void setType(ExecEnums.VizType type) { this.type = type; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public UUID getQueryId() { return queryId; }
    public void setQueryId(UUID queryId) { this.queryId = queryId; }
    public UUID getResultSetId() { return resultSetId; }
    public void setResultSetId(UUID resultSetId) { this.resultSetId = resultSetId; }
}

