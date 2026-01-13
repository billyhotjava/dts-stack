package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetAccessRequestRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.DatasetDataAccessApprovalService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ApiResponse<List<CatalogDatasetAccessRequest>> listMyRequests() {
        return ApiResponses.ok(approvalService.listMyRequests());
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
                activeDept
            );
            auditService.record(
                "CREATE",
                "catalog.dataset.access.request",
                "catalog.dataset.access.request",
                saved.getId().toString(),
                "SUCCESS",
                Map.of("datasetId", body.datasetId().toString())
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            auditService.record(
                "CREATE",
                "catalog.dataset.access.request",
                "catalog.dataset.access.request",
                body.datasetId().toString(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        }
    }

    @GetMapping("/tasks/pending")
    public ApiResponse<List<Map<String, Object>>> listPendingTasks(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
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
            .toList();
        return ApiResponses.ok(views);
    }

    @PostMapping("/tasks/{id}/approve")
    public ApiResponse<CatalogDatasetAccessTask> approveTask(
        @PathVariable UUID id,
        @RequestBody(required = false) DecisionNotes body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            CatalogDatasetAccessTask task = approvalService.approveTask(id, body != null ? body.notes() : null, activeDept);
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
            CatalogDatasetAccessTask task = approvalService.rejectTask(id, body != null ? body.notes() : null, activeDept);
            auditService.record("UPDATE", "catalog.dataset.access.task.reject", "catalog.dataset.access.task", id.toString(), "SUCCESS", Map.of());
            return ApiResponses.ok(task);
        } catch (RuntimeException ex) {
            auditService.record("UPDATE", "catalog.dataset.access.task.reject", "catalog.dataset.access.task", id.toString(), "FAILED", Map.of("error", ex.getMessage()));
            return ApiResponses.error(ex.getMessage());
        }
    }

    public record CreateDatasetAccessRequest(
        UUID datasetId,
        Boolean canQuery,
        Boolean canPreview,
        Instant validFrom,
        Instant validTo,
        String reason
    ) {}

    public record DecisionNotes(String notes) {}
}
