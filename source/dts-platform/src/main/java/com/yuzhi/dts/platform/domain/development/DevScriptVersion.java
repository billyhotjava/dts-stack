package com.yuzhi.dts.platform.domain.development;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "dev_script_version")
public class DevScriptVersion extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private DevScriptAsset asset;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "DRAFT";

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DevScriptAsset getAsset() {
        return asset;
    }

    public void setAsset(DevScriptAsset asset) {
        this.asset = asset;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }
}
