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
    private final Executor taskExecutor;
    private final QualityDatasetStatementExecutor statementExecutor;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;
    private final TransactionTemplate runTransactionTemplate;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final QualityDatasetReadGuard qualityDatasetReadGuard;
    private final QualityRunQueryService qualityRunQueryService;
    private final QualityRunAuditCoordinator runAuditCoordinator;

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
        this.taskExecutor = taskExecutor;
        this.statementExecutor = statementExecutor;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.qualityDatasetReadGuard = qualityDatasetReadGuard;
        this.qualityRunQueryService = new QualityRunQueryService(
            runRepository,
            metricRepository,
            datasetRepository,
            qualityDatasetReadGuard
        );
        this.runAuditCoordinator = new QualityRunAuditCoordinator(qualityAuditRecorder, issueTicketService);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.runTransactionTemplate = template;
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor) {
        return triggerInternal(request, actor, null, false, false, false);
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor, String activeDeptHeader) {
        return triggerInternal(request, actor, activeDeptHeader, true, false, false);
    }

    @Transactional
    public List<QualityRunDto> triggerTrustedIngestion(QualityRunTriggerRequest request) {
        return triggerInternal(request, "service:dts-ingestion", null, false, false, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerAuthorizedIndependent(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader
    ) {
        return triggerInternal(request, actor, activeDeptHeader, true, false, false);
    }

    @Transactional
    public List<QualityRunDto> triggerScheduled(QualityRunTriggerRequest request) {
        return triggerInternal(request, "scheduler", null, false, true, false);
    }

    /**
     * Executes a rule through the quality-workflow command boundary. The workflow identifier is persisted on every
     * generated rule run so orchestration can aggregate completion without taking ownership of rule execution.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerWorkflowAuthorized(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        UUID workflowRunId,
        String triggerRef
    ) {
        if (workflowRunId == null) {
            throw new IllegalArgumentException("缺少质量工作流实例ID");
        }
        if (triggerRef == null || triggerRef.isBlank() || triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量工作流触发标识无效");
        }
        return triggerInternal(
            request,
            actor,
            activeDeptHeader,
            true,
            false,
            false,
            null,
            triggerRef.trim(),
            workflowRunId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerWorkflowScheduled(
        QualityRunTriggerRequest request,
        UUID workflowRunId,
        String triggerRef
    ) {
        if (workflowRunId == null) {
            throw new IllegalArgumentException("缺少质量工作流实例ID");
        }
        if (triggerRef == null || triggerRef.isBlank() || triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量工作流触发标识无效");
        }
        return triggerInternal(
            request,
            "scheduler",
            null,
            false,
            true,
            false,
            null,
            triggerRef.trim(),
            workflowRunId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerWorkflowTrustedIngestion(
        QualityRunTriggerRequest request,
        UUID workflowRunId,
        String triggerRef
    ) {
        if (workflowRunId == null) {
            throw new IllegalArgumentException("缺少质量工作流实例ID");
        }
        if (triggerRef == null || triggerRef.isBlank() || triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量工作流触发标识无效");
        }
        return triggerInternal(
            request,
            "service:dts-ingestion",
            null,
            false,
            false,
            true,
            null,
            triggerRef.trim(),
            workflowRunId
        );
    }

    /**
     * Runs the exact rule version pinned by a release candidate. The trigger reference is server generated and
     * provides durable replay semantics while the caller serializes commands on the warehouse-plan lock.
     */
    @Transactional
    public List<QualityRunDto> triggerPinnedAuthorized(
        QualityRunTriggerRequest request,
        UUID ruleVersionId,
        String actor,
        String activeDeptHeader,
        String triggerRef
    ) {
        if (ruleVersionId == null) throw new IllegalArgumentException("缺少规则版本ID");
        if (triggerRef == null || triggerRef.isBlank() || triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量运行幂等标识无效");
        }
        String normalizedTriggerRef = triggerRef.trim();
        List<GovQualityRun> replay = runRepository.findByTriggerRefOrderByCreatedDateAsc(normalizedTriggerRef);
        if (!replay.isEmpty()) {
            replay.forEach(run -> qualityDatasetReadGuard.requireReadable(run.getDatasetId(), activeDeptHeader));
            return replay
                .stream()
                .map(run -> qualityRunQueryService.toSafeDto(run, metricRepository.findByRunId(run.getId())))
                .toList();
        }
        return triggerInternal(
            request,
            actor,
            activeDeptHeader,
            true,
            false,
            false,
            ruleVersionId,
            normalizedTriggerRef
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<QualityRunDto> triggerPinnedWorkflowAuthorized(
        QualityRunTriggerRequest request,
        UUID ruleVersionId,
        String actor,
        String activeDeptHeader,
        UUID workflowRunId,
        String triggerRef
    ) {
        if (workflowRunId == null) throw new IllegalArgumentException("缺少质量工作流实例ID");
        if (ruleVersionId == null) throw new IllegalArgumentException("缺少规则版本ID");
        if (triggerRef == null || triggerRef.isBlank() || triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量工作流触发标识无效");
        }
        List<GovQualityRun> replay = runRepository.findByTriggerRefOrderByCreatedDateAsc(triggerRef.trim());
        if (!replay.isEmpty()) {
            replay.forEach(run -> qualityDatasetReadGuard.requireReadable(run.getDatasetId(), activeDeptHeader));
            return replay
                .stream()
                .map(run -> qualityRunQueryService.toSafeDto(run, metricRepository.findByRunId(run.getId())))
                .toList();
        }
        return triggerInternal(
            request,
            actor,
            activeDeptHeader,
            true,
            false,
            false,
            ruleVersionId,
            triggerRef.trim(),
            workflowRunId
        );
    }

    private List<QualityRunDto> triggerInternal(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        boolean enforceUserAccess,
        boolean scheduledInvocation,
        boolean trustedIngestionInvocation
    ) {
        return triggerInternal(
            request,
            actor,
            activeDeptHeader,
            enforceUserAccess,
            scheduledInvocation,
            trustedIngestionInvocation,
            null,
            null
        );
    }

    private List<QualityRunDto> triggerInternal(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        boolean enforceUserAccess,
        boolean scheduledInvocation,
        boolean trustedIngestionInvocation,
        UUID pinnedRuleVersionId,
        String triggerRef
    ) {
        return triggerInternal(
            request,
            actor,
            activeDeptHeader,
            enforceUserAccess,
            scheduledInvocation,
            trustedIngestionInvocation,
            pinnedRuleVersionId,
            triggerRef,
            null
        );
    }

    private List<QualityRunDto> triggerInternal(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        boolean enforceUserAccess,
        boolean scheduledInvocation,
        boolean trustedIngestionInvocation,
        UUID pinnedRuleVersionId,
        String triggerRef,
        UUID workflowRunId
    ) {
        if (!properties.getQuality().isEnabled()) {
            throw new IllegalStateException("质量检测功能已禁用");
        }
        if (request == null) {
            throw new IllegalArgumentException("质量运行请求不能为空");
        }
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        String triggerType = resolveTriggerType(
            request.getTriggerType(),
            dryRun,
            scheduledInvocation,
            trustedIngestionInvocation
        );
        GovRule rule = resolveRule(request.getRuleId());
        GovRuleVersion version = pinnedRuleVersionId == null
            ? resolveVersion(rule)
            : resolvePinnedVersion(rule, pinnedRuleVersionId);
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
            run.setJobId(workflowRunId);
            run.setTriggerType(triggerType);
            run.setTriggerRef(triggerRef == null ? actor : triggerRef);
            run.setStatus("QUEUED");
            run.setSeverity(rule.getSeverity());
            run.setDataLevel(rule.getDataLevel());
            run.setScheduledAt(Instant.now());
            run.setInputParamsJson(writeJson(params));
            runRepository.save(run);

            Map<String, Object> beginPayload = buildRunAuditPayload(run, "开始运行质量规则：" + resolveRunRuleName(run));
            beginPayload.put("status", run.getStatus());
            String machineActor = runMachineAuditActor(run);
            if (machineActor != null) {
                qualityAuditRecorder.recordMachine(
                    machineActor,
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
            runs.add(qualityRunQueryService.toSafeDto(run, Collections.emptyList()));
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
        return qualityRunQueryService.getRun(runId, null);
    }

    @Transactional(readOnly = true)
    public QualityRunDto getRun(UUID runId, String activeDeptHeader) {
        return qualityRunQueryService.getRun(runId, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public void assertRunReadable(UUID runId, String activeDeptHeader) {
        qualityRunQueryService.assertRunReadable(runId, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> runsByWorkflow(UUID workflowRunId, String activeDeptHeader) {
        return qualityRunQueryService.byWorkflow(workflowRunId, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> trustedRunsByWorkflow(UUID workflowRunId) {
        return qualityRunQueryService.byWorkflowTrusted(workflowRunId, defaultLakeDatasetGuard);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByRule(UUID ruleId, int limit) {
        return qualityRunQueryService.recentByRule(ruleId, limit, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByRule(UUID ruleId, int limit, String activeDeptHeader) {
        return qualityRunQueryService.recentByRule(ruleId, limit, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByDataset(UUID datasetId, int limit) {
        return qualityRunQueryService.recentByDataset(datasetId, limit, null);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByDataset(UUID datasetId, int limit, String activeDeptHeader) {
        return qualityRunQueryService.recentByDataset(datasetId, limit, activeDeptHeader);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recent(int limit) {
        return qualityRunQueryService.recent(limit);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> listRuns(UUID ruleId, UUID datasetId, String status, String triggerType, Instant startedFrom, Instant startedTo, int limit) {
        return qualityRunQueryService.listRuns(ruleId, datasetId, status, triggerType, startedFrom, startedTo, limit, null);
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
        return qualityRunQueryService.listRuns(
            ruleId,
            datasetId,
            status,
            triggerType,
            startedFrom,
            startedTo,
            limit,
            activeDeptHeader
        );
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

            StatementExecutionResult.Status aggregate = aggregateStatus(results);
            run.setStatus(mapStatus(aggregate));
            run.setMessage(summaryMessage(results));
            run.setErrorCategory(resolveErrorCategory(results));
            run.setFinishedAt(Instant.now());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            run.setMetricsJson(execution.outcome().json());
            persistMetrics(run, results);
            runRepository.save(run);
            if (aggregate == StatementExecutionResult.Status.FAILED) {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("statementCount", results.size());
                payload.put("failedStatementCount", failedStatementCount(results));
                payload.put("errorCategory", StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_FAILED"));
                auditRunCompletion(run, AuditStage.FAIL, payload);
                if (!isDryRun(run) && execution.outcome().violated()) {
                    createIssueForFailedRun(run, results, resolveRunActor(run));
                }
            } else {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("statementCount", results.size());
                auditRunCompletion(run, AuditStage.SUCCESS, payload);
                if (aggregate == StatementExecutionResult.Status.SUCCEEDED && !isDryRun(run)) {
                    runAuditCoordinator.resolveIssuesForSuccessfulRun(run, resolveRunActor(run));
                }
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
            if (run.getMetricsJson() == null) run.setMetricsJson(QualityExecutionOutcome.unknown(run.getErrorCategory()).json());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            runRepository.save(run);
            Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
            payload.put("status", run.getStatus());
            payload.put("errorType", ex.getClass().getSimpleName());
            payload.put("errorCategory", StringUtils.defaultIfBlank(run.getErrorCategory(), "EXECUTION_FAILED"));
            auditRunCompletion(run, AuditStage.FAIL, payload);
            // Execution-only failures never create data-quality business issues.
        }
    }

    private boolean isDryRun(GovQualityRun run) {
        return run != null && "DRY_RUN".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()));
    }

    private String resolveTriggerType(
        String requestedTriggerType,
        boolean dryRun,
        boolean scheduledInvocation,
        boolean trustedIngestionInvocation
    ) {
        String triggerType = StringUtils.defaultIfBlank(requestedTriggerType, "MANUAL").trim();
        if (trustedIngestionInvocation) {
            if (dryRun) {
                throw new IllegalArgumentException("入湖服务质量运行不允许试跑模式");
            }
            return "INGESTION";
        }
        if ("INGESTION".equalsIgnoreCase(triggerType)) {
            throw new IllegalArgumentException("人工质量运行不允许使用入湖触发类型");
        }
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
        return qualityRunQueryService.findDtosByIds(runIds);
    }

    private void createIssueForFailedRun(GovQualityRun run, List<StatementExecutionResult> results, String actor) {
        runAuditCoordinator.createIssueForFailedRun(run, results, actor);
    }

    private String resolveRunRuleName(GovQualityRun run) {
        return runAuditCoordinator.resolveRunRuleName(run);
    }

    private String resolveRunActor(GovQualityRun run) {
        return runAuditCoordinator.resolveRunActor(run);
    }

    private void auditRunCompletion(GovQualityRun run, AuditStage stage, Map<String, Object> payload) {
        runAuditCoordinator.auditRunCompletion(run, stage, payload);
    }

    private String runMachineAuditActor(GovQualityRun run) {
        return runAuditCoordinator.runMachineAuditActor(run);
    }

    private String runEventIdentity(GovQualityRun run, AuditStage stage) {
        return runAuditCoordinator.runEventIdentity(run, stage);
    }

    private Map<String, Object> buildRunAuditPayload(GovQualityRun run, String summary) {
        return runAuditCoordinator.buildRunAuditPayload(run, summary);
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

    private GovRuleVersion resolvePinnedVersion(GovRule rule, UUID ruleVersionId) {
        GovRuleVersion version = versionRepository
            .findById(ruleVersionId)
            .orElseThrow(() -> new IllegalArgumentException("未找到候选版本钉定的质量规则版本"));
        if (version.getRule() == null || !rule.getId().equals(version.getRule().getId())) {
            throw new IllegalArgumentException("钉定规则版本与质量规则不匹配");
        }
        String status = StringUtils.trimToEmpty(version.getStatus()).toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(status)) {
            throw new IllegalStateException("候选版本钉定的质量规则版本不可执行");
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
        if (version.getDefinition() == null) return Collections.emptyMap();
        return QualityRuleStatements.resolve(version.getDefinition());
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
            Integer rate = QualityRunOutcomeSemantics.passRate(run.getStatus(), run.getErrorCategory(), rowsTotal, failingRows);
            if (rate != null && "EXACT".equals(QualityExecutionOutcome.read(run).statisticsStatus())) {
                metric.setMetricValue(BigDecimal.valueOf(rate));
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
        return QualityRunOutcomeSemantics.dominantFailureCategory(results);
    }

    private String resolveErrorCategory(String rawMessage) {
        return QualityRunOutcomeSemantics.classifyExecutionError(rawMessage);
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
                            ? QualityRunOutcomeSemantics.normalizeErrorCode(result.errorCode())
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

}
