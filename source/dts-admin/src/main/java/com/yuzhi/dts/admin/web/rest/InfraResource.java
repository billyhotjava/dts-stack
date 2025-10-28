package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.infra.InfraAdminService;
import com.yuzhi.dts.admin.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.HiveConnectionTestResult;
import com.yuzhi.dts.admin.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.admin.service.infra.dto.InfraFeatureFlags;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra")
public class InfraResource {

    private static final Logger log = LoggerFactory.getLogger(InfraResource.class);

    private final InfraAdminService infraAdminService;
    private final AuditV2Service auditV2Service;

    public InfraResource(InfraAdminService infraAdminService, AuditV2Service auditV2Service) {
        this.infraAdminService = infraAdminService;
        this.auditV2Service = auditV2Service;
    }

    @GetMapping("/data-sources")
    public ResponseEntity<ApiResponse<List<InfraDataSourceDto>>> listDataSources() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listDataSources()));
    }

    @PostMapping("/data-sources")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> createDataSource(
        @Valid @RequestBody UpsertInfraDataSourcePayload payload,
        HttpServletRequest servletRequest
    ) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        InfraDataSourceDto saved = infraAdminService.createDataSource(payload, actor);
        recordDataSourceMutationAudit(
            actor,
            null,
            saved,
            "新增数据源：" + safeName(saved),
            ButtonCodes.DATA_SOURCE_CREATE,
            servletRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PutMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> updateDataSource(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertInfraDataSourcePayload payload
    ) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        InfraDataSourceDto updated = infraAdminService
            .updateDataSource(id, payload, actor)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<Boolean>> deleteDataSource(@PathVariable UUID id, HttpServletRequest servletRequest) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        InfraDataSourceDto removed = infraAdminService
            .deleteDataSource(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        recordDataSourceMutationAudit(
            actor,
            removed,
            null,
            "删除数据源：" + safeName(removed),
            ButtonCodes.DATA_SOURCE_DELETE,
            servletRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE));
    }

    @GetMapping("/data-sources/test-logs")
    public ResponseEntity<ApiResponse<List<ConnectionTestLogDto>>> recentTestLogs(@RequestParam(required = false) UUID dataSourceId) {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.recentTestLogs(dataSourceId)));
    }

    @PostMapping("/data-sources/test-connection")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testConnection(@Valid @RequestBody HiveConnectionTestRequest request) {
        HiveConnectionTestResult result = infraAdminService.testDataSourceConnection(request, null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/data-sources/inceptor/publish")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> publishInceptor(@Valid @RequestBody HiveConnectionPersistRequest request) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        InfraDataSourceDto saved = infraAdminService.publishInceptor(request, actor);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PostMapping("/data-sources/inceptor/refresh")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "'," +
            "'" + AuthoritiesConstants.INST_DATA_OWNER + "','" + AuthoritiesConstants.INST_DATA_DEV + "','" + AuthoritiesConstants.INST_DATA_VIEWER + "'," +
            "'" + AuthoritiesConstants.DEPT_DATA_OWNER + "','" + AuthoritiesConstants.DEPT_DATA_DEV + "','" + AuthoritiesConstants.DEPT_DATA_VIEWER + "')"
    )
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> refreshInceptor(HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        InfraFeatureFlags flags = infraAdminService.refreshInceptorRegistry(actor);
        recordRefreshAudit(actor, flags, request);
        return ResponseEntity.ok(ApiResponse.ok(flags));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> features() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.computeFeatureFlags()));
    }

    private void recordRefreshAudit(String actor, InfraFeatureFlags flags, HttpServletRequest request) {
        String normalizedActor = SecurityUtils.sanitizeLogin(actor);
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, ButtonCodes.DATA_SOURCE_REFRESH)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("刷新 Inceptor 注册信息")
                .result(AuditResultStatus.SUCCESS)
                .allowEmptyTargets();
            builder.metadata("resourceType", "INFRA_DATA_SOURCE");

            if (request != null) {
                String clientIp = IpAddressUtils.resolveClientIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                builder.client(clientIp, request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }

            if (flags != null) {
                if (flags.getInceptorStatus() != null) {
                    builder.metadata("inceptorStatus", flags.getInceptorStatus());
                }
                builder.metadata("hasActiveInceptor", flags.isHasActiveInceptor());
                if (flags.getSyncInProgress() != null) {
                    builder.metadata("syncInProgress", flags.getSyncInProgress());
                }
                if (flags.getIntegrationStatus() != null) {
                    builder.detail("integrationStatus", flags.getIntegrationStatus());
                }
                if (flags.getLastUpdatedAt() != null) {
                    builder.metadata("lastUpdatedAt", flags.getLastUpdatedAt());
                }
            }

            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit for Inceptor refresh: {}", ex.getMessage());
        }
    }

    private void recordDataSourceMutationAudit(
        String actor,
        InfraDataSourceDto before,
        InfraDataSourceDto after,
        String summary,
        String buttonCode,
        HttpServletRequest request
    ) {
        String normalizedActor = SecurityUtils.sanitizeLogin(actor);
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, buttonCode)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .metadata("resourceType", "INFRA_DATA_SOURCE");

            if (request != null) {
                String clientIp = IpAddressUtils.resolveClientIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                builder.client(clientIp, request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }

            Map<String, Object> beforeMap = dataSourceSnapshot(before);
            Map<String, Object> afterMap = dataSourceSnapshot(after);
            if (!beforeMap.isEmpty() || !afterMap.isEmpty()) {
                builder.changeSnapshot(beforeMap, afterMap, "INFRA_DATA_SOURCE");
            }

            InfraDataSourceDto target = after != null ? after : before;
            if (target != null && target.getId() != null) {
                String targetId = target.getId().toString();
                builder.target("infra_data_source", targetId, safeName(target));
                builder.metadata("dataSourceId", targetId);
            }

            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit for data source mutation: {}", ex.getMessage());
        }
    }

    private Map<String, Object> dataSourceSnapshot(InfraDataSourceDto dto) {
        if (dto == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfNotNull(snapshot, "id", dto.getId() != null ? dto.getId().toString() : null);
        putIfNotNull(snapshot, "name", dto.getName());
        putIfNotNull(snapshot, "type", dto.getType());
        putIfNotNull(snapshot, "jdbcUrl", dto.getJdbcUrl());
        putIfNotNull(snapshot, "username", dto.getUsername());
        putIfNotNull(snapshot, "description", dto.getDescription());
        if (dto.getProps() != null && !dto.getProps().isEmpty()) {
            snapshot.put("props", dto.getProps());
        }
        putIfNotNull(snapshot, "status", dto.getStatus());
        snapshot.put("hasSecrets", dto.isHasSecrets());
        putIfNotNull(snapshot, "engineVersion", dto.getEngineVersion());
        putIfNotNull(snapshot, "driverVersion", dto.getDriverVersion());
        putIfNotNull(snapshot, "lastTestElapsedMillis", dto.getLastTestElapsedMillis());
        putIfNotNull(snapshot, "lastVerifiedAt", dto.getLastVerifiedAt());
        putIfNotNull(snapshot, "lastHeartbeatAt", dto.getLastHeartbeatAt());
        putIfNotNull(snapshot, "heartbeatStatus", dto.getHeartbeatStatus());
        putIfNotNull(snapshot, "heartbeatFailureCount", dto.getHeartbeatFailureCount());
        putIfNotNull(snapshot, "lastError", dto.getLastError());
        return snapshot;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String safeName(InfraDataSourceDto dto) {
        if (dto == null) {
            return "";
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            return dto.getName();
        }
        if (dto.getId() != null) {
            return dto.getId().toString();
        }
        return "";
    }
}
