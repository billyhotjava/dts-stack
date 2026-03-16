package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

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

        DbtQualityGateService service = new DbtQualityGateService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("tag:project-management");

        assertThat(result.blocking()).isFalse();
        assertThat(result.warning()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).contains("最近一次 dbt test 失败是因为目标关系尚未生成，首次上线可继续执行 dbt build");
    }

    @Test
    void shouldStillBlockWhenLatestFailedBuildMissesRelations() {
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

        DbtQualityGateService service = new DbtQualityGateService(repository, dbtConfigService, runResultService);

        DbtQualityGateService.DbtQualityGateResult result = service.evaluate("tag:project-management");

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("最近一次质量构建失败（dbt build --select tag:project-management），请先修复后再上线");
    }
}
