package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/infra")
public class AdminInfraResource {

    private static final String SYS_ADMIN_ONLY = "hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')";

    private final InfraAdminService infraAdminService;

    public AdminInfraResource(InfraAdminService infraAdminService) {
        this.infraAdminService = infraAdminService;
    }

    @GetMapping("/inceptor")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<PlatformInceptorConfigResponse>> currentInceptor() {
        PlatformInceptorConfigResponse payload = infraAdminService.currentPlatformInceptorConfig().orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping("/inceptor/flags")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> inceptorFlags() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.computeFeatureFlags()));
    }

    @PostMapping("/inceptor/test")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testInceptor(
        @RequestBody HiveConnectionTestRequest request,
        @RequestParam(required = false) UUID dataSourceId
    ) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请填写 JDBC 连接信息"));
        }
        HiveConnectionTestResult result = infraAdminService.testDataSourceConnection(request, dataSourceId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/inceptor/publish")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraAdminService.DataSourceMutation>> publishInceptor(
        @RequestBody HiveConnectionPersistRequest request
    ) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请填写 JDBC 连接信息"));
        }
        String operator = SecurityUtils.getCurrentAuditableLogin();
        InfraAdminService.DataSourceMutation mutation = infraAdminService.publishInceptor(request, operator);
        return ResponseEntity.ok(ApiResponse.ok(mutation));
    }

    @PostMapping("/inceptor/refresh")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> refreshInceptor() {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        InfraFeatureFlags flags = infraAdminService.refreshInceptorRegistry(operator);
        return ResponseEntity.ok(ApiResponse.ok(flags));
    }

    @GetMapping("/inceptor/test-logs")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<ConnectionTestLogDto>>> testLogs(@RequestParam(required = false) UUID dataSourceId) {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.recentTestLogs(dataSourceId)));
    }
}
