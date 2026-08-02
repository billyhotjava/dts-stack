package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Reconstructs an explicitly non-lossless, statically verifiable dbt source project. */
final class DbtCanonicalProjectReconstructor {

    private static final Pattern PROJECT_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Set<String> MATERIALIZATIONS = Set.of("table", "view", "incremental", "ephemeral");
    private static final Set<String> ACCEPTED_STATUSES = Set.of("IMPORTED", "COMPILED");
    private static final List<String> STRUCTURED_TYPES = List.of("SCHEMA", "CONFIG", "DEPENDENCY");

    private final ObjectMapper objectMapper;

    DbtCanonicalProjectReconstructor(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    CanonicalProject initialize(UUID modelSpecId, String materialization) {
        if (modelSpecId == null) throw unavailable();
        String token = modelSpecId.toString().replace("-", "").substring(0, 12);
        String projectKey = "dts_model_" + token;
        String modelName = "model_" + token;
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", projectFile(projectKey, List.of("models"), materialization));
        files.put(
            "models/" + modelName + ".sql",
            "-- DTS canonical initialization; replace this placeholder before commit.\n" +
            "select 1 as _dts_placeholder where 1 = 0\n"
        );
        return canonical(projectKey, files, Map.of());
    }

    CanonicalProject reconstruct(
        ImplementationSnapshot implementation,
        List<ArtifactEvidence> artifacts,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        if (implementation == null || !PROJECT_KEY.matcher(Objects.toString(implementation.projectKey(), "")).matches()) {
            throw unavailable();
        }
        LinkedHashMap<String, ArtifactEvidence> sql = new LinkedHashMap<>();
        Map<String, Map<String, ArtifactEvidence>> structured = new LinkedHashMap<>();
        (artifacts == null ? List.<ArtifactEvidence>of() : artifacts)
            .stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ArtifactEvidence::artifactPath, Comparator.nullsFirst(String::compareTo)))
            .forEach(artifact -> {
                requireExactArtifact(
                    artifact,
                    modelSpecId,
                    modelRevision,
                    modelChecksum,
                    implementationRevision,
                    implementationChecksum
                );
                String type = Objects.toString(artifact.artifactType(), "").toUpperCase(Locale.ROOT);
                if ("CONFIG".equals(type) && ".dts/dbt-project-bundle.json".equals(artifact.artifactPath())) return;
                if ("SQL".equals(type)) {
                    String path = normalizedPath(artifact.artifactPath(), artifact.artifactContent());
                    if (!path.endsWith(".sql") || sql.putIfAbsent(path, artifact) != null) throw unavailable();
                    return;
                }
                if (!STRUCTURED_TYPES.contains(type)) throw unavailable();
                String suffix = "#" + type.toLowerCase(Locale.ROOT);
                String path = Objects.toString(artifact.artifactPath(), "");
                if (!path.endsWith(suffix)) throw unavailable();
                String basePath = normalizedPath(path.substring(0, path.length() - suffix.length()), "");
                if (structured.computeIfAbsent(basePath, ignored -> new LinkedHashMap<>()).putIfAbsent(type, artifact) != null) {
                    throw unavailable();
                }
            });
        if (sql.isEmpty() || !structured.keySet().equals(sql.keySet())) throw unavailable();

        ArrayNode schemaModels = objectMapper.createArrayNode();
        LinkedHashMap<String, List<String>> dependencies = new LinkedHashMap<>();
        Set<String> modelNames = new LinkedHashSet<>();
        for (Map.Entry<String, ArtifactEvidence> entry : sql.entrySet()) {
            String path = entry.getKey();
            Map<String, ArtifactEvidence> metadata = structured.get(path);
            if (metadata == null || !metadata.keySet().containsAll(STRUCTURED_TYPES)) throw unavailable();
            JsonNode schema = object(metadata.get("SCHEMA").artifactContent());
            JsonNode config = object(metadata.get("CONFIG").artifactContent());
            JsonNode dependency = array(metadata.get("DEPENDENCY").artifactContent());
            if (!schema.path("columns").isArray() || !schema.path("tests").isArray()) throw unavailable();
            rejectDynamicConfig(config);

            String name = modelName(path);
            if (!modelNames.add(name)) throw unavailable();
            ObjectNode schemaModel = objectMapper.createObjectNode();
            schemaModel.put("name", name);
            schemaModel.set("columns", schema.path("columns").deepCopy());
            schemaModel.set("tests", schema.path("tests").deepCopy());
            schemaModels.add(schemaModel);
            dependencies.put(path, textArray(dependency));
        }

