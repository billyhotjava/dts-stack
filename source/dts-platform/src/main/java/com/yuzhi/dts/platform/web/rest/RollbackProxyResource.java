package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.etl.RollbackCascadeService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ingestion.RollbackCommand;
import com.yuzhi.dts.platform.service.ingestion.RollbackConfirmationTokenService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final RollbackCascadeService cascadeService;
    private final RollbackConfirmationTokenService confirmationTokenService;
    private final IngestionAccessDecisionService accessDecisionService;

    public RollbackProxyResource(
        IngestionServiceClient ingestionClient,
        RollbackCascadeService cascadeService,
        RollbackConfirmationTokenService confirmationTokenService,
        IngestionAccessDecisionService accessDecisionService
    ) {
        this.ingestionClient = ingestionClient;
        this.cascadeService = cascadeService;
        this.confirmationTokenService = confirmationTokenService;
        this.accessDecisionService = accessDecisionService;
    }

    @PostMapping("/analyze")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> analyze(@RequestBody Map<String, Object> request) {
        RollbackCommand executionPlan = RollbackCommand.executionPlan(request, false);
        accessDecisionService.requireRollbackAccess(executionPlan, true);
        ApiResponse<Object> result = ingestionClient.rollbackAnalyze(executionPlan.asAnalysisCommand().toMap());
        if (result == null || result.getStatus() < 200 || result.getStatus() >= 300) {
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
        return ResponseEntity.ok(new ApiResponse<>(result.getStatus(), result.getMessage(), result.getCode(), confirmedImpact));
    }

    @PostMapping("/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> execute(@RequestBody Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        RollbackCommand executionPlan = RollbackCommand.executionPlan(safeRequest, true);
        accessDecisionService.requireRollbackAccess(executionPlan, true);
        RollbackConfirmationTokenService.ConfirmedRollbackPlan confirmedPlan = confirmationTokenService.consume(
            executionPlan,
            text(safeRequest.get("confirmationToken")),
            text(safeRequest.get("confirmationType")),
            text(safeRequest.get("confirmationText")),
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );

        int level = executionPlan.level();
        if (level == 3 && !org.springframework.util.StringUtils.hasText(confirmedPlan.cascadeDataSourceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回退级联目标无效，请重新分析影响范围");
        }
        if (level == 3) {
            String currentCascadeDataSourceId = resolveTrustedCascadeDataSourceId(executionPlan);
            if (!confirmedPlan.cascadeDataSourceId().equals(currentCascadeDataSourceId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "回退级联目标已变化，请重新分析影响范围");
            }
        }
        Map<String, Object> command = executionPlan.toMap();
        ApiResponse<Object> result = ingestionClient.rollbackExecute(command);
        boolean successfulExecution = result != null && result.getStatus() >= 200 && result.getStatus() < 300;

        // After successful execute, trigger platform-side cascade cleanup for Level 3
        if (level == 3 && successfulExecution) {
            try {
                Map<String, Object> cascadeCommand = new LinkedHashMap<>(command);
                cascadeCommand.put("sourceDataSourceId", confirmedPlan.cascadeDataSourceId());
                cascadeService.cascadeCleanup(cascadeCommand, result.getData());
            } catch (Exception ex) {
                LOG.error("[rollback-cascade] platform cleanup partially failed", ex);
                return ResponseEntity.ok(
                    partialFailure(result, executionPlan, "CASCADE_CLEANUP", confirmedPlan.cascadeDataSourceId(), ex)
                );
            }
        }

        // If Level 2 with rebuildDbt, trigger dbt full-refresh
        if (level == 2 && executionPlan.rebuildDbt() && successfulExecution) {
            try {
                cascadeService.triggerDbtFullRefresh(command);
            } catch (Exception ex) {
                LOG.error("[rollback-cascade] dbt full-refresh trigger failed", ex);
                return ResponseEntity.ok(partialFailure(result, executionPlan, "DBT_FULL_REFRESH", null, ex));
            }
        }

        return ResponseEntity.ok(result);
    }

    private ApiResponse<Object> partialFailure(
        ApiResponse<Object> rollbackResult,
        RollbackCommand executionPlan,
        String failedStep,
        String cascadeDataSourceId,
        Exception failure
    ) {
        Map<String, Object> retryContext = new LinkedHashMap<>(executionPlan.toMap());
        if (org.springframework.util.StringUtils.hasText(cascadeDataSourceId)) {
            retryContext.put("sourceDataSourceId", cascadeDataSourceId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("state", "PARTIAL_FAILED");
        data.put("failedStep", failedStep);
        data.put("retryable", true);
        data.put("retryContext", Map.copyOf(retryContext));
        data.put("rollbackResult", rollbackResult == null ? null : rollbackResult.getData());
        data.put("failureType", failure.getClass().getSimpleName());
        return new ApiResponse<>(
            HttpStatus.MULTI_STATUS.value(),
            "数据回退已完成，但后续步骤失败，可按 retryContext 重试失败步骤",
            "PARTIAL_FAILED",
            data
        );
    }

    private String resolveTrustedCascadeDataSourceId(RollbackCommand command) {
        if (command.level() != 3) {
            return null;
        }
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @GetMapping("/audit-log")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getAuditLog(
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) UUID dataSourceId
    ) {
        accessDecisionService.requireRollbackAuditAccess(taskId, dataSourceId);
        ApiResponse<Object> result = ingestionClient.getRollbackAuditLog(taskId, dataSourceId);
        return ResponseEntity.ok(result);
    }
}
