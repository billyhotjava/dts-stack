package com.yuzhi.dts.ingestion.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Type;

/** Immutable execution configuration snapshot for an ingestion task. */
@Entity
@Table(name = "ingestion_task_revision")
public class IngestionTaskRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private IngestionTask task;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "state", length = 32, nullable = false)
    private String state;

    @Column(name = "source_kind", length = 32, nullable = false)
    private String sourceKind;

    @Type(JsonType.class)
    @Column(name = "task_snapshot", columnDefinition = "jsonb", nullable = false)
    private JsonNode taskSnapshot;

    @Type(JsonType.class)
    @Column(name = "effective_config", columnDefinition = "jsonb", nullable = false)
    private JsonNode effectiveConfig;

    @Column(name = "effective_config_checksum", length = 64)
    private String effectiveConfigChecksum;

    @Column(name = "default_policy_version", nullable = false)
    private Integer defaultPolicyVersion;

    @Column(name = "default_policy_checksum", length = 64, nullable = false)
    private String defaultPolicyChecksum;

    @Type(JsonType.class)
    @Column(name = "classification_seal", columnDefinition = "jsonb")
    private JsonNode classificationSeal;

    @Type(JsonType.class)
    @Column(name = "field_classifications", columnDefinition = "jsonb")
    private JsonNode fieldClassifications;

    @Column(name = "runtime_snapshot", columnDefinition = "bytea")
    private byte[] runtimeSnapshot;

    @Column(name = "runtime_snapshot_iv", columnDefinition = "bytea")
    private byte[] runtimeSnapshotIv;

    @Column(name = "runtime_snapshot_key_version", length = 64)
    private String runtimeSnapshotKeyVersion;

    @Column(name = "quality_policy_ref", length = 200)
    private String qualityPolicyRef;

    @Column(name = "target_dataset_id", columnDefinition = "uuid")
    private java.util.UUID targetDatasetId;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_by", length = 100)
    private String activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "dag_deployment_status", length = 32)
    private String dagDeploymentStatus;

    @Column(name = "dag_deployment_error", columnDefinition = "TEXT")
    private String dagDeploymentError;

    @Column(name = "staged_dag_path", length = 1000)
    private String stagedDagPath;

    @Column(name = "published_dag_path", length = 1000)
    private String publishedDagPath;

    @Column(name = "previous_airflow_dag_id", length = 200)
    private String previousAirflowDagId;

    @Column(name = "dag_deployment_updated_at")
    private Instant dagDeploymentUpdatedAt;

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

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Integer revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public JsonNode getTaskSnapshot() {
        return taskSnapshot;
    }

    public void setTaskSnapshot(JsonNode taskSnapshot) {
        this.taskSnapshot = taskSnapshot;
    }

    public JsonNode getEffectiveConfig() {
        return effectiveConfig;
    }

    public void setEffectiveConfig(JsonNode effectiveConfig) {
        this.effectiveConfig = effectiveConfig;
    }

    public String getEffectiveConfigChecksum() {
        return effectiveConfigChecksum;
    }

    public void setEffectiveConfigChecksum(String effectiveConfigChecksum) {
        this.effectiveConfigChecksum = effectiveConfigChecksum;
    }

    public Integer getDefaultPolicyVersion() {
        return defaultPolicyVersion;
    }

    public void setDefaultPolicyVersion(Integer defaultPolicyVersion) {
        this.defaultPolicyVersion = defaultPolicyVersion;
    }

    public String getDefaultPolicyChecksum() {
        return defaultPolicyChecksum;
    }

    public void setDefaultPolicyChecksum(String defaultPolicyChecksum) {
        this.defaultPolicyChecksum = defaultPolicyChecksum;
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

    public byte[] getRuntimeSnapshot() {
        return runtimeSnapshot;
    }

    public void setRuntimeSnapshot(byte[] runtimeSnapshot) {
        this.runtimeSnapshot = runtimeSnapshot;
    }

    public byte[] getRuntimeSnapshotIv() {
        return runtimeSnapshotIv;
    }

    public void setRuntimeSnapshotIv(byte[] runtimeSnapshotIv) {
        this.runtimeSnapshotIv = runtimeSnapshotIv;
    }

    public String getRuntimeSnapshotKeyVersion() {
        return runtimeSnapshotKeyVersion;
    }

    public void setRuntimeSnapshotKeyVersion(String runtimeSnapshotKeyVersion) {
        this.runtimeSnapshotKeyVersion = runtimeSnapshotKeyVersion;
    }

    public String getQualityPolicyRef() {
        return qualityPolicyRef;
    }

    public void setQualityPolicyRef(String qualityPolicyRef) {
        this.qualityPolicyRef = qualityPolicyRef;
    }

    public java.util.UUID getTargetDatasetId() {
        return targetDatasetId;
    }

    public void setTargetDatasetId(java.util.UUID targetDatasetId) {
        this.targetDatasetId = targetDatasetId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getActivatedBy() {
        return activatedBy;
    }

    public void setActivatedBy(String activatedBy) {
        this.activatedBy = activatedBy;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getDagDeploymentStatus() {
        return dagDeploymentStatus;
    }

    public void setDagDeploymentStatus(String dagDeploymentStatus) {
        this.dagDeploymentStatus = dagDeploymentStatus;
    }

    public String getDagDeploymentError() {
        return dagDeploymentError;
    }

    public void setDagDeploymentError(String dagDeploymentError) {
        this.dagDeploymentError = dagDeploymentError;
    }

    public String getStagedDagPath() {
        return stagedDagPath;
    }

    public void setStagedDagPath(String stagedDagPath) {
        this.stagedDagPath = stagedDagPath;
    }

    public String getPublishedDagPath() {
        return publishedDagPath;
    }

    public void setPublishedDagPath(String publishedDagPath) {
        this.publishedDagPath = publishedDagPath;
    }

    public String getPreviousAirflowDagId() {
        return previousAirflowDagId;
    }

    public void setPreviousAirflowDagId(String previousAirflowDagId) {
        this.previousAirflowDagId = previousAirflowDagId;
    }

    public Instant getDagDeploymentUpdatedAt() {
        return dagDeploymentUpdatedAt;
    }

    public void setDagDeploymentUpdatedAt(Instant dagDeploymentUpdatedAt) {
        this.dagDeploymentUpdatedAt = dagDeploymentUpdatedAt;
    }
}
