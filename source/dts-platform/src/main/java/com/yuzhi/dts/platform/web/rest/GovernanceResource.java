package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovCleansingFunction;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow;
import com.yuzhi.dts.platform.domain.governance.GovQualityTemplate;
import com.yuzhi.dts.platform.repository.governance.GovCleansingFunctionRepository;
import com.yuzhi.dts.platform.domain.governance.GovDataEditLog;
import com.yuzhi.dts.platform.repository.governance.GovDataEditLogRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityTemplateRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.service.governance.ComplianceService;
import com.yuzhi.dts.platform.service.governance.DataCleansingService;
import com.yuzhi.dts.platform.service.governance.GovernanceOpsMetricsService;
import com.yuzhi.dts.platform.service.governance.IssueTicketService;
import com.yuzhi.dts.platform.service.governance.OdsDataEditorService;
import com.yuzhi.dts.platform.service.governance.IngestionQualityBridge;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.QualityReportExportService;
import com.yuzhi.dts.platform.service.governance.QualityAuditRecorder;
import com.yuzhi.dts.platform.service.governance.QualityDatasetReadGuard;
import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.SqlRepairService;
import com.yuzhi.dts.platform.service.governance.QualityDashboardService;
import com.yuzhi.dts.platform.service.governance.QualityScoreService;
import com.yuzhi.dts.platform.service.governance.dto.PreCheckRequest;
import com.yuzhi.dts.platform.service.governance.dto.PreCheckResult;
import com.yuzhi.dts.platform.service.governance.dto.QualityDashboardDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService;
import com.yuzhi.dts.platform.service.governance.SqlTemplateRenderer;
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
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
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

    private static final Logger log = LoggerFactory.getLogger(GovernanceResource.class);

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";
    private static final String INGESTION_SERVICE_EXPRESSION =
        "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-ingestion'";

    private final QualityRuleService qualityRuleService;
    private final QualityRunService qualityRunService;
    private final ComplianceService complianceService;
    private final IssueTicketService issueTicketService;
    private final GovernanceOpsMetricsService governanceOpsMetricsService;
    private final ReferenceCodeService referenceCodeService;
    private final AuditService auditService;
    private final QualityAuditRecorder qualityAuditRecorder;
    private final QualityDatasetReadGuard qualityDatasetReadGuard;
    private final GovQualityTemplateRepository templateRepository;
    private final SqlTemplateRenderer sqlTemplateRenderer;
    private final GovQualityFailingRowRepository failingRowRepository;
    private final DataSource dataSource;
    private final DataCleansingService dataCleansingService;
    private final GovCleansingFunctionRepository cleansingFunctionRepository;
    private final GovRuleRepository ruleRepository;
    private final GovRuleBindingRepository ruleBindingRepository;
    private final GovernanceProperties governanceProperties;
    private final OdsDataEditorService odsDataEditorService;
    private final GovDataEditLogRepository editLogRepository;
    private final QualityScoreService qualityScoreService;
    private final QualityDashboardService qualityDashboardService;
    private final IngestionQualityBridge ingestionQualityBridge;
    private final QualityReportExportService qualityReportExportService;
    private final SqlRepairService sqlRepairService;

    public GovernanceResource(
        QualityRuleService qualityRuleService,
        QualityRunService qualityRunService,
        ComplianceService complianceService,
        IssueTicketService issueTicketService,
        GovernanceOpsMetricsService governanceOpsMetricsService,
        ReferenceCodeService referenceCodeService,
        AuditService auditService,
        QualityAuditRecorder qualityAuditRecorder,
        QualityDatasetReadGuard qualityDatasetReadGuard,
        GovQualityTemplateRepository templateRepository,
        SqlTemplateRenderer sqlTemplateRenderer,
        GovQualityFailingRowRepository failingRowRepository,
        DataSource dataSource,
        DataCleansingService dataCleansingService,
        GovCleansingFunctionRepository cleansingFunctionRepository,
        GovRuleRepository ruleRepository,
        GovRuleBindingRepository ruleBindingRepository,
        GovernanceProperties governanceProperties,
        OdsDataEditorService odsDataEditorService,
        GovDataEditLogRepository editLogRepository,
        QualityScoreService qualityScoreService,
        QualityDashboardService qualityDashboardService,
        IngestionQualityBridge ingestionQualityBridge,
        QualityReportExportService qualityReportExportService,
        SqlRepairService sqlRepairService
    ) {
        this.qualityRuleService = qualityRuleService;
        this.qualityRunService = qualityRunService;
        this.complianceService = complianceService;
        this.issueTicketService = issueTicketService;
        this.governanceOpsMetricsService = governanceOpsMetricsService;
        this.referenceCodeService = referenceCodeService;
        this.auditService = auditService;
        this.qualityAuditRecorder = qualityAuditRecorder;
        this.qualityDatasetReadGuard = qualityDatasetReadGuard;
        this.templateRepository = templateRepository;
        this.sqlTemplateRenderer = sqlTemplateRenderer;
        this.failingRowRepository = failingRowRepository;
        this.dataSource = dataSource;
        this.dataCleansingService = dataCleansingService;
        this.cleansingFunctionRepository = cleansingFunctionRepository;
        this.ruleRepository = ruleRepository;
        this.ruleBindingRepository = ruleBindingRepository;
        this.governanceProperties = governanceProperties;
        this.odsDataEditorService = odsDataEditorService;
        this.editLogRepository = editLogRepository;
        this.qualityScoreService = qualityScoreService;
        this.qualityDashboardService = qualityDashboardService;
        this.ingestionQualityBridge = ingestionQualityBridge;
        this.qualityReportExportService = qualityReportExportService;
        this.sqlRepairService = sqlRepairService;
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

    @GetMapping("/quality/rules/{id}/history")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<Map<String, Object>>> getRuleHistory(
        @PathVariable UUID id,
        @RequestParam(value = "limit", defaultValue = "10") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> history = qualityRunService
            .recentByRule(id, safeLimit, activeDept)
            .stream()
            .map(this::toRuleHistoryItem)
            .toList();
        return ApiResponses.ok(history);
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

    private Map<String, Object> toRuleHistoryItem(QualityRunDto run) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("runId", run.getId());
        item.put("time", run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt());
        item.put("status", normalizeQualityRunHistoryStatus(run.getStatus()));
        item.put("passRate", calculatePassRate(run));
        item.put("failingRows", run.getFailingRowCount() != null ? run.getFailingRowCount() : 0);
        return item;
    }

    private boolean isQualityRunPassed(String status) {
        return "SUCCESS".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status) || "PASSED".equalsIgnoreCase(status);
    }

    private String normalizeQualityRunHistoryStatus(String status) {
        if ("SKIPPED".equalsIgnoreCase(status)) {
            return "SKIPPED";
        }
        return isQualityRunPassed(status) ? "PASSED" : "FAILED";
    }

    private Integer calculatePassRate(QualityRunDto run) {
        if ("SKIPPED".equalsIgnoreCase(run.getStatus())) {
            return null;
        }
        Integer total = run.getRowsTotal();
        if (total != null && total > 0) {
            int failing = run.getFailingRowCount() != null ? run.getFailingRowCount() : 0;
            return Math.max(0, Math.min(100, Math.round(((total - failing) * 100.0f) / total)));
        }
        if (run.getStatus() == null) {
            return null;
        }
        return isQualityRunPassed(run.getStatus()) ? 100 : 0;
    }

    @PostMapping("/quality/runs")
    @PreAuthorize("(" + GOVERNANCE_MAINTAINER_EXPRESSION + ") or (" + INGESTION_SERVICE_EXPRESSION + ")")
    public ApiResponse<List<QualityRunDto>> triggerQualityRun(
        @RequestBody QualityRunTriggerRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String resourceId = request != null && request.getRuleId() != null ? request.getRuleId().toString() : "trigger";
        try {
            List<QualityRunDto> runs = isTrustedIngestionService()
                ? qualityRunService.trigger(request, currentUser())
                : qualityRunService.trigger(request, currentUser(), activeDept);
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
            auditQualityAction("GOV_RULE_EXECUTE", AuditStage.SUCCESS, resourceId, "执行质量检测", payload);
            return ApiResponses.ok(runs);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_RULE_EXECUTE", resourceId, "执行质量检测失败", ex);
            throw ex;
        }
    }

    private boolean isTrustedIngestionService() {
        return SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.SERVICE_INTERNAL) &&
            "service:dts-ingestion".equals(currentUser());
    }

    @PostMapping("/quality/runs/dry-run")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<QualityRunDto>> dryRunQualityRule(
        @RequestBody QualityRunTriggerRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QualityRunTriggerRequest effectiveRequest = request != null ? request : new QualityRunTriggerRequest();
        effectiveRequest.setDryRun(Boolean.TRUE);
        if (!StringUtils.hasText(effectiveRequest.getTriggerType())) {
            effectiveRequest.setTriggerType("DRY_RUN");
        }
        String resourceId = effectiveRequest.getRuleId() != null ? effectiveRequest.getRuleId().toString() : "dry-run";
        try {
            List<QualityRunDto> runs = qualityRunService.trigger(effectiveRequest, currentUser(), activeDept);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "试跑质量规则");
            payload.put("runCount", runs.size());
            if (effectiveRequest.getRuleId() != null) {
                payload.put("ruleId", effectiveRequest.getRuleId().toString());
            }
            if (effectiveRequest.getDatasetId() != null) {
                payload.put("datasetId", effectiveRequest.getDatasetId().toString());
            }
            auditQualityAction("GOV_RULE_DRY_RUN", AuditStage.SUCCESS, resourceId, "试跑质量规则", payload);
            return ApiResponses.ok(runs);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_RULE_DRY_RUN", resourceId, "试跑质量规则失败", ex);
            throw ex;
        }
    }

    @GetMapping("/quality/runs/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<QualityRunDto> getQualityRun(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        QualityRunDto dto = qualityRunService.getRun(id, activeDept);
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
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<QualityRunDto>> listQualityRuns(
        @RequestParam(value = "ruleId", required = false) UUID ruleId,
        @RequestParam(value = "datasetId", required = false) UUID datasetId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "triggerType", required = false) String triggerType,
        @RequestParam(value = "startedFrom", required = false) Instant startedFrom,
        @RequestParam(value = "startedTo", required = false) Instant startedTo,
        @RequestParam(value = "startFrom", required = false) Instant startFrom,
        @RequestParam(value = "startTo", required = false) Instant startTo,
        @RequestParam(value = "limit", defaultValue = "10") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Instant effectiveStartedFrom = startedFrom != null ? startedFrom : startFrom;
        Instant effectiveStartedTo = startedTo != null ? startedTo : startTo;
        List<QualityRunDto> runs = qualityRunService.listRuns(
            ruleId,
            datasetId,
            status,
            triggerType,
            effectiveStartedFrom,
            effectiveStartedTo,
            limit,
            activeDept
        );
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
        if (StringUtils.hasText(triggerType)) {
            payload.put("triggerType", triggerType.trim().toUpperCase(Locale.ROOT));
        }
        if (effectiveStartedFrom != null) {
            payload.put("startedFrom", effectiveStartedFrom.toString());
        }
        if (effectiveStartedTo != null) {
            payload.put("startedTo", effectiveStartedTo.toString());
        }
        String resourceId = ruleId != null
            ? ruleId.toString()
            : datasetId != null
                ? datasetId.toString()
                : "recent";
        auditService.auditAction("GOV_QUALITY_RUN_LIST", AuditStage.SUCCESS, resourceId, payload);
        return ApiResponses.ok(runs);
    }

    // Quality failing-row drill-down APIs ------------------------------------

    /**
     * 查询某次质量检测运行的失败行明细（分页）
     */
    @GetMapping("/quality/runs/{runId}/failing-rows")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listFailingRows(
        @PathVariable UUID runId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String columnName,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        qualityRunService.assertRunReadable(runId, activeDept);
        // 1. 分页查询 gov_quality_failing_row
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<GovQualityFailingRow> rows;
        if (StringUtils.hasText(columnName)) {
            rows = failingRowRepository.findByRunIdAndColumnName(runId, columnName, pageable);
        } else {
            rows = failingRowRepository.findByRunId(runId, pageable);
        }

        // 2. 对每行，实时查询 ODS 表获取最新行数据
        List<Map<String, Object>> content = new ArrayList<>();
        for (GovQualityFailingRow row : rows.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("rowId", row.getRowId());
            item.put("tableName", row.getTableName());
            item.put("columnName", row.getColumnName());
            item.put("actualValue", row.getActualValue());
            item.put("failReason", row.getFailReason());
            // 实时查询原始行数据
            item.put("rowData", fetchRowData(row.getTableName(), row.getRowId()));
            content.add(item);
        }

        // 3. 返回分页结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("totalElements", rows.getTotalElements());
        result.put("totalPages", rows.getTotalPages());
        result.put("number", rows.getNumber());
        result.put("size", rows.getSize());
        return ApiResponses.ok(result);
    }

    /**
     * 实时查询 ODS 表中的一行数据
     */
    private Map<String, Object> fetchRowData(String tableName, String rowId) {
        // 表名校验，防止 SQL 注入
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return Map.of();
        }
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM " + tableName + " WHERE id::text = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, rowId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            row.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        return row;
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                "event=quality_failing_row_refresh_failed tableName={} errorType={}",
                tableName,
                e.getClass().getSimpleName()
            );
        }
        return Map.of();
    }

    // Quality template APIs ---------------------------------------------------

    @GetMapping("/quality/templates")
    public ApiResponse<List<GovQualityTemplate>> listTemplates() {
        List<GovQualityTemplate> templates = templateRepository.findAll();
        auditQualityAction(
            "GOV_QUALITY_TEMPLATE_LIST",
            AuditStage.SUCCESS,
            "LIST",
            "查看质量模板列表",
            Map.of("count", templates.size())
        );
        return ApiResponses.ok(templates);
    }

    @GetMapping("/quality/templates/{id}")
    public ApiResponse<GovQualityTemplate> getTemplate(@PathVariable UUID id) {
        GovQualityTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("质量模板不存在"));
        auditQualityAction(
            "GOV_QUALITY_TEMPLATE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            "查看质量模板",
            Map.of("templateCode", valueOrUnknown(template.getCode()), "templateName", valueOrUnknown(template.getName()))
        );
        return ApiResponses.ok(template);
    }

    @PostMapping("/quality/templates")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovQualityTemplate> createTemplate(@RequestBody GovQualityTemplate template) {
        String requestedCode = qualityResourceId(template != null ? template.getCode() : null, "UNASSIGNED");
        try {
            template.setId(null);
            if (template.getBuiltin() == null) {
                template.setBuiltin(Boolean.FALSE);
            }
            if (template.getEnabled() == null) {
                template.setEnabled(Boolean.TRUE);
            }
            GovQualityTemplate saved = templateRepository.save(template);
            auditQualityAction(
                "GOV_QUALITY_TEMPLATE_CREATE",
                AuditStage.SUCCESS,
                qualityResourceId(saved.getId(), requestedCode),
                "创建质量模板",
                Map.of("templateCode", valueOrUnknown(saved.getCode()), "templateName", valueOrUnknown(saved.getName()))
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_TEMPLATE_CREATE", requestedCode, "创建质量模板失败", ex);
            throw ex;
        }
    }

    @PutMapping("/quality/templates/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovQualityTemplate> updateTemplate(@PathVariable UUID id, @RequestBody GovQualityTemplate template) {
        try {
            GovQualityTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("质量模板不存在"));
            if (StringUtils.hasText(template.getCode())) {
                existing.setCode(template.getCode().trim());
            }
            if (StringUtils.hasText(template.getName())) {
                existing.setName(template.getName().trim());
            }
            if (StringUtils.hasText(template.getCategory())) {
                existing.setCategory(template.getCategory().trim());
            }
            if (template.getDescription() != null) {
                existing.setDescription(template.getDescription());
            }
            if (template.getParamSchema() != null) {
                existing.setParamSchema(template.getParamSchema());
            }
            if (StringUtils.hasText(template.getSqlTemplate())) {
                existing.setSqlTemplate(template.getSqlTemplate());
            }
            if (template.getSeverityDefault() != null) {
                existing.setSeverityDefault(template.getSeverityDefault());
            }
            if (template.getActionDefault() != null) {
                existing.setActionDefault(template.getActionDefault());
            }
            if (template.getDialect() != null) {
                existing.setDialect(template.getDialect());
            }
            if (template.getEnabled() != null) {
                existing.setEnabled(template.getEnabled());
            }
            GovQualityTemplate saved = templateRepository.save(existing);
            auditQualityAction(
                "GOV_QUALITY_TEMPLATE_UPDATE",
                AuditStage.SUCCESS,
                id.toString(),
                "更新质量模板",
                Map.of("templateCode", valueOrUnknown(saved.getCode()), "templateName", valueOrUnknown(saved.getName()))
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_TEMPLATE_UPDATE", id.toString(), "更新质量模板失败", ex);
            throw ex;
        }
    }

    @DeleteMapping("/quality/templates/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteTemplate(@PathVariable UUID id) {
        try {
            GovQualityTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("质量模板不存在"));
            if (Boolean.TRUE.equals(existing.getBuiltin())) {
                throw new IllegalArgumentException("内置模板不可删除");
            }
            templateRepository.delete(existing);
            auditQualityAction(
                "GOV_QUALITY_TEMPLATE_DELETE",
                AuditStage.SUCCESS,
                id.toString(),
                "删除质量模板",
                Map.of("templateCode", valueOrUnknown(existing.getCode()), "templateName", valueOrUnknown(existing.getName()))
            );
            return ApiResponses.ok(Boolean.TRUE);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_TEMPLATE_DELETE", id.toString(), "删除质量模板失败", ex);
            throw ex;
        }
    }

    @PostMapping("/quality/templates/{id}/preview")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, String>> previewTemplate(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> params
    ) {
        try {
            GovQualityTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("质量模板不存在"));
            String renderedSql = sqlTemplateRenderer.render(
                template.getSqlTemplate(), params, template.getParamSchema());
            auditQualityAction(
                "GOV_QUALITY_TEMPLATE_PREVIEW",
                AuditStage.SUCCESS,
                id.toString(),
                "预览质量模板",
                Map.of("templateCode", valueOrUnknown(template.getCode()), "parameterCount", params != null ? params.size() : 0)
            );
            return ApiResponses.ok(Map.of("sql", renderedSql));
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_TEMPLATE_PREVIEW", id.toString(), "预览质量模板失败", ex);
            throw ex;
        }
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
        auditService.auditAction("GOV_COMPLIANCE_RUN", AuditStage.SUCCESS, null, detail);
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
        auditService.auditAction("GOV_COMPLIANCE_REVIEW", AuditStage.SUCCESS, id.toString(), detail);
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

    // Auto-trigger API (F2/T03) ---------------------------------------------

    /**
     * 对已授权的数据集触发质量检测。自动清洗链路在安全边界收敛前保持关闭。
     * 参数: tableName (String), datasetId (UUID, 必填)
     */
    @PostMapping("/quality/auto-trigger")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    @Transactional
    public ApiResponse<Map<String, Object>> autoTrigger(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String resourceId = body != null ? qualityResourceId(body.get("tableName"), "UNASSIGNED") : "UNASSIGNED";
        try {
            return autoTriggerInternal(body, activeDept);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_AUTO_TRIGGER", resourceId, "自动触发质量检测失败", ex);
            throw ex;
        }
    }

    private ApiResponse<Map<String, Object>> autoTriggerInternal(Map<String, Object> body, String activeDept) {
        String tableName = body != null ? String.valueOf(body.getOrDefault("tableName", "")).trim() : "";
        if (!StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException("参数 tableName 不能为空");
        }

        Object dsObj = body.get("datasetId");
        if (dsObj == null || !StringUtils.hasText(String.valueOf(dsObj).trim())) {
            throw new IllegalArgumentException("参数 datasetId 不能为空");
        }
        UUID datasetId = UUID.fromString(String.valueOf(dsObj).trim());
        CatalogDataset dataset = qualityDatasetReadGuard.requireReadable(datasetId, activeDept);
        String authorizedTable = dataset != null && dataset.getHiveTable() != null ? dataset.getHiveTable().trim() : "";
        if (!StringUtils.hasText(authorizedTable) || !authorizedTable.equalsIgnoreCase(tableName)) {
            throw new AccessDeniedException("请求表与授权数据集不匹配");
        }

        if (requestsAutoCleanse(body)) {
            throw new UnsupportedOperationException("质量自动清洗暂未开放");
        }
        String triggerRef = currentUser();

        boolean autoTriggerEnabled = governanceProperties.getQuality().isEnabled();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("autoTriggerEnabled", autoTriggerEnabled);
        result.put("autoCleanseEnabled", false);

        Map<String, Object> cleanseResult = new LinkedHashMap<>();
        cleanseResult.put("status", "SKIPPED");
        cleanseResult.put("reason", "auto-cleanse-unavailable");
        result.put("cleanse", cleanseResult);

        // Step 2: 自动触发质量检测
        List<Map<String, Object>> qualityResults = new ArrayList<>();
        if (autoTriggerEnabled) {
            Set<UUID> visibleRuleIds = qualityRuleService
                .listAll(activeDept)
                .stream()
                .map(QualityRuleDto::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
            Set<UUID> boundRuleIds = ruleBindingRepository
                .findByDatasetIdAndRuleVersionStatus(datasetId, "PUBLISHED")
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(GovRuleBinding::getRuleVersion)
                .filter(java.util.Objects::nonNull)
                .filter(version -> "PUBLISHED".equalsIgnoreCase(StringUtils.trimWhitespace(version.getStatus())))
                .map(version -> version.getRule())
                .filter(java.util.Objects::nonNull)
                .map(GovRule::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
            List<GovRule> autoRules = ruleRepository
                .findByAutoTriggerTrueAndEnabledTrue()
                .stream()
                .filter(rule ->
                    rule != null &&
                    rule.getId() != null &&
                    visibleRuleIds.contains(rule.getId()) &&
                    boundRuleIds.contains(rule.getId())
                )
                .toList();

            for (GovRule rule : autoRules) {
                try {
                    QualityRunTriggerRequest triggerRequest = new QualityRunTriggerRequest();
                    triggerRequest.setRuleId(rule.getId());
                    triggerRequest.setTriggerType("AUTO");
                    triggerRequest.setDatasetId(datasetId);
                    List<QualityRunDto> runs = qualityRunService.triggerAuthorizedIndependent(triggerRequest, triggerRef, activeDept);

                    Map<String, Object> ruleResult = new LinkedHashMap<>();
                    ruleResult.put("ruleId", rule.getId().toString());
                    ruleResult.put("ruleName", StringUtils.hasText(rule.getName()) ? rule.getName() : rule.getCode());
                    ruleResult.put("runCount", runs.size());
                    ruleResult.put("status", "TRIGGERED");
                    qualityResults.add(ruleResult);
                } catch (Exception e) {
                    log.warn(
                        "event=quality_auto_trigger_rule_failed ruleId={} errorType={}",
                        rule.getId(),
                        e.getClass().getSimpleName()
                    );
                    Map<String, Object> ruleResult = new LinkedHashMap<>();
                    ruleResult.put("ruleId", rule.getId().toString());
                    ruleResult.put("ruleName", StringUtils.hasText(rule.getName()) ? rule.getName() : rule.getCode());
                    ruleResult.put("runCount", 0);
                    ruleResult.put("status", "FAILED");
                    ruleResult.put("error", "质量规则执行失败");
                    ruleResult.put("errorType", e.getClass().getSimpleName());
                    ruleResult.put("errorCategory", qualityAuditErrorCategory(e));
                    qualityResults.add(ruleResult);
                }
            }
        }
        result.put("qualityRuns", qualityResults);
        result.put("totalRulesTriggered", qualityResults.size());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "自动触发质量检测");
        auditPayload.put("tableName", tableName);
        if (datasetId != null) {
            auditPayload.put("datasetId", datasetId.toString());
        }
        auditPayload.put("rulesTriggered", qualityResults.size());
        long failedRules = qualityResults
            .stream()
            .filter(item -> "FAILED".equals(item.get("status")))
            .count();
        auditPayload.put("failedRules", failedRules);
        auditQualityAction(
            "GOV_AUTO_TRIGGER",
            failedRules > 0 ? AuditStage.FAIL : AuditStage.SUCCESS,
            tableName,
            "自动触发质量检测",
            auditPayload
        );

        return ApiResponses.ok(result);
    }

    private boolean requestsAutoCleanse(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return List.of("autoCleanse", "cleanse", "cleaning")
            .stream()
            .map(body::get)
            .anyMatch(value -> Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)));
    }

    @PostMapping("/quality/cleansing/preview")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> previewQualityCleansing(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditResourceId = auditBodyResourceId(body, "runId", "UNASSIGNED");
        try {
            UUID runId = requiredRunId(body);
            qualityRunService.assertRunReadable(runId, activeDept);
            throw new UnsupportedOperationException("质量清洗预览暂未开放");
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_CLEANSING_PREVIEW", auditResourceId, "预览质量问题清洗失败", ex);
            throw ex;
        }
    }

    @PostMapping("/quality/cleansing/execute")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> executeQualityCleansing(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditResourceId = auditBodyResourceId(body, "runId", "UNASSIGNED");
        try {
            UUID runId = requiredRunId(body);
            qualityRunService.assertRunReadable(runId, activeDept);
            throw new UnsupportedOperationException("质量清洗执行暂未开放");
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_CLEANSING_EXECUTE", auditResourceId, "执行质量问题清洗失败", ex);
            throw ex;
        }
    }

    private GovCleansingFunction resolveCleansingFunction(UUID functionId) {
        GovCleansingFunction function = cleansingFunctionRepository
            .findById(functionId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("清洗函数不存在"));
        if (!Boolean.TRUE.equals(function.getEnabled())) {
            throw new IllegalArgumentException("清洗函数未启用");
        }
        return function;
    }

    private void validateCleansingFunctionReady(GovCleansingFunction function) {
        String expression = function.getSqlExpression();
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("清洗函数 SQL 表达式为空");
        }
        String unresolved = expression.replace("{{column}}", "");
        if (unresolved.contains("{{") || unresolved.contains("}}")) {
            throw new IllegalArgumentException("清洗函数包含未配置参数，请先在清洗函数管理中补齐参数化表达式");
        }
    }

    private Map<String, Object> previewCleansingRow(GovQualityFailingRow row, GovCleansingFunction function) throws java.sql.SQLException {
        String tableName = safeIdentifier(row.getTableName(), "表名");
        String columnName = safeIdentifier(row.getColumnName(), "字段名");
        String expression = renderCleansingExpression(function, columnName);
        String sql = "SELECT " + quoteIdentifier(columnName) + " AS before_value, " + expression + " AS after_value FROM "
            + quoteIdentifier(tableName) + " WHERE id::text = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, row.getRowId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Object before = rs.getObject("before_value");
                Object after = rs.getObject("after_value");
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("rowId", row.getRowId());
                sample.put("before", before != null ? String.valueOf(before) : "");
                sample.put("after", after != null ? String.valueOf(after) : "");
                return sample;
            }
        }
    }

    private int executeCleansingRow(GovQualityFailingRow row, GovCleansingFunction function) throws java.sql.SQLException {
        String tableName = safeIdentifier(row.getTableName(), "表名");
        String columnName = safeIdentifier(row.getColumnName(), "字段名");
        String expression = renderCleansingExpression(function, columnName);
        String sql = "UPDATE " + quoteIdentifier(tableName)
            + " SET " + quoteIdentifier(columnName) + " = " + expression
            + " WHERE id::text = ?"
            + " AND " + quoteIdentifier(columnName) + "::text IS DISTINCT FROM (" + expression + ")::text";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, row.getRowId());
            return ps.executeUpdate();
        }
    }

    private String renderCleansingExpression(GovCleansingFunction function, String columnName) {
        String rawExpression = function.getSqlExpression();
        if (!StringUtils.hasText(rawExpression)) {
            throw new IllegalArgumentException("清洗函数 SQL 表达式为空");
        }
        String normalized = rawExpression.trim();
        if (normalized.matches("(?is).*\\b(INSERT|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|COPY|EXEC|EXECUTE|INTO|FROM|UNION)\\b.*")
            || normalized.contains(";")
            || normalized.contains("--")) {
            throw new IllegalArgumentException("清洗函数 SQL 表达式包含危险关键字");
        }
        return normalized.replace("{{column}}", quoteIdentifier(columnName));
    }

    private String safeIdentifier(String value, String label) {
        if (!StringUtils.hasText(value) || !value.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return value;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // Cleansing function CRUD APIs ------------------------------------------

    @GetMapping("/cleansing/functions")
    public ApiResponse<List<GovCleansingFunction>> listCleansingFunctions() {
        List<GovCleansingFunction> functions = cleansingFunctionRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        auditQualityAction(
            "GOV_QUALITY_CLEANSING_FUNCTION_LIST",
            AuditStage.SUCCESS,
            "LIST",
            "查看清洗函数列表",
            Map.of("count", functions.size())
        );
        return ApiResponses.ok(functions);
    }

    @PostMapping("/cleansing/functions")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovCleansingFunction> createCleansingFunction(@RequestBody GovCleansingFunction function) {
        String requestedCode = qualityResourceId(function != null ? function.getCode() : null, "UNASSIGNED");
        try {
            function.setId(null);
            if (function.getBuiltin() == null) {
                function.setBuiltin(Boolean.FALSE);
            }
            if (function.getEnabled() == null) {
                function.setEnabled(Boolean.TRUE);
            }
            if (function.getDisplayOrder() == null) {
                function.setDisplayOrder(0);
            }
            GovCleansingFunction saved = cleansingFunctionRepository.save(function);
            auditQualityAction(
                "GOV_QUALITY_CLEANSING_FUNCTION_CREATE",
                AuditStage.SUCCESS,
                qualityResourceId(saved.getId(), requestedCode),
                "创建清洗函数",
                Map.of("functionCode", valueOrUnknown(saved.getCode()), "functionName", valueOrUnknown(saved.getName()))
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_CLEANSING_FUNCTION_CREATE", requestedCode, "创建清洗函数失败", ex);
            throw ex;
        }
    }

    @PutMapping("/cleansing/functions/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovCleansingFunction> updateCleansingFunction(
        @PathVariable UUID id,
        @RequestBody GovCleansingFunction function
    ) {
        try {
            GovCleansingFunction existing = cleansingFunctionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("清洗函数不存在"));
            if (StringUtils.hasText(function.getCode())) {
                existing.setCode(function.getCode().trim());
            }
            if (StringUtils.hasText(function.getName())) {
                existing.setName(function.getName().trim());
            }
            if (function.getDescription() != null) {
                existing.setDescription(function.getDescription());
            }
            if (StringUtils.hasText(function.getSqlExpression())) {
                existing.setSqlExpression(function.getSqlExpression());
            }
            if (function.getDisplayOrder() != null) {
                existing.setDisplayOrder(function.getDisplayOrder());
            }
            if (function.getEnabled() != null) {
                existing.setEnabled(function.getEnabled());
            }
            GovCleansingFunction saved = cleansingFunctionRepository.save(existing);
            auditQualityAction(
                "GOV_QUALITY_CLEANSING_FUNCTION_UPDATE",
                AuditStage.SUCCESS,
                id.toString(),
                "更新清洗函数",
                Map.of("functionCode", valueOrUnknown(saved.getCode()), "functionName", valueOrUnknown(saved.getName()))
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_CLEANSING_FUNCTION_UPDATE", id.toString(), "更新清洗函数失败", ex);
            throw ex;
        }
    }

    @DeleteMapping("/cleansing/functions/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteCleansingFunction(@PathVariable UUID id) {
        try {
            GovCleansingFunction existing = cleansingFunctionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("清洗函数不存在"));
            if (Boolean.TRUE.equals(existing.getBuiltin())) {
                throw new IllegalArgumentException("内置清洗函数不可删除");
            }
            cleansingFunctionRepository.delete(existing);
            auditQualityAction(
                "GOV_QUALITY_CLEANSING_FUNCTION_DELETE",
                AuditStage.SUCCESS,
                id.toString(),
                "删除清洗函数",
                Map.of("functionCode", valueOrUnknown(existing.getCode()), "functionName", valueOrUnknown(existing.getName()))
            );
            return ApiResponses.ok(Boolean.TRUE);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_CLEANSING_FUNCTION_DELETE", id.toString(), "删除清洗函数失败", ex);
            throw ex;
        }
    }

    // ODS Data Editor APIs ---------------------------------------------------

    @GetMapping("/data-editor/tables")
    public ApiResponse<List<String>> listOdsTables() {
        return ApiResponses.ok(odsDataEditorService.listOdsTables());
    }

    @GetMapping("/data-editor/{tableName}/columns")
    public ApiResponse<List<Map<String, String>>> listOdsColumns(@PathVariable String tableName) {
        return ApiResponses.ok(odsDataEditorService.listColumns(tableName));
    }

    @GetMapping("/data-editor/{tableName}/rows")
    public ApiResponse<Map<String, Object>> listOdsRows(
        @PathVariable String tableName,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponses.ok(odsDataEditorService.listRows(tableName, page, size));
    }

    @PutMapping("/data-editor/{tableName}/rows/{rowId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> updateOdsRow(
        @PathVariable String tableName,
        @PathVariable String rowId,
        @RequestBody Map<String, String> body
    ) {
        String reason = body.remove("_reason");
        String failingRowId = body.remove("_failingRowId");
        String actor = SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new IllegalStateException("无法获取当前用户"));
        odsDataEditorService.updateRow(tableName, rowId, body, reason, actor);
        if (StringUtils.hasText(failingRowId)) {
            failingRowRepository.deleteById(UUID.fromString(failingRowId));
        }
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/data-editor/{tableName}/rows")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> insertOdsRow(
        @PathVariable String tableName,
        @RequestBody Map<String, String> body
    ) {
        String reason = body.remove("_reason");
        String actor = SecurityUtils.getCurrentUserLogin().orElseThrow(() ->
            new IllegalStateException("无法获取当前用户"));
        Map<String, Object> newRow = odsDataEditorService.insertRow(tableName, body, reason, actor);
        return ApiResponses.ok(newRow);
    }

    @GetMapping("/data-editor/audit-log")
    public ApiResponse<Page<GovDataEditLog>> listAuditLog(
        @RequestParam String tableName,
        @RequestParam(required = false) String rowId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("editedAt").descending());
        Page<GovDataEditLog> result;
        if (StringUtils.hasText(rowId)) {
            result = editLogRepository.findByTableNameAndRowId(tableName, rowId, pageable);
        } else {
            result = editLogRepository.findByTableName(tableName, pageable);
        }
        return ApiResponses.ok(result);
    }

    // Quality score API -------------------------------------------------------

    @GetMapping("/quality/score")
    public ApiResponse<QualityScoreResult> getQualityScore(
        @RequestParam UUID datasetId,
        @RequestParam(defaultValue = "7") int periodDays,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(qualityScoreService.calculate(datasetId, periodDays, activeDept));
    }

    // Quality dashboard API ---------------------------------------------------

    @GetMapping("/quality/dashboard")
    public ApiResponse<QualityDashboardDto> getQualityDashboard(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(qualityDashboardService.getDashboard(activeDept));
    }

    // Ingestion pre-check API ------------------------------------------------

    /**
     * Internal endpoint called by dts-ingestion to run governance quality rules
     * against a staging table before data is committed to the target dataset.
     */
    @PostMapping("/quality/pre-check")
    @PreAuthorize(INGESTION_SERVICE_EXPRESSION)
    public ApiResponse<PreCheckResult> preCheckStagingData(@RequestBody PreCheckRequest request) {
        log.info("Pre-check requested: staging={}, dataset={}, rows={}",
            request.stagingTableName(), request.datasetId(), request.totalRows());
        PreCheckResult result = ingestionQualityBridge.preCheck(
            request.stagingTableName(), request.datasetId(), request.totalRows());
        return ApiResponses.ok(result);
    }

    // Quality report export API -----------------------------------------------

    @GetMapping("/quality/report/export")
    public void exportQualityReport(
        @RequestParam UUID datasetId,
        @RequestParam(defaultValue = "30") int periodDays,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        HttpServletResponse response
    ) throws IOException {
        try {
            byte[] excelBytes = qualityReportExportService.exportExcel(datasetId, periodDays, activeDept);

            String filename = "quality-report-" + datasetId + "-"
                + java.time.LocalDate.now() + ".xlsx";

            auditQualityAction(
                "GOV_QUALITY_REPORT_EXPORT",
                AuditStage.SUCCESS,
                datasetId.toString(),
                "导出质量报告",
                Map.of("datasetId", datasetId.toString(), "periodDays", periodDays, "bytes", excelBytes.length)
            );
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(excelBytes.length);
            response.getOutputStream().write(excelBytes);
        } catch (IOException | RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_REPORT_EXPORT", datasetId.toString(), "导出质量报告失败", ex);
            throw ex;
        }
    }

    // SQL repair API ----------------------------------------------------------

    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    @PostMapping("/quality/sql-repair/preview")
    public ApiResponse<SqlRepairService.SqlRepairPreview> previewSqlRepair(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditResourceId = auditBodyResourceId(body, "runId", "UNASSIGNED");
        try {
            UUID runId = requiredRunId(body);
            qualityRunService.assertRunReadable(runId, activeDept);
            String sql = (String) body.get("sql");
            int limit = body.containsKey("limit") ? ((Number) body.get("limit")).intValue() : 10;
            SqlRepairService.SqlRepairPreview preview = sqlRepairService.preview(sql, limit);
            auditQualityAction(
                "GOV_QUALITY_SQL_REPAIR_PREVIEW",
                AuditStage.SUCCESS,
                runId.toString(),
                "预览质量 SQL 修复",
                Map.of("runId", runId.toString(), "limit", limit, "sqlLength", sql != null ? sql.length() : 0)
            );
            return ApiResponses.ok(preview);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_SQL_REPAIR_PREVIEW", auditResourceId, "预览质量 SQL 修复失败", ex);
            throw ex;
        }
    }

    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    @PostMapping("/quality/sql-repair/execute")
    public ApiResponse<SqlRepairService.SqlRepairResult> executeSqlRepair(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditResourceId = auditBodyResourceId(body, "runId", "UNASSIGNED");
        try {
            UUID runId = requiredRunId(body);
            qualityRunService.assertRunReadable(runId, activeDept);
            String sql = (String) body.get("sql");
            SqlRepairService.SqlRepairResult result = sqlRepairService.execute(sql, runId);
            auditQualityAction(
                "GOV_QUALITY_SQL_REPAIR_EXECUTE",
                AuditStage.SUCCESS,
                auditResourceId,
                "执行质量 SQL 修复",
                Map.of("runId", runId.toString(), "sqlLength", sql != null ? sql.length() : 0)
            );
            return ApiResponses.ok(result);
        } catch (RuntimeException ex) {
            auditQualityFailure("GOV_QUALITY_SQL_REPAIR_EXECUTE", auditResourceId, "执行质量 SQL 修复失败", ex);
            throw ex;
        }
    }

    private void auditQualityFailure(String actionCode, String resourceId, String summary, Exception error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorType", error.getClass().getSimpleName());
        details.put("errorCategory", qualityAuditErrorCategory(error));
        String safeResourceId = qualityResourceId(resourceId, "UNASSIGNED");
        try {
            qualityAuditRecorder.recordFailureAction(
                actionCode,
                safeResourceId,
                qualityAuditPayload(summary, details)
            );
        } catch (RuntimeException auditFailure) {
            log.warn(
                "event=quality_failure_audit_write_failed actionCode={} resourceId={} errorType={}",
                actionCode,
                safeResourceId,
                auditFailure.getClass().getSimpleName()
            );
        }
    }

    private void auditQualityAction(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String summary,
        Map<String, ?> details
    ) {
        Map<String, Object> payload = qualityAuditPayload(summary, details);
        String safeResourceId = qualityResourceId(resourceId, "UNASSIGNED");
        qualityAuditRecorder.recordAction(actionCode, stage, safeResourceId, payload);
    }

    private Map<String, Object> qualityAuditPayload(String summary, Map<String, ?> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        if (details != null) {
            details.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    payload.put(key, value);
                }
            });
        }
        return payload;
    }

    private String qualityAuditErrorCategory(Throwable error) {
        if (error instanceof jakarta.persistence.EntityNotFoundException) {
            return "NOT_FOUND";
        }
        if (error instanceof org.springframework.security.access.AccessDeniedException) {
            return "ACCESS_DENIED";
        }
        if (error instanceof IllegalArgumentException) {
            return "VALIDATION";
        }
        String type = error != null ? error.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT) : "";
        if (type.contains("SQL") || type.contains("JDBC") || type.contains("DATAACCESS")) {
            return "DATA_ACCESS";
        }
        return "INTERNAL_ERROR";
    }

    private String auditBodyResourceId(Map<String, Object> body, String key, String fallback) {
        return body != null ? qualityResourceId(body.get(key), fallback) : fallback;
    }

    private UUID requiredRunId(Map<String, Object> body) {
        Object value = body != null ? body.get("runId") : null;
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("runId 格式不正确");
        }
    }

    private String qualityResourceId(Object value, String fallback) {
        String text = value != null ? String.valueOf(value).trim() : null;
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未知";
    }
}
