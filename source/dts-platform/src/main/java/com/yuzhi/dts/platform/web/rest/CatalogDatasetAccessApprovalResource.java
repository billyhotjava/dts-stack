package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetAccessRequestRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.DatasetDataAccessApprovalService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/access")
@Transactional
public class CatalogDatasetAccessApprovalResource {

    private final CatalogDatasetRepository datasetRepository;
    private final DatasetDataAccessApprovalService approvalService;
    private final AuditService auditService;
    private final CatalogDatasetAccessRequestRepository requestRepository;

    public CatalogDatasetAccessApprovalResource(
        CatalogDatasetRepository datasetRepository,
        DatasetDataAccessApprovalService approvalService,
        AuditService auditService,
        CatalogDatasetAccessRequestRepository requestRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.requestRepository = requestRepository;
    }

    @GetMapping("/requests/mine")
    public ApiResponse<Map<String, Object>> listMyRequests(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestParam(name = "page", required = false, defaultValue = "1") int page,
        @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        List<CatalogDatasetAccessRequest> list = approvalService.listMyRequests(status, keyword, datasetId);
        Map<String, Object> paged = toPage(list, page, size);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", ((List<?>) paged.getOrDefault("content", List.of())).size());
        meta.put("total", paged.getOrDefault("total", 0));
        if (status != null) {
            meta.put("status", status);
        }
        if (keyword != null) {
            meta.put("keyword", keyword);
        }
        meta.put("page", page);
        meta.put("size", size);
        auditService.record("READ", "catalog.dataset.access.request", "catalog.dataset.access.request", "mine", "SUCCESS", meta);
        return ApiResponses.ok(paged);
    }

    @GetMapping("/requests/{id}")
    public ApiResponse<DatasetDataAccessApprovalService.AccessRequestDetail> getRequestDetail(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DatasetDataAccessApprovalService.AccessRequestDetail detail = approvalService.getRequestDetail(id, activeDept);
            auditService.record("READ", "catalog.dataset.access.request", "catalog.dataset.access.request", id.toString(), "SUCCESS", Map.of());
            return ApiResponses.ok(detail);
        } catch (RuntimeException ex) {
            auditService.record(
                "READ",
                "catalog.dataset.access.request",
                "catalog.dataset.access.request",
                id.toString(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        }
    }

    @GetMapping("/workflow/preview")
    public ApiResponse<DatasetDataAccessApprovalService.WorkflowPreview> previewWorkflow(@RequestParam UUID datasetId) {
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            return ApiResponses.error("数据集不存在或已被删除");
        }
        DatasetDataAccessApprovalService.WorkflowPreview preview = approvalService.previewWorkflow(dataset);
        auditService.record("READ", "catalog.dataset.access.workflow", "catalog.dataset.access.workflow", datasetId.toString(), "SUCCESS", Map.of());
        return ApiResponses.ok(preview);
    }

