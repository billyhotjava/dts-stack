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
    private static final Path DTO_ROOT = Path.of("src/main/java/com/yuzhi/dts/metrics/service/dto");
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
        String appSource = Files.readString(WEBAPP_ROOT.resolve("src/app/MetricsShell.tsx"));

        assertThat(appSource)
            .contains(
                "\"/metrics/f1-architecture\"",
                "\"/metrics/f2-api-contracts\"",
                "\"/metrics/f3-visual-workbench\"",
                "\"/metrics/f4-modeling-gateway\"",
                "\"/metrics/f5-security-it\""
            );
        assertThat(appSource)
            .contains(
                "\"/api/metrics/capabilities\"",
                "/api/metrics/visual-assets",
                "\"/api/metrics/graphs\"",
                "\"/api/metrics/graphs/draft/preflight\"",
                "/api/metrics/models/",
                "\"artifacts\"",
                "\"validate\"",
                "\"submit-review\"",
                "\"rollback\"",
                "/versions",
                "\"/api/metrics/packs/preview-artifacts\"",
                "\"/api/metrics/packs/import\"",
                "\"/api/metrics/packs/publish-dry-run\"",
                "\"/api/metrics/migration/semantic-dry-run\""
            );
        assertThat(appSource).doesNotContain("\"/api/metrics/workspace/snapshot\"");
    }

    @Test
    void metricsLifecycleStatusAndErrorContractsAreCentralized() throws IOException {
        String semanticTypes = Files.readString(WEBAPP_ROOT.resolve("src/features/semantic/semanticTypes.ts"));
        String lifecycleStatus = Files.readString(DTO_ROOT.resolve("MetricLifecycleStatus.java"));
        String errorCodes = Files.readString(DTO_ROOT.resolve("MetricContractErrorCode.java"));

        assertThat(semanticTypes).contains("export type MetricLifecycleStatus", "export type MetricContractErrorCode");
        assertThat(lifecycleStatus).contains("ARTIFACT_GENERATED", "DBT_VALIDATED", "PUBLISHED", "ROLLED_BACK");
        assertThat(errorCodes)
            .contains(
                "GRAPH_VALIDATION_FAILED",
                "STANDARD_CODE_REQUIRED",
                "DBT_VALIDATION_FAILED",
                "PLATFORM_CONTRACT_UNAVAILABLE",
                "ROLLBACK_TARGET_REQUIRED"
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
