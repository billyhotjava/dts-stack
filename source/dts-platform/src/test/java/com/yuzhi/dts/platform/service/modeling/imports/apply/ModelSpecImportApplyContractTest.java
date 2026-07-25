package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
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