    @PostMapping("/requests")
    public ApiResponse<CatalogDatasetAccessRequest> createRequest(
        @Valid @RequestBody CreateDatasetAccessRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepository.findById(body.datasetId()).orElse(null);
        if (dataset == null) {
            return ApiResponses.error("数据集不存在或已被删除");
        }
        Instant now = Instant.now();
        Instant validFrom = body.validFrom() != null ? body.validFrom() : now;
        Instant validTo = body.validTo() != null ? body.validTo() : now.plus(7, ChronoUnit.DAYS);
        try {
            CatalogDatasetAccessRequest saved = approvalService.createRequest(
                dataset,
                Boolean.TRUE.equals(body.canQuery()),
                Boolean.TRUE.equals(body.canPreview()),
                validFrom,
                validTo,
                body.reason(),
                body.targetUserId(),
                body.targetUsername(),
                body.targetName(),
                body.targetDept(),
                activeDept
            );
            auditService.record(
                "CREATE",
                "catalog.dataset.access.request",
                "catalog.dataset.access.request",
                saved.getId().toString(),
                "SUCCESS",
                Map.of(
                    "datasetId",
                    body.datasetId() != null ? body.datasetId().toString() : "",
                    "targetUsername",
                    body.targetUsername() != null ? body.targetUsername() : "",
                    "requester",
                    saved.getRequesterUsername() != null ? saved.getRequesterUsername() : ""
                )
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            auditService.record(
                "CREATE",
                "catalog.dataset.access.request",
                "catalog.dataset.access.request",
                body.datasetId() != null ? body.datasetId().toString() : "unknown",
                "FAILED",
                meta
            );
            return ApiResponses.error(ex.getMessage());
        }
    }

    @GetMapping("/tasks/pending")
    public ApiResponse<Map<String, Object>> listPendingTasks(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestParam(name = "page", required = false, defaultValue = "1") int page,
        @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        List<CatalogDatasetAccessTask> tasks = approvalService.listPendingTasksForCurrentUser(activeDept);
        List<Map<String, Object>> views = tasks
            .stream()
            .map(task -> {
                CatalogDatasetAccessRequest req = task.getRequestId() != null ? requestRepository.findById(task.getRequestId()).orElse(null) : null;
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("task", task);
                dto.put("request", req);
                return dto;
            })
            .filter(view -> matchTaskView(view, keyword, datasetId, null))
            .toList();
        Map<String, Object> paged = toPage(views, page, size);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", ((List<?>) paged.getOrDefault("content", List.of())).size());
        meta.put("total", paged.getOrDefault("total", 0));
        meta.put("page", page);
        meta.put("size", size);
        if (keyword != null) {
            meta.put("keyword", keyword);
        }
        auditService.record("READ", "catalog.dataset.access.task", "catalog.dataset.access.task", "pending", "SUCCESS", meta);
        return ApiResponses.ok(paged);
    }

    @GetMapping("/tasks/done")
    public ApiResponse<Map<String, Object>> listDoneTasks(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", required = false, defaultValue = "1") int page,
        @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        String currentLogin = SecurityUtils.getCurrentUserLogin().orElse(null);
        List<CatalogDatasetAccessTask> tasks = approvalService.listDoneTasksForCurrentUser();
        List<Map<String, Object>> views = tasks
            .stream()
            .map(task -> {
                CatalogDatasetAccessRequest req = task.getRequestId() != null ? requestRepository.findById(task.getRequestId()).orElse(null) : null;
                if (req != null && currentLogin != null && currentLogin.equalsIgnoreCase(req.getRequesterUsername())) {
                    return null;
                }
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("task", task);
                dto.put("request", req);
                return dto;
            })
            .filter(item -> item != null)
            .filter(view -> matchTaskView(view, keyword, datasetId, status))
            .toList();
        Map<String, Object> paged = toPage(views, page, size);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", ((List<?>) paged.getOrDefault("content", List.of())).size());
        meta.put("total", paged.getOrDefault("total", 0));
        meta.put("page", page);
        meta.put("size", size);
        if (keyword != null) {
            meta.put("keyword", keyword);
        }
        if (status != null) {
            meta.put("status", status);
        }
        auditService.record("READ", "catalog.dataset.access.task", "catalog.dataset.access.task", "done", "SUCCESS", meta);
        return ApiResponses.ok(paged);
    }

    @PostMapping("/requests/{id}/cancel")
    public ApiResponse<CatalogDatasetAccessRequest> cancelRequest(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionNotes body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            CatalogDatasetAccessRequest request = approvalService.cancelRequest(id, body != null ? body.notes() : null, activeDept);
            auditService.record("UPDATE", "catalog.dataset.access.request.cancel", "catalog.dataset.access.request", id.toString(), "SUCCESS", Map.of());
            return ApiResponses.ok(request);
        } catch (RuntimeException ex) {
            auditService.record(
                "UPDATE",
                "catalog.dataset.access.request.cancel",
                "catalog.dataset.access.request",
                id.toString(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        }
    }

    @GetMapping("/requests/{id}/steps")
    public ApiResponse<List<AccessStepDto>> listRequestSteps(@PathVariable UUID id) {
        CatalogDatasetAccessRequest req = requestRepository.findById(id).orElse(null);
        if (req == null) {
            return ApiResponses.error("申请记录不存在或已被删除");
        }
        if (!canViewRequest(req)) {
            return ApiResponses.error("无权限查看审批详情");
        }
        List<AccessStepDto> steps = approvalService
            .listTasksForRequest(id)
            .stream()
            .map(AccessStepDto::fromTask)
            .toList();
        auditService.record("READ", "catalog.dataset.access.task", "catalog.dataset.access.task", id.toString(), "SUCCESS", Map.of("count", steps.size()));
        return ApiResponses.ok(steps);
    }

    @PostMapping("/tasks/{id}/approve")
    public ApiResponse<CatalogDatasetAccessTask> approveTask(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionNotes body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            CatalogDatasetAccessTask task = approvalService.decideTask(id, true, body != null ? body.notes() : null, activeDept);
            auditService.record("UPDATE", "catalog.dataset.access.task.approve", "catalog.dataset.access.task", id.toString(), "SUCCESS", Map.of());
            return ApiResponses.ok(task);
        } catch (RuntimeException ex) {
            auditService.record("UPDATE", "catalog.dataset.access.task.approve", "catalog.dataset.access.task", id.toString(), "FAILED", Map.of("error", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/reject")
    public ApiResponse<CatalogDatasetAccessTask> rejectTask(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionNotes body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            CatalogDatasetAccessTask task = approvalService.decideTask(id, false, body != null ? body.notes() : null, activeDept);
            auditService.record("UPDATE", "catalog.dataset.access.task.reject", "catalog.dataset.access.task", id.toString(), "SUCCESS", Map.of());
            return ApiResponses.ok(task);
        } catch (RuntimeException ex) {
            auditService.record("UPDATE", "catalog.dataset.access.task.reject", "catalog.dataset.access.task", id.toString(), "FAILED", Map.of("error", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/decide")
    public ApiResponse<CatalogDatasetAccessTask> decideTask(
        @PathVariable UUID id,
        @RequestBody DecisionRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        if (body == null || body.approved() == null) {
            return ApiResponses.error("approved 参数不能为空");
        }
        try {
            CatalogDatasetAccessTask task = approvalService.decideTask(id, body.approved().booleanValue(), body.notes(), activeDept);
            auditService.record(
                "UPDATE",
                "catalog.dataset.access.task.decide",
                "catalog.dataset.access.task",
                id.toString(),
                "SUCCESS",
                Map.of("approved", body.approved())
            );
            return ApiResponses.ok(task);
        } catch (RuntimeException ex) {
            auditService.record(
                "UPDATE",
                "catalog.dataset.access.task.decide",
                "catalog.dataset.access.task",
                id.toString(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        }
    }

    @PostMapping("/tasks/decide/batch")
    public ApiResponse<Map<String, Object>> decideBatch(
        @RequestBody DecisionBatchRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        if (body == null || body.approved() == null || body.taskIds() == null || body.taskIds().isEmpty()) {
            return ApiResponses.error("taskIds 与 approved 不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (UUID taskId : body.taskIds()) {
            if (taskId == null) {
                continue;
            }
            try {
                CatalogDatasetAccessTask task = approvalService.decideTask(taskId, body.approved().booleanValue(), body.notes(), activeDept);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskId", taskId.toString());
                row.put("status", "SUCCESS");
                row.put("taskStatus", task.getStatus());
                results.add(row);
                success++;
            } catch (RuntimeException ex) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskId", taskId.toString());
                row.put("status", "FAILED");
                row.put("error", ex.getMessage());
                results.add(row);
                failed++;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", body.approved());
        payload.put("success", success);
        payload.put("failed", failed);
        payload.put("results", results);
        auditService.record(
            "UPDATE",
            "catalog.dataset.access.task.batch.decide",
            "catalog.dataset.access.task",
            "batch",
            failed == 0 ? "SUCCESS" : "PARTIAL",
            Map.of("approved", body.approved(), "success", success, "failed", failed)
        );
        return ApiResponses.ok(payload);
    }

    public record CreateDatasetAccessRequest(
        UUID datasetId,
        String targetUserId,
        String targetUsername,
        String targetName,
        String targetDept,
        Boolean canQuery,
        Boolean canPreview,
        Instant validFrom,
        Instant validTo,
        String reason
    ) {}

    public record DecisionNotes(String notes) {}

    public record DecisionRequest(Boolean approved, String notes) {}

    public record DecisionBatchRequest(List<UUID> taskIds, Boolean approved, String notes) {}

    public record AccessStepDto(
        Integer stepOrder,
        String approverRole,
        String deptCode,
        String status,
        String decidedBy,
        Instant decidedAt,
        String decisionNotes
    ) {
        static AccessStepDto fromTask(CatalogDatasetAccessTask task) {
            return new AccessStepDto(
                task.getStepOrder(),
                task.getApproverRole(),
                task.getDeptCode(),
                task.getStatus(),
                task.getDecidedBy(),
                task.getDecidedAt(),
                task.getDecisionNotes()
            );
        }
    }

    private boolean canViewRequest(CatalogDatasetAccessRequest req) {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (login != null) {
            if (login.equalsIgnoreCase(req.getRequesterUsername()) || login.equalsIgnoreCase(req.getTargetUsername())) {
                return true;
            }
        }
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
    }

    private Map<String, Object> toPage(List<?> list, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(1, page);
        List<?> source = list == null ? List.of() : list;
        int total = source.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<?> content = from < to ? source.subList(from, to) : Collections.emptyList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private boolean matchTaskView(Map<String, Object> view, String keyword, UUID datasetId, String status) {
        if (view == null) {
            return false;
        }
        CatalogDatasetAccessRequest req = view.get("request") instanceof CatalogDatasetAccessRequest r ? r : null;
        CatalogDatasetAccessTask task = view.get("task") instanceof CatalogDatasetAccessTask t ? t : null;
        if (datasetId != null && (req == null || !datasetId.equals(req.getDatasetId()))) {
            return false;
        }
        if (status != null) {
            String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            String taskStatus = task != null && task.getStatus() != null ? task.getStatus().trim().toUpperCase(Locale.ROOT) : "";
            if (!normalizedStatus.equals(taskStatus)) {
                return false;
            }
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        String text =
            (
                String.valueOf(req != null ? req.getDatasetName() : "") +
                " " +
                String.valueOf(req != null ? req.getRequesterName() : "") +
                " " +
                String.valueOf(req != null ? req.getRequesterUsername() : "") +
                " " +
                String.valueOf(req != null ? req.getTargetName() : "") +
                " " +
                String.valueOf(req != null ? req.getTargetUsername() : "") +
                " " +
                String.valueOf(task != null ? task.getApproverRole() : "")
            )
                .toLowerCase(Locale.ROOT);
        return text.contains(kw);
    }
}
