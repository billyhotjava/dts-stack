package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    public ChangeSnapshotFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.dictionary = buildDictionary();
        this.genericValueDictionary = buildGenericValueDictionary();
    }

    public List<Map<String, String>> format(ChangeSnapshot snapshot, String resourceType) {
        if (snapshot == null) {
            return List.of();
        }
        List<ChangeSnapshot.FieldChange> changes = snapshot.getChanges();
        if (changes == null || changes.isEmpty()) {
            ChangeSnapshot recalculated = ChangeSnapshot.of(snapshot.getBefore(), snapshot.getAfter());
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
        ChangeSnapshot snapshot = ChangeSnapshot.of(before, after);
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
        map.put("departmentName", FieldMeta.label("归属部门"));
        map.put("securityLevel", FieldMeta.label("访问密级"));
        map.put("tags", FieldMeta.label("标签"));
        map.put("enabled", FieldMeta.mapping("启用状态", Map.of(
            "true", "启用",
            "false", "停用"
        )));
        return map;
    }

    private Map<String, Map<String, FieldMeta>> buildDictionary() {
        Map<String, Map<String, FieldMeta>> map = new LinkedHashMap<>();
        map.put("PORTAL_MENU", portalMenuDictionary());
        map.put("MENU", portalMenuDictionary());
        map.put("USER", userDictionary());
        map.put("ROLE", roleDictionary());
        map.put("CUSTOM_ROLE", roleDictionary());
        map.put("ROLE_ASSIGNMENT", Map.of(
            "addedRoles", FieldMeta.label("新增角色"),
            "removedRoles", FieldMeta.label("移除角色"),
            "roles", FieldMeta.label("角色列表")
        ));
        map.put("DATASET", datasetDictionary());
        return map;
    }

    private Map<String, String> buildGenericValueDictionary() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("true", "是");
        map.put("false", "否");
        map.put("PENDING", "待处理");
        map.put("APPLIED", "已应用");
        map.put("REJECTED", "已拒绝");
        map.put("PROCESSING", "处理中");
        map.put("APPROVED", "已批准");
        map.put("SUCCESS", "成功");
        map.put("FAILED", "失败");
        map.put("GENERAL", "一般");
        map.put("IMPORTANT", "重要");
        map.put("CORE", "核心");
        map.put("INTERNAL", "内部");
        map.put("PUBLIC", "公开");
        map.put("NON_SECRET", "非密");
        return map;
    }

    private String translateValue(Object value, FieldMeta meta) {
        if (value == null) {
            return "-";
        }
        if (meta != null && meta.valueMapping != null) {
            String mapped = lookup(meta.valueMapping, value);
            if (mapped != null) {
                return mapped;
            }
        }
        if (value instanceof Boolean bool) {
            return genericValueDictionary.getOrDefault(Boolean.toString(bool), bool ? "是" : "否");
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> segments = new ArrayList<>();
            for (Object element : iterable) {
                segments.add(translateValue(element, meta));
            }
            return String.join("，", segments);
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<String> segments = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                segments.add(translateValue(java.lang.reflect.Array.get(value, i), meta));
            }
            return String.join("，", segments);
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException ignored) {
                return map.toString();
            }
        }
        String text = value.toString();
        if (StringUtils.isBlank(text)) {
            return "-";
        }
        String normalized = text.trim();
        String mapped = lookup(genericValueDictionary, normalized);
        return mapped != null ? mapped : normalized;
    }

    private String lookup(Map<String, String> dictionary, Object value) {
        if (dictionary == null || dictionary.isEmpty() || value == null) {
            return null;
        }
        String key = value.toString().trim();
        if (key.isEmpty()) {
            return dictionary.get("");
        }
        String direct = dictionary.get(key);
        if (direct != null) {
            return direct;
        }
        return dictionary.get(key.toUpperCase(Locale.ROOT));
    }

    private String normalizeResource(String resourceType) {
        if (resourceType == null) {
            return "";
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    private static final class FieldMeta {
        private final String label;
        private final Map<String, String> valueMapping;

        private FieldMeta(String label, Map<String, String> valueMapping) {
            this.label = label;
            this.valueMapping = valueMapping == null ? Map.of() : Map.copyOf(valueMapping);
        }

        private static FieldMeta label(String label) {
            return new FieldMeta(label, Map.of());
        }

        private static FieldMeta mapping(String label, Map<String, String> valueMapping) {
            return new FieldMeta(label, valueMapping);
        }

        private static FieldMeta fallback(String field) {
            if (StringUtils.isBlank(field)) {
                return new FieldMeta("字段", Map.of());
            }
            String normalized = field.replace('_', ' ').replace('.', ' ');
            return new FieldMeta(normalized, Map.of());
        }
    }
}
