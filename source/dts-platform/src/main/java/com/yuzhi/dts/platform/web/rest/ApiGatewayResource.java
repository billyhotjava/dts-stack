package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.ApiCatalogService;
import com.yuzhi.dts.platform.service.services.dto.ApiTryInvokeRequestDto;
import com.yuzhi.dts.platform.service.services.dto.ApiTryInvokeResponseDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.yuzhi.dts.common.audit.AuditStage;

/**
 * Data Service public APIs mapping to tasks: test/publish/execute.
 */
@RestController
@RequestMapping("/api/apis")
@Transactional
public class ApiGatewayResource {

    private final AuditService audit;
    private final ApiCatalogService apiCatalogService;

    public ApiGatewayResource(AuditService audit, ApiCatalogService apiCatalogService) {
        this.audit = audit;
        this.apiCatalogService = apiCatalogService;
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ApiTryInvokeResponseDto> test(@PathVariable UUID id, @RequestBody(required = false) ApiTryInvokeRequestDto input) {
        ApiTryInvokeResponseDto resp = apiCatalogService.tryInvoke(id, input);
        audit.auditAction("SERVICE_API_TEST", AuditStage.SUCCESS, id.toString(), Map.of("summary", "测试API服务", "targetId", id.toString()));
        return ApiResponses.ok(resp);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> input) {
        String version = null;
        if (input != null && input.get("version") != null) {
            version = String.valueOf(input.get("version"));
        }
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        var detail = apiCatalogService.publish(id, version, user);
        audit.auditAction("SERVICE_API_PUBLISH", AuditStage.SUCCESS, id.toString(), Map.of("summary", "发布API服务", "targetId", id.toString(), "version", detail.latestVersion()));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("version", detail.latestVersion());
        resp.put("status", detail.status());
        resp.put("publishedAt", detail.lastPublishedAt());
        return ApiResponses.ok(resp);
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<ApiTryInvokeResponseDto> execute(@PathVariable UUID id, @RequestBody(required = false) ApiTryInvokeRequestDto input) {
        ApiTryInvokeResponseDto resp = apiCatalogService.execute(id, input);
        audit.auditAction("SERVICE_API_EXECUTE", AuditStage.SUCCESS, id.toString(), Map.of("summary", "调用API服务", "targetId", id.toString()));
        return ApiResponses.ok(resp);
    }
}
