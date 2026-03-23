package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
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
import com.yuzhi.dts.platform.service.etl.DbtRunResultService;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import com.yuzhi.dts.platform.service.topic.TopicBindingRuntimeService;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EtlResourceTest {

    @Test
    void triggerDbtCompile_shouldWaitUntilDagIsVisibleBeforeTriggering() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(1);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(true, "/tmp/topic_sources.yml", 0, Map.of(), List.of())
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.empty(), java.util.Optional.empty());
        when(airflowClient.listDags(200))
            .thenReturn(java.util.Optional.of(Map.of("dags", List.of())))
            .thenReturn(java.util.Optional.of(Map.of("dags", List.of(Map.of("dag_id", "dwh_biadmin_dbt_manual")))));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "state", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        resource.triggerDbtCompile(
            new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "compile", Map.of(), null, null, null),
            "BIADMIN"
        );

        verify(airflowClient, times(2)).listDags(200);
        verify(airflowClient).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void triggerDbtCompile_shouldUnpauseVisibleDagBeforeTriggering() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(1);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(true, "/tmp/topic_sources.yml", 0, Map.of(), List.of())
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200))
            .thenReturn(java.util.Optional.of(Map.of("dags", List.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", true)))));
        when(airflowClient.setDagPaused("dwh_biadmin_dbt_manual", false))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "state", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        resource.triggerDbtCompile(
            new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "compile", Map.of(), null, null, null),
            "BIADMIN"
        );

        verify(airflowClient).setDagPaused("dwh_biadmin_dbt_manual", false);
        verify(airflowClient).triggerDag(eq("dwh_biadmin_dbt_manual"), any());
    }

    @Test
    void triggerDbtCompile_shouldFailWhenAirflowTriggerReturnsEmpty() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(true, "/tmp/topic_sources.yml", 0, Map.of(), List.of())
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual")).thenReturn(java.util.Optional.empty());
        when(airflowClient.listDags(200))
            .thenReturn(java.util.Optional.of(Map.of("dags", List.of(Map.of("dag_id", "dwh_biadmin_dbt_manual")))));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any())).thenReturn(java.util.Optional.empty());

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        assertThatThrownBy(
            () ->
                resource.triggerDbtCompile(
                    new EtlResource.DbtRunRequest(
                        "tag:project-management",
                        "tag:project-management",
                        "dev",
                        "compile",
                        Map.of(),
                        null,
                        null,
                        null
                    ),
                    "BIADMIN"
                )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Airflow");
    }

    @Test
    void shouldUseLatestBuildSummaryForSyncStatusAndSyncRelevantDagRuns() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        Map<String, Object> dagRunsPayload = Map.of("dag_runs", List.of(Map.of("dag_run_id", "dag-run-1", "state", "success")));
        DbtRunResultService.DbtRunSummary summary = new DbtRunResultService.DbtRunSummary(
            true,
            "/opt/dbt",
            "/opt/dbt/target/run_results.json",
            "/opt/dbt/target/manifest.json",
            "dag-run-1",
            "2026-03-16T08:00:00Z",
            "dbt compile --select tag:project-management",
            "SUCCESS",
            20,
            20,
            0,
            0,
            List.of(),
            List.of()
        );

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.listDagRuns("dwh_biadmin_dbt_manual", 10)).thenReturn(java.util.Optional.of(dagRunsPayload));
        when(dbtRunResultService.loadLatestBuildSummary(20)).thenReturn(summary);

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        ApiResponse<DbtArtifactSyncState.DbtArtifactSyncStatus> response = resource.getDbtSyncStatus("tag:project-management", "BIADMIN");

        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().latestRun()).isEqualTo(summary);
        verify(dbtRunResultService).loadLatestBuildSummary(20);
        verify(dbtRunResultService, never()).loadLatestSummary(any(Integer.class));
        verify(externalRunLogService).syncAirflowRuns(
            eq(ExternalRunLogService.ENTRY_DBT),
            eq("dwh_biadmin_dbt_manual"),
            eq(dagRunsPayload),
            eq("BIADMIN")
        );
    }

    @Test
    void shouldAutoSyncAssetsAfterBuildTrigger() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any())).thenReturn(java.util.Optional.of(Map.of("status", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        resource.triggerDbtRun(
            new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "build", Map.of(), null, null, null),
            "BIADMIN"
        );

        verify(dbtAssetSyncService, never()).syncFromManifest();
        verify(dbtRunResultService, never()).syncFromRunResults();
        verify(sqlModelRepository, never()).saveAll(any());
    }

    @Test
    void triggerDbtTest_shouldNotUpdateModelStatusImmediatelyAfterTrigger() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(true, "/tmp/topic_sources.yml", 0, Map.of(), List.of())
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any()))
            .thenReturn(java.util.Optional.of(Map.of("dag_run_id", "run-1", "state", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        resource.triggerDbtTest(
            new EtlResource.DbtRunRequest("tag:project-management", "tag:project-management", "dev", "test", Map.of(), null, null, null),
            "BIADMIN"
        );

        verify(sqlModelRepository, never()).saveAll(any());
    }

    @Test
    void rebuildDbtOutputRelation_shouldPrepareRelationAndTriggerBuild() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        UUID modelId = UUID.randomUUID();
        when(dbtOutputRelationService.prepareRebuild(modelId))
            .thenReturn(
                new DbtOutputRelationService.DbtOutputRelationActionResult(
                    modelId,
                    "biz_ads_major_project_overview",
                    "tag:project-management",
                    "\"public\".\"major_project_overview\"",
                    "rebuild",
                    true,
                    true,
                    "dropped"
                )
            );
        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any())).thenReturn(java.util.Optional.of(Map.of("status", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        ApiResponse<Map<String, Object>> response = resource.rebuildDbtOutputRelation(
            new EtlResource.DbtOutputRelationRequest(modelId, "dev", Map.of()),
            "BIADMIN"
        );

        assertThat(response.getData()).containsEntry("selector", "tag:project-management");
        assertThat(response.getData()).containsEntry("relation", "\"public\".\"major_project_overview\"");
        verify(dbtOutputRelationService).prepareRebuild(modelId);
    }

    @Test
    void triggerDbtCompile_shouldRefreshSourcesAndMergeTopicBindingVars() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(
                true,
                "/tmp/topic_sources.yml",
                1,
                Map.of("topic_bindings", Map.of("project-management", Map.of("project_subject_domain", Map.of("table", "ods_pm_project_20260316")))),
                List.of()
            )
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenReturn(java.util.Optional.of(Map.of("dag_id", "dwh_biadmin_dbt_manual", "is_paused", false)));
        when(airflowClient.triggerDag(eq("dwh_biadmin_dbt_manual"), any())).thenReturn(java.util.Optional.of(Map.of("status", "queued")));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        resource.triggerDbtCompile(
            new EtlResource.DbtRunRequest(
                "tag:project-management",
                "tag:project-management",
                "dev",
                "compile",
                Map.of("manual_flag", true),
                null,
                null,
                null
            ),
            "BIADMIN"
        );

        verify(dbtSourceService).refreshOdsSources();
        verify(topicBindingRuntimeService).compileRuntimeArtifacts();
        verify(airflowClient).triggerDag(eq("dwh_biadmin_dbt_manual"), argThat(payload -> {
            Object confObj = ((Map<?, ?>) payload).get("conf");
            if (!(confObj instanceof Map<?, ?> conf)) {
                return false;
            }
            Object vars = conf.get("vars");
            return vars instanceof String text && text.contains("topic_bindings") && text.contains("manual_flag");
        }));
    }

    @Test
    void triggerDbtCompile_shouldSurfaceAirflowDagLookupFailure() {
        DbtConfigService dbtConfigService = mock(DbtConfigService.class);
        DbtManifestService manifestService = mock(DbtManifestService.class);
        DbtSourceService dbtSourceService = mock(DbtSourceService.class);
        DbtAssetSyncService dbtAssetSyncService = mock(DbtAssetSyncService.class);
        DbtDagService dbtDagService = mock(DbtDagService.class);
        DbtPreviewService dbtPreviewService = mock(DbtPreviewService.class);
        DbtOutputRelationService dbtOutputRelationService = mock(DbtOutputRelationService.class);
        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        DbtQualityGateService dbtQualityGateService = mock(DbtQualityGateService.class);
        DbtReleaseGateService dbtReleaseGateService = mock(DbtReleaseGateService.class);
        TopicBindingRuntimeService topicBindingRuntimeService = mock(TopicBindingRuntimeService.class);
        DbtArtifactSyncState dbtArtifactSyncState = new DbtArtifactSyncState();
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);
        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        AuditService auditService = mock(AuditService.class);
        ModelingSqlModelRepository sqlModelRepository = mock(ModelingSqlModelRepository.class);

        when(dbtDagService.ensureDagForSelector("tag:project-management")).thenReturn("dwh_biadmin_dbt_manual");
        when(dbtSourceService.refreshOdsSources()).thenReturn(DbtSourceService.DbtSourceRefreshResult.success("/tmp/ods_sources.yml", 1));
        when(topicBindingRuntimeService.compileRuntimeArtifacts()).thenReturn(
            new TopicBindingRuntimeService.RuntimeCompilationResult(true, "/tmp/topic_sources.yml", 0, Map.of(), List.of())
        );
        when(airflowClient.getDag("dwh_biadmin_dbt_manual"))
            .thenThrow(new AirflowClient.AirflowApiException("查询 DAG 失败 (dwh_biadmin_dbt_manual): 500 INTERNAL_SERVER_ERROR", null));

        EtlResource resource = new EtlResource(
            dbtConfigService,
            manifestService,
            dbtSourceService,
            dbtAssetSyncService,
            dbtDagService,
            dbtPreviewService,
            dbtOutputRelationService,
            dbtRunResultService,
            dbtQualityGateService,
            dbtReleaseGateService,
            topicBindingRuntimeService,
            dbtArtifactSyncState,
            airflowClient,
            airflowProperties,
            externalRunLogService,
            auditService,
            new ObjectMapper(),
            sqlModelRepository
        );

        assertThatThrownBy(
            () ->
                resource.triggerDbtCompile(
                    new EtlResource.DbtRunRequest(
                        "tag:project-management",
                        "tag:project-management",
                        "dev",
                        "compile",
                        Map.of(),
                        null,
                        null,
                        null
                    ),
                    "BIADMIN"
                )
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Airflow DAG 查询失败");
    }
}
