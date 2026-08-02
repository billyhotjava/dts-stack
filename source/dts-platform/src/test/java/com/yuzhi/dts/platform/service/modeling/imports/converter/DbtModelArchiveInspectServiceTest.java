package com.yuzhi.dts.platform.service.modeling.imports.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void convertsWrappedDbtSourceProjectWithoutManifestIntoStableGraph() throws Exception {
        var result = service.inspect(
            archive(
                Map.of(
                    "dbt_model/dbt_project.yml",
                    """
                    name: "pm_analytics_v3"
                    version: "2.1.0"
                    model-paths: ["models"]
                    """,
                    "dbt_model/models.tsv",
                    """
                    name\tlayer\tsql_path\tmaterialized\ttags\tenabled\tdescription
                    stg_budget\tSTG\tmodels/stg/stg_budget.sql\tview\tdomain:project-management,stg\ttrue\t预算接入技术层
                    budget_detail\tDWD\tmodels/dwd/budget_detail.sql\ttable\tdomain:project-management,biz,dwd\ttrue\t预算明细
                    budget_summary\tDWS\tmodels/dws/budget_summary.sql\ttable\tdomain:project-management,biz,dws\ttrue\t预算汇总
                    """,
                    "dbt_model/models/stg/stg_budget.sql",
                    "select * from {{ source('pm_ods_v2', 'budget_v2') }}",
                    "dbt_model/models/dwd/budget_detail.sql",
                    "select * from {{ ref('stg_budget') }}",
                    "dbt_model/models/dws/budget_summary.sql",
                    "select project_no, sum(amount) as amount from {{ ref('budget_detail') }} group by project_no"
                )
            )
        );

        assertThat(result.dbt().projectName()).isEqualTo("pm_analytics_v3");
        assertThat(result.dbt().manifestVersion()).isEqualTo("source-project/v1");
        assertThat(result.dbt().adapterType()).isEqualTo("static-no-execution");
        assertThat(result.sources())
            .extracting(source -> source.dbtUniqueId())
            .containsExactly("source.pm_analytics_v3.pm_ods_v2.budget_v2");
        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .contains("model.pm_analytics_v3.stg_budget");
        assertThat(result.models())
            .extracting(model -> model.dbtUniqueId())
            .containsExactly("model.pm_analytics_v3.budget_detail", "model.pm_analytics_v3.budget_summary");
        assertThat(result.models().getFirst().dependencies()).containsExactly("model.pm_analytics_v3.stg_budget");
        assertThat(result.models().get(1).dependencies()).containsExactly("model.pm_analytics_v3.budget_detail");
        assertThat(result.models())
            .allSatisfy(model -> {
                assertThat(model.semantics()).isNotNull();
                assertThat(model.semantics().domainCode()).isEqualTo("project-management");
                assertThat(model.semantics().sourceRefs())
                    .extracting(source -> source.ref())
                    .contains("source.pm_analytics_v3.pm_ods_v2.budget_v2");
                assertThat(model.conversion().reasonCodes()).doesNotContain("LEGACY_MANIFEST_REQUIRED");
            });
        assertThat(result.issues())
            .extracting(issue -> issue.code())
            .contains("DBT_SOURCE_PROJECT_STATIC_ANALYSIS", "SOURCE_SEMANTICS_INCOMPLETE")
            .doesNotContain("LEGACY_MANIFEST_REQUIRED");
    }

    @Test
    void scansStandardDbtProjectWhenModelsTsvIsAbsent() throws Exception {
        var result = service.inspect(
            archive(
                Map.of(
                    "dbt_project.yml",
                    "name: inventory\nversion: 1.0.0\nmodel-paths: [models]\n",
                    "models/stg/stg_inventory.sql",
                    "{{ config(materialized='view', tags=['stg']) }} select * from {{ source('erp', 'inventory') }}",
                    "models/dwd/inventory_detail.sql",
                    "{{ config(materialized='table', tags=['dwd']) }} select * from {{ ref('stg_inventory') }}"
                )
            )
        );

        assertThat(result.dbt().projectName()).isEqualTo("inventory");
        assertThat(result.models())
            .extracting(model -> model.dbtUniqueId())
            .containsExactly("model.inventory.inventory_detail");
        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .contains("model.inventory.stg_inventory");
        assertThat(result.sources())
            .extracting(source -> source.dbtUniqueId())
            .containsExactly("source.inventory.erp.inventory");
    }

    @Test
    void keepsUnresolvedAndDynamicRefsOutOfStructuralDependencies() throws Exception {
        var result = service.inspect(
            archive(
                Map.of(
                    "dbt_project.yml",
                    "name: inventory\nmodel-paths: [models]\n",
                    "models/detail.sql",
                    """
                    {{ config(tags=['dwd']) }}
                    select * from {{ ref('missing_model') }}
                    union all select * from {{ ref(var('dynamic_model')) }}
                    union all select * from {{ ref('detail' ~ var('suffix')) }}
                    union all select * from {{ source('erp', 'inventory' ~ var('suffix')) }}
                    """
                )
            )
        );

        assertThat(result.models()).singleElement().satisfies(model -> {
            assertThat(model.dependencies()).isEmpty();
            assertThat(model.conversion().mode().name()).isEqualTo("BLOCKED");
            assertThat(model.conversion().reasonCodes())
                .containsExactlyInAnyOrder(
                    "SOURCE_SEMANTICS_INCOMPLETE",
                    "SOURCE_FIELDS_UNVERIFIED",
                    "SOURCE_PACKAGE_MISSING",
                    "SOURCE_DEPENDENCY_DYNAMIC"
                );
        });
        assertThat(result.sources()).isEmpty();
        assertThat(result.issues())
            .extracting(issue -> issue.code())
            .contains("DBT_SOURCE_PROJECT_REF_UNRESOLVED", "DBT_SOURCE_PROJECT_DYNAMIC_REFERENCE");
    }

    @Test
    void reconcilesTsvWithConfiguredPathsAndPreservesLiteralPackageRefsAndMacroDeclarations() throws Exception {
        var result = service.inspect(
            archive(
                Map.of(
                    "dbt_project.yml",
                    """
                    name: inventory
                    model-paths: [warehouse]
                    macro-paths:
                      - jinja_macros
                    """,
                    "models.tsv",
                    """
                    name\tlayer\tsql_path\tmaterialized\ttags\tenabled
                    base\tDWD\twarehouse/base.sql\ttable\tsales,dwd\ttrue
                    disabled_model\tDWD\twarehouse/disabled_model.sql\ttable\tdwd\tfalse
                    """,
                    "warehouse/base.sql",
                    "select 1 as id",
                    "warehouse/disabled_model.sql",
                    "{{ config(tags=['dwd']) }} select 0 as id",
                    "warehouse/consumer.sql",
                    "{{ config(tags=['dwd', 'domain:sales']) }} select * from {{ ref('inventory', 'base') }}",
                    "warehouse/transient.sql",
                    "{{ config(materialized='ephemeral', tags=['dwd']) }} select * from {{ ref('base') }}",
                    "jinja_macros/helpers.sql",
                    "{% macro first_helper() %}1{% endmacro %}\n{% macro second_helper() %}2{% endmacro %}"
                )
            )
        );

        assertThat(result.models())
            .extracting(model -> model.dbtUniqueId())
            .containsExactly("model.inventory.base", "model.inventory.consumer");
        assertThat(result.models().getFirst().semantics().domainCode()).isNull();
        assertThat(result.models().get(1).semantics().domainCode()).isEqualTo("sales");
        assertThat(result.models().get(1).dependencies()).containsExactly("model.inventory.base");
        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .contains(
                "macro.inventory.first_helper",
                "macro.inventory.second_helper",
                "model.inventory.transient"
            );
        assertThat(result.models())
            .allSatisfy(model -> {
                assertThat(model.sql().rawSql()).isNull();
                assertThat(model.sql().effectiveSql()).isNotBlank();
            });
    }

    @Test
    void rejectsMalformedUtf8InSourceProjectSql() throws Exception {
        var invalidArchive = archiveBytes(
            Map.of(
                "dbt_project.yml",
                "name: inventory\nmodel-paths: [models]\n".getBytes(StandardCharsets.UTF_8),
                "models/detail.sql",
                new byte[] { (byte) 0xc3, 0x28 }
            )
        );

        assertThatThrownBy(() -> service.inspect(invalidArchive))
            .isInstanceOf(DbtModelArchiveInspectService.ArchiveInspectionException.class)
            .extracting(exception -> ((DbtModelArchiveInspectService.ArchiveInspectionException) exception).code())
            .isEqualTo("MODEL_IMPORT_ARCHIVE_SOURCE_PROJECT_INVALID");
    }

    @Test
    void convertsTheUnmodifiedPjmAcceptanceZipWhenRepositoryFixtureIsAvailable() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.endsWith(Path.of("source", "dts-platform")) ? module.getParent().getParent() : module;
        Path fixture = repository.resolve("worklog/v2.2.3/s10/v4/pjm/pjm-dbt-model.zip");
        assumeTrue(Files.isRegularFile(fixture), "PJM repository fixture is not available in this build context");

        var result = service.inspect(
            new MockMultipartFile("archive", fixture.getFileName().toString(), "application/zip", Files.readAllBytes(fixture))
        );

        assertThat(result.dbt().projectName()).isEqualTo("pm_analytics_v3");
        assertThat(result.dbt().manifestVersion()).isEqualTo("source-project/v1");
        assertThat(result.models()).hasSize(38);
        assertThat(result.technicalNodes())
            .extracting(node -> node.dbtUniqueId())
            .contains(
                "model.pm_analytics_v3.stg_pm__budget_v2",
                "macro.pm_analytics_v3.ensure_date_helpers"
            );
        assertThat(result.sources())
            .extracting(source -> source.dbtUniqueId())
            .contains("source.pm_analytics_v3.pm_ods_v2.budget_v2");
        assertThat(result.models())
            .filteredOn(model -> model.dbtUniqueId().equals("model.pm_analytics_v3.biz_dwd_budget_v2"))
            .singleElement()
            .satisfies(model -> {
                assertThat(model.dependencies()).containsExactly("model.pm_analytics_v3.stg_pm__budget_v2");
                assertThat(model.semantics().sourceRefs())
                    .extracting(source -> source.ref())
                    .containsExactly("source.pm_analytics_v3.pm_ods_v2.budget_v2");
            });
        assertThat(result.issues())
            .extracting(issue -> issue.code())
            .contains("DBT_SOURCE_PROJECT_STATIC_ANALYSIS", "SOURCE_SEMANTICS_INCOMPLETE")
            .doesNotContain("LEGACY_MANIFEST_REQUIRED");
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
        Map<String, byte[]> encoded = new java.util.LinkedHashMap<>();
        entries.forEach((name, value) -> encoded.put(name, value.getBytes(StandardCharsets.UTF_8)));
        return archiveBytes(encoded);
    }

    private static MockMultipartFile archiveBytes(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
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
