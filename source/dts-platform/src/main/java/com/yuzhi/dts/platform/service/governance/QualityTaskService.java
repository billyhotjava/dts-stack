package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityTaskRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional
public class QualityTaskService {

    private static final Logger log = LoggerFactory.getLogger(QualityTaskService.class);
    private static final java.util.concurrent.atomic.AtomicBoolean TABLE_NOT_READY_WARNED = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final GovQualityTaskRepository taskRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final QualityWorkflowOrchestrator workflowOrchestrator;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final IssueTicketService issueTicketService;
    private final DataStandardSecurity security;
    private final QualityEffectiveDepartmentResolver departmentResolver;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final AccessChecker accessChecker;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final TransactionTemplate taskTransactionTemplate;

    public QualityTaskService(
        GovQualityTaskRepository taskRepository,
        GovRuleBindingRepository bindingRepository,
        CatalogDatasetRepository datasetRepository,
        QualityWorkflowOrchestrator workflowOrchestrator,
        QualityAuditRecorder qualityAuditRecorder,
        IssueTicketService issueTicketService,
        DataStandardSecurity security,
        QualityEffectiveDepartmentResolver departmentResolver,
        OrganizationVisibilityService organizationVisibilityService,
        AccessChecker accessChecker,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.bindingRepository = bindingRepository;
        this.datasetRepository = datasetRepository;
        this.workflowOrchestrator = workflowOrchestrator;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.issueTicketService = issueTicketService;
        this.security = security;
        this.departmentResolver = departmentResolver;
        this.organizationVisibilityService = organizationVisibilityService;
        this.accessChecker = accessChecker;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.taskTransactionTemplate = template;
    }

    @Transactional(readOnly = true)
    public List<GovQualityTask> list(String activeDeptHeader) {
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.currentDefaultLakeSourceId().orElse(null);
        return taskRepository
            .findAll()
            .stream()
            .filter(task -> isOwnerDeptVisible(task != null ? task.getOwnerDept() : null, activeDept, instituteScope))
            .filter(task -> task != null && task.getDatasetId() != null)
            .filter(task ->
                datasetRepository
                    .findById(task.getDatasetId())
                    .filter(accessChecker::canRead)
                    .filter(dataset -> defaultLakeDatasetGuard.isDefaultLakeDataset(dataset, defaultLakeSourceId))
                    .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
                    .isPresent()
            )
            .sorted(Comparator.comparing(task -> String.valueOf(task.getName()).toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
    }

    public GovQualityTask create(GovQualityTask request, String actor, String activeDeptHeader) {
        try {
            GovQualityTask task = new GovQualityTask();
            applyUpsert(task, request, activeDeptHeader);
            GovQualityTask saved = taskRepository.save(task);
            taskRepository.flush();
            auditTaskAction(
                "GOV_QUALITY_TASK_CREATE",
                AuditStage.SUCCESS,
                taskResourceId(saved != null ? saved.getId() : null),
                "创建运行策略",
                saved,
                Map.of()
            );
            return saved;
        } catch (RuntimeException ex) {
            auditTaskFailure("GOV_QUALITY_TASK_CREATE", "UNASSIGNED", "创建运行策略失败", request, ex);
            throw ex;
        }
    }

    public GovQualityTask update(UUID id, GovQualityTask request, String actor, String activeDeptHeader) {
        try {
            GovQualityTask task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("运行策略不存在"));
            ensureAccessible(task, activeDeptHeader);
            applyUpsert(task, request, activeDeptHeader);
            GovQualityTask saved = taskRepository.save(task);
            taskRepository.flush();
            auditTaskAction(
                "GOV_QUALITY_TASK_UPDATE",
                AuditStage.SUCCESS,
                id.toString(),
                "更新运行策略",
                saved,
                Map.of()
            );
            return saved;
        } catch (RuntimeException ex) {
            auditTaskFailure("GOV_QUALITY_TASK_UPDATE", id.toString(), "更新运行策略失败", request, ex);
            throw ex;
        }
    }

    public void delete(UUID id, String actor, String activeDeptHeader) {
        GovQualityTask task = null;
        try {
            task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("运行策略不存在"));
            ensureAccessible(task, activeDeptHeader);
            taskRepository.delete(task);
            taskRepository.flush();
            auditTaskAction(
                "GOV_QUALITY_TASK_DELETE",
                AuditStage.SUCCESS,
                id.toString(),
                "删除运行策略",
                null,
                Map.of()
            );
        } catch (RuntimeException ex) {
            auditTaskFailure("GOV_QUALITY_TASK_DELETE", id.toString(), "删除运行策略失败", task, ex);
            throw ex;
        }
    }

