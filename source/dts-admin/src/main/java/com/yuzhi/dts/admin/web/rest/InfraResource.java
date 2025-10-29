package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.security.session.AdminSessionRegistry;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
    private final AdminSessionRegistry adminSessionRegistry;

    public InfraResource(
        InfraAdminService infraAdminService,
        AuditV2Service auditV2Service,
        AdminSessionRegistry adminSessionRegistry
    ) {
        this.infraAdminService = infraAdminService;
        this.auditV2Service = auditV2Service;
        this.adminSessionRegistry = adminSessionRegistry;
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
        ActorResolution actor = resolveActor(servletRequest);
        InfraDataSourceDto saved = infraAdminService.createDataSource(payload, actor.resolved());
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
        @Valid @RequestBody UpsertInfraDataSourcePayload payload,
        HttpServletRequest servletRequest
    ) {
        ActorResolution actor = resolveActor(servletRequest);
        Map<String, Object> secretsSnapshot = new HashMap<>(payload.getSecrets());
        boolean hasKrb5Secret = hasSecretValue(secretsSnapshot, "krb5Conf");
        boolean hasKeytabSecret = hasSecretValue(secretsSnapshot, "keytabBase64");
        String keytabFileNameSecret = extractString(secretsSnapshot, "keytabFileName");
        InfraAdminService.DataSourceMutation mutation = infraAdminService
            .updateDataSource(id, payload, actor.resolved())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
        InfraDataSourceDto after = mutation.after();
        InfraDataSourceDto before = mutation.before();
        UUID targetId = after != null ? after.getId() : before != null ? before.getId() : id;
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KRB5,
            "上传 krb5.conf（更新配置）",
            "UPDATE",
            hasKrb5Secret,
            null,
            targetId,
            servletRequest
        );
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KEYTAB,
            "上传 Keytab 文件（更新配置）",
            "UPDATE",
            hasKeytabSecret,
            keytabFileNameSecret,
            targetId,
            servletRequest
        );
        String summaryName = safeName(after != null ? after : mutation.before());
        recordDataSourceMutationAudit(
            actor,
            before,
            after,
            "更新数据源：" + summaryName,
            ButtonCodes.DATA_SOURCE_UPDATE,
            servletRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(after));
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<Boolean>> deleteDataSource(@PathVariable UUID id, HttpServletRequest servletRequest) {
        ActorResolution actor = resolveActor(servletRequest);
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
    public ResponseEntity<ApiResponse<HiveConnectionTestResult>> testConnection(
        @Valid @RequestBody HiveConnectionTestRequest request,
        @RequestParam(value = "dataSourceId", required = false) UUID dataSourceId,
        HttpServletRequest servletRequest
    ) {
        ActorResolution actor = resolveActor(servletRequest);
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KRB5,
            "上传 krb5.conf（测试连接）",
            "TEST",
            StringUtils.hasText(request.getKrb5Conf()),
            null,
            dataSourceId,
            servletRequest
        );
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KEYTAB,
            "上传 Keytab 文件（测试连接）",
            "TEST",
            StringUtils.hasText(request.getKeytabBase64()),
            request.getKeytabFileName(),
            dataSourceId,
            servletRequest
        );
        HiveConnectionTestResult result = infraAdminService.testDataSourceConnection(request, dataSourceId);
        recordDataSourceTestAudit(actor, dataSourceId, request, result, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/data-sources/inceptor/publish")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ResponseEntity<ApiResponse<InfraDataSourceDto>> publishInceptor(
        @Valid @RequestBody HiveConnectionPersistRequest request,
        HttpServletRequest servletRequest
    ) {
        ActorResolution actor = resolveActor(servletRequest);
        InfraAdminService.DataSourceMutation mutation = infraAdminService.publishInceptor(request, actor.resolved());
        InfraDataSourceDto after = mutation.after();
        InfraDataSourceDto before = mutation.before();
        UUID targetId = after != null ? after.getId() : before != null ? before.getId() : null;
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KRB5,
            before == null ? "上传 krb5.conf（首次发布）" : "上传 krb5.conf（更新配置）",
            before == null ? "PUBLISH" : "UPDATE",
            StringUtils.hasText(request.getKrb5Conf()),
            null,
            targetId,
            servletRequest
        );
        recordDataSourceFileUploadAudit(
            actor,
            ButtonCodes.DATA_SOURCE_UPLOAD_KEYTAB,
            before == null ? "上传 Keytab 文件（首次发布）" : "上传 Keytab 文件（更新配置）",
            before == null ? "PUBLISH" : "UPDATE",
            StringUtils.hasText(request.getKeytabBase64()),
            request.getKeytabFileName(),
            targetId,
            servletRequest
        );
        String label = safeName(after != null ? after : before);
        String summaryPrefix = before == null ? "发布 Inceptor 数据源：" : "更新 Inceptor 数据源：";
        recordDataSourceMutationAudit(
            actor,
            before,
            after,
            summaryPrefix + label,
            ButtonCodes.DATA_SOURCE_PUBLISH,
            servletRequest
        );
        return ResponseEntity.ok(ApiResponse.ok(after));
    }

    @PostMapping("/data-sources/inceptor/refresh")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "'," +
            "'" + AuthoritiesConstants.INST_DATA_OWNER + "','" + AuthoritiesConstants.INST_DATA_DEV + "','" + AuthoritiesConstants.INST_DATA_VIEWER + "'," +
            "'" + AuthoritiesConstants.DEPT_DATA_OWNER + "','" + AuthoritiesConstants.DEPT_DATA_DEV + "','" + AuthoritiesConstants.DEPT_DATA_VIEWER + "')"
    )
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> refreshInceptor(HttpServletRequest request) {
        ActorResolution actor = resolveActor(request);
        InfraFeatureFlags flags = infraAdminService.refreshInceptorRegistry(actor.resolved());
        recordRefreshAudit(actor, flags, request);
        return ResponseEntity.ok(ApiResponse.ok(flags));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<InfraFeatureFlags>> features() {
        return ResponseEntity.ok(ApiResponse.ok(infraAdminService.computeFeatureFlags()));
    }

    private void recordRefreshAudit(ActorResolution actor, InfraFeatureFlags flags, HttpServletRequest request) {
        String normalizedActor = actor.resolved();
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, ButtonCodes.DATA_SOURCE_REFRESH)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("刷新 Inceptor 注册信息")
                .result(AuditResultStatus.SUCCESS)
                .allowEmptyTargets();
            builder.metadata("resourceType", "INFRA_DATA_SOURCE");

            if (actor.overridden()) {
                builder.metadata("actorOriginal", actor.original());
                builder.metadata("actorSource", actor.source());
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
        ActorResolution actor,
        InfraDataSourceDto before,
        InfraDataSourceDto after,
        String summary,
        String buttonCode,
        HttpServletRequest request
    ) {
        String normalizedActor = actor.resolved();
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, buttonCode)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .metadata("resourceType", "INFRA_DATA_SOURCE");

            if (actor.overridden()) {
                builder.metadata("actorOriginal", actor.original());
                builder.metadata("actorSource", actor.source());
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

    private void recordDataSourceTestAudit(
        ActorResolution actor,
        UUID dataSourceId,
        HiveConnectionTestRequest payload,
        HiveConnectionTestResult result,
        HttpServletRequest request
    ) {
        String normalizedActor = actor.resolved();
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, ButtonCodes.DATA_SOURCE_TEST)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("测试数据源连接")
                .result(result != null && Boolean.TRUE.equals(result.isSuccess()) ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED)
                .allowEmptyTargets();

            if (actor.overridden()) {
                builder.metadata("actorOriginal", actor.original());
                builder.metadata("actorSource", actor.source());
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

            Map<String, Object> requestDetail = new LinkedHashMap<>();
            if (payload != null) {
                putIfHasText(requestDetail, "jdbcUrl", payload.getJdbcUrl());
                putIfHasText(requestDetail, "loginPrincipal", payload.getLoginPrincipal());
                putIfHasText(requestDetail, "authMethod", payload.getAuthMethod() != null ? payload.getAuthMethod().name() : null);
                putIfHasText(requestDetail, "proxyUser", payload.getProxyUser());
                requestDetail.put("hasKrb5Conf", payload.getKrb5Conf() != null && !payload.getKrb5Conf().isBlank());
                requestDetail.put("hasKeytab", payload.getKeytabBase64() != null && !payload.getKeytabBase64().isBlank());
                putIfHasText(requestDetail, "keytabFileName", payload.getKeytabFileName());
                putIfHasText(requestDetail, "testQuery", payload.getTestQuery());
                if (payload.getJdbcProperties() != null && !payload.getJdbcProperties().isEmpty()) {
                    requestDetail.put("jdbcProperties", new LinkedHashMap<>(payload.getJdbcProperties()));
                }
            }

            Map<String, Object> resultDetail = new LinkedHashMap<>();
            if (result != null) {
                resultDetail.put("success", result.isSuccess());
                putIfHasText(resultDetail, "message", result.getMessage());
                resultDetail.put("elapsedMillis", result.getElapsedMillis());
                putIfHasText(resultDetail, "engineVersion", result.getEngineVersion());
                putIfHasText(resultDetail, "driverVersion", result.getDriverVersion());
                if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
                    resultDetail.put("warnings", List.copyOf(result.getWarnings()));
                }
            }

            builder.detail("request", requestDetail);
            builder.detail("result", resultDetail);

            builder.metadata("success", result != null && Boolean.TRUE.equals(result.isSuccess()));
            if (result != null) {
                builder.metadata("elapsedMillis", result.getElapsedMillis());
                if (result.getMessage() != null) {
                    builder.metadata("resultMessage", result.getMessage());
                }
            }

            if (dataSourceId != null) {
                String targetId = dataSourceId.toString();
                builder.target("infra_data_source", targetId, targetId);
                builder.metadata("dataSourceId", targetId);
            }

            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit for data source test: {}", ex.getMessage());
        }
    }

    private void recordDataSourceFileUploadAudit(
        ActorResolution actor,
        String buttonCode,
        String summary,
        String stage,
        boolean present,
        String fileName,
        UUID targetId,
        HttpServletRequest request
    ) {
        if (!present) {
            return;
        }
        String normalizedActor = actor.resolved();
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(normalizedActor, buttonCode)
                .actorName(normalizedActor)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .allowEmptyTargets()
                .metadata("resourceType", "INFRA_DATA_SOURCE")
                .metadata("stage", stage);

            if (actor.overridden()) {
                builder.metadata("actorOriginal", actor.original());
                builder.metadata("actorSource", actor.source());
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

            if (StringUtils.hasText(fileName)) {
                builder.metadata("fileName", fileName);
            }

            if (targetId != null) {
                String target = targetId.toString();
                builder.target("infra_data_source", target, target);
                builder.metadata("dataSourceId", target);
            }

            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record audit for data source file upload [{}]: {}", buttonCode, ex.getMessage());
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


    private ActorResolution resolveActor(HttpServletRequest request) {
        String raw = SecurityUtils.getCurrentUserLogin().orElse(null);
        String sanitized = SecurityUtils.sanitizeLogin(raw);
        String resolved = sanitized;
        String source = "direct";

        if (needsOverride(resolved) && request != null) {
            String headerActor = request.getHeader("X-Admin-Actor");
            if (StringUtils.hasText(headerActor)) {
                String candidate = SecurityUtils.sanitizeLogin(headerActor);
                if (!needsOverride(candidate)) {
                    resolved = candidate;
                    source = "header";
                }
            }
            if (needsOverride(resolved)) {
                String bearer = extractBearer(request.getHeader("Authorization"));
                if (StringUtils.hasText(bearer)) {
                    Optional<String> sessionActor = adminSessionRegistry.resolveUsernameFromAccessToken(bearer);
                    if (sessionActor.isPresent()) {
                        String candidate = SecurityUtils.sanitizeLogin(sessionActor.orElseThrow());
                        if (!needsOverride(candidate) || "system".equalsIgnoreCase(resolved)) {
                            resolved = candidate;
                            source = "session";
                        }
                    }
                }
            }
        }
        return new ActorResolution(resolved, sanitized, source);
    }

    private boolean needsOverride(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "system".equals(normalized) || normalized.startsWith("service:");
    }

    private String extractBearer(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return trimmed.substring("Bearer ".length()).trim();
        }
        return null;
    }

    private record ActorResolution(String resolved, String original, String source) {
        boolean overridden() {
            return original != null && !Objects.equals(resolved, original);
        }
    }

    private boolean hasSecretValue(Map<String, Object> secrets, String key) {
        if (secrets == null) {
            return false;
        }
        Object value = secrets.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return StringUtils.hasText(str);
        }
        if (value instanceof byte[] bytes) {
            return bytes.length > 0;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private String extractString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (value != null) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                target.put(key, trimmed);
            }
        }
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
