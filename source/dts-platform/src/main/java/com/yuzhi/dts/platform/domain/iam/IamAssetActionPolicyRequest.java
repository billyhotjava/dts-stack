package com.yuzhi.dts.platform.domain.iam;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_asset_action_policy_request")
public class IamAssetActionPolicyRequest extends AbstractAuditingEntity<UUID> implements Serializable {

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

    @Lob
    @Column(name = "changes_json", nullable = false)
    private String changesJson;

    @Lob
    @Column(name = "before_snapshot_json", nullable = false)
    private String beforeSnapshotJson;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "reason", length = 1024, nullable = false)
    private String reason;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "requested_by", length = 128, nullable = false)
    private String requestedBy;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", length = 1024)
    private String decisionNotes;

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

    public String getChangesJson() {
        return changesJson;
    }

    public void setChangesJson(String changesJson) {
        this.changesJson = changesJson;
    }

    public String getBeforeSnapshotJson() {
        return beforeSnapshotJson;
    }

    public void setBeforeSnapshotJson(String beforeSnapshotJson) {
        this.beforeSnapshotJson = beforeSnapshotJson;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }

    public void setDecisionNotes(String decisionNotes) {
        this.decisionNotes = decisionNotes;
    }
}
