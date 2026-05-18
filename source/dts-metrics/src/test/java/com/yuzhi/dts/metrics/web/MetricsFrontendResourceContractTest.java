package com.yuzhi.dts.metrics.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MetricsFrontendResourceContractTest {

    private static final Path WEBAPP_ROOT = Path.of("../dts-metrics-webapp");
    private static final Path STATIC_ROOT = Path.of("src/main/resources/static/metrics");

    @Test
    void metricsFrontendIsAReactViteApplication() throws IOException {
        Path packageJson = WEBAPP_ROOT.resolve("package.json");
        Path viteConfig = WEBAPP_ROOT.resolve("vite.config.ts");
        Path main = WEBAPP_ROOT.resolve("src/main.tsx");

        assertThat(packageJson).exists();
        assertThat(viteConfig).exists();
        assertThat(main).exists();

        assertThat(Files.readString(packageJson))
            .contains("\"name\": \"dts-metrics-webapp\"", "\"react\"", "\"react-dom\"", "\"build\"");
        assertThat(Files.readString(viteConfig))
            .contains("base: \"/metrics/\"", "outDir: \"../dts-metrics/src/main/resources/static/metrics\"");
    }

    @Test
    void metricsReactAppKeepsTheRequiredPagesAndApiActions() throws IOException {
        String appSource = Files.readString(WEBAPP_ROOT.resolve("src/App.tsx"));

        assertThat(appSource)
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
        assertThat(appSource)
            .contains(
                "\"/api/metrics/workspace/snapshot\"",
                "\"/api/metrics/capabilities\"",
                "\"/api/metrics/packs/preview-artifacts\"",
                "\"/api/metrics/packs/import\"",
                "\"/api/metrics/packs/publish-dry-run\"",
                "\"/api/metrics/migration/semantic-dry-run\""
            );
    }

    @Test
    void metricsStaticBundleIsGeneratedFromReactBuild() throws IOException {
        Path indexHtml = STATIC_ROOT.resolve("index.html");
        Path legacyScript = STATIC_ROOT.resolve("assets/metrics-app.js");

        assertThat(legacyScript).doesNotExist();
        assertThat(indexHtml).exists();
        assertThat(Files.readString(indexHtml))
            .contains("id=\"root\"", "type=\"module\"", "/metrics/assets/");
    }
}
