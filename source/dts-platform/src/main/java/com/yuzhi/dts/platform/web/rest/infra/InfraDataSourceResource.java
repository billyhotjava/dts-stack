package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.JdbcCatalogSyncService;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.service.infra.OdsGenerationService;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationApplyResult;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationPreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationRequest;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsPrecheckResponse;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsSyncTaskDraftResponse;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverRequest;
import com.yuzhi.dts.platform.service.infra.dto.SchemaDiscoverDtos.SchemaDiscoverResponse;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final JdbcCatalogSyncService jdbcCatalogSyncService;
    private final OdsGenerationService odsGenerationService;
    private final PlatformInboundServiceAuthProperties inboundAuthProperties;
    private final SvcTokenAuthService svcTokenAuthService;

    public InfraDataSourceResource(
        InfraManagementService infraManagementService,
        AuditService auditService,
        JdbcConnectionTestService jdbcConnectionTestService,
        JdbcCatalogSyncService jdbcCatalogSyncService,
        OdsGenerationService odsGenerationService,
        PlatformInboundServiceAuthProperties inboundAuthProperties,
        SvcTokenAuthService svcTokenAuthService
    ) {
        this.infraManagementService = infraManagementService;
        this.auditService = auditService;
        this.jdbcConnectionTestService = jdbcConnectionTestService;
        this.jdbcCatalogSyncService = jdbcCatalogSyncService;
        this.odsGenerationService = odsGenerationService;
        this.inboundAuthProperties = inboundAuthProperties;
        this.svcTokenAuthService = svcTokenAuthService;
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
                Map.of("summary", "查看数据源详情失败", "id", id.toString(), "error", ex.getMessage())
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
        try {
            InfraDataSourceDto dto = infraManagementService.createDataSource(request, operator, activeDept);
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.SUCCESS,
                dto.id() != null ? dto.id().toString() : "create",
                Map.of(
                    "summary",
                    "新增数据源",
                    "name",
                    dto.name(),
                    "connectorKey",
                    dto.connectorKey(),
                    "operator",
                    operator
                )
            );
            return ApiResponses.ok(dto);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.FAIL,
                "create",
                Map.of("summary", "新增数据源失败", "error", ex.getMessage(), "operator", operator)
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
        try {
            InfraManagementService.DataSourceUpdateImpact impact = infraManagementService.updateDataSourceWithImpact(
                id,
                request,
                operator,
                activeDept
            );
            InfraDataSourceDto dto = impact.dataSource();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("summary", "更新数据源");
            meta.put("name", dto.name());
            meta.put("connectorKey", dto.connectorKey());
            meta.put("operator", operator);
            meta.put("connectionChanged", impact.connectionChanged());
            meta.put("affectedTasks", impact.affectedTasks());
            meta.put("changeLogCreated", impact.changeLogCreated());
            meta.put("taskIds", impact.taskIds());
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.SUCCESS,
                id.toString(),
                meta
            );
            return ApiResponses.ok(dto);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.FAIL,
                id.toString(),
                Map.of("summary", "更新数据源失败", "error", ex.getMessage(), "operator", operator)
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
        try {
            InfraManagementService.DataSourceUpdateImpact impact = infraManagementService.updateDataSourceWithImpact(
                id,
                request,
                operator,
                activeDept
            );
            InfraDataSourceDto dto = impact.dataSource();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("summary", "更新数据源");
            meta.put("name", dto.name());
            meta.put("connectorKey", dto.connectorKey());
            meta.put("operator", operator);
            meta.put("connectionChanged", impact.connectionChanged());
            meta.put("affectedTasks", impact.affectedTasks());
            meta.put("changeLogCreated", impact.changeLogCreated());
            meta.put("taskIds", impact.taskIds());
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.SUCCESS,
                id.toString(),
                meta
            );
            return ApiResponses.ok(impact);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_REGISTER",
                AuditStage.FAIL,
                id.toString(),
                Map.of("summary", "更新数据源失败", "error", ex.getMessage(), "operator", operator)
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
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            infraManagementService.deleteDataSource(id, activeDept);
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_DISABLE",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("summary", "删除数据源", "id", id.toString(), "operator", operator)
            );
            return ApiResponses.ok(null);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                "FOUNDATION_DATASOURCE_DISABLE",
                AuditStage.FAIL,
                id.toString(),
                Map.of("summary", "删除数据源失败", "error", ex.getMessage(), "operator", operator)
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
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            if (isApiDataSource(dto)) {
                HiveConnectionTestResult result = testApiConnection(id, dto);
                if (result.success()) {
                    infraManagementService.markDataSourceVerified(id);
                }
                auditService.auditAction(
                    "FOUNDATION_DATASOURCE_TEST",
                    result.success() ? AuditStage.SUCCESS : AuditStage.FAIL,
                    id.toString(),
                    Map.of("summary", "测试 API 数据源连接", "id", id.toString(), "result", result.success() ? "SUCCESS" : "FAILED")
                );
                return ApiResponses.ok(result);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前数据源类型不支持连接测试");
        }
        // Use existing JDBC test endpoint by mapping dto into request
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
        // Persist test log and update lastVerifiedAt on success
        String username = SecurityUtils.getCurrentUserLogin().orElse("system");
        infraManagementService.recordConnectionTest(id, request, result, username);
        if (result.success()) {
            infraManagementService.markDataSourceVerified(id);
        }
        auditService.auditAction(
            "FOUNDATION_DATASOURCE_TEST",
            result.success() ? AuditStage.SUCCESS : AuditStage.FAIL,
            id.toString(),
            Map.of("summary", "测试数据源连接", "id", id.toString(), "result", result.success() ? "SUCCESS" : "FAILED")
        );
        return ApiResponses.ok(result);
    }

    private HiveConnectionTestResult testApiConnection(UUID id, InfraDataSourceDto dto) {
        InfraDataSourceDetailDto detail = infraManagementService.getDataSourceRuntimeDetail(id);
        Map<String, Object> props = detail.props() != null ? detail.props() : Map.of();
        Map<String, Object> secrets = detail.secrets() != null ? detail.secrets() : Map.of();
        String baseUrl = extractApiBaseUrl(props);
        if (!StringUtils.hasText(baseUrl)) {
            return HiveConnectionTestResult.failure("API 数据源 baseUrl 为空，无法测试连接。", 0, "CONFIG", "请编辑 API 数据源并填写 http/https baseUrl。");
        }

        Instant startedAt = Instant.now();
        try {
            HttpURLConnection connection = (HttpURLConnection) buildApiProbeUri(baseUrl, props, secrets).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            applyApiHeaders(connection, props, secrets);
            int status = connection.getResponseCode();
            long elapsed = Duration.between(startedAt, Instant.now()).toMillis();
            return apiProbeResult(status, elapsed);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            long elapsed = Duration.between(startedAt, Instant.now()).toMillis();
            return HiveConnectionTestResult.failure(
                "API 连接失败：" + ex.getMessage(),
                elapsed,
                "NETWORK",
                "请确认 baseUrl 可由 dts-platform 容器访问，并检查 DNS、端口、防火墙和代理配置。"
            );
        }
    }

    private HiveConnectionTestResult apiProbeResult(int status, long elapsed) {
        if (status >= 200 && status < 400) {
            return HiveConnectionTestResult.success("API 基础地址连接成功，HTTP 状态码：" + status, elapsed, null, null, List.of());
        }
        if (status == 401 || status == 403) {
            return HiveConnectionTestResult.failure(
                "API 基础地址可达，但鉴权失败，HTTP 状态码：" + status,
                elapsed,
                "AUTH",
                "请检查 API 数据源连接的鉴权方式、密钥字段和默认请求头。"
            );
        }
        if (status < 500) {
            return HiveConnectionTestResult.success(
                "API 基础地址可达，HTTP 状态码：" + status + "。具体资源路径请在 API 入湖任务中配置后再执行。",
                elapsed,
                null,
                null,
                List.of("基础地址返回 4xx，通常表示需要配置具体资源 path、查询参数或业务鉴权。")
            );
        }
        return HiveConnectionTestResult.failure(
            "API 服务返回异常，HTTP 状态码：" + status,
            elapsed,
            "HTTP_" + status,
            "请确认外部 API 服务健康，或在资源配置中使用可正常响应的 path。"
        );
    }

    private boolean isApiDataSource(InfraDataSourceDto dto) {
        String type = dto.type() == null ? "" : dto.type().trim().toLowerCase();
        Object connectorType = dto.props() != null ? dto.props().get("connectorType") : null;
        return List.of("api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader").contains(type)
            || "api".equalsIgnoreCase(String.valueOf(connectorType));
    }

    private String extractApiBaseUrl(Map<String, Object> props) {
        String direct = asText(props.get("baseUrl"));
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        Map<String, Object> api = asMap(props.get("api"));
        direct = api != null ? asText(api.get("baseUrl")) : null;
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        Map<String, Object> readerConfig = asMap(props.get("readerConfig"));
        direct = readerConfig != null ? asText(readerConfig.get("baseUrl")) : null;
        return StringUtils.hasText(direct) ? direct.trim() : null;
    }

    private URI buildApiProbeUri(String baseUrl, Map<String, Object> props, Map<String, Object> secrets) {
        try {
            URI uri = URI.create(baseUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl 仅支持 http/https");
            }
            Map<String, Object> auth = extractApiAuth(props);
            String provider = asText(auth.get("provider"));
            Map<String, Object> config = asMap(auth.get("config"));
            if ("apikey".equalsIgnoreCase(provider) && "query".equalsIgnoreCase(asText(config != null ? config.get("location") : null))) {
                String name = asText(config.get("name"));
                String value = asText(secrets.get("value"));
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    String query = uri.getRawQuery();
                    String nextQuery = (StringUtils.hasText(query) ? query + "&" : "")
                        + URLEncoder.encode(name, StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8);
                    return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), nextQuery, uri.getRawFragment());
                }
            }
            return uri;
        } catch (IllegalArgumentException | java.net.URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API 数据源 baseUrl 格式不合法");
        }
    }

    private void applyApiHeaders(HttpURLConnection connection, Map<String, Object> props, Map<String, Object> secrets) {
        applyHeaderMap(connection, asMap(props.get("defaultHeaders")));
        Map<String, Object> api = asMap(props.get("api"));
        if (api != null) {
            applyHeaderMap(connection, asMap(api.get("defaultHeaders")));
        }
        Map<String, Object> readerConfig = asMap(props.get("readerConfig"));
        if (readerConfig != null) {
            applyHeaderMap(connection, asMap(readerConfig.get("defaultHeaders")));
        }

        Map<String, Object> auth = extractApiAuth(props);
        String provider = asText(auth.get("provider"));
        Map<String, Object> config = asMap(auth.get("config"));
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        if ("bearertoken".equals(normalizedProvider) || "bearer".equals(normalizedProvider)) {
            String token = asText(secrets.get("token"));
            if (StringUtils.hasText(token)) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }
        } else if ("basic".equals(normalizedProvider)) {
            String username = asText(config != null ? config.get("username") : null);
            String password = asText(secrets.get("password"));
            if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + token);
            }
        } else if ("apikey".equals(normalizedProvider) && !"query".equalsIgnoreCase(asText(config != null ? config.get("location") : null))) {
            String name = asText(config != null ? config.get("name") : null);
            String value = asText(secrets.get("value"));
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                connection.setRequestProperty(name.trim(), value.trim());
            }
        }
    }

    private void applyHeaderMap(HttpURLConnection connection, Map<String, Object> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((key, value) -> {
            String headerName = key == null ? "" : key.trim();
            String headerValue = asText(value);
            if (StringUtils.hasText(headerName) && StringUtils.hasText(headerValue)) {
                connection.setRequestProperty(headerName, headerValue.trim());
            }
        });
    }

    private Map<String, Object> extractApiAuth(Map<String, Object> props) {
        Map<String, Object> direct = asMap(props.get("auth"));
        if (direct != null) {
            return direct;
        }
        Map<String, Object> api = asMap(props.get("api"));
        Map<String, Object> apiAuth = api != null ? asMap(api.get("auth")) : null;
        if (apiAuth != null) {
            return apiAuth;
        }
        Map<String, Object> readerConfig = asMap(props.get("readerConfig"));
        Map<String, Object> readerAuth = readerConfig != null ? asMap(readerConfig.get("auth")) : null;
        if (readerAuth != null) {
            return readerAuth;
        }
        String provider = asText(props.get("authProvider"));
        return StringUtils.hasText(provider) ? Map.of("provider", provider) : Map.of("provider", "none");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @PostMapping("/{id}/schema-discover")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<SchemaDiscoverResponse> discoverSchema(
        @PathVariable UUID id,
        @RequestBody(required = false) SchemaDiscoverRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源不支持 JDBC Schema Discover");
        }
        SchemaDiscoverResponse response = jdbcCatalogSyncService.discover(infraManagementService.findEntity(id), request);
        auditService.auditAction(
            "FOUNDATION_SCHEMA_DISCOVER",
            "SUCCESS".equalsIgnoreCase(response.status()) ? AuditStage.SUCCESS : AuditStage.FAIL,
            id.toString(),
            Map.of(
                "summary",
                "探测数据源 Schema",
                "id",
                id.toString(),
                "schemaCount",
                response.schemas() != null ? response.schemas().size() : 0,
                "tableCount",
                response.tables() != null ? response.tables().size() : 0,
                "status",
                response.status()
            )
        );
        return ApiResponses.ok(response);
    }

    @PostMapping("/{id}/ods-preview")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<OdsGenerationPreviewResponse> previewOdsGeneration(
        @PathVariable UUID id,
        @RequestBody OdsGenerationRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源暂不支持 ODS 预览生成");
        }
        OdsGenerationPreviewResponse response = odsGenerationService.preview(infraManagementService.findEntity(id), request);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "预览 ODS/dbt source 生成");
        meta.put("id", id.toString());
        meta.put("tables", response.tables() != null ? response.tables().size() : 0);
        meta.put("odsSchema", response.odsSchema());
        auditService.auditAction("FOUNDATION_ODS_PREVIEW", AuditStage.SUCCESS, id.toString(), meta);
        return ApiResponses.ok(response);
    }

    @PostMapping("/{id}/ods-apply")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<OdsGenerationApplyResult> applyOdsGeneration(
        @PathVariable UUID id,
        @RequestBody OdsGenerationRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源暂不支持 ODS 映射生成");
        }
        OdsGenerationApplyResult result = odsGenerationService.apply(infraManagementService.findEntity(id), request);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "生成 ODS 映射和 dbt source");
        meta.put("id", id.toString());
        meta.put("mappingsUpserted", result.mappingsUpserted());
        meta.put("columnsUpserted", result.columnsUpserted());
        meta.put("lineageCreated", result.lineageCreated());
        meta.put("lineageUpdated", result.lineageUpdated());
        meta.put("lineageSkipped", result.lineageSkipped());
        meta.put("dbt", result.dbtMessage());
        auditService.auditAction("FOUNDATION_ODS_APPLY", AuditStage.SUCCESS, id.toString(), meta);
        return ApiResponses.ok(result);
    }

    @PostMapping("/{id}/ods-precheck")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<OdsPrecheckResponse> precheckOdsGeneration(
        @PathVariable UUID id,
        @RequestBody OdsGenerationRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源暂不支持 ODS 预检");
        }
        OdsPrecheckResponse response = odsGenerationService.precheck(infraManagementService.findEntity(id), request);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "同步任务生成前预检");
        meta.put("id", id.toString());
        meta.put("status", response.status());
        meta.put("tables", response.tables() != null ? response.tables().size() : 0);
        meta.put("failedRules", response.failedRules());
        meta.put("warningRules", response.warningRules());
        auditService.auditAction(
            "FOUNDATION_ODS_PRECHECK",
            response.failedRules() > 0 ? AuditStage.FAIL : AuditStage.SUCCESS,
            id.toString(),
            meta
        );
        return ApiResponses.ok(response);
    }

    @PostMapping("/{id}/sync-task-draft")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<OdsSyncTaskDraftResponse> buildSyncTaskDraft(
        @PathVariable UUID id,
        @RequestBody OdsGenerationRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        InfraDataSourceDto dto = infraManagementService.getDataSource(id, activeDept);
        if (!StringUtils.hasText(dto.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源暂不支持同步任务生成");
        }
        OdsSyncTaskDraftResponse response = odsGenerationService.buildSyncTaskDraft(infraManagementService.findEntity(id), request);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("summary", "生成同步任务草稿");
        meta.put("id", id.toString());
        meta.put("taskName", response.taskName());
        meta.put("tables", response.tables() != null ? response.tables().size() : 0);
        auditService.auditAction("FOUNDATION_SYNC_TASK_DRAFT", AuditStage.SUCCESS, id.toString(), meta);
        return ApiResponses.ok(response);
    }
}
