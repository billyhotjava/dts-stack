package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.topic.TopicBindingRuntimeService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbtReleaseGateServiceTest {

    @Test
    void shouldNotBlockWhenGitMetadataMissingAndGateDoesNotRequireGit() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(successfulSummary("dbt build --select model:test_model"));
        when(topicBindingRuntimeService.diagnose("model:test_model")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("model:test_model", List.of(), List.of(), List.of())
        );
        when(runResultService.hasRecentCompatibleBuildEvidence("model:test_model", 20)).thenReturn(false);

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, true);

        assertThat(result.blocking()).isFalse();
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldBlockWhenGitMetadataMissingAndGateRequiresGit() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(successfulSummary("dbt build --select model:test_model"));
        when(topicBindingRuntimeService.diagnose("model:test_model")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("model:test_model", List.of(), List.of(), List.of())
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, true);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, true);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("缺少 Git 分支信息（gitRef）", "缺少 Commit SHA（commitSha）");
    }

    @Test
    void shouldStillBlockOnFailedBuildWithoutGitRequirements() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-1",
                Instant.now().toString(),
                "dbt run --select model:test_model",
                "FAILED",
                1,
                0,
                1,
                0,
                List.of(),
                List.of()
            )
        );
        when(topicBindingRuntimeService.diagnose("model:test_model")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("model:test_model", List.of(), List.of(), List.of())
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, false);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains(
            "最近一次记录命令不是 compile/test/build，且最近 20 条中未发现匹配 selector 的有效 CI 校验，请先补齐 CI 校验"
        );
    }

    @Test
    void shouldBlockWhenRequiredTopicBindingMissing() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(successfulSummary("dbt build --select tag:project-management"));
        when(topicBindingRuntimeService.diagnose("tag:project-management")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics(
                "tag:project-management",
                List.of("project-management"),
                List.of(),
                List.of("project-management.project_subject_domain")
            )
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("tag:project-management", null, null, false);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("缺少专题绑定：project-management.project_subject_domain");
    }

    @Test
    void shouldWarnInsteadOfBlockWhenLatestFailedTestOnlyMissesUnbuiltRelations() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-2",
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
        when(topicBindingRuntimeService.diagnose("tag:project-management")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("tag:project-management", List.of(), List.of(), List.of())
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("tag:project-management", null, null, false);

        assertThat(result.blocking()).isFalse();
        assertThat(result.warning()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).contains("最近一次 dbt test 失败是因为目标关系尚未生成，首次上线可继续执行 dbt build");
    }

    @Test
    void shouldStillBlockWhenLatestFailedBuildMissesRelations() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-3",
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
        when(topicBindingRuntimeService.diagnose("tag:project-management")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("tag:project-management", List.of(), List.of(), List.of())
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("tag:project-management", null, null, false);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("最近一次构建状态为 FAILED，不允许发布");
    }

    @Test
    void shouldWarnWhenLatestCommandIsNotBuildLikeButRecentMatchingEvidenceExists() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                "/tmp/dbt",
                "/tmp/dbt/target/run_results.json",
                "/tmp/dbt/target/manifest.json",
                "inv-4",
                Instant.now().toString(),
                "dbt run --select tag:project-management",
                "FAILED",
                1,
                0,
                1,
                0,
                List.of(),
                List.of()
            )
        );
        when(runResultService.hasRecentCompatibleBuildEvidence("tag:project-management", 20)).thenReturn(true);
        when(topicBindingRuntimeService.diagnose("tag:project-management")).thenReturn(
            new TopicBindingRuntimeService.BindingDiagnostics("tag:project-management", List.of(), List.of(), List.of())
        );

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, topicBindingRuntimeService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("tag:project-management", null, null, false);

        assertThat(result.blocking()).isFalse();
        assertThat(result.warning()).isTrue();
        assertThat(result.warnings()).contains("最近一次记录命令不是 compile/test/build，但最近 20 条中已发现匹配 selector 的有效 CI 校验");
    }

    private DbtRunResultService.DbtRunSummary successfulSummary(String command) {
        return new DbtRunResultService.DbtRunSummary(
            true,
            "/tmp/dbt",
            "/tmp/dbt/target/run_results.json",
            "/tmp/dbt/target/manifest.json",
            "inv-1",
            Instant.now().toString(),
            command,
            "SUCCESS",
            1,
            1,
            0,
            0,
            List.of(),
            List.of()
        );
    }
}
