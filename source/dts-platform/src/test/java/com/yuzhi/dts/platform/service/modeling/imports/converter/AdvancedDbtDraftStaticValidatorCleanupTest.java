package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancedDbtDraftStaticValidatorCleanupTest {

    @Test
    void failsClosedWhenThePrivatePlaintextWorkspaceCannotBeRemoved() throws Exception {
        Path root = Files.createTempDirectory("dbt-draft-cleanup-test-");
        AdvancedDbtDraftStaticValidator.WorkspaceFactory workspaces = () ->
            new AdvancedDbtDraftStaticValidator.Workspace() {
                @Override
                public Path root() {
                    return root;
                }

                @Override
                public void close() throws IOException {
                    throw new AdvancedDbtDraftStaticValidator.WorkspaceCleanupException("simulated cleanup failure");
                }
            };
        AdvancedDbtDraftStaticValidator validator = new AdvancedDbtDraftStaticValidator(workspaces, root.getParent());

        try {
            assertThatThrownBy(() ->
                validator.validate(
                    Map.of(
                        "dbt_project.yml",
                        "name: sprint83\nmodel-paths: [models]\n",
                        "models/orders.sql",
                        "select 1\n"
                    )
                )
            )
                .isInstanceOf(AdvancedDbtDraftStaticValidator.StaticValidationException.class)
                .extracting(error -> ((AdvancedDbtDraftStaticValidator.StaticValidationException) error).code())
                .isEqualTo("DBT_DRAFT_TEMP_CLEANUP_FAILED");
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void cleanupFailureRemainsAuthoritativeWhenStaticParsingAlsoFails() throws Exception {
        Path root = Files.createTempDirectory("dbt-draft-double-failure-");
        AdvancedDbtDraftStaticValidator.WorkspaceFactory workspaces = () ->
            new AdvancedDbtDraftStaticValidator.Workspace() {
                @Override
                public Path root() {
                    return root;
                }

                @Override
                public void close() throws IOException {
                    throw new AdvancedDbtDraftStaticValidator.WorkspaceCleanupException("simulated cleanup failure");
                }
            };
        AdvancedDbtDraftStaticValidator validator = new AdvancedDbtDraftStaticValidator(workspaces, root.getParent());

        try {
            var failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> validator.validate(Map.of("models/orders.sql", "select 1\n")),
                AdvancedDbtDraftStaticValidator.StaticValidationException.class
            );

            assertThat(failure.code()).isEqualTo("DBT_DRAFT_TEMP_CLEANUP_FAILED");
            assertThat(failure.getCause()).isInstanceOf(AdvancedDbtDraftStaticValidator.WorkspaceCleanupException.class);
            assertThat(failure.getCause().getSuppressed()).isNotEmpty();
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {}
                });
            }
        }
    }
}
