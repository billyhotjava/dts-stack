package com.yuzhi.dts.admin.service.auditv2;

import java.util.Arrays;
import java.util.Locale;

/**
 * 统一的操作类型枚举，供规则引擎和审计展示使用。
 */
public enum AuditOperationType {
    CREATE("CREATE", "新增", true),
    UPDATE("UPDATE", "修改", true),
    DELETE("DELETE", "删除", true),
    READ("READ", "查询", false),
    LIST("LIST", "列表查询", false),
    LOGIN("LOGIN", "登录", false),
    LOGOUT("LOGOUT", "登出", false),
    EXPORT("EXPORT", "导出", false),
    EXECUTE("EXECUTE", "执行", false),
    GRANT("GRANT", "授权", true),
    ENABLE("ENABLE", "启用", true),
    DISABLE("DISABLE", "禁用", true),
    APPROVE("APPROVE", "批准", true),
    REJECT("REJECT", "拒绝", true),
    REVOKE("REVOKE", "撤销", true),
    PUBLISH("PUBLISH", "发布", false),
    REFRESH("REFRESH", "刷新", false),
    REQUEST("REQUEST", "申请", false),
    TEST("TEST", "测试", false),
    UNKNOWN("UNKNOWN", "操作", false);

    private final String code;
    private final String displayName;
    private final boolean mutating;

    AuditOperationType(String code, String displayName, boolean mutating) {
        this.code = code;
        this.displayName = displayName;
        this.mutating = mutating;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresTarget() {
        return mutating;
    }

    public static AuditOperationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(type -> type.code.equals(normalized))
            .findFirst()
            .orElseGet(() -> mapLegacy(normalized));
    }

    private static AuditOperationType mapLegacy(String normalized) {
        return switch (normalized) {
            case "新增", "CREATE" -> CREATE;
            case "修改", "UPDATE" -> UPDATE;
            case "删除", "DELETE" -> DELETE;
            case "查询", "READ" -> READ;
            case "列表", "LIST" -> LIST;
            case "登录", "LOGIN" -> LOGIN;
            case "登出", "LOGOUT" -> LOGOUT;
            case "导出", "EXPORT" -> EXPORT;
            case "执行", "运行", "EXECUTE" -> EXECUTE;
            case "授权", "GRANT" -> GRANT;
            case "启用", "ENABLE" -> ENABLE;
            case "禁用", "DISABLE" -> DISABLE;
            case "批准", "APPROVE" -> APPROVE;
            case "拒绝", "REJECT" -> REJECT;
            case "撤销", "REVOKE" -> REVOKE;
            case "发布", "PUBLISH" -> PUBLISH;
            case "刷新", "REFRESH" -> REFRESH;
            case "申请", "REQUEST" -> REQUEST;
            case "测试", "TEST" -> TEST;
            default -> UNKNOWN;
        };
    }
}
