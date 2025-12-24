package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.UUID;

public class IndicatorDto {

    private UUID id;
    private String code;
    private String name;
    private String category;
    private String definition;
    private String expressionSql;
    private String datasetId;
    private String owner;
    private String ownerDept;
    private String dataLevel;
    private String status;
    private String version;
    private String versionNotes;
    private String tags;
    private String lastValidationStatus;
    private String lastValidationMessage;
    private Instant lastValidatedAt;
    private String lastValidationSignature;

    private String createdBy;
    private Instant createdDate;
    private String lastModifiedBy;
    private Instant lastModifiedDate;

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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
