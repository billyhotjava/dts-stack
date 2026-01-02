package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleRequestService;
import com.yuzhi.dts.platform.service.catalog.dto.LifecycleRequestDto;
import com.yuzhi.dts.platform.service.catalog.request.LifecycleRequestCreateRequest;
import com.yuzhi.dts.platform.service.catalog.request.LifecycleRequestDecisionRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/lifecycle")
@Transactional
public class CatalogLifecycleRequestResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogLifecycleRequestService service;
    private final AuditService auditService;

    public CatalogLifecycleRequestResource(CatalogLifecycleRequestService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping("/requests")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<LifecycleRequestDto>> list(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "datasetId", required = false) UUID datasetId,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<LifecycleRequestDto> result = service.list(status, datasetId, limit, activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看生命周期申请列表");
        payload.put("count", result.size());
        payload.put("limit", limit);
        if (StringUtils.hasText(status)) {
            payload.put("status", status.trim());
        }
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        if (StringUtils.hasText(activeDept)) {
            payload.put("activeDept", activeDept.trim());
        }
        auditService.auditAction("CATALOG_LIFECYCLE_REQUEST_LIST", AuditStage.SUCCESS, "LIST", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleRequestDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        LifecycleRequestDto dto = service.get(id, activeDept);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看生命周期申请详情");
        payload.put("requestId", id.toString());
        auditService.auditAction("CATALOG_LIFECYCLE_REQUEST_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(dto);
    }

    @PostMapping("/requests")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleRequestDto> submit(
        @RequestBody LifecycleRequestCreateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(service.submit(request, currentUser(), activeDept));
    }

    @PostMapping("/requests/{id}/decide")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleRequestDto> decide(
        @PathVariable UUID id,
        @RequestBody LifecycleRequestDecisionRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(service.decide(id, request, currentUser(), activeDept));
    }

    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<LifecycleRequestDto> cancel(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(service.cancel(id, currentUser(), activeDept));
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}

