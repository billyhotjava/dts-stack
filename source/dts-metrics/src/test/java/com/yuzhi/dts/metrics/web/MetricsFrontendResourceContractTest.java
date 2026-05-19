package com.yuzhi.dts.metrics.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MetricsFrontendResourceContractTest {

    private static final Path WEBAPP_ROOT = Path.of("../dts-metrics-webapp");
    private static final Path STATIC_ROOT = Path.of("src/main/resources/static/metrics");
    private static final Path POM = Path.of("pom.xml");

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
            .contains("base: \"/metrics/\"", "outDir: \"dist/metrics\"");
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
    void metricsStaticBundleIsNotCommittedToJavaResources() throws IOException {
        assertThat(Files.readString(POM))
            .contains("<directory>${project.build.directory}/generated-resources</directory>");

        if (!Files.exists(STATIC_ROOT)) {
            return;
        }
        try (Stream<Path> staticFiles = Files.walk(STATIC_ROOT)) {
            assertThat(staticFiles.filter(path -> path.toString().endsWith(".js")).toList()).isEmpty();
        }
    }
}
