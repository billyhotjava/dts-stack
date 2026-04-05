package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.DimensionService;
import com.yuzhi.dts.platform.service.governance.IndicatorDashboardService;
import com.yuzhi.dts.platform.service.governance.IndicatorService;
import com.yuzhi.dts.platform.service.governance.IndicatorPublishPreviewService;
import com.yuzhi.dts.platform.service.governance.IndicatorReferenceService;
import com.yuzhi.dts.platform.service.governance.IndicatorObservabilityService;
import com.yuzhi.dts.platform.service.governance.IndicatorTemplateService;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorSubscriptionRepository;
import com.yuzhi.dts.platform.service.governance.DbtIndicatorGenerator;
import com.yuzhi.dts.platform.service.governance.GenerationResult;
import com.yuzhi.dts.platform.service.governance.dto.DimensionDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorVersionDto;
import com.yuzhi.dts.platform.service.governance.request.DimensionUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.TemplateApplyRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance")
@Transactional
public class GovernanceIndicatorResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final IndicatorService indicators;
    private final DimensionService dimensions;
    private final IndicatorReferenceService indicatorReferences;
    private final IndicatorPublishPreviewService indicatorPublishPreviewService;
    private final IndicatorObservabilityService indicatorObservabilityService;
    private final IndicatorTemplateService indicatorTemplates;
    private final IndicatorDashboardService indicatorDashboard;
    private final DbtIndicatorGenerator dbtGenerator;
    private final GovIndicatorSubscriptionRepository subscriptionRepo;
    private final AuditService audit;

    public GovernanceIndicatorResource(
        IndicatorService indicators,
        DimensionService dimensions,
        IndicatorReferenceService indicatorReferences,
        IndicatorPublishPreviewService indicatorPublishPreviewService,
        IndicatorObservabilityService indicatorObservabilityService,
        IndicatorTemplateService indicatorTemplates,
        IndicatorDashboardService indicatorDashboard,
        DbtIndicatorGenerator dbtGenerator,
        GovIndicatorSubscriptionRepository subscriptionRepo,
        AuditService audit
    ) {
        this.indicators = indicators;
        this.dimensions = dimensions;
        this.indicatorReferences = indicatorReferences;
        this.indicatorPublishPreviewService = indicatorPublishPreviewService;
        this.indicatorObservabilityService = indicatorObservabilityService;
        this.indicatorTemplates = indicatorTemplates;
        this.indicatorDashboard = indicatorDashboard;
        this.dbtGenerator = dbtGenerator;
        this.subscriptionRepo = subscriptionRepo;
        this.audit = audit;
    }

    // Indicators ------------------------------------------------------------

    @GetMapping("/indicators")
    public ApiResponse<Map<String, Object>> listIndicators(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Boolean derived,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        long startedAt = System.nanoTime();
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
        Page<IndicatorDto> result = indicators.list(keyword, status, domain, category, derived, pageable, activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", result.getContent());
        payload.put("total", result.getTotalElements());
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalPages", result.getTotalPages());
        payload.put(
            "pageStats",
            Map.of(
                "page",
                result.getNumber(),
                "size",
                result.getSize(),
                "total",
                result.getTotalElements(),
                "totalPages",
                result.getTotalPages()
            )
        );
        payload.put("queryCostMs", (System.nanoTime() - startedAt) / 1_000_000L);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标字典列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(status)) auditPayload.put("status", status.trim());
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        if (StringUtils.hasText(domain)) auditPayload.put("domain", domain.trim());
        if (StringUtils.hasText(category)) auditPayload.put("category", category.trim());
        if (derived != null) auditPayload.put("derived", derived);
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_INDICATOR_LIST", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/indicators/by-domain")
    public ApiResponse<List<IndicatorDto>> listIndicatorsByDomain(
        @RequestParam String domain,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<IndicatorDto> list = indicators.listByDomain(domain, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "按域查询指标列表");
        auditPayload.put("domain", domain);
        auditPayload.put("count", list.size());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_INDICATOR_LIST_BY_DOMAIN", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/indicators/by-template")
    public ApiResponse<List<IndicatorDto>> listIndicatorsByTemplate(
        @RequestParam UUID templateId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<IndicatorDto> list = indicators.listByTemplateId(templateId, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "按模板查询指标列表");
        auditPayload.put("templateId", templateId.toString());
        auditPayload.put("count", list.size());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_INDICATOR_LIST_BY_TEMPLATE", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/indicators/{id}")
    public ApiResponse<IndicatorDto> getIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto dto = indicators.get(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "查看指标详情");
        if (dto != null && StringUtils.hasText(dto.getName())) {
            detail.put("targetName", dto.getName());
        }
        audit.auditAction("GOV_INDICATOR_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/indicators/ops/overview")
    public ApiResponse<Map<String, Object>> indicatorOpsOverview(
        @RequestParam(defaultValue = "168") int hours,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> payload = indicatorObservabilityService.overview(hours, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标运维概览");
        auditPayload.put("hours", hours);
        if (StringUtils.hasText(activeDept)) {
            auditPayload.put("activeDept", activeDept.trim());
        }
        audit.auditAction("GOV_INDICATOR_OPS_OVERVIEW", AuditStage.SUCCESS, "OVERVIEW", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/indicators/ops/trend")
    public ApiResponse<List<Map<String, Object>>> indicatorOpsTrend(
        @RequestParam(defaultValue = "168") int hours,
        @RequestParam(defaultValue = "24") int bucketHours,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> rows = indicatorObservabilityService.trend(hours, bucketHours, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标校验趋势");
        auditPayload.put("hours", hours);
        auditPayload.put("bucketHours", bucketHours);
        auditPayload.put("rows", rows.size());
        if (StringUtils.hasText(activeDept)) {
            auditPayload.put("activeDept", activeDept.trim());
        }
        audit.auditAction("GOV_INDICATOR_OPS_TREND", AuditStage.SUCCESS, "TREND", auditPayload);
        return ApiResponses.ok(rows);
    }

    @GetMapping("/indicators/{id}/versions")
    public ApiResponse<List<IndicatorVersionDto>> listIndicatorVersions(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<IndicatorVersionDto> versions = indicators.listVersions(id, activeDept);
        audit.auditAction(
            "GOV_INDICATOR_VERSION_LIST",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看指标版本历史", "versionCount", versions != null ? versions.size() : 0)
        );
        return ApiResponses.ok(versions);
    }

    @GetMapping("/indicators/{id}/versions/{version}")
    public ApiResponse<IndicatorVersionDto> getIndicatorVersion(
        @PathVariable UUID id,
        @PathVariable String version,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorVersionDto snapshot = indicators.getVersion(id, version, activeDept);
        audit.auditAction(
            "GOV_INDICATOR_VERSION_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看指标版本快照", "version", version)
        );
        return ApiResponses.ok(snapshot);
    }

    @GetMapping("/indicators/{id}/versions/diff")
    public ApiResponse<Map<String, Object>> diffIndicatorVersions(
        @PathVariable UUID id,
        @RequestParam(value = "left", required = false, defaultValue = "CURRENT") String left,
        @RequestParam(value = "right", required = false) String right,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> payload = indicators.compareVersions(id, left, right, activeDept);
        int diffCount = payload.get("diffCount") instanceof Number number ? number.intValue() : 0;
        audit.auditAction(
            "GOV_INDICATOR_VERSION_DIFF",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看指标版本差异", "left", left, "right", right, "diffCount", diffCount)
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/indicators/{id}/references")
    public ApiResponse<List<Map<String, Object>>> listIndicatorReferences(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> list = indicatorReferences.list(id, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标引用关系");
        auditPayload.put("indicatorId", id.toString());
        auditPayload.put("count", list.size());
        audit.auditAction("GOV_INDICATOR_REFERENCE_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(list);
    }

    @PostMapping("/indicators/{id}/references")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createIndicatorReference(
        @PathVariable UUID id,
        @RequestBody IndicatorReferenceService.ReferenceUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> saved = indicatorReferences.create(id, activeDept, request);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "新增指标引用关系");
        auditPayload.put("indicatorId", id.toString());
        if (request != null) {
            if (StringUtils.hasText(request.refType())) auditPayload.put("refType", request.refType().trim());
            if (StringUtils.hasText(request.refTarget())) auditPayload.put("refTarget", request.refTarget().trim());
        }
        audit.auditAction("GOV_INDICATOR_REFERENCE_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/indicators/{id}/references/{refId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> updateIndicatorReference(
        @PathVariable UUID id,
        @PathVariable UUID refId,
        @RequestBody IndicatorReferenceService.ReferenceUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> saved = indicatorReferences.update(id, refId, activeDept, request);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新指标引用关系");
        auditPayload.put("indicatorId", id.toString());
        auditPayload.put("referenceId", refId.toString());
        audit.auditAction("GOV_INDICATOR_REFERENCE_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/indicators/{id}/references/{refId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteIndicatorReference(
        @PathVariable UUID id,
        @PathVariable UUID refId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        indicatorReferences.delete(id, refId, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "删除指标引用关系");
        auditPayload.put("indicatorId", id.toString());
        auditPayload.put("referenceId", refId.toString());
        audit.auditAction("GOV_INDICATOR_REFERENCE_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/indicators/{id}/publish-preview")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> publishPreview(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> result = indicatorPublishPreviewService.preview(id, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "指标发布一致性预检");
        auditPayload.put("indicatorId", id.toString());
        if (result != null) {
            Object ready = result.get("readyToPublish");
            if (ready != null) auditPayload.put("readyToPublish", ready);
            Object issues = result.get("issues");
            if (issues instanceof List<?> list) {
                auditPayload.put("issueCount", list.size());
            }
        }
        audit.auditAction("GOV_INDICATOR_PUBLISH_PREVIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(result);
    }

    @PostMapping("/indicators")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> createIndicator(
        @RequestBody IndicatorUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto saved = indicators.create(request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", saved.getId() != null ? saved.getId().toString() : "");
        detail.put("targetName", saved.getName());
        detail.put("summary", "创建指标：" + saved.getName());
        audit.auditAction("GOV_INDICATOR_EDIT", AuditStage.SUCCESS, saved.getId() != null ? saved.getId().toString() : "CREATE", detail);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/indicators/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> updateIndicator(
        @PathVariable UUID id,
        @RequestBody IndicatorUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto saved = indicators.update(id, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "更新指标：" + saved.getName());
        audit.auditAction("GOV_INDICATOR_EDIT", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/indicators/{id}/publish")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> publishIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> preview = indicatorPublishPreviewService.preview(id, activeDept);
        boolean ready = Boolean.TRUE.equals(preview.get("readyToPublish"));
        if (!ready) {
            String reasonCode = preview.get("failureReasonCode") != null ? String.valueOf(preview.get("failureReasonCode")) : "IND_PUBLISH_BLOCKED";
            String message = "发布前预检未通过";
            Object blocking = preview.get("blockingIssues");
            if (blocking instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> issue) {
                Object text = issue.get("message");
                if (text != null) {
                    message = String.valueOf(text);
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[" + reasonCode + "] " + message);
        }
        IndicatorDto saved = indicators.publish(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "发布指标：" + saved.getName());
        audit.auditAction("GOV_INDICATOR_PUBLISH", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    public record IndicatorRollbackRequest(String reason, Boolean publishAfterRollback) {}

    @PostMapping("/indicators/{id}/versions/{version}/rollback")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> rollbackIndicatorVersion(
        @PathVariable UUID id,
        @PathVariable String version,
        @RequestBody(required = false) IndicatorRollbackRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        boolean publishAfterRollback = request != null && Boolean.TRUE.equals(request.publishAfterRollback());
        String reason = request != null ? request.reason() : null;
        Map<String, Object> payload = indicators.rollbackToVersion(id, version, activeDept, reason, publishAfterRollback);
        audit.auditAction(
            "GOV_INDICATOR_VERSION_ROLLBACK",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "回滚指标历史版本",
                "sourceVersion",
                version,
                "targetVersion",
                String.valueOf(payload.get("rollbackToVersion")),
                "publishAfterRollback",
                publishAfterRollback
            )
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping("/indicators/{id}/archive")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> archiveIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto saved = indicators.archive(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "废止指标：" + saved.getName());
        audit.auditAction("GOV_INDICATOR_ARCHIVE", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/indicators/{id}/validate")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorValidationResultDto> validateIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorValidationResultDto result = indicators.validateComputeRule(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "校验指标计算规则");
        if (result != null) {
            detail.put("status", result.getStatus());
            if (StringUtils.hasText(result.getMessage())) {
                detail.put("message", result.getMessage());
            }
        }
        audit.auditAction("GOV_INDICATOR_VALIDATE", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(result);
    }

    @PostMapping("/indicators/{id}/preview")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> previewIndicatorComputeRule(
        @PathVariable UUID id,
        @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Map<String, Object> result = indicators.previewComputeRule(id, safeLimit, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "预览指标计算结果");
        auditPayload.put("indicatorId", id.toString());
        auditPayload.put("limit", safeLimit);
        if (result != null) {
            Object status = result.get("status");
            if (status != null) auditPayload.put("status", status);
        }
        audit.auditAction("GOV_INDICATOR_COMPUTE_PREVIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/indicators/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        indicators.delete(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "删除指标");
        audit.auditAction("GOV_INDICATOR_DELETE", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Indicator Templates -----------------------------------------------------

    @GetMapping("/indicator-templates")
    public ApiResponse<List<GovIndicatorTemplate>> listIndicatorTemplates(
        @RequestParam(required = false) String domain
    ) {
        List<GovIndicatorTemplate> list = indicatorTemplates.list(domain);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标模板列表");
        auditPayload.put("count", list.size());
        if (StringUtils.hasText(domain)) auditPayload.put("domain", domain.trim());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_LIST", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/indicator-templates/{id}")
    public ApiResponse<GovIndicatorTemplate> getIndicatorTemplate(@PathVariable UUID id) {
        GovIndicatorTemplate tpl = indicatorTemplates.getById(id);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标模板详情");
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", tpl.getName());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(tpl);
    }

    @PostMapping("/indicator-templates")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovIndicatorTemplate> createIndicatorTemplate(
        @RequestBody GovIndicatorTemplate template
    ) {
        GovIndicatorTemplate saved = indicatorTemplates.create(template);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "创建指标模板：" + saved.getName());
        auditPayload.put("targetId", saved.getId() != null ? saved.getId().toString() : "");
        auditPayload.put("targetName", saved.getName());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_EDIT", AuditStage.SUCCESS,
            saved.getId() != null ? saved.getId().toString() : "CREATE", auditPayload);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/indicator-templates/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GovIndicatorTemplate> updateIndicatorTemplate(
        @PathVariable UUID id,
        @RequestBody GovIndicatorTemplate template
    ) {
        GovIndicatorTemplate saved = indicatorTemplates.update(id, template);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新指标模板：" + saved.getName());
        auditPayload.put("targetId", id.toString());
        auditPayload.put("targetName", saved.getName());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/indicator-templates/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteIndicatorTemplate(@PathVariable UUID id) {
        indicatorTemplates.delete(id);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "删除指标模板");
        auditPayload.put("targetId", id.toString());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_DELETE", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/indicator-templates/{templateId}/apply")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<List<GovIndicatorDefinition>> applyIndicatorTemplate(
        @PathVariable UUID templateId,
        @RequestBody TemplateApplyRequest request
    ) {
        List<GovIndicatorDefinition> created = indicatorTemplates.applyTemplate(templateId, request);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "展开指标模板");
        auditPayload.put("templateId", templateId.toString());
        auditPayload.put("sourceTable", request.sourceTable());
        auditPayload.put("createdCount", created.size());
        audit.auditAction("GOV_INDICATOR_TEMPLATE_APPLY", AuditStage.SUCCESS, templateId.toString(), auditPayload);
        return ApiResponses.ok(created);
    }

    // Indicator Dashboard -----------------------------------------------------

    @GetMapping("/indicators/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
        @RequestParam(required = false) String domain,
        @RequestParam(defaultValue = "30") int days
    ) {
        Map<String, Object> payload = indicatorDashboard.getDashboard(domain, days);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/indicators/{id}/detail")
    public ApiResponse<Map<String, Object>> indicatorDetail(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "30") int days
    ) {
        Map<String, Object> payload = indicatorDashboard.getDetail(id, days);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/indicators/{id}/drilldown")
    public ApiResponse<List<Map<String, Object>>> drilldown(
        @PathVariable UUID id,
        @RequestParam String dimension,
        @RequestParam(required = false) String period
    ) {
        if (!dimension.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dimension parameter");
        }
        List<Map<String, Object>> rows = indicatorDashboard.drilldown(id, dimension, period);
        return ApiResponses.ok(rows);
    }

    // Dimensions ------------------------------------------------------------

    @GetMapping("/dimensions")
    public ApiResponse<Map<String, Object>> listDimensions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        long startedAt = System.nanoTime();
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
        Page<DimensionDto> result = dimensions.list(keyword, status, pageable, activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", result.getContent());
        payload.put("total", result.getTotalElements());
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalPages", result.getTotalPages());
        payload.put(
            "pageStats",
            Map.of(
                "page",
                result.getNumber(),
                "size",
                result.getSize(),
                "total",
                result.getTotalElements(),
                "totalPages",
                result.getTotalPages()
            )
        );
        payload.put("queryCostMs", (System.nanoTime() - startedAt) / 1_000_000L);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看维度字典列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(status)) auditPayload.put("status", status.trim());
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_DIMENSION_LIST", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/dimensions/{id}")
    public ApiResponse<DimensionDto> getDimension(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DimensionDto dto = dimensions.get(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "查看维度详情");
        if (dto != null && StringUtils.hasText(dto.getName())) {
            detail.put("targetName", dto.getName());
        }
        audit.auditAction("GOV_DIMENSION_VIEW", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(dto);
    }

    @PostMapping("/dimensions")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> createDimension(
        @RequestBody DimensionUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DimensionDto saved = dimensions.create(request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", saved.getId() != null ? saved.getId().toString() : "");
        detail.put("targetName", saved.getName());
        detail.put("summary", "创建维度：" + saved.getName());
        audit.auditAction("GOV_DIMENSION_EDIT", AuditStage.SUCCESS, saved.getId() != null ? saved.getId().toString() : "CREATE", detail);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/dimensions/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> updateDimension(
        @PathVariable UUID id,
        @RequestBody DimensionUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DimensionDto saved = dimensions.update(id, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "更新维度：" + saved.getName());
        audit.auditAction("GOV_DIMENSION_EDIT", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/dimensions/{id}/publish")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> publishDimension(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DimensionDto saved = dimensions.publish(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "发布维度：" + saved.getName());
        audit.auditAction("GOV_DIMENSION_PUBLISH", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/dimensions/{id}/archive")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<DimensionDto> archiveDimension(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        DimensionDto saved = dimensions.archive(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "废止维度：" + saved.getName());
        audit.auditAction("GOV_DIMENSION_ARCHIVE", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/dimensions/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDimension(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        dimensions.delete(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("summary", "删除维度");
        audit.auditAction("GOV_DIMENSION_DELETE", AuditStage.SUCCESS, id.toString(), detail);
        return ApiResponses.ok(Boolean.TRUE);
    }

    // dbt Generation -----------------------------------------------------------

    @PostMapping("/indicators/generate")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GenerationResult> generateIndicators(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> idStrings = (List<String>) body.get("indicatorIds");
        List<UUID> ids = idStrings.stream().map(UUID::fromString).toList();
        List<String> files = dbtGenerator.generateBatch(ids);
        dbtGenerator.generateSchemaYml(ids);
        return ApiResponses.ok(new GenerationResult(ids.size(), files, "READY", null, List.of()));
    }

    @PostMapping("/indicators/generate-and-run")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<GenerationResult> generateAndRun(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> idStrings = (List<String>) body.get("indicatorIds");
        List<UUID> ids = idStrings.stream().map(UUID::fromString).toList();
        GenerationResult result = dbtGenerator.generateAndRun(ids);
        return ApiResponses.ok(result);
    }

    @PostMapping("/indicators/{id}/preview-sql")
    public ApiResponse<Map<String, String>> previewIndicatorSql(@PathVariable UUID id) {
        return ApiResponses.ok(dbtGenerator.previewSql(id));
    }

    // Indicator Subscriptions --------------------------------------------------

    private String currentUserLogin() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping("/indicators/subscriptions")
    public ApiResponse<List<GovIndicatorSubscription>> listSubscriptions() {
        String login = currentUserLogin();
        List<GovIndicatorSubscription> list = subscriptionRepo.findByUserLoginOrderByDisplayOrderAsc(login);
        return ApiResponses.ok(list);
    }

    public record SubscriptionUpsertRequest(UUID indicatorId, String filterConfig, Integer displayOrder) {}

    @PostMapping("/indicators/subscriptions")
    public ApiResponse<GovIndicatorSubscription> createSubscription(
        @RequestBody SubscriptionUpsertRequest request
    ) {
        String login = currentUserLogin();
        if (subscriptionRepo.existsByIndicatorIdAndUserLogin(request.indicatorId(), login)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already subscribed");
        }
        GovIndicatorSubscription sub = new GovIndicatorSubscription();
        sub.setIndicatorId(request.indicatorId());
        sub.setUserLogin(login);
        sub.setFilterConfig(request.filterConfig());
        sub.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        sub.setCreatedDate(Instant.now());
        GovIndicatorSubscription saved = subscriptionRepo.save(sub);
        audit.auditAction("GOV_INDICATOR_SUBSCRIBE", AuditStage.SUCCESS, saved.getId().toString(),
            Map.of("summary", "订阅指标", "indicatorId", request.indicatorId().toString()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/indicators/subscriptions/{id}")
    public ApiResponse<GovIndicatorSubscription> updateSubscription(
        @PathVariable UUID id,
        @RequestBody SubscriptionUpsertRequest request
    ) {
        String login = currentUserLogin();
        GovIndicatorSubscription sub = subscriptionRepo.findById(id)
            .filter(s -> login.equals(s.getUserLogin()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (request.filterConfig() != null) sub.setFilterConfig(request.filterConfig());
        if (request.displayOrder() != null) sub.setDisplayOrder(request.displayOrder());
        GovIndicatorSubscription saved = subscriptionRepo.save(sub);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/indicators/subscriptions/{id}")
    public ApiResponse<Boolean> deleteSubscription(@PathVariable UUID id) {
        String login = currentUserLogin();
        GovIndicatorSubscription sub = subscriptionRepo.findById(id)
            .filter(s -> login.equals(s.getUserLogin()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        subscriptionRepo.delete(sub);
        audit.auditAction("GOV_INDICATOR_UNSUBSCRIBE", AuditStage.SUCCESS, id.toString(),
            Map.of("summary", "取消订阅指标", "indicatorId", sub.getIndicatorId().toString()));
        return ApiResponses.ok(Boolean.TRUE);
    }
}
