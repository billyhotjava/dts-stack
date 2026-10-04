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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SchemaDriftDetector {

    private static final int CONTRACT_VERSION = 1;
    private static final Pattern TYPE_PATTERN = Pattern.compile("^([a-z ]+?)(?:\\(([^)]*)\\))?$");

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
            if (
                col == null ||
                !StringUtils.hasText(col.getName()) ||
                CatalogColumnSyncService.STATUS_REMOVED.equalsIgnoreCase(col.getStatus())
            ) {
                continue;
            }
            String key = col.getName().trim().toLowerCase(Locale.ROOT);
            map.putIfAbsent(key, new ColumnSnapshot(col.getName(), trimToNull(col.getDataType()), col.getNullable()));
        }
        return map;
    }

    public DriftSummary diff(Map<String, ColumnSnapshot> before, List<? extends ColumnSnapshot> after) {
        return diff(before, after, null);
    }

    /**
     * Classifies structural drift. A null referenced-field set means consumer coverage
     * is unknown and therefore cannot downgrade a removal to compatible.
     */
    public DriftSummary diff(
        Map<String, ColumnSnapshot> before,
        List<? extends ColumnSnapshot> after,
        Set<String> referencedFields
    ) {
        Map<String, ColumnSnapshot> beforeSafe = normalizedSnapshots(before);
        List<? extends ColumnSnapshot> afterSafe = after == null ? List.of() : after;

        Map<String, ColumnSnapshot> afterMap = new LinkedHashMap<>();
        for (ColumnSnapshot snap : afterSafe) {
            if (snap == null || !StringUtils.hasText(snap.name())) {
                continue;
            }
            afterMap.putIfAbsent(snap.name().trim().toLowerCase(Locale.ROOT), snap);
        }

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        List<String> addedKeys = afterMap.keySet().stream().filter(key -> !beforeSafe.containsKey(key)).toList();
        List<String> removedKeys = beforeSafe.keySet().stream().filter(key -> !afterMap.containsKey(key)).toList();
        List<String> retainedKeys = beforeSafe.keySet().stream().filter(afterMap::containsKey).toList();
        for (String key : addedKeys) {
            added.add(snapshotPayload(afterMap.get(key)));
        }
        for (String key : removedKeys) {
            removed.add(snapshotPayload(beforeSafe.get(key)));
        }
        for (String key : retainedKeys) {
            ColumnSnapshot b = beforeSafe.get(key);
            ColumnSnapshot a = afterMap.get(key);
            boolean typeChanged = !Objects.equals(normalizeType(b.dataType()), normalizeType(a.dataType()));
            boolean nullableChanged = !Objects.equals(b.nullable(), a.nullable());
            if (typeChanged || nullableChanged) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", a.name());
                entry.put("before", typePayload(b));
                entry.put("after", typePayload(a));
                changed.add(entry);
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return new DriftSummary(0, 0, 0, null);
        }

        Set<String> normalizedReferences = normalizeReferences(referencedFields);
        List<Map<String, Object>> classifiedChanges = new ArrayList<>();
        ImpactLevel overall = ImpactLevel.COMPATIBLE;
        boolean possibleRename = addedKeys.size() == 1 &&
            removedKeys.size() == 1 &&
            samePhysicalShape(beforeSafe.get(removedKeys.getFirst()), afterMap.get(addedKeys.getFirst()));
        if (possibleRename) {
            ColumnSnapshot removedColumn = beforeSafe.get(removedKeys.getFirst());
            ColumnSnapshot addedColumn = afterMap.get(addedKeys.getFirst());
            classifiedChanges.add(
                change(
                    removedColumn.name(),
                    "POSSIBLE_RENAME",
                    snapshotPayload(removedColumn),
                    snapshotPayload(addedColumn),
                    ImpactLevel.REVIEW_REQUIRED
                )
            );
            overall = ImpactLevel.REVIEW_REQUIRED;
        } else {
            for (String key : addedKeys) {
                ColumnSnapshot column = afterMap.get(key);
                ImpactLevel impact = Boolean.TRUE.equals(column.nullable())
                    ? ImpactLevel.COMPATIBLE
                    : ImpactLevel.REVIEW_REQUIRED;
                classifiedChanges.add(change(column.name(), "FIELD_ADDED", null, snapshotPayload(column), impact));
                overall = ImpactLevel.max(overall, impact);
            }
            for (String key : removedKeys) {
                ColumnSnapshot column = beforeSafe.get(key);
                ImpactLevel impact = referencedFields != null && !normalizedReferences.contains(key)
                    ? ImpactLevel.COMPATIBLE
                    : ImpactLevel.BREAKING;
                classifiedChanges.add(change(column.name(), "FIELD_REMOVED", snapshotPayload(column), null, impact));
                overall = ImpactLevel.max(overall, impact);
            }
        }
        for (String key : retainedKeys) {
            ColumnSnapshot beforeColumn = beforeSafe.get(key);
            ColumnSnapshot afterColumn = afterMap.get(key);
            boolean typeChanged = !Objects.equals(normalizeType(beforeColumn.dataType()), normalizeType(afterColumn.dataType()));
            boolean nullableChanged = !Objects.equals(beforeColumn.nullable(), afterColumn.nullable());
            if (!typeChanged && !nullableChanged) {
                continue;
            }
            ImpactLevel impact = ImpactLevel.COMPATIBLE;
            if (typeChanged) {
                impact = ImpactLevel.max(impact, classifyTypeChange(beforeColumn.dataType(), afterColumn.dataType()));
            }
            if (nullableChanged) {
                impact = ImpactLevel.max(impact, classifyNullability(beforeColumn.nullable(), afterColumn.nullable()));
            }
            String kind = typeChanged && nullableChanged
                ? "TYPE_AND_NULLABILITY_CHANGED"
                : (typeChanged ? "TYPE_CHANGED" : "NULLABILITY_CHANGED");
            classifiedChanges.add(
                change(afterColumn.name(), kind, typePayload(beforeColumn), typePayload(afterColumn), impact)
            );
            overall = ImpactLevel.max(overall, impact);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractVersion", CONTRACT_VERSION);
        payload.put("impactLevel", overall.name());
        payload.put("changes", classifiedChanges);
        if (!added.isEmpty()) payload.put("added", added);
        if (!removed.isEmpty()) payload.put("removed", removed);
        if (!changed.isEmpty()) payload.put("changed", changed);

        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            detailsJson =
                "{\"contractVersion\":1,\"impactLevel\":\"REVIEW_REQUIRED\",\"changes\":[],\"error\":\"failed-to-serialize\"}";
        }

        return new DriftSummary(added.size(), removed.size(), changed.size(), detailsJson);
    }

    private Map<String, ColumnSnapshot> normalizedSnapshots(Map<String, ColumnSnapshot> snapshots) {
        Map<String, ColumnSnapshot> normalized = new LinkedHashMap<>();
        if (snapshots == null) {
            return normalized;
        }
        snapshots.forEach((key, snapshot) -> {
            if (snapshot == null) {
                return;
            }
            String name = StringUtils.hasText(snapshot.name()) ? snapshot.name() : key;
            if (StringUtils.hasText(name)) {
                normalized.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), snapshot);
            }
        });
        return normalized;
    }

    private Set<String> normalizeReferences(Set<String> referencedFields) {
        Set<String> normalized = new LinkedHashSet<>();
        if (referencedFields == null) {
            return normalized;
        }
        for (String field : referencedFields) {
            if (StringUtils.hasText(field)) {
                normalized.add(field.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private Map<String, Object> snapshotPayload(ColumnSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", snapshot.name());
        payload.put("dataType", snapshot.dataType());
        payload.put("nullable", snapshot.nullable());
        return payload;
    }

    private Map<String, Object> typePayload(ColumnSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dataType", snapshot.dataType());
        payload.put("nullable", snapshot.nullable());
        return payload;
    }

    private Map<String, Object> change(
        String field,
        String kind,
        Object before,
        Object after,
        ImpactLevel impact
    ) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("field", field);
        change.put("kind", kind);
        change.put("before", before);
        change.put("after", after);
        change.put("impact", impact.name());
        return change;
    }

    private boolean samePhysicalShape(ColumnSnapshot before, ColumnSnapshot after) {
        return before != null &&
        after != null &&
        Objects.equals(normalizeType(before.dataType()), normalizeType(after.dataType())) &&
        Objects.equals(before.nullable(), after.nullable());
    }

    private ImpactLevel classifyTypeChange(String before, String after) {
        String normalizedBefore = normalizeType(before);
        String normalizedAfter = normalizeType(after);
        if (Objects.equals(normalizedBefore, normalizedAfter)) {
            return ImpactLevel.COMPATIBLE;
        }
        TypeShape beforeShape = typeShape(normalizedBefore);
        TypeShape afterShape = typeShape(normalizedAfter);
        if (beforeShape == null || afterShape == null) {
            return ImpactLevel.REVIEW_REQUIRED;
        }
        if (!beforeShape.family().equals(afterShape.family())) {
            return ImpactLevel.BREAKING;
        }
        return switch (beforeShape.family()) {
            case "STRING", "INTEGER", "FLOAT" -> compareSingleCapacity(beforeShape, afterShape);
            case "DECIMAL" -> compareDecimalCapacity(beforeShape, afterShape);
            default -> ImpactLevel.REVIEW_REQUIRED;
        };
    }

    private ImpactLevel compareSingleCapacity(TypeShape before, TypeShape after) {
        if (before.first() == null || after.first() == null) {
            return ImpactLevel.REVIEW_REQUIRED;
        }
        return after.first() >= before.first() ? ImpactLevel.COMPATIBLE : ImpactLevel.BREAKING;
    }

    private ImpactLevel compareDecimalCapacity(TypeShape before, TypeShape after) {
        if (before.first() == null || before.second() == null || after.first() == null || after.second() == null) {
            return ImpactLevel.REVIEW_REQUIRED;
        }
        int beforeIntegerDigits = before.first() - before.second();
        int afterIntegerDigits = after.first() - after.second();
        return afterIntegerDigits >= beforeIntegerDigits && after.second() >= before.second()
            ? ImpactLevel.COMPATIBLE
            : ImpactLevel.BREAKING;
    }

    private ImpactLevel classifyNullability(Boolean before, Boolean after) {
        if (Objects.equals(before, after)) {
            return ImpactLevel.COMPATIBLE;
        }
        if (Boolean.TRUE.equals(before) && Boolean.FALSE.equals(after)) {
            return ImpactLevel.BREAKING;
        }
        if (Boolean.FALSE.equals(before) && Boolean.TRUE.equals(after)) {
            return ImpactLevel.COMPATIBLE;
        }
        return ImpactLevel.REVIEW_REQUIRED;
    }

    private TypeShape typeShape(String normalized) {
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Matcher matcher = TYPE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String base = matcher.group(1).trim();
        List<Integer> parameters = numericParameters(matcher.group(2));
        return switch (base) {
            case "char", "character", "varchar", "character varying" ->
                new TypeShape("STRING", parameters.isEmpty() ? null : parameters.getFirst(), null);
            case "text", "string" -> new TypeShape("STRING", Integer.MAX_VALUE, null);
            case "decimal", "numeric", "number" ->
                new TypeShape(
                    "DECIMAL",
                    parameters.isEmpty() ? null : parameters.getFirst(),
                    parameters.size() < 2 ? null : parameters.get(1)
                );
            case "tinyint" -> new TypeShape("INTEGER", 1, null);
            case "smallint" -> new TypeShape("INTEGER", 2, null);
            case "int", "integer" -> new TypeShape("INTEGER", 3, null);
            case "bigint" -> new TypeShape("INTEGER", 4, null);
            case "real" -> new TypeShape("FLOAT", 1, null);
            case "float" -> new TypeShape("FLOAT", 2, null);
            case "double", "double precision" -> new TypeShape("FLOAT", 3, null);
            default -> new TypeShape("OTHER:" + base, null, null);
        };
    }

    private List<Integer> numericParameters(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<Integer> parameters = new ArrayList<>();
        for (String value : raw.split(",")) {
            try {
                parameters.add(Integer.parseInt(value.trim()));
            } catch (NumberFormatException exception) {
                return List.of();
            }
        }
        return parameters;
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

    private enum ImpactLevel {
        COMPATIBLE,
        REVIEW_REQUIRED,
        BREAKING;

        private static ImpactLevel max(ImpactLevel left, ImpactLevel right) {
            return left.ordinal() >= right.ordinal() ? left : right;
        }
    }

    private record TypeShape(String family, Integer first, Integer second) {}
}
