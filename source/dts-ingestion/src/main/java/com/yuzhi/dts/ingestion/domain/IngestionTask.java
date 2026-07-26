package com.yuzhi.dts.ingestion.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * 数据入湖任务实体
 * 用于配置和管理从外部数据源到平台PostgreSQL的数据同步任务
 */
@Entity
@Table(name = "ingestion_task")
public class IngestionTask extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 1, max = 200)
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 数据源配置
    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "source_type", length = 50, nullable = false)
    private String sourceType; // mysql, postgres, oracle, etc.

    @Column(name = "source_data_source_id")
    private java.util.UUID sourceDataSourceId;

    @Type(JsonType.class)
    @Column(name = "source_config", columnDefinition = "jsonb")
    private JsonNode sourceConfig; // 任务级Reader参数（不含连接密钥）

    // 目标配置
    @Size(max = 50)
    @Column(name = "destination_type", length = 50)
    private String destinationType = "postgres";

    @Type(JsonType.class)
    @Column(name = "destination_config", columnDefinition = "jsonb")
    private JsonNode destinationConfig; // 目标数据库配置

    // 同步配置
    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "sync_mode", length = 50, nullable = false)
    private String syncMode; // full_refresh, incremental, cdc

    @Size(max = 100)
    @Column(name = "sync_schedule", length = 100)
    private String syncSchedule; // cron表达式

    @Type(JsonType.class)
    @Column(name = "table_mapping", columnDefinition = "jsonb")
    private JsonNode tableMapping; // 源表到目标表的映射 [{source: "tb1", target: "ods_tb1"}]

    @Type(JsonType.class)
    @Column(name = "sync_config", columnDefinition = "jsonb")
    private JsonNode syncConfig; // 增量等同步扩展配置

    @Type(JsonType.class)
    @Column(name = "graph_dsl", columnDefinition = "jsonb")
    private JsonNode graphDsl; // 可视化编排 DSL（仅持久化，执行链路暂不消费）

    @Type(JsonType.class)
    @Column(name = "classification_seal", columnDefinition = "jsonb")
    private JsonNode classificationSeal;

    @Type(JsonType.class)
    @Column(name = "field_classifications", columnDefinition = "jsonb")
    private JsonNode fieldClassifications;

    // Addax配置
    @Size(max = 500)
    @Column(name = "addax_job_path", length = 500)
    private String addaxJobPath; // Addax JSON文件路径

    @Type(JsonType.class)
    @Column(name = "addax_config", columnDefinition = "jsonb")
    private JsonNode addaxConfig; // 额外的Addax配置参数

    // Airflow集成
    @Column(name = "airflow_enabled")
    private Boolean airflowEnabled = false;

    @Size(max = 200)
    @Column(name = "airflow_dag_id", length = 200)
    private String airflowDagId;

    @Size(max = 512)
    @Column(name = "dbt_model_selector", length = 512)
    private String dbtModelSelector;

    @Size(max = 256)
    @Column(name = "dbt_dag_selector", length = 256)
    private String dbtDagSelector;

    // 状态与审计
    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status = "draft"; // draft, active, paused, deleted

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Size(max = 50)
    @Column(name = "last_execution_status", length = 50)
    private String lastExecutionStatus; // success, failed, running

    // Excel pre-check fields
    @Column(name = "quality_pre_check_enabled")
    private Boolean qualityPreCheckEnabled = false;

    @Size(max = 100)
    @Column(name = "staging_table_name", length = 100)
    private String stagingTableName;

    @Size(max = 20)
    @Column(name = "pre_check_status", length = 20)
    private String preCheckStatus; // PENDING / CHECKING / PASSED / FAILED

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public JsonNode getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(JsonNode sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public java.util.UUID getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(java.util.UUID sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public String getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(String destinationType) {
        this.destinationType = destinationType;
    }

    public JsonNode getDestinationConfig() {
        return destinationConfig;
    }

    public void setDestinationConfig(JsonNode destinationConfig) {
        this.destinationConfig = destinationConfig;
    }

    public String getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(String syncMode) {
        this.syncMode = syncMode;
    }

    public String getSyncSchedule() {
        return syncSchedule;
    }

    public void setSyncSchedule(String syncSchedule) {
        this.syncSchedule = syncSchedule;
    }

    public JsonNode getTableMapping() {
        return tableMapping;
    }

    public void setTableMapping(JsonNode tableMapping) {
        this.tableMapping = tableMapping;
    }

    public JsonNode getSyncConfig() {
        return syncConfig;
    }

    public void setSyncConfig(JsonNode syncConfig) {
        this.syncConfig = syncConfig;
    }

    public JsonNode getGraphDsl() {
        return graphDsl;
    }

    public void setGraphDsl(JsonNode graphDsl) {
        this.graphDsl = graphDsl;
    }

    public JsonNode getClassificationSeal() {
        return classificationSeal;
    }

    public void setClassificationSeal(JsonNode classificationSeal) {
        this.classificationSeal = classificationSeal;
    }

    public JsonNode getFieldClassifications() {
        return fieldClassifications;
    }

    public void setFieldClassifications(JsonNode fieldClassifications) {
        this.fieldClassifications = fieldClassifications;
    }

    public String getAddaxJobPath() {
        return addaxJobPath;
    }

    public void setAddaxJobPath(String addaxJobPath) {
        this.addaxJobPath = addaxJobPath;
    }

    public JsonNode getAddaxConfig() {
        return addaxConfig;
    }

    public void setAddaxConfig(JsonNode addaxConfig) {
        this.addaxConfig = addaxConfig;
    }

    public Boolean getAirflowEnabled() {
        return airflowEnabled;
    }

    public void setAirflowEnabled(Boolean airflowEnabled) {
        this.airflowEnabled = airflowEnabled;
    }

    public String getAirflowDagId() {
        return airflowDagId;
    }

    public void setAirflowDagId(String airflowDagId) {
        this.airflowDagId = airflowDagId;
    }

    public String getDbtModelSelector() {
        return dbtModelSelector;
    }

    public void setDbtModelSelector(String dbtModelSelector) {
        this.dbtModelSelector = dbtModelSelector;
    }

    public String getDbtDagSelector() {
        return dbtDagSelector;
    }

    public void setDbtDagSelector(String dbtDagSelector) {
        this.dbtDagSelector = dbtDagSelector;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }

    public void setLastExecutionStatus(String lastExecutionStatus) {
        this.lastExecutionStatus = lastExecutionStatus;
    }

    public Boolean getQualityPreCheckEnabled() {
        return qualityPreCheckEnabled;
    }

    public void setQualityPreCheckEnabled(Boolean qualityPreCheckEnabled) {
        this.qualityPreCheckEnabled = qualityPreCheckEnabled;
    }

    public String getStagingTableName() {
        return stagingTableName;
    }

    public void setStagingTableName(String stagingTableName) {
        this.stagingTableName = stagingTableName;
    }

    public String getPreCheckStatus() {
        return preCheckStatus;
    }

    public void setPreCheckStatus(String preCheckStatus) {
        this.preCheckStatus = preCheckStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngestionTask)) return false;
        IngestionTask that = (IngestionTask) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "IngestionTask{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", sourceType='" + sourceType + '\'' +
            ", syncMode='" + syncMode + '\'' +
            ", syncConfig=" + syncConfig +
            ", graphDsl=" + graphDsl +
            ", status='" + status + '\'' +
            '}';
    }
}
