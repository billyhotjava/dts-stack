package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_screen_access")
public class AnalyticsScreenAccess implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screen_id", nullable = false)
    private Long screenId;

    /** "USER" or "ROLE" */
    @Column(name = "grantee_type", nullable = false, length = 10)
    private String granteeType;

    /** For USER: String.valueOf(analyticsUser.id). For ROLE: role name string. */
    @Column(name = "grantee_id", nullable = false, length = 200)
    private String granteeId;

    /** "OWNER", "MANAGER", or "VIEWER" */
    @Column(name = "permission", nullable = false, length = 10)
    private String permission;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /**
     * 大屏密级越级共享：true 表示授予 grantee 越级查看权限——当 caller
     * 人员密级低于 screen.classification 时仍允许通过 ScreenPermissionService.snapshot()。
     * 仅对 VIEWER 类 grant 有意义（OWNER/MANAGER 本就豁免密级，业务层强制 false）。
     * 默认 false 不影响历史数据。
     */
    @Column(name = "level_override", nullable = false)
    private boolean levelOverride = false;

    @PrePersist
    void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }

    public String getGranteeType() { return granteeType; }
    public void setGranteeType(String granteeType) { this.granteeType = granteeType; }

    public String getGranteeId() { return granteeId; }
    public void setGranteeId(String granteeId) { this.granteeId = granteeId; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public boolean isLevelOverride() { return levelOverride; }
    public void setLevelOverride(boolean levelOverride) { this.levelOverride = levelOverride; }
}
