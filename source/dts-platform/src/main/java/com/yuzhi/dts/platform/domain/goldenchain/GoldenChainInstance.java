package com.yuzhi.dts.platform.domain.goldenchain;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "golden_chain_instance")
public class GoldenChainInstance extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "chain_key", length = 128, nullable = false, unique = true)
    private String chainKey;

    @Column(name = "display_name", length = 256, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", length = 32, nullable = false)
    private GoldenChainSourceKind sourceKind;

    @Column(name = "source_ref_type", length = 64)
    private String sourceRefType;

    @Column(name = "source_ref_id", length = 256)
    private String sourceRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 32, nullable = false)
    private GoldenChainStage currentStage = GoldenChainStage.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 32, nullable = false)
    private GoldenChainStageStatus lifecycleStatus = GoldenChainStageStatus.PENDING;

    @Column(name = "owner", length = 128, nullable = false)
    private String owner;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    public static GoldenChainInstance create(
        String chainKey,
        String displayName,
        GoldenChainSourceKind sourceKind,
        String owner
    ) {
        GoldenChainInstance instance = new GoldenChainInstance();
        instance.setChainKey(chainKey);
        instance.setDisplayName(displayName);
        instance.setSourceKind(sourceKind);
        instance.setOwner(owner);
        return instance;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getChainKey() {
        return chainKey;
    }

    public void setChainKey(String chainKey) {
        this.chainKey = normalizeRequired(chainKey, "chainKey");
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = normalizeRequired(displayName, "displayName");
    }

    public GoldenChainSourceKind getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(GoldenChainSourceKind sourceKind) {
        if (sourceKind == null) {
            throw new IllegalArgumentException("sourceKind must not be null");
        }
        this.sourceKind = sourceKind;
    }

    public String getSourceRefType() {
        return sourceRefType;
    }

    public String getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRef(String sourceRefType, String sourceRefId) {
        this.sourceRefType = normalize(sourceRefType);
        this.sourceRefId = normalize(sourceRefId);
    }

    public GoldenChainStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(GoldenChainStage currentStage) {
        if (currentStage == null) {
            throw new IllegalArgumentException("currentStage must not be null");
        }
        this.currentStage = currentStage;
    }

    public GoldenChainStageStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(GoldenChainStageStatus lifecycleStatus) {
        if (lifecycleStatus == null) {
            throw new IllegalArgumentException("lifecycleStatus must not be null");
        }
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = normalizeRequired(owner, "owner");
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
