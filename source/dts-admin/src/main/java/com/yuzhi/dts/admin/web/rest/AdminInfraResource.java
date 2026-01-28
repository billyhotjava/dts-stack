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
import com.yuzhi.dts.admin.service.infra.dto.JdbcConnectionTestRequest;
import com.yuzhi.dts.admin.service.infra.dto.JdbcDriverInfo;
import com.yuzhi.dts.admin.service.infra.dto.PlatformInceptorConfigResponse;
import com.yuzhi.dts.admin.service.infra.dto.UpsertInfraDataSourcePayload;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String MASKED_SECRET = "******";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password",
        "token",
        "authToken",
        "secret",
        "keytab",
        "keytabBase64",
        "krb5Conf"
    );

    private final InfraAdminService infraAdminService;
    private final AuditV2Service auditV2Service;

    public AdminInfraResource(InfraAdminService infraAdminService, AuditV2Service auditV2Service) {
        this.infraAdminService = infraAdminService;
        this.auditV2Service = auditV2Service;
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
        @RequestParam(required = false) UUID dataSourceId,
        HttpServletRequest httpRequest
    ) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请填写 JDBC 连接信息"));
        }
        HiveConnectionTestResult result = infraAdminService.testDataSourceConnection(request, dataSourceId);
        recordDataSourceAudit(
            ButtonCodes.DATA_SOURCE_TEST,
            "测试数据源连接",
            metaIfNotNull("dataSourceId", dataSourceId),
            null,
            null,
            dataSourceId != null ? dataSourceId.toString() : null,
            null,
            result != null && result.isSuccess() ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED,
            httpRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/inceptor/publish")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraAdminService.DataSourceMutation>> publishInceptor(
        @RequestBody HiveConnectionPersistRequest request,
        HttpServletRequest httpRequest
    ) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请填写 JDBC 连接信息"));
        }
        String operator = SecurityUtils.getCurrentAuditableLogin();
        try {
            InfraAdminService.DataSourceMutation mutation = infraAdminService.publishInceptor(request, operator);
            InfraDataSourceDto after = mutation != null ? mutation.after() : null;
            recordDataSourceAudit(
                ButtonCodes.DATA_SOURCE_PUBLISH,
                "发布 Inceptor 数据源",
                null,
                mutation != null ? mutation.before() : null,
                after,
                after != null && after.getId() != null ? after.getId().toString() : null,
                resolveLabel(mutation != null ? mutation.before() : null, after, request.getName()),
                AuditResultStatus.SUCCESS,
                httpRequest
            );
            return ResponseEntity.ok(ApiResponse.ok(mutation));
        } catch (RuntimeException ex) {
            recordDataSourceAudit(
                ButtonCodes.DATA_SOURCE_PUBLISH,
                "发布 Inceptor 数据源失败",
                metaIfNotNull("error", trimMessage(ex.getMessage())),
                null,
                null,
                null,
                request.getName(),
                AuditResultStatus.FAILED,
                httpRequest
            );
            throw ex;
        }
    }

    @PostMapping("/inceptor/refresh")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> refreshInceptor(HttpServletRequest httpRequest) {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        InfraFeatureFlags flags = infraAdminService.refreshInceptorRegistry(operator);
        recordDataSourceAudit(
            ButtonCodes.DATA_SOURCE_REFRESH,
            "刷新 Inceptor 注册信息",
            metaIfNotNull("operator", operator),
            null,
            null,
            null,
            null,
            AuditResultStatus.SUCCESS,
            httpRequest
        );
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

    @GetMapping("/settings/{service}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<Map<String, Object>>> integrationSettings(
        @PathVariable String service,
        HttpServletRequest httpRequest
    ) {
        Map<String, Object> settings = infraAdminService.getIntegrationSettings(service);
        recordSettingsAudit(
            ButtonCodes.INTEGRATION_SETTINGS_VIEW,
            "查看集成配置",
            service,
            null,
            settings,
            AuditResultStatus.SUCCESS,
            httpRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PostMapping("/settings/{service}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateIntegrationSettings(
        @PathVariable String service,
        @RequestBody(required = false) Map<String, Object> payload,
        HttpServletRequest httpRequest
    ) {
        Map<String, Object> before = infraAdminService.getIntegrationSettings(service);
        try {
            Map<String, Object> result = infraAdminService.updateIntegrationSettings(service, payload == null ? Map.of() : payload);
            recordSettingsAudit(
                ButtonCodes.INTEGRATION_SETTINGS_UPDATE,
                "更新集成配置",
                service,
                before,
                result,
                AuditResultStatus.SUCCESS,
                httpRequest
            );
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (RuntimeException ex) {
            recordSettingsAudit(
                ButtonCodes.INTEGRATION_SETTINGS_UPDATE,
                "更新集成配置失败",
                service,
                before,
                null,
                AuditResultStatus.FAILED,
                httpRequest
            );
            throw ex;
        }
    }

    @PostMapping("/settings/{service}/test")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<Map<String, Object>>> testIntegrationSettings(
        @PathVariable String service,
        @RequestBody(required = false) Map<String, Object> payload,
        HttpServletRequest httpRequest
    ) {
        Map<String, Object> result = infraAdminService.testIntegrationSettings(service, payload == null ? Map.of() : payload);
        AuditResultStatus status = Boolean.TRUE.equals(result.get("success")) ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED;
        recordSettingsAudit(
            ButtonCodes.INTEGRATION_SETTINGS_TEST,
            "测试集成配置",
            service,
            payload,
            result,
            status,
            httpRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/inceptor/drivers")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<JdbcDriverInfo>>> inceptorDrivers() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.listJdbcDrivers()));
    }

    @GetMapping("/data-lakes")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<List<InfraDataSourceDto>>> listDataLakes(HttpServletRequest httpRequest) {
        List<InfraDataSourceDto> list = infraAdminService.listDataSources();
        recordDataSourceAudit(
            ButtonCodes.DATA_SOURCE_LIST,
            "查看数据源列表",
            metaIfNotNull("count", list.size()),
            null,
            null,
            null,
            null,
            AuditResultStatus.SUCCESS,
            httpRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/data-lakes")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> createDataLake(
        @Valid @RequestBody UpsertInfraDataSourcePayload payload,
        HttpServletRequest httpRequest
    ) {
        String validationError = validateDataLakePayload(payload, null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(validationError));
        }
        String operator = SecurityUtils.getCurrentAuditableLogin();
        try {
            InfraDataSourceDto created = infraAdminService.createDataSource(payload, operator);
            recordDataSourceAudit(
                ButtonCodes.DATA_SOURCE_CREATE,
                "新增数据湖",
                metaIfNotNull("type", normalize(payload.getType())),
                null,
                created,
                created != null && created.getId() != null ? created.getId().toString() : null,
                created != null ? created.getName() : payload.getName(),
                AuditResultStatus.SUCCESS,
                httpRequest
            );
            return ResponseEntity.ok(ApiResponse.ok(created));
        } catch (RuntimeException ex) {
            recordDataSourceAudit(
                ButtonCodes.DATA_SOURCE_CREATE,
                "新增数据湖失败",
                metaIfNotNull("error", trimMessage(ex.getMessage())),
                null,
                null,
                payload.getName(),
                payload.getName(),
                AuditResultStatus.FAILED,
                httpRequest
            );
            throw ex;
        }
    }

    @PutMapping("/data-lakes/{id}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraAdminService.DataSourceMutation>> updateDataLake(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertInfraDataSourcePayload payload,
        HttpServletRequest httpRequest
    ) {
        InfraDataSourceDto existing = infraAdminService.getDataSource(id).orElse(null);
        String validationError = validateDataLakePayload(payload, existing);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(validationError));
        }
        String operator = SecurityUtils.getCurrentAuditableLogin();
        try {
            return infraAdminService
                .updateDataSource(id, payload, operator)
                .map(mutation -> {
                    InfraDataSourceDto before = mutation.before();
                    InfraDataSourceDto after = mutation.after();
                    recordDataSourceAudit(
                        ButtonCodes.DATA_SOURCE_UPDATE,
                        "更新数据湖",
                        metaIfNotNull("type", normalize(payload.getType())),
                        before,
                        after,
                        id.toString(),
                        resolveLabel(before, after, payload.getName()),
                        AuditResultStatus.SUCCESS,
                        httpRequest
                    );
                    return ResponseEntity.ok(ApiResponse.ok(mutation));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (RuntimeException ex) {
            recordDataSourceAudit(
                ButtonCodes.DATA_SOURCE_UPDATE,
                "更新数据湖失败",
                metaIfNotNull("error", trimMessage(ex.getMessage())),
                null,
                null,
                id.toString(),
                payload.getName(),
                AuditResultStatus.FAILED,
                httpRequest
            );
            throw ex;
        }
    }

    @DeleteMapping("/data-lakes/{id}")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> deleteDataLake(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return infraAdminService
            .deleteDataSource(id)
            .map(snapshot -> {
                recordDataSourceAudit(
                    ButtonCodes.DATA_SOURCE_DELETE,
                    "删除数据湖",
                    null,
                    snapshot,
                    null,
                    id.toString(),
                    snapshot != null ? snapshot.getName() : id.toString(),
                    AuditResultStatus.SUCCESS,
                    httpRequest
                );
                return ResponseEntity.ok(ApiResponse.ok(snapshot));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/data-lakes/{id}/default")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> setDefaultDataLake(@PathVariable UUID id, HttpServletRequest httpRequest) {
        String operator = SecurityUtils.getCurrentAuditableLogin();
        InfraDataSourceDto before = infraAdminService
            .listDataSources()
            .stream()
            .filter(ds -> id.equals(ds.getId()))
            .findFirst()
            .orElse(null);
        return infraAdminService
            .setDefaultDataSource(id, operator)
            .map(updated -> {
                recordDataSourceAudit(
                    ButtonCodes.DATA_SOURCE_UPDATE,
                    "设置默认数据湖",
                    null,
                    before,
                    updated,
                    id.toString(),
                    resolveLabel(before, updated, null),
                    AuditResultStatus.SUCCESS,
                    httpRequest
                );
                return ResponseEntity.ok(ApiResponse.ok(updated));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/data-lakes/{id}/test")
    @PreAuthorize(SYS_ADMIN_ONLY)
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testDataLake(
        @PathVariable UUID id,
        @RequestBody(required = false) JdbcConnectionTestRequest request,
        HttpServletRequest httpRequest
    ) {
        JdbcConnectionTestRequest payload = request != null ? request : new JdbcConnectionTestRequest();
        return infraAdminService
            .testJdbcDataSourceConnection(id, payload)
            .map(result -> {
                recordDataSourceAudit(
                    ButtonCodes.DATA_SOURCE_TEST,
                    "测试数据源连接",
                    metaIfNotNull("dataSourceId", id),
                    null,
                    null,
                    id.toString(),
                    id.toString(),
                    result != null && result.isSuccess() ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED,
                    httpRequest
                );
                return ResponseEntity.ok(ApiResponse.ok(result));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void recordDataSourceAudit(
        String buttonCode,
        String summary,
        Map<String, Object> meta,
        InfraDataSourceDto before,
        InfraDataSourceDto after,
        String targetId,
        String targetLabel,
        AuditResultStatus result,
        HttpServletRequest request
    ) {
        try {
            String actor = SecurityUtils.getCurrentAuditableLogin();
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(actor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(result)
                .metadata("resourceType", "INFRA_DATA_SOURCE");

            if (meta != null) {
                meta.forEach(builder::metadata);
            }

            if (request != null) {
                String clientIp = IpAddressUtils.resolveClientIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                builder.client(clientIp, request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }

            Map<String, Object> beforeMap = snapshotDataSource(before);
            Map<String, Object> afterMap = snapshotDataSource(after);
            if (!beforeMap.isEmpty() || !afterMap.isEmpty()) {
                builder.changeSnapshot(beforeMap, afterMap, "INFRA_DATA_SOURCE");
            }

            if (StringUtils.hasText(targetId)) {
                builder.target("infra_data_source", targetId, targetLabel);
            } else {
                builder.allowEmptyTargets();
            }

            auditV2Service.record(builder.build());
        } catch (Exception ignored) {
            // audit failure must not break business calls
        }
    }

    private void recordSettingsAudit(
        String buttonCode,
        String summary,
        String service,
        Map<String, Object> before,
        Map<String, Object> after,
        AuditResultStatus result,
        HttpServletRequest request
    ) {
        try {
            String actor = SecurityUtils.getCurrentAuditableLogin();
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorName(actor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(result)
                .metadata("resourceType", "INFRA_SETTINGS");

            String normalizedService = normalize(service);
            if (StringUtils.hasText(normalizedService)) {
                builder.metadata("service", normalizedService);
                builder.target("infra_settings", normalizedService, normalizedService);
            } else {
                builder.allowEmptyTargets();
            }

            if (request != null) {
                String clientIp = IpAddressUtils.resolveClientIp(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                builder.client(clientIp, request.getHeader("User-Agent"));
                builder.request(request.getRequestURI(), request.getMethod());
            }

            Map<String, Object> beforeMap = maskSensitiveMap(before);
            Map<String, Object> afterMap = maskSensitiveMap(after);
            if (!beforeMap.isEmpty() || !afterMap.isEmpty()) {
                builder.changeSnapshot(beforeMap, afterMap, "INFRA_SETTINGS");
            }

            auditV2Service.record(builder.build());
        } catch (Exception ignored) {
            // audit failure must not break business calls
        }
    }

    private Map<String, Object> snapshotDataSource(InfraDataSourceDto dto) {
        if (dto == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (dto.getId() != null) {
            map.put("id", dto.getId().toString());
        }
        putIfText(map, "name", dto.getName());
        putIfText(map, "type", dto.getType());
        putIfText(map, "jdbcUrl", dto.getJdbcUrl());
        putIfText(map, "username", dto.getUsername());
        putIfText(map, "description", dto.getDescription());
        map.put("defaulted", dto.isDefaulted());
        putIfText(map, "status", dto.getStatus());
        putIfText(map, "engineVersion", dto.getEngineVersion());
        putIfText(map, "driverVersion", dto.getDriverVersion());
        if (dto.getProps() != null && !dto.getProps().isEmpty()) {
            map.put("props", maskSensitiveMap(dto.getProps()));
        }
        return map;
    }

    private Map<String, Object> maskSensitiveMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String normalizedKey = key.trim().toLowerCase();
            if (isSensitiveKey(normalizedKey)) {
                masked.put(key, MASKED_SECRET);
            } else if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> nested = new LinkedHashMap<>();
                mapValue.forEach((nestedKey, nestedValue) -> {
                    if (nestedKey != null) {
                        nested.put(String.valueOf(nestedKey), nestedValue);
                    }
                });
                masked.put(key, maskSensitiveMap(nested));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.trim().toLowerCase();
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token");
    }

    private String resolveLabel(InfraDataSourceDto before, InfraDataSourceDto after, String fallback) {
        String candidate = after != null ? after.getName() : null;
        if (!StringUtils.hasText(candidate)) {
            candidate = before != null ? before.getName() : null;
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = fallback;
        }
        return candidate;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return StringUtils.hasText(str) ? str.trim() : null;
    }

    private String trimMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private Map<String, Object> metaIfNotNull(String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return Map.of();
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(key, value);
        return meta;
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (target == null) {
            return;
        }
        String trimmed = normalize(value);
        if (StringUtils.hasText(trimmed)) {
            target.put(key, trimmed);
        }
    }

    private String validateDataLakePayload(UpsertInfraDataSourcePayload payload, InfraDataSourceDto existing) {
        if (payload == null) {
            return "请求不能为空";
        }
        if (!StringUtils.hasText(payload.getUsername())) {
            return "请填写用户名";
        }
        Object password = payload.getSecrets() == null ? null : payload.getSecrets().get("password");
        if (!StringUtils.hasText(password == null ? null : password.toString())) {
            return "请填写密码";
        }
        String destinationDefinitionId = payload.getProps() == null ? null : asString(payload.getProps().get("destinationDefinitionId"));
        if (!StringUtils.hasText(destinationDefinitionId)) {
            return "请选择写入器类型";
        }
        Object destConfigRaw = payload.getSecrets() == null ? null : payload.getSecrets().get("destinationConfig");
        boolean hasNewConfig = destConfigRaw instanceof Map<?, ?> map && !map.isEmpty();
        boolean hasExistingConfig = existing != null
            && existing.getDestinationConfig() != null
            && !existing.getDestinationConfig().isEmpty();
        if (!hasNewConfig && !hasExistingConfig) {
            return "请完善写入器配置";
        }
        return null;
    }
}
