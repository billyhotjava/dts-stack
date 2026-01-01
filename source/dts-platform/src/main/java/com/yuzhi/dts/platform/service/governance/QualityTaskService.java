package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityTask;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityTaskRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QualityTaskService {

    private final GovQualityTaskRepository taskRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final QualityRunService qualityRunService;
    private final AuditService auditService;
    private final DataStandardSecurity security;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final AccessChecker accessChecker;

    public QualityTaskService(
        GovQualityTaskRepository taskRepository,
        GovRuleBindingRepository bindingRepository,
        CatalogDatasetRepository datasetRepository,
        QualityRunService qualityRunService,
        AuditService auditService,
        DataStandardSecurity security,
        OrganizationVisibilityService organizationVisibilityService,
        AccessChecker accessChecker
    ) {
        this.taskRepository = taskRepository;
        this.bindingRepository = bindingRepository;
        this.datasetRepository = datasetRepository;
        this.qualityRunService = qualityRunService;
        this.auditService = auditService;
        this.security = security;
        this.organizationVisibilityService = organizationVisibilityService;
        this.accessChecker = accessChecker;
    }

    @Transactional(readOnly = true)
    public List<GovQualityTask> list(String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        return taskRepository
            .findAll()
            .stream()
            .filter(task -> isOwnerDeptVisible(task != null ? task.getOwnerDept() : null, activeDept, instituteScope))
            .filter(task -> task != null && task.getDatasetId() != null)
            .filter(task ->
                datasetRepository
                    .findById(task.getDatasetId())
                    .filter(accessChecker::canRead)
                    .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
                    .isPresent()
            )
            .sorted(Comparator.comparing(task -> String.valueOf(task.getName()).toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
    }

    public GovQualityTask create(GovQualityTask request, String actor, String activeDeptHeader) {
        GovQualityTask task = new GovQualityTask();
        applyUpsert(task, request, activeDeptHeader);
        GovQualityTask saved = taskRepository.save(task);
        auditService.recordAs(
            actor,
            "CREATE",
            "governance.quality.task",
            "governance.quality.task",
            saved.getId().toString(),
            "SUCCESS",
            Map.of("summary", "创建质量巡检计划", "taskName", saved.getName()),
            null
        );
        return saved;
    }

    public GovQualityTask update(UUID id, GovQualityTask request, String actor, String activeDeptHeader) {
        GovQualityTask task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("巡检计划不存在"));
        ensureAccessible(task, activeDeptHeader);
        applyUpsert(task, request, activeDeptHeader);
        GovQualityTask saved = taskRepository.save(task);
        auditService.recordAs(
            actor,
            "UPDATE",
            "governance.quality.task",
            "governance.quality.task",
            id.toString(),
            "SUCCESS",
            Map.of("summary", "更新质量巡检计划", "taskName", saved.getName()),
            null
        );
        return saved;
    }

    public void delete(UUID id, String actor) {
        taskRepository.deleteById(id);
        auditService.recordAs(actor, "DELETE", "governance.quality.task", "governance.quality.task", id.toString(), "SUCCESS", Map.of("summary", "删除质量巡检计划"), null);
    }

    public GovQualityTask toggle(UUID id, boolean enabled, String actor, String activeDeptHeader) {
        GovQualityTask task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("巡检计划不存在"));
        ensureAccessible(task, activeDeptHeader);
        task.setEnabled(enabled);
        GovQualityTask saved = taskRepository.save(task);
        auditService.recordAs(
            actor,
            "UPDATE",
            "governance.quality.task",
            "governance.quality.task",
            id.toString(),
            "SUCCESS",
            Map.of("summary", enabled ? "启用质量巡检计划" : "停用质量巡检计划"),
            null
        );
        return saved;
    }

    public List<Map<String, Object>> trigger(UUID id, String actor, String activeDeptHeader) {
        GovQualityTask task = taskRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("巡检计划不存在"));
        ensureAccessible(task, activeDeptHeader);
        List<Map<String, Object>> runs = triggerInternal(task, actor, "MANUAL");
        task.setLastTriggeredAt(Instant.now());
        taskRepository.save(task);
        return runs;
    }

    public void runDueTasks() {
        List<GovQualityTask> tasks = taskRepository.findByEnabledTrue();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (GovQualityTask task : tasks) {
            if (task == null || task.getDatasetId() == null || !Boolean.TRUE.equals(task.getEnabled())) {
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
            try {
                triggerInternal(task, "system", "SCHEDULED");
                task.setLastTriggeredAt(now);
                taskRepository.save(task);
            } catch (Exception ex) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("summary", "执行质量巡检计划失败");
                payload.put("taskId", String.valueOf(task.getId()));
                payload.put("taskName", task.getName());
                payload.put("message", ex.getMessage());
                auditService.recordAs("system", "ERROR", "governance.quality.task", "governance.quality.task", String.valueOf(task.getId()), "FAIL", payload, null);
            }
        }
    }

    private List<Map<String, Object>> triggerInternal(GovQualityTask task, String actor, String triggerType) {
        UUID datasetId = task.getDatasetId();
        List<UUID> rulesToRun = new ArrayList<>();
        if (task.getRuleId() != null) {
            rulesToRun.add(task.getRuleId());
        } else {
            List<GovRuleBinding> bindings = bindingRepository.findByDatasetId(datasetId);
            for (GovRuleBinding binding : bindings) {
                try {
                    UUID rid = binding.getRuleVersion().getRule().getId();
                    if (rid != null) {
                        rulesToRun.add(rid);
                    }
                } catch (Exception ignored) {}
            }
            rulesToRun = rulesToRun.stream().filter(Objects::nonNull).distinct().toList();
        }
        if (rulesToRun.isEmpty()) {
            throw new IllegalStateException("未找到该数据集绑定的质量规则");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (UUID rid : rulesToRun) {
            try {
                QualityRunTriggerRequest req = new QualityRunTriggerRequest();
                req.setRuleId(rid);
                req.setDatasetId(datasetId);
                req.setTriggerType(StringUtils.defaultIfBlank(triggerType, "MANUAL"));
                Object runs = qualityRunService.trigger(req, actor);
                result.add(Map.of("ruleId", rid.toString(), "runs", runs));
            } catch (Exception ex) {
                result.add(Map.of("ruleId", rid.toString(), "error", StringUtils.defaultString(ex.getMessage(), "执行失败")));
            }
        }
        auditService.recordAs(
            actor,
            "EXECUTE",
            "governance.quality.task",
            "governance.quality.task",
            String.valueOf(task.getId()),
            "SUCCESS",
            Map.of("summary", "触发质量巡检计划", "taskName", task.getName(), "datasetId", String.valueOf(datasetId), "ruleCount", rulesToRun.size()),
            null
        );
        return result;
    }

    private void applyUpsert(GovQualityTask task, GovQualityTask request, String activeDeptHeader) {
        if (task == null || request == null) {
            return;
        }
        if (request.getDatasetId() == null) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        var dataset = datasetRepository
            .findById(request.getDatasetId())
            .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));
        if (!accessChecker.canRead(dataset)) {
            throw new AccessDeniedException("无权限访问该数据集");
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

        String requestedOwnerDept = StringUtils.trimToNull(request.getOwnerDept());
        if (security.hasInstituteScope()) {
            task.setOwnerDept(requestedOwnerDept);
            return;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (!org.springframework.util.StringUtils.hasText(activeDept)) {
            throw new AccessDeniedException("当前账号未配置所属部门，无法执行该操作");
        }
        if (!accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new AccessDeniedException("当前部门上下文不可访问该数据集");
        }
        if (requestedOwnerDept != null && !DepartmentUtils.matches(requestedOwnerDept, activeDept)) {
            throw new AccessDeniedException("仅允许设置为当前登录部门的巡检计划");
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
            throw new EntityNotFoundException("巡检计划不存在");
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        if (!isOwnerDeptVisible(task.getOwnerDept(), activeDept, instituteScope)) {
            throw new AccessDeniedException("当前账号无权访问该巡检计划");
        }
        if (task.getDatasetId() == null) {
            return;
        }
        datasetRepository
            .findById(task.getDatasetId())
            .filter(accessChecker::canRead)
            .filter(dataset -> instituteScope || accessChecker.departmentAllowed(dataset, activeDept))
            .orElseThrow(() -> new AccessDeniedException("当前账号无权访问该巡检计划关联的数据集"));
    }
}
