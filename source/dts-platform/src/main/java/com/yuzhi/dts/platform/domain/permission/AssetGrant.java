package com.yuzhi.dts.platform.domain.permission;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "asset_grant")
public class AssetGrant extends AbstractAuditingEntity<Long> implements Serializable {

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
    @Column(name = "grantee_type", length = 16, nullable = false)
    private String granteeType;

    @NotBlank
    @Column(name = "grantee_id", length = 128, nullable = false)
    private String granteeId;

    @NotBlank
    @Column(name = "permission", length = 16, nullable = false)
    private String permission;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @NotBlank
    @Column(name = "granted_by", length = 128, nullable = false)
    private String grantedBy;

    @Column(name = "grant_reason", length = 512)
    private String grantReason;

    /**
     * 大屏密级越级共享标志位。
     * 当 grantee 的人员密级低于资产密级时，必须置为 true，
     * DashboardAccessGuard 据此放行越级访问；默认 false 不影响普通授权。
     */
    @Column(name = "level_override", nullable = false)
    private boolean levelOverride = false;

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

    public String getGranteeType() {
        return granteeType;
    }

    public void setGranteeType(String granteeType) {
        this.granteeType = granteeType;
    }

    public String getGranteeId() {
        return granteeId;
    }

    public void setGranteeId(String granteeId) {
        this.granteeId = granteeId;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
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

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getGrantReason() {
        return grantReason;
    }

    public void setGrantReason(String grantReason) {
        this.grantReason = grantReason;
    }

    public boolean isLevelOverride() {
        return levelOverride;
    }

    public void setLevelOverride(boolean levelOverride) {
        this.levelOverride = levelOverride;
    }

    public boolean isValid() {
        Instant now = Instant.now();
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return true;
    }
}
