package com.yuzhi.dts.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "admin_workflow_template")
public class AdminWorkflowTemplate extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "owner_scope", nullable = false, length = 16)
    private String ownerScope = "ANY";

    @Column(name = "classification_min", length = 32)
    private String classificationMin;

    @Column(name = "classification_max", length = 32)
    private String classificationMax;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getOwnerScope() {
        return ownerScope;
    }

    public void setOwnerScope(String ownerScope) {
        this.ownerScope = ownerScope;
    }

    public String getClassificationMin() {
        return classificationMin;
    }

    public void setClassificationMin(String classificationMin) {
        this.classificationMin = classificationMin;
    }

    public String getClassificationMax() {
        return classificationMax;
    }

    public void setClassificationMax(String classificationMax) {
        this.classificationMax = classificationMax;
    }
}

