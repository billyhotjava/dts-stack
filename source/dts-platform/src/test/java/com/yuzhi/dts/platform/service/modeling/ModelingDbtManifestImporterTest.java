package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelingDbtManifestImporterTest {

    @Test
    void importsModelNodeWithColumnsDependenciesAndDeterministicChecksum() {
        ModelingDbtManifestImporter.ImportSummary summary =
            ModelingDbtManifestImporter.importModel(request(manifestNode("model.pjm.project_progress", "project_progress", "select project_no from source")));

        assertThat(summary.models()).hasSize(1);
        ModelingDbtManifestImporter.ImportedModel model = summary.models().getFirst();
        assertThat(model.uniqueId()).isEqualTo("model.pjm.project_progress");
        assertThat(model.fields()).containsExactly("project_no", "plan_month");
        assertThat(model.dependencies()).containsExactly("source.pjm.ods_project_node");
        assertThat(model.contentChecksum()).hasSize(64);
        assertThat(model.idempotencyKey()).isEqualTo("idem-pjm-1");
    }

    @Test
    void missingModelNodeReturnsStableDbtModelNotFoundError() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("nodes", Map.of("model.pjm.other", manifestNode("model.pjm.other", "other", "select 1")));

        assertThatThrownBy(() -> ModelingDbtManifestImporter.importModel(request(manifest, "model.pjm.project_progress")))
            .isInstanceOf(ModelingDbtManifestImporter.ImportException.class)
            .satisfies(error -> assertThat(((ModelingDbtManifestImporter.ImportException) error).code())
                .isEqualTo(DbtModelingContract.ErrorCode.DBT_MODEL_NOT_FOUND));
    }

    @Test
    void unreadableSqlIsRejectedWithoutCreatingAnArtifact() {
        assertThatThrownBy(() -> ModelingDbtManifestImporter.importModel(new DbtModelingContract.ManifestImportRequest(
            "pjm-analytics",
            "1.7.4",
            "model.pjm.project_progress",
            manifestNode("model.pjm.project_progress", "project_progress", ""),
            "",
            "idem-pjm-1",
            "30000000-0000-0000-0000-000000000001",
            1,
            "a".repeat(64),
            2,
            "b".repeat(64)
        )))
            .isInstanceOf(ModelingDbtManifestImporter.ImportException.class)
            .satisfies(error -> assertThat(((ModelingDbtManifestImporter.ImportException) error).code())
                .isEqualTo(DbtModelingContract.ErrorCode.DBT_ARTIFACT_UNREADABLE));
    }

    private static DbtModelingContract.ManifestImportRequest request(Map<String, Object> manifest) {
        return request(manifest, "model.pjm.project_progress");
    }

    private static DbtModelingContract.ManifestImportRequest request(Map<String, Object> manifest, String modelUniqueId) {
        return new DbtModelingContract.ManifestImportRequest(
            "pjm-analytics",
            "1.7.4",
            modelUniqueId,
            manifest,
            "select project_no from source",
            "idem-pjm-1",
            "30000000-0000-0000-0000-000000000001",
            1,
            "a".repeat(64),
            2,
            "b".repeat(64)
        );
    }

    private static Map<String, Object> manifestNode(String uniqueId, String name, String sql) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name);
        node.put("resource_type", "model");
        node.put("raw_code", sql);
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("project_no", Map.of("name", "project_no", "data_type", "varchar"));
        columns.put("plan_month", Map.of("name", "plan_month", "data_type", "date"));
        node.put("columns", columns);
        node.put("depends_on", Map.of("nodes", List.of("source.pjm.ods_project_node")));
        return Map.of("nodes", Map.of(uniqueId, node));
    }
}
