package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.governance.dto.QualityMetricDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QualityRunService {

    private static final Logger log = LoggerFactory.getLogger(QualityRunService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final GovQualityRunRepository runRepository;
    private final GovQualityMetricRepository metricRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final Executor taskExecutor;
    private final QualityDatasetStatementExecutor statementExecutor;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final IssueTicketService issueTicketService;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;
    private final TransactionTemplate runTransactionTemplate;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final QualityDatasetReadGuard qualityDatasetReadGuard;

    public QualityRunService(
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        GovRuleBindingRepository bindingRepository,
        GovQualityRunRepository runRepository,
        GovQualityMetricRepository metricRepository,
        CatalogDatasetRepository datasetRepository,
        @Qualifier("taskExecutor") Executor taskExecutor,
        QualityDatasetStatementExecutor statementExecutor,
        QualityAuditRecorder qualityAuditRecorder,
        IssueTicketService issueTicketService,
        ObjectMapper objectMapper,
        GovernanceProperties properties,
        PlatformTransactionManager transactionManager,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        QualityDatasetReadGuard qualityDatasetReadGuard
    ) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.bindingRepository = bindingRepository;
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.datasetRepository = datasetRepository;
        this.taskExecutor = taskExecutor;
        this.statementExecutor = statementExecutor;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.issueTicketService = issueTicketService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.qualityDatasetReadGuard = qualityDatasetReadGuard;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.runTransactionTemplate = template;
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor) {
        return triggerInternal(request, actor, null, false, false);
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor, String activeDeptHeader) {
        return triggerInternal(request, actor, activeDeptHeader, true, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerAuthorizedIndependent(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader
    ) {
        return triggerInternal(request, actor, activeDeptHeader, true, false);
    }

    @Transactional
    public List<QualityRunDto> triggerScheduled(QualityRunTriggerRequest request) {
        return triggerInternal(request, "scheduler", null, false, true);
    }

    private List<QualityRunDto> triggerInternal(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        boolean enforceUserAccess,
        boolean scheduledInvocation
    ) {
        if (!properties.getQuality().isEnabled()) {
            throw new IllegalStateException("质量检测功能已禁用");
        }
        if (request == null) {
            throw new IllegalArgumentException("质量运行请求不能为空");
        }
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        String triggerType = resolveTriggerType(request.getTriggerType(), dryRun, scheduledInvocation);
        GovRule rule = resolveRule(request.getRuleId());
        GovRuleVersion version = resolveVersion(rule);
        if (resolveStatements(version).isEmpty()) {
            throw new IllegalArgumentException("质量规则未配置可执行检测语句，请先编辑并发布规则");
        }
        List<GovRuleBinding> bindings = resolveBindings(version, request.getBindingId(), request.getDatasetId());
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("该规则尚未绑定数据集");
        }
        bindings.forEach(binding -> {
            if (enforceUserAccess) {
                qualityDatasetReadGuard.requireReadable(binding.getDatasetId(), activeDeptHeader);
            } else {
                defaultLakeDatasetGuard.requireDefaultLakeDataset(binding.getDatasetId());
            }
        });

        Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();
        List<QualityRunDto> runs = new ArrayList<>();
        List<UUID> runIds = new ArrayList<>();
        for (GovRuleBinding binding : bindings) {
            GovQualityRun run = new GovQualityRun();
            run.setRule(rule);
            run.setRuleVersion(version);
            run.setBinding(binding);
            run.setDatasetId(binding.getDatasetId());
            run.setTriggerType(triggerType);
            run.setTriggerRef(actor);
            run.setStatus("QUEUED");
            run.setSeverity(rule.getSeverity());
            run.setDataLevel(rule.getDataLevel());
            run.setScheduledAt(Instant.now());
            run.setInputParamsJson(writeJson(params));
            runRepository.save(run);

            Map<String, Object> beginPayload = buildRunAuditPayload(run, "开始运行质量规则：" + resolveRunRuleName(run));
            beginPayload.put("status", run.getStatus());
            if ("SCHEDULED".equalsIgnoreCase(run.getTriggerType())) {
                qualityAuditRecorder.recordMachine(
                    "scheduler",
                    runEventIdentity(run, AuditStage.BEGIN),
                    run.getScheduledAt(),
                    "GOV_QUALITY_RUN_EXECUTE",
                    AuditStage.BEGIN,
                    run.getId().toString(),
                    beginPayload
                );
            } else {
                qualityAuditRecorder.recordAction(
                    "GOV_QUALITY_RUN_EXECUTE",
                    AuditStage.BEGIN,
                    run.getId().toString(),
                    beginPayload
                );
            }

            runIds.add(run.getId());
            runs.add(toSafeDto(run, Collections.emptyList()));
        }

        if (!runIds.isEmpty()) {
            List<UUID> dispatchIds = List.copyOf(runIds);
            if (dryRun) {
                dispatchIds.forEach(id -> doExecuteRun(id, params));
            } else {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchIds.forEach(id -> dispatchOne(id, params));
                        }
                    });
                } else {
                    dispatchIds.forEach(id -> dispatchOne(id, params));
                }
            }
        }
        if (dryRun) {
            return dispatchIdsToDtos(runIds);
        }
        return runs;
    }

    private void dispatchOne(UUID runId, Map<String, Object> params) {
        try {
            taskExecutor.execute(() ->
                runTransactionTemplate.executeWithoutResult(status -> doExecuteRun(runId, params))
            );
        } catch (RejectedExecutionException ex) {
            log.warn("event=quality_run_dispatch_rejected runId={}", runId);
            runTransactionTemplate.executeWithoutResult(status -> markDispatchRejected(runId));
        }
    }

    private void markDispatchRejected(UUID runId) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        Instant finishedAt = Instant.now();
        run.setStatus("FAILED");
        run.setFinishedAt(finishedAt);
        run.setMessage("质量检测任务分发失败");
        run.setErrorCategory("DISPATCH_REJECTED");
        run.setDurationMs(0L);
        runRepository.save(run);

        Map<String, Object> payload = buildRunAuditPayload(run, "分发质量检测任务失败：" + resolveRunRuleName(run));
        payload.put("status", run.getStatus());
        payload.put("errorCategory", run.getErrorCategory());
        auditRunCompletion(run, AuditStage.FAIL, payload);
    }

    @Transactional(readOnly = true)
    public QualityRunDto getRun(UUID runId) {
        return getRun(runId, null);
    }

    @Transactional(readOnly = true)
    public QualityRunDto getRun(UUID runId, String activeDeptHeader) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        requireReadableRun(run, activeDeptHeader);
        List<GovQualityMetric> metrics = metricRepository.findByRunId(runId);
        return toSafeDto(run, metrics);
    }

    @Transactional(readOnly = true)
    public void assertRunReadable(UUID runId, String activeDeptHeader) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        requireReadableRun(run, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByRule(UUID ruleId, int limit) {
        return recentByRule(ruleId, limit, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByRule(UUID ruleId, int limit, String activeDeptHeader) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return filterReadableRuns(runRepository.findByRuleId(ruleId, pageable), activeDeptHeader)
            .stream()
            .map(run -> toSafeDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByDataset(UUID datasetId, int limit) {
        return recentByDataset(datasetId, limit, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByDataset(UUID datasetId, int limit, String activeDeptHeader) {
        qualityDatasetReadGuard.requireReadable(datasetId, activeDeptHeader);
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return runRepository
            .findByDatasetId(datasetId, pageable)
            .stream()
            .map(run -> toSafeDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recent(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return filterReadableRuns(runRepository.findAll(pageable).getContent(), null)
            .stream()
            .map(run -> toSafeDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> listRuns(UUID ruleId, UUID datasetId, String status, String triggerType, Instant startedFrom, Instant startedTo, int limit) {
        return listRuns(ruleId, datasetId, status, triggerType, startedFrom, startedTo, limit, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> listRuns(
        UUID ruleId,
        UUID datasetId,
        String status,
        String triggerType,
        Instant startedFrom,
        Instant startedTo,
        int limit,
        String activeDeptHeader
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int querySize = Math.max(safeLimit, 200);
        Pageable pageable = PageRequest.of(0, querySize, Sort.Direction.DESC, "createdDate");
        List<GovQualityRun> candidates;
        if (ruleId != null) {
            candidates = runRepository.findByRuleId(ruleId, pageable);
        } else if (datasetId != null) {
            qualityDatasetReadGuard.requireReadable(datasetId, activeDeptHeader);
            candidates = runRepository.findByDatasetId(datasetId, pageable);
        } else {
            candidates = runRepository.findAll(pageable).getContent();
        }
        String normalizedStatus = StringUtils.trimToNull(status);
        String normalizedTriggerType = StringUtils.trimToNull(triggerType);
        return filterReadableRuns(candidates, activeDeptHeader)
            .stream()
            .filter(run -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(StringUtils.trimToEmpty(run.getStatus())))
            .filter(run -> normalizedTriggerType == null || normalizedTriggerType.equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType())))
            .filter(run -> {
                Instant pivot = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedDate();
                if (startedFrom != null && (pivot == null || pivot.isBefore(startedFrom))) {
                    return false;
                }
                return startedTo == null || pivot == null || !pivot.isAfter(startedTo);
            })
            .limit(safeLimit)
            .map(run -> toSafeDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    private void doExecuteRun(UUID runId, Map<String, Object> params) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        Instant start = Instant.now();
        run.setStatus("RUNNING");
        run.setStartedAt(start);
        run.setMessage("正在执行质量检测");
        runRepository.save(run);

        try {
            Map<String, String> statements = resolveStatements(run.getRuleVersion());
            if (statements.isEmpty()) {
                run.setStatus("SKIPPED");
                run.setFinishedAt(Instant.now());
                run.setMessage("未配置检测语句");
                run.setErrorCategory(null);
                runRepository.save(run);
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("statementCount", 0);
                auditRunCompletion(run, AuditStage.SUCCESS, payload);
                return;
            }

            Map<String, String> rendered = renderParams(statements, params);
            QualityDatasetStatementExecutor.Execution execution = statementExecutor.execute(run, rendered);
            List<StatementExecutionResult> results = execution.results();
            run.setRowsTotal(execution.rowsTotal());
            run.setFailingRowCount(execution.failingRowCount());
            persistMetrics(run, results);
            StatementExecutionResult.Status aggregate = aggregateStatus(results);
            run.setStatus(mapStatus(aggregate));
            run.setMessage(summaryMessage(results));
            run.setErrorCategory(resolveErrorCategory(results));
            run.setFinishedAt(Instant.now());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            run.setMetricsJson(writeSafeMetrics(results));
            runRepository.save(run);
            if (aggregate == StatementExecutionResult.Status.FAILED) {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("statementCount", results.size());
                payload.put("failedStatementCount", failedStatementCount(results));
                payload.put("errorCategory", StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_FAILED"));
                auditRunCompletion(run, AuditStage.FAIL, payload);
                if (!isDryRun(run)) {
                    createIssueForFailedRun(run, results, resolveRunActor(run));
                }
            } else {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("statementCount", results.size());
                auditRunCompletion(run, AuditStage.SUCCESS, payload);
            }
        } catch (QualityRunAuditWriteException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error(
                "event=quality_run_execution_failed runId={} errorType={}",
                runId,
                ex.getClass().getSimpleName()
            );
            run.setStatus("FAILED");
            run.setFinishedAt(Instant.now());
            run.setMessage("质量检测执行失败");
            run.setErrorCategory(resolveErrorCategory(ex.getMessage()));
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            runRepository.save(run);
            Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
            payload.put("status", run.getStatus());
            payload.put("errorType", ex.getClass().getSimpleName());
            payload.put("errorCategory", StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_FAILED"));
            auditRunCompletion(run, AuditStage.FAIL, payload);
            if (!isDryRun(run)) {
                createIssueForFailedRun(run, null, resolveRunActor(run));
            }
        }
    }

    private boolean isDryRun(GovQualityRun run) {
        return run != null && "DRY_RUN".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()));
    }

    private String resolveTriggerType(String requestedTriggerType, boolean dryRun, boolean scheduledInvocation) {
        String triggerType = StringUtils.defaultIfBlank(requestedTriggerType, "MANUAL").trim();
        if ("SCHEDULED".equalsIgnoreCase(triggerType) && !scheduledInvocation) {
            throw new IllegalArgumentException("人工质量运行不允许使用调度触发类型");
        }
        if (dryRun) {
            if (scheduledInvocation) {
                throw new IllegalArgumentException("调度质量运行不允许试跑模式");
            }
            return "DRY_RUN";
        }
        if (scheduledInvocation && !"SCHEDULED".equalsIgnoreCase(triggerType)) {
            throw new IllegalArgumentException("调度入口仅允许使用调度触发类型");
        }
        return triggerType;
    }

    private List<QualityRunDto> dispatchIdsToDtos(List<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Collections.emptyList();
        }
        return runIds
            .stream()
            .map(runRepository::findById)
            .flatMap(Optional::stream)
            .map(run -> toSafeDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    private void createIssueForFailedRun(GovQualityRun run, List<StatementExecutionResult> results, String actor) {
        if (run == null || run.getId() == null) {
            return;
        }
        IssueTicketUpsertRequest req = new IssueTicketUpsertRequest();
        String ruleName = resolveRunRuleName(run);
        req.setTitle("质量检测失败：" + ruleName);
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
        req.setSummary(summary.toString());
        req.setSeverity(run.getSeverity());
        req.setDataLevel(run.getDataLevel());
        req.setDatasetId(run.getDatasetId());
        req.setOwner(run.getRule() != null ? run.getRule().getOwner() : null);
        req.setTags(List.of(
            "QUALITY_RUN",
            "trigger=" + String.valueOf(run.getTriggerType()),
            "datasetId=" + String.valueOf(run.getDatasetId())
        ));
        String effectiveActor = StringUtils.isNotBlank(actor) ? actor : "system";
        String issueActor = "SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))
            ? "system"
            : effectiveActor;
        IssueTicketService.CreateOrTouchResult result;
        try {
            result = issueTicketService.createOrTouchWithDisposition(
                "QUALITY_RUN",
                run.getId(),
                req,
                issueActor,
                "系统自动生成：质量检测失败"
            );
        } catch (Exception ex) {
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
            if ("SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
                Instant occurredAt = run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now();
                qualityAuditRecorder.recordMachine(
                    "scheduler",
                    runEventIdentity(run, AuditStage.FAIL) + ":ISSUE:" + disposition.name(),
                    occurredAt,
                    actionCode,
                    AuditStage.SUCCESS,
                    issueId.toString(),
                    payload
                );
                return;
            }
            qualityAuditRecorder.recordAction(
                actionCode,
                AuditStage.SUCCESS,
                issueId.toString(),
                payload
            );
        } catch (RuntimeException ex) {
            throw new QualityRunAuditWriteException(ex);
        }
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

    private String resolveRunRuleName(GovQualityRun run) {
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

    private String resolveRunActor(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        if ("SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
            return "scheduler";
        }
        if (StringUtils.isNotBlank(run.getTriggerRef())) {
            return run.getTriggerRef();
        }
        if (StringUtils.isNotBlank(run.getCreatedBy())) {
            return run.getCreatedBy();
        }
        if (StringUtils.isNotBlank(run.getLastModifiedBy())) {
            return run.getLastModifiedBy();
        }
        return null;
    }

    private void auditRunCompletion(GovQualityRun run, AuditStage stage, Map<String, Object> payload) {
        try {
            String resourceId = run != null && run.getId() != null ? run.getId().toString() : "UNASSIGNED";
            if (run != null && "SCHEDULED".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()))) {
                Map<String, Object> machinePayload = new LinkedHashMap<>(payload);
                machinePayload.putAll(buildRunAuditTags(run));
                Instant occurredAt = run.getFinishedAt() != null ? run.getFinishedAt() : Instant.now();
                qualityAuditRecorder.recordMachine(
                    "scheduler",
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
            qualityAuditRecorder.recordAction(
                "GOV_QUALITY_RUN_EXECUTE",
                stage,
                resourceId,
                actorPayload
            );
        } catch (RuntimeException ex) {
            throw new QualityRunAuditWriteException(ex);
        }
    }

    private String runEventIdentity(GovQualityRun run, AuditStage stage) {
        String runId = run != null && run.getId() != null ? run.getId().toString() : "UNASSIGNED";
        return "quality-run:" + runId + ":" + stage.name();
    }

    private Map<String, Object> buildRunAuditPayload(GovQualityRun run, String summary) {
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
                GovRule vrule = run.getRuleVersion().getRule();
                if (vrule.getId() != null) {
                    payload.put("ruleId", vrule.getId().toString());
                }
                payload.put("ruleName", resolveRuleName(vrule));
            }
            payload.putIfAbsent("ruleName", resolveRunRuleName(run));
        }
        return payload;
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

    private void requireReadableRun(GovQualityRun run, String activeDeptHeader) {
        if (run == null || run.getDatasetId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("质量运行缺少可授权的数据集");
        }
        qualityDatasetReadGuard.requireReadable(run.getDatasetId(), activeDeptHeader);
    }

    private List<GovQualityRun> filterReadableRuns(List<GovQualityRun> runs, String activeDeptHeader) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> datasetIds = runs
            .stream()
            .map(GovQualityRun::getDatasetId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (datasetIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> readableIds = qualityDatasetReadGuard.readableDatasetIds(
            datasetRepository.findAllById(datasetIds),
            activeDeptHeader
        );
        return runs.stream().filter(run -> readableIds.contains(run.getDatasetId())).collect(Collectors.toList());
    }

    private GovRule resolveRule(UUID ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("缺少规则ID");
        }
        GovRule rule = ruleRepository.findById(ruleId).orElseThrow(EntityNotFoundException::new);
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw new IllegalStateException("质量规则已停用，无法执行");
        }
        return rule;
    }

    private GovRuleVersion resolveVersion(GovRule rule) {
        GovRuleVersion version = rule.getLatestVersion();
        if (version != null && !"PUBLISHED".equalsIgnoreCase(StringUtils.trimToEmpty(version.getStatus()))) {
            version = null;
        }
        if (version == null) {
            version = versionRepository.findFirstByRuleIdAndStatusOrderByVersionDesc(rule.getId(), "PUBLISHED").orElse(null);
        }
        if (version == null) {
            throw new IllegalStateException("规则尚无可执行的已发布版本");
        }
        return version;
    }

    private List<GovRuleBinding> resolveBindings(GovRuleVersion version, UUID bindingId, UUID datasetId) {
        if (bindingId != null) {
            Optional<GovRuleBinding> binding = bindingRepository.findById(bindingId);
            GovRuleBinding entity = binding.orElseThrow(() -> new IllegalArgumentException("未找到绑定"));
            if (entity.getRuleVersion() == null || !entity.getRuleVersion().getId().equals(version.getId())) {
                throw new IllegalArgumentException("绑定与规则版本不匹配");
            }
            if (datasetId != null && entity.getDatasetId() != null && !datasetId.equals(entity.getDatasetId())) {
                throw new IllegalArgumentException("绑定与数据集不匹配");
            }
            return List.of(entity);
        }
        List<GovRuleBinding> bindings = new ArrayList<>(version.getBindings());
        if (datasetId == null) {
            return bindings;
        }
        return bindings.stream().filter(binding -> datasetId.equals(binding.getDatasetId())).collect(Collectors.toList());
    }

    private Map<String, String> resolveStatements(GovRuleVersion version) {
        if (version.getDefinition() == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(version.getDefinition(), MAP_TYPE);
            if (raw.containsKey("statements")) {
                Object statements = raw.get("statements");
                if (statements instanceof Map<?, ?> map) {
                    Map<String, String> resolved = new LinkedHashMap<>();
                    map.forEach((key, value) -> {
                        if (key != null && value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                            resolved.put(String.valueOf(key), String.valueOf(value));
                        }
                    });
                    if (!resolved.isEmpty()) {
                        return resolved;
                    }
                }
            }
            if (raw.containsKey("sql")) {
                Object sqlValue = raw.get("sql");
                if (sqlValue == null) {
                    return Collections.emptyMap();
                }
                String sql = String.valueOf(sqlValue);
                return StringUtils.isNotBlank(sql) ? Map.of("sql", sql) : Collections.emptyMap();
            }
        } catch (Exception ex) {
            log.warn("event=quality_rule_definition_parse_failed errorType={}", ex.getClass().getSimpleName());
        }
        return Collections.emptyMap();
    }

    private Map<String, String> renderParams(Map<String, String> statements, Map<String, Object> params) {
        if (params.isEmpty()) {
            return statements;
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        statements.forEach((key, value) -> rendered.put(key, applyParams(value, params)));
        return rendered;
    }

    private String applyParams(String sql, Map<String, Object> params) {
        String rendered = sql;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = ":" + entry.getKey();
            if (rendered.contains(placeholder) && entry.getValue() != null) {
                rendered = rendered.replace(placeholder, quote(entry.getValue().toString()));
            }
        }
        return rendered;
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private void persistMetrics(GovQualityRun run, List<StatementExecutionResult> results) {
        metricRepository.findByRunId(run.getId()).forEach(metricRepository::delete);
        boolean hasExecutionError = results
            .stream()
            .anyMatch(result ->
                result.status() == StatementExecutionResult.Status.FAILED &&
                StringUtils.isNotBlank(result.errorCode())
            );
        for (StatementExecutionResult result : results) {
            GovQualityMetric metric = new GovQualityMetric();
            metric.setRun(run);
            metric.setMetricKey(result.key());
            metric.setDetail(safeMetricDetail(result.status()));
            metric.setStatus(result.status().name());
            // Compute metric_value: pass rate based on rowsTotal and failingRowCount
            Integer rowsTotal = run.getRowsTotal();
            Integer failingRows = run.getFailingRowCount();
            if (hasExecutionError && result.status() == StatementExecutionResult.Status.FAILED) {
                metric.setMetricValue(BigDecimal.ZERO);
            } else if (rowsTotal != null && rowsTotal > 0) {
                int failing = Math.min(rowsTotal, Math.max(0, failingRows != null ? failingRows : 0));
                BigDecimal passRate = BigDecimal.valueOf(rowsTotal - failing)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(rowsTotal), 6, RoundingMode.HALF_UP);
                metric.setMetricValue(passRate);
            } else if (result.status() == StatementExecutionResult.Status.SUCCEEDED) {
                metric.setMetricValue(BigDecimal.valueOf(100));
            } else if (result.status() == StatementExecutionResult.Status.FAILED) {
                metric.setMetricValue(BigDecimal.ZERO);
            }
            // Set threshold_value from rule severity config if available
            GovRule rule = run.getRule();
            if (rule != null) {
                String severity = rule.getSeverity();
                if (StringUtils.isNotBlank(severity)) {
                    BigDecimal threshold = switch (severity.toUpperCase(Locale.ROOT)) {
                        case "CRITICAL" -> BigDecimal.valueOf(99);
                        case "HIGH" -> BigDecimal.valueOf(95);
                        case "MEDIUM" -> BigDecimal.valueOf(90);
                        case "LOW" -> BigDecimal.valueOf(80);
                        default -> null;
                    };
                    metric.setThresholdValue(threshold);
                }
            }
            metricRepository.save(metric);
        }
    }

    private StatementExecutionResult.Status aggregateStatus(List<StatementExecutionResult> results) {
        boolean hasFailure = results.stream().anyMatch(res -> res.status() == StatementExecutionResult.Status.FAILED);
        if (hasFailure) {
            return StatementExecutionResult.Status.FAILED;
        }
        boolean allSkipped = results.stream().allMatch(res -> res.status() == StatementExecutionResult.Status.SKIPPED);
        if (allSkipped) {
            return StatementExecutionResult.Status.SKIPPED;
        }
        return StatementExecutionResult.Status.SUCCEEDED;
    }

    private long failedStatementCount(List<StatementExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return 0L;
        }
        return results
            .stream()
            .filter(result -> result != null && result.status() == StatementExecutionResult.Status.FAILED)
            .count();
    }

    private String mapStatus(StatementExecutionResult.Status status) {
        return switch (status) {
            case FAILED -> "FAILED";
            case SKIPPED -> "SKIPPED";
            default -> "SUCCEEDED";
        };
    }

    private String resolveErrorCategory(List<StatementExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        StatementExecutionResult failed = results
            .stream()
            .filter(item -> item != null && item.status() == StatementExecutionResult.Status.FAILED)
            .findFirst()
            .orElse(null);
        if (failed == null) {
            return null;
        }
        if (StringUtils.isNotBlank(failed.errorCode())) {
            return normalizeErrorCode(failed.errorCode());
        }
        return resolveErrorCategory(failed.message());
    }

    private String resolveErrorCategory(String rawMessage) {
        String message = StringUtils.trimToEmpty(rawMessage).toLowerCase(Locale.ROOT);
        if (message.isEmpty()) {
            return "UNKNOWN";
        }
        if (message.contains("permission denied") || message.contains("access denied") || message.contains("not authorized")) {
            return "PERMISSION_DENIED";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("syntax error") || message.contains("parse exception") || message.contains("parser")) {
            return "SQL_SYNTAX";
        }
        if (
            message.contains("does not exist") ||
            message.contains("not found") ||
            message.contains("unknown table") ||
            message.contains("unknown column")
        ) {
            return "OBJECT_NOT_FOUND";
        }
        if (message.contains("connection refused") || message.contains("connection reset") || message.contains("connection closed")) {
            return "CONNECTION_ERROR";
        }
        return "EXECUTION_ERROR";
    }

    private String normalizeErrorCode(String errorCode) {
        String normalized = StringUtils.trimToEmpty(errorCode).toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.matches("[A-Z0-9_]{1,64}") ? normalized : "EXECUTION_ERROR";
    }

    private String summaryMessage(List<StatementExecutionResult> results) {
        long failed = results.stream().filter(res -> res.status() == StatementExecutionResult.Status.FAILED).count();
        long skipped = results.stream().filter(res -> res.status() == StatementExecutionResult.Status.SKIPPED).count();
        if (failed > 0) {
            return "存在" + failed + "个检测失败";
        }
        if (skipped == results.size()) {
            return "Hive 执行未开启，已跳过";
        }
        return "执行成功";
    }

    private String writeSafeMetrics(List<StatementExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> safeResults = results
            .stream()
            .filter(java.util.Objects::nonNull)
            .map(result -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", result.key());
                item.put("status", result.status().name());
                item.put("detail", safeMetricDetail(result.status()));
                if (result.status() == StatementExecutionResult.Status.FAILED) {
                    item.put(
                        "errorCategory",
                        StringUtils.isNotBlank(result.errorCode())
                            ? normalizeErrorCode(result.errorCode())
                            : resolveErrorCategory(result.message())
                    );
                }
                return item;
            })
            .collect(Collectors.toList());
        return writeJson(safeResults);
    }

    private String safeMetricDetail(StatementExecutionResult.Status status) {
        if (status == null) {
            return "质量检测状态未知";
        }
        return switch (status) {
            case FAILED -> "质量检测项执行失败";
            case SKIPPED -> "质量检测项已跳过";
            case SUCCEEDED -> "质量检测项执行成功";
        };
    }

    private QualityRunDto toSafeDto(GovQualityRun run, List<GovQualityMetric> metrics) {
        QualityRunDto dto = GovernanceMapper.toDto(run, metrics);
        if (dto == null) {
            return null;
        }
        dto.setInputParamsJson(null);
        dto.setMetricsJson(null);
        dto.setMessage(safeRunMessage(run));
        if (dto.getMetrics() != null) {
            dto.getMetrics().forEach(metric -> metric.setDetail(safeMetricDetail(parseMetricStatus(metric.getStatus()))));
        }
        return dto;
    }

    private StatementExecutionResult.Status parseMetricStatus(String status) {
        try {
            return StatementExecutionResult.Status.valueOf(StringUtils.trimToEmpty(status).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String safeRunMessage(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        String status = StringUtils.trimToEmpty(run.getStatus()).toUpperCase(Locale.ROOT);
        return switch (status) {
            case "QUEUED" -> "质量检测已进入队列";
            case "RUNNING" -> "正在执行质量检测";
            case "SUCCEEDED" -> "质量检测执行成功";
            case "SKIPPED" -> "质量检测已跳过";
            case "FAILED" -> "质量检测执行失败";
            default -> "质量检测状态未知";
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class QualityRunAuditWriteException extends RuntimeException {

        private QualityRunAuditWriteException(RuntimeException cause) {
            super("质量运行审计写入失败", cause);
        }
    }
}
