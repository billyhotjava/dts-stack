package com.yuzhi.dts.platform.service.governance.request;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QualityRuleUpsertRequest {

    private String code;
    private String name;
    private String type;
    private String category;
    private String description;
    private String owner;
    private String severity;
    private String dataLevel;
    private String executor;
    private String frequencyCron;
    private Boolean template;
    private Boolean enabled;
    private UUID datasetId;
    private Map<String, Object> definition;
    private List<QualityRuleBindingRequest> bindings;

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

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public void setDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }

    public List<QualityRuleBindingRequest> getBindings() {
        return bindings;
    }

    public void setBindings(List<QualityRuleBindingRequest> bindings) {
        this.bindings = bindings;
    }
}

