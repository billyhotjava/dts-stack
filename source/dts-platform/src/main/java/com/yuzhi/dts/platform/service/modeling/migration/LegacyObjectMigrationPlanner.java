package com.yuzhi.dts.platform.service.modeling.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure, deterministic classifier for the Sprint-67 legacy-object retirement inventory. */
public final class LegacyObjectMigrationPlanner {

    public enum Classification {
        AUTO_DIMENSION,
        AUTO_FACT_MERGE,
        MANUAL_SPLIT,
        ARCHIVE_ONLY,
        NEEDS_CLASSIFICATION,
    }

    public Report plan(
        Map<String, Long> sourceCounts,
        Map<String, Long> orphanCounts,
        List<LegacyObjectSnapshot> sourceSnapshots
    ) {
        Map<String, Long> counts = sortedCopy(sourceCounts);
        Map<String, Long> orphans = sortedCopy(orphanCounts);
        List<LegacyObjectSnapshot> snapshots = sourceSnapshots == null
            ? List.of()
            : sourceSnapshots.stream().sorted(snapshotOrder()).toList();
        List<LegacyObjectDecision> decisions = snapshots.stream().map(this::classify).toList();
        String checksum = sha256(canonical(counts, orphans, snapshots));
        Map<String, Long> classificationCounts = new LinkedHashMap<>();
        for (Classification classification : Classification.values()) {
            long count = decisions.stream().filter(decision -> decision.classification() == classification).count();
            classificationCounts.put(classification.name(), count);
        }
        return new Report(
            "legacy-objects-" + checksum.substring(0, 16),
            checksum,
            counts,
            orphans,
            Map.copyOf(classificationCounts),
            decisions
        );
    }

    private LegacyObjectDecision classify(LegacyObjectSnapshot source) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        boolean domainMatched = source.catalogDomainId() != null;
        if (!domainMatched) blockers.add("DOMAIN_CLASSIFICATION_REQUIRED");

        List<SourceRefProjection> sourceRefs = safe(source.sources())
            .stream()
            .sorted(Comparator.comparingInt(LegacySource::sortOrder).thenComparing(LegacySource::id, Comparator.nullsLast(String::compareTo)))
            .map(mapping -> projectSource(mapping, blockers))
            .toList();

        if (hasGrainConflict(source.businessKey(), source.models())) blockers.add("GRAIN_CONFLICT");

        Classification classification;
        if (!domainMatched) {
            classification = Classification.NEEDS_CLASSIFICATION;
        } else if (isArchiveOnly(source)) {
            classification = Classification.ARCHIVE_ONLY;
        } else if (blockers.contains("GRAIN_CONFLICT") || blockers.contains("SOURCE_JOIN_TYPE_REQUIRED")) {
            classification = Classification.MANUAL_SPLIT;
        } else if (isDimension(source)) {
            classification = Classification.AUTO_DIMENSION;
        } else if (isFactMerge(source)) {
            classification = Classification.AUTO_FACT_MERGE;
        } else {
            classification = Classification.MANUAL_SPLIT;
            blockers.add("MANUAL_MODEL_SPLIT_REQUIRED");
        }

        if (
            (classification == Classification.AUTO_DIMENSION || classification == Classification.AUTO_FACT_MERGE) &&
            source.targetPlanId() == null
        ) {
            blockers.add("TARGET_PLAN_REQUIRED");
        }
        if (
            (classification == Classification.AUTO_DIMENSION || classification == Classification.AUTO_FACT_MERGE) &&
            !sourceRefs.isEmpty()
        ) {
            blockers.add("SOURCE_BINDING_APPROVAL_REQUIRED");
        }

