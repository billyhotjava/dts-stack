package com.yuzhi.dts.platform.domain.catalog;

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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "catalog_classification_event")
public class CatalogClassificationEvent extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "subject_type", nullable = false, length = 32, updatable = false)
    private String subjectType;

    @Column(name = "subject_key", nullable = false, length = 512, updatable = false)
    private String subjectKey;

    @Column(name = "asset_type", length = 32, updatable = false)
    private String assetType;

    @Column(name = "event_type", nullable = false, length = 48, updatable = false)
    private String eventType;

    @Column(name = "previous_level", length = 32, updatable = false)
    private String previousLevel;

    @Column(name = "candidate_level", nullable = false, length = 32, updatable = false)
    private String candidateLevel;

    @Column(name = "resulting_level", nullable = false, length = 32, updatable = false)
    private String resultingLevel;

    @Column(name = "trigger_type", nullable = false, length = 48, updatable = false)
    private String triggerType;

    @Column(name = "trigger_ref", length = 512, updatable = false)
    private String triggerRef;

    @Lob
    @Column(name = "evidence_json", columnDefinition = "text", updatable = false)
    private String evidenceJson;

    @Column(name = "actor", nullable = false, length = 50, updatable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "snapshot_version", nullable = false, updatable = false)
    private long snapshotVersion;

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

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPreviousLevel() {
        return previousLevel;
    }

    public void setPreviousLevel(String previousLevel) {
        this.previousLevel = previousLevel;
    }

    public String getCandidateLevel() {
        return candidateLevel;
    }

    public void setCandidateLevel(String candidateLevel) {
        this.candidateLevel = candidateLevel;
    }

    public String getResultingLevel() {
        return resultingLevel;
    }

    public void setResultingLevel(String resultingLevel) {
        this.resultingLevel = resultingLevel;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerRef() {
        return triggerRef;
    }

    public void setTriggerRef(String triggerRef) {
        this.triggerRef = triggerRef;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }
}
