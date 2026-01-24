package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.admin.service.infra.dto.AirbyteDestinationDefinitionDto;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import com.yuzhi.dts.admin.service.infra.dto.JdbcConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.JdbcDriverInfo;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/data-lakes/test-logs")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<ConnectionTestLogDto>>> dataLakeTestLogs(
        @RequestParam(required = false) UUID dataSourceId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.recentTestLogs(dataSourceId)));
    }

    @GetMapping("/data-lakes/jdbc-drivers")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<JdbcDriverInfo>>> dataLakeJdbcDrivers() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listJdbcDrivers()));
    }

    @GetMapping("/data-lakes/destination-definitions")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<AirbyteDestinationDefinitionDto>>> dataLakeDestinationDefinitions() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listDestinationDefinitions()));
    }

    @GetMapping("/inceptor/drivers")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<JdbcDriverInfo>>> inceptorDrivers() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listJdbcDrivers()));
    }

    @GetMapping("/data-lakes")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<InfraDataSourceDto>>> listDataLakes() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listDataSources()));
    }

    @PostMapping("/data-lakes")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> createDataLake(@Valid @RequestBody UpsertInfraDataSourcePayload payload) {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        InfraDataSourceDto created = infraAdminService.createDataSource(payload, operator);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PutMapping("/data-lakes/{id}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraAdminService.DataSourceMutation>> updateDataLake(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertInfraDataSourcePayload payload
    ) {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        return infraAdminService
            .updateDataSource(id, payload, operator)
            .map(mutation -> ResponseEntity.ok(ApiResponse.ok(mutation)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/data-lakes/{id}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> deleteDataLake(@PathVariable UUID id) {
        return infraAdminService
            .deleteDataSource(id)
            .map(snapshot -> ResponseEntity.ok(ApiResponse.ok(snapshot)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/data-lakes/{id}/default")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> setDefaultDataLake(@PathVariable UUID id) {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        return infraAdminService
            .setDefaultDataSource(id, operator)
            .map(updated -> ResponseEntity.ok(ApiResponse.ok(updated)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/data-lakes/{id}/test")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testDataLake(
        @PathVariable UUID id,
        @RequestBody(required = false) JdbcConnectionTestRequest request
    ) {
        JdbcConnectionTestRequest payload = request != null ? request : new JdbcConnectionTestRequest();
        return infraAdminService
            .testJdbcDataSourceConnection(id, payload)
            .map(result -> ResponseEntity.ok(ApiResponse.ok(result)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
