package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "admin_role_assignment")
public class AdminRoleAssignment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "username", nullable = false)
    private String username;

    /**
     * F11-T02：稳定账号键（Keycloak kc_id）。双写过渡期可空；范围/动作列
     * （scope_org_id、dataset_ids、operations）保持原语义，随本列整体迁移。
     */
    @Column(name = "keycloak_id", length = 64)
    private String keycloakId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "user_security_level", nullable = false)
    private String userSecurityLevel; // GENERAL/IMPORTANT/CORE

    @Column(name = "scope_org_id")
    private Long scopeOrgId; // null => institute scope

    @Lob
    @Column(name = "dataset_ids")
    private String datasetIdsCsv; // comma-separated dataset ids

    @Column(name = "operations", nullable = false)
    private String operationsCsv; // comma-separated read/write/export

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getKeycloakId() { return keycloakId; }
    public void setKeycloakId(String keycloakId) { this.keycloakId = keycloakId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getUserSecurityLevel() { return userSecurityLevel; }
    public void setUserSecurityLevel(String userSecurityLevel) { this.userSecurityLevel = userSecurityLevel; }
    public Long getScopeOrgId() { return scopeOrgId; }
    public void setScopeOrgId(Long scopeOrgId) { this.scopeOrgId = scopeOrgId; }
    public String getDatasetIdsCsv() { return datasetIdsCsv; }
    public void setDatasetIdsCsv(String datasetIdsCsv) { this.datasetIdsCsv = datasetIdsCsv; }
    public String getOperationsCsv() { return operationsCsv; }
    public void setOperationsCsv(String operationsCsv) { this.operationsCsv = operationsCsv; }
}
