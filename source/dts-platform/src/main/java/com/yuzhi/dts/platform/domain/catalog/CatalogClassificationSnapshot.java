package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_classification_snapshot")
public class CatalogClassificationSnapshot extends AbstractAuditingEntity<UUID> implements Serializable {

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

    @Column(name = "declared_level", length = 32, updatable = false)
    private String declaredLevel;

    @Column(name = "detected_level", length = 32)
    private String detectedLevel;

    @Column(name = "manual_floor", length = 32)
    private String manualFloor;

    @Column(name = "effective_level", nullable = false, length = 32)
    private String effectiveLevel;

    @Column(name = "origin_type", nullable = false, length = 48, updatable = false)
    private String originType;

    @Column(name = "origin_ref", length = 512, updatable = false)
    private String originRef;

    @Column(name = "sealed_at", nullable = false, updatable = false)
    private Instant sealedAt;

    @Column(name = "evidence_checksum", nullable = false, length = 64, updatable = false)
    private String evidenceChecksum;

    @Column(name = "propagation_status", nullable = false, length = 32)
    private String propagationStatus;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;

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

    public String getDeclaredLevel() {
        return declaredLevel;
    }

    public void setDeclaredLevel(String declaredLevel) {
        this.declaredLevel = declaredLevel;
    }

    public String getDetectedLevel() {
        return detectedLevel;
    }

    public void setDetectedLevel(String detectedLevel) {
        this.detectedLevel = detectedLevel;
    }

    public String getManualFloor() {
        return manualFloor;
    }

    public void setManualFloor(String manualFloor) {
        this.manualFloor = manualFloor;
    }

    public String getEffectiveLevel() {
        return effectiveLevel;
    }

    public void setEffectiveLevel(String effectiveLevel) {
        this.effectiveLevel = effectiveLevel;
    }

    public String getOriginType() {
        return originType;
    }

    public void setOriginType(String originType) {
        this.originType = originType;
    }

    public String getOriginRef() {
        return originRef;
    }

    public void setOriginRef(String originRef) {
        this.originRef = originRef;
    }

    public Instant getSealedAt() {
        return sealedAt;
    }

    public void setSealedAt(Instant sealedAt) {
        this.sealedAt = sealedAt;
    }

    public String getEvidenceChecksum() {
        return evidenceChecksum;
    }

    public void setEvidenceChecksum(String evidenceChecksum) {
        this.evidenceChecksum = evidenceChecksum;
    }

    public String getPropagationStatus() {
        return propagationStatus;
    }

    public void setPropagationStatus(String propagationStatus) {
        this.propagationStatus = propagationStatus;
    }

    public Long getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Long recordVersion) {
        this.recordVersion = recordVersion;
    }
}
