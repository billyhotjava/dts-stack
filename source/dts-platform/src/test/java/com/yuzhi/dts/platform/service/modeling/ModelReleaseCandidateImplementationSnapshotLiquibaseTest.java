package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateImplementationSnapshotLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260811_03_model_release_candidate_implementation_snapshot.xml";

    @Test
    void candidatePinsTheImmutableImplementationRevisionInsteadOfTheMutableHead() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("constraintName=\"fk_model_release_candidate_entry_implementation\"")
            .contains("constraintName=\"uk_modeling_implementation_revision_candidate_pin\"")
            .contains("columnNames=\"tenant_id, implementation_id, revision, content_checksum, ownership\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_implementation_identity\"")
            .contains("baseColumnNames=\"tenant_id, implementation_id\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_implementation_revision\"")
            .contains(
                "baseColumnNames=\"tenant_id, implementation_id, implementation_revision, implementation_checksum, implementation_mode\""
            )
            .contains("referencedTableName=\"modeling_model_implementation_revision\"")
            .contains(
                "referencedColumnNames=\"tenant_id, implementation_id, revision, content_checksum, ownership\""
            )
            .contains("onDelete=\"RESTRICT\"");
    }

    @Test
    void migrationFailsClosedWhenHistoricalCandidatePinsAreInvalid() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("MODEL_RELEASE_CANDIDATE_IMPLEMENTATION_SNAPSHOT_INVALID")
            .contains("implementation_revision")
            .contains("implementation_checksum");
    }

    @Test
    void masterIncludesTheConstraintReplacementAfterCandidateBuildMigrations() throws Exception {
        String master = read("/config/liquibase/master.xml");

        assertThat(master)
            .containsSubsequence(
                "config/liquibase/changelog/20260727_10_model_materialization_build.xml",
                "config/liquibase/changelog/20260811_03_model_release_candidate_implementation_snapshot.xml"
            );
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
