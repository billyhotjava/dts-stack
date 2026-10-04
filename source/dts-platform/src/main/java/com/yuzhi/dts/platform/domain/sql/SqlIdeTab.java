package com.yuzhi.dts.platform.domain.sql;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "sql_ide_tab")
public class SqlIdeTab extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_login", length = 50, nullable = false)
    private String userLogin;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "sql_text", columnDefinition = "text")
    private String sqlText;

    @Column(name = "engine", length = 50)
    private String engine;

    @Column(name = "datasource_id", columnDefinition = "uuid")
    private UUID datasourceId;

    @Column(name = "schema_ctx", length = 500)
    private String schemaCtx;

    @Column(name = "cursor_line")
    private Integer cursorLine;

    @Column(name = "cursor_col")
    private Integer cursorCol;

    @Column(name = "selection_json", columnDefinition = "text")
    private String selectionJson;

    @Column(name = "last_execution_id", columnDefinition = "uuid")
    private UUID lastExecutionId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Override
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUserLogin() { return userLogin; }
    public void setUserLogin(String userLogin) { this.userLogin = userLogin; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public UUID getDatasourceId() { return datasourceId; }
    public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }
    public String getSchemaCtx() { return schemaCtx; }
    public void setSchemaCtx(String schemaCtx) { this.schemaCtx = schemaCtx; }
    public Integer getCursorLine() { return cursorLine; }
    public void setCursorLine(Integer cursorLine) { this.cursorLine = cursorLine; }
    public Integer getCursorCol() { return cursorCol; }
    public void setCursorCol(Integer cursorCol) { this.cursorCol = cursorCol; }
    public String getSelectionJson() { return selectionJson; }
    public void setSelectionJson(String selectionJson) { this.selectionJson = selectionJson; }
    public UUID getLastExecutionId() { return lastExecutionId; }
    public void setLastExecutionId(UUID lastExecutionId) { this.lastExecutionId = lastExecutionId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
