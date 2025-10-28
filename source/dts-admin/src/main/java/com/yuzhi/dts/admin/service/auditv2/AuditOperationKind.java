package com.yuzhi.dts.admin.service.auditv2;

import java.util.Locale;

public enum AuditOperationKind {
    QUERY("QUERY", "查询"),
    CREATE("CREATE", "新增"),
    UPDATE("UPDATE", "修改"),
    DELETE("DELETE", "删除"),
    APPROVE("APPROVE", "批准"),
    REJECT("REJECT", "驳回"),
    EXPORT("EXPORT", "导出"),
    IMPORT("IMPORT", "导入"),
    EXECUTE("EXECUTE", "执行"),
    LOGIN("LOGIN", "登录"),
    LOGOUT("LOGOUT", "登出"),
    DOWNLOAD("DOWNLOAD", "下载"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String displayName;

    AuditOperationKind(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static AuditOperationKind fromHttpMethod(String method) {
        if (method == null) {
            return OTHER;
        }
        return switch (method.trim().toUpperCase(Locale.ROOT)) {
            case "GET" -> QUERY;
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> OTHER;
        };
    }
}
