package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScreenPortalCatalogSourceContractTest {

    @Test
    void portalDirectoryUsesPublishedOnlyParameterAndBatchVersionHydration() throws Exception {
        String resource = Files.readString(Path.of("src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java"));
        String repository = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenVersionRepository.java")
        );

        assertThat(resource).contains("publishedOnly");
        assertThat(resource).contains("findAllByScreenIdInAndCurrentPublishedTrue");
        assertThat(repository).contains("findAllByScreenIdInAndCurrentPublishedTrue");

        int listStart = resource.indexOf("public ResponseEntity<?> list(");
        int nextEndpoint = resource.indexOf("@PostMapping", listStart);
        String listMethod = resource.substring(listStart, nextEndpoint);
        assertThat(listMethod).doesNotContain("findFirstByScreenIdAndCurrentPublishedTrue");
    }
}
