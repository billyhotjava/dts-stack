package com.yuzhi.dts.platform.domain.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_permission_policy_injection")
public class AssetPermissionPolicyInjection implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "actor", length = 128)
    private String actor;

    @Column(name = "asset_type", length = 32)
    private String assetType;

    @Column(name = "asset_id", length = 256)
    private String assetId;

    @Column(name = "action", length = 32)
    private String action;

    @Column(name = "predicates", columnDefinition = "text")
    private String predicates;

    @Column(name = "masked_columns", columnDefinition = "text")
    private String maskedColumns;

    @Column(name = "policy_source", length = 128)
    private String policySource;

    @Column(name = "predicate_hash", length = 96)
    private String predicateHash;

    @Column(name = "direction", length = 16)
    private String direction;

    @Column(name = "pack_id", length = 128)
    private String packId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPredicates() {
        return predicates;
    }

    public void setPredicates(String predicates) {
        this.predicates = predicates;
    }

    public String getMaskedColumns() {
        return maskedColumns;
    }

    public void setMaskedColumns(String maskedColumns) {
        this.maskedColumns = maskedColumns;
    }

    public String getPolicySource() {
        return policySource;
    }

    public void setPolicySource(String policySource) {
        this.policySource = policySource;
    }

    public String getPredicateHash() {
        return predicateHash;
    }

    public void setPredicateHash(String predicateHash) {
        this.predicateHash = predicateHash;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getPackId() {
        return packId;
    }

    public void setPackId(String packId) {
        this.packId = packId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
