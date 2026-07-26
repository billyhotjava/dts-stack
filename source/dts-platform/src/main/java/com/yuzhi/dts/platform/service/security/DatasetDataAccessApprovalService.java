package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetAccessRequestRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetAccessTaskRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.PolicyErrorCodes;
import com.yuzhi.dts.platform.service.workflow.AdminWorkflowConfigClient;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleUsageRecorder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class DatasetDataAccessApprovalService {

    public enum DataAction {
        QUERY,
        PREVIEW,
    }

    public static final String GRANT_TYPE_SHARE = "SHARE";
    public static final String GRANT_TYPE_DATA_ACCESS = "DATA_ACCESS";

    public static final String REQUEST_PENDING = "PENDING";
    public static final String REQUEST_APPROVED = "APPROVED";
    public static final String REQUEST_REJECTED = "REJECTED";
    public static final String REQUEST_CANCELLED = "CANCELLED";

    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_APPROVED = "APPROVED";
    public static final String TASK_REJECTED = "REJECTED";
    public static final String TASK_SKIPPED = "SKIPPED";

    private static final List<String> EMPLOYEE_FORBIDDEN_LAYERS = List.of("ODS", "DWD", "DWS");

    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetGrantRepository grantRepository;
    private final CatalogDatasetAccessRequestRepository requestRepository;
    private final CatalogDatasetAccessTaskRepository taskRepository;
    private final AdminWorkflowConfigClient adminWorkflowConfigClient;
    private final CatalogLifecycleUsageRecorder lifecycleUsageRecorder;

    public DatasetDataAccessApprovalService(
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetGrantRepository grantRepository,
        CatalogDatasetAccessRequestRepository requestRepository,
        CatalogDatasetAccessTaskRepository taskRepository,
        AdminWorkflowConfigClient adminWorkflowConfigClient
    ) {
        this(
            accessChecker,
            organizationVisibilityService,
            datasetRepository,
            grantRepository,
            requestRepository,
            taskRepository,
            adminWorkflowConfigClient,
            null
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DatasetDataAccessApprovalService(
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetGrantRepository grantRepository,
        CatalogDatasetAccessRequestRepository requestRepository,
        CatalogDatasetAccessTaskRepository taskRepository,
        AdminWorkflowConfigClient adminWorkflowConfigClient,
        CatalogLifecycleUsageRecorder lifecycleUsageRecorder
    ) {
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.datasetRepository = datasetRepository;
        this.grantRepository = grantRepository;
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.adminWorkflowConfigClient = adminWorkflowConfigClient;
        this.lifecycleUsageRecorder = lifecycleUsageRecorder;
    }

    public record Decision(boolean allowed, String code, String message) {
        public static Decision allow() {
            return new Decision(true, null, null);
        }

        public static Decision deny(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    public record WorkflowPreview(
        String source,
        UUID templateId,
        String templateName,
        String ownerScope,
        String classificationMin,
        String classificationMax,
        List<WorkflowStepPreview> steps
    ) {}

    public record WorkflowStepPreview(Integer stepOrder, String approverRole, Boolean deptBinding, String deptCode) {}

    public record AccessRequestDetail(
        CatalogDatasetAccessRequest request,
        List<CatalogDatasetAccessTask> steps,
        CatalogDatasetAccessTask currentTask,
        String effectiveStatus,
        Map<String, Object> grant
    ) {}

    @Transactional(readOnly = true)
    public Decision checkDataAccess(CatalogDataset dataset, DataAction action, String activeDeptHeader) {
        if (dataset == null) {
            return Decision.deny(PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "只能查询已登记的数据资产");
        }
        if (!Boolean.TRUE.equals(dataset.getEnabled()) || lifecycleBlocksAccess(dataset.getLifecycleStatus())) {
            return Decision.deny(
                PolicyErrorCodes.RESOURCE_NOT_VISIBLE,
                "数据资产已归档、进入回收站或永久销毁，当前不可访问"
            );
        }

        // Layer restriction: do not allow employees to access base layers even if granted.
        if (!isDataMaintainer() && isEmployeeForbiddenLayer(dataset.getWarehouseLayer())) {
            return Decision.deny(PolicyErrorCodes.RBAC_DENY, "当前数据分层不允许普通员工访问数据内容");
        }

        // Keep existing ABAC/RBAC checks (classification + department context).
        if (!accessChecker.canRead(dataset)) {
            return Decision.deny(PolicyErrorCodes.RBAC_DENY, "无权限访问该数据集");
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        if (!accessChecker.departmentAllowed(dataset, activeDept)) {
            return Decision.deny(PolicyErrorCodes.INVALID_CONTEXT, "无权限访问该数据集");
        }

        // Data maintainers can query/preview without per-user permit.
        if (isDataMaintainer()) {
            recordUsage(dataset, action);
            return Decision.allow();
        }

        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        Instant now = Instant.now();

        boolean granted = switch (action) {
            case QUERY -> grantRepository.existsActiveQueryGrantForUser(dataset.getId(), userId, username, now);
            case PREVIEW -> grantRepository.existsActivePreviewGrantForUser(dataset.getId(), userId, username, now);
        };
        if (!granted) {
            return Decision.deny(PolicyErrorCodes.TEMP_PERMIT_REQUIRED, "需要审批授权后才能访问数据内容");
        }
        recordUsage(dataset, action);
        return Decision.allow();
    }

    private void recordUsage(CatalogDataset dataset, DataAction action) {
        if (lifecycleUsageRecorder == null) {
            return;
        }
        lifecycleUsageRecorder.record(
            dataset,
            action == DataAction.PREVIEW ? "DATA_PREVIEWED" : "DATA_QUERIED",
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );
    }

    public CatalogDatasetAccessRequest createRequest(
        CatalogDataset dataset,
        boolean canQuery,
        boolean canPreview,
        Instant validFrom,
        Instant validTo,
        String reason,
        String targetUserId,
        String targetUsername,
        String targetName,
        String targetDept,
        String activeDeptHeader
    ) {
        Objects.requireNonNull(dataset, "dataset is required");
        if (!Boolean.TRUE.equals(dataset.getEnabled()) || lifecycleBlocksAccess(dataset.getLifecycleStatus())) {
            throw new IllegalStateException("数据资产当前生命周期状态不允许申请访问");
        }
        if (!canQuery && !canPreview) {
            throw new IllegalArgumentException("至少需要申请一种权限（QUERY/ PREVIEW）");
        }
        if (validFrom == null || validTo == null || !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("授权有效期不合法");
        }
        String activeDept = resolveActiveDept(activeDeptHeader);
        assertCanCreateProxyRequest(activeDept, targetDept);
        if (isEmployeeForbiddenLayer(dataset.getWarehouseLayer())) {
            throw new IllegalStateException("当前数据分层不允许普通员工申请访问数据内容");
        }
        if (!accessChecker.canRead(dataset)) {
            throw new IllegalStateException("无权限申请访问该数据集");
        }
        if (!accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new IllegalStateException("无权限申请访问该数据集");
        }

        String requesterId = SecurityUtils.getCurrentUserId().orElse(null);
        String requesterUsername = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(requesterUsername)) {
            throw new IllegalStateException("缺少用户身份信息");
        }
        String normalizedTargetUsername = trimToNull(targetUsername);
        if (!StringUtils.hasText(normalizedTargetUsername)) {
            throw new IllegalArgumentException("请选择申请对象（用户名）");
        }

        CatalogDatasetAccessRequest req = new CatalogDatasetAccessRequest();
        req.setDatasetId(dataset.getId());
        req.setDatasetName(trimToNull(dataset.getName()));
        req.setOwnerDept(trimToNull(dataset.getOwnerDept()));
        req.setClassification(trimToNull(dataset.getClassification()));
        req.setWarehouseLayer(trimToNull(dataset.getWarehouseLayer()));
        req.setRequesterId(trimToNull(requesterId));
        req.setRequesterUsername(trimToNull(requesterUsername));
        req.setRequesterName(trimToNull(SecurityUtils.getCurrentUserDisplayName().orElse(null)));
        req.setRequesterDept(trimToNull(activeDept));
        req.setTargetUserId(trimToNull(targetUserId));
        req.setTargetUsername(normalizedTargetUsername);
        req.setTargetName(trimToNull(targetName));
        req.setTargetDept(trimToNull(targetDept));
        req.setCanQuery(Boolean.valueOf(canQuery));
        req.setCanPreview(Boolean.valueOf(canPreview));
        req.setReason(trimToNull(reason));
        req.setStatus(REQUEST_PENDING);
        req.setValidFrom(validFrom);
        req.setValidTo(validTo);
        req = requestRepository.save(req);

        List<CatalogDatasetAccessTask> tasks = buildApprovalTasks(req, dataset);
        taskRepository.saveAll(tasks);
        return req;
    }

    private static boolean lifecycleBlocksAccess(String lifecycleStatus) {
        if (!StringUtils.hasText(lifecycleStatus)) {
            return false;
        }
        return List.of(
            "ARCHIVED",
            "TRASH_REQUESTED",
            "TRASHED",
            "DISPOSE_REQUESTED",
            "DISPOSED",
            "DESTROYED"
        ).contains(lifecycleStatus.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessRequest> listMyRequests(String status, String keyword, UUID datasetId) {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        String normalizedStatus = trimToNull(status);
        String normalizedKeyword = trimToNull(keyword);
        return requestRepository
            .findByRequesterUsernameIgnoreCaseOrTargetUsernameIgnoreCaseOrderByCreatedDateDesc(username, username)
            .stream()
            .filter(req -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(trimToNull(req.getStatus())))
            .filter(req -> datasetId == null || datasetId.equals(req.getDatasetId()))
            .filter(req -> {
                if (normalizedKeyword == null) {
                    return true;
                }
                String text =
                    (
                        String.valueOf(req.getDatasetName()) +
                        " " +
                        String.valueOf(req.getRequesterName()) +
                        " " +
                        String.valueOf(req.getRequesterUsername()) +
                        " " +
                        String.valueOf(req.getTargetName()) +
                        " " +
                        String.valueOf(req.getTargetUsername())
                    )
                        .toLowerCase(Locale.ROOT);
                return text.contains(normalizedKeyword.toLowerCase(Locale.ROOT));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessTask> listPendingTasksForCurrentUser(String activeDeptHeader) {
        if (isSuperAdmin() || SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER)) {
            // Institute leaders can see institute tasks; optionally also see dept tasks for convenience.
            List<CatalogDatasetAccessTask> tasks = new ArrayList<>();
            tasks.addAll(taskRepository.findPendingTasksForRole(AuthoritiesConstants.INST_LEADER));
            tasks.addAll(taskRepository.findPendingTasksForRole(AuthoritiesConstants.DEPT_LEADER));
            return tasks;
        }

        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_LEADER)) {
            String dept = trimToNull(resolveActiveDept(activeDeptHeader));
            if (dept == null) {
                return taskRepository.findPendingTasksForRole(AuthoritiesConstants.DEPT_LEADER);
            }
            String normalized = dept.toLowerCase(Locale.ROOT);
            return taskRepository.findPendingTasksForRoleAndDept(AuthoritiesConstants.DEPT_LEADER, normalized);
        }

        return List.of();
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessTask> listDoneTasksForCurrentUser() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        return taskRepository.findDecidedTasksForUser(username);
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessTask> listTasksForRequest(UUID requestId) {
        if (requestId == null) {
            return List.of();
        }
        return taskRepository.findByRequestIdOrderByStepOrderAsc(requestId);
    }

    public CatalogDatasetAccessTask approveTask(UUID taskId, String notes, String activeDeptHeader) {
        return decideTask(taskId, true, notes, activeDeptHeader);
    }

    public CatalogDatasetAccessTask rejectTask(UUID taskId, String notes, String activeDeptHeader) {
        return decideTask(taskId, false, notes, activeDeptHeader);
    }

    public CatalogDatasetAccessTask decideTask(UUID taskId, boolean approved, String notes, String activeDeptHeader) {
        CatalogDatasetAccessTask task = taskRepository.findById(taskId).orElseThrow();
        assertCanDecide(task, activeDeptHeader);
        if (!TASK_PENDING.equalsIgnoreCase(task.getStatus())) {
            return task;
        }
        String actor = SecurityUtils.getCurrentUserLogin().orElse(null);
        task.setStatus(approved ? TASK_APPROVED : TASK_REJECTED);
        task.setDecidedAt(Instant.now());
        task.setDecidedBy(actor);
        task.setDecisionNotes(trimToNull(notes));
        task = taskRepository.save(task);

        CatalogDatasetAccessRequest req = requestRepository.findById(task.getRequestId()).orElseThrow();
        if (approved) {
            Optional<CatalogDatasetAccessTask> next = taskRepository.findFirstPendingTask(req.getId());
            if (next.isEmpty()) {
                finalizeApprovedRequest(req, notes);
            }
        } else if (!REQUEST_REJECTED.equalsIgnoreCase(req.getStatus()) && !REQUEST_APPROVED.equalsIgnoreCase(req.getStatus())) {
            req.setStatus(REQUEST_REJECTED);
            req.setDecidedAt(Instant.now());
            req.setDecidedBy(actor);
            req.setDecisionNotes(trimToNull(notes));
            requestRepository.save(req);
            markRemainingTasksSkipped(req.getId(), actor, notes);
        }
        return task;
    }

    public CatalogDatasetAccessRequest cancelRequest(UUID requestId, String notes, String activeDeptHeader) {
        CatalogDatasetAccessRequest req = requestRepository.findById(requestId).orElseThrow();
        if (!REQUEST_PENDING.equalsIgnoreCase(trimToNull(req.getStatus()))) {
            throw new IllegalStateException("仅待审批申请可撤回");
        }
        String actor = trimToNull(SecurityUtils.getCurrentUserLogin().orElse(null));
        if (
            actor == null ||
            (!actor.equalsIgnoreCase(trimToNull(req.getRequesterUsername())) &&
                !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS))
        ) {
            throw new IllegalStateException("无权撤回该申请");
        }
        if (!canReadRequest(req, activeDeptHeader)) {
            throw new IllegalStateException("无权限操作该申请");
        }
        req.setStatus(REQUEST_CANCELLED);
        req.setDecidedAt(Instant.now());
        req.setDecidedBy(actor);
        req.setDecisionNotes(trimToNull(notes));
        req = requestRepository.save(req);
        markRemainingTasksSkipped(req.getId(), actor, notes);
        return req;
    }

    @Transactional(readOnly = true)
    public AccessRequestDetail getRequestDetail(UUID requestId, String activeDeptHeader) {
        CatalogDatasetAccessRequest req = requestRepository.findById(requestId).orElseThrow();
        if (!canReadRequest(req, activeDeptHeader)) {
            throw new IllegalStateException("无权限查看该申请");
        }
        List<CatalogDatasetAccessTask> steps = listTasksForRequest(requestId);
        CatalogDatasetAccessTask currentTask = steps.stream().filter(task -> TASK_PENDING.equalsIgnoreCase(task.getStatus())).findFirst().orElse(null);
        CatalogDatasetGrant grant = grantRepository.findFirstBySourceRequestIdOrderByCreatedDateDesc(requestId).orElse(null);
        String effectiveStatus = calculateEffectiveStatus(req, grant);
        Map<String, Object> grantView = grantToMap(grant);
        return new AccessRequestDetail(req, steps, currentTask, effectiveStatus, grantView);
    }

    private boolean canReadRequest(CatalogDatasetAccessRequest req, String activeDeptHeader) {
        if (req == null) {
            return false;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            return true;
        }
        String login = trimToNull(SecurityUtils.getCurrentUserLogin().orElse(null));
        if (login != null && (login.equalsIgnoreCase(trimToNull(req.getRequesterUsername())) || login.equalsIgnoreCase(trimToNull(req.getTargetUsername())))) {
            return true;
        }
        List<CatalogDatasetAccessTask> tasks = listTasksForRequest(req.getId());
        return tasks.stream().anyMatch(task -> canDecide(task, activeDeptHeader));
    }

    private String calculateEffectiveStatus(CatalogDatasetAccessRequest req, CatalogDatasetGrant grant) {
        String status = trimToNull(req != null ? req.getStatus() : null);
        if (status == null) {
            return "UNKNOWN";
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!REQUEST_APPROVED.equals(normalized)) {
            return normalized;
        }
        if (grant == null) {
            return "GRANT_MISSING";
        }
        Instant now = Instant.now();
        if (grant.getValidFrom() != null && now.isBefore(grant.getValidFrom())) {
            return "WAITING_EFFECTIVE";
        }
        if (grant.getValidTo() != null && now.isAfter(grant.getValidTo())) {
            return "EXPIRED";
        }
        return "EFFECTIVE";
    }

    private Map<String, Object> grantToMap(CatalogDatasetGrant grant) {
        if (grant == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (grant.getId() != null) {
            map.put("id", grant.getId().toString());
        }
        map.put("grantType", grant.getGrantType());
        map.put("granteeUsername", grant.getGranteeUsername());
        map.put("granteeName", grant.getGranteeName());
        map.put("granteeDept", grant.getGranteeDept());
        map.put("canQuery", grant.getCanQuery());
        map.put("canPreview", grant.getCanPreview());
        map.put("validFrom", grant.getValidFrom());
        map.put("validTo", grant.getValidTo());
        map.put("sourceRequestId", grant.getSourceRequestId() != null ? grant.getSourceRequestId().toString() : null);
        return map;
    }

    private void markRemainingTasksSkipped(UUID requestId, String actor, String notes) {
        List<CatalogDatasetAccessTask> pending = taskRepository.findPendingTasks(requestId);
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        String skipNotes = trimToNull(notes) != null ? "SKIPPED: " + trimToNull(notes) : "SKIPPED: 已终止";
        for (CatalogDatasetAccessTask task : pending) {
            task.setStatus(TASK_SKIPPED);
            task.setDecidedBy(actor);
            task.setDecidedAt(now);
            task.setDecisionNotes(skipNotes);
        }
        taskRepository.saveAll(pending);
    }

    private void finalizeApprovedRequest(CatalogDatasetAccessRequest req, String notes) {
        if (REQUEST_APPROVED.equalsIgnoreCase(req.getStatus())) {
            return;
        }
        req.setStatus(REQUEST_APPROVED);
        req.setDecidedAt(Instant.now());
        req.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        req.setDecisionNotes(trimToNull(notes));
        requestRepository.save(req);

        upsertGrant(req);
    }

    private void upsertGrant(CatalogDatasetAccessRequest req) {
        UUID datasetId = req.getDatasetId();
        if (datasetId == null) {
            return;
        }
        String granteeId = trimToNull(req.getTargetUserId());
        String username = trimToNull(req.getTargetUsername());
        String displayName = trimToNull(req.getTargetName());
        String dept = trimToNull(req.getTargetDept());
        if (!StringUtils.hasText(username)) {
            granteeId = trimToNull(req.getRequesterId());
            username = trimToNull(req.getRequesterUsername());
            displayName = trimToNull(req.getRequesterName());
            dept = trimToNull(req.getRequesterDept());
        }
        if (!StringUtils.hasText(username)) return;
        CatalogDatasetGrant grant = grantRepository
            .findLatestDataAccessGrantForUser(datasetId, granteeId, username)
            .orElseGet(CatalogDatasetGrant::new);

        if (grant.getDataset() == null) {
            grant.setDataset(datasetRepository.getReferenceById(datasetId));
        }
        if (!StringUtils.hasText(grant.getGranteeUsername())) {
            grant.setGranteeUsername(username);
        }
        if (!StringUtils.hasText(grant.getGranteeId()) && StringUtils.hasText(granteeId)) {
            grant.setGranteeId(granteeId);
        }
        if (!StringUtils.hasText(grant.getGranteeName()) && StringUtils.hasText(displayName)) {
            grant.setGranteeName(displayName);
        }
        if (!StringUtils.hasText(grant.getGranteeDept()) && StringUtils.hasText(dept)) {
            grant.setGranteeDept(dept);
        }
        grant.setGrantType(GRANT_TYPE_DATA_ACCESS);
        grant.setCanQuery(Boolean.TRUE.equals(req.getCanQuery()));
        grant.setCanPreview(Boolean.TRUE.equals(req.getCanPreview()));
        grant.setValidFrom(req.getValidFrom());
        grant.setValidTo(req.getValidTo());
        grant.setSourceRequestId(req.getId());
        grantRepository.save(grant);
    }

    private List<CatalogDatasetAccessTask> buildApprovalTasks(CatalogDatasetAccessRequest req, CatalogDataset dataset) {
        List<CatalogDatasetAccessTask> configured = buildApprovalTasksFromAdminConfig(req, dataset);
        if (!configured.isEmpty()) {
            return configured;
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        boolean instituteOwned = !StringUtils.hasText(ownerDept) || organizationVisibilityService.isRoot(ownerDept);
        boolean highSensitivity = isHighSensitivity(dataset.getClassification());

        List<String> chain = new ArrayList<>();
        if (instituteOwned) {
            chain.add(AuthoritiesConstants.INST_LEADER);
        } else {
            chain.add(AuthoritiesConstants.DEPT_LEADER);
            if (highSensitivity) {
                chain.add(AuthoritiesConstants.INST_LEADER);
            }
        }

        List<CatalogDatasetAccessTask> tasks = new ArrayList<>();
        int i = 1;
        for (String role : chain) {
            CatalogDatasetAccessTask task = new CatalogDatasetAccessTask();
            task.setRequestId(req.getId());
            task.setStepOrder(i++);
            task.setApproverRole(role);
            task.setDeptCode(AuthoritiesConstants.DEPT_LEADER.equals(role) ? ownerDept : null);
            task.setStatus(TASK_PENDING);
            tasks.add(task);
        }
        return tasks;
    }

    private List<CatalogDatasetAccessTask> buildApprovalTasksFromAdminConfig(CatalogDatasetAccessRequest req, CatalogDataset dataset) {
        if (adminWorkflowConfigClient == null || dataset == null || req == null) {
            return List.of();
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        AdminWorkflowConfigClient.WorkflowTemplateDto picked = pickAdminTemplate(dataset);
        if (picked == null || picked.steps() == null || picked.steps().isEmpty()) {
            return List.of();
        }

        List<CatalogDatasetAccessTask> tasks = new ArrayList<>();
        int i = 1;
        for (AdminWorkflowConfigClient.WorkflowStepDto step : picked.steps()) {
            if (step == null || !StringUtils.hasText(step.approverRole())) {
                continue;
            }
            CatalogDatasetAccessTask task = new CatalogDatasetAccessTask();
            task.setRequestId(req.getId());
            task.setStepOrder(i++);
            task.setApproverRole(step.approverRole().trim());
            boolean deptBinding = step.deptBinding() != null && step.deptBinding().booleanValue();
            task.setDeptCode(deptBinding ? ownerDept : null);
            task.setStatus(TASK_PENDING);
            tasks.add(task);
        }
        return tasks;
    }

    @Transactional(readOnly = true)
    public WorkflowPreview previewWorkflow(CatalogDataset dataset) {
        if (dataset == null) {
            return new WorkflowPreview("DEFAULT", null, null, null, null, null, List.of());
        }
        AdminWorkflowConfigClient.WorkflowTemplateDto picked = pickAdminTemplate(dataset);
        List<WorkflowStepPreview> steps = buildPreviewStepsFromTemplate(picked, dataset);
        if (!steps.isEmpty()) {
            return new WorkflowPreview(
                "ADMIN_CONFIG",
                picked != null ? picked.id() : null,
                picked != null ? picked.name() : null,
                picked != null ? picked.ownerScope() : null,
                picked != null ? picked.classificationMin() : null,
                picked != null ? picked.classificationMax() : null,
                steps
            );
        }
        return new WorkflowPreview("DEFAULT", null, null, null, null, null, buildDefaultPreviewSteps(dataset));
    }

    private AdminWorkflowConfigClient.WorkflowTemplateDto pickAdminTemplate(CatalogDataset dataset) {
        if (adminWorkflowConfigClient == null || dataset == null) {
            return null;
        }
        List<AdminWorkflowConfigClient.WorkflowTemplateDto> templates =
            adminWorkflowConfigClient.listEnabledTemplates("DATASET_DATA_ACCESS");
        if (templates.isEmpty()) {
            return null;
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        boolean instituteOwned = !StringUtils.hasText(ownerDept) || organizationVisibilityService.isRoot(ownerDept);
        DataLevel parsedLevel = DataLevel.normalize(dataset.getClassification());
        final DataLevel datasetLevel = parsedLevel != null ? parsedLevel : DataLevel.DATA_INTERNAL;

        return templates
            .stream()
            .filter(t -> templateMatches(t, instituteOwned, datasetLevel))
            .max(Comparator.comparingInt(t -> t.priority() == null ? 0 : t.priority()))
            .orElse(null);
    }

    private List<WorkflowStepPreview> buildPreviewStepsFromTemplate(
        AdminWorkflowConfigClient.WorkflowTemplateDto picked,
        CatalogDataset dataset
    ) {
        if (picked == null || picked.steps() == null || picked.steps().isEmpty()) {
            return List.of();
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        List<WorkflowStepPreview> steps = new ArrayList<>();
        int index = 1;
        for (AdminWorkflowConfigClient.WorkflowStepDto step : picked.steps()) {
            if (step == null || !StringUtils.hasText(step.approverRole())) {
                continue;
            }
            boolean deptBinding = step.deptBinding() != null && step.deptBinding().booleanValue();
            Integer order = step.stepOrder() != null ? step.stepOrder() : index;
            steps.add(new WorkflowStepPreview(order, step.approverRole().trim(), deptBinding, deptBinding ? ownerDept : null));
            index++;
        }
        steps.sort(Comparator.comparingInt(s -> s.stepOrder() != null ? s.stepOrder() : 0));
        return steps;
    }

    private List<WorkflowStepPreview> buildDefaultPreviewSteps(CatalogDataset dataset) {
        if (dataset == null) {
            return List.of();
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        boolean instituteOwned = !StringUtils.hasText(ownerDept) || organizationVisibilityService.isRoot(ownerDept);
        boolean highSensitivity = isHighSensitivity(dataset.getClassification());

        List<WorkflowStepPreview> steps = new ArrayList<>();
        int i = 1;
        if (instituteOwned) {
            steps.add(new WorkflowStepPreview(i++, AuthoritiesConstants.INST_LEADER, false, null));
        } else {
            steps.add(new WorkflowStepPreview(i++, AuthoritiesConstants.DEPT_LEADER, true, ownerDept));
            if (highSensitivity) {
                steps.add(new WorkflowStepPreview(i++, AuthoritiesConstants.INST_LEADER, false, null));
            }
        }
        return steps;
    }

    private boolean templateMatches(
        AdminWorkflowConfigClient.WorkflowTemplateDto template,
        boolean instituteOwned,
        DataLevel datasetLevel
    ) {
        if (template == null) return false;
        String scope = template.ownerScope();
        if (StringUtils.hasText(scope)) {
            String normalized = scope.trim().toUpperCase(Locale.ROOT);
            if (!"ANY".equals(normalized)) {
                if ("INST".equals(normalized) && !instituteOwned) return false;
                if ("DEPT".equals(normalized) && instituteOwned) return false;
            }
        }

        DataLevel min = DataLevel.normalize(template.classificationMin());
        DataLevel max = DataLevel.normalize(template.classificationMax());
        int rank = datasetLevel != null ? datasetLevel.rank() : DataLevel.DATA_INTERNAL.rank();
        if (min != null && rank < min.rank()) return false;
        if (max != null && rank > max.rank()) return false;
        return true;
    }

    private void assertCanDecide(CatalogDatasetAccessTask task, String activeDeptHeader) {
        if (!canDecide(task, activeDeptHeader)) {
            throw new IllegalStateException("当前账号无审批权限");
        }
    }

    private boolean canDecide(CatalogDatasetAccessTask task, String activeDeptHeader) {
        if (task == null) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        String role = trimToNull(task.getApproverRole());
        if (AuthoritiesConstants.INST_LEADER.equals(role)) {
            return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER);
        }
        if (AuthoritiesConstants.DEPT_LEADER.equals(role)) {
            if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER)) {
                return true;
            }
            if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_LEADER)) {
                return false;
            }
            String dept = DepartmentUtils.normalize(resolveActiveDept(activeDeptHeader));
            String requiredDept = DepartmentUtils.normalize(task.getDeptCode());
            return !StringUtils.hasText(requiredDept) || requiredDept.equalsIgnoreCase(dept);
        }
        return false;
    }

    private void assertCanCreateProxyRequest(String activeDept, String targetDept) {
        boolean canCreate =
            isSuperAdmin() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_DATA_OWNER, AuthoritiesConstants.DEPT_DATA_OWNER);
        if (!canCreate) {
            throw new IllegalStateException("普通员工不支持自助申请，请联系数据管理员代申请");
        }

        boolean isDeptOwnerOnly =
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_DATA_OWNER) &&
            !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_DATA_OWNER) &&
            !isSuperAdmin();
        if (!isDeptOwnerOnly) {
            return;
        }
        String active = DepartmentUtils.normalize(activeDept);
        if (!StringUtils.hasText(active) || organizationVisibilityService.isRoot(active)) {
            throw new IllegalStateException("部门数据管理员缺少有效的部门上下文（X-Active-Dept）");
        }
        String target = DepartmentUtils.normalize(targetDept);
        if (!StringUtils.hasText(target)) {
            throw new IllegalStateException("缺少申请对象部门信息，请从用户目录选择账号");
        }
        if (!active.equalsIgnoreCase(target)) {
            throw new IllegalStateException("部门数据管理员只能代本部门用户提交申请");
        }
    }

    private boolean isDataMaintainer() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
    }

    private boolean isSuperAdmin() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OP_ADMIN);
    }

    private boolean isEmployeeForbiddenLayer(String layer) {
        if (!StringUtils.hasText(layer)) {
            return false;
        }
        String normalized = layer.trim().toUpperCase(Locale.ROOT);
        return EMPLOYEE_FORBIDDEN_LAYERS.contains(normalized);
    }

    private boolean isHighSensitivity(String classification) {
        DataLevel level = DataLevel.normalize(classification);
        if (level == null) {
            return false;
        }
        return level.rank() >= DataLevel.DATA_SECRET.rank();
    }

    private String resolveActiveDept(String activeDeptHeader) {
        if (StringUtils.hasText(activeDeptHeader)) {
            String trimmed = activeDeptHeader.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("dept_code");
                if (v != null) {
                    String text = String.valueOf(v).trim();
                    return text.isEmpty() ? null : text;
                }
            } else if (auth != null && auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute("dept_code");
                if (v != null) {
                    String text = String.valueOf(v).trim();
                    return text.isEmpty() ? null : text;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
