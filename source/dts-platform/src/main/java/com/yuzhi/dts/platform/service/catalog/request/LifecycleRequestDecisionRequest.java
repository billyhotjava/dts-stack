package com.yuzhi.dts.platform.service.catalog.request;

public class LifecycleRequestDecisionRequest {

    private Boolean approved;
    private String notes;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

