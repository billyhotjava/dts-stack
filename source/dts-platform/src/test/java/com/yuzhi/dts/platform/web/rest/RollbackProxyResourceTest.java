package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.etl.RollbackCascadeService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ingestion.RollbackConfirmationTokenService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RollbackProxyResourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void modalAnalyzeIssuesOneTimeTokenAndExecuteDoesNotRequireConfirmationTextEcho() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackCascadeService cascadeService = mock(RollbackCascadeService.class);
        IngestionAccessDecisionService accessDecisionService = mock(IngestionAccessDecisionService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService
        );
        Map<String, Object> command = Map.of("level", 1, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of(
                        "level", 1,
                        "scope", "task",
                        "confirmationType", "MODAL",
                        "confirmationText", "确认清空 ODS 数据"
                    )
                )
            );
        when(ingestionClient.rollbackExecute(executionTaskCommand(1, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("status", "SUCCESS")));

        ApiResponse<Object> analyzed = resource.analyze(command).getBody();
        assertThat(analyzed).isNotNull();
        Map<String, Object> impact = (Map<String, Object>) analyzed.getData();
        assertThat(impact)
            .containsEntry("confirmationType", "MODAL")
            .containsEntry("confirmationText", "确认清空 ODS 数据");
        assertThat(impact.get("confirmationToken")).asString().isNotBlank();

        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));
        executeRequest.put("confirmationExpiresAt", impact.get("confirmationExpiresAt"));

        ApiResponse<Object> executed = resource.execute(executeRequest).getBody();
        assertThat(executed).isNotNull();
        assertThat(executed.getStatus()).isEqualTo(200);
        verify(ingestionClient).rollbackExecute(executionTaskCommand(1, 7));

        assertThatThrownBy(() -> resource.execute(executeRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeRejectsClientProvidedCascadeTargetBeforeExecutingRollback() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackCascadeService cascadeService = mock(RollbackCascadeService.class);
        IngestionAccessDecisionService accessDecisionService = mock(IngestionAccessDecisionService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));
        executeRequest.put("sourceDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        assertThatThrownBy(() -> resource.execute(executeRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        verify(ingestionClient, never()).rollbackExecute(org.mockito.ArgumentMatchers.anyMap());
        verify(cascadeService, never()).cascadeCleanup(
            org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeCascadeUsesSourceResolvedAndBoundDuringAnalyze() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackCascadeService cascadeService = mock(RollbackCascadeService.class);
        IngestionAccessDecisionService accessDecisionService = mock(IngestionAccessDecisionService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(executionTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("success", true)));

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));
        resource.execute(executeRequest);

        ArgumentCaptor<Map<String, Object>> cascadeCommand = ArgumentCaptor.forClass(Map.class);
        verify(cascadeService).cascadeCleanup(
            cascadeCommand.capture(),
            org.mockito.ArgumentMatchers.eq(Map.of("success", true))
        );
        assertThat(cascadeCommand.getValue())
            .containsEntry("sourceDataSourceId", trustedSourceId)
            .doesNotContainKeys("models", "confirmationToken", "confirmationText", "confirmationType");
        verify(ingestionClient).rollbackExecute(executionTaskCommand(3, 7));
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeCascadeFailureReturnsRetryablePartialFailure() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackCascadeService cascadeService = mock(RollbackCascadeService.class);
        IngestionAccessDecisionService accessDecisionService = mock(IngestionAccessDecisionService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(executionTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("rollback", "SUCCESS")));
        org.mockito.Mockito.doThrow(new IllegalStateException("cascade unavailable"))
            .when(cascadeService)
            .cascadeCleanup(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        ApiResponse<Object> partial = resource.execute(executeRequest).getBody();

        assertThat(partial).isNotNull();
        assertThat(partial.getStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
        assertThat(partial.getCode()).isEqualTo("PARTIAL_FAILED");
        Map<String, Object> data = (Map<String, Object>) partial.getData();
        assertThat(data)
            .containsEntry("state", "PARTIAL_FAILED")
            .containsEntry("failedStep", "CASCADE_CLEANUP")
            .containsEntry("retryable", true)
            .containsEntry("rollbackResult", Map.of("rollback", "SUCCESS"));
        assertThat((Map<String, Object>) data.get("retryContext"))
            .containsEntry("sourceDataSourceId", trustedSourceId)
            .containsEntry("dryRun", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelTwoDbtFailureReturnsRetryablePartialFailure() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackCascadeService cascadeService = mock(RollbackCascadeService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            mock(IngestionAccessDecisionService.class)
        );
        Map<String, Object> command = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7,
            "rebuildDbt", true
        );
        Map<String, Object> analysis = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7L,
            "rebuildDbt", true,
            "dryRun", true
        );
        Map<String, Object> execution = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7L,
            "rebuildDbt", true,
            "dryRun", false
        );
        when(ingestionClient.rollbackAnalyze(analysis))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(ingestionClient.rollbackExecute(execution))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("rollback", "SUCCESS")));
        org.mockito.Mockito.doThrow(new IllegalStateException("dbt unavailable"))
            .when(cascadeService)
            .triggerDbtFullRefresh(execution);

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        ApiResponse<Object> partial = resource.execute(executeRequest).getBody();

        assertThat(partial).isNotNull();
        assertThat(partial.getStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
        assertThat(partial.getCode()).isEqualTo("PARTIAL_FAILED");
        Map<String, Object> data = (Map<String, Object>) partial.getData();
        assertThat(data)
            .containsEntry("failedStep", "DBT_FULL_REFRESH")
            .containsEntry("retryable", true);
        assertThat((Map<String, Object>) data.get("retryContext"))
            .containsEntry("rebuildDbt", true)
            .containsEntry("dryRun", false);
    }

    @Test
    void analyzeRejectsUnknownConfirmationType() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            mock(RollbackCascadeService.class),
            new RollbackConfirmationTokenService(),
            mock(IngestionAccessDecisionService.class)
        );
        Map<String, Object> command = Map.of("level", 1, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of("confirmationType", "APPROVAL", "confirmationText", "confirm")
                )
            );

        assertThatThrownBy(() -> resource.analyze(command))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
            );
    }

    @Test
    void executeRejectsDryRunAndWrongBooleanTypesBeforeCallingIngestion() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            mock(RollbackCascadeService.class),
            new RollbackConfirmationTokenService(),
            mock(IngestionAccessDecisionService.class)
        );

        assertThatThrownBy(() -> resource.execute(Map.of(
                "level", 1,
                "scope", "task",
                "taskId", 7,
                "dryRun", true
            )))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        assertThatThrownBy(() -> resource.analyze(Map.of(
                "level", 1,
                "scope", "task",
                "taskId", 7,
                "rebuildDbt", "true"
            )))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        verify(ingestionClient, never()).rollbackExecute(org.mockito.ArgumentMatchers.any());
        verify(ingestionClient, never()).rollbackAnalyze(org.mockito.ArgumentMatchers.any());
    }

    private static Map<String, Object> analysisTaskCommand(int level, long taskId) {
        return Map.of(
            "level", level,
            "scope", "task",
            "taskId", taskId,
            "rebuildDbt", false,
            "dryRun", true
        );
    }

    private static Map<String, Object> executionTaskCommand(int level, long taskId) {
        return Map.of(
            "level", level,
            "scope", "task",
            "taskId", taskId,
            "rebuildDbt", false,
            "dryRun", false
        );
    }
}
