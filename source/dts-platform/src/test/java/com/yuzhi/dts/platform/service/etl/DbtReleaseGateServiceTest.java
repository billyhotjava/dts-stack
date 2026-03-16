package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbtReleaseGateServiceTest {

    @Test
    void shouldNotBlockWhenGitMetadataMissingAndGateDoesNotRequireGit() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(successfulSummary("dbt build --select model:test_model"));

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, true);

        assertThat(result.blocking()).isFalse();
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldBlockWhenGitMetadataMissingAndGateRequiresGit() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
        when(runResultService.loadLatestBuildSummary(20)).thenReturn(successfulSummary("dbt build --select model:test_model"));

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, true);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, true);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("缺少 Git 分支信息（gitRef）", "缺少 Commit SHA（commitSha）");
    }

    @Test
    void shouldStillBlockOnFailedBuildWithoutGitRequirements() {
        DbtRunResultService runResultService = mock(DbtRunResultService.class);
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

        DbtReleaseGateService service = new DbtReleaseGateService(runResultService, false);

        DbtReleaseGateService.DbtReleaseGateResult result = service.evaluate("model:test_model", null, null, false);

        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains(
            "最近一次构建状态为 FAILED，不允许发布",
            "最近一次构建命令不是 compile/test/build，请先补齐 CI 校验"
        );
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
