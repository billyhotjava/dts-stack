package com.yuzhi.dts.platform.domain.goldenchain;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
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
@Table(name = "golden_chain_stage_snapshot")
public class GoldenChainStageSnapshotRecord extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "chain_instance_id", columnDefinition = "uuid", nullable = false)
    private UUID chainInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 32, nullable = false)
    private GoldenChainStage stage;

    @Column(name = "stage_sequence", nullable = false)
    private Integer stageSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private GoldenChainStageStatus status;

    @Column(name = "owner", length = 128, nullable = false)
    private String owner;

    @Column(name = "evidence_ref", length = 2048)
    private String evidenceRef;

    @Column(name = "evidence_type", length = 64)
    private String evidenceType;

    @Column(name = "source_ref_type", length = 64)
    private String sourceRefType;

    @Column(name = "source_ref_id", length = 256)
    private String sourceRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_code", length = 64)
    private GoldenChainBlockerCode blockerCode;

    @Column(name = "blocker_reason", length = 1024)
    private String blockerReason;

    public static GoldenChainStageSnapshotRecord fromContract(
        GoldenChainInstance instance,
        GoldenChainStageSnapshot snapshot
    ) {
        if (instance == null || instance.getId() == null) {
            throw new IllegalArgumentException("saved chain instance is required");
        }
        GoldenChainStageSnapshotRecord record = new GoldenChainStageSnapshotRecord();
        record.setChainInstanceId(instance.getId());
        record.setStage(snapshot.stage());
        record.setStatus(snapshot.status());
        record.setOwner(snapshot.owner());
        record.setEvidenceRef(snapshot.evidenceRef());
        record.setBlockerCode(snapshot.blockerCode());
        record.setBlockerReason(snapshot.blockerReason());
        return record;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChainInstanceId() {
        return chainInstanceId;
    }

    public void setChainInstanceId(UUID chainInstanceId) {
        if (chainInstanceId == null) {
            throw new IllegalArgumentException("chainInstanceId must not be null");
        }
        this.chainInstanceId = chainInstanceId;
    }

    public GoldenChainStage getStage() {
        return stage;
    }

    public void setStage(GoldenChainStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        this.stage = stage;
        this.stageSequence = stage.sequence();
    }

    public Integer getStageSequence() {
        return stageSequence;
    }

    public GoldenChainStageStatus getStatus() {
        return status;
    }

    public void setStatus(GoldenChainStageStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = normalizeRequired(owner, "owner");
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public void setEvidenceRef(String evidenceRef) {
        this.evidenceRef = normalize(evidenceRef);
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = normalize(evidenceType);
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

    public GoldenChainBlockerCode getBlockerCode() {
        return blockerCode;
    }

    public void setBlockerCode(GoldenChainBlockerCode blockerCode) {
        this.blockerCode = blockerCode;
    }

    public String getBlockerReason() {
        return blockerReason;
    }

    public void setBlockerReason(String blockerReason) {
        this.blockerReason = normalize(blockerReason);
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
