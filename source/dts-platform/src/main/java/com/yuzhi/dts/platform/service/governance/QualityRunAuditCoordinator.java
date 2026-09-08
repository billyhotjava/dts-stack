package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates quality-run completion audit and automatic issue lifecycle. */
final class QualityRunAuditCoordinator {

    private static final Logger log = LoggerFactory.getLogger(QualityRunAuditCoordinator.class);

    private final QualityAuditRecorder auditRecorder;
    private final IssueTicketService issueTicketService;

    QualityRunAuditCoordinator(QualityAuditRecorder auditRecorder, IssueTicketService issueTicketService) {
        this.auditRecorder = auditRecorder;
        this.issueTicketService = issueTicketService;
    }

    void createIssueForFailedRun(GovQualityRun run, List<StatementExecutionResult> results, String actor) {
        if (run == null || run.getId() == null) {
            return;
        }
        IssueTicketUpsertRequest request = new IssueTicketUpsertRequest();
        String ruleName = resolveRunRuleName(run);
        request.setTitle("质量检测失败：" + ruleName);
        StringBuilder summary = new StringBuilder();
        summary.append("规则：").append(ruleName);
        if (run.getDatasetId() != null) {
            summary.append("\n数据集：").append(run.getDatasetId());
        }
        summary.append("\n原因：质量检测执行失败（类别：")
            .append(StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_ERROR"))
            .append("）");
        if (results != null && !results.isEmpty()) {
            long failed = results.stream().filter(r -> r != null && r.status() == StatementExecutionResult.Status.FAILED).count();
            summary.append("\n失败项数：").append(failed);
        }
        request.setSummary(summary.toString());
        request.setSeverity(run.getSeverity());
        request.setDataLevel(run.getDataLevel());
        request.setDatasetId(run.getDatasetId());
        request.setOwner(run.getRule() != null ? run.getRule().getOwner() : null);
        request.setTags(List.of(
            "QUALITY_RUN",
            "trigger=" + String.valueOf(run.getTriggerType()),
            "datasetId=" + String.valueOf(run.getDatasetId()),
            "runId=" + run.getId(),
            "workflowId=" + String.valueOf(run.getJobId())
        ));
        String effectiveActor = StringUtils.isNotBlank(actor) ? actor : "system";
        String issueActor = "SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))
            ? "system"
            : effectiveActor;
        IssueTicketService.CreateOrTouchResult result;
        try {
            result = issueTicketService.createOrTouchQualityProblem(
                QualityIssueIdentity.problemKey(run),
                run.getId(),
                request,
                issueActor,
                failureEvidenceNote(run)
            );
        } catch (Exception ex) {
            auditAutomaticIssueFailure(run, effectiveActor, ex);
            log.warn(
                "event=quality_run_issue_create_failed runId={} errorType={}",
                run.getId(),
                ex.getClass().getSimpleName()
            );
            return;
        }
        IssueTicketDto issue = result != null ? result.ticket() : null;
        if (issue != null && issue.getId() != null) {
            auditAutomaticIssue(run, issue.getId(), effectiveActor, result.disposition());
        }
    }

    void resolveIssuesForSuccessfulRun(GovQualityRun run, String actor) {
        if (run == null || run.getId() == null) {
            return;
        }
        String effectiveActor = StringUtils.defaultIfBlank(actor, "quality-workflow");
        try {
            List<UUID> issueIds = issueTicketService.resolveQualityProblems(run, effectiveActor);
            for (UUID issueId : issueIds) {
                auditAutomaticRecovery(run, issueId, effectiveActor);
            }
        } catch (Exception ex) {
            log.warn(
                "event=quality_run_issue_recovery_failed runId={} errorType={}",
                run.getId(),
                ex.getClass().getSimpleName()
            );
        }
    }

    private String failureEvidenceNote(GovQualityRun run) {
        StringBuilder note = new StringBuilder("系统自动追加：质量验证未通过；运行编号=").append(run.getId());
        if (run.getJobId() != null) {
            note.append("；工作流编号=").append(run.getJobId());
        }
        note.append("；错误分类=")
            .append(StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_ERROR"));
        return note.toString();
    }

    private void auditAutomaticRecovery(GovQualityRun run, UUID issueId, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "质量问题自动恢复");
        payload.put("issueId", issueId.toString());
        payload.put("qualityRunId", run.getId().toString());
        if (run.getJobId() != null) {
            payload.put("workflowId", run.getJobId().toString());
        }
        payload.put("triggerActor", actor);
        String machineActor = runMachineAuditActor(run);
        if (machineActor != null) {
            auditRecorder.recordMachine(
                machineActor,
                runEventIdentity(run, AuditStage.SUCCESS) + ":ISSUE:RECOVERED:" + issueId,
                run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now(),
                "GOV_ISSUE_ACTION_APPEND",
                AuditStage.SUCCESS,
                issueId.toString(),
                payload
            );
        } else {
            auditRecorder.recordAction(
                "GOV_ISSUE_ACTION_APPEND",
                AuditStage.SUCCESS,
                issueId.toString(),
                payload
            );
        }
    }

    private void auditAutomaticIssue(
        GovQualityRun run,
        UUID issueId,
        String actor,
        IssueTicketService.CreateOrTouchDisposition disposition
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "自动创建质量问题");
            payload.put("sourceType", "QUALITY_RUN");
            payload.put("sourceId", run.getId().toString());
            payload.put("issueId", issueId.toString());
            if (run.getDatasetId() != null) {
                payload.put("datasetId", run.getDatasetId().toString());
            }
            payload.put("disposition", disposition.name());
            payload.put("triggerActor", actor);
            String actionCode = disposition == IssueTicketService.CreateOrTouchDisposition.CREATED
                ? "GOV_ISSUE_CREATE"
                : "GOV_ISSUE_ACTION_APPEND";
            String machineActor = runMachineAuditActor(run);
            if (machineActor != null) {
                Instant occurredAt = run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now();
                auditRecorder.recordMachine(
                    machineActor,
                    runEventIdentity(run, AuditStage.FAIL) + ":ISSUE:" + disposition.name(),
                    occurredAt,
                    actionCode,
                    AuditStage.SUCCESS,
                    issueId.toString(),
                    payload
                );
                return;
            }
            auditRecorder.recordAction(actionCode, AuditStage.SUCCESS, issueId.toString(), payload);
        } catch (RuntimeException ex) {
            throw new QualityRunAuditWriteException(ex);
        }
    }

    private void auditAutomaticIssueFailure(GovQualityRun run, String actor, Exception failure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "自动创建质量问题失败");
        payload.put("sourceType", "QUALITY_RUN");
        payload.put("sourceId", run.getId().toString());
        if (run.getDatasetId() != null) {
            payload.put("datasetId", run.getDatasetId().toString());
        }
        payload.put("triggerActor", actor);
        payload.put("errorType", failure.getClass().getSimpleName());
        payload.put("errorCategory", "ISSUE_PERSISTENCE_FAILED");
        try {
            String machineActor = runMachineAuditActor(run);
            if (machineActor != null) {
                Instant occurredAt = run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now();
                auditRecorder.recordMachineAttempt(
                    machineActor,
                    runEventIdentity(run, AuditStage.FAIL) + ":ISSUE:FAIL",
                    occurredAt,
                    "GOV_ISSUE_CREATE",
                    AuditStage.FAIL,
                    run.getId().toString(),
                    payload
                );
            } else {
                auditRecorder.recordFailureAction("GOV_ISSUE_CREATE", run.getId().toString(), payload);
            }
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_run_issue_failure_audit_write_failed runId={} errorType={}",
                run.getId(),
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    String resolveRunRuleName(GovQualityRun run) {
        if (run == null) {
            return "未知规则";
        }
        GovRule rule = run.getRule();
        if (rule != null) {
            return resolveRuleName(rule);
        }
        GovRuleVersion version = run.getRuleVersion();
        if (version != null && version.getRule() != null) {
            return resolveRuleName(version.getRule());
        }
        return run.getId() != null ? run.getId().toString() : "未知规则";
    }

    String resolveRunActor(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        if ("SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
            return "scheduler";
        }
        if (StringUtils.isNotBlank(run.getCreatedBy())) {
            return run.getCreatedBy();
        }
        if (StringUtils.isNotBlank(run.getLastModifiedBy())) {
            return run.getLastModifiedBy();
        }
        // Legacy manual runs stored the actor here; current workflows store correlation keys.
        String triggerRef = StringUtils.trimToNull(run.getTriggerRef());
        if (triggerRef != null && triggerRef.length() <= 64 && !triggerRef.startsWith("mcq:")) {
            return triggerRef;
        }
        return null;
    }

    void auditRunCompletion(GovQualityRun run, AuditStage stage, Map<String, Object> payload) {
        try {
            String resourceId = run != null && run.getId() != null ? run.getId().toString() : "UNASSIGNED";
            String machineActor = runMachineAuditActor(run);
            if (machineActor != null) {
                Map<String, Object> machinePayload = new LinkedHashMap<>(payload);
                machinePayload.putAll(buildRunAuditTags(run));
                Instant occurredAt = run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now();
                auditRecorder.recordMachine(
                    machineActor,
                    runEventIdentity(run, stage),
                    occurredAt,
                    "GOV_QUALITY_RUN_EXECUTE",
                    stage,
                    resourceId,
                    machinePayload
                );
                return;
            }
            Map<String, Object> actorPayload = new LinkedHashMap<>(payload);
            actorPayload.putAll(buildRunAuditTags(run));
            actorPayload.put("triggerActor", resolveRunActor(run));
            auditRecorder.recordAction("GOV_QUALITY_RUN_EXECUTE", stage, resourceId, actorPayload);
        } catch (RuntimeException ex) {
            throw new QualityRunAuditWriteException(ex);
        }
    }

    String runMachineAuditActor(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        if ("SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
            return "scheduler";
        }
        if ("INGESTION".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
            return "ingestion";
        }
        return null;
    }

    String runEventIdentity(GovQualityRun run, AuditStage stage) {
        String runId = run != null && run.getId() != null ? run.getId().toString() : "UNASSIGNED";
        return "quality-run:" + runId + ":" + stage.name();
    }

    Map<String, Object> buildRunAuditPayload(GovQualityRun run, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        if (run != null) {
            if (run.getId() != null) {
                payload.put("runId", run.getId().toString());
            }
            GovRule rule = run.getRule();
            if (rule != null && rule.getId() != null) {
                payload.put("ruleId", rule.getId().toString());
                payload.put("ruleName", resolveRuleName(rule));
            } else if (run.getRuleVersion() != null && run.getRuleVersion().getRule() != null) {
                GovRule versionRule = run.getRuleVersion().getRule();
                if (versionRule.getId() != null) {
                    payload.put("ruleId", versionRule.getId().toString());
                }
                payload.put("ruleName", resolveRuleName(versionRule));
            }
            payload.putIfAbsent("ruleName", resolveRunRuleName(run));
        }
        return payload;
    }

    private String resolveRuleName(GovRule rule) {
        if (rule == null) {
            return "未知规则";
        }
        if (StringUtils.isNotBlank(rule.getName())) {
            return rule.getName();
        }
        if (StringUtils.isNotBlank(rule.getCode())) {
            return rule.getCode();
        }
        return rule.getId() != null ? rule.getId().toString() : "未知规则";
    }

    private Map<String, Object> buildRunAuditTags(GovQualityRun run) {
        if (run == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> tags = new LinkedHashMap<>();
        if (run.getId() != null) {
            tags.put("qualityRunId", run.getId().toString());
        }
        GovRule rule = run.getRule();
        if (rule != null && rule.getId() != null) {
            tags.put("qualityRuleId", rule.getId().toString());
        }
        GovRuleVersion version = run.getRuleVersion();
        if (version != null && version.getId() != null) {
            tags.put("qualityRuleVersionId", version.getId().toString());
        }
        if (run.getDatasetId() != null) {
            tags.put("datasetId", run.getDatasetId().toString());
        }
        if (StringUtils.isNotBlank(run.getSeverity())) {
            tags.put("severity", run.getSeverity());
        }
        if (StringUtils.isNotBlank(run.getTriggerType())) {
            tags.put("triggerType", run.getTriggerType());
        }
        if (StringUtils.isNotBlank(run.getStatus())) {
            tags.put("status", run.getStatus());
        }
        return tags.isEmpty() ? Collections.emptyMap() : tags;
    }
}
