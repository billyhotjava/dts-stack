package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gov_quality_template")
public class GovQualityTemplate extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "param_schema", columnDefinition = "jsonb")
    private List<Map<String, Object>> paramSchema;

    @Column(name = "sql_template", columnDefinition = "text", nullable = false)
    private String sqlTemplate;

    @Column(name = "severity_default", length = 16)
    private String severityDefault;

    @Column(name = "action_default", length = 16)
    private String actionDefault;

    @Column(name = "dialect", length = 16)
    private String dialect;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin = Boolean.FALSE;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Map<String, Object>> getParamSchema() {
        return paramSchema;
    }

    public void setParamSchema(List<Map<String, Object>> paramSchema) {
        this.paramSchema = paramSchema;
    }

    public String getSqlTemplate() {
        return sqlTemplate;
    }

    public void setSqlTemplate(String sqlTemplate) {
        this.sqlTemplate = sqlTemplate;
    }

    public String getSeverityDefault() {
        return severityDefault;
    }

    public void setSeverityDefault(String severityDefault) {
        this.severityDefault = severityDefault;
    }

    public String getActionDefault() {
        return actionDefault;
    }

    public void setActionDefault(String actionDefault) {
        this.actionDefault = actionDefault;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
