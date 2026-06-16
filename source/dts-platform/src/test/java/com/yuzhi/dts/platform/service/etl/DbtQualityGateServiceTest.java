package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtQualityGateServiceTest {

    @Test
    void shouldWarnInsteadOfBlockWhenLatestFailedTestOnlyMissesUnbuiltRelations() {
        ModelingSqlModelRepository repository = mock(ModelingSqlModelRepository.class);
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        when(repository.findAll()).thenReturn(List.of());
        when(runResultService.loadLatestSummary(10)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-1",
                Instant.now().toString(),
                "dbt test --select tag:project-management",
                "FAILED",
                10,
                0,
                10,
                0,
                List.of(
                    new DbtRunResultService.DbtRunFailure(
                        "test.dts.not_null_biz_dwd_project_node_enriched_node_id",
                        "not_null_biz_dwd_project_node_enriched_node_id",
                        "test",
                        "models/project_cockpit_schema.yml",
                        "error",
                        "Database Error in test not_null_biz_dwd_project_node_enriched_node_id (models/project_cockpit_schema.yml)\n" +
                        "  relation \"public.biz_dwd_project_node_enriched\" does not exist",
                        0.1
                    )
                ),
                List.of()
            )
        );

        DbtQualityGateService service = newService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("tag:project-management");

        assertThat(result.blocking()).isFalse();
        assertThat(result.warning()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).contains("最近一次 dbt test 失败是因为目标关系尚未生成，首次上线可继续执行 dbt build");
    }

    @Test
    void shouldWarnInsteadOfBlockWhenLatestFailedBuildMissesRelations() {
        ModelingSqlModelRepository repository = mock(ModelingSqlModelRepository.class);
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        when(repository.findAll()).thenReturn(List.of());
        when(runResultService.loadLatestSummary(10)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-2",
                Instant.now().toString(),
                "dbt build --select tag:project-management",
                "FAILED",
                10,
                0,
                10,
                0,
                List.of(
                    new DbtRunResultService.DbtRunFailure(
                        "test.dts.not_null_biz_dwd_project_node_enriched_node_id",
                        "not_null_biz_dwd_project_node_enriched_node_id",
                        "test",
                        "models/project_cockpit_schema.yml",
                        "error",
                        "Database Error in test not_null_biz_dwd_project_node_enriched_node_id (models/project_cockpit_schema.yml)\n" +
                        "  relation \"public.biz_dwd_project_node_enriched\" does not exist",
                        0.1
                    )
                ),
                List.of()
            )
        );

        DbtQualityGateService service = newService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("tag:project-management");

        assertThat(result.blocking()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).contains("最近一次质量构建失败（dbt build --select tag:project-management），建议修复后再上线");
    }

    @Test
    void shouldWarnInsteadOfBlockWhenYamlQualityMetadataIsIncomplete(@TempDir Path tempDir) throws Exception {
        ModelingSqlModelRepository repository = mock(ModelingSqlModelRepository.class);
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtRunResultService runResultService = mock(DbtRunResultService.class);

        Path sqlPath = tempDir.resolve("models/stg/model/stg_pm__budget_v2.sql");
        Files.createDirectories(sqlPath.getParent());
        Files.writeString(sqlPath, "select 1 as budget_id\n");
        Files.writeString(
            tempDir.resolve("models/pm_stg_v2.yml"),
            """
            version: 2
            models:
              - name: stg_pm__budget_v2
                description: budget staging model
            """
        );

        ModelingSqlModel model = new ModelingSqlModel();
        model.setName("stg_pm__budget_v2");
        model.setModelPath("models/stg/model/stg_pm__budget_v2.sql");

        when(repository.findAll()).thenReturn(List.of(model));
        when(dbtConfigService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), tempDir.toString(), "dts", "dev", null, null, "public", java.util.Map.of()),
                DbtConfigService.DbtProfileStatus.skipped("test"),
                null,
                new DbtConfigService.DbtWorkspaceStatus(true, "ok", java.util.Map.of())
            )
        );
        when(runResultService.loadLatestSummary(10)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                tempDir.toString(),
                tempDir.resolve("target/run_results.json").toString(),
                tempDir.resolve("target/manifest.json").toString(),
                "inv-5",
                Instant.now().toString(),
                "dbt test --select model:stg_pm__budget_v2",
                "SUCCESS",
                1,
                1,
                0,
                0,
                List.of(),
                List.of()
            )
        );

        DbtQualityGateService service = newService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("model:stg_pm__budget_v2");

        assertThat(result.blocking()).isFalse();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).contains(
            "以下模型未发现 schema.yml 测试: stg_pm__budget_v2",
            "以下模型缺少类型元信息(expected_data_type): stg_pm__budget_v2",
            "以下模型缺少 owner 治理元信息: stg_pm__budget_v2",
            "以下模型缺少 classification 治理元信息: stg_pm__budget_v2"
        );
    }

    @Test
    void shouldAcceptCatalogMetadataWhenYamlOnlyContainsTests(@TempDir Path tempDir) throws Exception {
        ModelingSqlModelRepository repository = mock(ModelingSqlModelRepository.class);
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        CatalogTableSchemaRepository tableRepository = mock(CatalogTableSchemaRepository.class);
        CatalogColumnSchemaRepository columnRepository = mock(CatalogColumnSchemaRepository.class);

        Path sqlPath = tempDir.resolve("models/stg/model/stg_pm__budget_v2.sql");
        Files.createDirectories(sqlPath.getParent());
        Files.writeString(sqlPath, "select 1 as budget_id\n");
        Files.writeString(
            tempDir.resolve("models/pm_stg_v2.yml"),
            """
            version: 2
            models:
              - name: stg_pm__budget_v2
                columns:
                  - name: budget_id
                    tests:
                      - not_null
            """
        );

        ModelingSqlModel model = new ModelingSqlModel();
        model.setName("stg_pm__budget_v2");
        model.setModelPath("models/stg/model/stg_pm__budget_v2.sql");

        CatalogTableSchema table = new CatalogTableSchema();
        table.setName("stg_pm__budget_v2");
        table.setOwner("biadmin");
        table.setClassification("INTERNAL");
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setTable(table);
        column.setName("budget_id");
        column.setDataType("bigint");

        when(repository.findAll()).thenReturn(List.of(model));
        when(tableRepository.findAll()).thenReturn(List.of(table));
        when(columnRepository.findByTable(table)).thenReturn(List.of(column));
        when(dbtConfigService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), tempDir.toString(), "dts", "dev", null, null, "public", java.util.Map.of()),
                DbtConfigService.DbtProfileStatus.skipped("test"),
                null,
                new DbtConfigService.DbtWorkspaceStatus(true, "ok", java.util.Map.of())
            )
        );
        when(runResultService.loadLatestSummary(10)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                tempDir.toString(),
                tempDir.resolve("target/run_results.json").toString(),
                tempDir.resolve("target/manifest.json").toString(),
                "inv-4",
                Instant.now().toString(),
                "dbt test --select model:stg_pm__budget_v2",
                "SUCCESS",
                1,
                1,
                0,
                0,
                List.of(),
                List.of()
            )
        );

        DbtQualityGateService service = new DbtQualityGateService(
            repository,
            dbtConfigService,
            runResultService,
            tableRepository,
            columnRepository
        );

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("model:stg_pm__budget_v2");

        assertThat(result.blocking()).isFalse();
        assertThat(result.blockers()).noneMatch(item -> item.contains("expected_data_type"));
        assertThat(result.blockers()).noneMatch(item -> item.contains("owner 治理元信息"));
        assertThat(result.blockers()).noneMatch(item -> item.contains("classification 治理元信息"));
    }

    @Test
    void shouldDetectRootLevelSchemaYmlForImportedNestedModelPath(@TempDir Path tempDir) throws Exception {
        ModelingSqlModelRepository repository = mock(ModelingSqlModelRepository.class);
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtRunResultService runResultService = mock(DbtRunResultService.class);

        Path sqlPath = tempDir.resolve("models/dwd/project_management/biz_dwd_quality_issue.sql");
        Files.createDirectories(sqlPath.getParent());
        Files.writeString(sqlPath, "select 1 as issue_id\n");
        Files.writeString(
            tempDir.resolve("models/pm_schema.yml"),
            """
            version: 2
            models:
              - name: biz_dwd_quality_issue
                columns:
                  - name: issue_id
                    expected_data_type: bigint
                    tests:
                      - not_null
                meta:
                  owner: data-team
                  classification: INTERNAL
            """
        );

        ModelingSqlModel model = new ModelingSqlModel();
        model.setName("biz_dwd_quality_issue");
        model.setModelPath("models/dwd/project_management/biz_dwd_quality_issue.sql");

        when(repository.findAll()).thenReturn(List.of(model));
        when(dbtConfigService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), tempDir.toString(), "dts", "dev", null, null, "public", java.util.Map.of()),
                DbtConfigService.DbtProfileStatus.skipped("test"),
                null,
                new DbtConfigService.DbtWorkspaceStatus(true, "ok", java.util.Map.of())
            )
        );
        when(runResultService.loadLatestSummary(10)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                tempDir.toString(),
                tempDir.resolve("target/run_results.json").toString(),
                tempDir.resolve("target/manifest.json").toString(),
                "inv-3",
                Instant.now().toString(),
                "dbt test --select model:biz_dwd_quality_issue",
                "SUCCESS",
                1,
                1,
                0,
                0,
                List.of(),
                List.of()
            )
        );

        DbtQualityGateService service = newService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("model:biz_dwd_quality_issue");

        assertThat(result.warnings()).noneMatch(item -> item.contains("未发现测试模板"));
    }

    private DbtQualityGateService newService(
        ModelingSqlModelRepository repository,
        DbtConfigService dbtConfigService,
        DbtRunResultService runResultService
    ) {
        CatalogTableSchemaRepository tableRepository = mock(CatalogTableSchemaRepository.class);
        CatalogColumnSchemaRepository columnRepository = mock(CatalogColumnSchemaRepository.class);
        when(tableRepository.findAll()).thenReturn(List.of());
        return new DbtQualityGateService(
            repository,
            dbtConfigService,
            runResultService,
            tableRepository,
            columnRepository
        );
    }
}
