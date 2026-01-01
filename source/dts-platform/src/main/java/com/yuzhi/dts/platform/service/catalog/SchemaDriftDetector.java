package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SchemaDriftDetector {

    public record ColumnSnapshot(String name, String dataType, Boolean nullable) {}

    public record DriftSummary(int added, int removed, int changed, String detailsJson) {}

    private final ObjectMapper objectMapper;

    public SchemaDriftDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, ColumnSnapshot> snapshotExisting(List<CatalogColumnSchema> existingColumns) {
        Map<String, ColumnSnapshot> map = new LinkedHashMap<>();
        if (existingColumns == null || existingColumns.isEmpty()) {
            return map;
        }
        for (CatalogColumnSchema col : existingColumns) {
            if (col == null || !StringUtils.hasText(col.getName())) {
                continue;
            }
            String key = col.getName().trim().toLowerCase(Locale.ROOT);
            map.putIfAbsent(key, new ColumnSnapshot(col.getName(), trimToNull(col.getDataType()), col.getNullable()));
        }
        return map;
    }

    public DriftSummary diff(Map<String, ColumnSnapshot> before, List<? extends ColumnSnapshot> after) {
        Map<String, ColumnSnapshot> beforeSafe = before == null ? Map.of() : before;
        List<? extends ColumnSnapshot> afterSafe = after == null ? List.of() : after;

        Map<String, ColumnSnapshot> afterMap = new LinkedHashMap<>();
        for (ColumnSnapshot snap : afterSafe) {
            if (snap == null || !StringUtils.hasText(snap.name())) {
                continue;
            }
            afterMap.putIfAbsent(snap.name().trim().toLowerCase(Locale.ROOT), snap);
        }

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(beforeSafe.keySet());
        allKeys.addAll(afterMap.keySet());

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (String key : allKeys) {
            ColumnSnapshot b = beforeSafe.get(key);
            ColumnSnapshot a = afterMap.get(key);
            if (b == null && a != null) {
                added.add(Map.of("name", a.name(), "dataType", a.dataType(), "nullable", a.nullable()));
                continue;
            }
            if (b != null && a == null) {
                removed.add(Map.of("name", b.name(), "dataType", b.dataType(), "nullable", b.nullable()));
                continue;
            }
            if (b == null || a == null) {
                continue;
            }
            boolean typeChanged = !Objects.equals(normalizeType(b.dataType()), normalizeType(a.dataType()));
            boolean nullableChanged = !Objects.equals(b.nullable(), a.nullable());
            if (typeChanged || nullableChanged) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", a.name());
                entry.put("before", Map.of("dataType", b.dataType(), "nullable", b.nullable()));
                entry.put("after", Map.of("dataType", a.dataType(), "nullable", a.nullable()));
                changed.add(entry);
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return new DriftSummary(0, 0, 0, null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (!added.isEmpty()) payload.put("added", added);
        if (!removed.isEmpty()) payload.put("removed", removed);
        if (!changed.isEmpty()) payload.put("changed", changed);

        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            detailsJson = "{\"error\":\"failed-to-serialize\"}";
        }

        return new DriftSummary(added.size(), removed.size(), changed.size(), detailsJson);
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ");
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

