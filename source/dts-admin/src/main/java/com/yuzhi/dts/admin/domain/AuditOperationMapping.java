package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "audit_operation_mapping")
public class AuditOperationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_pattern", length = 512, nullable = false)
    private String urlPattern;

    @Column(name = "http_method", length = 16, nullable = false)
    private String httpMethod = "ALL";

    @Column(name = "status_code_regex", length = 32)
    private String statusCodeRegex;

    @Column(name = "module_name", length = 128, nullable = false)
    private String moduleName;

    @Column(name = "action_type", length = 32, nullable = false)
    private String actionType;

    @Column(name = "description_template", length = 1024, nullable = false)
    private String descriptionTemplate;

    @Column(name = "source_table_template", length = 256, nullable = false)
    private String sourceTableTemplate;

    // Use TEXT to store small JSON; avoid JDBC getClob() path on PostgreSQL
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "param_extractors", columnDefinition = "text")
    private String paramExtractors; // JSON string

    @Column(name = "event_class", length = 64)
    private String eventClass;

    @Column(name = "order_value")
    private Integer orderValue = 0;

    @Column(name = "enabled")
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrlPattern() { return urlPattern; }
    public void setUrlPattern(String urlPattern) { this.urlPattern = urlPattern; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getStatusCodeRegex() { return statusCodeRegex; }
    public void setStatusCodeRegex(String statusCodeRegex) { this.statusCodeRegex = statusCodeRegex; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getDescriptionTemplate() { return descriptionTemplate; }
    public void setDescriptionTemplate(String descriptionTemplate) { this.descriptionTemplate = descriptionTemplate; }
    public String getSourceTableTemplate() { return sourceTableTemplate; }
    public void setSourceTableTemplate(String sourceTableTemplate) { this.sourceTableTemplate = sourceTableTemplate; }
    public String getParamExtractors() { return paramExtractors; }
    public void setParamExtractors(String paramExtractors) { this.paramExtractors = paramExtractors; }
    public String getEventClass() { return eventClass; }
    public void setEventClass(String eventClass) { this.eventClass = eventClass; }
    public Integer getOrderValue() { return orderValue; }
    public void setOrderValue(Integer orderValue) { this.orderValue = orderValue; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
