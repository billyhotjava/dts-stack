package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Adds the single canonical project file required by generated and reconstructed dbt bundles. */
public final class CanonicalDbtProjectBundleAssembler {

    private static final Pattern PROJECT_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Pattern MODEL_PATH = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$");
    private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental", "ephemeral");

    private CanonicalDbtProjectBundleAssembler() {}

    public static Map<String, String> assemble(
        String projectKey,
        String materialization,
        Map<String, String> generatedFiles
    ) {
        if (generatedFiles == null || generatedFiles.isEmpty()) throw unavailable();
        List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(
            generatedFiles.entrySet().stream().map(entry -> new FileInput(entry.getKey(), entry.getValue())).toList()
        );
        if (normalized.stream().anyMatch(file -> "dbt_project.yml".equals(file.path()))) throw unavailable();
        List<String> modelPaths = normalized
            .stream()
            .map(FileInput::path)
            .map(CanonicalDbtProjectBundleAssembler::topDirectory)
            .distinct()
            .sorted()
            .toList();
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", projectFile(projectKey, modelPaths, materialization));
        normalized.forEach(file -> files.put(file.path(), file.content()));
        List<FileInput> complete = DbtImplementationDraftContract.normalizeFiles(
            files.entrySet().stream().map(entry -> new FileInput(entry.getKey(), entry.getValue())).toList()
        );
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        complete.stream().sorted(Comparator.comparing(FileInput::path)).forEach(file -> ordered.put(file.path(), file.content()));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    static String projectFile(String projectKey, List<String> modelPaths, String materialization) {
        if (
            !PROJECT_KEY.matcher(Objects.toString(projectKey, "")).matches() ||
            modelPaths == null ||
            modelPaths.isEmpty() ||
            modelPaths.stream().anyMatch(path -> !MODEL_PATH.matcher(Objects.toString(path, "")).matches())
        ) {
            throw unavailable();
        }
        String normalizedMaterialization = Objects.toString(materialization, "").toLowerCase(Locale.ROOT);
        if (!MATERIALIZATIONS.contains(normalizedMaterialization)) throw unavailable();
        StringBuilder result = new StringBuilder()
            .append("name: ").append(projectKey).append('\n')
            .append("version: '1.0'\n")
            .append("config-version: 2\n")
            .append("model-paths:\n");
        modelPaths.forEach(path -> result.append("  - ").append(path).append('\n'));
        return result
            .append("models:\n  ").append(projectKey).append(":\n    +materialized: ")
            .append(normalizedMaterialization).append('\n')
            .toString();
    }

    private static String topDirectory(String path) {
        int separator = path.indexOf('/');
        if (separator < 1) throw unavailable();
        return path.substring(0, separator);
    }

    private static DbtImplementationDraftContract.DraftException unavailable() {
        return DbtImplementationDraftContract.precondition(
            "DBT_DRAFT_SOURCE_BUNDLE_UNAVAILABLE",
            "The exactly pinned dbt source evidence cannot be reconstructed safely"
        );
    }
}
