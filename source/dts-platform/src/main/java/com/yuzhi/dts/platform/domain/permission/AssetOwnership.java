package com.yuzhi.dts.platform.domain.permission;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@Entity
@Table(name = "asset_ownership")
public class AssetOwnership extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Column(name = "asset_type", length = 32, nullable = false)
    private String assetType;

    @NotBlank
    @Column(name = "asset_id", length = 128, nullable = false)
    private String assetId;

    @NotBlank
    @Column(name = "owner_dept_code", length = 128, nullable = false)
    private String ownerDeptCode;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "assigned_by", length = 128)
    private String assignedBy;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getOwnerDeptCode() {
        return ownerDeptCode;
    }

    public void setOwnerDeptCode(String ownerDeptCode) {
        this.ownerDeptCode = ownerDeptCode;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }
}
