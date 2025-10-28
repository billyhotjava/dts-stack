package com.yuzhi.dts.admin.service.auditv2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 将 ChangeSnapshot 内容转换成面向审计展示的中文对照列表。
 */
@Component
public class ChangeSnapshotFormatter {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, FieldMeta>> dictionary;
    private final Map<String, String> genericValueDictionary;
    private static final Set<String> KEYCLOAK_DEFAULT_REALM_ROLE_NAMES = Set.of("offline_access", "uma_authorization");

    public ChangeSnapshotFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.dictionary = buildDictionary();
        this.genericValueDictionary = buildGenericValueDictionary();
    }

    public List<Map<String, String>> format(ChangeSnapshot snapshot, String resourceType) {
        if (snapshot == null) {
            return List.of();
        }
        ChangeSnapshot sanitizedSnapshot = ChangeSnapshot.of(
            sanitizeRealmRoles(snapshot.getBefore()),
            sanitizeRealmRoles(snapshot.getAfter())
        );
        List<ChangeSnapshot.FieldChange> changes = sanitizedSnapshot.getChanges();
        if (changes == null || changes.isEmpty()) {
            ChangeSnapshot recalculated = ChangeSnapshot.of(
                sanitizeRealmRoles(snapshot.getBefore()),
                sanitizeRealmRoles(snapshot.getAfter())
            );
            changes = recalculated.getChanges();
        }
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        Map<String, FieldMeta> resourceDictionary = dictionary.getOrDefault(normalizeResource(resourceType), Map.of());
        List<Map<String, String>> result = new ArrayList<>();
        for (ChangeSnapshot.FieldChange change : changes) {
            if (change == null || StringUtils.isBlank(change.field())) {
                continue;
            }
            String field = change.field();
            FieldMeta meta = resourceDictionary.getOrDefault(field, FieldMeta.fallback(field));
            String beforeText = translateValue(change.before(), meta);
            String afterText = translateValue(change.after(), meta);
            if (Objects.equals(beforeText, afterText)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("label", meta.label);
            item.put("before", beforeText);
            item.put("after", afterText);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, String>> format(Map<String, Object> before, Map<String, Object> after, String resourceType) {
        ChangeSnapshot snapshot = ChangeSnapshot.of(
            sanitizeRealmRoles(before),
            sanitizeRealmRoles(after)
        );
        return format(snapshot, resourceType);
    }

    private Map<String, FieldMeta> portalMenuDictionary() {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        map.put("name", FieldMeta.label("菜单名称"));
        map.put("path", FieldMeta.label("访问路径"));
        map.put("component", FieldMeta.label("前端组件"));
        map.put("icon", FieldMeta.label("菜单图标"));
        map.put("sortOrder", FieldMeta.label("排序值"));
        map.put("metadata", FieldMeta.label("扩展配置"));
        map.put("securityLevel", FieldMeta.mapping("访问密级", Map.of(
            "INTERNAL", "内部",
            "GENERAL", "一般",
            "IMPORTANT", "重要",
            "CORE", "核心",
            "PUBLIC", "公开",
            "NON_SECRET", "非密"
        )));
        map.put("maxDataLevel", FieldMeta.mapping("最大数据密级", Map.of(
            "INTERNAL", "内部",
            "GENERAL", "一般",
            "IMPORTANT", "重要",
            "CORE", "核心",
            "PUBLIC", "公开",
            "NON_SECRET", "非密"
        )));
        map.put("deleted", FieldMeta.mapping("禁用状态", Map.of(
            "true", "禁用",
            "false", "启用"
        )));
        map.put("enabled", FieldMeta.mapping("启用状态", Map.of(
            "true", "启用",
            "false", "停用"
        )));
        map.put("allowedRoles", FieldMeta.label("允许角色"));
        map.put("allowedPermissions", FieldMeta.label("允许权限"));
        map.put("visibilityRules", FieldMeta.label("可见性规则"));
        map.put("allowedOrgCodes", FieldMeta.label("可见组织"));
        return map;
    }

    private Map<String, FieldMeta> userDictionary() {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        map.put("fullName", FieldMeta.label("姓名"));
        map.put("email", FieldMeta.label("邮箱"));
        map.put("phone", FieldMeta.label("手机号"));
        map.put("personSecurityLevel", FieldMeta.mapping("人员密级", Map.of(
            "GENERAL", "一般",
            "IMPORTANT", "重要",
            "CORE", "核心",
            "NON_SECRET", "非密"
        )));
        map.put("enabled", FieldMeta.mapping("启用状态", Map.of(
            "true", "启用",
            "false", "停用"
        )));
        map.put("groupPaths", FieldMeta.label("所属组织"));
        map.put("realmRoles", FieldMeta.label("角色"));
        return map;
    }

    private Map<String, FieldMeta> roleDictionary() {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        map.put("name", FieldMeta.label("角色名称"));
        map.put("description", FieldMeta.label("角色描述"));
        map.put("permissions", FieldMeta.label("权限列表"));
        map.put("enabled", FieldMeta.mapping("启用状态", Map.of(
            "true", "启用",
            "false", "停用"
        )));
        return map;
    }

    private Map<String, FieldMeta> datasetDictionary() {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        map.put("name", FieldMeta.label("数据集名称"));
        map.put("description", FieldMeta.label("数据集描述"));
        map.put("owner", FieldMeta.label("归属人"));
        map.put("ownerDept", FieldMeta.label("归属部门"));
        map.put("departmentName", FieldMeta.label("归属部门"));
        map.put("securityLevel", FieldMeta.label("访问密级"));
        map.put("classification", FieldMeta.mapping("数据密级", Map.of(
            "PUBLIC", "公开",
            "INTERNAL", "内部",
            "GENERAL", "一般",
            "IMPORTANT", "重要",
            "SECRET", "秘密",
            "CONFIDENTIAL", "机密",
            "CORE", "核心",
            "NON_SECRET", "非密"
        )));
        map.put("tags", FieldMeta.label("标签"));
        map.put("domainId", FieldMeta.label("所属数据域ID"));
        map.put("domainName", FieldMeta.label("所属数据域"));
        map.put("type", FieldMeta.mapping("数据源类型", Map.of(
            "HIVE", "Hive",
            "JDBC", "JDBC",
            "INCEPTOR", "Inceptor",
            "FILE", "文件",
            "API", "API",
            "VIEW", "视图"
        )));
        map.put("hiveDatabase", FieldMeta.label("Hive 库"));
        map.put("hiveTable", FieldMeta.label("Hive 表"));
        map.put("trinoCatalog", FieldMeta.label("Trino Catalog"));
        map.put("exposedBy", FieldMeta.mapping("对外方式", Map.of(
            "VIEW", "视图",
            "RANGER", "Ranger 策略",
            "API", "开放接口"
        )));
        map.put("enabled", FieldMeta.mapping("启用状态", Map.of(
            "true", "启用",
            "false", "停用"
        )));
        return map;
    }

    private Map<String, FieldMeta> dataSourceDictionary() {
        Map<String, FieldMeta> map = new LinkedHashMap<>();
        map.put("name", FieldMeta.label("数据源名称"));
        map.put("type", FieldMeta.label("数据源类型"));
        map.put("jdbcUrl", FieldMeta.label("JDBC 地址"));
        map.put("username", FieldMeta.label("连接用户名"));
        map.put("description", FieldMeta.label("描述"));
        map.put("props", FieldMeta.label("连接参数"));
        map.put("status", FieldMeta.mapping("状态", Map.of(
            "ACTIVE", "可用",
            "INACTIVE", "不可用",
            "UNKNOWN", "未知",
            "DISABLED", "已禁用"
        )));
        map.put("hasSecrets", FieldMeta.mapping("包含密钥", Map.of(
            "true", "是",
            "false", "否"
        )));
        map.put("engineVersion", FieldMeta.label("引擎版本"));
        map.put("driverVersion", FieldMeta.label("驱动版本"));
        map.put("lastTestElapsedMillis", FieldMeta.label("最近测试耗时(ms)"));
        map.put("lastVerifiedAt", FieldMeta.label("最近验证时间"));
        map.put("lastHeartbeatAt", FieldMeta.label("最近心跳时间"));
        map.put("heartbeatStatus", FieldMeta.mapping("心跳状态", Map.of(
            "UP", "正常",
            "DOWN", "异常",
            "UNKNOWN", "未知"
        )));
        map.put("heartbeatFailureCount", FieldMeta.label("连续失败次数"));
        map.put("lastError", FieldMeta.label("最新错误信息"));
        return map;
    }

    private Map<String, Map<String, FieldMeta>> buildDictionary() {
        Map<String, Map<String, FieldMeta>> map = new LinkedHashMap<>();
        map.put("PORTAL_MENU", portalMenuDictionary());
        map.put("MENU", portalMenuDictionary());
        map.put("ADMIN_KEYCLOAK_USER", userDictionary());
        map.put("USER", userDictionary());
        map.put("ROLE", roleDictionary());
        map.put("CUSTOM_ROLE", roleDictionary());
        map.put("CATALOG_DATASET", datasetDictionary());
        map.put("INFRA_DATA_SOURCE", dataSourceDictionary());
        map.put("ADMIN_DATA_SOURCE", dataSourceDictionary());
        return map;
    }

    private Map<String, String> buildGenericValueDictionary() {
        return Map.of(
            "true", "是",
            "false", "否",
            "ENABLED", "启用",
            "DISABLED", "禁用",
            "APPROVED", "批准",
            "REJECTED", "驳回",
            "PENDING", "处理中"
        );
    }

    private String translateValue(Object value, FieldMeta meta) {
        if (value == null) {
            return "";
        }
        if (meta.mapping != null) {
            String key = String.valueOf(value);
            String mapped = meta.mapping.get(key);
            if (mapped != null) {
                return mapped;
            }
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stringify).filter(StringUtils::isNotBlank).reduce((a, b) -> a + "，" + b).orElse("");
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            } catch (JsonProcessingException ignored) {}
        }
        if (value instanceof Boolean bool) {
            return bool ? "是" : "否";
        }
        String text = stringify(value);
        if (!text.isEmpty()) {
            String mapped = genericValueDictionary.get(text.toUpperCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        return text;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString().trim();
        return str;
    }

    private Map<String, Object> sanitizeRealmRoles(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> nested = new LinkedHashMap<>();
                mapValue.forEach((k, v) -> nested.put(String.valueOf(k), v));
                sanitized.put(key, sanitizeRealmRoles(nested));
            } else if ("realmRoles".equalsIgnoreCase(key)) {
                if (value instanceof Collection<?> collection) {
                    sanitized.put(key, filterRealmRoles(collection));
                } else if (value instanceof String str) {
                    if (!isKeycloakDefaultRealmRole(str)) {
                        sanitized.put(key, str);
                    }
                }
            } else if (value instanceof Collection<?> collection) {
                List<Object> list = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> nested = new LinkedHashMap<>();
                        itemMap.forEach((k, v) -> nested.put(String.valueOf(k), v));
                        list.add(sanitizeRealmRoles(nested));
                    } else {
                        list.add(item);
                    }
                }
                sanitized.put(key, list);
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private List<String> filterRealmRoles(Collection<?> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (Object item : roles) {
            if (item == null) {
                continue;
            }
            String role = item.toString();
            if (!isKeycloakDefaultRealmRole(role)) {
                filtered.add(role);
            }
        }
        return filtered;
    }

    private boolean isKeycloakDefaultRealmRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (KEYCLOAK_DEFAULT_REALM_ROLE_NAMES.contains(lower)) {
            return true;
        }
        return lower.startsWith("default-roles-");
    }

    private String normalizeResource(String resourceType) {
        if (StringUtils.isBlank(resourceType)) {
            return "UNKNOWN";
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    private record FieldMeta(String label, Map<String, String> mapping) {
        static FieldMeta label(String label) {
            return new FieldMeta(label, null);
        }

        static FieldMeta mapping(String label, Map<String, String> mapping) {
            return new FieldMeta(label, mapping);
        }

        static FieldMeta fallback(String field) {
            String normalized = field.contains("_")
                ? field.replace('_', ' ')
                : field.replaceAll("([A-Z])", " $1");
            return new FieldMeta(normalized.trim(), null);
        }
    }
}
