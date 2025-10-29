package com.yuzhi.dts.admin.service.auditv2;

import java.util.Locale;

public enum AuditOperationKind {
    QUERY("QUERY", "查询"),
    CREATE("CREATE", "新增"),
    UPDATE("UPDATE", "修改"),
    ENABLE("ENABLE", "启用"),
    DISABLE("DISABLE", "禁用"),
    DELETE("DELETE", "删除"),
    CLEAN("CLEAN", "清理"),
    APPROVE("APPROVE", "批准"),
    REJECT("REJECT", "驳回"),
    EXPORT("EXPORT", "导出"),
    IMPORT("IMPORT", "导入"),
    UPLOAD("UPLOAD", "上传"),
    DOWNLOAD("DOWNLOAD", "下载"),
    REFRESH("REFRESH", "刷新"),
    TEST("TEST", "测试"),
    GRANT("GRANT", "授权"),
    REVOKE("REVOKE", "撤销"),
    EXECUTE("EXECUTE", "执行"),
    LOGIN("LOGIN", "登录"),
    LOGOUT("LOGOUT", "登出"),
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
