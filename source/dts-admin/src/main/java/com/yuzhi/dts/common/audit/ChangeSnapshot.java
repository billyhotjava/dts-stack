package com.yuzhi.dts.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Lightweight ChangeSnapshot implementation used by the admin service during audit logging.
 * Captures before/after payloads and materialises a list of field-level differences.
 */
public final class ChangeSnapshot {

    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final List<FieldChange> changes;

    private ChangeSnapshot(Map<String, Object> before, Map<String, Object> after, List<FieldChange> changes) {
        this.before = before == null ? Map.of() : Collections.unmodifiableMap(before);
        this.after = after == null ? Map.of() : Collections.unmodifiableMap(after);
        this.changes = Collections.unmodifiableList(changes);
    }

    public static ChangeSnapshot of(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> normalizedBefore = normalize(before);
        Map<String, Object> normalizedAfter = normalize(after);
        List<FieldChange> diffs = computeDiff(normalizedBefore, normalizedAfter);
        return new ChangeSnapshot(normalizedBefore, normalizedAfter, diffs);
    }

    public static ChangeSnapshot fromJson(String json, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(json) || objectMapper == null) {
            return null;
        }
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            return fromMap(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ChangeSnapshot fromMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> before = normalize(raw.get("before"));
        Map<String, Object> after = normalize(raw.get("after"));
        if (before.isEmpty() && after.isEmpty()) {
            return null;
        }
        return of(before, after);
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

    public Map<String, Object> toMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("before", before);
        response.put("after", after);
        response.put("changes", changes);
        return response;
    }

    private static Map<String, Object> normalize(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private static List<FieldChange> computeDiff(Map<String, Object> before, Map<String, Object> after) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());
        List<FieldChange> diff = new ArrayList<>();
        for (String field : fields) {
            Object beforeValue = before.get(field);
            Object afterValue = after.get(field);
            if (!Objects.equals(beforeValue, afterValue)) {
                diff.add(new FieldChange(field, beforeValue, afterValue));
            }
        }
        return diff;
    }

    public record FieldChange(String field, Object before, Object after) {}

    @Override
    public String toString() {
        return "ChangeSnapshot{before=" + before + ", after=" + after + ", changes=" + changes + '}';
    }
}