        boolean ready = classification == Classification.ARCHIVE_ONLY ||
            ((classification == Classification.AUTO_DIMENSION || classification == Classification.AUTO_FACT_MERGE) && blockers.isEmpty());
        Map<String, Integer> relatedCounts = new LinkedHashMap<>();
        relatedCounts.put("models", safe(source.models()).size());
        relatedCounts.put("tableMappings", sourceRefs.size());
        relatedCounts.put("dimensions", source.dimensionCount());
        relatedCounts.put("metrics", source.metricCount());
        relatedCounts.put("artifacts", source.artifactCount());
        relatedCounts.put("runs", source.runCount());
        relatedCounts.put("reviews", source.reviewCount());

        return new LegacyObjectDecision(
            source.legacySource(),
            source.legacyId(),
            source.code(),
            source.name(),
            source.legacyDomainId(),
            source.catalogDomainId(),
            source.targetPlanId(),
            classification,
            targetType(source, classification),
            null,
            ready,
            List.copyOf(blockers),
            List.copyOf(source.businessKey() == null ? List.of() : source.businessKey()),
            sourceRefs,
            Map.copyOf(relatedCounts)
        );
    }

    private static SourceRefProjection projectSource(LegacySource source, Set<String> blockers) {
        String role = upper(source.legacyRawRole());
        String joinType = switch (role) {
            case "LEFT", "RIGHT", "INNER", "FULL" -> role;
            default -> null;
        };
        if (hasText(source.joinExpression()) && joinType == null) blockers.add("SOURCE_JOIN_TYPE_REQUIRED");
        String normalizedRole = switch (role) {
            case "PRIMARY", "MAIN", "FACT" -> "PRIMARY";
            case "DIMENSION" -> "JOINED";
            default -> joinType == null ? "UNKNOWN" : "JOINED";
        };
        return new SourceRefProjection(
            source.id(),
            source.tableName(),
            normalizedRole,
            null,
            joinType,
            hasText(source.joinExpression()) ? sha256(source.joinExpression().trim()) : null,
            source.sortOrder(),
            source.legacyRawRole()
        );
    }

    private static boolean isArchiveOnly(LegacyObjectSnapshot source) {
        return safe(source.models()).isEmpty() && source.dimensionCount() == 0 && source.metricCount() == 0;
    }

    private static boolean isDimension(LegacyObjectSnapshot source) {
        return "DIMENSION".equals(upper(source.objectKind())) ||
            (safe(source.models()).isEmpty() && source.dimensionCount() > 0 && source.metricCount() == 0);
    }

    private static boolean isFactMerge(LegacyObjectSnapshot source) {
        List<LegacyModel> models = safe(source.models());
        return !models.isEmpty() && models.stream().allMatch(model -> "DWD".equals(upper(model.type())));
    }

    private static String targetType(LegacyObjectSnapshot source, Classification classification) {
        if (classification == Classification.AUTO_DIMENSION) return "DIMENSION";
        if (classification == Classification.AUTO_FACT_MERGE) return "FACT";
        Set<String> modelTypes = new LinkedHashSet<>();
        safe(source.models()).forEach(model -> modelTypes.add(upper(model.type())));
        if (modelTypes.equals(Set.of("DWS"))) return "SUMMARY";
        if (modelTypes.equals(Set.of("ADS"))) return "APPLICATION";
        return null;
    }

    private static boolean hasGrainConflict(List<String> businessKey, List<LegacyModel> models) {
        Set<String> objectGrain = normalizedSet(businessKey);
        if (objectGrain.isEmpty()) return false;
        for (LegacyModel model : safe(models)) {
            Set<String> modelGrain = normalizedSet(model.grainKeys());
            if (!modelGrain.isEmpty() && !modelGrain.equals(objectGrain)) return true;
        }
        return false;
    }

    private static String canonical(
        Map<String, Long> counts,
        Map<String, Long> orphans,
        List<LegacyObjectSnapshot> snapshots
    ) {
        StringBuilder value = new StringBuilder();
        counts.forEach((key, count) -> token(value, "count", key, count));
        orphans.forEach((key, count) -> token(value, "orphan", key, count));
        for (LegacyObjectSnapshot snapshot : snapshots) {
            token(
                value,
                snapshot.legacySource(),
                snapshot.legacyId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.objectKind(),
                snapshot.legacyDomainId(),
                snapshot.catalogDomainId(),
                snapshot.targetPlanId(),
                snapshot.processId(),
                snapshot.status(),
                normalizedSet(snapshot.businessKey()),
                snapshot.dimensionCount(),
                snapshot.metricCount(),
                snapshot.artifactCount(),
                snapshot.runCount(),
                snapshot.reviewCount()
            );
            safe(snapshot.models())
                .stream()
                .sorted(Comparator.comparing(LegacyModel::id, Comparator.nullsLast(String::compareTo)))
                .forEach(model -> token(value, "model", model.id(), model.type(), normalizedSet(model.grainKeys())));
            safe(snapshot.sources())
                .stream()
                .sorted(Comparator.comparingInt(LegacySource::sortOrder).thenComparing(LegacySource::id, Comparator.nullsLast(String::compareTo)))
                .forEach(source -> token(
                    value,
                    "source",
                    source.id(),
                    source.tableName(),
                    source.legacyRawRole(),
                    hasText(source.joinExpression()) ? sha256(source.joinExpression().trim()) : null,
                    source.sortOrder()
                ));
        }
        return value.toString();
    }

    private static void token(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = String.valueOf(value);
            target.append(text.length()).append(':').append(text).append('|');
        }
        target.append('\n');
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Comparator<LegacyObjectSnapshot> snapshotOrder() {
        return Comparator
            .comparing(LegacyObjectSnapshot::legacySource, Comparator.nullsFirst(String::compareTo))
            .thenComparing(LegacyObjectSnapshot::legacyId, Comparator.nullsFirst(UUID::compareTo));
    }

    private static Map<String, Long> sortedCopy(Map<String, Long> source) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (source != null) source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    private static Set<String> normalizedSet(List<String> values) {
        if (values == null) return Set.of();
        return values
            .stream()
            .filter(LegacyObjectMigrationPlanner::hasText)
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record LegacyModel(String id, String type, List<String> grainKeys) {
        public LegacyModel {
            grainKeys = grainKeys == null ? List.of() : List.copyOf(grainKeys);
        }
    }

    public record LegacySource(String id, String tableName, String legacyRawRole, String joinExpression, int sortOrder) {}

    public record LegacyObjectSnapshot(
        String legacySource,
        UUID legacyId,
        String code,
        String name,
        String objectKind,
        UUID legacyDomainId,
        UUID catalogDomainId,
        UUID targetPlanId,
        String processId,
        String status,
        List<String> businessKey,
        List<LegacyModel> models,
        List<LegacySource> sources,
        int dimensionCount,
        int metricCount,
        int artifactCount,
        int runCount,
        int reviewCount
    ) {
        public LegacyObjectSnapshot {
            businessKey = businessKey == null ? List.of() : List.copyOf(businessKey);
            models = models == null ? List.of() : List.copyOf(models);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record SourceRefProjection(
        String legacyMappingId,
        String tableName,
        String role,
        String alias,
        String joinType,
        String joinExpressionChecksum,
        int sortOrder,
        String legacyRawRole
    ) {}

    public record LegacyObjectDecision(
        String legacySource,
        UUID legacyId,
        String legacyCode,
        String legacyName,
        UUID legacyDomainId,
        UUID catalogDomainId,
        UUID targetPlanId,
        Classification classification,
        String targetType,
        String targetRef,
        boolean ready,
        List<String> blockers,
        List<String> grainKeys,
        List<SourceRefProjection> sourceRefs,
        Map<String, Integer> relatedCounts
    ) {}

    public record Report(
        String batchId,
        String checksum,
        Map<String, Long> sourceCounts,
        Map<String, Long> orphanCounts,
        Map<String, Long> classificationCounts,
        List<LegacyObjectDecision> decisions
    ) {}
}
