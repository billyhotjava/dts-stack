package com.yuzhi.dts.admin.service.auditv2;

public enum AuditResultStatus {
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败"),
    FAILED("FAILED", "失败"),
    PENDING("PENDING", "处理中"),
    /** Caller declared a result the audit system does not recognise. Never silently mapped to SUCCESS. */
    UNKNOWN("UNKNOWN", "未识别");

    private final String code;
    private final String displayName;

    AuditResultStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }
}
