package com.yuzhi.dts.platform.domain.iam;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_dataset_policy")
public class IamDatasetPolicy extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dataset_id", columnDefinition = "uuid", nullable = false)
    private UUID datasetId;

    @Column(name = "dataset_name", length = 160)
    private String datasetName;

    @Column(name = "subject_type", length = 32)
    private String subjectType;

    @Column(name = "subject_id", length = 128)
    private String subjectId;

    @Column(name = "subject_name", length = 128)
    private String subjectName;

    @Column(name = "scope", length = 16)
    private String scope;

    @Column(name = "field_name", length = 128)
    private String fieldName;

    @Column(name = "effect", length = 16)
    private String effect;

    @Lob
    @Column(name = "row_expression")
    private String rowExpression;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getRowExpression() {
        return rowExpression;
    }

    public void setRowExpression(String rowExpression) {
        this.rowExpression = rowExpression;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }
}
