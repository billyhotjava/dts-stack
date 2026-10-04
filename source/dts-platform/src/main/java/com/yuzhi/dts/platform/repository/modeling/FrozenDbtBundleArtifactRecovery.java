package com.yuzhi.dts.platform.repository.modeling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.BuildArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Restores immutable model dependencies omitted by older implementation commits. */
final class FrozenDbtBundleArtifactRecovery {

    private static final String BUNDLE_PATH = ".dts/dbt-project-bundle.json";
    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern PROJECT_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern DBT_UNIQUE_ID = Pattern.compile(
        "^model\\.[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*$"
    );

    private FrozenDbtBundleArtifactRecovery() {}

    static List<BuildArtifact> recover(
        ObjectMapper objectMapper,
        UUID modelSpecId,
        String expectedDbtUniqueId,
        String inputsJson,
        FrozenBundleEvidence evidence,
        List<BuildArtifact> baseArtifacts
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper is required");
        Objects.requireNonNull(modelSpecId, "modelSpecId is required");
        Objects.requireNonNull(baseArtifacts, "baseArtifacts are required");
        JsonNode inputs = parse(objectMapper, inputsJson, modelSpecId);
        JsonNode config = bundleConfig(inputs, modelSpecId);
        if (config == null) return List.copyOf(baseArtifacts);

        String projectKey = required(config, "projectKey", PROJECT_KEY, modelSpecId);
        String configuredDbtUniqueId = required(config, "dbtUniqueId", DBT_UNIQUE_ID, modelSpecId);
        String projectChecksum = required(config, "projectChecksum", CHECKSUM, modelSpecId);
        String bundleChecksum = required(config, "bundleChecksum", CHECKSUM, modelSpecId);
        if (!Objects.equals(expectedDbtUniqueId, configuredDbtUniqueId)) {
            throw stale(modelSpecId, "Frozen dbt bundle identity does not match the candidate");
        }
        if (evidence == null) {
            throw failure(
                "MATERIALIZATION_ARTIFACT_MISSING",
                "Pinned dbt project bundle is unavailable",
                modelSpecId,
                Map.of("path", BUNDLE_PATH)
            );
        }
        if (
            !Objects.equals(BUNDLE_PATH, evidence.path()) ||
            !Objects.equals(projectKey, evidence.projectKey()) ||
            !Objects.equals(configuredDbtUniqueId, evidence.dbtUniqueId()) ||
            !Objects.equals(bundleChecksum, evidence.contentChecksum()) ||
            evidence.content() == null
        ) {
            throw stale(modelSpecId, "Pinned dbt project bundle identity has drifted");
        }

        DbtProjectBundleManifest.RestoredBundle restored;
        try {
            restored = DbtProjectBundleManifest.restore(
                objectMapper,
                evidence.content(),
                bundleChecksum,
                projectChecksum
            );
        } catch (RuntimeException invalid) {
            throw stale(modelSpecId, "Pinned dbt project bundle content is invalid");
        }
        if (!Objects.equals(projectKey, restored.projectKey())) {
            throw stale(modelSpecId, "Pinned dbt project bundle project has drifted");
        }

        LinkedHashMap<String, BuildArtifact> merged = new LinkedHashMap<>();
        LinkedHashSet<String> availableModelNames = new LinkedHashSet<>();
        List<String> rootSql = new ArrayList<>();
        for (BuildArtifact artifact : baseArtifacts) {
            if (artifact == null || artifact.path() == null) {
                throw stale(modelSpecId, "Candidate build artifact is invalid");
            }
            BuildArtifact previous = merged.putIfAbsent(artifact.path(), artifact);
            if (previous != null && !same(previous, artifact)) {
                throw stale(modelSpecId, "Candidate build artifacts contain a path conflict");
            }
            if (isModelSql(artifact.path())) {
                availableModelNames.add(modelName(artifact.path()));
                rootSql.add(artifact.content());
            }
        }
        List<BundleFile> dependencies;
        try {
            dependencies = DbtProjectBundleManifest.resolveLocalModelDependencies(
                restored.files(),
                rootSql,
                availableModelNames
            );
        } catch (DraftException invalid) {
            if ("DBT_DRAFT_BUNDLE_DEPENDENCY_MISSING".equals(invalid.code())) {
                throw failure(
                    "MATERIALIZATION_DEPENDENCY_MISSING",
                    "Pinned dbt model dependency is unavailable",
                    modelSpecId,
                    Map.of()
                );
            }
            throw stale(modelSpecId, "Pinned dbt project dependencies are invalid");
        }
        for (BundleFile file : dependencies) {
            BuildArtifact dependency = new BuildArtifact(file.path(), file.checksum(), file.content());
            BuildArtifact previous = merged.putIfAbsent(file.path(), dependency);
            if (previous != null && !same(previous, dependency)) {
                throw stale(modelSpecId, "Pinned dbt model dependency conflicts with candidate artifacts");
            }
        }
        return List.copyOf(merged.values());
    }

    private static JsonNode parse(ObjectMapper objectMapper, String value, UUID modelSpecId) {
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed == null || !parsed.isArray()) throw new IllegalArgumentException("inputs must be an array");
            return parsed;
        } catch (Exception invalid) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation inputs are invalid",
                modelSpecId,
                Map.of()
            );
        }
    }

    private static JsonNode bundleConfig(JsonNode inputs, UUID modelSpecId) {
        JsonNode match = null;
        for (JsonNode input : inputs) {
            JsonNode config = input == null ? null : input.get("config");
            if (config == null || !config.isObject() || !config.hasNonNull("bundleChecksum")) continue;
            if (match != null) {
                throw failure(
                    "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                    "Current implementation contains multiple frozen dbt bundles",
                    modelSpecId,
                    Map.of()
                );
            }
            match = config;
        }
        return match;
    }

    private static String required(
        JsonNode config,
        String field,
        Pattern pattern,
        UUID modelSpecId
    ) {
        JsonNode value = config.get(field);
        String text = value != null && value.isTextual() ? value.textValue() : null;
        if (text == null || !pattern.matcher(text).matches()) {
            throw failure(
                "MODEL_IMPLEMENTATION_SNAPSHOT_INVALID",
                "Current implementation frozen dbt bundle configuration is invalid",
                modelSpecId,
                Map.of("field", field)
            );
        }
        return text;
    }

    private static boolean isModelSql(String path) {
        return path != null && path.startsWith("models/") && path.endsWith(".sql");
    }

    private static String modelName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.length() - ".sql".length());
    }

    private static boolean same(BuildArtifact left, BuildArtifact right) {
        return Objects.equals(left.contentChecksum(), right.contentChecksum()) && Objects.equals(left.content(), right.content());
    }

    private static ModelReleaseCandidateException stale(UUID modelSpecId, String message) {
        return failure("MATERIALIZATION_ARTIFACT_STALE", message, modelSpecId, Map.of());
    }

    private static ModelReleaseCandidateException failure(
        String code,
        String message,
        UUID modelSpecId,
        Map<String, Object> extra
    ) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("modelSpecId", modelSpecId);
        details.putAll(extra);
        return new ModelReleaseCandidateException(code, message, Kind.CONFLICT, Map.copyOf(details));
    }

    record FrozenBundleEvidence(
        String projectKey,
        String dbtUniqueId,
        String path,
        String contentChecksum,
        String content
    ) {}
}
