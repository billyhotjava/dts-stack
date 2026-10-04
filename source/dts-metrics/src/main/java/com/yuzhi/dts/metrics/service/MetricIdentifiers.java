package com.yuzhi.dts.metrics.service;

/**
 * Shared SQL identifier sanitizer for metric artifact generation.
 *
 * <p>Centralised so the candidate-SQL builder ({@link MetricCandidateArtifactBuilder}) and the lifecycle
 * model-name resolver ({@link MetricModelLifecycleService}) apply the EXACT same normalisation. This is a
 * security-relevant sanitizer (it is the boundary that keeps user-supplied graph identifiers out of raw
 * SQL), so it must never drift between call sites: only {@code [A-Za-z0-9_]} survive, runs of {@code _} are
 * collapsed, and leading/trailing {@code _} are trimmed.
 */
final class MetricIdentifiers {

    private MetricIdentifiers() {}

    static String safeIdentifier(String value) {
        String identifier = (value != null ? value.trim() : "").replaceAll("[^A-Za-z0-9_]", "_");
        while (identifier.contains("__")) {
            identifier = identifier.replace("__", "_");
        }
        return identifier.replaceAll("^_+|_+$", "");
    }
}
