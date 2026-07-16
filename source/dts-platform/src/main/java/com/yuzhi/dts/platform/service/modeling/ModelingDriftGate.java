package com.yuzhi.dts.platform.service.modeling;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compares dbt snapshots with ModelSpec rules and evaluates the controlled-release gate. */
public final class ModelingDriftGate {

    private ModelingDriftGate() {}

    public enum DriftKind {
        FIELD_DRIFT,
        GRAIN_DRIFT,
        SOURCE_DRIFT,
        STANDARD_DRIFT,
        ARTIFACT_CHECKSUM_DRIFT,
    }

    public record Snapshot(
        List<String> fields,
        String grain,
        List<String> sources,
        List<String> standardElements,
        String checksum,
        boolean businessObjectRegistered,
        boolean parsePassed,
        boolean testsPassed
    ) {}

    public record DriftResult(List<DriftKind> kinds) {
        public boolean clean() {
            return kinds == null || kinds.isEmpty();
        }
    }

    public record ReleaseGateResult(boolean publishable, String status, List<String> blockers) {}

    public static DriftResult compare(Snapshot expected, Snapshot actual) {
        if (expected == null || actual == null) return new DriftResult(List.of(DriftKind.FIELD_DRIFT));
        Set<DriftKind> kinds = new LinkedHashSet<>();
        if (!same(expected.fields(), actual.fields())) kinds.add(DriftKind.FIELD_DRIFT);
        if (!same(expected.grain(), actual.grain())) kinds.add(DriftKind.GRAIN_DRIFT);
        if (!same(expected.sources(), actual.sources())) kinds.add(DriftKind.SOURCE_DRIFT);
        if (!same(expected.standardElements(), actual.standardElements())) kinds.add(DriftKind.STANDARD_DRIFT);
        if (!same(expected.checksum(), actual.checksum())) kinds.add(DriftKind.ARTIFACT_CHECKSUM_DRIFT);
        return new DriftResult(List.copyOf(kinds));
    }

    public static ReleaseGateResult evaluate(Snapshot snapshot, List<DriftKind> driftKinds) {
        Set<String> blockers = new LinkedHashSet<>();
        if (snapshot == null || !snapshot.businessObjectRegistered()) blockers.add("UNREGISTERED_BUSINESS_OBJECT");
        if (snapshot == null || !snapshot.parsePassed()) blockers.add("DBT_PARSE_FAILED");
        if (snapshot == null || !snapshot.testsPassed()) blockers.add("DBT_TEST_FAILED");
        if (driftKinds != null) driftKinds.forEach(kind -> blockers.add(kind.name()));
        List<String> result = List.copyOf(blockers);
        return new ReleaseGateResult(result.isEmpty(), result.isEmpty() ? "RELEASE_READY" : "BLOCKED", result);
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
