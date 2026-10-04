package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.ModelFieldIndicatorDraftService;
import com.yuzhi.dts.platform.service.governance.ModelFieldIndicatorDraftService.CreateDraftRequest;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/governance/indicators/model-field-drafts")
public class ModelFieldIndicatorDraftResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final ModelFieldIndicatorDraftService service;
    private final AuditService audit;
    private final String serverTenantId;

    public ModelFieldIndicatorDraftResource(
        ModelFieldIndicatorDraftService service,
        AuditService audit,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.audit = audit;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> create(
        @RequestBody CreateDraftRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto created = service.create(serverTenantId, activeDept, request);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "从已发布模型字段创建指标草稿");
        detail.put("modelSpecId", request.modelSpecId().toString());
        detail.put("modelRevision", request.modelRevision());
        detail.put("fieldName", request.fieldName());
        if (StringUtils.hasText(activeDept)) detail.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_INDICATOR_EDIT", AuditStage.SUCCESS, created.getId().toString(), detail);
        return ApiResponses.ok(created);
    }

    @PutMapping("/{indicatorId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<IndicatorDto> rebind(
        @PathVariable UUID indicatorId,
        @RequestBody CreateDraftRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        IndicatorDto saved = service.rebind(serverTenantId, activeDept, indicatorId, request);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "更新指标的固定模型字段实现");
        detail.put("modelSpecId", request.modelSpecId().toString());
        detail.put("modelRevision", request.modelRevision());
        detail.put("fieldName", request.fieldName());
        if (StringUtils.hasText(activeDept)) detail.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_INDICATOR_EDIT", AuditStage.SUCCESS, indicatorId.toString(), detail);
        return ApiResponses.ok(saved);
    }
}
