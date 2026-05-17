package com.yuzhi.dts.metrics.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MetricsFrontendResourceContractTest {

    private static final Path APP_SCRIPT = Path.of(
        "src/main/resources/static/metrics/assets/metrics-app.js"
    );

    @Test
    void metricsPagesExposeRealApiActions() throws IOException {
        String script = Files.readString(APP_SCRIPT);

        assertThat(script)
            .contains(
                "\"/metrics/dictionary\"",
                "\"/metrics/semantic/subjects\"",
                "\"/metrics/semantic/objects\"",
                "\"/metrics/semantic/metrics\"",
                "\"/metrics/semantic/models\"",
                "\"/metrics/semantic/publish\"",
                "\"/metrics/semantic/runs\"",
                "\"/metrics/operations\""
            );
        assertThat(script)
            .contains(
                "refresh-dictionary-contract",
                "refresh-subject-contract",
                "preview-object-manifest",
                "preview-formula-artifacts",
                "generate-model-candidates",
                "dry-run-publish",
                "refresh-run-status"
            );
        assertThat(script)
            .contains(
                "\"/api/metrics/workspace/snapshot\"",
                "\"/api/metrics/capabilities\"",
                "\"/api/metrics/packs/preview-artifacts\"",
                "\"/api/metrics/packs/import\"",
                "\"/api/metrics/packs/publish-dry-run\""
            );
        assertThat(script).contains("loadWorkspaceSnapshot", "applyWorkspaceSnapshot");
    }

    @Test
    void modelGenerationAndPublishActionsAreNotDisabledPlaceholders() throws IOException {
        String script = Files.readString(APP_SCRIPT);

        assertThat(script).doesNotContain("disabled>生成候选物", "disabled>提交平台门禁");
        assertThat(script).contains("id=\"generate-model-candidates\"", "id=\"dry-run-publish\"");
    }
}
