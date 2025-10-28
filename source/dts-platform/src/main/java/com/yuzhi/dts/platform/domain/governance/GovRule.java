package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "gov_rule")
public class GovRule extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, unique = true)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "type", length = 64)
    private String type;

    @Column(name = "expression", length = 2048)
    private String expression;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "data_level", length = 32)
    private String dataLevel;

    @Column(name = "frequency_cron", length = 128)
    private String frequencyCron;

    @Column(name = "template")
    private Boolean template = Boolean.FALSE;

    @Column(name = "executor", length = 64)
    private String executor;

    @Column(name = "enabled")
    private Boolean enabled = Boolean.TRUE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_version_id")
    @JsonIgnoreProperties(value = { "rule", "bindings" }, allowSetters = true)
    private GovRuleVersion latestVersion;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = { "rule", "bindings" }, allowSetters = true)
    private Set<GovRuleVersion> versions = new LinkedHashSet<>();

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public String getFrequencyCron() {
        return frequencyCron;
    }

    public void setFrequencyCron(String frequencyCron) {
        this.frequencyCron = frequencyCron;
    }

    public Boolean getTemplate() {
        return template;
    }

    public void setTemplate(Boolean template) {
        this.template = template;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public GovRuleVersion getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(GovRuleVersion latestVersion) {
        this.latestVersion = latestVersion;
    }

    public Set<GovRuleVersion> getVersions() {
        return versions;
    }

    public void setVersions(Set<GovRuleVersion> versions) {
        this.versions = versions;
    }
}
