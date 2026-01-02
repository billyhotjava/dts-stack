package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.ApiCatalogService;
import com.yuzhi.dts.platform.service.services.dto.ApiMetricsDto;
import com.yuzhi.dts.platform.service.services.dto.ApiServiceDetailDto;
import com.yuzhi.dts.platform.service.services.dto.ApiServiceSummaryDto;
import com.yuzhi.dts.platform.service.services.dto.ApiServiceUpsertRequest;
import com.yuzhi.dts.platform.service.services.dto.ApiTryInvokeRequestDto;
import com.yuzhi.dts.platform.service.services.dto.ApiTryInvokeResponseDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/apis")
@Transactional
public class ApiServicesResource {

    private static final String SERVICES_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).SERVICE_MAINTAINERS)";

    private final ApiCatalogService apiCatalogService;
    private final AuditService auditService;

    public ApiServicesResource(ApiCatalogService apiCatalogService, AuditService auditService) {
        this.apiCatalogService = apiCatalogService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<ApiServiceSummaryDto>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String method,
        @RequestParam(required = false) String status
    ) {
        List<ApiServiceSummaryDto> result = apiCatalogService.list(keyword, method, status);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看API服务列表");
        payload.put("count", result != null ? result.size() : 0);
        if (keyword != null && !keyword.isBlank()) payload.put("keyword", keyword.trim());
        if (method != null && !method.isBlank()) payload.put("method", method.trim());
        if (status != null && !status.isBlank()) payload.put("status", status.trim());
        auditService.auditAction("SERVICE_API_LIST", AuditStage.SUCCESS, "list", payload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiServiceDetailDto> detail(@PathVariable UUID id) {
        ApiServiceDetailDto detail = apiCatalogService.detail(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看API服务详情");
        payload.put("targetId", id.toString());
        if (detail != null) {
            payload.put("targetName", detail.name());
            payload.put("code", detail.code());
            payload.put("status", detail.status());
        }
        auditService.auditAction("SERVICE_API_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(detail);
    }

    @PostMapping("/{id}/try")
    public ApiResponse<ApiTryInvokeResponseDto> tryInvoke(@PathVariable UUID id, @RequestBody(required = false) ApiTryInvokeRequestDto body) {
        ApiTryInvokeResponseDto response = apiCatalogService.tryInvoke(id, body);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "测试API服务");
        payload.put("targetId", id.toString());
        auditService.auditAction("SERVICE_API_TEST", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(response);
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<ApiMetricsDto> metrics(@PathVariable UUID id) {
        ApiMetricsDto metrics = apiCatalogService.metrics(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看API调用统计");
        payload.put("targetId", id.toString());
        auditService.auditAction("SERVICE_API_METRICS_VIEW", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(metrics);
    }

    @PostMapping
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<ApiServiceDetailDto> create(@RequestBody ApiServiceUpsertRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        ApiServiceDetailDto detail = apiCatalogService.create(request, user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "登记API服务");
        if (detail != null) {
            payload.put("targetId", detail.id() != null ? detail.id().toString() : null);
            payload.put("targetName", detail.name());
            payload.put("code", detail.code());
        }
        auditService.auditAction("SERVICE_API_REGISTER", AuditStage.SUCCESS, detail != null && detail.id() != null ? detail.id().toString() : "create", payload);
        return ApiResponses.ok(detail);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<ApiServiceDetailDto> update(@PathVariable UUID id, @RequestBody ApiServiceUpsertRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        ApiServiceDetailDto detail = apiCatalogService.update(id, request, user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "维护API服务");
        payload.put("targetId", id.toString());
        if (detail != null) {
            payload.put("targetName", detail.name());
            payload.put("code", detail.code());
        }
        auditService.auditAction("SERVICE_API_EDIT", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(detail);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<ApiServiceDetailDto> disable(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        ApiServiceDetailDto detail = apiCatalogService.disable(id, user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "下线API服务");
        payload.put("targetId", id.toString());
        if (detail != null) {
            payload.put("targetName", detail.name());
            payload.put("code", detail.code());
        }
        auditService.auditAction("SERVICE_API_DISABLE", AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(detail);
    }
}
