package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.ComplianceService;
import com.yuzhi.dts.platform.service.governance.IssueTicketService;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchDto;
import com.yuzhi.dts.platform.service.governance.dto.ComplianceBatchItemDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueActionDto;
import com.yuzhi.dts.platform.service.governance.dto.IssueTicketDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.ComplianceBatchRequest;
import com.yuzhi.dts.platform.service.governance.request.ComplianceItemUpdateRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueActionRequest;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.security.SecurityUtils;
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
    private final AuditService auditService;

    public GovernanceResource(
        QualityRuleService qualityRuleService,
        QualityRunService qualityRunService,
        ComplianceService complianceService,
        IssueTicketService issueTicketService,
        AuditService auditService
    ) {
        this.qualityRuleService = qualityRuleService;
        this.qualityRunService = qualityRunService;
        this.complianceService = complianceService;
        this.issueTicketService = issueTicketService;
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
        if (!rules.isEmpty()) {
            payload.put(
                "ruleNames",
                rules.stream().map(QualityRuleDto::getName).filter(StringUtils::hasText).limit(20).toList()
            );
        }
        auditService.recordAs(
            currentUser(),
            "READ",
            "governance.rule",
            "governance.rule",
            "LIST",
            "SUCCESS",
            payload,
            null
        );
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
        auditService.recordAs(
            currentUser(),
            "READ",
            "governance.rule",
            "governance.rule",
            id.toString(),
            "SUCCESS",
            detail,
            null
        );
        return ApiResponses.ok(dto);
    }

    @PostMapping("/quality/runs")
    public ApiResponse<List<QualityRunDto>> triggerQualityRun(@RequestBody QualityRunTriggerRequest request) {
        return ApiResponses.ok(qualityRunService.trigger(request, currentUser()));
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
        auditService.recordAs(
            currentUser(),
            "READ",
            "governance.compliance.qualityRun",
            "governance.compliance.qualityRun",
            id.toString(),
            "SUCCESS",
            detail,
            null
        );
        return ApiResponses.ok(dto);
    }

    @GetMapping("/quality/runs")
    public ApiResponse<List<QualityRunDto>> listQualityRuns(
        @RequestParam(value = "ruleId", required = false) UUID ruleId,
        @RequestParam(value = "datasetId", required = false) UUID datasetId,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        if (ruleId != null) {
            List<QualityRunDto> runs = qualityRunService.recentByRule(ruleId, limit);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("targetId", ruleId.toString());
            try {
                QualityRuleDto rule = qualityRuleService.getRule(ruleId);
                if (rule != null && StringUtils.hasText(rule.getName())) {
                    detail.put("targetName", rule.getName());
                    detail.put("summary", "查看质量规则：" + rule.getName());
                } else {
                    detail.put("summary", "查看质量规则详情");
                }
            } catch (Exception ex) {
                detail.put("summary", "查看质量规则详情");
            }
            auditService.recordAs(
                currentUser(),
                "READ",
                "governance.rule",
                "governance.rule",
                ruleId.toString(),
                "SUCCESS",
                detail,
                null
            );
            return ApiResponses.ok(runs);
        }
        if (datasetId != null) {
            return ApiResponses.ok(qualityRunService.recentByDataset(datasetId, limit));
        }
        return ApiResponses.ok(qualityRunService.recent(limit));
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
    public ApiResponse<List<IssueTicketDto>> listIssues() {
        return ApiResponses.ok(issueTicketService.listActiveTickets());
    }

    @PostMapping("/issues")
    public ApiResponse<IssueTicketDto> createIssue(@RequestBody IssueTicketUpsertRequest request) {
        return ApiResponses.ok(issueTicketService.create(request, currentUser()));
    }

    @PutMapping("/issues/{id}")
    public ApiResponse<IssueTicketDto> updateIssue(@PathVariable UUID id, @RequestBody IssueTicketUpsertRequest request) {
        return ApiResponses.ok(issueTicketService.update(id, request));
    }

    @PostMapping("/issues/{id}/close")
    public ApiResponse<IssueTicketDto> closeIssue(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String resolution = body != null ? body.get("resolution") : null;
        return ApiResponses.ok(issueTicketService.close(id, resolution, currentUser()));
    }

    @PostMapping("/issues/{id}/actions")
    public ApiResponse<IssueActionDto> appendIssueAction(@PathVariable UUID id, @RequestBody IssueActionRequest request) {
        return ApiResponses.ok(issueTicketService.appendAction(id, request, currentUser()));
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
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
