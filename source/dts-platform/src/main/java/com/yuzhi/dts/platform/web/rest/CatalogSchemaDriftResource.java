package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSchemaDriftEventRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
@Transactional(readOnly = true)
public class CatalogSchemaDriftResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogSchemaDriftEventRepository driftRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public record SchemaDriftPolicyRequest(String policyMode, String note) {}

    public record SchemaDriftTicketRequest(String ticketStatus, String assignee, String note) {}

    public CatalogSchemaDriftResource(
        CatalogSchemaDriftEventRepository driftRepo,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.driftRepo = driftRepo;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    @GetMapping("/datasets/{datasetId}/schema-drift")
    public ApiResponse<List<Map<String, Object>>> listForDataset(
        @PathVariable UUID datasetId,
        @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        if (!accessChecker.canRead(dataset)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该数据集");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> payload = driftRepo
            .findTop200ByDatasetIdOrderByCreatedDateDesc(datasetId)
            .stream()
            .limit(safeLimit)
            .map(event -> toDto(event, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集 Schema 漂移记录");
        auditPayload.put("datasetId", datasetId.toString());
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_LIST", AuditStage.SUCCESS, datasetId.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/schema-drift/by-run")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> listForRun(
        @RequestParam(name = "runId") UUID runId,
        @RequestParam(name = "limit", required = false, defaultValue = "200") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> payload = driftRepo
            .findTop200ByRunIdOrderByCreatedDateDesc(runId)
            .stream()
            .limit(safeLimit)
            .map(event -> toDto(event, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看采集批次 Schema 漂移记录");
        auditPayload.put("runId", runId.toString());
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_LIST", AuditStage.SUCCESS, runId.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/schema-drift")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> listAll(
        @RequestParam(name = "policyMode", required = false) String policyMode,
        @RequestParam(name = "ticketStatus", required = false) String ticketStatus,
        @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String normalizedPolicy = normalize(policyMode);
        String normalizedTicket = normalize(ticketStatus);
        List<Map<String, Object>> payload = driftRepo
            .findTop500ByOrderByCreatedDateDesc()
            .stream()
            .filter(event -> match(normalizedPolicy, normalize(event.getPolicyMode())))
            .filter(event -> match(normalizedTicket, normalize(event.getTicketStatus())))
            .limit(safeLimit)
            .map(event -> toDto(event, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看全量 Schema 漂移工单");
        auditPayload.put("policyMode", normalizedPolicy == null ? "all" : normalizedPolicy);
        auditPayload.put("ticketStatus", normalizedTicket == null ? "all" : normalizedTicket);
        auditPayload.put("limit", safeLimit);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_LIST", AuditStage.SUCCESS, "all", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/schema-drift/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable UUID id, @RequestParam(name = "includeDetails", required = false, defaultValue = "true") boolean includeDetails) {
        CatalogSchemaDriftEvent event = driftRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        CatalogDataset dataset = datasetRepo.findById(event.getDatasetId()).orElse(null);
        if (dataset != null && !accessChecker.canRead(dataset)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该数据集");
        }
        Map<String, Object> payload = toDto(event, includeDetails);
        audit.auditAction("CATALOG_SCHEMA_DRIFT_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看 Schema 漂移详情"));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/schema-drift/{id}/policy")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    @Transactional
    public ApiResponse<Map<String, Object>> updatePolicy(
        @PathVariable UUID id,
        @RequestBody(required = false) SchemaDriftPolicyRequest request
    ) {
        CatalogSchemaDriftEvent event = driftRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        String policy = normalize(request == null ? null : request.policyMode());
        if (!isAllowedPolicy(policy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法策略，支持：REVIEW/AUTO_APPLY/BLOCK");
        }
        event.setPolicyMode(policy);
        if (request != null && request.note() != null) {
            event.setWorkflowNote(request.note().trim());
        }
        driftRepo.save(event);
        audit.auditAction(
            "CATALOG_SCHEMA_DRIFT_POLICY_UPDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新 Schema 漂移策略", "policyMode", policy)
        );
        return ApiResponses.ok(toDto(event, true));
    }

    @PostMapping("/schema-drift/{id}/ticket")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    @Transactional
    public ApiResponse<Map<String, Object>> updateTicket(
        @PathVariable UUID id,
        @RequestBody(required = false) SchemaDriftTicketRequest request
    ) {
        CatalogSchemaDriftEvent event = driftRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        String ticketStatus = normalize(request == null ? null : request.ticketStatus());
        if (!isAllowedTicketStatus(ticketStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法工单状态，支持：OPEN/IN_REVIEW/RESOLVED/IGNORED/REJECTED");
        }
        event.setTicketStatus(ticketStatus);
        if (request != null && request.assignee() != null) {
            event.setTicketAssignee(request.assignee().trim());
        }
        if (request != null && request.note() != null) {
            event.setWorkflowNote(request.note().trim());
        }
        if (
            CatalogSchemaDriftEvent.TICKET_RESOLVED.equals(ticketStatus) ||
            CatalogSchemaDriftEvent.TICKET_IGNORED.equals(ticketStatus) ||
            CatalogSchemaDriftEvent.TICKET_REJECTED.equals(ticketStatus)
        ) {
            event.setHandledAt(Instant.now());
            event.setHandledBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        }
        driftRepo.save(event);
        audit.auditAction(
            "CATALOG_SCHEMA_DRIFT_TICKET_UPDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "更新 Schema 漂移工单状态", "ticketStatus", ticketStatus)
        );
        return ApiResponses.ok(toDto(event, true));
    }

    private Map<String, Object> toDto(CatalogSchemaDriftEvent event, boolean includeDetails) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (event == null) {
            return dto;
        }
        if (event.getId() != null) dto.put("id", event.getId().toString());
        if (event.getRunId() != null) dto.put("runId", event.getRunId().toString());
        dto.put("integration", event.getIntegration());
        dto.put("datasetId", event.getDatasetId() != null ? event.getDatasetId().toString() : null);
        dto.put("datasetName", resolveDatasetName(event.getDatasetId()));
        dto.put("hiveDatabase", event.getHiveDatabase());
        dto.put("hiveTable", event.getHiveTable());
        dto.put("addedCount", event.getAddedCount());
        dto.put("removedCount", event.getRemovedCount());
        dto.put("changedCount", event.getChangedCount());
        dto.put("policyMode", normalize(event.getPolicyMode()) == null ? CatalogSchemaDriftEvent.POLICY_REVIEW : normalize(event.getPolicyMode()));
        dto.put("ticketStatus", normalize(event.getTicketStatus()) == null ? CatalogSchemaDriftEvent.TICKET_OPEN : normalize(event.getTicketStatus()));
        dto.put("ticketAssignee", event.getTicketAssignee());
        dto.put("handledBy", event.getHandledBy());
        dto.put("handledAt", event.getHandledAt());
        dto.put("workflowNote", event.getWorkflowNote());
        dto.put("createdDate", event.getCreatedDate());
        if (includeDetails) {
            dto.put("detailsJson", event.getDetailsJson());
        }
        return dto;
    }

    private boolean isAllowedPolicy(String policyMode) {
        return CatalogSchemaDriftEvent.POLICY_REVIEW.equals(policyMode) ||
        CatalogSchemaDriftEvent.POLICY_AUTO_APPLY.equals(policyMode) ||
        CatalogSchemaDriftEvent.POLICY_BLOCK.equals(policyMode);
    }

    private boolean isAllowedTicketStatus(String ticketStatus) {
        return CatalogSchemaDriftEvent.TICKET_OPEN.equals(ticketStatus) ||
        CatalogSchemaDriftEvent.TICKET_IN_REVIEW.equals(ticketStatus) ||
        CatalogSchemaDriftEvent.TICKET_RESOLVED.equals(ticketStatus) ||
        CatalogSchemaDriftEvent.TICKET_IGNORED.equals(ticketStatus) ||
        CatalogSchemaDriftEvent.TICKET_REJECTED.equals(ticketStatus);
    }

    private boolean match(String expected, String actual) {
        if (expected == null) {
            return true;
        }
        return expected.equals(actual);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String resolveDatasetName(UUID datasetId) {
        if (datasetId == null) {
            return null;
        }
        return datasetRepo.findById(datasetId).map(CatalogDataset::getName).orElse(null);
    }
}
