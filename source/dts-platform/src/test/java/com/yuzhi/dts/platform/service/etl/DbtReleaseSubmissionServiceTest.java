package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbtReleaseSubmissionServiceTest {

    @Test
    void submitShouldReturnBlockedWhenDagIsNotReady() {
        DbtQualityGateService qualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200)).thenReturn(java.util.Optional.of(Map.of("dags", List.of())));

        DbtReleaseSubmissionService service = newService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            externalRunLogService
        );

        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = service.submit(
            new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
                "tag:project-management",
                "dev",
                Map.of(),
                null,
                null,
                true,
                false
            ),
            "BIADMIN"
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).anyMatch(item -> item.contains("尚未在 Airflow 中注册"));
        verify(airflowClient, never()).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void submitShouldReturnWarningWithoutTriggerWhenWarningsNeedConfirmation() {
        DbtQualityGateService qualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(qualityGateService.evaluate("tag:project-management"))
            .thenReturn(new DbtQualityGateService.DbtQualityGateResult(
                "tag:project-management",
                List.of("a"),
                false,
                true,
                "SUCCESS",
                "dbt test --select tag:project-management",
                "2026-03-29T00:00:00Z",
                0,
                List.of(),
                List.of("缺少类型元信息")
            ));
        when(releaseGateService.evaluate("tag:project-management", null, null, true))
            .thenReturn(new DbtReleaseGateService.DbtReleaseGateResult(
                "tag:project-management",
                true,
                null,
                null,
                "WARN",
                false,
                true,
                List.of(),
                List.of("缺少 Git 分支信息"),
                null
            ));

        DbtReleaseSubmissionService service = newService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            externalRunLogService
        );

        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = service.submit(
            new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
                "tag:project-management",
                "dev",
                Map.of(),
                null,
                null,
                true,
                false
            ),
            "BIADMIN"
        );

        assertThat(result.status()).isEqualTo("WARNING");
        assertThat(result.warning()).isTrue();
        assertThat(result.warnings()).contains("缺少类型元信息", "缺少 Git 分支信息");
        verify(airflowClient, never()).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void submitShouldBlockQualityGateBlockers() {
        DbtQualityGateService qualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(qualityGateService.evaluate("tag:project-management"))
            .thenReturn(new DbtQualityGateService.DbtQualityGateResult(
                "tag:project-management",
                List.of("biz_dwd_quality_issue"),
                true,
                false,
                "FAILED",
                "dbt build --select tag:project-management",
                "2026-03-29T00:00:00Z",
                1,
                List.of("以下模型未发现测试模板: biz_dwd_quality_issue"),
                List.of()
            ));
        when(releaseGateService.evaluate("tag:project-management", null, null, true))
            .thenReturn(new DbtReleaseGateService.DbtReleaseGateResult(
                "tag:project-management",
                true,
                null,
                null,
                "PASS",
                false,
                false,
                List.of(),
                List.of(),
                null
            ));

        DbtReleaseSubmissionService service = newService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            externalRunLogService
        );

        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = service.submit(
            new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
                "tag:project-management",
                "dev",
                Map.of(),
                null,
                null,
                true,
                false
            ),
            "BIADMIN"
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("以下模型未发现测试模板: biz_dwd_quality_issue");
        verify(airflowClient, never()).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void submitShouldBlockReleaseGateBlockers() {
        DbtQualityGateService qualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(qualityGateService.evaluate("tag:project-management"))
            .thenReturn(new DbtQualityGateService.DbtQualityGateResult(
                "tag:project-management",
                List.of("a"),
                false,
                false,
                "SUCCESS",
                "dbt test --select tag:project-management",
                "2026-03-29T00:00:00Z",
                0,
                List.of(),
                List.of()
            ));
        when(releaseGateService.evaluate("tag:project-management", null, null, true))
            .thenReturn(new DbtReleaseGateService.DbtReleaseGateResult(
                "tag:project-management",
                true,
                null,
                null,
                "BLOCK",
                true,
                false,
                List.of("最近一次构建状态为 FAILED，不允许发布"),
                List.of(),
                new DbtReleaseGateService.BuildEvidence("inv-1", "dbt build --select tag:project-management", "FAILED", "2026-03-29T00:00:00Z", "/tmp/run_results.json")
            ));

        DbtReleaseSubmissionService service = newService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            externalRunLogService
        );

        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = service.submit(
            new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
                "tag:project-management",
                "dev",
                Map.of(),
                null,
                null,
                true,
                false
            ),
            "BIADMIN"
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.blocking()).isTrue();
        assertThat(result.blockers()).contains("最近一次构建状态为 FAILED，不允许发布");
        verify(airflowClient, never()).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void submitShouldNotTriggerDagWhenBlockersAreConfirmed() {
        DbtQualityGateService qualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(qualityGateService.evaluate("tag:project-management"))
            .thenReturn(new DbtQualityGateService.DbtQualityGateResult(
                "tag:project-management",
                List.of("a"),
                true,
                false,
                "FAILED",
                "dbt build --select tag:project-management",
                "2026-03-29T00:00:00Z",
                1,
                List.of("以下模型未发现测试模板: biz_dwd_quality_issue"),
                List.of()
            ));
        when(releaseGateService.evaluate("tag:project-management", null, null, true))
            .thenReturn(new DbtReleaseGateService.DbtReleaseGateResult(
                "tag:project-management",
                true,
                null,
                null,
                "BLOCK",
                true,
                false,
                List.of("最近一次构建状态为 FAILED，不允许发布"),
                List.of(),
                new DbtReleaseGateService.BuildEvidence("inv-1", "dbt build --select tag:project-management", "FAILED", "2026-03-29T00:00:00Z", "/tmp/run_results.json")
            ));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "dag_id", "dwh_biadmin_dbt_manual", "state", "queued")));

        DbtReleaseSubmissionService service = newService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            externalRunLogService
        );

        DbtReleaseSubmissionService.DbtReleaseSubmitResult result = service.submit(
            new DbtReleaseSubmissionService.DbtReleaseSubmitRequest(
                "tag:project-management",
                "dev",
                Map.of(),
                null,
                null,
                true,
                true
            ),
            "BIADMIN"
        );

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.dagRunId()).isNull();
        assertThat(result.dagId()).isEqualTo("dwh_biadmin_dbt_manual");
        assertThat(result.blockers()).contains("以下模型未发现测试模板: biz_dwd_quality_issue", "最近一次构建状态为 FAILED，不允许发布");
        verify(airflowClient, never()).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    private DbtReleaseSubmissionService newService(
        DbtQualityGateService qualityGateService,
        DbtReleaseGateService releaseGateService,
        DbtDagService dbtDagService,
        AirflowClient airflowClient,
        ExternalRunLogService externalRunLogService
    ) {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        return new DbtReleaseSubmissionService(
            qualityGateService,
            releaseGateService,
            dbtDagService,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            new ObjectMapper()
        );
    }
}
