package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure migration policy for retiring legacy business objects without creating a replacement entity. */
public final class BusinessObjectRetirementPolicy {

    private BusinessObjectRetirementPolicy() {}

    public enum Identification {
        DIMENSION_LIKE,
        FACT_LIKE,
        MIXED,
        ARCHIVE_CANDIDATE,
    }

    public enum Readiness {
        READY,
        NEEDS_CLASSIFICATION,
        NEEDS_TARGET,
        NEEDS_SPLIT,
        BLOCKED_BY_CONSUMERS,
    }

    public enum Disposition {
        AUTO_DIMENSION,
        AUTO_FACT_MERGE,
        MANUAL_SPLIT,
        ARCHIVE_ONLY,
    }

    public enum FieldSignal {
        KEY,
        ATTRIBUTE,
        GRAIN,
        SOURCE,
        STANDARD,
        METRIC,
    }

    public enum FieldOwner {
        MODEL_DIMENSION_KEY,
        MODEL_FIELD,
        MODEL_GRAIN,
        MODEL_SOURCE_REF,
        STANDARD_REFERENCE,
        METRIC_DEFINITION,
        MANUAL_REVIEW,
    }

    public enum SourceRole {
        PRIMARY,
        JOINED,
    }

    public enum JoinType {
        INNER,
        LEFT,
        RIGHT,
        FULL,
    }

    public record LegacyField(String name, Set<FieldSignal> signals) {
        public LegacyField {
            name = required(name, "field.name");
            signals = signals == null ? Set.of() : Set.copyOf(signals);
        }
    }

    public record LegacySourceRef(
        String kind,
        String ref,
        String layer,
        String tableRole,
        String alias,
        String joinExpression,
        int sortOrder
    ) {
        public LegacySourceRef {
            kind = required(kind, "source.kind");
            ref = required(ref, "source.ref");
        }
    }

    public record LegacyBusinessObject(
        String sourceSystem,
        String sourceId,
        String legacyKind,
        String domainId,
        String targetDomainId,
        List<String> stableKeys,
        boolean factSignal,
        List<String> grainKeys,
        List<String> timeFields,
        List<LegacyField> fields,
        List<LegacySourceRef> sourceRefs,
        List<String> candidateTargetModelIds,
        List<String> consumerRefs,
        boolean archiveRequested,
        String planId,
        String proposedModelSpecId,
        int targetRevision
    ) {
        public LegacyBusinessObject {
            sourceSystem = required(sourceSystem, "sourceSystem");
            sourceId = required(sourceId, "sourceId");
            stableKeys = immutable(stableKeys);
            grainKeys = immutable(grainKeys);
            timeFields = immutable(timeFields);
            fields = immutable(fields);
            sourceRefs = immutable(sourceRefs);
            candidateTargetModelIds = immutable(candidateTargetModelIds);
            consumerRefs = immutable(consumerRefs);
        }
    }

    public record LegacyRef(String sourceSystem, String sourceId) {
        public LegacyRef {
            sourceSystem = required(sourceSystem, "sourceSystem");
            sourceId = required(sourceId, "sourceId");
        }

        public String externalForm() {
            return sourceSystem + ":" + sourceId;
        }
    }

    /** Canonical target-write identity. Deliberately has no objectId component. */
    public record TargetWriteMetadata(String planId, String domainId, String modelSpecId, int revision, LegacyRef legacyRef) {
        public TargetWriteMetadata {
            planId = required(planId, "planId");
            domainId = required(domainId, "domainId");
            modelSpecId = required(modelSpecId, "modelSpecId");
            legacyRef = Objects.requireNonNull(legacyRef, "legacyRef");
            if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        }
    }

    public record FieldDisposition(String sourceField, FieldOwner owner) {}

    public record TargetSourceRef(
        String kind,
        String ref,
        String layer,
        SourceRole role,
        String alias,
        JoinType joinType,
        String joinExpression,
        int sortOrder,
        String legacyRawRole
    ) {}

