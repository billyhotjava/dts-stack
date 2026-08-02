package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyContractTest {

    @Test
    void freezesClientSelectionAndServerResolvedClosure() {
        List<String> selected = new ArrayList<>(List.of("model.project.summary"));
        List<String> closure = new ArrayList<>(List.of("model.project.fact", "model.project.summary"));

        BeginCommand command = command(selected, closure);
        selected.clear();
        closure.clear();

        assertThat(command.selectedUniqueIds()).containsExactly("model.project.summary");
        assertThat(command.selectedClosure()).containsExactly("model.project.fact", "model.project.summary");
        assertThatThrownBy(() -> command.selectedClosure().add("model.project.application"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSelectionThatIsNotContainedByFrozenClosure() {
        assertThatThrownBy(() ->
            command(
                List.of("model.project.summary"),
                List.of("model.project.fact")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("selectedClosure");
    }

    @Test
    void exposesPersistedFailureClassificationAndRetryFacts() {
        ApplyIssue issue = new ApplyIssue(
            "MODEL_IMPORT_CANDIDATE_FAILED",
            Severity.ERROR,
            "APPLY",
            "INTERNAL",
            true,
            "$.items[0]",
            "model.project.fact",
            null,
            "Candidate apply failed",
            "RETRY",
            "correlation-123"
        );
        CandidateResult result = new CandidateResult(
            UUID.randomUUID(),
            0,
            "model.project.fact",
            "candidate-key",
            "candidate-hash",
            ResultStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(issue),
            Instant.now()
        );

        assertThat(result.retryable()).isTrue();
        assertThat(result.issues()).singleElement().satisfies(stored -> {
            assertThat(stored.stage()).isEqualTo("APPLY");
            assertThat(stored.category()).isEqualTo("INTERNAL");
            assertThat(stored.retryable()).isTrue();
            assertThat(stored.correlationId()).isEqualTo("correlation-123");
            assertThat(stored.dependencyUniqueId()).isNull();
        });
    }

    @Test
    void rejectsFailureFactsOutsideTheFrozenPartialResultContract() {
        assertThatThrownBy(() -> issue("RECOVERY", "PERSISTENCE", "RETRY", "correlation-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("stage");
        assertThatThrownBy(() -> issue("RETRY", "LEASE", "RETRY", "correlation-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category");
        assertThatThrownBy(() -> issue("RETRY", "PERSISTENCE", "TRY_AGAIN", "correlation-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("recoveryAction");
        assertThatThrownBy(() -> issue("RETRY", "PERSISTENCE", "RETRY", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("correlationId");
    }

    private static ApplyIssue issue(String stage, String category, String recoveryAction, String correlationId) {
        return new ApplyIssue(
            "MODEL_IMPORT_APPLY_INTERRUPTED",
            Severity.ERROR,
            stage,
            category,
            true,
            "$.items[0]",
            "model.project.fact",
            null,
            "Apply worker lease expired",
            recoveryAction,
            correlationId
        );
    }

    private static BeginCommand command(List<String> selected, List<String> closure) {
        return new BeginCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "default",
            "preview-hash",
            selected,
            closure,
            "apply-key",
            "request-hash",
            "actor",
            Instant.parse("2026-07-25T00:00:00Z")
        );
    }
}
