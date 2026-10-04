package com.yuzhi.dts.platform.domain.iam;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "iam_asset_action_policy",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_iam_asset_action_policy_tuple",
            columnNames = { "subject_type", "subject_id", "resource_type", "resource_id", "action" }
        ),
    }
)
public class IamAssetActionPolicy extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "subject_type", length = 32, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", length = 160, nullable = false)
    private String subjectId;

    @Column(name = "subject_name", length = 160)
    private String subjectName;

    @Column(name = "resource_type", length = 32, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 256, nullable = false)
    private String resourceId;

    @Column(name = "resource_name", length = 256)
    private String resourceName;

    @Column(name = "action", length = 32, nullable = false)
    private String action;

    @Column(name = "effect", length = 16, nullable = false)
    private String effect;

    @Column(name = "source", length = 32, nullable = false)
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

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
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
