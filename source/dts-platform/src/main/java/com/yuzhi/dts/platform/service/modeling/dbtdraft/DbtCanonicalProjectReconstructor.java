package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
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
    private static final Pattern MODEL_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Set<String> ACCEPTED_STATUSES = Set.of("IMPORTED", "COMPILED");
    private static final List<String> STRUCTURED_TYPES = List.of("SCHEMA", "CONFIG", "DEPENDENCY");

    private final ObjectMapper objectMapper;

    DbtCanonicalProjectReconstructor(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    CanonicalProject initialize(UUID modelSpecId, String materialization, String targetPhysicalName) {
        return initialize(modelSpecId, materialization, targetPhysicalName, null);
    }

    CanonicalProject initialize(
        UUID modelSpecId,
        String materialization,
        String targetPhysicalName,
        Resolution dependencies
    ) {
        if (modelSpecId == null) throw unavailable();
        String token = modelSpecId.toString().replace("-", "").substring(0, 12);
        String projectKey = "dts_model_" + token;
        String modelName = Objects.toString(targetPhysicalName, "").trim();
        if (!modelName.matches("^[a-z][a-z0-9_]{0,62}$")) throw unavailable();
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
            "dbt_project.yml",
            CanonicalDbtProjectBundleAssembler.projectFile(projectKey, List.of("models"), materialization)
        );
        if (dependencies == null) {
            files.put(
                "models/" + modelName + ".sql",
                "-- DTS canonical initialization; replace this placeholder before commit.\n" +
                "select 1 as _dts_placeholder where 1 = 0\n"
            );
            return canonical(projectKey, files, Map.of());
        }
        Snapshot snapshot = dependencies.snapshot();
        if (!Objects.equals(modelSpecId, snapshot.modelSpecId())) throw unavailable();
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        List<ManagedDependency> managed = new ArrayList<>();
        if (!snapshot.physicalSources().isEmpty()) {
            files.put("models/.dts_dependencies/sources.yml", managedSources(snapshot, dependencies));
        }
        for (PhysicalSource source : snapshot.physicalSources()) {
            String[] identity = source.dbtSourceUniqueId().split("\\.", -1);
            if (identity.length != 4 || !"source".equals(identity[0])) throw unavailable();
            managed.add(
                new ManagedDependency(
                    "source('" + identity[2] + "', '" + identity[3] + "')",
                    source.dbtSourceUniqueId()
                )
            );
        }
        for (ModelInput input : snapshot.modelInputs()) {
            String proxyName = "dts_ref_" + input.modelSpecId().toString().replace("-", "").substring(0, 12);
            String proxyPath = "models/.dts_dependencies/" + proxyName + ".sql";
            files.put(
                proxyPath,
                "{{ config(materialized='ephemeral', tags=['dts-managed-dependency']) }}\n" +
                "-- Managed dependency proxy for " + input.dbtUniqueId() + ". Do not rename or delete.\n" +
                "select 1 as _dts_dependency_placeholder where 1 = 0\n"
            );
            String localUniqueId = "model." + projectKey + "." + proxyName;
            aliases.put(localUniqueId, input.dbtUniqueId());
            managed.add(new ManagedDependency("ref('" + proxyName + "')", localUniqueId));
        }
        String targetPath = "models/" + modelName + ".sql";
        files.put(targetPath, initializedTargetSql(managed));
        Map<String, List<String>> expected = Map.of(
            targetPath,
            managed.stream().map(ManagedDependency::localUniqueId).sorted().toList()
        );
        return canonical(projectKey, files, expected, snapshot, aliases);
    }

    private static String managedSources(Snapshot snapshot, Resolution dependencies) {
        StringBuilder yaml = new StringBuilder("version: 2\nsources:\n");
        for (PhysicalSource source : snapshot.physicalSources()) {
            PhysicalSourceFact fact = dependencies.physicalSourceFacts().get(source.sourceBindingId());
            if (fact == null || !fact.current() || fact.executableRef() == null || fact.executableRef().isBlank()) {
                throw unavailable();
            }
            String[] identity = source.dbtSourceUniqueId().split("\\.", -1);
            if (identity.length != 4) throw unavailable();
            List<String> relation = java.util.Arrays
                .stream(fact.executableRef().split("\\."))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            if (relation.isEmpty() || relation.size() > 3) throw unavailable();
            String identifier = relation.getLast();
            String schema = relation.size() >= 2 ? relation.get(relation.size() - 2) : null;
            String database = relation.size() == 3 ? relation.getFirst() : null;
            yaml.append("  - name: '").append(yaml(identity[2])).append("'\n");
            if (database != null) yaml.append("    database: '").append(yaml(database)).append("'\n");
            if (schema != null) yaml.append("    schema: '").append(yaml(schema)).append("'\n");
            yaml
                .append("    tables:\n")
                .append("      - name: '")
                .append(yaml(identity[3]))
                .append("'\n")
                .append("        identifier: '")
                .append(yaml(identifier))
                .append("'\n");
        }
        return yaml.toString();
    }

    private static String initializedTargetSql(List<ManagedDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return "-- DTS canonical initialization; replace this placeholder before commit.\n" +
            "select 1 as _dts_placeholder where 1 = 0\n";
        }
        StringBuilder sql = new StringBuilder(
            "-- DTS managed dependency skeleton. Keep every source/ref until ModelSpec is changed.\nwith\n"
        );
        for (int index = 0; index < dependencies.size(); index++) {
            if (index > 0) sql.append(",\n");
            sql
                .append("_dts_dependency_")
                .append(index + 1)
                .append(" as (select * from {{ ")
                .append(dependencies.get(index).expression())
                .append(" }})");
        }
        sql.append("\nselect _dts_dependency_1.*\n  from _dts_dependency_1\n");
        for (int index = 1; index < dependencies.size(); index++) {
            sql
                .append("  left join _dts_dependency_")
                .append(index + 1)
                .append(" on 1 = 0\n");
        }
        return sql.append(" where 1 = 0\n").toString();
    }

    private static String yaml(String value) {
        return value.replace("'", "''");
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
        files.put(
            "dbt_project.yml",
            CanonicalDbtProjectBundleAssembler.projectFile(
                implementation.projectKey(),
                modelPaths,
                implementation.materialization()
            )
        );
        sql.forEach((path, artifact) -> files.put(path, artifact.artifactContent()));
        addSameProjectDependencyStubs(implementation.projectKey(), modelNames, dependencies, files);
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

    private static void addSameProjectDependencyStubs(
        String projectKey,
        Set<String> representedModelNames,
        Map<String, List<String>> dependencies,
        Map<String, String> files
    ) {
        String sameProjectPrefix = "model." + projectKey + ".";
        dependencies
            .values()
            .stream()
            .flatMap(List::stream)
            .distinct()
            .sorted()
            .forEach(dependency -> {
                if (!dependency.startsWith(sameProjectPrefix)) return;
                String modelName = dependency.substring(sameProjectPrefix.length());
                if (representedModelNames.contains(modelName)) return;
                if (!MODEL_NAME.matcher(modelName).matches()) throw unavailable();
                String path = "models/dts_dependencies/" + modelName + ".sql";
                if (files.containsKey(path)) throw unavailable();
                files.put(
                    path,
                    "{{ config(materialized='ephemeral', tags=['dts-source-evidence-placeholder']) }}\n" +
                        "-- Static dependency placeholder reconstructed from exact imported lineage evidence.\n" +
                        "select 1 as _dts_dependency_placeholder where 1 = 0\n"
                );
            });
    }

    private CanonicalProject canonical(
        String projectKey,
        Map<String, String> files,
        Map<String, List<String>> dependencies
    ) {
        return canonical(projectKey, files, dependencies, null, Map.of());
    }

    private CanonicalProject canonical(
        String projectKey,
        Map<String, String> files,
        Map<String, List<String>> dependencies,
        Snapshot dependencySnapshot,
        Map<String, String> dependencyAliases
    ) {
        List<FileInput> normalized = DbtImplementationDraftContract.normalizeFiles(
            files.entrySet().stream().map(entry -> new FileInput(entry.getKey(), entry.getValue())).toList()
        );
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        normalized.stream().sorted(Comparator.comparing(FileInput::path)).forEach(file -> ordered.put(file.path(), file.content()));
        return new CanonicalProject(
            projectKey,
            Map.copyOf(ordered),
            Map.copyOf(dependencies),
            dependencySnapshot,
            Map.copyOf(dependencyAliases)
        );
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

    private record ManagedDependency(String expression, String localUniqueId) {}

    record CanonicalProject(
        String projectKey,
        Map<String, String> files,
        Map<String, List<String>> expectedDependencies,
        Snapshot dependencySnapshot,
        Map<String, String> dependencyAliases
    ) {
        CanonicalProject {
            if (!PROJECT_KEY.matcher(Objects.toString(projectKey, "")).matches()) throw unavailable();
            files = Map.copyOf(files);
            expectedDependencies = Map.copyOf(expectedDependencies);
            dependencyAliases = Map.copyOf(dependencyAliases == null ? Map.of() : dependencyAliases);
        }
    }
}
