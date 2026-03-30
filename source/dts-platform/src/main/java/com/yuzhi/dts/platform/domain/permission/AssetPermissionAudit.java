package com.yuzhi.dts.platform.domain.permission;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "asset_permission_audit")
public class AssetPermissionAudit implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Column(name = "action", length = 32, nullable = false)
    private String action;

    @Column(name = "asset_type", length = 32)
    private String assetType;

    @Column(name = "asset_id", length = 128)
    private String assetId;

    @Column(name = "target_user", length = 128)
    private String targetUser;

    @Column(name = "permission", length = 16)
    private String permission;

    @NotBlank
    @Column(name = "operator", length = 128, nullable = false)
    private String operator;

    @Column(name = "oa_reference", length = 128)
    private String oaReference;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "created_date")
    private Instant createdDate;

    @PrePersist
    public void prePersist() {
        if (createdDate == null) {
            createdDate = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOaReference() {
        return oaReference;
    }

    public void setOaReference(String oaReference) {
        this.oaReference = oaReference;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
}
