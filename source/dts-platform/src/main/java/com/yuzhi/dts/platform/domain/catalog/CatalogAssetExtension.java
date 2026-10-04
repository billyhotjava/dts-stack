package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_asset_extension")
public class CatalogAssetExtension extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "om_asset_id")
    private OpenMetadataAssetCache omAsset;

    @Column(name = "legacy_dataset_id", columnDefinition = "uuid")
    private UUID legacyDatasetId;

    @Column(name = "domain_id", columnDefinition = "uuid")
    private UUID domainId;

    @Column(name = "classification", length = 32)
    private String classification;

    @Column(name = "warehouse_layer", length = 32)
    private String warehouseLayer;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "business_owner", length = 128)
    private String businessOwner;

    @Column(name = "lifecycle_status", length = 32)
    private String lifecycleStatus;

    @Column(name = "governance_status", length = 32, nullable = false)
    private String governanceStatus = "PENDING_GOVERNANCE";

    @Column(name = "security_policy_refs", columnDefinition = "text")
    private String securityPolicyRefs;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OpenMetadataAssetCache getOmAsset() {
        return omAsset;
    }

    public void setOmAsset(OpenMetadataAssetCache omAsset) {
        this.omAsset = omAsset;
    }

    public UUID getLegacyDatasetId() {
        return legacyDatasetId;
    }

    public void setLegacyDatasetId(UUID legacyDatasetId) {
        this.legacyDatasetId = legacyDatasetId;
    }

    public UUID getDomainId() {
        return domainId;
    }

    public void setDomainId(UUID domainId) {
        this.domainId = domainId;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getWarehouseLayer() {
        return warehouseLayer;
    }

    public void setWarehouseLayer(String warehouseLayer) {
        this.warehouseLayer = warehouseLayer;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getBusinessOwner() {
        return businessOwner;
    }

    public void setBusinessOwner(String businessOwner) {
        this.businessOwner = businessOwner;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getGovernanceStatus() {
        return governanceStatus;
    }

    public void setGovernanceStatus(String governanceStatus) {
        this.governanceStatus = governanceStatus;
    }

    public String getSecurityPolicyRefs() {
        return securityPolicyRefs;
    }

    public void setSecurityPolicyRefs(String securityPolicyRefs) {
        this.securityPolicyRefs = securityPolicyRefs;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
