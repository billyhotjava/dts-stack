package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.ComplianceService;
import com.yuzhi.dts.platform.service.governance.GovernanceOpsMetricsService;
import com.yuzhi.dts.platform.service.governance.IssueTicketService;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchDto;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchItemDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleVersionDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.ComplianceBatchRequest;
import com.yuzhi.dts.platform.service.governance.request.ComplianceItemUpdateRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueActionRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleVersionStatusRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance")
@Transactional
public class GovernanceResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final QualityRuleService qualityRuleService;
    private final QualityRunService qualityRunService;
    private final ComplianceService complianceService;
    private final IssueTicketService issueTicketService;
    private final GovernanceOpsMetricsService governanceOpsMetricsService;
    private final ReferenceCodeService referenceCodeService;
    private final AuditService auditService;

    public GovernanceResource(
        QualityRuleService qualityRuleService,
        QualityRunService qualityRunService,
        ComplianceService complianceService,
        IssueTicketService issueTicketService,
        GovernanceOpsMetricsService governanceOpsMetricsService,
        ReferenceCodeService referenceCodeService,
        AuditService auditService
    ) {
        this.qualityRuleService = qualityRuleService;
        this.qualityRunService = qualityRunService;
        this.complianceService = complianceService;
        this.issueTicketService = issueTicketService;
        this.governanceOpsMetricsService = governanceOpsMetricsService;
        this.referenceCodeService = referenceCodeService;
        this.auditService = auditService;
    }

    // Quality rule APIs ------------------------------------------------------

    @GetMapping("/quality/rules")
    public ApiResponse<List<QualityRuleDto>> listQualityRules(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<QualityRuleDto> rules = qualityRuleService.listAll(activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看质量规则列表");
        payload.put("count", rules.size());
        if (StringUtils.hasText(activeDept)) {
            payload.put("activeDept", activeDept.trim());
        }
        if (!rules.isEmpty()) {
            payload.put(
                "ruleNames",
                rules.stream().map(QualityRuleDto::getName).filter(StringUtils::hasText).limit(20).toList()
            );
        }
        auditService.auditAction("GOV_RULE_LIST", AuditStage.SUCCESS, "LIST", payload);
        return ApiResponses.ok(rules);
    }

    @GetMapping("/rules")
    public ApiResponse<List<QualityRuleDto>> legacyListRules(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return listQualityRules(activeDept);
    }

    @PostMapping("/quality/rules")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRuleDto> createRule(
        @RequestBody QualityRuleUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(qualityRuleService.createRule(request, currentUser(), activeDept));
    }

    @PostMapping("/rules")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRuleDto> legacyCreateRule(
        @RequestBody QualityRuleUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return createRule(request, activeDept);
    }

    @PutMapping("/quality/rules/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRuleDto> updateRule(
        @PathVariable UUID id,
        @RequestBody QualityRuleUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(qualityRuleService.updateRule(id, request, currentUser(), activeDept));
    }

    @DeleteMapping("/quality/rules/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteRule(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        qualityRuleService.deleteRule(id, activeDept);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/quality/rules/{id}/toggle")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRuleDto> toggleRule(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        boolean enabled = Boolean.TRUE.equals(body.getOrDefault("enabled", Boolean.TRUE));
        return ApiResponses.ok(qualityRuleService.toggleRule(id, enabled, activeDept));
    }

    @GetMapping("/quality/rules/{id}")
    public ApiResponse<QualityRuleDto> getRule(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QualityRuleDto dto = qualityRuleService.getRule(id, activeDept);
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("targetId", id.toString());
        if (dto != null && StringUtils.hasText(dto.getName())) {
            detail.put("targetName", dto.getName());
            detail.put("summary", "查看质量规则：" + dto.getName());
        } else {
            detail.put("summary", "查看质量规则详情");
        }
        if (StringUtils.hasText(activeDept)) {
            detail.put("activeDept", activeDept.trim());
        }
        auditService.auditAction("GOV_RULE_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/quality/rules/{id}/versions")
    public ApiResponse<List<QualityRuleVersionDto>> listRuleVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<QualityRuleVersionDto> versions = qualityRuleService.listRuleVersions(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看质量规则版本列表");
        detail.put("targetId", id.toString());
        detail.put("count", versions.size());
        auditService.auditAction("GOV_RULE_VERSION_LIST", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(versions);
    }

    @GetMapping("/quality/rules/{id}/versions/{version}")
    public ApiResponse<QualityRuleVersionDto> getRuleVersion(
        @PathVariable UUID id,
        @PathVariable Integer version,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QualityRuleVersionDto dto = qualityRuleService.getRuleVersion(id, version, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看质量规则版本详情");
        detail.put("targetId", id.toString());
        detail.put("version", version);
        auditService.auditAction("GOV_RULE_VERSION_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @PostMapping("/quality/rules/{id}/versions/{version}/status")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRuleVersionDto> changeRuleVersionStatus(
        @PathVariable UUID id,
        @PathVariable Integer version,
        @RequestBody QualityRuleVersionStatusRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(
            qualityRuleService.changeRuleVersionStatus(
                id,
                version,
                request != null ? request.getStatus() : null,
                request != null ? request.getNotes() : null,
                currentUser(),
                activeDept
            )
        );
    }

    @PostMapping("/quality/runs")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<QualityRunDto>> triggerQualityRun(@RequestBody QualityRunTriggerRequest request) {
        List<QualityRunDto> runs = qualityRunService.trigger(request, currentUser());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "执行质量检测");
        payload.put("runCount", runs.size());
        if (request != null) {
            if (request.getRuleId() != null) {
                payload.put("ruleId", request.getRuleId().toString());
            }
            if (request.getDatasetId() != null) {
                payload.put("datasetId", request.getDatasetId().toString());
            }
            if (StringUtils.hasText(request.getTriggerType())) {
                payload.put("triggerType", request.getTriggerType());
            }
        }
        String resourceId = request != null && request.getRuleId() != null ? request.getRuleId().toString() : "trigger";
        auditService.auditAction("GOV_RULE_EXECUTE", AuditStage.SUCCESS, resourceId, payload);
        return ApiResponses.ok(runs);
    }

    @PostMapping("/quality/runs/dry-run")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<QualityRunDto>> dryRunQualityRule(@RequestBody QualityRunTriggerRequest request) {
        QualityRunTriggerRequest effectiveRequest = request != null ? request : new QualityRunTriggerRequest();
        effectiveRequest.setDryRun(Boolean.TRUE);
        if (!StringUtils.hasText(effectiveRequest.getTriggerType())) {
            effectiveRequest.setTriggerType("DRY_RUN");
        }
        List<QualityRunDto> runs = qualityRunService.trigger(effectiveRequest, currentUser());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "试跑质量规则");
        payload.put("runCount", runs.size());
        if (effectiveRequest.getRuleId() != null) {
            payload.put("ruleId", effectiveRequest.getRuleId().toString());
        }
        if (effectiveRequest.getDatasetId() != null) {
            payload.put("datasetId", effectiveRequest.getDatasetId().toString());
        }
        auditService.auditAction(
            "GOV_RULE_DRY_RUN",
            AuditStage.SUCCESS,
            effectiveRequest.getRuleId() != null ? effectiveRequest.getRuleId().toString() : "dry-run",
            payload
        );
        return ApiResponses.ok(runs);
    }

    @GetMapping("/quality/runs/{id}")
    public ApiResponse<QualityRunDto> getQualityRun(@PathVariable UUID id) {
        QualityRunDto dto = qualityRunService.getRun(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "查看质量运行详情");
        if (dto != null) {
            if (dto.getStatus() != null) {
                detail.put("status", dto.getStatus());
            }
            if (dto.getRuleId() != null) {
                detail.put("ruleId", dto.getRuleId().toString());
            }
            if (dto.getDatasetId() != null) {
                detail.put("datasetId", dto.getDatasetId().toString());
            }
            if (dto.getTriggerType() != null) {
                detail.put("triggerType", dto.getTriggerType());
            }
        }
        auditService.auditAction("GOV_QUALITY_RUN_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/quality/runs")
    public ApiResponse<List<QualityRunDto>> listQualityRuns(
        @RequestParam(value = "ruleId", required = false) UUID ruleId,
        @RequestParam(value = "datasetId", required = false) UUID datasetId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "startedFrom", required = false) Instant startedFrom,
        @RequestParam(value = "startedTo", required = false) Instant startedTo,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        List<QualityRunDto> runs = qualityRunService.listRuns(ruleId, datasetId, status, startedFrom, startedTo, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看质量运行记录");
        payload.put("limit", limit);
        payload.put("count", runs.size());
        if (ruleId != null) {
            payload.put("ruleId", ruleId.toString());
        }
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        if (StringUtils.hasText(status)) {
            payload.put("status", status.trim().toUpperCase(Locale.ROOT));
        }
        if (startedFrom != null) {
            payload.put("startedFrom", startedFrom.toString());
        }
        if (startedTo != null) {
            payload.put("startedTo", startedTo.toString());
        }
        String resourceId = ruleId != null
            ? ruleId.toString()
            : datasetId != null
                ? datasetId.toString()
                : "recent";
        auditService.auditAction("GOV_QUALITY_RUN_LIST", AuditStage.SUCCESS, resourceId, payload);
        return ApiResponses.ok(runs);
    }

    // Compliance APIs --------------------------------------------------------

    @PostMapping("/compliance/batches")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ComplianceBatchDto> createComplianceBatch(
        @RequestBody ComplianceBatchRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(complianceService.createBatch(request, currentUser(), activeDept));
    }

    @GetMapping("/compliance/batches")
    public ApiResponse<List<ComplianceBatchDto>> listComplianceBatches(
        @RequestParam(value = "limit", defaultValue = "10") int limit,
        @RequestParam(value = "status", required = false) String status,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<String> statuses = parseStatuses(status);
        List<ComplianceBatchDto> data = complianceService.recentBatches(limit, statuses, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "刷新合规检查列表");
        detail.put("limit", limit);
        detail.put("count", data.size());
        if (!statuses.isEmpty()) {
            detail.put("statusFilter", statuses);
        }
        if (StringUtils.hasText(activeDept)) {
            detail.put("activeDept", activeDept.trim());
        }
        auditService.record("LIST", "governance.compliance", "governance.compliance.batch", null, "SUCCESS", detail);
        return ApiResponses.ok(data);
    }

    @GetMapping("/compliance/batches/{id}")
    public ApiResponse<ComplianceBatchDto> getComplianceBatch(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ComplianceBatchDto dto = complianceService.getBatch(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        if (dto != null) {
            if (StringUtils.hasText(dto.getName())) {
                detail.put("targetName", dto.getName());
                detail.put("summary", "查看合规批次：" + dto.getName());
            } else {
                detail.put("summary", "查看合规批次详情");
            }
            if (StringUtils.hasText(dto.getStatus())) {
                detail.put("status", dto.getStatus());
            }
            if (dto.getTotalItems() != null) {
                detail.put("itemCount", dto.getTotalItems());
            }
        } else {
            detail.put("summary", "查看合规批次详情");
        }
        if (StringUtils.hasText(activeDept)) {
            detail.put("activeDept", activeDept.trim());
        }
        auditService.record(
            "READ",
            "governance.compliance.batch",
            "governance.compliance.batch",
            id.toString(),
            "SUCCESS",
            detail
        );
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/compliance/batches/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteComplianceBatch(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        complianceService.deleteBatch(id, currentUser(), activeDept);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PutMapping("/compliance/items/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ComplianceBatchItemDto> updateComplianceItem(
        @PathVariable UUID id,
        @RequestBody ComplianceItemUpdateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(complianceService.updateItem(id, request, currentUser(), activeDept));
    }

    // Issue & remediation APIs ----------------------------------------------

    @GetMapping("/issues")
    public ApiResponse<List<IssueTicketDto>> listIssues(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "datasetId", required = false) UUID datasetId,
        @RequestParam(value = "assignedTo", required = false) String assignedTo,
        @RequestParam(value = "owner", required = false) String owner,
        @RequestParam(value = "priority", required = false) String priority,
        @RequestParam(value = "overdue", required = false) Boolean overdue,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<IssueTicketDto> tickets = issueTicketService.listTickets(
            status,
            sourceType,
            datasetId,
            assignedTo,
            owner,
            priority,
            overdue,
            keyword,
            limit,
            currentUser(),
            activeDept
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看问题单列表");
        payload.put("count", tickets.size());
        payload.put("limit", limit);
        if (StringUtils.hasText(status)) {
            payload.put("statusFilter", status.trim());
        }
        if (StringUtils.hasText(sourceType)) {
            payload.put("sourceType", sourceType.trim());
        }
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        if (StringUtils.hasText(assignedTo)) {
            payload.put("assignedTo", assignedTo.trim());
        }
        if (StringUtils.hasText(owner)) {
            payload.put("owner", owner.trim());
        }
        if (StringUtils.hasText(priority)) {
            payload.put("priority", priority.trim().toUpperCase(Locale.ROOT));
        }
        if (overdue != null) {
            payload.put("overdue", overdue);
        }
        if (StringUtils.hasText(keyword)) {
            payload.put("keyword", keyword.trim());
        }
        if (StringUtils.hasText(activeDept)) {
            payload.put("activeDept", activeDept.trim());
        }
        auditService.auditAction("GOV_ISSUE_LIST", AuditStage.SUCCESS, "LIST", payload);

        return ApiResponses.ok(tickets);
    }

    @GetMapping("/issues/{id}")
    public ApiResponse<IssueTicketDto> getIssue(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IssueTicketDto ticket = issueTicketService.get(id, currentUser(), activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看问题单详情");
        payload.put("targetId", id.toString());
        if (ticket != null && StringUtils.hasText(ticket.getTitle())) {
            payload.put("title", ticket.getTitle());
        }
        auditService.auditAction("GOV_ISSUE_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(ticket);
    }

    @PostMapping("/issues")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IssueTicketDto> createIssue(
        @RequestBody IssueTicketUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(issueTicketService.create(request, currentUser(), activeDept));
    }

    @PutMapping("/issues/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IssueTicketDto> updateIssue(
        @PathVariable UUID id,
        @RequestBody IssueTicketUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(issueTicketService.update(id, request, currentUser(), activeDept));
    }

    @PostMapping("/issues/{id}/close")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IssueTicketDto> closeIssue(
        @PathVariable UUID id,
        @RequestBody Map<String, String> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String resolution = body != null ? body.get("resolution") : null;
        return ApiResponses.ok(issueTicketService.close(id, resolution, currentUser(), activeDept));
    }

    @PostMapping("/issues/{id}/actions")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IssueActionDto> appendIssueAction(
        @PathVariable UUID id,
        @RequestBody IssueActionRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(issueTicketService.appendAction(id, request, currentUser(), activeDept));
    }

    @GetMapping("/issues/metrics")
    public ApiResponse<Map<String, Object>> issueSlaMetrics(
        @RequestParam(value = "days", defaultValue = "30") int days,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> metrics = issueTicketService.issueSlaMetrics(days, currentUser(), activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看问题单 SLA 指标");
        payload.put("days", days);
        payload.put("open", metrics.get("open"));
        payload.put("overdue", metrics.get("overdue"));
        payload.put("overdueRate", metrics.get("overdueRate"));
        payload.put("avgHandlingHours", metrics.get("avgHandlingHours"));
        auditService.auditAction("GOV_ISSUE_METRICS", AuditStage.SUCCESS, "metrics", payload);
        return ApiResponses.ok(metrics);
    }

    // Governance ops metrics ------------------------------------------------

    @GetMapping("/ops/overview")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> governanceOpsOverview(@RequestParam(value = "days", defaultValue = "7") int days) {
        Map<String, Object> metrics = governanceOpsMetricsService.overview(days);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看治理运营概览");
        detail.put("days", days);
        detail.put("qualitySuccessRate", ((Map<?, ?>) metrics.getOrDefault("kpi", Map.of())).get("qualitySuccessRate"));
        detail.put("issueOverdueRate", ((Map<?, ?>) metrics.getOrDefault("kpi", Map.of())).get("issueOverdueRate"));
        auditService.auditAction("GOV_OPS_OVERVIEW_VIEW", AuditStage.SUCCESS, "overview", detail);
        return ApiResponses.ok(metrics);
    }

    @GetMapping("/ops/trend")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> governanceOpsTrend(@RequestParam(value = "days", defaultValue = "14") int days) {
        List<Map<String, Object>> trend = governanceOpsMetricsService.trend(days);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看治理运营趋势");
        detail.put("days", days);
        detail.put("points", trend.size());
        auditService.auditAction("GOV_OPS_TREND_VIEW", AuditStage.SUCCESS, "trend", detail);
        return ApiResponses.ok(trend);
    }

    @GetMapping("/ops/release-gate")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> governanceReleaseGate(
        @RequestParam(value = "days", defaultValue = "7") int days,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> governance = governanceOpsMetricsService.overview(days);
        Map<String, Object> importOps = referenceCodeService.importOpsOverview(Math.max(24, days * 24), activeDept);
        BigDecimal qualitySuccessRate = decimal(readNested(governance, "kpi", "qualitySuccessRate"));
        BigDecimal issueOverdueRate = decimal(readNested(governance, "kpi", "issueOverdueRate"));
        long importErrorTotal = longValue(importOps.get("errorTotal"));

        List<Map<String, Object>> checks = List.of(
            gateCheck(
                "QUALITY_SUCCESS_RATE",
                "质量成功率",
                qualitySuccessRate.compareTo(BigDecimal.valueOf(95)) >= 0,
                qualitySuccessRate,
                ">=95%",
                "BLOCKER"
            ),
            gateCheck(
                "ISSUE_OVERDUE_RATE",
                "问题单逾期率",
                issueOverdueRate.compareTo(BigDecimal.valueOf(10)) <= 0,
                issueOverdueRate,
                "<=10%",
                "BLOCKER"
            ),
            gateCheck(
                "REFERENCE_IMPORT_ERROR",
                "码表导入错误数",
                importErrorTotal <= 0,
                importErrorTotal,
                "=0",
                "BLOCKER"
            )
        );
        long blockerFailed = checks
            .stream()
            .filter(row -> "BLOCKER".equals(String.valueOf(row.get("severity"))))
            .filter(row -> !Boolean.TRUE.equals(row.get("passed")))
            .count();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowDays", days);
        payload.put("readyForRelease", blockerFailed == 0);
        payload.put("blockerFailed", blockerFailed);
        payload.put("checks", checks);
        payload.put("governanceOverview", governance);
        payload.put("referenceImportOverview", importOps);
        payload.put("checkedAt", Instant.now());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看治理发布门禁");
        detail.put("days", days);
        detail.put("readyForRelease", payload.get("readyForRelease"));
        detail.put("blockerFailed", blockerFailed);
        auditService.auditAction("GOV_OPS_RELEASE_GATE_VIEW", AuditStage.SUCCESS, "release-gate", detail);
        return ApiResponses.ok(payload);
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    private Object readNested(Map<String, Object> source, String parent, String key) {
        Object nested = source.get(parent);
        if (nested instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Map<String, Object> gateCheck(
        String code,
        String name,
        boolean passed,
        Object actual,
        String threshold,
        String severity
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("passed", passed);
        row.put("actual", actual);
        row.put("threshold", threshold);
        row.put("severity", severity);
        return row;
    }

    private List<String> parseStatuses(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays
            .stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toList());
    }
}
