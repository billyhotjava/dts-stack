package com.yuzhi.dts.platform.domain.development;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "dev_script_asset")
public class DevScriptAsset extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "script_type", length = 32, nullable = false)
    private String scriptType = "PYTHON";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "DRAFT";

    @Column(name = "latest_version_no", nullable = false)
    private Integer latestVersionNo = 1;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScriptType() {
        return scriptType;
    }

    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLatestVersionNo() {
        return latestVersionNo;
    }

    public void setLatestVersionNo(Integer latestVersionNo) {
        this.latestVersionNo = latestVersionNo;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
