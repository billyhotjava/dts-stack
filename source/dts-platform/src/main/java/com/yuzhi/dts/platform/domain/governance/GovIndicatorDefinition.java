package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gov_indicator_definition")
public class GovIndicatorDefinition extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, unique = true)
    private String code;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "definition")
    private String definition;

    @Column(name = "expression_sql")
    private String expressionSql;

    @Column(name = "dataset_id", length = 64)
    private String datasetId;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "data_level", length = 32)
    private String dataLevel;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "version_notes")
    private String versionNotes;

    @Column(name = "tags")
    private String tags;

    @Column(name = "last_validation_status", length = 32)
    private String lastValidationStatus;

    @Column(name = "last_validation_message")
    private String lastValidationMessage;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_validation_signature", length = 64)
    private String lastValidationSignature;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getExpressionSql() {
        return expressionSql;
    }

    public void setExpressionSql(String expressionSql) {
        this.expressionSql = expressionSql;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionNotes() {
        return versionNotes;
    }

    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getLastValidationStatus() {
        return lastValidationStatus;
    }

    public void setLastValidationStatus(String lastValidationStatus) {
        this.lastValidationStatus = lastValidationStatus;
    }

    public String getLastValidationMessage() {
        return lastValidationMessage;
    }

    public void setLastValidationMessage(String lastValidationMessage) {
        this.lastValidationMessage = lastValidationMessage;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getLastValidationSignature() {
        return lastValidationSignature;
    }

    public void setLastValidationSignature(String lastValidationSignature) {
        this.lastValidationSignature = lastValidationSignature;
    }
}
