package com.yuzhi.dts.copilot.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_report_template")
public class AnalyticsReportTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "spec_json", columnDefinition = "text")
    private String specJson;

    @Column(name = "template_code", length = 128)
    private String templateCode;

    @Column(name = "domain", length = 128)
    private String domain;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "data_source_type", length = 64)
    private String dataSourceType;

    @Column(name = "target_object", length = 255)
    private String targetObject;

    @Column(name = "refresh_policy", length = 64)
    private String refreshPolicy;

    @Column(name = "permission_policy_json", columnDefinition = "text")
    private String permissionPolicyJson;

    @Column(name = "parameter_schema_json", columnDefinition = "text")
    private String parameterSchemaJson;

    @Column(name = "metric_definition_json", columnDefinition = "text")
    private String metricDefinitionJson;

    @Column(name = "presentation_schema_json", columnDefinition = "text")
    private String presentationSchemaJson;

    @Column(name = "certification_status", length = 32)
    private String certificationStatus;

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
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

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }

    public String getTargetObject() {
        return targetObject;
    }

    public void setTargetObject(String targetObject) {
        this.targetObject = targetObject;
    }

    public String getRefreshPolicy() {
        return refreshPolicy;
    }

    public void setRefreshPolicy(String refreshPolicy) {
        this.refreshPolicy = refreshPolicy;
    }

    public String getPermissionPolicyJson() {
        return permissionPolicyJson;
    }

    public void setPermissionPolicyJson(String permissionPolicyJson) {
        this.permissionPolicyJson = permissionPolicyJson;
    }

    public String getParameterSchemaJson() {
        return parameterSchemaJson;
    }

    public void setParameterSchemaJson(String parameterSchemaJson) {
        this.parameterSchemaJson = parameterSchemaJson;
    }

    public String getMetricDefinitionJson() {
        return metricDefinitionJson;
    }

    public void setMetricDefinitionJson(String metricDefinitionJson) {
        this.metricDefinitionJson = metricDefinitionJson;
    }

    public String getPresentationSchemaJson() {
        return presentationSchemaJson;
    }

    public void setPresentationSchemaJson(String presentationSchemaJson) {
        this.presentationSchemaJson = presentationSchemaJson;
    }

    public String getCertificationStatus() {
        return certificationStatus;
    }

    public void setCertificationStatus(String certificationStatus) {
        this.certificationStatus = certificationStatus;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