    public record Decision(
        Identification identification,
        Readiness readiness,
        Optional<Disposition> recommendedDisposition,
        Optional<Disposition> executableAction,
        Optional<String> targetModelSpecId,
        LegacyRef legacyRef,
        List<FieldDisposition> fieldDispositions,
        List<TargetSourceRef> mappedSourceRefs,
        List<String> conflicts,
        Optional<TargetWriteMetadata> targetWriteMetadata
    ) {
        public Decision {
            recommendedDisposition = Objects.requireNonNull(recommendedDisposition, "recommendedDisposition");
            executableAction = Objects.requireNonNull(executableAction, "executableAction");
            targetModelSpecId = Objects.requireNonNull(targetModelSpecId, "targetModelSpecId");
            legacyRef = Objects.requireNonNull(legacyRef, "legacyRef");
            fieldDispositions = List.copyOf(fieldDispositions);
            mappedSourceRefs = List.copyOf(mappedSourceRefs);
            conflicts = List.copyOf(conflicts);
            targetWriteMetadata = Objects.requireNonNull(targetWriteMetadata, "targetWriteMetadata");
            if (readiness != Readiness.READY && executableAction.isPresent()) {
                throw new IllegalArgumentException("non-ready decisions cannot expose executable actions");
            }
        }
    }

    public static LegacyRef legacyRef(String sourceSystem, String sourceId) {
        return new LegacyRef(sourceSystem, sourceId);
    }

    public static Decision decide(LegacyBusinessObject legacy) {
        Objects.requireNonNull(legacy, "legacy");
        LegacyRef legacyRef = legacyRef(legacy.sourceSystem(), legacy.sourceId());
        List<String> conflicts = new ArrayList<>();
        List<FieldDisposition> fieldDispositions = mapFields(legacy.fields(), conflicts);
        List<TargetSourceRef> sourceRefs = mapSources(legacy.sourceRefs(), conflicts);

        Identification identification = identify(legacy);
        Optional<Disposition> recommendation = recommendation(identification);
        Optional<String> targetModelSpecId = targetModelSpecId(legacy, identification);
        Readiness readiness = readiness(legacy, identification, targetModelSpecId, conflicts);
        Optional<Disposition> executableAction = readiness == Readiness.READY ? recommendation : Optional.empty();
        Optional<TargetWriteMetadata> metadata = targetWriteMetadata(
            legacy,
            legacyRef,
            targetModelSpecId,
            executableAction
        );

        return new Decision(
            identification,
            readiness,
            recommendation,
            executableAction,
            targetModelSpecId,
            legacyRef,
            fieldDispositions,
            sourceRefs,
            conflicts,
            metadata
        );
    }

    private static Identification identify(LegacyBusinessObject legacy) {
        String kind = normalized(legacy.legacyKind());
        boolean dimensionSignal = switch (kind) {
            case "DIMENSION", "MASTER", "REFERENCE" -> true;
            case "ENTITY" -> !legacy.stableKeys().isEmpty();
            default -> false;
        };
        boolean factSignal =
            legacy.factSignal() ||
            !legacy.grainKeys().isEmpty() ||
            !legacy.timeFields().isEmpty() ||
            Set.of("FACT", "EVENT", "SNAPSHOT").contains(kind);

        if (dimensionSignal && factSignal) return Identification.MIXED;
        if (dimensionSignal) return Identification.DIMENSION_LIKE;
        if (factSignal) return Identification.FACT_LIKE;
        return Identification.ARCHIVE_CANDIDATE;
    }

    private static Optional<Disposition> recommendation(Identification identification) {
        return switch (identification) {
            case DIMENSION_LIKE -> Optional.of(Disposition.AUTO_DIMENSION);
            case FACT_LIKE -> Optional.of(Disposition.AUTO_FACT_MERGE);
            case MIXED -> Optional.of(Disposition.MANUAL_SPLIT);
            case ARCHIVE_CANDIDATE -> Optional.of(Disposition.ARCHIVE_ONLY);
        };
    }

    private static Optional<String> targetModelSpecId(LegacyBusinessObject legacy, Identification identification) {
        if (identification == Identification.DIMENSION_LIKE) return nonBlank(legacy.proposedModelSpecId());
        if (identification != Identification.FACT_LIKE) return Optional.empty();
        List<String> targets = legacy.candidateTargetModelIds().stream().filter(BusinessObjectRetirementPolicy::hasText).distinct().toList();
        return targets.size() == 1 ? Optional.of(targets.get(0)) : Optional.empty();
    }

