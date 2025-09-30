package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "saved_query")
public class SavedQuery extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Lob
    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine", nullable = false, length = 16)
    private ExecEnums.ExecEngine engine;

    @Column(name = "connection", length = 64)
    private String connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    private ExecEnums.SecurityLevel level;

    @Column(name = "tags", length = 256)
    private String tags;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public ExecEnums.ExecEngine getEngine() { return engine; }
    public void setEngine(ExecEnums.ExecEngine engine) { this.engine = engine; }
    public String getConnection() { return connection; }
    public void setConnection(String connection) { this.connection = connection; }
    public ExecEnums.SecurityLevel getLevel() { return level; }
    public void setLevel(ExecEnums.SecurityLevel level) { this.level = level; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}