    public GovQualityTask toggle(UUID id, boolean enabled, String actor, String activeDeptHeader) {
        GovQualityTask task = null;
        try {
            task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("运行策略不存在"));
            ensureAccessible(task, activeDeptHeader);
            task.setEnabled(enabled);
            GovQualityTask saved = taskRepository.save(task);
            taskRepository.flush();
            auditTaskAction(
                "GOV_QUALITY_TASK_TOGGLE",
                AuditStage.SUCCESS,
                id.toString(),
                enabled ? "启用运行策略" : "停用运行策略",
                saved,
                Map.of("enabled", enabled)
            );
            return saved;
        } catch (RuntimeException ex) {
            auditTaskFailure("GOV_QUALITY_TASK_TOGGLE", id.toString(), "切换运行策略状态失败", task, ex);
            throw ex;
        }
    }

    public QualityWorkflowRunDto trigger(
        UUID id,
        String actor,
        String activeDeptHeader,
        String idempotencyKey
    ) {
        GovQualityTask task = null;
        try {
            task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("运行策略不存在"));
            ensureAccessible(task, activeDeptHeader);
            QualityWorkflowRunDto workflow = workflowOrchestrator.startAuthorizedTask(
                task,
                actor,
                activeDeptHeader,
                "MANUAL",
                StringUtils.defaultIfBlank(
                    idempotencyKey,
                    "quality-task:" + id + ":manual:" + UUID.randomUUID()
                )
            );
            task.setLastTriggeredAt(Instant.now());
            taskRepository.save(task);
            taskRepository.flush();
            boolean failed = List.of("FAILED", "BLOCKED", "CANCELLED").contains(workflow.status());
            auditTaskAction(
                "GOV_QUALITY_TASK_EXECUTE",
                failed ? AuditStage.FAIL : AuditStage.SUCCESS,
                id.toString(),
                failed ? "运行策略未能完成验证" : "触发运行策略",
                task,
                Map.of(
                    "triggerType",
                    "MANUAL",
                    "workflowId",
                    workflow.id().toString(),
                    "runCount",
                    workflow.expectedRunCount()
                )
            );
            return workflow;
        } catch (RuntimeException ex) {
            auditTaskFailure("GOV_QUALITY_TASK_EXECUTE", id.toString(), "触发运行策略失败", task, ex);
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runDueTasks() {
        List<GovQualityTask> tasks;
        try {
            tasks = taskRepository.findByEnabledTrue();
        } catch (RuntimeException ex) {
            // Liquibase may be running asynchronously; avoid spamming scheduling logs until tables are ready.
            if (TABLE_NOT_READY_WARNED.compareAndSet(false, true)) {
                log.warn(
                    "event=quality_task_schedule_repository_unavailable errorType={} hint=verify_governance_quality_task_migration",
                    ex.getClass().getSimpleName()
                );
            } else {
                log.debug(
                    "event=quality_task_schedule_repository_unavailable errorType={}",
                    ex.getClass().getSimpleName()
                );
            }
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (GovQualityTask task : tasks) {
            if (task == null || task.getId() == null || task.getDatasetId() == null || !Boolean.TRUE.equals(task.getEnabled())) {
                continue;
            }
            int intervalMinutes = task.getIntervalMinutes() != null ? task.getIntervalMinutes() : 60;
            if (intervalMinutes <= 0) {
                intervalMinutes = 60;
            }
            Instant last = task.getLastTriggeredAt();
            if (last != null) {
                Duration elapsed = Duration.between(last, now);
                if (!elapsed.isNegative() && elapsed.toMinutes() < intervalMinutes) {
                    continue;
                }
            }
            Instant dueBefore = now.minus(Duration.ofMinutes(intervalMinutes));
            boolean claimed;
            try {
                claimed = Boolean.TRUE.equals(taskTransactionTemplate.execute(status ->
                    taskRepository.claimDueExecution(task.getId(), now, dueBefore) == 1
                ));
            } catch (RuntimeException claimFailure) {
                log.warn(
                    "event=quality_task_schedule_claim_failed taskId={} errorType={}",
                    task.getId(),
                    claimFailure.getClass().getSimpleName()
                );
                continue;
            }
            if (!claimed) {
                continue;
            }
            Instant scheduleSlot = normalizedScheduleSlot(now, intervalMinutes);
            String attemptIdentity = scheduledEventIdentity(task.getId(), now, intervalMinutes);
            Map<String, Object> beginPayload = taskAuditPayload(
                "开始调度运行策略",
                task,
                Map.of("triggerType", "SCHEDULED")
            );
            try {
                qualityAuditRecorder.recordMachineAttempt(
                    "scheduler",
                    attemptIdentity + ":BEGIN",
                    scheduleSlot,
                    "GOV_QUALITY_TASK_EXECUTE",
                    AuditStage.BEGIN,
                    taskResourceId(task.getId()),
                    beginPayload
                );
            } catch (RuntimeException auditFailure) {
                log.warn(
                    "event=quality_task_begin_audit_write_failed taskId={} errorType={}",
                    task.getId(),
                    auditFailure.getClass().getSimpleName()
                );
                continue;
            }
            try {
                taskTransactionTemplate.executeWithoutResult(status -> {
                    QualityWorkflowRunDto workflow = workflowOrchestrator.startScheduledTask(
                        task,
                        scheduleSlot,
                        attemptIdentity
                    );
                    boolean failed = List.of("FAILED", "BLOCKED", "CANCELLED").contains(workflow.status());
                    auditScheduledTask(
                        attemptIdentity + (failed ? ":FAIL" : ":SUCCESS"),
                        scheduleSlot,
                        task,
                        failed ? AuditStage.FAIL : AuditStage.SUCCESS,
                        failed ? "调度运行策略未能完成验证" : "调度运行策略",
                        Map.of("workflowId", workflow.id().toString(), "runCount", workflow.expectedRunCount())
                    );
                });
            } catch (RuntimeException ex) {
                String errorCategory = taskAuditErrorCategory(ex);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("summary", "执行运行策略失败");
                payload.put("taskId", String.valueOf(task.getId()));
                payload.put("taskName", task.getName());
                payload.put("errorType", ex.getClass().getSimpleName());
                payload.put("errorCategory", errorCategory);
                safeRecordMachineFailure(
                    attemptIdentity + ":FAIL",
                    scheduleSlot,
                    "GOV_QUALITY_TASK_EXECUTE",
                    String.valueOf(task.getId()),
                    payload
                );
                try {
                    taskTransactionTemplate.executeWithoutResult(status ->
                        createIssueForTaskFailure(task, errorCategory, attemptIdentity, scheduleSlot)
                    );
                } catch (RuntimeException issueFailure) {
                    Map<String, Object> issuePayload = new LinkedHashMap<>();
                    issuePayload.put("summary", "自动创建质量巡检问题失败");
                    issuePayload.put("sourceType", "QUALITY_TASK");
                    issuePayload.put("sourceId", String.valueOf(task.getId()));
                    if (task.getDatasetId() != null) {
                        issuePayload.put("datasetId", task.getDatasetId().toString());
                    }
                    issuePayload.put("errorType", issueFailure.getClass().getSimpleName());
                    issuePayload.put("errorCategory", "ISSUE_PERSISTENCE_FAILED");
                    safeRecordMachineFailure(
                        attemptIdentity + ":ISSUE:FAIL",
                        scheduleSlot,
                        "GOV_ISSUE_CREATE",
                        String.valueOf(task.getId()),
                        issuePayload
                    );
                    log.warn(
                        "event=quality_task_issue_create_failed taskId={} errorType={}",
                        task.getId(),
                        issueFailure.getClass().getSimpleName()
                    );
                }
            }
        }
    }

    private void createIssueForTaskFailure(
        GovQualityTask task,
        String errorCategory,
        String attemptIdentity,
        Instant occurredAt
    ) {
        if (task == null || task.getId() == null) {
            return;
        }
        IssueTicketUpsertRequest req = new IssueTicketUpsertRequest();
        req.setTitle("质量运行策略执行失败");
        StringBuilder summary = new StringBuilder();
        summary.append("计划：").append(task.getName() != null ? task.getName() : task.getId().toString());
        if (task.getDatasetId() != null) {
            summary.append("\n数据集：").append(task.getDatasetId());
        }
        summary.append("\n原因：质量运行策略执行失败（类别：")
            .append(StringUtils.defaultIfBlank(errorCategory, "INTERNAL_ERROR"))
            .append("）");
        req.setSummary(summary.toString());
        req.setSeverity("HIGH");
        req.setDatasetId(task.getDatasetId());
        req.setTags(List.of("QUALITY_TASK", "datasetId=" + String.valueOf(task.getDatasetId())));
        IssueTicketService.CreateOrTouchResult result = issueTicketService.createOrTouchWithDisposition(
            "QUALITY_TASK",
            task.getId(),
            req,
            "system",
            "系统自动生成：运行策略执行失败"
        );
        IssueTicketDto issue = result != null ? result.ticket() : null;
        if (issue != null && issue.getId() != null) {
            String actionCode = result.disposition() == IssueTicketService.CreateOrTouchDisposition.CREATED
                ? "GOV_ISSUE_CREATE"
                : "GOV_ISSUE_ACTION_APPEND";
            qualityAuditRecorder.recordMachine(
                "scheduler",
                attemptIdentity + ":ISSUE:" + result.disposition().name(),
                occurredAt,
                actionCode,
                AuditStage.SUCCESS,
                issue.getId().toString(),
                automaticIssueAuditPayload("QUALITY_TASK", task.getId(), task.getDatasetId(), issue.getId())
            );
        }
    }

    private List<UUID> executableRuleIds(UUID datasetId) {
        List<GovRuleBinding> bindings = bindingRepository.findWorkflowBindings(datasetId, "PUBLISHED");
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings
            .stream()
            .filter(Objects::nonNull)
            .filter(binding -> binding.getRuleVersion() != null)
            .filter(binding -> "PUBLISHED".equalsIgnoreCase(StringUtils.trimToEmpty(binding.getRuleVersion().getStatus())))
            .map(binding -> binding.getRuleVersion().getRule())
            .filter(Objects::nonNull)
            .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
            .map(rule -> rule.getId())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private void auditScheduledTask(
        String eventIdentity,
        Instant occurredAt,
        GovQualityTask task,
        AuditStage stage,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = taskAuditPayload(summary, task, details);
        qualityAuditRecorder.recordMachine(
            "scheduler",
            eventIdentity,
            occurredAt,
            "GOV_QUALITY_TASK_EXECUTE",
            stage,
            taskResourceId(task != null ? task.getId() : null),
            payload
        );
    }

    private void auditTaskFailure(
        String actionCode,
        String resourceId,
        String summary,
        GovQualityTask task,
        RuntimeException error
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorType", error.getClass().getSimpleName());
        details.put("errorCategory", taskAuditErrorCategory(error));
        Map<String, Object> payload = taskAuditPayload(summary, task, details);
        try {
            qualityAuditRecorder.recordFailureAction(actionCode, resourceId, payload);
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_task_failure_audit_write_failed actionCode={} resourceId={} errorType={}",
                actionCode,
                resourceId,
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    private void auditTaskAction(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String summary,
        GovQualityTask task,
        Map<String, Object> details
    ) {
        qualityAuditRecorder.recordAction(actionCode, stage, resourceId, taskAuditPayload(summary, task, details));
    }

    static Instant normalizedScheduleSlot(Instant occurredAt, int intervalMinutes) {
        Instant effectiveTime = occurredAt != null ? occurredAt : Instant.EPOCH;
        long intervalSeconds = Math.max(1L, intervalMinutes) * 60L;
        long slotEpochSeconds = Math.floorDiv(effectiveTime.getEpochSecond(), intervalSeconds) * intervalSeconds;
        return Instant.ofEpochSecond(slotEpochSeconds);
    }

    static String scheduledEventIdentity(UUID taskId, Instant occurredAt, int intervalMinutes) {
        return "quality-task:" + taskId + ":scheduled:" + normalizedScheduleSlot(occurredAt, intervalMinutes).getEpochSecond();
    }

    private void safeRecordMachineFailure(
        String eventIdentity,
        Instant occurredAt,
        String actionCode,
        String resourceId,
        Object payload
    ) {
        try {
            qualityAuditRecorder.recordMachineAttempt(
                "scheduler",
                eventIdentity,
                occurredAt,
                actionCode,
                AuditStage.FAIL,
                resourceId,
                payload
            );
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_task_machine_audit_write_failed actionCode={} resourceId={} errorType={}",
                actionCode,
                resourceId,
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    private Map<String, Object> automaticIssueAuditPayload(
        String sourceType,
        UUID sourceId,
        UUID datasetId,
        UUID issueId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "自动创建质量问题");
        payload.put("sourceType", sourceType);
        payload.put("sourceId", sourceId.toString());
        payload.put("issueId", issueId.toString());
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        return payload;
    }

    private String taskAuditErrorCategory(Throwable error) {
        if (error instanceof EntityNotFoundException) {
            return "NOT_FOUND";
        }
        if (error instanceof AccessDeniedException) {
            return "ACCESS_DENIED";
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return "VALIDATION";
        }
        String type = error != null ? error.getClass().getSimpleName().toUpperCase(Locale.ROOT) : "";
        if (type.contains("SQL") || type.contains("JDBC") || type.contains("DATAACCESS")) {
            return "DATA_ACCESS";
        }
        return "INTERNAL_ERROR";
    }

    private Map<String, Object> taskAuditPayload(
        String summary,
        GovQualityTask task,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        if (task != null) {
            if (StringUtils.isNotBlank(task.getName())) {
                payload.put("taskName", task.getName());
            }
            if (task.getDatasetId() != null) {
                payload.put("datasetId", task.getDatasetId().toString());
            }
            if (task.getRuleId() != null) {
                payload.put("ruleId", task.getRuleId().toString());
            }
        }
        if (details != null) {
            details.forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && value != null) {
                    payload.put(key, value);
                }
            });
        }
        return payload;
    }

    private String taskResourceId(UUID taskId) {
        return taskId != null ? taskId.toString() : "UNASSIGNED";
    }

    private void applyUpsert(GovQualityTask task, GovQualityTask request, String activeDeptHeader) {
        if (task == null || request == null) {
            return;
        }
        if (request.getDatasetId() == null) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        var dataset = defaultLakeDatasetGuard.requireDefaultLakeDataset(request.getDatasetId());
        if (!accessChecker.canRead(dataset)) {
            throw new AccessDeniedException("无权限访问该数据集");
        }
        if (request.getRuleId() != null && !executableRuleIds(request.getDatasetId()).contains(request.getRuleId())) {
            throw new IllegalArgumentException("所选质量规则未启用、未发布或未绑定当前数据资产");
        }
        task.setName(StringUtils.trimToNull(request.getName()));
        task.setDatasetId(request.getDatasetId());
        task.setRuleId(request.getRuleId());
        int minutes = request.getIntervalMinutes() != null ? request.getIntervalMinutes() : 60;
        if (minutes <= 0) {
            minutes = 60;
        }
        task.setIntervalMinutes(minutes);
        task.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        int maxRetryAttempts = request.getMaxRetryAttempts() != null ? request.getMaxRetryAttempts() : 1;
        int retryBackoffSeconds = request.getRetryBackoffSeconds() != null ? request.getRetryBackoffSeconds() : 0;
        if (maxRetryAttempts < 0 || maxRetryAttempts > 5) {
            throw new IllegalArgumentException("最多重试次数必须在 0 到 5 之间");
        }
        if (retryBackoffSeconds < 0 || retryBackoffSeconds > 86_400) {
            throw new IllegalArgumentException("重试等待时间必须在 0 到 86400 秒之间");
        }
        task.setMaxRetryAttempts(maxRetryAttempts);
        task.setRetryBackoffSeconds(retryBackoffSeconds);

        String requestedOwnerDept = StringUtils.trimToNull(request.getOwnerDept());
        if (security.hasInstituteScope()) {
            task.setOwnerDept(requestedOwnerDept);
            return;
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (!accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new AccessDeniedException("当前部门上下文不可访问该数据集");
        }
        if (requestedOwnerDept != null && !DepartmentUtils.matches(requestedOwnerDept, activeDept)) {
            throw new AccessDeniedException("仅允许设置为当前登录部门的运行策略");
        }
        task.setOwnerDept(activeDept.trim());
    }

    private boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = StringUtils.trimToNull(ownerDept);
        if (trimmedOwner == null) {
            return true;
        }
        if (instituteScope) {
            return true;
        }
        if (organizationVisibilityService.isRoot(trimmedOwner)) {
            return true;
        }
        if (StringUtils.isBlank(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    private void ensureAccessible(GovQualityTask task, String activeDeptHeader) {
        if (task == null) {
            throw new EntityNotFoundException("运行策略不存在");
        }
        String activeDept = departmentResolver.resolve(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        if (!isOwnerDeptVisible(task.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该运行策略");
        }
        if (task.getDatasetId() == null) {
            return;
        }
        datasetRepository
            .findById(task.getDatasetId())
            .filter(accessChecker::canRead)
            .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
            .orElseThrow(() -> new AccessDeniedException("当前账号无权访问该运行策略关联的数据资产"));
    }
}
