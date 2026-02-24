package com.yuzhi.dts.platform.service.governance.request;

public class QualityRuleVersionStatusRequest {

    private String status;
    private String notes;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
