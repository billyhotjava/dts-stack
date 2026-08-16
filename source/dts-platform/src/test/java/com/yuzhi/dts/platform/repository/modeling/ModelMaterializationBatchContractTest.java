package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModelMaterializationBatchContractTest {

    @Test
    void crossPlanDependencyUsesAnExactPublishedRevisionInsteadOfTheMutablePlanHead() throws IOException {
        String method = method(
            "ModelMaterializationBuildRepository.java",
            "private void validateInputs(",
            "private void persistEntrySnapshot("
        );

        assertThat(method)
            .contains("modeling_model_spec_revision revision")
            .contains("revision.revision = ?")
            .contains("revision.content_checksum = ?")
            .contains("modeling_model_lifecycle_event event")
            .contains("event.status = 'PUBLISHED'")
            .contains("event.details_json ->> 'executionTargetKey' = ?")
            .contains("upper(event.details_json ->> 'environment') = upper(?)")
            .doesNotContain("s.plan_id = ?");
    }

    @Test
    void partialBatchResultsRemainPerEntryAndRetryable() throws IOException {
        String runRepository = Files.readString(main("ModelMaterializationRunRepository.java"));
        String buildRepository = Files.readString(main("ModelMaterializationBuildRepository.java"));

        assertThat(runRepository)
            .contains("recordDbtResults(")
            .contains("SKIPPED_DEPENDENCY_FAILED")
            .contains("Per-model dbt result row count does not match candidate scope");
        assertThat(buildRepository)
            .contains("'DBT_SUCCEEDED',")
            .contains("'SKIPPED_DEPENDENCY_FAILED'");
    }

    @Test
    void historyReadScansAllImmutableDispatchAttemptsNewestFirst() throws IOException {
        String method = method(
            "ReleaseCandidateWorkbenchEvidenceRepository.java",
            "public List<MaterializationAttemptView> findHistory(",
            "private static EntryEvidenceView mapEvidence("
        );

        assertThat(method)
            .contains("from modeling_materialization_dispatch d")
            .contains("pr.pipeline_run_group_id = d.id")
            .contains("where d.tenant_id = ?")
            .contains("and d.candidate_id = ?")
            .contains("order by d.attempt desc");
    }

    @Test
    void latestStatusDoesNotLetAnAbandonedCandidateShadowDurableMaterializationEvidence() throws IOException {
        String method = method(
            "ReleaseCandidateWorkbenchEvidenceRepository.java",
            "public List<ModelMaterializationStatusView> findLatest(",
            "public List<MaterializationAttemptView> findHistory("
        );

        assertThat(method)
            .contains("case when c.status in ('CANCELLED', 'REJECTED') then 1 else 0 end")
            .contains("c.created_date desc, c.last_modified_date desc, c.id desc");
    }

    private static String method(String file, String start, String end) throws IOException {
        String source = Files.readString(main(file));
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to);
    }

    private static Path main(String file) {
        return Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling", file);
    }
}
