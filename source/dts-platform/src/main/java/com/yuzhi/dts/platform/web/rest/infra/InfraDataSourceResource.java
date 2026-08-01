package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra/data-sources")
public class InfraDataSourceResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String ANALYTICS_SERVICE_EXPRESSION =
        "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-analytics'";
    private static final String INTERNAL_SERVICE_EXPRESSION =
        "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "')";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final InfraManagementService infraManagementService;
    private final AuditService auditService;
    private final JdbcConnectionTestService jdbcConnectionTestService;
    private final IngestionServiceClient ingestionServiceClient;
    private final PlatformInboundServiceAuthProperties inboundAuthProperties;
    private final SvcTokenAuthService svcTokenAuthService;
    private final TransactionTemplate transactionTemplate;

    public InfraDataSourceResource(
        InfraManagementService infraManagementService,
        AuditService auditService,
        JdbcConnectionTestService jdbcConnectionTestService,
        IngestionServiceClient ingestionServiceClient,
        PlatformInboundServiceAuthProperties inboundAuthProperties,
        SvcTokenAuthService svcTokenAuthService,
        PlatformTransactionManager transactionManager
    ) {
        this.infraManagementService = infraManagementService;
        this.auditService = auditService;
        this.jdbcConnectionTestService = jdbcConnectionTestService;
        this.ingestionServiceClient = ingestionServiceClient;
        this.inboundAuthProperties = inboundAuthProperties;
        this.svcTokenAuthService = svcTokenAuthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @GetMapping
    @PreAuthorize("(" + INFRA_MAINTAINER_EXPRESSION + ") or (" + ANALYTICS_SERVICE_EXPRESSION + ")")
    public ApiResponse<List<InfraDataSourceDto>> list(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<InfraDataSourceDto> list = infraManagementService.listDataSources(activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据源列表");
        if (StringUtils.hasText(activeDept)) {
            auditPayload.put("activeDept", StringUtils.trimWhitespace(activeDept));
        }
        auditService.auditAction(
            "FOUNDATION_DATASOURCE_REGISTER",
            AuditStage.SUCCESS,
            "list",
            auditPayload
        );
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraDataSourceDto> detail(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("summary", "查看数据源详情", "id", id.toString())
            );
            return ApiResponses.ok(dto);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.FAIL,
                id.toString(),
                Map.of("summary", "查看数据源详情失败", "id", id.toString(), "errorType", ex.getClass().getSimpleName())
            );
            throw ex;
        }
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "') or (" + ANALYTICS_SERVICE_EXPRESSION + ")")
    public ApiResponse<InfraDataSourceDetailDto> detailWithSecrets(@PathVariable UUID id) {
        InfraDataSourceDetailDto detail = infraManagementService.getDataSourceDetail(id);
        auditService.auditAction(
            "FOUNDATION_DATASOURCE_REGISTER",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看数据源详情(脱敏)", "id", id.toString(), "secretsReturned", false)
        );
        return ApiResponses.ok(detail);
    }

    @GetMapping("/{id}/runtime-detail")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "') or (" + INTERNAL_SERVICE_EXPRESSION + ")")
    public ApiResponse<InfraDataSourceDetailDto> runtimeDetail(
        @PathVariable UUID id,
        @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken
    ) {
        String principal = SecurityUtils.getCurrentUserLogin().orElse("");
        if (!principal.startsWith("service:")) {
            auditService.auditAction(
                "SERVICE_AUTH_DENIED",
                AuditStage.FAIL,
                id.toString(),
                Map.of("endpoint", "infra.runtime-detail", "reason", "non_service_principal", "principal", principal)
            );
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "运行时凭据详情仅允许内部服务调用");
        }
        String serviceName = principal.substring("service:".length());
        if (!serviceTokenMatches(serviceToken, serviceName)) {
            auditService.auditAction(
                "SERVICE_AUTH_DENIED",
                AuditStage.FAIL,
                id.toString(),
                Map.of(
                    "endpoint", "infra.runtime-detail",
                    "reason", "token_mismatch",
                    "service", serviceName
                )
            );
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "运行时凭据详情需要有效服务令牌");
        }
        InfraDataSourceDetailDto detail = infraManagementService.getDataSourceRuntimeDetail(id);
        auditService.auditAction(
            "FOUNDATION_DATASOURCE_REGISTER",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "内部服务读取数据源运行时详情", "id", id.toString(), "service", principal)
        );
        return ApiResponses.ok(detail);
    }

    private boolean serviceTokenMatches(String supplied, String serviceName) {
        if (!StringUtils.hasText(supplied)) {
            return false;
        }
        String normalized = supplied.trim();
        String expected = inboundAuthProperties != null ? inboundAuthProperties.resolveExpectedToken(serviceName) : null;
        if (StringUtils.hasText(expected) && expected.trim().equals(normalized)) {
            return true;
        }
        return svcTokenAuthService != null && svcTokenAuthService.authenticateService(normalized, serviceName) != null;
    }

    @PostMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraDataSourceDto> create(
        @Valid @RequestBody DataSourceRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("name", request.name());
        requested.put("type", request.type());
        putText(requested, "connectorKey", request.connectorKey());
        putText(requested, "ownerDept", request.ownerDept());
        putText(requested, "activeDept", activeDept);
        UUID beginAuditReceipt = auditDataSourceAction(
            "FOUNDATION_DATASOURCE_CREATE",
            AuditStage.BEGIN,
            "create",
            auditOperationId,
            "开始新增数据连接",
            requested
        );
        try {
            InfraDataSourceDto dto = transactionTemplate.execute(status -> {
                InfraDataSourceDto created = infraManagementService.createDataSource(request, operator, activeDept);
                auditDataSourceTerminal(
                    "FOUNDATION_DATASOURCE_CREATE",
                    AuditStage.SUCCESS,
                    created.id() != null ? created.id().toString() : "create",
                    auditOperationId,
                    beginAuditReceipt,
                    "新增数据连接成功",
                    requested,
                    true
                );
                return created;
            });
            return ApiResponses.ok(dto);
        } catch (AuditFinalizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            auditDataSourceAction(
                "FOUNDATION_DATASOURCE_CREATE",
                AuditStage.FAIL,
                "create",
                auditOperationId,
                "新增数据连接失败",
                withBeginReceipt(failureDetails(requested, ex), beginAuditReceipt)
            );
            throw ex;
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraDataSourceDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody DataSourceRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> requested = dataSourceUpdateAuditDetails(id, request, activeDept);
        UUID beginAuditReceipt = auditDataSourceAction(
            "FOUNDATION_DATASOURCE_UPDATE",
            AuditStage.BEGIN,
            id.toString(),
            auditOperationId,
            "开始更新数据连接",
            requested
        );
        try {
            InfraManagementService.DataSourceUpdateImpact impact = transactionTemplate.execute(status -> {
                InfraManagementService.DataSourceUpdateImpact updated = infraManagementService.updateDataSourceWithImpact(
                    id,
                    request,
                    operator,
                    activeDept
                );
                InfraDataSourceDto updatedDataSource = updated.dataSource();
                Map<String, Object> successDetails = dataSourceImpactAuditDetails(updatedDataSource, updated);
                auditDataSourceTerminal(
                    "FOUNDATION_DATASOURCE_UPDATE",
                    AuditStage.SUCCESS,
                    id.toString(),
                    auditOperationId,
                    beginAuditReceipt,
                    "更新数据连接成功",
                    successDetails,
                    true
                );
                return updated;
            });
            InfraDataSourceDto dto = impact.dataSource();
            return ApiResponses.ok(dto);
        } catch (AuditFinalizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            auditDataSourceAction(
                "FOUNDATION_DATASOURCE_UPDATE",
                AuditStage.FAIL,
                id.toString(),
                auditOperationId,
                "更新数据连接失败",
                withBeginReceipt(failureDetails(requested, ex), beginAuditReceipt)
            );
            throw ex;
        }
    }

    @PutMapping("/{id}/impact")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraManagementService.DataSourceUpdateImpact> updateWithImpact(
        @PathVariable UUID id,
        @Valid @RequestBody DataSourceRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> requested = dataSourceUpdateAuditDetails(id, request, activeDept);
        UUID beginAuditReceipt = auditDataSourceAction(
            "FOUNDATION_DATASOURCE_UPDATE",
            AuditStage.BEGIN,
            id.toString(),
            auditOperationId,
            "开始更新数据连接并评估影响",
            requested
        );
        try {
            InfraManagementService.DataSourceUpdateImpact impact = transactionTemplate.execute(status -> {
                InfraManagementService.DataSourceUpdateImpact updated = infraManagementService.updateDataSourceWithImpact(
                    id,
                    request,
                    operator,
                    activeDept
                );
                auditDataSourceTerminal(
                    "FOUNDATION_DATASOURCE_UPDATE",
                    AuditStage.SUCCESS,
                    id.toString(),
                    auditOperationId,
                    beginAuditReceipt,
                    "更新数据连接并完成影响评估",
                    dataSourceImpactAuditDetails(updated.dataSource(), updated),
                    true
                );
                return updated;
            });
            return ApiResponses.ok(impact);
        } catch (AuditFinalizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            auditDataSourceAction(
                "FOUNDATION_DATASOURCE_UPDATE",
                AuditStage.FAIL,
                id.toString(),
                auditOperationId,
                "更新数据连接失败",
                withBeginReceipt(failureDetails(requested, ex), beginAuditReceipt)
            );
            throw ex;
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSourceId", id.toString());
        putText(details, "activeDept", activeDept);
        UUID beginAuditReceipt = auditDataSourceAction(
            "FOUNDATION_DATASOURCE_DISABLE",
            AuditStage.BEGIN,
            id.toString(),
            auditOperationId,
            "开始停用数据连接",
            details
        );
        try {
            transactionTemplate.executeWithoutResult(status -> {
                infraManagementService.deleteDataSource(id, activeDept);
                auditDataSourceTerminal(
                    "FOUNDATION_DATASOURCE_DISABLE",
                    AuditStage.SUCCESS,
                    id.toString(),
                    auditOperationId,
                    beginAuditReceipt,
                    "停用数据连接成功",
                    details,
                    true
                );
            });
            return ApiResponses.ok(null);
        } catch (AuditFinalizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            auditDataSourceAction(
                "FOUNDATION_DATASOURCE_DISABLE",
                AuditStage.FAIL,
                id.toString(),
                auditOperationId,
                "停用数据连接失败",
                withBeginReceipt(failureDetails(details, ex), beginAuditReceipt)
            );
            throw ex;
        }
    }

    @PostMapping("/{id}/test")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<HiveConnectionTestResult> test(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSourceId", id.toString());
        putText(details, "activeDept", activeDept);
        UUID beginAuditReceipt = auditDataSourceAction(
            "FOUNDATION_DATASOURCE_TEST",
            AuditStage.BEGIN,
            id.toString(),
            auditOperationId,
            "开始测试数据连接",
            details
        );
        try {
            InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
            details.put("type", dto.type());
            putText(details, "connectorKey", dto.connectorKey());
            HiveConnectionTestResult result;
            if (!StringUtils.hasText(dto.jdbcUrl())) {
                if (!isApiDataSource(dto)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前数据源类型不支持连接测试");
                }
                result = testApiConnection(id, dto);
            } else {
                result = testJdbcConnection(id, dto);
            }
            if (result.success()) {
                infraManagementService.markDataSourceVerified(id);
            }
            Map<String, Object> outcome = new LinkedHashMap<>(details);
            outcome.put("result", result.success() ? "SUCCESS" : "FAILED");
            outcome.put("elapsedMillis", result.elapsedMillis());
            putText(outcome, "errorType", result.errorType());
            outcome.put("warningCount", result.warnings() == null ? 0 : result.warnings().size());
            auditDataSourceTerminal(
                "FOUNDATION_DATASOURCE_TEST",
                result.success() ? AuditStage.SUCCESS : AuditStage.FAIL,
                id.toString(),
                auditOperationId,
                beginAuditReceipt,
                result.success() ? "数据连接测试成功" : "数据连接测试失败",
                outcome,
                false
            );
            return ApiResponses.ok(result);
        } catch (AuditFinalizationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            auditDataSourceAction(
                "FOUNDATION_DATASOURCE_TEST",
                AuditStage.FAIL,
                id.toString(),
                auditOperationId,
                "数据连接测试异常",
                withBeginReceipt(failureDetails(details, ex), beginAuditReceipt)
            );
            throw ex;
        }
    }

    private HiveConnectionTestResult testJdbcConnection(UUID id, InfraDataSourceDto dto) {
        JdbcConnectionTestRequest request = new JdbcConnectionTestRequest();
        request.setJdbcUrl(dto.jdbcUrl());
        request.setUsername(dto.username());
        Map<String, Object> props = dto.props() != null ? dto.props() : Map.of();
        Object driver = props.get("driverClass");
        if (driver != null) {
            request.setDriverClass(driver.toString());
        }
        Object driverVersion = props.get("driverVersion");
        if (driverVersion != null) {
            request.setDriverVersion(driverVersion.toString());
        }
        if (props.get("schemas") instanceof java.util.List<?> schemas) {
            request.setSchemas(schemas.stream().map(String::valueOf).toList());
        }
        Map<String, Object> secrets = infraManagementService.getDataSourceRuntimeDetail(id).secrets();
        Object password = secrets.get("password");
        if (password != null) {
            request.setPassword(password.toString());
        }
        HiveConnectionTestResult result = jdbcConnectionTestService.testConnection(request);
        infraManagementService.recordConnectionTest(
            id,
            request,
            result,
            SecurityUtils.getCurrentUserLogin().orElse("system")
        );
        return result;
    }

    private HiveConnectionTestResult testApiConnection(UUID id, InfraDataSourceDto dto) {
        Instant startedAt = Instant.now();
        try {
            ApiResponse<Object> response = ingestionServiceClient.testApiConnection(Map.of("dataSourceId", id.toString()));
            Map<String, Object> data = asMap(response != null ? response.getData() : null);
            long elapsed = longValue(data.get("elapsedMs"), Duration.between(startedAt, Instant.now()).toMillis());
            if (boolValue(data.get("connected"))) {
                return HiveConnectionTestResult.success(apiSuccessMessage(data), elapsed, "api-http", null, apiWarnings(data));
            }
            String message = firstText(data, "message", "advice");
            if (!StringUtils.hasText(message) && response != null) {
                message = response.getMessage();
            }
            return HiveConnectionTestResult.failure(
                StringUtils.hasText(message) ? message : "API 连接测试失败",
                elapsed,
                firstText(data, "errorCode", "failureCategory"),
                firstText(data, "advice", "suggestion")
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            long elapsed = Duration.between(startedAt, Instant.now()).toMillis();
            return HiveConnectionTestResult.failure(
                "API 连接测试调用失败：" + ex.getMessage(),
                elapsed,
                "NETWORK",
                "请确认 dts-ingestion 服务可用，并在 API 数据源中检查 baseUrl、鉴权、资源路径与运行时策略。"
            );
        }
    }

    private boolean isApiDataSource(InfraDataSourceDto dto) {
        String type = dto.type() == null ? "" : dto.type().trim().toLowerCase();
        Object connectorType = dto.props() != null ? dto.props().get("connectorType") : null;
        return List.of("api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader").contains(type)
            || "api".equalsIgnoreCase(String.valueOf(connectorType));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> apiWarnings(Map<String, Object> data) {
        Object raw = data.get("warnings");
        if (!(raw instanceof Iterable<?> items)) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (Object item : items) {
            String value = asText(item);
            if (StringUtils.hasText(value)) {
                warnings.add(value);
            }
        }
        return List.copyOf(warnings);
    }

    private String apiSuccessMessage(Map<String, Object> data) {
        StringBuilder message = new StringBuilder("API 连接成功");
        String httpStatus = asText(data.get("httpStatus"));
        if (StringUtils.hasText(httpStatus)) {
            message.append("，HTTP ").append(httpStatus);
        }
        String sampleCount = asText(data.get("sampleCount"));
        if (StringUtils.hasText(sampleCount)) {
            message.append("，样本 ").append(sampleCount).append(" 条");
        }
        return message.toString();
    }

    private String firstText(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = asText(data.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean boolValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(asText(value));
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = asText(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @PostMapping("/{id}/schema-discover")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> discoverSchema(
        @PathVariable UUID id,
        @RequestBody(required = false) Object ignored,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return retiredApi("旧 Schema 探测接口已停用，请在接入任务中使用源表发现");
    }

    @PostMapping("/{id}/ods-preview")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> previewOdsGeneration(
        @PathVariable UUID id,
        @RequestBody(required = false) Object ignored,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return retiredApi("旧 ODS 预览接口已停用，请在接入任务落地配置中预览目标结构");
    }

    @PostMapping("/{id}/ods-apply")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> applyOdsGeneration(
        @PathVariable UUID id,
        @RequestBody(required = false) Object ignored,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return retiredApi("旧 ODS 应用接口已停用，请创建并执行正式接入任务");
    }

    @PostMapping("/{id}/ods-precheck")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> precheckOdsGeneration(
        @PathVariable UUID id,
        @RequestBody(required = false) Object ignored,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return retiredApi("旧 ODS 预检接口已停用；文件任务使用落地前预检，数据库和 API 任务使用运行后质量检查");
    }

    @PostMapping("/{id}/sync-task-draft")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> buildSyncTaskDraft(
        @PathVariable UUID id,
        @RequestBody(required = false) Object ignored,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return retiredApi("旧同步任务草稿接口已停用，请在统一接入工作台创建任务");
    }

    private org.springframework.http.ResponseEntity<ApiResponse<Void>> retiredApi(String message) {
        return org.springframework.http.ResponseEntity
            .status(HttpStatus.GONE)
            .body(new ApiResponse<>(HttpStatus.GONE.value(), message, "API_RETIRED", null));
    }

    private UUID auditDataSourceAction(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String operationId,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("auditOperationId", operationId);
        payload.put("operator", SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (details != null) {
            payload.putAll(details);
        }
        payload.entrySet().removeIf(entry -> entry.getValue() == null);
        return auditService.auditActionStrict(actionCode, stage, resourceId, Map.copyOf(payload));
    }

    private void auditDataSourceTerminal(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String operationId,
        UUID beginAuditReceipt,
        String summary,
        Map<String, Object> details,
        boolean rolledBackOnFailure
    ) {
        try {
            auditDataSourceAction(
                actionCode,
                stage,
                resourceId,
                operationId,
                summary,
                withBeginReceipt(details, beginAuditReceipt)
            );
        } catch (RuntimeException auditFailure) {
            throw new AuditFinalizationException(operationId, beginAuditReceipt, rolledBackOnFailure, auditFailure);
        }
    }

    private Map<String, Object> dataSourceImpactAuditDetails(
        InfraDataSourceDto dto,
        InfraManagementService.DataSourceUpdateImpact impact
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", dto.name());
        details.put("connectorKey", dto.connectorKey());
        details.put("connectionChanged", impact.connectionChanged());
        details.put("affectedTasks", impact.affectedTasks());
        details.put("changeLogCreated", impact.changeLogCreated());
        details.put("taskIds", impact.taskIds());
        return details;
    }

    private Map<String, Object> withBeginReceipt(Map<String, Object> details, UUID beginAuditReceipt) {
        Map<String, Object> correlated = new LinkedHashMap<>();
        if (details != null) {
            correlated.putAll(details);
        }
        correlated.put("beginAuditReceipt", beginAuditReceipt.toString());
        return correlated;
    }

    private Map<String, Object> dataSourceUpdateAuditDetails(
        UUID id,
        DataSourceRequest request,
        String activeDept
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSourceId", id.toString());
        details.put("name", request.name());
        details.put("type", request.type());
        putText(details, "connectorKey", request.connectorKey());
        putText(details, "ownerDept", request.ownerDept());
        putText(details, "activeDept", activeDept);
        return details;
    }

    private Map<String, Object> failureDetails(Map<String, Object> base, RuntimeException failure) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (base != null) {
            details.putAll(base);
        }
        details.put("errorType", failure.getClass().getSimpleName());
        if (failure instanceof ResponseStatusException statusFailure) {
            details.put("httpStatus", statusFailure.getStatusCode().value());
        }
        return details;
    }

    private void putText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private static final class AuditFinalizationException extends ResponseStatusException {

        private AuditFinalizationException(
            String operationId,
            UUID beginAuditReceipt,
            boolean rolledBack,
            RuntimeException cause
        ) {
            super(
                HttpStatus.CONFLICT,
                rolledBack
                    ? "审计终态写入失败，数据连接变更已回滚；请使用审计操作号核对后再重试。operationId=" + operationId
                    : "操作结果已经产生，但审计终态尚未确认；请勿重复执行，请使用 BEGIN 回执核对。operationId=" +
                    operationId +
                    ", beginReceipt=" +
                    beginAuditReceipt,
                cause
            );
        }
    }
}
