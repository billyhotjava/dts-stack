package com.yuzhi.dts.ingestion.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "ingestion_schema_snapshot")
public class IngestionSchemaSnapshot extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private IngestionTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_execution_id")
    private IngestionExecution execution;

    @Column(name = "snapshot_version")
    private Integer snapshotVersion = 1;

    @Size(max = 50)
    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Size(max = 200)
    @Column(name = "source_system", length = 200)
    private String sourceSystem;

    @Size(max = 200)
    @Column(name = "source_schema", length = 200)
    private String sourceSchema;

    @Size(max = 300)
    @Column(name = "source_table", length = 300)
    private String sourceTable;

    @Size(max = 500)
    @Column(name = "source_resource", length = 500)
    private String sourceResource;

    @Size(max = 200)
    @Column(name = "ods_schema", length = 200)
    private String odsSchema;

    @Size(max = 300)
    @Column(name = "ods_table", length = 300)
    private String odsTable;

    @Size(max = 128)
    @Column(name = "schema_fingerprint", length = 128)
    private String schemaFingerprint;

    @Type(JsonType.class)
    @Column(name = "columns_json", columnDefinition = "jsonb")
    private JsonNode columnsJson;

    @Type(JsonType.class)
    @Column(name = "primary_key_columns", columnDefinition = "jsonb")
    private JsonNode primaryKeyColumns;

    @Type(JsonType.class)
    @Column(name = "indexes_json", columnDefinition = "jsonb")
    private JsonNode indexesJson;

    @Type(JsonType.class)
    @Column(name = "warnings_json", columnDefinition = "jsonb")
    private JsonNode warningsJson;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IngestionTask getTask() {
        return task;
    }

    public void setTask(IngestionTask task) {
        this.task = task;
    }

    public IngestionExecution getExecution() {
        return execution;
    }

    public void setExecution(IngestionExecution execution) {
        this.execution = execution;
    }

    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Integer snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSourceResource() {
        return sourceResource;
    }

    public void setSourceResource(String sourceResource) {
        this.sourceResource = sourceResource;
    }

    public String getOdsSchema() {
        return odsSchema;
    }

    public void setOdsSchema(String odsSchema) {
        this.odsSchema = odsSchema;
    }

    public String getOdsTable() {
        return odsTable;
    }

    public void setOdsTable(String odsTable) {
        this.odsTable = odsTable;
    }

    public String getSchemaFingerprint() {
        return schemaFingerprint;
    }

    public void setSchemaFingerprint(String schemaFingerprint) {
        this.schemaFingerprint = schemaFingerprint;
    }

    public JsonNode getColumnsJson() {
        return columnsJson;
    }

    public void setColumnsJson(JsonNode columnsJson) {
        this.columnsJson = columnsJson;
    }

    public JsonNode getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public void setPrimaryKeyColumns(JsonNode primaryKeyColumns) {
        this.primaryKeyColumns = primaryKeyColumns;
    }

    public JsonNode getIndexesJson() {
        return indexesJson;
    }

    public void setIndexesJson(JsonNode indexesJson) {
        this.indexesJson = indexesJson;
    }

    public JsonNode getWarningsJson() {
        return warningsJson;
    }

    public void setWarningsJson(JsonNode warningsJson) {
        this.warningsJson = warningsJson;
    }
}
