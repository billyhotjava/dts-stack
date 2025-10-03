package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.ApiCatalogService;
import com.yuzhi.dts.platform.service.services.dto.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/apis")
public class ApiServicesResource {

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
        auditService.audit("READ", "svc.api", "list");
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiServiceDetailDto> detail(@PathVariable UUID id) {
        ApiServiceDetailDto detail = apiCatalogService.detail(id);
        auditService.audit("READ", "svc.api", id.toString());
        return ApiResponses.ok(detail);
    }

    @PostMapping("/{id}/try")
    public ApiResponse<ApiTryInvokeResponseDto> tryInvoke(@PathVariable UUID id, @RequestBody(required = false) ApiTryInvokeRequestDto body) {
        ApiTryInvokeResponseDto response = apiCatalogService.tryInvoke(id, body);
        auditService.audit("EXECUTE", "svc.api.try", id.toString());
        return ApiResponses.ok(response);
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<ApiMetricsDto> metrics(@PathVariable UUID id) {
        ApiMetricsDto metrics = apiCatalogService.metrics(id);
        auditService.audit("READ", "svc.api.metrics", id.toString());
        return ApiResponses.ok(metrics);
    }
}
