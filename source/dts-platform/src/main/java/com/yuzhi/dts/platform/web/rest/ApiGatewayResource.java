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
        audit.audit("EXECUTE", "api.test", id.toString());
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
        audit.audit("PUBLISH", "api.publish", id.toString());
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
        audit.audit("EXECUTE", "api.execute", id.toString());
        return ApiResponses.ok(resp);
    }
}
