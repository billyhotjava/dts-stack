package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.DimensionService;
import com.yuzhi.dts.platform.service.governance.IndicatorService;
import com.yuzhi.dts.platform.service.governance.dto.DimensionDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.DimensionUpsertRequest;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/governance")
@Transactional
public class GovernanceIndicatorResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final IndicatorService indicators;
    private final DimensionService dimensions;
    private final AuditService audit;

    public GovernanceIndicatorResource(IndicatorService indicators, DimensionService dimensions, AuditService audit) {
        this.indicators = indicators;
        this.dimensions = dimensions;
        this.audit = audit;
    }

    // Indicators ------------------------------------------------------------

    @GetMapping("/indicators")
    public ApiResponse<Map<String, Object>> listIndicators(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
        Page<IndicatorDto> result = indicators.list(keyword, status, pageable, activeDept);
        Map<String, Object> payload = Map.of(
            "content",
            result.getContent(),
            "total",
            result.getTotalElements(),
            "page",
            result.getNumber(),
            "size",
            result.getSize()
        );
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看指标字典列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(status)) auditPayload.put("status", status.trim());
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.recordAuxiliary("READ", "governance.indicator", "governance.indicator", "LIST", "SUCCESS", auditPayload);
        return ApiResponses.ok(payload);
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
        audit.recordAuxiliary("READ", "governance.indicator", "governance.indicator", id.toString(), "SUCCESS", detail);
        return ApiResponses.ok(dto);
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
        audit.recordAs(currentUser(), "WRITE", "governance.indicator", "governance.indicator", "CREATE", "SUCCESS", detail, null);
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
        audit.recordAs(currentUser(), "WRITE", "governance.indicator", "governance.indicator", id.toString(), "SUCCESS", detail, null);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/indicators/{id}/publish")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> publishIndicator(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto saved = indicators.publish(id, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("targetId", id.toString());
        detail.put("targetName", saved.getName());
        detail.put("summary", "发布指标：" + saved.getName());
        audit.recordAs(currentUser(), "WRITE", "governance.indicator", "governance.indicator", id.toString(), "SUCCESS", detail, null);
        return ApiResponses.ok(saved);
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
        audit.recordAs(currentUser(), "WRITE", "governance.indicator", "governance.indicator", id.toString(), "SUCCESS", detail, null);
        return ApiResponses.ok(saved);
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
        audit.recordAs(currentUser(), "WRITE", "governance.indicator", "governance.indicator", id.toString(), "SUCCESS", detail, null);
        return ApiResponses.ok(Boolean.TRUE);
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
        Page<DimensionDto> result = dimensions.list(keyword, status, pageable, activeDept);
        Map<String, Object> payload = Map.of(
            "content",
            result.getContent(),
            "total",
            result.getTotalElements(),
            "page",
            result.getNumber(),
            "size",
            result.getSize()
        );
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看维度字典列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(status)) auditPayload.put("status", status.trim());
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.recordAuxiliary("READ", "governance.dimension", "governance.dimension", "LIST", "SUCCESS", auditPayload);
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
        audit.recordAuxiliary("READ", "governance.dimension", "governance.dimension", id.toString(), "SUCCESS", detail);
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
        audit.recordAs(currentUser(), "WRITE", "governance.dimension", "governance.dimension", "CREATE", "SUCCESS", detail, null);
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
        audit.recordAs(currentUser(), "WRITE", "governance.dimension", "governance.dimension", id.toString(), "SUCCESS", detail, null);
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
        audit.recordAs(currentUser(), "WRITE", "governance.dimension", "governance.dimension", id.toString(), "SUCCESS", detail, null);
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
        audit.recordAs(currentUser(), "WRITE", "governance.dimension", "governance.dimension", id.toString(), "SUCCESS", detail, null);
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
        audit.recordAs(currentUser(), "WRITE", "governance.dimension", "governance.dimension", id.toString(), "SUCCESS", detail, null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}

