package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifact;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.CandidateArtifactEntry;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Identical, deterministic source overlay for candidate builds and published operational runs. */
final class PinnedDbtSourceArtifacts {
    private PinnedDbtSourceArtifacts() {}

    static List<CandidateArtifactEntry> attach(List<CandidateArtifactEntry> entries, List<PinnedSourceDefinition> sources) {
        String yaml = renderPinnedSources(sources);
        if (yaml == null) return entries;
        if (entries.isEmpty()) throw new IllegalArgumentException("Source overlay requires an operational model scope");
        List<CandidateArtifactEntry> result = new ArrayList<>(entries);
        CandidateArtifactEntry first = result.getFirst();
        List<CandidateArtifact> artifacts = new ArrayList<>(first.artifacts());
        artifacts.add(new CandidateArtifact("models/_dts_pinned_sources.yml", sha256(yaml), yaml));
        result.set(0, new CandidateArtifactEntry(first.modelSpecId(), first.modelRevision(), first.modelChecksum(),
            first.implementationRevision(), first.implementationChecksum(), first.dbtUniqueId(), List.copyOf(artifacts)));
        return List.copyOf(result);
    }

    static String renderPinnedSources(List<PinnedSourceDefinition> requestedSources) {
        if (requestedSources == null || requestedSources.isEmpty()) return null;
        List<PinnedSourceDefinition> sources = requestedSources
            .stream()
            .sorted(
                Comparator.comparing(PinnedSourceDefinition::sourceName)
                    .thenComparing(PinnedSourceDefinition::schemaName)
                    .thenComparing(PinnedSourceDefinition::tableName)
                    .thenComparing(definition -> definition.sourceBindingId().toString())
            )
            .toList();
        Map<SourceGroup, Map<String, List<PinnedSourceDefinition>>> grouped = new LinkedHashMap<>();
        for (PinnedSourceDefinition source : sources) {
            grouped
                .computeIfAbsent(
                    new SourceGroup(source.sourceName(), source.schemaName()),
                    ignored -> new LinkedHashMap<>()
                )
                .computeIfAbsent(source.tableName(), ignored -> new ArrayList<>())
                .add(source);
        }
        StringBuilder yaml = new StringBuilder("version: 2\nsources:\n");
        grouped.forEach((group, tables) -> {
            yaml.append("  - name: ").append(group.sourceName()).append('\n');
            yaml.append("    schema: ").append(group.schemaName()).append('\n');
            yaml.append("    tables:\n");
            tables.forEach((table, pins) -> {
                for (PinnedSourceDefinition pin : pins) {
                    yaml.append("      # dts-pin: ")
                        .append(pin.sourceBindingId())
                        .append('/')
                        .append(safeYamlComment(pin.resolvedVersion()))
                        .append('\n');
                }
                yaml.append("      - name: ").append(table).append('\n');
            });
        });
        return yaml.toString();
    }

    private static String safeYamlComment(String value) {
        return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record SourceGroup(String sourceName, String schemaName) {}
}
