package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AnalysisExportSourceContractTest {

    @Test
    void analysisResourceExportsCsvAndXlsxThroughTheGovernedGateway() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuzhi/dts/analytics/web/rest/AnalysisResource.java"));

        assertThat(source)
            .contains("/{id}/query/csv", "/{id}/query/xlsx")
            .contains("QueryExportService")
            .contains("sealCardExport")
            .contains("queryGateway.preview")
            .contains("X-DTS-Classification", "X-DTS-Classification-Snapshot");
    }

    @Test
    void analysisDtoAdvertisesExportWithoutIntroducingANewPermissionVocabulary() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yuzhi/dts/analytics/service/analysis/AnalysisApplicationService.java"));

        assertThat(source).contains("\"export\"");
        assertThat(source).doesNotContain("downloadPermission", "analysisExportPermission");
    }
}
