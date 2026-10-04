package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelMaterializationBuildLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260727_10_model_materialization_build.xml";

    @Test
    void expandsCandidateEntryAndPipelineRunForAtomicReleaseBuilds() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("name=\"origin\"")
            .contains("SINGLE_MODEL_INTENT")
            .contains("BATCH_WORKBENCH")
            .contains("name=\"execution_target_key\"")
            .contains("name=\"target_name\"")
            .contains("name=\"implementation_revision\"")
            .contains("name=\"implementation_checksum\"")
            .contains("name=\"artifact_bundle_checksum\"")
            .contains("name=\"dependency_snapshot_checksum\"")
            .contains("name=\"active_claim_key\"")
            .contains("name=\"release_candidate_id\"")
            .contains("name=\"release_candidate_entry_id\"")
            .contains("name=\"release_candidate_version\"")
            .contains("name=\"pipeline_run_group_id\"")
            .contains("name=\"run_purpose\"")
            .contains("RELEASE_BUILD")
            .contains("name=\"attempt\"")
            .contains("name=\"dbt_invocation_id\"");
    }

    @Test
    void enforcesActiveClaimAndCandidateRunIdentityInPostgresql() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("uk_model_release_candidate_entry_active_claim")
            .contains("WHERE active_claim_key IS NOT NULL")
            .contains("uk_pipeline_run_candidate_entry_attempt")
            .contains("idx_pipeline_run_candidate_group")
            .contains("fk_pipeline_run_release_candidate")
            .contains("fk_pipeline_run_release_candidate_entry")
            .contains("ck_model_release_candidate_entry_build_snapshot")
            .contains("ck_modeling_pipeline_run_candidate_build")
            .contains("onDelete=\"RESTRICT\"")
            .doesNotContain("onDelete=\"CASCADE\"");
    }

    @Test
    void registersAfterPermissionMigrationsAndHasDataSafeRollback() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String xml = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "20260727_08_iam_asset_action_policy.xml",
                "20260727_09_iam_asset_action_policy_request.xml",
                "20260727_10_model_materialization_build.xml"
            );
        assertThat(xml)
            .contains("ROLLBACK_BLOCKED_MODEL_MATERIALIZATION_BUILD_DATA_EXISTS")
            .contains("IN ACCESS EXCLUSIVE MODE")
            .contains("DROP INDEX IF EXISTS uk_model_release_candidate_entry_active_claim")
            .contains("dropForeignKeyConstraint")
            .contains("dropColumn");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
