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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_APPROVED = "APPROVED";
    public static final String TASK_REJECTED = "REJECTED";

    private static final List<String> EMPLOYEE_FORBIDDEN_LAYERS = List.of("ODS", "DWD", "DWS");

    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetGrantRepository grantRepository;
    private final CatalogDatasetAccessRequestRepository requestRepository;
    private final CatalogDatasetAccessTaskRepository taskRepository;
    private final AdminWorkflowConfigClient adminWorkflowConfigClient;

    public DatasetDataAccessApprovalService(
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetGrantRepository grantRepository,
        CatalogDatasetAccessRequestRepository requestRepository,
        CatalogDatasetAccessTaskRepository taskRepository,
        AdminWorkflowConfigClient adminWorkflowConfigClient
    ) {
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
        this.datasetRepository = datasetRepository;
        this.grantRepository = grantRepository;
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.adminWorkflowConfigClient = adminWorkflowConfigClient;
    }

    public record Decision(boolean allowed, String code, String message) {
        public static Decision allow() {
            return new Decision(true, null, null);
        }

        public static Decision deny(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    @Transactional(readOnly = true)
    public Decision checkDataAccess(CatalogDataset dataset, DataAction action, String activeDeptHeader) {
        if (dataset == null) {
            return Decision.deny(PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "只能查询已登记的数据资产");
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
        return Decision.allow();
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

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessRequest> listMyRequests() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        return requestRepository.findByRequesterUsernameIgnoreCaseOrTargetUsernameIgnoreCaseOrderByCreatedDateDesc(username, username);
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetAccessTask> listPendingTasksForCurrentUser(String activeDeptHeader) {
        if (isSuperAdmin() || SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER)) {
            // Institute leaders can see institute tasks; optionally also see dept tasks for convenience.
            List<CatalogDatasetAccessTask> tasks = new ArrayList<>();
            tasks.addAll(taskRepository.findPendingTasksForRole(AuthoritiesConstants.INST_LEADER, null));
            tasks.addAll(taskRepository.findPendingTasksForRole(AuthoritiesConstants.DEPT_LEADER, null));
            return tasks;
        }

        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_LEADER)) {
            String dept = trimToNull(resolveActiveDept(activeDeptHeader));
            return taskRepository.findPendingTasksForRole(AuthoritiesConstants.DEPT_LEADER, dept);
        }

        return List.of();
    }

    public CatalogDatasetAccessTask approveTask(UUID taskId, String notes, String activeDeptHeader) {
        CatalogDatasetAccessTask task = taskRepository.findById(taskId).orElseThrow();
        assertCanDecide(task, activeDeptHeader);

        if (!TASK_PENDING.equalsIgnoreCase(task.getStatus())) {
            return task;
        }
        task.setStatus(TASK_APPROVED);
        task.setDecidedAt(Instant.now());
        task.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        task.setDecisionNotes(trimToNull(notes));
        task = taskRepository.save(task);

        CatalogDatasetAccessRequest req = requestRepository.findById(task.getRequestId()).orElseThrow();
        Optional<CatalogDatasetAccessTask> next = taskRepository.findFirstPendingTask(req.getId());
        if (next.isEmpty()) {
            finalizeApprovedRequest(req, notes);
        }
        return task;
    }

    public CatalogDatasetAccessTask rejectTask(UUID taskId, String notes, String activeDeptHeader) {
        CatalogDatasetAccessTask task = taskRepository.findById(taskId).orElseThrow();
        assertCanDecide(task, activeDeptHeader);

        if (!TASK_PENDING.equalsIgnoreCase(task.getStatus())) {
            return task;
        }
        task.setStatus(TASK_REJECTED);
        task.setDecidedAt(Instant.now());
        task.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        task.setDecisionNotes(trimToNull(notes));
        task = taskRepository.save(task);

        CatalogDatasetAccessRequest req = requestRepository.findById(task.getRequestId()).orElseThrow();
        if (!REQUEST_REJECTED.equalsIgnoreCase(req.getStatus()) && !REQUEST_APPROVED.equalsIgnoreCase(req.getStatus())) {
            req.setStatus(REQUEST_REJECTED);
            req.setDecidedAt(Instant.now());
            req.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
            req.setDecisionNotes(trimToNull(notes));
            requestRepository.save(req);
        }
        return task;
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
        List<AdminWorkflowConfigClient.WorkflowTemplateDto> templates =
            adminWorkflowConfigClient.listEnabledTemplates("DATASET_DATA_ACCESS");
        if (templates.isEmpty()) {
            return List.of();
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        boolean instituteOwned = !StringUtils.hasText(ownerDept) || organizationVisibilityService.isRoot(ownerDept);
        DataLevel parsedLevel = DataLevel.normalize(dataset.getClassification());
        final DataLevel datasetLevel = parsedLevel != null ? parsedLevel : DataLevel.DATA_INTERNAL;

        AdminWorkflowConfigClient.WorkflowTemplateDto picked = templates
            .stream()
            .filter(t -> templateMatches(t, instituteOwned, datasetLevel))
            .max(Comparator.comparingInt(t -> t.priority() == null ? 0 : t.priority()))
            .orElse(null);
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
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        if (isSuperAdmin()) {
            return;
        }
        String role = task.getApproverRole();
        if (AuthoritiesConstants.INST_LEADER.equals(role)) {
            if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER)) {
                throw new IllegalStateException("当前账号无所级审批权限");
            }
            return;
        }
        if (AuthoritiesConstants.DEPT_LEADER.equals(role)) {
            if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_LEADER)) {
                return;
            }
            if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_LEADER)) {
                throw new IllegalStateException("当前账号无部门审批权限");
            }
            String dept = DepartmentUtils.normalize(resolveActiveDept(activeDeptHeader));
            String requiredDept = DepartmentUtils.normalize(task.getDeptCode());
            if (StringUtils.hasText(requiredDept) && !requiredDept.equalsIgnoreCase(dept)) {
                throw new IllegalStateException("当前账号无该部门审批权限");
            }
            return;
        }
        throw new IllegalStateException("未知审批角色配置");
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
