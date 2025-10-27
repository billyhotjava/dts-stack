package com.yuzhi.dts.common.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 标准化的变更快照，统一包含变更前数据、变更后数据以及字段级差异列表。
 * 可以直接用于审批详情展示或审计详情记录。
 */
public final class ChangeSnapshot {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> CHANGE_LIST_TYPE = new TypeReference<>() {};

    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final List<FieldChange> changes;

    private ChangeSnapshot(Map<String, Object> before, Map<String, Object> after, List<FieldChange> changes) {
        this.before = before == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        this.after = after == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(after));
        this.changes = changes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(changes));
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public List<FieldChange> getChanges() {
        return changes;
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * 以标准 Map 结构输出，便于序列化或直接存入审计详情。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("before", before);
        map.put("after", after);
        List<Map<String, Object>> changeMaps = new ArrayList<>(changes.size());
        for (FieldChange change : changes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", change.field());
            entry.put("before", change.before());
            entry.put("after", change.after());
            changeMaps.add(entry);
        }
        map.put("changes", changeMaps);
        return map;
    }

    /**
     * 根据变更前后的取值计算字段差异。
     */
    public static ChangeSnapshot of(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> normalizedBefore = normalize(before);
        Map<String, Object> normalizedAfter = normalize(after);
        List<FieldChange> changes = computeChanges(normalizedBefore, normalizedAfter);
        return new ChangeSnapshot(normalizedBefore, normalizedAfter, changes);
    }

    /**
     * 从已经序列化的 Map 结构恢复 ChangeSnapshot。
     */
    public static ChangeSnapshot fromMap(Map<String, Object> diff) {
        if (diff == null || diff.isEmpty()) {
            return new ChangeSnapshot(Map.of(), Map.of(), List.of());
        }
        Map<String, Object> before = normalize(asMap(diff.get("before")));
        Map<String, Object> after = normalize(asMap(diff.get("after")));
        List<FieldChange> changes = parseChangeList(diff.get("changes"));
        if (changes.isEmpty()) {
            changes = computeChanges(before, after);
        }
        return new ChangeSnapshot(before, after, changes);
    }

    /**
     * 从 JSON 文本恢复 ChangeSnapshot。
     */
    public static ChangeSnapshot fromJson(String diffJson, ObjectMapper objectMapper) {
        if (diffJson == null || diffJson.isBlank() || objectMapper == null) {
            return new ChangeSnapshot(Map.of(), Map.of(), List.of());
        }
        try {
            Map<String, Object> diff = objectMapper.readValue(diffJson, MAP_TYPE);
            return fromMap(diff);
        } catch (Exception ignored) {
            return new ChangeSnapshot(Map.of(), Map.of(), List.of());
        }
    }

    /**
     * 从 before/after 的 JSON 字符串组合恢复 ChangeSnapshot。
     * 当 diffJson 缺失时可作为兜底。
     */
    public static ChangeSnapshot fromJsonPair(String beforeJson, String afterJson, ObjectMapper objectMapper) {
        Map<String, Object> before = readMap(beforeJson, objectMapper);
        Map<String, Object> after = readMap(afterJson, objectMapper);
        return of(before, after);
    }

    private static Map<String, Object> readMap(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return Map.of();
        }
        try {
            return normalize(objectMapper.readValue(json, MAP_TYPE));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Object> normalize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey();
            Object value = normalizeValue(entry.getValue());
            result.put(key, value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            return result;
        }
        return Map.of();
    }

    private static List<FieldChange> parseChangeList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<FieldChange> changes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object field = map.get("field");
                    if (field == null) {
                        continue;
                    }
                    changes.add(new FieldChange(String.valueOf(field), map.get("before"), map.get("after")));
                }
            }
            return changes;
        }
        return List.of();
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry != null && entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
                }
            }
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return value;
    }

    private static List<FieldChange> computeChanges(Map<String, Object> before, Map<String, Object> after) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());
        List<FieldChange> changes = new ArrayList<>();
        for (String field : fields) {
            Object left = before.get(field);
            Object right = after.get(field);
            if (!Objects.equals(left, right)) {
                changes.add(new FieldChange(field, left, right));
            }
        }
        return changes;
    }

    /**
     * 字段层面的差异。
     */
    public record FieldChange(String field, Object before, Object after) {
        public FieldChange {
            field = field == null ? "" : field.trim();
        }

        public String fieldDisplay() {
            if (field == null || field.isBlank()) {
                return "";
            }
            return field.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }
}
