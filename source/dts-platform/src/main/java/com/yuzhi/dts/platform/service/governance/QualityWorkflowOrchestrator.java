package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.governance.GovQualityWorkflowRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable command boundary for data-quality automation. It owns workflow idempotency and aggregation metadata while
 * {@link QualityRunService} remains the only owner of individual rule execution.
 */
@Service
public class QualityWorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QualityWorkflowOrchestrator.class);
    private static final int MAX_RULES_PER_WORKFLOW = 100;
    private static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");
    private static final List<String> RETRYABLE_STATUSES = List.of("FAILED", "BLOCKED");

    private enum InvocationMode {
        AUTHORIZED,
        SCHEDULED,
        TRUSTED_INGESTION
    }

    private final GovQualityWorkflowRunRepository workflowRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final QualityRunService qualityRunService;
    private final QualityDatasetReadGuard datasetReadGuard;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final QualityAuditRecorder auditRecorder;

    public QualityWorkflowOrchestrator(
        GovQualityWorkflowRunRepository workflowRepository,
        GovRuleBindingRepository bindingRepository,
        QualityRunService qualityRunService,
        QualityDatasetReadGuard datasetReadGuard,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        QualityAuditRecorder auditRecorder
    ) {
        this.workflowRepository = workflowRepository;
        this.bindingRepository = bindingRepository;
        this.qualityRunService = qualityRunService;
        this.datasetReadGuard = datasetReadGuard;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public QualityWorkflowRunDto startAuthorizedTask(
        GovQualityTask task,
        String actor,
        String activeDeptHeader,
        String triggerType,
        String idempotencyKey
    ) {
        return start(
            task,
            actor,
            activeDeptHeader,
            triggerType,
            idempotencyKey,
            InvocationMode.AUTHORIZED,
            null,
            1,
            null,
            null,
            null
        );
    }

    @Transactional
    public QualityWorkflowRunDto startAuthorizedRule(
        QualityRunTriggerRequest request,
        String actor,
        String activeDeptHeader,
        String idempotencyKey
    ) {
        if (request == null || request.getRuleId() == null) {
            throw new IllegalArgumentException("质量工作流缺少规则");
        }
        List<GovRuleBinding> bindings = bindingRepository.findPublishedWorkflowBindingsByRuleId(
            request.getRuleId(),
            "PUBLISHED"
        );
        List<GovRuleBinding> matching = bindings == null
            ? List.of()
            : bindings
                .stream()
                .filter(Objects::nonNull)
                .filter(binding -> request.getBindingId() == null || request.getBindingId().equals(binding.getId()))
                .filter(binding -> request.getDatasetId() == null || request.getDatasetId().equals(binding.getDatasetId()))
                .toList();
        Set<UUID> datasetIds = matching
            .stream()
            .map(GovRuleBinding::getDatasetId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (matching.size() != 1 || datasetIds.size() != 1) {
            throw new IllegalStateException("质量规则必须唯一绑定一个可执行数据资产");
        }
        GovQualityTask task = new GovQualityTask();
        task.setDatasetId(datasetIds.iterator().next());
        task.setRuleId(request.getRuleId());
        task.setMaxRetryAttempts(1);
        task.setRetryBackoffSeconds(0);
        return start(
            task,
            actor,
            activeDeptHeader,
            "MANUAL",
            idempotencyKey,
            InvocationMode.AUTHORIZED,
            null,
            1,
            null,
            null,
            request.getParameters()
        );
    }

    @Transactional
    public QualityWorkflowRunDto startScheduledTask(
        GovQualityTask task,
        Instant scheduleSlot,
        String idempotencyKey
    ) {
        return start(
            task,
            "scheduler",
            null,
            "SCHEDULED",
            idempotencyKey,
            InvocationMode.SCHEDULED,
            null,
            1,
            scheduleSlot,
            null,
            null
        );
    }

    @Transactional
    public QualityWorkflowRunDto startTrustedIngestion(
        UUID datasetId,
        String sourceRef,
        String idempotencyKey
    ) {
        GovQualityTask task = new GovQualityTask();
        task.setDatasetId(datasetId);
        task.setMaxRetryAttempts(1);
        task.setRetryBackoffSeconds(30);
        return start(
            task,
            "service:dts-ingestion",
            null,
            "INGESTION",
            idempotencyKey,
            InvocationMode.TRUSTED_INGESTION,
            null,
            1,
            Instant.now(),
            sourceRef,
            null
        );
    }

    @Transactional
    public QualityWorkflowRunDto startPinnedModelQuality(
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        String actor,
        String activeDeptHeader,
        String sourceRef,
        String idempotencyKey
    ) {
        return startPinnedModelQuality(
            List.of(new PinnedQualityBinding(ruleId, ruleVersionId, bindingId)),
            actor,
            activeDeptHeader,
            sourceRef,
            idempotencyKey
        );
    }

    @Transactional
    public QualityWorkflowRunDto startPinnedModelQuality(
        List<PinnedQualityBinding> pins,
        String actor,
        String activeDeptHeader,
        String sourceRef,
        String idempotencyKey
    ) {
        return startPinnedModelQuality(
            pins,
            actor,
            activeDeptHeader,
            sourceRef,
            idempotencyKey,
            "MODEL_RELEASE",
            null,
            1,
            1,
            0,
            false
        );
    }

    private QualityWorkflowRunDto startPinnedModelQuality(
        List<PinnedQualityBinding> pins,
        String actor,
        String activeDeptHeader,
        String sourceRef,
        String idempotencyKey,
        String triggerType,
        UUID retryOfId,
        int attemptNo,
        int maxRetryAttempts,
        int retryBackoffSeconds,
        boolean allowHistoricalVersion
    ) {
        List<PinnedQualityBinding> normalizedPins = PinnedQualityCommandValidator.validateCommand(
            pins,
            MAX_RULES_PER_WORKFLOW
        );
        String normalizedKey = StringUtils.trimToEmpty(idempotencyKey);
        String normalizedRef = StringUtils.trimToEmpty(sourceRef);
        if (StringUtils.isBlank(actor)) {
            throw new IllegalArgumentException("模型质量工作流缺少执行人");
        }
        if (normalizedKey.isBlank() || normalizedKey.length() > 160) {
            throw new IllegalArgumentException("模型质量工作流幂等标识无效");
        }
        if (normalizedRef.isBlank() || normalizedRef.length() > 128) {
            throw new IllegalArgumentException("模型质量工作流来源标识无效");
        }
        if (normalizedPins.size() > 1 && normalizedRef.length() > 125) {
            throw new IllegalArgumentException("模型质量工作流来源标识过长");
        }
        String normalizedTriggerType = normalizeStatus(triggerType);
        String snapshotJson = PinnedQualitySnapshotCodec.encode(normalizedPins);
        GovQualityWorkflowRun replay = workflowRepository.findByIdempotencyKey(normalizedKey).orElse(null);
        if (replay != null) {
            datasetReadGuard.requireReadable(replay.getDatasetId(), activeDeptHeader);
            PinnedQualityCommandValidator.requireReplayScope(
                replay,
                normalizedPins,
                normalizedTriggerType,
                normalizedRef,
                retryOfId
            );
            return QualityWorkflowMapper.toDto(
                replay,
                qualityRunService.runsByWorkflow(replay.getId(), activeDeptHeader)
            );
        }

        UUID datasetId = null;
        for (PinnedQualityBinding pin : normalizedPins) {
            GovRuleBinding binding = bindingRepository
                .findById(pin.bindingId())
                .orElseThrow(() -> new EntityNotFoundException("模型质量规则绑定不存在"));
            PinnedQualityCommandValidator.validateBinding(pin, binding, allowHistoricalVersion);
            if (datasetId == null) {
                datasetId = binding.getDatasetId();
            } else if (!datasetId.equals(binding.getDatasetId())) {
                throw new IllegalStateException("同一模型质量工作流只能验证一个数据资产");
            }
        }
        datasetReadGuard.requireReadable(datasetId, activeDeptHeader);

        Instant now = Instant.now();
        UUID proposedId = UUID.randomUUID();
        UUID workflowRuleId = normalizedPins.size() == 1 ? normalizedPins.getFirst().ruleId() : null;
        int inserted = workflowRepository.insertQueued(
            proposedId,
            null,
            datasetId,
            workflowRuleId,
            retryOfId,
            attemptNo,
            maxRetryAttempts,
            retryBackoffSeconds,
            normalizedTriggerType,
            normalizedRef,
            normalizedKey,
            now,
            actor,
            now
        );
        GovQualityWorkflowRun workflow = workflowRepository
            .findByIdempotencyKey(normalizedKey)
            .orElseThrow(() -> new IllegalStateException("模型质量工作流账本写入失败"));
        if (inserted == 0) {
            PinnedQualityCommandValidator.requireReplayScope(
                workflow,
                normalizedPins,
                normalizedTriggerType,
                normalizedRef,
                retryOfId
            );
            return QualityWorkflowMapper.toDto(
                workflow,
                qualityRunService.runsByWorkflow(workflow.getId(), activeDeptHeader)
            );
        }
        workflow.setStatus("RUNNING");
        workflow.setStartedAt(now);
        workflow.setContextJson(snapshotJson);
        workflowRepository.save(workflow);

        List<QualityRunDto> ruleRuns = new ArrayList<>();
        int dispatchFailures = 0;
        for (int index = 0; index < normalizedPins.size(); index++) {
            PinnedQualityBinding pin = normalizedPins.get(index);
            String childTriggerRef = normalizedPins.size() == 1 ? normalizedRef : normalizedRef + index;
            QualityRunTriggerRequest request = new QualityRunTriggerRequest();
            request.setRuleId(pin.ruleId());
            request.setBindingId(pin.bindingId());
            request.setTriggerType(normalizedTriggerType);
            try {
                List<QualityRunDto> dispatched = qualityRunService.triggerPinnedWorkflowAuthorized(
                    request,
                    pin.ruleVersionId(),
                    actor,
                    activeDeptHeader,
                    workflow.getId(),
                    childTriggerRef
                );
                if (dispatched.size() != 1) {
                    dispatchFailures++;
                    log.warn(
                        "event=model_quality_workflow_dispatch_invalid workflowId={} bindingId={} runCount={}",
                        workflow.getId(),
                        pin.bindingId(),
                        dispatched.size()
                    );
                } else {
                    ruleRuns.addAll(dispatched);
                }
            } catch (RuntimeException ex) {
                dispatchFailures++;
                log.warn(
                    "event=model_quality_workflow_dispatch_failed workflowId={} bindingId={} errorType={}",
                    workflow.getId(),
                    pin.bindingId(),
                    ex.getClass().getSimpleName()
                );
            }
        }
        workflow.setExpectedRunCount(ruleRuns.size());
        workflow.setDispatchFailureCount(dispatchFailures);
        if (ruleRuns.isEmpty()) {
            workflow.setStatus("FAILED");
            workflow.setFinishedAt(Instant.now());
            workflow.setErrorCategory("PINNED_RULE_DISPATCH_INVALID");
            workflow.setMessage("模型质量规则未能形成执行记录");
        }
        workflowRepository.save(workflow);
        recordStartAudit(
            workflow,
            InvocationMode.AUTHORIZED,
            "RUNNING".equals(workflow.getStatus()) ? AuditStage.SUCCESS : AuditStage.FAIL
        );
        return QualityWorkflowMapper.toDto(workflow, ruleRuns);
    }

    @Transactional
    public QualityWorkflowRunDto retryAuthorized(
        UUID workflowRunId,
        String actor,
        String activeDeptHeader,
        String idempotencyKey
    ) {
        GovQualityWorkflowRun source = workflowRepository
            .findById(workflowRunId)
            .orElseThrow(() -> new EntityNotFoundException("质量工作流不存在"));
        datasetReadGuard.requireReadable(source.getDatasetId(), activeDeptHeader);
        if (!RETRYABLE_STATUSES.contains(normalizeStatus(source.getStatus()))) {
            throw new IllegalStateException("只有未通过或被阻断的质量验证可以重试");
        }
        int attemptNo = Math.max(1, source.getAttemptNo() == null ? 1 : source.getAttemptNo());
        int retryLimit = retryLimit(source.getMaxRetryAttempts());
        if (attemptNo > retryLimit) {
            throw new IllegalStateException("质量验证已达到最大重试次数");
        }
        int retryBackoffSeconds = retryBackoffSeconds(source.getRetryBackoffSeconds());
        Instant retryAvailableAt = source.getFinishedAt() == null
            ? Instant.now()
            : source.getFinishedAt().plusSeconds(retryBackoffSeconds);
        if (retryAvailableAt.isAfter(Instant.now())) {
            throw new IllegalStateException("质量验证仍在重试等待时间内");
        }
        if (PinnedQualityCommandValidator.isPinnedModelWorkflow(source)) {
            List<PinnedQualityBinding> pins = PinnedQualitySnapshotCodec.decode(
                source.getContextJson(),
                source.getRuleId()
            );
            return startPinnedModelQuality(
                pins,
                actor,
                activeDeptHeader,
                "quality-workflow:retry:" + source.getId(),
                idempotencyKey,
                "RETRY",
                source.getId(),
                attemptNo + 1,
                retryLimit,
                retryBackoffSeconds,
                true
            );
        }
        GovQualityTask syntheticTask = new GovQualityTask();
        syntheticTask.setId(source.getTaskId());
        syntheticTask.setDatasetId(source.getDatasetId());
        syntheticTask.setRuleId(source.getRuleId());
        syntheticTask.setMaxRetryAttempts(retryLimit);
        syntheticTask.setRetryBackoffSeconds(retryBackoffSeconds);
        return start(
            syntheticTask,
            actor,
            activeDeptHeader,
            "RETRY",
            idempotencyKey,
            InvocationMode.AUTHORIZED,
            source.getId(),
            attemptNo + 1,
            null,
            null,
            null
        );
    }

    @Transactional
    public QualityWorkflowRunDto cancelAuthorized(
        UUID workflowRunId,
        String actor,
        String activeDeptHeader
    ) {
        GovQualityWorkflowRun workflow = workflowRepository
            .findById(workflowRunId)
            .orElseThrow(() -> new EntityNotFoundException("质量工作流不存在"));
        datasetReadGuard.requireReadable(workflow.getDatasetId(), activeDeptHeader);
        if (!ACTIVE_STATUSES.contains(normalizeStatus(workflow.getStatus()))) {
            throw new IllegalStateException("只有排队中或运行中的质量验证可以取消");
        }
        workflow.setStatus("CANCELLED");
        workflow.setFinishedAt(Instant.now());
        workflow.setErrorCategory("USER_CANCELLED");
        workflow.setMessage("质量验证已由用户取消；已提交的规则执行事实仍保留");
        workflowRepository.save(workflow);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "取消数据质量工作流");
        payload.put("workflowId", workflow.getId().toString());
        payload.put("datasetId", workflow.getDatasetId().toString());
        payload.put("status", workflow.getStatus());
        payload.put("actor", actor);
        auditRecorder.recordAction(
            "GOV_QUALITY_WORKFLOW_CANCEL",
            AuditStage.SUCCESS,
            workflow.getId().toString(),
            payload
        );
        return QualityWorkflowMapper.toDto(
            workflow,
            qualityRunService.runsByWorkflow(workflow.getId(), activeDeptHeader)
        );
    }

    private QualityWorkflowRunDto start(
        GovQualityTask task,
        String actor,
        String activeDeptHeader,
        String triggerType,
        String idempotencyKey,
        InvocationMode invocationMode,
        UUID retryOfId,
        int attemptNo,
        Instant scheduledAt,
        String sourceRef,
        Map<String, Object> parameters
    ) {
        validateCommand(task, actor, triggerType, idempotencyKey);
        if (invocationMode == InvocationMode.AUTHORIZED) {
            datasetReadGuard.requireReadable(task.getDatasetId(), activeDeptHeader);
        } else {
            defaultLakeDatasetGuard.requireDefaultLakeDataset(task.getDatasetId());
        }

        Instant now = Instant.now();
        UUID proposedId = UUID.randomUUID();
        String normalizedKey = idempotencyKey.trim();
        String normalizedTriggerType = triggerType.trim().toUpperCase(Locale.ROOT);
        int maxRetryAttempts = retryLimit(task.getMaxRetryAttempts());
        int retryBackoffSeconds = retryBackoffSeconds(task.getRetryBackoffSeconds());
        String triggerRef = StringUtils.defaultIfBlank(
            StringUtils.trimToNull(sourceRef),
            workflowTriggerRef(proposedId, normalizedTriggerType)
        );
        if (triggerRef.length() > 128) {
            throw new IllegalArgumentException("质量工作流来源标识过长");
        }
        Instant effectiveScheduledAt = scheduledAt != null ? scheduledAt : now;
        int inserted = workflowRepository.insertQueued(
            proposedId,
            task.getId(),
            task.getDatasetId(),
            task.getRuleId(),
            retryOfId,
            attemptNo,
            maxRetryAttempts,
            retryBackoffSeconds,
            normalizedTriggerType,
            triggerRef,
            normalizedKey,
            effectiveScheduledAt,
            actor.trim(),
            now
        );
        GovQualityWorkflowRun workflow = workflowRepository.findByIdempotencyKey(normalizedKey).orElse(null);
        boolean replayedByKey = workflow != null;
        if (workflow == null && task.getId() != null) {
            workflow = workflowRepository
                .findFirstByTaskIdAndStatusInOrderByCreatedDateDesc(task.getId(), ACTIVE_STATUSES)
                .orElse(null);
        }
        if (workflow == null) {
            throw new IllegalStateException("质量工作流账本写入失败");
        }
        if (inserted == 0) {
            if (replayedByKey) {
                requireReplayScope(workflow, task, normalizedTriggerType, retryOfId, sourceRef);
            }
            return QualityWorkflowMapper.toDto(
                workflow,
                invocationMode == InvocationMode.AUTHORIZED
                    ? qualityRunService.runsByWorkflow(workflow.getId(), activeDeptHeader)
                    : qualityRunService.trustedRunsByWorkflow(workflow.getId())
            );
        }

        List<UUID> ruleIds = executableRuleIds(task);
        if (ruleIds.size() > MAX_RULES_PER_WORKFLOW) {
            workflow.setStatus("BLOCKED");
            workflow.setFinishedAt(Instant.now());
            workflow.setErrorCategory("QUALITY_WORKFLOW_RULE_LIMIT_EXCEEDED");
            workflow.setMessage("单次质量验证最多执行 100 条规则，请缩小运行策略范围");
            workflowRepository.save(workflow);
            recordStartAudit(workflow, invocationMode, AuditStage.FAIL);
            return QualityWorkflowMapper.toDto(workflow, List.of());
        }
        if (ruleIds.isEmpty()) {
            workflow.setStatus("BLOCKED");
            workflow.setFinishedAt(Instant.now());
            workflow.setErrorCategory("NO_EXECUTABLE_RULES");
            workflow.setMessage("当前数据资产没有已启用、已发布且已绑定的质量规则");
            workflowRepository.save(workflow);
            recordStartAudit(workflow, invocationMode, AuditStage.FAIL);
            return QualityWorkflowMapper.toDto(workflow, List.of());
        }

        workflow.setStatus("RUNNING");
        workflow.setStartedAt(Instant.now());
        workflowRepository.save(workflow);
        List<QualityRunDto> ruleRuns = new ArrayList<>();
        int dispatchFailures = 0;
        for (UUID ruleId : ruleIds) {
            try {
                QualityRunTriggerRequest request = new QualityRunTriggerRequest();
                request.setRuleId(ruleId);
                request.setDatasetId(task.getDatasetId());
                request.setTriggerType(normalizedTriggerType);
                request.setParameters(parameters);
                List<QualityRunDto> dispatched = switch (invocationMode) {
                    case SCHEDULED -> qualityRunService.triggerWorkflowScheduled(
                        request,
                        workflow.getId(),
                        workflow.getTriggerRef()
                    );
                    case TRUSTED_INGESTION -> qualityRunService.triggerWorkflowTrustedIngestion(
                        request,
                        workflow.getId(),
                        workflow.getTriggerRef()
                    );
                    case AUTHORIZED -> qualityRunService.triggerWorkflowAuthorized(
                        request,
                        actor,
                        activeDeptHeader,
                        workflow.getId(),
                        workflow.getTriggerRef()
                    );
                };
                ruleRuns.addAll(dispatched);
            } catch (RuntimeException ex) {
                dispatchFailures++;
                log.warn(
                    "event=quality_workflow_rule_dispatch_failed workflowId={} ruleId={} errorType={}",
                    workflow.getId(),
                    ruleId,
                    ex.getClass().getSimpleName()
                );
            }
        }
        workflow.setExpectedRunCount(ruleRuns.size());
        workflow.setDispatchFailureCount(dispatchFailures);
        if (ruleRuns.isEmpty()) {
            workflow.setStatus("FAILED");
            workflow.setFinishedAt(Instant.now());
            workflow.setErrorCategory("RULE_DISPATCH_FAILED");
            workflow.setMessage("质量规则未能进入执行队列");
        }
        workflowRepository.save(workflow);
        recordStartAudit(
            workflow,
            invocationMode,
            "RUNNING".equals(workflow.getStatus()) ? AuditStage.SUCCESS : AuditStage.FAIL
        );
        return QualityWorkflowMapper.toDto(workflow, ruleRuns);
    }

    private List<UUID> executableRuleIds(GovQualityTask task) {
        List<GovRuleBinding> bindings = bindingRepository.findWorkflowBindings(
            task.getDatasetId(),
            "PUBLISHED"
        );
        List<UUID> available = bindings == null
            ? List.of()
            : bindings
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
        if (task.getRuleId() == null) {
            return available;
        }
        if (!available.contains(task.getRuleId())) {
            throw new IllegalStateException("运行策略规则未启用、未发布或未绑定当前数据资产");
        }
        return List.of(task.getRuleId());
    }

    private void recordStartAudit(
        GovQualityWorkflowRun workflow,
        InvocationMode invocationMode,
        AuditStage stage
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean retry = workflow.getRetryOfId() != null;
        String actionCode = retry ? "GOV_QUALITY_WORKFLOW_RETRY" : "GOV_QUALITY_WORKFLOW_START";
        payload.put("summary", retry ? "重试数据质量工作流" : "启动数据质量工作流");
        payload.put("workflowId", workflow.getId().toString());
        payload.put("datasetId", workflow.getDatasetId().toString());
        payload.put("triggerType", workflow.getTriggerType());
        payload.put("status", workflow.getStatus());
        payload.put("expectedRunCount", workflow.getExpectedRunCount());
        payload.put("dispatchFailureCount", workflow.getDispatchFailureCount());
        if (invocationMode != InvocationMode.AUTHORIZED) {
            String machineActor = invocationMode == InvocationMode.SCHEDULED ? "scheduler" : "ingestion";
            auditRecorder.recordMachine(
                machineActor,
                workflow.getId() + ":START",
                workflow.getScheduledAt(),
                actionCode,
                stage,
                workflow.getId().toString(),
                payload
            );
        } else {
            auditRecorder.recordAction(
                actionCode,
                stage,
                workflow.getId().toString(),
                payload
            );
        }
    }

    private void validateCommand(GovQualityTask task, String actor, String triggerType, String idempotencyKey) {
        if (task == null || task.getDatasetId() == null) {
            throw new IllegalArgumentException("质量工作流缺少数据资产");
        }
        if (StringUtils.isBlank(actor)) {
            throw new IllegalArgumentException("质量工作流缺少执行人");
        }
        if (StringUtils.isBlank(triggerType) || triggerType.length() > 32) {
            throw new IllegalArgumentException("质量工作流触发类型无效");
        }
        if (StringUtils.isBlank(idempotencyKey) || idempotencyKey.length() > 160) {
            throw new IllegalArgumentException("质量工作流幂等标识无效");
        }
    }

    private void requireReplayScope(
        GovQualityWorkflowRun workflow,
        GovQualityTask task,
        String triggerType,
        UUID retryOfId,
        String sourceRef
    ) {
        boolean mismatch = !Objects.equals(workflow.getTaskId(), task.getId()) ||
            !Objects.equals(workflow.getDatasetId(), task.getDatasetId()) ||
            !normalizeStatus(triggerType).equals(normalizeStatus(workflow.getTriggerType())) ||
            !Objects.equals(workflow.getRetryOfId(), retryOfId);
        if (StringUtils.isNotBlank(sourceRef)) {
            mismatch = mismatch || !sourceRef.trim().equals(workflow.getTriggerRef());
        }
        if (mismatch) {
            throw new IllegalStateException("质量工作流幂等标识已用于其他验证");
        }
    }

    private String workflowTriggerRef(UUID workflowId, String triggerType) {
        return "quality-workflow:" + triggerType.toLowerCase(Locale.ROOT) + ":" + workflowId;
    }

    private int retryLimit(Integer value) {
        return Math.max(0, Math.min(value == null ? 1 : value, 5));
    }

    private int retryBackoffSeconds(Integer value) {
        return Math.max(0, Math.min(value == null ? 0 : value, 86_400));
    }

    private String normalizeStatus(String status) {
        return StringUtils.trimToEmpty(status).toUpperCase(Locale.ROOT);
    }
}
