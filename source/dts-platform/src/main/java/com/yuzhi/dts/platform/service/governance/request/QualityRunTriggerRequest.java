package com.yuzhi.dts.platform.service.governance.request;

import java.util.Map;
import java.util.UUID;

public class QualityRunTriggerRequest {

    private UUID ruleId;
    private UUID bindingId;
    private String triggerType = "MANUAL";
    private Map<String, Object> parameters;

    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getBindingId() {
        return bindingId;
    }

    public void setBindingId(UUID bindingId) {
        this.bindingId = bindingId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}

