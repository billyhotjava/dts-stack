package com.yuzhi.dts.admin.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_action_catalog")
public class AuditActionCatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", length = 32, nullable = false)
    private String sourceSystem;

    @Column(name = "action_code", length = 128, nullable = false)
    private String actionCode;

    @Column(name = "module_key", length = 128, nullable = false)
    private String moduleKey;

    @Column(name = "module_name", length = 128, nullable = false)
    private String moduleName;

    @Column(name = "operation_code", length = 128, nullable = false)
    private String operationCode;

    @Column(name = "operation_name", length = 256, nullable = false)
    private String operationName;

    @Column(name = "operation_kind", length = 32, nullable = false)
    private String operationKind;

    @Column(name = "resource_type", length = 128)
    private String resourceType;

    @Column(name = "allow_empty_targets", nullable = false)
    private Boolean allowEmptyTargets = Boolean.FALSE;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

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

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
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

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getOperationKind() {
        return operationKind;
    }

    public void setOperationKind(String operationKind) {
        this.operationKind = operationKind;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Boolean getAllowEmptyTargets() {
        return allowEmptyTargets;
    }

    public void setAllowEmptyTargets(Boolean allowEmptyTargets) {
        this.allowEmptyTargets = allowEmptyTargets;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
