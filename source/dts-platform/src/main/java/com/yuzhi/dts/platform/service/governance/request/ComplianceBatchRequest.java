package com.yuzhi.dts.platform.service.governance.request;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ComplianceBatchRequest {

    private String name;
    private String templateCode;
    private Boolean evidenceRequired;
    private String dataLevel;
    private List<UUID> ruleIds;
    private Map<String, Object> metadata;
    private String ownerDept;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Boolean getEvidenceRequired() {
        return evidenceRequired;
    }

    public void setEvidenceRequired(Boolean evidenceRequired) {
        this.evidenceRequired = evidenceRequired;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public List<UUID> getRuleIds() {
        return ruleIds;
    }

    public void setRuleIds(List<UUID> ruleIds) {
        this.ruleIds = ruleIds;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }
}
