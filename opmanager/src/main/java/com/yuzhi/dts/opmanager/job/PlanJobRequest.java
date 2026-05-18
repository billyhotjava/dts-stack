package com.yuzhi.dts.opmanager.job;

public record PlanJobRequest(String packageRegistrationId, String packageId, String note) {
    public String lookupId() {
        return packageRegistrationId == null || packageRegistrationId.isBlank() ? packageId : packageRegistrationId;
    }
}
