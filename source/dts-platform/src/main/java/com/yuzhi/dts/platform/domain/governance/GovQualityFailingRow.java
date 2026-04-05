package com.yuzhi.dts.platform.domain.governance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "gov_quality_failing_row")
public class GovQualityFailingRow implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonIgnoreProperties(value = { "rule", "ruleVersion", "binding" }, allowSetters = true)
    private GovQualityRun run;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "table_name", length = 200, nullable = false)
    private String tableName;

    @Column(name = "row_id", length = 64, nullable = false)
    private String rowId;

    @Column(name = "column_name", length = 200)
    private String columnName;

    @Column(name = "actual_value", columnDefinition = "text")
    private String actualValue;

    @Column(name = "fail_reason", length = 500, nullable = false)
    private String failReason;

    @Column(name = "created_date")
    private Instant createdDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovQualityRun getRun() {
        return run;
    }

    public void setRun(GovQualityRun run) {
        this.run = run;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        this.rowId = rowId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
}
