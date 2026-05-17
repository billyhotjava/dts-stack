package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricWorkspaceResourceTest {

    @Test
    void snapshotExposesWorkspaceDataForFrontendPages() {
        MetricWorkspaceResource resource = new MetricWorkspaceResource();

        Map<String, Object> snapshot = resource.snapshot();

        assertThat(snapshot).containsKeys(
            "metricAssets",
            "subjectMappings",
            "objectJoins",
            "formulaBlocks",
            "modelCandidates",
            "publishGates",
            "runRecords",
            "actions"
        );
        assertThat((List<?>) snapshot.get("metricAssets")).isNotEmpty();
        assertThat((List<?>) snapshot.get("subjectMappings")).isNotEmpty();
        assertThat((List<?>) snapshot.get("modelCandidates")).isNotEmpty();
        assertThat((List<?>) snapshot.get("runRecords")).isNotEmpty();
    }

    @Test
    void snapshotPublishesApiBindingsUsedByFrontendActions() {
        MetricWorkspaceResource resource = new MetricWorkspaceResource();

        Map<String, Object> snapshot = resource.snapshot();

        Map<?, ?> actions = (Map<?, ?>) snapshot.get("actions");
        assertThat(actions.get("capabilities")).isEqualTo("/api/metrics/capabilities");
        assertThat(actions.get("previewArtifacts")).isEqualTo("/api/metrics/packs/preview-artifacts");
        assertThat(actions.get("publishDryRun")).isEqualTo("/api/metrics/packs/publish-dry-run");
    }
}
