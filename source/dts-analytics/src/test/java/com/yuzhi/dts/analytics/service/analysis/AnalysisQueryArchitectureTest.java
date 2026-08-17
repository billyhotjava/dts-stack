package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisQueryArchitectureTest {

    @Test
    void productionRunNativeCallsAreLimitedToFacadeAndExactScreenWarmupS3Exception() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<Path> callers;
        try (var files = Files.walk(sourceRoot)) {
            callers = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().endsWith("DatasetQueryService.java"))
                .filter(path -> read(path).contains(".runNative("))
                .map(sourceRoot::relativize)
                .sorted()
                .toList();
        }

        assertThat(callers).containsExactly(
            Path.of("com/yuzhi/dts/analytics/service/QueryExecutionFacade.java"),
            Path.of("com/yuzhi/dts/analytics/service/ScreenWarmupService.java")
        );
        String warmup = read(sourceRoot.resolve("com/yuzhi/dts/analytics/service/ScreenWarmupService.java"));
        assertThat(warmup).contains("warmupForPublishedScreen");
        assertThat(occurrences(warmup, ".runNative(")).isEqualTo(1);
    }

    @Test
    void interactiveResourcesDoNotExecuteTheLowLevelFacadeDirectly() {
        Path resourceRoot = Path.of("src/main/java/com/yuzhi/dts/analytics/web/rest");
        for (String file : List.of(
            "SemanticResource.java",
            "CardResource.java",
            "DashboardResource.java",
            "DatasetResource.java",
            "PublicResource.java",
            "EmbedResource.java"
        )) {
            assertThat(read(resourceRoot.resolve(file)))
                .as(file)
                .doesNotContain("queryExecutionFacade.executeWithCompliance(")
                .doesNotContain("queryExecutionFacade.executeRaw(");
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
