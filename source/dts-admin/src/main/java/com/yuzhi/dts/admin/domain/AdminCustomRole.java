package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "admin_custom_role")
public class AdminCustomRole extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope; // DEPARTMENT or INSTITUTE

    @Column(name = "operations", nullable = false, length = 64)
    private String operationsCsv; // comma separated

    @Column(name = "max_rows")
    private Integer maxRows;

    @Column(name = "allow_desensitize_json")
    private Boolean allowDesensitizeJson;

    @Column(name = "description", length = 1000)
    private String description;

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getOperationsCsv() { return operationsCsv; }
    public void setOperationsCsv(String operationsCsv) { this.operationsCsv = operationsCsv; }
    public Integer getMaxRows() { return maxRows; }
    public void setMaxRows(Integer maxRows) { this.maxRows = maxRows; }
    public Boolean getAllowDesensitizeJson() { return allowDesensitizeJson; }
    public void setAllowDesensitizeJson(Boolean allowDesensitizeJson) { this.allowDesensitizeJson = allowDesensitizeJson; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

