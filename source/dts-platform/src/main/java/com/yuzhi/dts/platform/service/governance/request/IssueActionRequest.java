package com.yuzhi.dts.platform.service.governance.request;

import java.util.List;

public class IssueActionRequest {

    private String actionType;
    private String notes;
    private List<String> attachments;

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }
}

