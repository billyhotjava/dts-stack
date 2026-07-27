package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCompilerProjection;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtCompiler;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Explicit live-runtime proof for Sprint-76 F1. It is skipped during normal unit runs and enabled
 * with {@code -Ddts.realDbtCompile=true}; the test uses the already deployed dts-dbt container.
 */
class DbtScopedProjectServiceRealCompileIT {

    private static final Path DBT_WORKSPACE = Path.of("")
        .toAbsolutePath()
        .normalize()
        .resolve("../../services/dts-dbt")
        .normalize();
    private static final String CONTAINER_WORKSPACE = "/opt/dbt";
    private static final String PROJECT_NAME = "dts";

    @Test
    void compilesOrdinaryDimFactAndSummaryIntoOneTraceableManifest() throws Exception {
        Assumptions.assumeTrue(
            Boolean.getBoolean("dts.realDbtCompile"),
            "Enable only for the explicit Sprint-76 real dbt runtime gate"
        );
        assertThat(DBT_WORKSPACE.resolve("dbt_project.yml")).isRegularFile();

        DbtConfigService config = mock(DbtConfigService.class);
        when(config.resolveProjectDir()).thenReturn(DBT_WORKSPACE.toString());
        ModelingSqlModelRepository sqlModels = mock(ModelingSqlModelRepository.class);
        DbtScopedProjectService scoped = new DbtScopedProjectService(config, new DbtProperties(), sqlModels);

        CompiledEntry dimension = compileEntry(
            "76000000-0000-0000-0000-000000000001",
            ModelingVNextContract.ModelType.DIMENSION,
            ModelingVNextContract.Layer.DWD,
            "s76_dimension_project",
            "table",
            "FULL",
            new ModelingVNextContract.SourceRef(
                "TABLE",
                "ods.ods_erp_smoke_project",
                ModelingVNextContract.Layer.ODS
            ),
            List.of("project_id", "owner_name"),
            List.of(
                new FieldMapping("project_id", "project_id"),
                new FieldMapping("owner_name", "owner_name")
            ),
            Map.of("project_id", "string")
        );
        CompiledEntry fact = compileEntry(
            "76000000-0000-0000-0000-000000000002",
            ModelingVNextContract.ModelType.FACT,
            ModelingVNextContract.Layer.DWD,
            "s76_fact_budget",
            "incremental",
            "INCREMENTAL",
            new ModelingVNextContract.SourceRef(
                "TABLE",
                "ods.ods_erp_smoke_project",
                ModelingVNextContract.Layer.ODS
            ),
            List.of("project_id", "budget_amount"),
            List.of(
                new FieldMapping("project_id", "project_id"),
                new FieldMapping("budget_amount", "budget_amount")
            ),
            Map.of("project_id", "string", "budget_amount", "decimal")
        );
        CompiledEntry summary = compileEntry(
            "76000000-0000-0000-0000-000000000003",
            ModelingVNextContract.ModelType.SUMMARY,
            ModelingVNextContract.Layer.DWS,
            "s76_summary_budget",
            "view",
            "FULL",
            new ModelingVNextContract.SourceRef(
                "DBT_MODEL",
                fact.selector(),
                ModelingVNextContract.Layer.DWD
            ),
            List.of("project_id", "budget_amount"),
            List.of(
                new FieldMapping("project_id", "project_id"),
                new FieldMapping("budget_amount", "budget_amount")
            ),
            Map.of()
        );

        DbtScopedProjectService.ScopedCandidateProject project = scoped.prepareCandidate(
            List.of(dimension.entry(), fact.entry(), summary.entry())
        );
        Path hostProject = Path.of(project.projectDir());
        String runtimeKey = "sprint76-real-compile-" + UUID.randomUUID();
        Path runtimeDirectory = DBT_WORKSPACE.resolve(".dts-runtime").resolve(runtimeKey);
        Path compileLog = runtimeDirectory.resolve("dbt-compile.log");
        Files.createDirectories(runtimeDirectory);
        try {
            String containerProject = CONTAINER_WORKSPACE + "/" + DBT_WORKSPACE.relativize(hostProject);
            String containerTarget = CONTAINER_WORKSPACE + "/.dts-runtime/" + runtimeKey + "/target";
            List<String> command = new ArrayList<>(
                List.of(
                    "docker",
                    "exec",
                    "--user",
                    "1000:1000",
                    "-e",
                    "DBT_USE_COLORS=false",
                    "-e",
                    "DBT_LOG_PATH=" + CONTAINER_WORKSPACE + "/.dts-runtime/" + runtimeKey + "/logs",
                    "dts-dbt",
                    "dbt",
                    "compile",
                    "--project-dir",
                    containerProject,
                    "--profiles-dir",
                    "/opt/dbt/profiles",
                    "--target-path",
                    containerTarget,
                    "--select"
                )
            );
            command.addAll(List.of(dimension.selector(), fact.selector(), summary.selector()));
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(compileLog.toFile())
                .start();
            boolean finished = process.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) process.destroyForcibly();
            String output = Files.exists(compileLog)
                ? Files.readString(compileLog, StandardCharsets.UTF_8)
                : "";
            assertThat(finished).as("dbt compile timeout: %s", output).isTrue();
            assertThat(process.exitValue()).as("dbt compile output:%n%s", output).isZero();

            JsonNode manifest = new ObjectMapper().readTree(
                runtimeDirectory.resolve("target/manifest.json").toFile()
            );
            assertManifestNode(manifest, dimension, "table");
            assertManifestNode(manifest, fact, "incremental");
            assertManifestNode(manifest, summary, "view");

            String factUniqueId = uniqueId(fact.selector());
            String factStgUniqueId = uniqueId("stg_" + fact.selector());
            String summaryStgUniqueId = uniqueId("stg_" + summary.selector());
            assertThat(textValues(manifest.path("parent_map").path(factUniqueId))).contains(factStgUniqueId);
            assertThat(textValues(manifest.path("child_map").path(factStgUniqueId))).contains(factUniqueId);
            assertThat(textValues(manifest.path("parent_map").path(summaryStgUniqueId))).contains(factUniqueId);
            assertThat(manifest.path("nodes").path(factStgUniqueId).path("compiled_code").asText())
                .contains("cast(project_id as text)")
                .contains("cast(budget_amount as numeric)");
            verifyNoInteractions(sqlModels);
        } finally {
            scoped.releaseCandidateProject(project.bundleChecksum());
            deleteRecursively(runtimeDirectory);
            deleteRecursively(hostProject);
        }
    }

    private static CompiledEntry compileEntry(
        String modelId,
        ModelingVNextContract.ModelType modelType,
        ModelingVNextContract.Layer layer,
        String targetIdentifier,
        String materialization,
        String loadStrategy,
        ModelingVNextContract.SourceRef source,
        List<String> columns,
        List<FieldMapping> mappings,
        Map<String, String> casts
    ) {
        UUID id = UUID.fromString(modelId);
        String selector = "model_" + modelId.replace("-", "_");
        ModelingVNextContract.ModelSpec model = new ModelingVNextContract.ModelSpec(
            modelId,
            null,
            null,
            layer,
            modelType,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            targetIdentifier,
            new ModelingVNextContract.Grain("one row per project", List.of("project_id")),
            List.of(),
            List.of(source),
            columns,
            List.of(),
            materialization,
            1,
            List.of(),
            null
        );
        Map<String, Object> settings = new java.util.LinkedHashMap<>();
        settings.put("targetPhysicalName", targetIdentifier);
        settings.put("loadStrategy", loadStrategy);
        settings.put("partitionFields", List.of());
        if (!casts.isEmpty()) settings.put("casts", casts);
        ModelSpecCompilerProjection.ImplementationProjection projection =
            new ModelSpecCompilerProjection.ImplementationProjection(
                model,
                "tenant-sprint76",
                "a".repeat(64),
                1,
                "b".repeat(64),
                uniqueId(selector),
                "DBT_MODEL".equals(source.kind()) ? InputMode.UPSTREAM_MODEL : InputMode.PHYSICAL_ASSET,
                List.of(),
                mappings,
                settings,
                materialization,
                List.of("project_id"),
                "76000000-0000-0000-0000-000000000000"
            );
        ModelingDbtCompiler.CompiledArtifacts compiled = ModelingDbtCompiler.compile(projection);
        List<DbtScopedProjectService.CandidateArtifact> artifacts = compiled
            .files()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(file ->
                artifact(compiled.outputDirectory() + "/" + file.getKey(), file.getValue())
            )
            .toList();
        DbtScopedProjectService.CandidateArtifactEntry entry =
            new DbtScopedProjectService.CandidateArtifactEntry(
                id,
                1,
                projection.modelChecksum(),
                projection.implementationRevision(),
                projection.implementationChecksum(),
                projection.dbtUniqueId(),
                artifacts
            );
        return new CompiledEntry(entry, selector, targetIdentifier, modelId);
    }

    private static void assertManifestNode(
        JsonNode manifest,
        CompiledEntry expected,
        String materialization
    ) {
        JsonNode node = manifest.path("nodes").path(uniqueId(expected.selector()));
        assertThat(node.isMissingNode()).isFalse();
        assertThat(node.path("alias").asText()).isEqualTo(expected.targetIdentifier());
        assertThat(node.path("config").path("materialized").asText()).isEqualTo(materialization);
        assertThat(node.path("config").path("meta").path("modelSpecId").asText())
            .isEqualTo(expected.modelId());
        assertThat(node.path("config").path("meta").path("implementationRevision").asInt())
            .isEqualTo(1);
    }

    private static List<String> textValues(JsonNode values) {
        if (!values.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private static String uniqueId(String selector) {
        return "model." + PROJECT_NAME + "." + selector;
    }

    private static DbtScopedProjectService.CandidateArtifact artifact(String path, String content) {
        return new DbtScopedProjectService.CandidateArtifact(path, sha256(content), content);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to clean Sprint-76 dbt compile project", failure);
        }
    }

    private record CompiledEntry(
        DbtScopedProjectService.CandidateArtifactEntry entry,
        String selector,
        String targetIdentifier,
        String modelId
    ) {}
}
