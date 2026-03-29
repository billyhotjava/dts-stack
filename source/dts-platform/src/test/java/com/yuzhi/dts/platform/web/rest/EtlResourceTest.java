package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import com.yuzhi.dts.platform.service.etl.DbtAssetSyncService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.etl.DbtOutputRelationService;
import com.yuzhi.dts.platform.service.etl.DbtPreviewService;
import com.yuzhi.dts.platform.service.etl.DbtQualityGateService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseSubmissionService;
import com.yuzhi.dts.platform.service.etl.DbtRunResultService;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EtlResourceTest {

    @Test
    void triggerDbtCompileShouldFailFastWhenDagIsNotReady() {
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = airflowProperties(30, 1);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200)).thenReturn(java.util.Optional.of(Map.of("dags", java.util.List.of())));

        EtlResource resource = newResource(dbtDagService, dbtSourceService, airflowClient, airflowProperties);

        assertThatThrownBy(
            () ->
                resource.triggerDbtCompile(
                    new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "compile", Map.of(), null, null, null),
                    "BIADMIN"
                )
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("尚未在 Airflow 中注册"));

        verify(airflowClient, times(1)).listDags(200);
    }

    @Test
    void triggerDbtCompileShouldUnpauseVisibleDagBeforeTriggering() {
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = airflowProperties(1, 0);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200))
            .thenReturn(java.util.Optional.of(Map.of("dags", java.util.List.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", true)))));
        when(airflowClient.setDagPaused("dwh_biadmin_dbt_manual", false))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "state", "queued")));

        EtlResource resource = newResource(dbtDagService, dbtSourceService, airflowClient, airflowProperties);

        resource.triggerDbtCompile(
            new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "compile", Map.of(), null, null, null),
            "BIADMIN"
        );

        verify(airflowClient).setDagPaused("dwh_biadmin_dbt_manual", false);
        verify(airflowClient).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void triggerDbtCompileShouldPreserveManualVarsInTriggerPayload() {
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = airflowProperties(0, 0);

        when(dbtDagService.ensureDagForSelector("model:test_model")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "state", "queued")));

        EtlResource resource = newResource(dbtDagService, dbtSourceService, airflowClient, airflowProperties);

        resource.triggerDbtCompile(
            new EtlResource.DbtRunRequest(
                "model:test_model",
                "model:test_model",
                "dev",
                "compile",
                Map.of("manual_flag", true, "owner", "bi"),
                null,
                null,
                null
            ),
            "BIADMIN"
        );

        verify(airflowClient).triggerDag(
            eq("dwh_biadmin_dbt_manual"),
            argThat(payload -> {
                Object conf = payload.get("conf");
                if (!(conf instanceof Map<?, ?> confMap)) {
                    return false;
                }
                Object vars = confMap.get("vars");
                if (!(vars instanceof String varsJson)) {
                    return false;
                }
                return varsJson.contains("\"manual_flag\":true") && varsJson.contains("\"owner\":\"bi\"");
            })
        );
    }

    @Test
    void triggerDbtCompileShouldSurfaceAirflowDagLookupFailure() {
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = airflowProperties(0, 0);

        when(dbtDagService.ensureDagForSelector("model:test_model")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200)).thenReturn(java.util.Optional.empty());

        EtlResource resource = newResource(dbtDagService, dbtSourceService, airflowClient, airflowProperties);

        assertThatThrownBy(
            () ->
                resource.triggerDbtCompile(
                    new EtlResource.DbtRunRequest("model:test_model", "model:test_model", "dev", "compile", Map.of(), null, null, null),
                    "BIADMIN"
                )
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("尚未在 Airflow 中注册"));
    }

    private EtlResource newResource(
        DbtDagService dbtDagService,
        DbtSourceService dbtSourceService,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties
    ) {
        return new EtlResource(
            mock(DbtConfigService.class),
            mock(DbtManifestService.class),
            dbtSourceService,
            mock(DbtAssetSyncService.class),
            dbtDagService,
            mock(DbtPreviewService.class),
            mock(DbtOutputRelationService.class),
            mock(DbtRunResultService.class),
            mock(DbtQualityGateService.class),
            mock(DbtReleaseGateService.class),
            mock(DbtReleaseSubmissionService.class),
            new DbtArtifactSyncState(),
            airflowClient,
            airflowProperties,
            mock(ExternalRunLogService.class),
            mock(AuditService.class),
            new ObjectMapper(),
            mock(ModelingSqlModelRepository.class)
        );
    }

    private AirflowProperties airflowProperties(int waitSeconds, int pollSeconds) {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(waitSeconds);
        airflowProperties.setDagReadyPollSeconds(pollSeconds);
        return airflowProperties;
    }
}
