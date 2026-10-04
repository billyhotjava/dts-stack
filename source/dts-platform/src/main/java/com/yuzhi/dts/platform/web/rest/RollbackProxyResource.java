package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ingestion.RollbackCommand;
import com.yuzhi.dts.platform.service.ingestion.RollbackConfirmationTokenService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.DispatchEnvelope;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.PrepareCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.PreparedInvalidation;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.ReceiptView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rollback")
public class RollbackProxyResource {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackProxyResource.class);

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private final IngestionServiceClient ingestionClient;
    private final RollbackInvalidationService invalidationService;
    private final RollbackConfirmationTokenService confirmationTokenService;
    private final IngestionAccessDecisionService accessDecisionService;
    private final AuditService auditService;

    public RollbackProxyResource(
        IngestionServiceClient ingestionClient,
        RollbackInvalidationService invalidationService,
        RollbackConfirmationTokenService confirmationTokenService,
        IngestionAccessDecisionService accessDecisionService,
        AuditService auditService
    ) {
        this.ingestionClient = ingestionClient;
        this.invalidationService = invalidationService;
        this.confirmationTokenService = confirmationTokenService;
        this.accessDecisionService = accessDecisionService;
        this.auditService = auditService;
    }

    @PostMapping("/analyze")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> analyze(@RequestBody Map<String, Object> request) {
        RollbackCommand executionPlan = RollbackCommand.executionPlan(request, false);
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> auditDetails = rollbackAuditDetails(executionPlan);
        UUID beginAuditReceipt = auditRollbackAction(
            "INGESTION_ROLLBACK_ANALYZE",
            AuditStage.BEGIN,
            executionPlan,
            auditOperationId,
            "开始分析接入回退影响",
            auditDetails
        );
        boolean terminalAuditWritten = false;
        try {
            accessDecisionService.requireRollbackAccess(executionPlan, true);
            ApiResponse<Object> result = accessDecisionService.sanitizeResponse(
                ingestionClient.rollbackAnalyze(executionPlan.asAnalysisCommand().toMap())
            );
            if (result == null || result.getStatus() < 200 || result.getStatus() >= 300) {
                Map<String, Object> failed = new LinkedHashMap<>(auditDetails);
                failed.put("downstreamStatus", result == null ? "UNAVAILABLE" : result.getStatus());
                terminalRollbackAudit(
                    "INGESTION_ROLLBACK_ANALYZE",
                    AuditStage.FAIL,
                    executionPlan,
                    auditOperationId,
                    beginAuditReceipt,
                    "接入回退影响分析失败",
                    failed
                );
                terminalAuditWritten = true;
                return ResponseEntity.ok(result);
            }
            if (!(result.getData() instanceof Map<?, ?> impact)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "接入服务未返回有效的回退影响分析"
                );
            }

            Map<String, Object> confirmedImpact = new LinkedHashMap<>();
            impact.forEach((key, value) -> confirmedImpact.put(String.valueOf(key), value));
            String confirmationType = text(confirmedImpact.get("confirmationType"));
            String confirmationText = text(confirmedImpact.get("confirmationText"));
            String cascadeDataSourceId = resolveTrustedCascadeDataSourceId(executionPlan);
            RollbackConfirmationTokenService.IssuedConfirmation issued = confirmationTokenService.issue(
                executionPlan,
                confirmationType,
                confirmationText,
                SecurityUtils.getCurrentUserLogin().orElse("system"),
                cascadeDataSourceId
            );
            confirmedImpact.put("confirmationType", issued.confirmationType());
            confirmedImpact.put("confirmationText", issued.confirmationText());
            confirmedImpact.put("confirmationToken", issued.token());
            confirmedImpact.put("confirmationExpiresAt", issued.expiresAt());
            Map<String, Object> outcome = new LinkedHashMap<>(auditDetails);
            outcome.put("downstreamStatus", result.getStatus());
            outcome.put("confirmationType", issued.confirmationType());
            copyAuditCount(confirmedImpact, outcome, "targetCount");
            copyAuditCount(confirmedImpact, outcome, "affectedTaskCount");
            copyAuditCount(confirmedImpact, outcome, "affectedAssetCount");
            terminalRollbackAudit(
                "INGESTION_ROLLBACK_ANALYZE",
                AuditStage.SUCCESS,
                executionPlan,
                auditOperationId,
                beginAuditReceipt,
                "接入回退影响分析完成",
                outcome
            );
            terminalAuditWritten = true;
            return ResponseEntity.ok(new ApiResponse<>(result.getStatus(), result.getMessage(), result.getCode(), confirmedImpact));
        } catch (RuntimeException failure) {
            if (!(failure instanceof AuditFinalizationException) && !terminalAuditWritten) {
                Map<String, Object> failed = new LinkedHashMap<>(auditDetails);
                failed.put("errorType", failure.getClass().getSimpleName());
                if (failure instanceof ResponseStatusException statusFailure) {
                    failed.put("httpStatus", statusFailure.getStatusCode().value());
                }
                terminalRollbackAudit(
                    "INGESTION_ROLLBACK_ANALYZE",
                    AuditStage.FAIL,
                    executionPlan,
                    auditOperationId,
                    beginAuditReceipt,
                    "接入回退影响分析异常",
                    failed
                );
            }
            throw failure;
        }
    }

    @PostMapping("/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> execute(@RequestBody Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        RollbackCommand executionPlan = RollbackCommand.executionPlan(safeRequest, true);
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> auditDetails = rollbackAuditDetails(executionPlan, false);
        auditDetails.put("commandSemantic", "USER_EXECUTE");
        UUID beginAuditReceipt = auditRollbackAction(
            "INGESTION_ROLLBACK_EXECUTE",
            AuditStage.BEGIN,
            executionPlan,
            auditOperationId,
            "开始执行接入数据回退",
            auditDetails
        );
        ResponseEntity<ApiResponse<Object>> result;
        try {
            result = executeRollback(safeRequest, executionPlan);
        } catch (RuntimeException failure) {
            Map<String, Object> failed = new LinkedHashMap<>(auditDetails);
            failed.put("errorType", failure.getClass().getSimpleName());
            failed.put("operationOutcome", "UNKNOWN");
            failed.put("doNotRetry", true);
            if (failure instanceof ResponseStatusException statusFailure) {
                failed.put("httpStatus", statusFailure.getStatusCode().value());
            }
            terminalRollbackAudit(
                "INGESTION_ROLLBACK_EXECUTE",
                AuditStage.FAIL,
                executionPlan,
                auditOperationId,
                beginAuditReceipt,
                "接入数据回退执行结果待核对",
                failed
            );
            throw failure;
        }
        Map<String, Object> outcome = new LinkedHashMap<>(auditDetails);
        ApiResponse<Object> body = result.getBody();
        String resultCode = body == null ? "" : text(body.getCode());
        outcome.put("httpStatus", result.getStatusCode().value());
        if (!resultCode.isBlank()) {
            outcome.put("resultCode", resultCode);
        }
        copyRollbackReceipt(body, outcome);
        boolean accepted = "ROLLBACK_DISPATCH_PENDING".equals(resultCode) ||
            "ROLLBACK_APPLIED_INVALIDATION_PENDING".equals(resultCode);
        if (!accepted) {
            outcome.put("operationOutcome", "RECONCILIATION_REQUIRED");
            outcome.put("doNotRetry", true);
        }
        terminalRollbackAudit(
            "INGESTION_ROLLBACK_EXECUTE",
            accepted ? AuditStage.SUCCESS : AuditStage.FAIL,
            executionPlan,
            auditOperationId,
            beginAuditReceipt,
            accepted ? "接入数据回退命令已受理" : "接入数据回退结果需要对账",
            outcome
        );
        return result;
    }

    private ResponseEntity<ApiResponse<Object>> executeRollback(
        Map<String, Object> safeRequest,
        RollbackCommand executionPlan
    ) {
        accessDecisionService.requireRollbackAccess(executionPlan, true);
        RollbackConfirmationTokenService.ConfirmedRollbackPlan confirmedPlan = confirmationTokenService.consume(
            executionPlan,
            text(safeRequest.get("confirmationToken")),
            text(safeRequest.get("confirmationType")),
            text(safeRequest.get("confirmationText")),
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );

        if (!org.springframework.util.StringUtils.hasText(confirmedPlan.cascadeDataSourceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回退失效目标无效，请重新分析影响范围");
        }
        String currentCascadeDataSourceId = resolveTrustedCascadeDataSourceId(executionPlan);
        if (!confirmedPlan.cascadeDataSourceId().equals(currentCascadeDataSourceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回退失效目标已变化，请重新分析影响范围");
        }
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        PreparedInvalidation prepared = invalidationService.prepare(
            new PrepareCommand(
                executionPlan,
                UUID.fromString(confirmedPlan.cascadeDataSourceId()),
                operator,
                text(safeRequest.get("confirmationToken"))
            )
        );

        DispatchEnvelope dispatch = invalidationService.claimDispatch(prepared.receiptId()).orElse(null);
        if (dispatch == null) {
            return ResponseEntity
                .status(HttpStatus.MULTI_STATUS)
                .body(
                    accessDecisionService.sanitizeResponse(
                        durableDispatchPending(executionPlan, prepared)
                    )
                );
        }
        ApiResponse<Object> result;
        try {
            result = ingestionClient.rollbackExecute(dispatch.command());
        } catch (RuntimeException transportFailure) {
            try {
                invalidationService.recordDispatchFailure(
                    prepared.receiptId(),
                    dispatch.claimAttempt(),
                    "ROLLBACK_DISPATCH_TRANSPORT_FAILURE",
                    Map.of("transportFailure", transportFailure.getClass().getSimpleName())
                );
            } catch (RuntimeException reconciliationFailure) {
                LOG.error(
                    "[rollback-invalidation] transport and reconciliation persistence are both unconfirmed receiptId={}",
                    prepared.receiptId(),
                    reconciliationFailure
                );
                return ResponseEntity
                    .status(HttpStatus.MULTI_STATUS)
                    .body(
                        accessDecisionService.sanitizeResponse(
                            reconciliationMarkFailed(null, executionPlan, prepared)
                        )
                    );
            }
            return ResponseEntity
                .status(HttpStatus.MULTI_STATUS)
                .body(
                    accessDecisionService.sanitizeResponse(
                        reconciliationRequired(null, executionPlan, prepared)
                    )
                );
        }
        boolean httpSuccessful = result != null && result.getStatus() >= 200 && result.getStatus() < 300;
        if (!httpSuccessful || !rollbackSucceeded(result.getData())) {
            LOG.error(
                "[rollback-execute] downstream did not explicitly confirm data.success=true operator={} plan={}",
                operator,
                executionPlan.toMap()
            );
            try {
                invalidationService.recordDispatchFailure(
                    prepared.receiptId(),
                    dispatch.claimAttempt(),
                    "ROLLBACK_DISPATCH_NOT_CONFIRMED",
                    downstreamSummary(result)
                );
            } catch (RuntimeException reconciliationFailure) {
                LOG.error(
                    "[rollback-invalidation] reconciliation marker not confirmed; current state must be queried receiptId={}",
                    prepared.receiptId(),
                    reconciliationFailure
                );
                return ResponseEntity
                    .status(HttpStatus.MULTI_STATUS)
                    .body(
                        accessDecisionService.sanitizeResponse(
                            reconciliationMarkFailed(result, executionPlan, prepared)
                        )
                    );
            }
            return ResponseEntity
                .status(HttpStatus.MULTI_STATUS)
                .body(
                    accessDecisionService.sanitizeResponse(
                        reconciliationRequired(result, executionPlan, prepared)
                    )
                );
        }

        invalidationService.markDispatchSent(prepared.receiptId(), dispatch.claimAttempt());
        return ResponseEntity
            .status(HttpStatus.MULTI_STATUS)
            .body(
                accessDecisionService.sanitizeResponse(
                    invalidationApplyPending(result, executionPlan, prepared)
                )
            );
    }

    private boolean rollbackSucceeded(Object data) {
        return data instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("success"));
    }

    private ApiResponse<Object> invalidationApplyPending(
        ApiResponse<Object> rollbackResult,
        RollbackCommand executionPlan,
        PreparedInvalidation prepared
    ) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("level", executionPlan.level());
        diagnostic.put("scope", executionPlan.scope());
        diagnostic.put("failedStep", "INVALIDATION_APPLY");
        diagnostic.put("receiptId", prepared.receiptId().toString());
        diagnostic.put("sourceSequence", prepared.sourceSequence());
        diagnostic.put("targetCount", prepared.targetCount());
        if (executionPlan.taskId() != null) {
            diagnostic.put("taskId", executionPlan.taskId());
        }
        if (executionPlan.dataSourceId() != null) {
            diagnostic.put("dataSourceId", executionPlan.dataSourceId().toString());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", "INVALIDATION_PENDING");
        data.put("failedStep", "INVALIDATION_APPLY");
        data.put("retryable", true);
        data.put("manualRecoveryRequired", true);
        data.put("completionPending", true);
        data.put("diagnostic", Map.copyOf(diagnostic));
        if (rollbackResult != null && rollbackResult.getData() != null) {
            data.put("downstreamResult", rollbackResult.getData());
        }
        return new ApiResponse<>(
            HttpStatus.MULTI_STATUS.value(),
            "数据回退已明确完成，但资产失效尚待幂等重试；可使用回执完成对账",
            "ROLLBACK_APPLIED_INVALIDATION_PENDING",
            data
        );
    }

    private ApiResponse<Object> durableDispatchPending(
        RollbackCommand executionPlan,
        PreparedInvalidation prepared
    ) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("level", executionPlan.level());
        diagnostic.put("scope", executionPlan.scope());
        diagnostic.put("receiptId", prepared.receiptId().toString());
        diagnostic.put("sourceSequence", prepared.sourceSequence());
        diagnostic.put("targetCount", prepared.targetCount());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", prepared.state());
        data.put("retryable", true);
        data.put("manualRecoveryRequired", false);
        data.put("dispatchPending", true);
        data.put("completionPending", true);
        data.put("diagnostic", Map.copyOf(diagnostic));
        return new ApiResponse<>(
            HttpStatus.MULTI_STATUS.value(),
            "回退命令已持久化，后台投递器将继续使用同一幂等标识投递",
            "ROLLBACK_DISPATCH_PENDING",
            data
        );
    }

    private ApiResponse<Object> reconciliationRequired(
        ApiResponse<Object> rollbackResult,
        RollbackCommand executionPlan,
        PreparedInvalidation prepared
    ) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("level", executionPlan.level());
        diagnostic.put("scope", executionPlan.scope());
        diagnostic.put("receiptId", prepared.receiptId().toString());
        diagnostic.put("sourceSequence", prepared.sourceSequence());
        diagnostic.put("targetCount", prepared.targetCount());
        if (rollbackResult != null) {
            diagnostic.put("downstreamStatus", rollbackResult.getStatus());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", "RECONCILIATION_REQUIRED");
        data.put("retryable", true);
        data.put("manualRecoveryRequired", true);
        data.put("diagnostic", Map.copyOf(diagnostic));
        return new ApiResponse<>(
            HttpStatus.MULTI_STATUS.value(),
            "回退结果不明确，资产可用性围栏保持关闭，等待可信完成事件对账",
            "ROLLBACK_OUTCOME_RECONCILIATION_REQUIRED",
            data
        );
    }

    private ApiResponse<Object> reconciliationMarkFailed(
        ApiResponse<Object> rollbackResult,
        RollbackCommand executionPlan,
        PreparedInvalidation prepared
    ) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("level", executionPlan.level());
        diagnostic.put("scope", executionPlan.scope());
        diagnostic.put("receiptId", prepared.receiptId().toString());
        diagnostic.put("sourceSequence", prepared.sourceSequence());
        diagnostic.put("targetCount", prepared.targetCount());
        diagnostic.put("reconciliationMarkerPersisted", false);
        if (rollbackResult != null) {
            diagnostic.put("downstreamStatus", rollbackResult.getStatus());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", "UNKNOWN");
        data.put("retryable", true);
        data.put("manualRecoveryRequired", true);
        data.put("diagnostic", Map.copyOf(diagnostic));
        return new ApiResponse<>(
            HttpStatus.MULTI_STATUS.value(),
            "回退结果与资产围栏状态均需重新查询；对账标记写入未获确认",
            "ROLLBACK_RECONCILIATION_MARK_FAILED",
            data
        );
    }

    private Map<String, Object> downstreamSummary(ApiResponse<Object> result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", result == null ? 0 : result.getStatus());
        summary.put("code", result == null || result.getCode() == null ? "" : result.getCode());
        if (result != null && result.getData() instanceof Map<?, ?> data) {
            summary.put("success", Boolean.TRUE.equals(data.get("success")));
            summary.put("sideEffectStatus", text(data.get("sideEffectStatus")));
            if (data.get("sideEffectsApplied") instanceof Boolean sideEffectsApplied) {
                summary.put("sideEffectsApplied", sideEffectsApplied);
            }
            copyDiagnostic(data, summary, "receiptId");
            copyDiagnostic(data, summary, "outcome");
            copyDiagnostic(data, summary, "completionEventId");
            copyDiagnostic(data, summary, "completionPending");
        }
        return Map.copyOf(summary);
    }

    private void copyDiagnostic(Map<?, ?> source, Map<String, Object> target, String field) {
        Object value = source.get(field);
        if (value instanceof String text && !text.isBlank()) {
            target.put(field, text.trim());
        } else if (value instanceof Boolean flag) {
            target.put(field, flag);
        }
    }

    private String resolveTrustedCascadeDataSourceId(RollbackCommand command) {
        if ("datasource".equals(command.scope())) {
            return command.dataSourceId().toString();
        }
        if (!"task".equals(command.scope())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "接入服务返回了不支持的回退范围");
        }
        Map<String, Object> task = accessDecisionService.requireTaskAccess(command.taskId(), true);
        try {
            return UUID.fromString(text(task.get("sourceDataSourceId"))).toString();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "回退任务缺少可信数据源标识");
        }
    }

    private Map<String, Object> rollbackAuditDetails(RollbackCommand command) {
        return rollbackAuditDetails(command, true);
    }

    private Map<String, Object> rollbackAuditDetails(RollbackCommand command, boolean dryRun) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("level", command.level());
        details.put("scope", command.scope());
        details.put("dryRun", dryRun);
        if (command.taskId() != null) {
            details.put("taskId", command.taskId());
        }
        if (command.dataSourceId() != null) {
            details.put("dataSourceId", command.dataSourceId().toString());
        }
        return details;
    }

    private UUID auditRollbackAction(
        String actionCode,
        AuditStage stage,
        RollbackCommand command,
        String auditOperationId,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("auditOperationId", auditOperationId);
        payload.put("operator", SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    payload.put(key, value);
                }
            });
        }
        String resourceId = command.taskId() != null
            ? String.valueOf(command.taskId())
            : command.dataSourceId() != null ? command.dataSourceId().toString() : command.scope();
        return auditService.auditActionStrict(
            actionCode,
            stage,
            resourceId,
            Map.copyOf(payload)
        );
    }

    private void terminalRollbackAudit(
        String actionCode,
        AuditStage stage,
        RollbackCommand command,
        String auditOperationId,
        UUID beginAuditReceipt,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> correlated = new LinkedHashMap<>();
        if (details != null) {
            correlated.putAll(details);
        }
        correlated.put("beginAuditReceipt", beginAuditReceipt == null ? "UNAVAILABLE" : beginAuditReceipt.toString());
        try {
            auditRollbackAction(actionCode, stage, command, auditOperationId, summary, correlated);
        } catch (RuntimeException auditFailure) {
            throw new AuditFinalizationException(auditOperationId, beginAuditReceipt, auditFailure);
        }
    }

    private UUID auditRollbackReceiptAction(
        String actionCode,
        AuditStage stage,
        UUID receiptId,
        String auditOperationId,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("auditOperationId", auditOperationId);
        payload.put("operator", SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    payload.put(key, value);
                }
            });
        }
        return auditService.auditActionStrict(actionCode, stage, receiptId.toString(), Map.copyOf(payload));
    }

    private void auditRollbackReceiptTerminal(
        String actionCode,
        AuditStage stage,
        UUID receiptId,
        String auditOperationId,
        UUID beginAuditReceipt,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> correlated = new LinkedHashMap<>();
        if (details != null) {
            correlated.putAll(details);
        }
        correlated.put("beginAuditReceipt", beginAuditReceipt == null ? "UNAVAILABLE" : beginAuditReceipt.toString());
        try {
            auditRollbackReceiptAction(
                actionCode,
                stage,
                receiptId,
                auditOperationId,
                summary,
                correlated
            );
        } catch (RuntimeException auditFailure) {
            throw new AuditFinalizationException(auditOperationId, beginAuditReceipt, auditFailure);
        }
    }

    private void copyRollbackReceipt(ApiResponse<Object> response, Map<String, Object> target) {
        if (response == null || !(response.getData() instanceof Map<?, ?> data)) {
            return;
        }
        Object diagnosticValue = data.get("diagnostic");
        if (!(diagnosticValue instanceof Map<?, ?> diagnostic)) {
            return;
        }
        Object receiptId = diagnostic.get("receiptId");
        if (receiptId != null && !String.valueOf(receiptId).isBlank()) {
            target.put("invalidationReceiptId", String.valueOf(receiptId));
        }
    }

    private void copyAuditCount(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            target.put(key, number.longValue());
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class AuditFinalizationException extends ResponseStatusException {

        private AuditFinalizationException(String operationId, UUID beginAuditReceipt, RuntimeException cause) {
            super(
                HttpStatus.CONFLICT,
                "回退命令结果已经产生，但审计终态尚未确认；请勿重复执行，请使用 BEGIN 回执核对。operationId=" +
                operationId +
                ", beginReceipt=" +
                beginAuditReceipt,
                cause
            );
        }
    }

    @ExceptionHandler(RollbackInvalidationException.class)
    public ResponseEntity<Map<String, String>> handleRollbackInvalidation(RollbackInvalidationException failure) {
        return ResponseEntity
            .status(failure.status())
            .body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }

    @GetMapping("/receipts/{receiptId}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getReceipt(@PathVariable UUID receiptId) {
        ReceiptView receipt = invalidationService.receipt(receiptId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "ok", receipt));
    }

    @PostMapping("/receipts/{receiptId}/dispatch/replay")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> replayDispatch(@PathVariable UUID receiptId) {
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> beginDetails = new LinkedHashMap<>();
        beginDetails.put("receiptId", receiptId.toString());
        beginDetails.put("commandSemantic", "MANUAL_DISPATCH_REPLAY");
        UUID beginAuditReceipt = auditRollbackReceiptAction(
            "INGESTION_ROLLBACK_REPLAY",
            AuditStage.BEGIN,
            receiptId,
            auditOperationId,
            "开始重新投递接入回退命令",
            beginDetails
        );
        ReceiptView receipt;
        try {
            receipt = invalidationService.replayDispatch(receiptId);
        } catch (RuntimeException failure) {
            Map<String, Object> failed = new LinkedHashMap<>(beginDetails);
            failed.put("errorType", failure.getClass().getSimpleName());
            auditRollbackReceiptTerminal(
                "INGESTION_ROLLBACK_REPLAY",
                AuditStage.FAIL,
                receiptId,
                auditOperationId,
                beginAuditReceipt,
                "重新投递接入回退命令失败",
                failed
            );
            throw failure;
        }
        Map<String, Object> outcome = new LinkedHashMap<>(beginDetails);
        outcome.put("state", receipt.state());
        auditRollbackReceiptTerminal(
            "INGESTION_ROLLBACK_REPLAY",
            AuditStage.SUCCESS,
            receiptId,
            auditOperationId,
            beginAuditReceipt,
            "接入回退命令已重新进入投递队列",
            outcome
        );
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(
                new ApiResponse<>(
                    HttpStatus.ACCEPTED.value(),
                    "回退命令已重新进入持久化投递队列",
                    "ROLLBACK_DISPATCH_REPLAY_QUEUED",
                    receipt
                )
            );
    }

    @GetMapping("/audit-log")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getAuditLog(
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) UUID dataSourceId
    ) {
        accessDecisionService.requireRollbackAuditAccess(taskId, dataSourceId);
        ApiResponse<Object> result = ingestionClient.getRollbackAuditLog(taskId, dataSourceId);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(result));
    }
}
