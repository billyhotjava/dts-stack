package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RematerializationEvidenceAggregationContractTest {

    @Test
    void publicationEvidenceSelectsTheLatestExactSuccessfulRunPerCandidateEntry() throws IOException {
        String source = read("CandidatePublicationEvidenceRepository.java");

        assertThat(source.indexOf("join modeling_model_release_candidate_entry e"))
            .isLessThan(source.indexOf("select pr.id as pipeline_run_id"));
        assertThat(source)
            .contains("pr.release_candidate_entry_id = e.id")
            .contains("d.status = 'COMPLETED'")
            .contains("order by d.attempt desc")
            .contains("pr.model_revision = e.revision")
            .contains("pr.implementation_revision = e.implementation_revision");
    }

    @Test
    void workbenchEvidenceRanksAttemptsPerCandidateEntryInsteadOfPerCandidate() throws IOException {
        String source = read("ReleaseCandidateWorkbenchEvidenceRepository.java");
        int currentFrom = source.indexOf("public List<EntryEvidenceView> findCurrent(");
        int latestFrom = source.indexOf("public List<ModelMaterializationStatusView> findLatest(", currentFrom);
        String currentQuery = source.substring(currentFrom, latestFrom);

        assertThat(currentQuery)
            .contains("pr.release_candidate_entry_id = e.id")
            .contains("order by d.attempt desc")
            .doesNotContain("pr.pipeline_run_group_id = d.id\n                   and pr.run_purpose = 'RELEASE_BUILD'");
    }

    @Test
    void qualityEvidenceAggregatesExactBuiltRunsAcrossCompletedAttempts() throws IOException {
        String source = read("ModelPublicationQualityEvidenceRepository.java");

        assertThat(source)
            .contains("pipeline.release_candidate_entry_id = entry.id")
            .contains("run.dispatch_status = 'COMPLETED'")
            .contains("order by materialization.attempt desc")
            .contains("pipeline.model_revision = entry.revision")
            .contains("pipeline.implementation_revision = entry.implementation_revision");
    }

    @Test
    void retryPreservesThePreviousPartialAttemptScope() throws IOException {
        String source = read("ModelMaterializationBuildRepository.java");
        int from = source.indexOf("private QueuedBuildGroup createSubsequentQueuedBuild(");
        int to = source.indexOf("@Override", from);
        String retry = source.substring(from, to);

        assertThat(retry)
            .contains("immutableScope.containsAll(retryScope)")
            .doesNotContain("entries.size() != candidate.entries().size()");
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling", fileName)
        );
    }
}
