package com.yuzhi.dts.admin.service.dto.keycloak;

import java.util.List;

public class ApprovalDTOs {
    public static class ApprovalRequest {
        public long id;
        public String requester;
        public String type;
        public String reason;
        public String createdAt;
        public String decidedAt;
        public String status;
        public String approver;
        public String decisionNote;
        public String errorMessage;
        public int retryCount;
        public String category;
    }

    public static class ApprovalItem {
        public long id;
        public String targetKind;
        public String targetId;
        public int seqNumber;
        public String payload;
    }

    public static class ApprovalRequestDetail extends ApprovalRequest {
        public List<ApprovalItem> items;
    }

    public static class ApprovalActionRequest {
        public String approver;
        public String note;
    }
}
