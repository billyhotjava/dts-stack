package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ingestion.RollbackConfirmationTokenService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RollbackProxyResourceTest {

    @Test
    void analyzeWritesStrictCentralAuditWithoutConfirmationTokenOrRawRequest() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        AuditService auditService = mock(AuditService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService(),
            new RollbackConfirmationTokenService(),
            sanitizingAccessDecisionService(),
            auditService
        );
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of("confirmationType", "MODAL", "targetCount", 3, "dbPassword", "must-not-audit")
                )
            );

        resource.analyze(Map.of("level", 1, "scope", "task", "taskId", 7));

        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_ANALYZE"),
            eq(AuditStage.BEGIN),
            eq("7"),
            argThat(this::isSafeRollbackAuditPayload)
        );
        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_ANALYZE"),
            eq(AuditStage.SUCCESS),
            eq("7"),
            argThat(this::isSafeRollbackAuditPayload)
        );
    }

    private boolean isSafeRollbackAuditPayload(Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return false;
        }
        return payload.containsKey("auditOperationId") &&
            payload.containsKey("operator") &&
            payload.containsKey("level") &&
            payload.containsKey("scope") &&
            payload.keySet().stream().noneMatch(key ->
                java.util.List.of("confirmationToken", "confirmationText", "request", "dbPassword", "config")
                    .contains(String.valueOf(key))
            );
    }

    private boolean hasInvalidationReceipt(Object value) {
        return value instanceof Map<?, ?> payload &&
            payload.get("invalidationReceiptId") instanceof String receipt &&
            !receipt.isBlank() &&
            payload.containsKey("beginAuditReceipt");
    }

    private boolean hasReceipt(Object value, UUID receiptId) {
        return value instanceof Map<?, ?> payload &&
            receiptId.toString().equals(payload.get("receiptId")) &&
            payload.containsKey("auditOperationId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void modalAnalyzeIssuesOneTimeTokenAndExecuteDoesNotRequireConfirmationTextEcho() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        AuditService auditService = mock(AuditService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            auditService
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
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("status", "SUCCESS", "success", true)));

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

        var executeResponse = resource.execute(executeRequest);
        ApiResponse<Object> executed = executeResponse.getBody();
        assertThat(executed).isNotNull();
        assertThat(executeResponse.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(executed.getCode()).isEqualTo("ROLLBACK_APPLIED_INVALIDATION_PENDING");
        ArgumentCaptor<Map<String, Object>> downstream = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).rollbackExecute(downstream.capture());
        assertThat(downstream.getValue())
            .containsEntry("level", 1)
            .containsKeys("rollbackId", "idempotencyKey", "requestHash", "availabilityFence");
        ArgumentCaptor<RollbackInvalidationService.PrepareCommand> prepare = ArgumentCaptor.forClass(
            RollbackInvalidationService.PrepareCommand.class
        );
        verify(cascadeService).prepare(prepare.capture());
        assertThat(prepare.getValue().sourceDataSourceId().toString())
            .isEqualTo("11111111-2222-3333-4444-555555555555");
        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_EXECUTE"),
            eq(AuditStage.BEGIN),
            eq("7"),
            argThat(this::isSafeRollbackAuditPayload)
        );
        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq("7"),
            argThat(payload -> isSafeRollbackAuditPayload(payload) && hasInvalidationReceipt(payload))
        );

        assertThatThrownBy(() -> resource.execute(executeRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeRejectsClientProvidedCascadeTargetBeforeExecutingRollback() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
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
        verify(cascadeService, never()).prepare(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeCascadeUsesSourceResolvedAndBoundDuringAnalyze() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("success", true)));

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));
        resource.execute(executeRequest);

        ArgumentCaptor<RollbackInvalidationService.PrepareCommand> prepare = ArgumentCaptor.forClass(
            RollbackInvalidationService.PrepareCommand.class
        );
        verify(cascadeService).prepare(prepare.capture());
        assertThat(prepare.getValue().sourceDataSourceId().toString()).isEqualTo(trustedSourceId);
        ArgumentCaptor<Map<String, Object>> downstream = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).rollbackExecute(downstream.capture());
        assertThat(downstream.getValue())
            .containsKeys("rollbackId", "idempotencyKey", "requestHash", "availabilityFence")
            .doesNotContainKeys("models", "confirmationToken", "confirmationText", "confirmationType");
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpSuccessWithoutExplicitRollbackSuccessNeverCleansPlatformMetadata() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of(
                        "success", false,
                        "auditId", "rollback-audit-7",
                        "dbPassword", "must-not-leak",
                        "hostPath", "/opt/rollback/raw.json"
                    )
                )
            );

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        org.springframework.http.ResponseEntity<ApiResponse<Object>> response = resource.execute(executeRequest);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody().getCode()).isEqualTo("ROLLBACK_OUTCOME_RECONCILIATION_REQUIRED");
        assertThat(data)
            .containsEntry("retryable", true)
            .containsEntry("manualRecoveryRequired", true)
            .doesNotContainKey("rollbackResult");
        verify(cascadeService).recordDispatchFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliationMarkerFailureReportsPreparedStateInsteadOfClaimingPersistence() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService invalidationService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new ApiResponse<>(503, "unknown", Map.of("success", false)));
        doThrow(new IllegalStateException("audit unavailable"))
            .when(invalidationService)
            .recordDispatchFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
            );
        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        var response = resource.execute(executeRequest);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        Map<String, Object> diagnostic = (Map<String, Object>) data.get("diagnostic");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody().getCode()).isEqualTo("ROLLBACK_RECONCILIATION_MARK_FAILED");
        assertThat(data).containsEntry("state", "UNKNOWN");
        assertThat(diagnostic).containsEntry("reconciliationMarkerPersisted", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unresolvedInvalidationTargetsStopBeforeCallingIngestionExecute() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService invalidationService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        doThrow(RollbackInvalidationException.unresolvedTarget("missing target"))
            .when(invalidationService)
            .prepare(org.mockito.ArgumentMatchers.any());
        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        assertThatThrownBy(() -> resource.execute(executeRequest))
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND")
            );
        verify(ingestionClient, never()).rollbackExecute(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void unresolvedInvalidationTargetsMapToStableConflictOverHttp() throws Exception {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService invalidationService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        doThrow(RollbackInvalidationException.unresolvedTarget("missing target"))
            .when(invalidationService)
            .prepare(org.mockito.ArgumentMatchers.any());
        var mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(resource)
            .build();
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String analysisJson = mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/rollback/analyze")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"level\":1,\"scope\":\"task\",\"taskId\":7}")
            )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        var analysis = objectMapper.readTree(analysisJson).path("data");
        String executeJson = objectMapper.writeValueAsString(
            Map.of(
                "level",
                1,
                "scope",
                "task",
                "taskId",
                7,
                "confirmationType",
                analysis.path("confirmationType").asText(),
                "confirmationToken",
                analysis.path("confirmationToken").asText()
            )
        );

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/rollback/execute")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(executeJson)
            )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .jsonPath("$.code")
                    .value("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND")
            );
        verify(ingestionClient, never()).rollbackExecute(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void nullAndNon2xxRollbackResponsesRequireManualRecoveryWithoutCleanup() {
        assertUnconfirmedRollbackResponse(null, null);
        assertUnconfirmedRollbackResponse(
            new ApiResponse<>(HttpStatus.SERVICE_UNAVAILABLE.value(), "downstream unavailable", Map.of("success", true)),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyzeExecuteAndAuditRecursivelySanitizeSuccessAndFailurePayloads() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService(),
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        Map<String, Object> command = Map.of("level", 1, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of(
                        "confirmationType", "MODAL",
                        "hostPath", "/opt/rollback/impact.json",
                        "dbPassword", "hidden",
                        "nested", Map.of("authToken", "hidden", "count", 2)
                    )
                )
            );
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 8)))
            .thenReturn(
                new ApiResponse<>(
                    HttpStatus.BAD_GATEWAY.value(),
                    "failed",
                    Map.of("hostPath", "/opt/rollback/failure.json", "secretAccessKey", "hidden", "reason", "timeout")
                )
            );
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of(
                        "success", true,
                        "containerPath", "/decrypted/result.json",
                        "passphrase", "hidden",
                        "nested", Map.of("path", "/tmp/raw.json", "rows", 3)
                    )
                )
            );
        when(ingestionClient.getRollbackAuditLog(7L, null))
            .thenReturn(
                new ApiResponse<>(
                    200,
                    "ok",
                    Map.of(
                        "content",
                        java.util.List.of(Map.of(
                            "auditId",
                            "audit-7",
                            "hostPath",
                            "/opt/audit.json",
                            "pwd",
                            "hidden",
                            "requestJson",
                            "{\"request\":{\"password\":\"audit-password\",\"hostPath\":\"/opt/audit/request.json\"},\"scope\":\"task\"}",
                            "impactJson",
                            "{\"impact\":[{\"authToken\":\"audit-token\",\"containerPath\":\"/decrypted/audit-impact.json\",\"rows\":2}]}",
                            "resultJson",
                            "{\"result\":{\"secretAccessKey\":\"audit-secret\",\"hostPath\":\"/opt/audit/result.json\",\"success\":false}}",
                            "errorMessage",
                            "rollback failed password=audit-plain-secret; containerPath=/decrypted/audit-error.log; reason=timeout"
                        ))
                    )
                )
            );

        ApiResponse<Object> analysis = resource.analyze(command).getBody();
        Map<String, Object> analysisData = (Map<String, Object>) analysis.getData();
        assertThat(analysisData).doesNotContainKeys("hostPath", "dbPassword");
        assertThat((Map<String, Object>) analysisData.get("nested"))
            .containsEntry("count", 2)
            .doesNotContainKey("authToken");

        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", analysisData.get("confirmationType"));
        executeRequest.put("confirmationToken", analysisData.get("confirmationToken"));
        Map<String, Object> executeData = (Map<String, Object>) resource.execute(executeRequest).getBody().getData();
        assertThat(executeData).containsEntry("state", "INVALIDATION_PENDING");
        Map<String, Object> downstreamResult = (Map<String, Object>) executeData.get("downstreamResult");
        assertThat(downstreamResult)
            .containsEntry("success", true)
            .doesNotContainKeys("containerPath", "passphrase");
        assertThat((Map<String, Object>) downstreamResult.get("nested"))
            .containsEntry("rows", 3)
            .doesNotContainKey("path");

        Map<String, Object> auditData = (Map<String, Object>) resource.getAuditLog(7L, null).getBody().getData();
        Map<String, Object> auditItem = (Map<String, Object>) ((java.util.List<?>) auditData.get("content")).get(0);
        assertThat(auditItem).containsEntry("auditId", "audit-7").doesNotContainKeys("hostPath", "pwd");
        assertThat((String) auditItem.get("requestJson"))
            .contains("\"scope\":\"task\"")
            .doesNotContain("audit-password", "/opt/audit/request.json", "password", "hostPath");
        assertThat((String) auditItem.get("impactJson"))
            .contains("\"rows\":2")
            .doesNotContain("audit-token", "/decrypted/audit-impact.json", "authToken", "containerPath");
        assertThat((String) auditItem.get("resultJson"))
            .contains("\"success\":false")
            .doesNotContain("audit-secret", "/opt/audit/result.json", "secretAccessKey", "hostPath");
        assertThat((String) auditItem.get("errorMessage"))
            .contains("reason=timeout")
            .doesNotContain("audit-plain-secret", "/decrypted/audit-error.log");

        ApiResponse<Object> failedAnalysis = resource.analyze(Map.of("level", 1, "scope", "task", "taskId", 8)).getBody();
        Map<String, Object> failedData = (Map<String, Object>) failedAnalysis.getData();
        assertThat(failedData)
            .containsEntry("reason", "timeout")
            .doesNotContainKeys("hostPath", "secretAccessKey");
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelThreeSuccessWaitsForAuthoritativeCompletionEvent() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("rollback", "SUCCESS", "success", true)));
        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        org.springframework.http.ResponseEntity<ApiResponse<Object>> partialResponse = resource.execute(executeRequest);
        ApiResponse<Object> partial = partialResponse.getBody();

        assertThat(partialResponse.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(partial).isNotNull();
        assertThat(partial.getStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
        assertThat(partial.getCode()).isEqualTo("ROLLBACK_APPLIED_INVALIDATION_PENDING");
        Map<String, Object> data = (Map<String, Object>) partial.getData();
        assertThat(data)
            .containsEntry("state", "INVALIDATION_PENDING")
            .containsEntry("failedStep", "INVALIDATION_APPLY")
            .containsEntry("retryable", true)
            .containsEntry("manualRecoveryRequired", true)
            .containsEntry("completionPending", true)
            .doesNotContainKey("retryContext")
            .doesNotContainKey("rollbackResult");
        assertThat((Map<String, Object>) data.get("diagnostic"))
            .containsKey("receiptId")
            .containsEntry("failedStep", "INVALIDATION_APPLY")
            .containsEntry("level", 3);
        verify(cascadeService, never()).complete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelTwoSuccessWaitsForAuthoritativeCompletionEvent() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", "11111111-2222-3333-4444-555555555555"));
        Map<String, Object> command = Map.of("level", 2, "scope", "task", "taskId", 7);
        Map<String, Object> analysis = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7L,
            "dryRun", true
        );
        Map<String, Object> execution = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7L,
            "dryRun", false
        );
        when(ingestionClient.rollbackAnalyze(analysis))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("rollback", "SUCCESS", "success", true)));
        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        org.springframework.http.ResponseEntity<ApiResponse<Object>> partialResponse = resource.execute(executeRequest);
        ApiResponse<Object> partial = partialResponse.getBody();

        assertThat(partialResponse.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(partial).isNotNull();
        assertThat(partial.getStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
        assertThat(partial.getCode()).isEqualTo("ROLLBACK_APPLIED_INVALIDATION_PENDING");
        Map<String, Object> data = (Map<String, Object>) partial.getData();
        assertThat(data)
            .containsEntry("failedStep", "INVALIDATION_APPLY")
            .containsEntry("retryable", true)
            .containsEntry("manualRecoveryRequired", true)
            .containsEntry("completionPending", true)
            .doesNotContainKey("retryContext");
        assertThat((Map<String, Object>) data.get("diagnostic"))
            .containsEntry("failedStep", "INVALIDATION_APPLY")
            .containsEntry("level", 2)
            .containsEntry("taskId", 7L);
        verify(cascadeService, never()).complete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void committedCommandAlreadyClaimedByWorkerReturnsDurableDispatchPendingWithoutDuplicateSend() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        org.mockito.Mockito.doReturn(Optional.empty())
            .when(cascadeService)
            .claimDispatch(org.mockito.ArgumentMatchers.any());
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        Map<String, Object> command = Map.of("level", 1, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(1, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        var response = resource.execute(executeRequest);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody().getCode()).isEqualTo("ROLLBACK_DISPATCH_PENDING");
        assertThat(data)
            .containsEntry("dispatchPending", true)
            .containsEntry("completionPending", true)
            .containsEntry("manualRecoveryRequired", false);
        verify(ingestionClient, never()).rollbackExecute(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void receiptQueryAndManualReplayExposeDurableDispatchState() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = mock(RollbackInvalidationService.class);
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        AuditService auditService = mock(AuditService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            auditService
        );
        UUID receiptId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        var retry = new RollbackInvalidationService.ReceiptView(
            receiptId,
            RollbackInvalidationService.RECONCILIATION_REQUIRED,
            sourceId,
            10L,
            2,
            new RollbackInvalidationService.DispatchView(
                receiptId,
                "RETRY",
                3,
                null,
                null,
                null,
                "connection reset"
            )
        );
        var pending = new RollbackInvalidationService.ReceiptView(
            receiptId,
            RollbackInvalidationService.RECONCILIATION_REQUIRED,
            sourceId,
            10L,
            2,
            new RollbackInvalidationService.DispatchView(receiptId, "PENDING", 3, null, null, null, null)
        );
        when(cascadeService.receipt(receiptId)).thenReturn(retry);
        when(cascadeService.replayDispatch(receiptId)).thenReturn(pending);

        var queried = resource.getReceipt(receiptId);
        var replayed = resource.replayDispatch(receiptId);

        assertThat(queried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queried.getBody().getData()).isEqualTo(retry);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replayed.getBody().getCode()).isEqualTo("ROLLBACK_DISPATCH_REPLAY_QUEUED");
        assertThat(replayed.getBody().getData()).isEqualTo(pending);
        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_REPLAY"),
            eq(AuditStage.BEGIN),
            eq(receiptId.toString()),
            argThat(payload -> hasReceipt(payload, receiptId))
        );
        verify(auditService).auditActionStrict(
            eq("INGESTION_ROLLBACK_REPLAY"),
            eq(AuditStage.SUCCESS),
            eq(receiptId.toString()),
            argThat(payload -> hasReceipt(payload, receiptId))
        );
    }

    @Test
    void replayIsNotExecutedTwiceOrReclassifiedAsFailWhenSuccessAuditFails() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = mock(RollbackInvalidationService.class);
        AuditService auditService = mock(AuditService.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            sanitizingAccessDecisionService(),
            auditService
        );
        UUID receiptId = UUID.fromString("70000000-0000-0000-0000-000000000002");
        UUID beginReceipt = UUID.randomUUID();
        RollbackInvalidationService.ReceiptView pending = new RollbackInvalidationService.ReceiptView(
            receiptId,
            RollbackInvalidationService.RECONCILIATION_REQUIRED,
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            10L,
            2,
            new RollbackInvalidationService.DispatchView(receiptId, "PENDING", 3, null, null, null, null)
        );
        when(cascadeService.replayDispatch(receiptId)).thenReturn(pending);
        when(auditService.auditActionStrict(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(AuditStage.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
            ))
            .thenAnswer(invocation -> {
                if (invocation.getArgument(1) == AuditStage.SUCCESS) {
                    throw new IllegalStateException("audit outbox unavailable");
                }
                return beginReceipt;
            });

        assertThatThrownBy(() -> resource.replayDispatch(receiptId))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );

        verify(cascadeService).replayDispatch(receiptId);
        verify(auditService, never()).auditActionStrict(
            eq("INGESTION_ROLLBACK_REPLAY"),
            eq(AuditStage.FAIL),
            eq(receiptId.toString()),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void analyzeRejectsUnknownConfirmationType() {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            invalidationService(),
            new RollbackConfirmationTokenService(),
            sanitizingAccessDecisionService(),
            mock(AuditService.class)
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
            invalidationService(),
            new RollbackConfirmationTokenService(),
            sanitizingAccessDecisionService(),
            mock(AuditService.class)
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
                "dryRun", "true"
            )))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        assertThatThrownBy(() -> resource.analyze(Map.of(
                "level", 2,
                "scope", "task",
                "taskId", 7,
                "tables", java.util.List.of("orders")
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
            "dryRun", true
        );
    }

    private static Map<String, Object> executionTaskCommand(int level, long taskId) {
        return Map.of(
            "level", level,
            "scope", "task",
            "taskId", taskId,
            "dryRun", false
        );
    }

    private static RollbackInvalidationService invalidationService() {
        RollbackInvalidationService service = mock(RollbackInvalidationService.class);
        UUID receiptId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        AtomicReference<RollbackInvalidationService.PrepareCommand> preparedCommand = new AtomicReference<>();
        when(service.prepare(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                RollbackInvalidationService.PrepareCommand command = invocation.getArgument(0);
                preparedCommand.set(command);
                return new RollbackInvalidationService.PreparedInvalidation(
                    receiptId,
                    RollbackInvalidationService.PREPARED,
                    command.sourceDataSourceId(),
                    10L,
                    2,
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    false
                );
            });
        when(
            service.claimDispatch(org.mockito.ArgumentMatchers.any())
        ).thenAnswer(invocation -> {
            RollbackInvalidationService.PrepareCommand prepared = preparedCommand.get();
            Map<String, Object> command = new LinkedHashMap<>(prepared.plan().toMap());
            command.put("rollbackId", receiptId.toString());
            command.put("idempotencyKey", "platform:" + receiptId);
            command.put("requestHash", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            command.put(
                "availabilityFence",
                Map.of(
                    "receiptId",
                    receiptId.toString(),
                    "sourceDataSourceId",
                    prepared.sourceDataSourceId().toString(),
                    "sourceSequence",
                    10L,
                    "state",
                    RollbackInvalidationService.PREPARED,
                    "targetCount",
                    2
                )
            );
            return Optional.of(
                new RollbackInvalidationService.DispatchEnvelope(
                    receiptId,
                    1,
                    "a".repeat(64),
                    Map.copyOf(command)
                )
            );
        });
        when(
            service.recordDispatchFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
            )
        ).thenReturn(
            new RollbackInvalidationService.DispatchView(
                receiptId,
                "RETRY",
                1,
                null,
                null,
                null,
                "ROLLBACK_DISPATCH_NOT_CONFIRMED"
            )
        );
        when(service.markDispatchSent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(
                new RollbackInvalidationService.DispatchView(
                    receiptId,
                    "SENT",
                    1,
                    null,
                    null,
                    null,
                    null
                )
            );
        return service;
    }

    @SuppressWarnings("unchecked")
    private void assertUnconfirmedRollbackResponse(ApiResponse<Object> rollbackResult, Integer downstreamStatus) {
        IngestionServiceClient ingestionClient = mock(IngestionServiceClient.class);
        RollbackInvalidationService cascadeService = invalidationService();
        IngestionAccessDecisionService accessDecisionService = sanitizingAccessDecisionService();
        RollbackProxyResource resource = new RollbackProxyResource(
            ingestionClient,
            cascadeService,
            new RollbackConfirmationTokenService(),
            accessDecisionService,
            mock(AuditService.class)
        );
        String trustedSourceId = "11111111-2222-3333-4444-555555555555";
        Map<String, Object> command = Map.of("level", 3, "scope", "task", "taskId", 7);
        when(ingestionClient.rollbackAnalyze(analysisTaskCommand(3, 7)))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("confirmationType", "MODAL")));
        when(accessDecisionService.requireTaskAccess(7L, true))
            .thenReturn(Map.of("id", 7, "sourceDataSourceId", trustedSourceId));
        when(ingestionClient.rollbackExecute(org.mockito.ArgumentMatchers.anyMap())).thenReturn(rollbackResult);

        Map<String, Object> impact = (Map<String, Object>) resource.analyze(command).getBody().getData();
        Map<String, Object> executeRequest = new LinkedHashMap<>(command);
        executeRequest.put("confirmationType", impact.get("confirmationType"));
        executeRequest.put("confirmationToken", impact.get("confirmationToken"));

        org.springframework.http.ResponseEntity<ApiResponse<Object>> response = resource.execute(executeRequest);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        Map<String, Object> diagnostic = (Map<String, Object>) data.get("diagnostic");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody().getCode()).isEqualTo("ROLLBACK_OUTCOME_RECONCILIATION_REQUIRED");
        assertThat(data)
            .containsEntry("retryable", true)
            .containsEntry("manualRecoveryRequired", true);
        if (downstreamStatus == null) {
            assertThat(diagnostic).doesNotContainKey("downstreamStatus");
        } else {
            assertThat(diagnostic).containsEntry("downstreamStatus", downstreamStatus);
        }
        assertThat(data).doesNotContainKey("rollbackResult");
        verify(cascadeService).recordDispatchFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private static IngestionAccessDecisionService sanitizingAccessDecisionService() {
        IngestionAccessDecisionService boundary = new IngestionAccessDecisionService(
            mock(com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository.class),
            mock(IngestionServiceClient.class),
            mock(com.yuzhi.dts.platform.security.ClassificationUtils.class),
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        IngestionAccessDecisionService accessDecisionService = mock(IngestionAccessDecisionService.class);
        org.mockito.Mockito.doAnswer(invocation -> boundary.sanitizeResponse(invocation.getArgument(0)))
            .when(accessDecisionService)
            .sanitizeResponse(org.mockito.ArgumentMatchers.any());
        when(
            accessDecisionService.requireTaskAccess(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(true)
            )
        ).thenAnswer(invocation ->
            Map.of(
                "id",
                invocation.<Long>getArgument(0),
                "sourceDataSourceId",
                "11111111-2222-3333-4444-555555555555"
            )
        );
        return accessDecisionService;
    }
}
