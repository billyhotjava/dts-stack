package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DbtProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtScopedProjectServiceTest {

    @Test
    void constructorDoesNotDependOnLegacyModelingSqlRepository() {
        assertThat(DbtScopedProjectService.class.getDeclaredConstructors())
            .flatExtracting(constructor -> Arrays.asList(constructor.getParameterTypes()))
            .extracting(Class::getSimpleName)
            .doesNotContain("ModelingSqlModelRepository");
    }

    @Test
    void legacyPrepareEntryPointIsNotExposed() {
        assertThat(DbtScopedProjectService.class.getDeclaredMethods())
            .noneMatch(method ->
                "prepare".equals(method.getName()) &&
                Arrays.equals(method.getParameterTypes(), new Class<?>[] { String.class })
            );
    }

    @TempDir
    Path workspace;

    private DbtScopedProjectService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(
            workspace.resolve("dbt_project.yml"),
            "name: dts_test\nversion: 1.0.0\nconfig-version: 2\nprofile: dts_test\nmodel-paths: [models]\n",
            StandardCharsets.UTF_8
        );
        Files.createDirectories(workspace.resolve("models/ods"));
        Files.writeString(
            workspace.resolve("models/ods/sources.yml"),
            "version: 2\nsources:\n  - name: ods\n    schema: ods\n    tables:\n      - name: finance_event\n",
            StandardCharsets.UTF_8
        );
        Files.createDirectories(workspace.resolve("models/dwd"));
        Files.writeString(
            workspace.resolve("models/dwd/upstream_finance.sql"),
            "select 1 as finance_id\n",
            StandardCharsets.UTF_8
        );
        Files.createDirectories(workspace.resolve("macros"));
        Files.writeString(
            workspace.resolve("macros/dts_marker.sql"),
            "{% macro dts_marker() %}1{% endmacro %}\n",
            StandardCharsets.UTF_8
        );

        DbtConfigService config = mock(DbtConfigService.class);
        when(config.resolveProjectDir()).thenReturn(workspace.toString());
        DbtProperties properties = new DbtProperties();
        service = new DbtScopedProjectService(config, properties);
    }

    @Test
    void preparesDeterministicCandidateOverlayWithWorkspaceDependencies() throws Exception {
        DbtScopedProjectService.CandidateArtifactEntry entry = candidateEntry();

        DbtScopedProjectService.ScopedCandidateProject first = service.prepareCandidate(List.of(entry));
        DbtScopedProjectService.ScopedCandidateProject replay = service.prepareCandidate(List.of(entry));

        Path projectDir = Path.of(first.projectDir());
        assertThat(projectDir.resolve(".dts-active")).isRegularFile();
        assertThat(replay.bundleChecksum()).isEqualTo(first.bundleChecksum());
        assertThat(replay.projectDir()).isEqualTo(first.projectDir());
        assertThat(first.selector()).isEqualTo("model_30000000_0000_0000_0000_000000000001");
        assertThat(first.entries()).containsExactly(entry);
        assertThat(projectDir.resolve("dbt_project.yml")).isRegularFile();
        assertThat(projectDir.resolve("macros/dts_marker.sql")).isRegularFile();
        assertThat(projectDir.resolve("models/ods/sources.yml")).isRegularFile();
        assertThat(projectDir.resolve("models/dwd/upstream_finance.sql")).isRegularFile();
        assertThat(projectDir.resolve(entry.artifacts().getFirst().path())).hasContent(
            entry.artifacts().getFirst().content()
        );

        Files.writeString(
            workspace.resolve("models/dwd/upstream_finance.sql"),
            "select 2 as finance_id\n",
            StandardCharsets.UTF_8
        );
        DbtScopedProjectService.ScopedCandidateProject dependencyChanged = service.prepareCandidate(List.of(entry));
        assertThat(dependencyChanged.bundleChecksum()).isNotEqualTo(first.bundleChecksum());
        assertThat(dependencyChanged.projectDir()).isNotEqualTo(first.projectDir());

        service.releaseCandidateProject(first.bundleChecksum());
        assertThat(projectDir.resolve(".dts-active")).doesNotExist();
    }

    @Test
    void verifiesImmutableCandidateInputsAfterDbtWritesRuntimeOutputs() throws Exception {
        DbtScopedProjectService.ScopedCandidateProject prepared = service.prepareCandidate(
            List.of(candidateEntry())
        );
        Path project = Path.of(prepared.projectDir());
        Files.createDirectories(project.resolve("target"));
        Files.writeString(
            project.resolve("target/manifest.json"),
            "{\"metadata\":{\"invocation_id\":\"runtime-output\"}}\n",
            StandardCharsets.UTF_8
        );
        Files.createDirectories(project.resolve("logs"));
        Files.writeString(
            project.resolve("logs/dbt.log"),
            "runtime log\n",
            StandardCharsets.UTF_8
        );

        assertThat(
            service.verifyCandidateProject(prepared.bundleChecksum())
        ).isEqualTo(project);

        Files.writeString(
            project.resolve("dbt_project.yml"),
            "name: tampered\n",
            StandardCharsets.UTF_8
        );
        assertFailureCode(
            () -> service.verifyCandidateProject(
                prepared.bundleChecksum()
            ),
            "MATERIALIZATION_BUNDLE_CONFLICT"
        );
    }

    @Test
    void failsClosedForNodeConflictStaleChecksumAndMissingDependency() throws Exception {
        DbtScopedProjectService.CandidateArtifactEntry entry = candidateEntry();
        Files.writeString(
            workspace.resolve("models/dwd/model_30000000_0000_0000_0000_000000000001.sql"),
            "select 'workspace-owner' as owner\n",
            StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> service.prepareCandidate(List.of(entry)))
            .isInstanceOf(DbtScopedProjectService.ScopedProjectException.class)
            .extracting(error -> ((DbtScopedProjectService.ScopedProjectException) error).code())
            .isEqualTo("MATERIALIZATION_NODE_CONFLICT");

        Files.delete(workspace.resolve("models/dwd/model_30000000_0000_0000_0000_000000000001.sql"));
        DbtScopedProjectService.CandidateArtifact stale = new DbtScopedProjectService.CandidateArtifact(
            "models/dwd/stale.sql",
            "0".repeat(64),
            "select 1\n"
        );
        assertThatThrownBy(() -> service.prepareCandidate(List.of(withArtifacts(entry, List.of(stale)))))
            .isInstanceOf(DbtScopedProjectService.ScopedProjectException.class)
            .extracting(error -> ((DbtScopedProjectService.ScopedProjectException) error).code())
            .isEqualTo("MATERIALIZATION_ARTIFACT_STALE");

        DbtScopedProjectService.CandidateArtifact missingRef = artifact(
            "models/dwd/model_30000000_0000_0000_0000_000000000001.sql",
            "select * from {{ ref('missing_upstream') }}\n"
        );
        assertThatThrownBy(() -> service.prepareCandidate(List.of(withArtifacts(entry, List.of(missingRef)))))
            .isInstanceOf(DbtScopedProjectService.ScopedProjectException.class)
            .extracting(error -> ((DbtScopedProjectService.ScopedProjectException) error).code())
            .isEqualTo("MATERIALIZATION_DEPENDENCY_MISSING");
    }

    @Test
    void rejectsUnsafeOversizedAndDuplicateCandidateEntries() {
        DbtScopedProjectService.CandidateArtifactEntry entry = candidateEntry();
        DbtScopedProjectService.CandidateArtifact unsafe = artifact(
            "models\\..\\outside.sql",
            "select 1\n"
        );
        assertFailureCode(
            () -> service.prepareCandidate(List.of(withArtifacts(entry, List.of(unsafe)))),
            "MATERIALIZATION_ARTIFACT_INVALID"
        );

        String selectedPath = entry
            .artifacts()
            .stream()
            .filter(artifact -> artifact.path().endsWith("/model_30000000_0000_0000_0000_000000000001.sql"))
            .findFirst()
            .orElseThrow()
            .path();
        DbtScopedProjectService.CandidateArtifact oversized = artifact(
            selectedPath,
            "x".repeat((5 * 1024 * 1024) + 1)
        );
        assertFailureCode(
            () -> service.prepareCandidate(List.of(withArtifacts(entry, List.of(oversized)))),
            "MATERIALIZATION_ARTIFACT_TOO_LARGE"
        );

        DbtScopedProjectService.CandidateArtifactEntry duplicateSelector = new DbtScopedProjectService.CandidateArtifactEntry(
            UUID.fromString("30000000-0000-0000-0000-000000000002"),
            entry.modelRevision(),
            entry.modelChecksum(),
            entry.implementationRevision(),
            entry.implementationChecksum(),
            entry.dbtUniqueId(),
            entry.artifacts()
        );
        assertFailureCode(
            () -> service.prepareCandidate(List.of(entry, duplicateSelector)),
            "MATERIALIZATION_SCOPE_DUPLICATE"
        );
    }

    @Test
    void rejectsCandidateOverlayDependencyCyclesBeforeDbtExecution() {
        DbtScopedProjectService.CandidateArtifactEntry first = cycleEntry(
            "30000000-0000-0000-0000-000000000001",
            "model_30000000_0000_0000_0000_000000000002"
        );
        DbtScopedProjectService.CandidateArtifactEntry second = cycleEntry(
            "30000000-0000-0000-0000-000000000002",
            "model_30000000_0000_0000_0000_000000000001"
        );

        assertFailureCode(
            () -> service.prepareCandidate(List.of(first, second)),
            "MATERIALIZATION_DEPENDENCY_CYCLE"
        );
    }

    private DbtScopedProjectService.CandidateArtifactEntry candidateEntry() {
        String root = "models/dwd/model_30000000_0000_0000_0000_000000000001/v2/i5/";
        return new DbtScopedProjectService.CandidateArtifactEntry(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            2,
            "a".repeat(64),
            5,
            "b".repeat(64),
            "model.dts_test.model_30000000_0000_0000_0000_000000000001",
            List.of(
                artifact(
                    root + "stg_model_30000000_0000_0000_0000_000000000001.sql",
                    """
                    {{ config(materialized='ephemeral') }}
                    select src.finance_id
                      from {{ source('ods', 'finance_event') }} src
                      join {{ ref('upstream_finance') }} upstream using (finance_id)
                    """
                ),
                artifact(
                    root + "model_30000000_0000_0000_0000_000000000001.sql",
                    "select * from {{ ref('stg_model_30000000_0000_0000_0000_000000000001') }}\n"
                ),
                artifact(
                    root + "model_30000000_0000_0000_0000_000000000001.yml",
                    "version: 2\nmodels:\n  - name: model_30000000_0000_0000_0000_000000000001\n"
                )
            )
        );
    }

    private static DbtScopedProjectService.CandidateArtifactEntry withArtifacts(
        DbtScopedProjectService.CandidateArtifactEntry current,
        List<DbtScopedProjectService.CandidateArtifact> artifacts
    ) {
        return new DbtScopedProjectService.CandidateArtifactEntry(
            current.modelSpecId(),
            current.modelRevision(),
            current.modelChecksum(),
            current.implementationRevision(),
            current.implementationChecksum(),
            current.dbtUniqueId(),
            artifacts
        );
    }

    private static DbtScopedProjectService.CandidateArtifactEntry cycleEntry(
        String modelSpecId,
        String referencedSelector
    ) {
        UUID id = UUID.fromString(modelSpecId);
        String selector = "model_" + modelSpecId.replace("-", "_");
        return new DbtScopedProjectService.CandidateArtifactEntry(
            id,
            1,
            "a".repeat(64),
            1,
            "b".repeat(64),
            "model.dts_test." + selector,
            List.of(
                artifact(
                    "models/dwd/" + selector + "/v1/i1/" + selector + ".sql",
                    "select * from {{ ref('" + referencedSelector + "') }}\n"
                )
            )
        );
    }

    private static DbtScopedProjectService.CandidateArtifact artifact(String path, String content) {
        return new DbtScopedProjectService.CandidateArtifact(path, sha256(content), content);
    }

    private static void assertFailureCode(Runnable invocation, String code) {
        assertThatThrownBy(invocation::run)
            .isInstanceOf(DbtScopedProjectService.ScopedProjectException.class)
            .extracting(error -> ((DbtScopedProjectService.ScopedProjectException) error).code())
            .isEqualTo(code);
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
}
