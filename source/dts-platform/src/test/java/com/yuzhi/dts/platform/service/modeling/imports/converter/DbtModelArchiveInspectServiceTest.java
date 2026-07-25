package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DbtModelArchiveInspectServiceTest {

    private final DbtModelArchiveInspectService service = new DbtModelArchiveInspectService(new ObjectMapper(), new SafeZipExtractor());

    @Test
    void convertsArtifactsAtArchiveRoot() throws Exception {
        var result = service.inspect(archive(Map.of("manifest.json", manifest())));

        assertThat(result.dbt().projectName()).isEqualTo("pjm");
        assertThat(result.models()).extracting(model -> model.dbtUniqueId()).containsExactly("model.pjm.budget");
        assertThat(result.packageId()).startsWith("pjm-");
    }

    @Test
    void convertsArtifactsInASingleProjectWrapper() throws Exception {
        var result = service.inspect(archive(Map.of("existing-dbt/target/manifest.json", manifest())));

        assertThat(result.dbt().projectName()).isEqualTo("pjm");
        assertThat(result.models()).hasSize(1);
    }

    @Test
    void prefersTargetManifestWhenOneProjectContainsBothArtifactLocations() throws Exception {
        var result = service.inspect(
            archive(Map.of("manifest.json", manifest(), "target/manifest.json", manifest().replace("\"pjm\"", "\"target_pjm\"")))
        );

        assertThat(result.dbt().projectName()).isEqualTo("target_pjm");
        assertThat(result.packageId()).startsWith("target-pjm-");
    }

    @Test
    void convertsLegacyModelsTsvIntoBlockedPreviewCandidates() throws Exception {
        var result = service.inspect(
            archive(Map.of("models.tsv", "name\tsql_path\tlayer\nbudget\tmodels/budget.sql\tDWD\n", "models/budget.sql", "select 1"))
        );

        assertThat(result.dbt().manifestVersion()).isEqualTo("legacy-tsv/v1");
        assertThat(result.models()).hasSize(1);
        assertThat(result.models().getFirst().conversion().mode().name()).isEqualTo("BLOCKED");
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("LEGACY_MANIFEST_REQUIRED");
    }

    @Test
    void rejectsArchivesWithoutManifestOrLegacyInventory() throws Exception {
        assertCode(Map.of("README.md", "no dbt artifacts"));
    }

    private void assertCode(Map<String, String> files) throws Exception {
        assertThatThrownBy(() -> service.inspect(archive(files)))
            .isInstanceOf(DbtModelArchiveInspectService.ArchiveInspectionException.class)
            .extracting(exception -> ((DbtModelArchiveInspectService.ArchiveInspectionException) exception).code())
            .isEqualTo("MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING");
    }

    private static MockMultipartFile archive(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return new MockMultipartFile("archive", "existing-dbt.zip", "application/zip", bytes.toByteArray());
    }

    private static String manifest() {
        return """
            {
              "metadata": {
                "project_name": "pjm",
                "dbt_version": "1.8.0",
                "dbt_schema_version": "https://schemas.getdbt.com/dbt/manifest/v12.json"
              },
              "nodes": {
                "model.pjm.budget": {
                  "unique_id": "model.pjm.budget",
                  "name": "budget",
                  "resource_type": "model",
                  "original_file_path": "models/budget.sql",
                  "raw_code": "select id from source_budget",
                  "config": {"materialized": "table"},
                  "depends_on": {"nodes": [], "macros": []},
                  "columns": {"id": {"name": "id"}}
                }
              },
              "sources": {},
              "macros": {}
            }
            """;
    }
}
