package com.yuzhi.dts.platform.service.audit;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps free-form action / payload text to the canonical operation-type token used by the audit
 * pipeline (CREATE, UPDATE, DELETE, EXPORT, ...).
 * <p>
 * Extracted from {@code AuditService} so the canonicalisation rules can be tested and evolved
 * independently of the persistence pipeline. The mapping deliberately matches both English
 * verbs (create / delete / export) and Chinese phrases (新增 / 删除 / 导出) because audit summaries
 * arrive in either language depending on the caller.
 */
@Component
public class OperationTypeNormalizer {

    public String deriveOperationType(String action, Map<String, Object> payload) {
        String direct = extractText(payload, "operationType");
        if (!StringUtils.hasText(direct)) {
            direct = extractText(payload, "operation_type");
        }
        if (StringUtils.hasText(direct)) {
            return canonicalOperationType(direct);
        }
        if (StringUtils.hasText(action)) {
            return canonicalOperationType(action);
        }
        String summary = extractText(payload, "summary");
        if (StringUtils.hasText(summary)) {
            return canonicalOperationType(summary);
        }
        return "READ";
    }

    public String canonicalOperationType(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "READ";
        }
        String trimmed = candidate.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (upper.contains("LOGIN") || containsAny(lower, "登录", "登入")) {
            return "LOGIN";
        }
        if (upper.contains("LOGOUT") || containsAny(lower, "登出", "退出登录", "注销登录")) {
            return "LOGOUT";
        }
        if (upper.contains("DOWNLOAD") || containsAny(lower, "下载", "download")) {
            return "DOWNLOAD";
        }
        if (upper.contains("UPLOAD") || containsAny(lower, "上传", "upload")) {
            return "UPLOAD";
        }
        if (upper.contains("EXPORT") || containsAny(lower, "导出", "export")) {
            return "EXPORT";
        }
        if (upper.contains("IMPORT") || containsAny(lower, "导入", "import")) {
            return "IMPORT";
        }
        if (upper.contains("GRANT") || containsAny(lower, "授权", "共享", "grant")) {
            return "GRANT";
        }
        if (upper.contains("REVOKE") || containsAny(lower, "撤销授权", "取消授权", "收回", "回收", "revoke")) {
            return "REVOKE";
        }
        if (upper.contains("ENABLE") || containsAny(lower, "启用", "开启", "激活", "enable")) {
            return "ENABLE";
        }
        if (upper.contains("DISABLE") || containsAny(lower, "禁用", "停用", "关闭", "失效", "disable")) {
            return "DISABLE";
        }
        if (
            upper.contains("CLEAN") ||
            upper.contains("PURGE") ||
            containsAny(lower, "清理", "清除", "清空", "清扫", "purge", "cleanup")
        ) {
            return "CLEAN";
        }
        if (upper.contains("ARCHIVE") || containsAny(lower, "归档", "封存", "archive")) {
            return "ARCHIVE";
        }
        if (upper.contains("PUBLISH") || containsAny(lower, "发布", "publish")) {
            return "PUBLISH";
        }
        if (upper.contains("APPROVE") || containsAny(lower, "批准", "审批通过")) {
            return "APPROVE";
        }
        if (upper.contains("REJECT") || containsAny(lower, "拒绝", "驳回")) {
            return "REJECT";
        }
        if (
            upper.contains("EXECUTE") ||
            upper.contains("RUN") ||
            containsAny(lower, "执行", "运行", "run", "apply")
        ) {
            return "EXECUTE";
        }
        if (upper.contains("REFRESH") || containsAny(lower, "刷新", "refresh")) {
            return "REFRESH";
        }
        if (upper.contains("TEST") || containsAny(lower, "测试", "校验", "验证", "test")) {
            return "TEST";
        }
        if (
            upper.contains("CREATE") ||
            upper.contains("ADD") ||
            upper.contains("NEW") ||
            containsAny(lower, "新增", "新建", "创建", "提交", "申请")
        ) {
            return "CREATE";
        }
        if (upper.contains("DELETE") || containsAny(lower, "删除", "移除", "下线", "注销")) {
            return "DELETE";
        }
        if (
            upper.contains("UPDATE") ||
            upper.contains("MODIFY") ||
            upper.contains("EDIT") ||
            upper.contains("SAVE") ||
            containsAny(lower, "修改", "更新", "调整", "保存", "编辑", "配置")
        ) {
            return "UPDATE";
        }
        if (
            upper.contains("READ") ||
            upper.contains("QUERY") ||
            upper.contains("GET") ||
            containsAny(lower, "查看", "查询", "预览", "浏览", "列表", "检索")
        ) {
            return "READ";
        }
        return "READ";
    }

    private static boolean containsAny(String source, String... needles) {
        if (!StringUtils.hasText(source) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String extractText(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