    private static Readiness readiness(
        LegacyBusinessObject legacy,
        Identification identification,
        Optional<String> targetModelSpecId,
        List<String> conflicts
    ) {
        if (identification == Identification.MIXED) return Readiness.NEEDS_SPLIT;
        if (identification == Identification.ARCHIVE_CANDIDATE) {
            if (!legacy.archiveRequested()) return Readiness.NEEDS_CLASSIFICATION;
            return legacy.consumerRefs().isEmpty() ? Readiness.READY : Readiness.BLOCKED_BY_CONSUMERS;
        }
        if (!hasText(legacy.domainId()) || !Objects.equals(legacy.domainId(), legacy.targetDomainId()) || !conflicts.isEmpty()) {
            return Readiness.NEEDS_CLASSIFICATION;
        }
        if (targetModelSpecId.isEmpty()) return Readiness.NEEDS_TARGET;
        return Readiness.READY;
    }

    private static Optional<TargetWriteMetadata> targetWriteMetadata(
        LegacyBusinessObject legacy,
        LegacyRef legacyRef,
        Optional<String> targetModelSpecId,
        Optional<Disposition> executableAction
    ) {
        if (targetModelSpecId.isEmpty() || executableAction.isEmpty()) return Optional.empty();
        return Optional.of(
            new TargetWriteMetadata(
                legacy.planId(),
                legacy.targetDomainId(),
                targetModelSpecId.get(),
                legacy.targetRevision(),
                legacyRef
            )
        );
    }

    private static List<FieldDisposition> mapFields(List<LegacyField> fields, List<String> conflicts) {
        return fields
            .stream()
            .map(field -> {
                FieldOwner owner = field.signals().size() == 1
                    ? owner(field.signals().iterator().next())
                    : FieldOwner.MANUAL_REVIEW;
                if (owner == FieldOwner.MANUAL_REVIEW) conflicts.add("FIELD_OWNER_AMBIGUOUS:" + field.name());
                return new FieldDisposition(field.name(), owner);
            })
            .toList();
    }

    private static FieldOwner owner(FieldSignal signal) {
        return switch (signal) {
            case KEY -> FieldOwner.MODEL_DIMENSION_KEY;
            case ATTRIBUTE -> FieldOwner.MODEL_FIELD;
            case GRAIN -> FieldOwner.MODEL_GRAIN;
            case SOURCE -> FieldOwner.MODEL_SOURCE_REF;
            case STANDARD -> FieldOwner.STANDARD_REFERENCE;
            case METRIC -> FieldOwner.METRIC_DEFINITION;
        };
    }

    private static List<TargetSourceRef> mapSources(List<LegacySourceRef> sources, List<String> conflicts) {
        return sources.stream().map(source -> mapSource(source, conflicts)).toList();
    }

    private static TargetSourceRef mapSource(LegacySourceRef source, List<String> conflicts) {
        String rawRole = source.tableRole();
        String normalizedRole = normalized(rawRole);
        SourceRole role = null;
        JoinType joinType = null;

        if (normalizedRole.equals("PRIMARY")) {
            role = SourceRole.PRIMARY;
        } else if (normalizedRole.equals("JOINED")) {
            role = SourceRole.JOINED;
            conflicts.add("SOURCE_JOIN_TYPE_AMBIGUOUS:" + source.ref() + ":" + rawRole);
        } else {
            joinType = joinType(normalizedRole);
            if (joinType == null) {
                conflicts.add("SOURCE_ROLE_AMBIGUOUS:" + source.ref() + ":" + rawRole);
            } else {
                role = SourceRole.JOINED;
            }
        }

        String alias = hasText(source.alias()) ? source.alias() : null;
        if (alias == null) conflicts.add("SOURCE_ALIAS_MISSING:" + source.ref());

        return new TargetSourceRef(
            source.kind(),
            source.ref(),
            source.layer(),
            role,
            alias,
            joinType,
            source.joinExpression(),
            source.sortOrder(),
            rawRole
        );
    }

    private static JoinType joinType(String normalizedRole) {
        return switch (normalizedRole) {
            case "INNER", "INNER_JOIN", "INNER JOIN" -> JoinType.INNER;
            case "LEFT", "LEFT_JOIN", "LEFT JOIN" -> JoinType.LEFT;
            case "RIGHT", "RIGHT_JOIN", "RIGHT JOIN" -> JoinType.RIGHT;
            case "FULL", "FULL_JOIN", "FULL JOIN" -> JoinType.FULL;
            default -> null;
        };
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Optional<String> nonBlank(String value) {
        return hasText(value) ? Optional.of(value) : Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
