package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.FrozenDbtBundleArtifactRecovery.FrozenBundleEvidence;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.BuildArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtProjectBundleManifest.BundleFile;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedNode;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.ValidatedProject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FrozenDbtBundleArtifactRecoveryTest {

    private static final UUID MODEL_ID = UUID.fromString("e6315152-875b-47db-90c4-db03a9a59269");
    private static final String PROJECT_KEY = "dts";
    private static final String TARGET_UNIQUE_ID = "model.dts.project_task_snapshot";
    private static final String TARGET_PATH = "models/dwd/project_task_snapshot.sql";
    private static final String STAGE_UNIQUE_ID = "model.dts.stg_project_task_snapshot";
    private static final String STAGE_PATH = "models/dwd/stg_project_task_snapshot.sql";
    private static final String PROJECT_CHECKSUM = "c".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void restoresOnlyPinnedTransitiveModelDependenciesForAnExistingCandidate() throws Exception {
        String targetSql = "select * from {{ ref('stg_project_task_snapshot') }}\n";
        String stageSql = "select * from {{ source('public', 'project_task_clean') }}\n";
        DbtProjectBundleManifest.BundleSnapshot bundle = bundle(targetSql, stageSql);
        BuildArtifact target = artifact(TARGET_PATH, targetSql);

        List<BuildArtifact> recovered = FrozenDbtBundleArtifactRecovery.recover(
            objectMapper,
            MODEL_ID,
            TARGET_UNIQUE_ID,
            inputs(bundle),
            new FrozenBundleEvidence(
                PROJECT_KEY,
                TARGET_UNIQUE_ID,
                ".dts/dbt-project-bundle.json",
                bundle.bundleChecksum(),
                bundle.manifest()
            ),
            List.of(target)
        );

        assertThat(recovered)
            .extracting(BuildArtifact::path)
            .containsExactly(TARGET_PATH, STAGE_PATH)
            .doesNotContain("models/dwd/unrelated.sql");
        assertThat(recovered.get(1).content()).isEqualTo(stageSql);
    }

    @Test
    void failsClosedWhenPinnedBundleEvidenceIsMissing() throws Exception {
        DbtProjectBundleManifest.BundleSnapshot bundle = bundle("select 1\n", "select 1\n");

        assertThatThrownBy(() ->
            FrozenDbtBundleArtifactRecovery.recover(
                objectMapper,
                MODEL_ID,
                TARGET_UNIQUE_ID,
                inputs(bundle),
                null,
                List.of(artifact(TARGET_PATH, "select 1\n"))
            )
        )
            .isInstanceOfSatisfying(ModelReleaseCandidateException.class, exception ->
                assertThat(exception.code()).isEqualTo("MATERIALIZATION_ARTIFACT_MISSING")
            );
    }

    @Test
    void leavesLegacyCandidatesWithoutAFrozenBundleUnchanged() {
        BuildArtifact target = artifact(TARGET_PATH, "select 1\n");

        assertThat(
            FrozenDbtBundleArtifactRecovery.recover(
                objectMapper,
                MODEL_ID,
                TARGET_UNIQUE_ID,
                "[{\"sourceBindingId\":\"legacy\"}]",
                null,
                List.of(target)
            )
        ).containsExactly(target);
    }

    private DbtProjectBundleManifest.BundleSnapshot bundle(String targetSql, String stageSql) {
        List<BundleFile> files = List.of(
            file("dbt_project.yml", "name: dts\nmodel-paths: [models]\n"),
            file(TARGET_PATH, targetSql),
            file(STAGE_PATH, stageSql),
            file("models/dwd/unrelated.sql", "select 2\n")
        );
        ValidatedNode target = node(
            TARGET_UNIQUE_ID,
            TARGET_PATH,
            targetSql,
            List.of("source.dts.public.project_task_clean")
        );
        ValidatedNode unrelated = node("model.dts.unrelated", "models/dwd/unrelated.sql", "select 2\n", List.of());
        return DbtProjectBundleManifest.freeze(
            objectMapper,
            files,
            new ValidatedProject("a".repeat(64), PROJECT_CHECKSUM, PROJECT_KEY, List.of(target, unrelated), List.of())
        );
    }

    private String inputs(DbtProjectBundleManifest.BundleSnapshot bundle) throws Exception {
        return objectMapper.writeValueAsString(
            List.of(
                Map.of(
                    "config",
                    Map.of(
                        "projectKey",
                        PROJECT_KEY,
                        "dbtUniqueId",
                        TARGET_UNIQUE_ID,
                        "projectChecksum",
                        bundle.projectChecksum(),
                        "bundleChecksum",
                        bundle.bundleChecksum()
                    )
                )
            )
        );
    }

    private static BuildArtifact artifact(String path, String content) {
        return new BuildArtifact(path, ModelPackageChecksum.sha256Text(content), content);
    }

    private static BundleFile file(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new BundleFile(path, content, ModelPackageChecksum.sha256(bytes), bytes.length);
    }

    private static ValidatedNode node(String uniqueId, String path, String sql, List<String> dependencies) {
        String schema = "{\"columns\":[],\"tests\":[]}";
        return new ValidatedNode(
            uniqueId,
            uniqueId.substring(uniqueId.lastIndexOf('.') + 1),
            path,
            "table",
            "MODEL",
            sql,
            ModelPackageChecksum.sha256Text(sql),
            schema,
            ModelPackageChecksum.sha256Text(schema),
            dependencies,
            List.of()
        );
    }
}