        List<String> modelPaths = sql.keySet().stream().map(DbtCanonicalProjectReconstructor::topDirectory).distinct().sorted().toList();
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dbt_project.yml", projectFile(implementation.projectKey(), modelPaths, implementation.materialization()));
        sql.forEach((path, artifact) -> files.put(path, artifact.artifactContent()));
        ObjectNode schemaRoot = objectMapper.createObjectNode();
        schemaRoot.put("version", 2);
        schemaRoot.set("models", schemaModels);
        try {
            files.put(modelPaths.getFirst() + "/schema.yml", objectMapper.writeValueAsString(schemaRoot) + "\n");
        } catch (JsonProcessingException exception) {
            throw unavailable();
        }
        return canonical(implementation.projectKey(), files, dependencies);
    }

    private CanonicalProject canonical(
        String projectKey,
        Map<String, String> files,
        Map<String, List<String>> dependencies
    ) {
        List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(
            files.entrySet().stream().map(entry -> new FileInput(entry.getKey(), entry.getValue())).toList()
        );
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        normalized.stream().sorted(Comparator.comparing(FileInput::path)).forEach(file -> ordered.put(file.path(), file.content()));
        return new CanonicalProject(projectKey, Map.copyOf(ordered), Map.copyOf(dependencies));
    }

    private void requireExactArtifact(
        ArtifactEvidence artifact,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        String content = artifact.artifactContent();
        if (
            !Objects.equals(artifact.modelSpecId(), modelSpecId) ||
            artifact.modelRevision() != modelRevision ||
            !Objects.equals(artifact.modelChecksum(), modelChecksum) ||
            artifact.implementationRevision() != implementationRevision ||
            !Objects.equals(artifact.implementationChecksum(), implementationChecksum) ||
            !ACCEPTED_STATUSES.contains(Objects.toString(artifact.status(), "").toUpperCase(Locale.ROOT)) ||
            content == null ||
            !Objects.equals(artifact.artifactChecksum(), ModelPackageChecksum.sha256Text(content))
        ) {
            throw unavailable();
        }
    }

    private static String projectFile(String projectKey, List<String> modelPaths, String materialization) {
        if (!PROJECT_KEY.matcher(Objects.toString(projectKey, "")).matches() || modelPaths == null || modelPaths.isEmpty()) {
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

    private JsonNode object(String content) {
        JsonNode value = json(content);
        if (!value.isObject()) throw unavailable();
        return value;
    }

    private JsonNode array(String content) {
        JsonNode value = json(content);
        if (!value.isArray()) throw unavailable();
        return value;
    }

    private JsonNode json(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw unavailable();
        }
    }

    private static void rejectDynamicConfig(JsonNode config) {
        if (config == null || !config.isObject()) throw unavailable();
        List<String> forbidden = List.of("pre-hook", "post-hook", "on-run-start", "on-run-end", "vars", "packages");
        if (forbidden.stream().anyMatch(config::has)) throw unavailable();
    }

    private static List<String> textArray(JsonNode values) {
        ArrayList<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank()) throw unavailable();
            result.add(value.textValue());
        }
        return List.copyOf(result);
    }

    private static String normalizedPath(String path, String content) {
        return DbtImplementationDraftContract.normalizeFiles(List.of(new FileInput(path, content))).getFirst().path();
    }

    private static String modelName(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.substring(0, file.length() - ".sql".length());
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

    record CanonicalProject(String projectKey, Map<String, String> files, Map<String, List<String>> expectedDependencies) {
        CanonicalProject {
            if (!PROJECT_KEY.matcher(Objects.toString(projectKey, "")).matches()) throw unavailable();
            files = Map.copyOf(files);
            expectedDependencies = Map.copyOf(expectedDependencies);
        }
    }
}
