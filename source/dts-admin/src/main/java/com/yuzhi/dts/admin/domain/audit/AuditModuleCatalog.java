package com.yuzhi.dts.admin.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_module_catalog")
public class AuditModuleCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", length = 32, nullable = false)
    private String sourceSystem;

    @Column(name = "module_key", length = 128, nullable = false)
    private String moduleKey;

    @Column(name = "module_name", length = 128, nullable = false)
    private String moduleName;

    @Column(name = "parent_module_key", length = 128)
    private String parentModuleKey;

    @Column(name = "owner", length = 128)
    private String owner;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "order_value")
    private Integer orderValue = 0;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getParentModuleKey() {
        return parentModuleKey;
    }

    public void setParentModuleKey(String parentModuleKey) {
        this.parentModuleKey = parentModuleKey;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getOrderValue() {
        return orderValue;
    }

    public void setOrderValue(Integer orderValue) {
        this.orderValue = orderValue;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
