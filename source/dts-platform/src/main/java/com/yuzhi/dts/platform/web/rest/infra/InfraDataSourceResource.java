package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
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
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
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
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final InfraManagementService infraManagementService;
    private final AuditService auditService;
    private final JdbcConnectionTestService jdbcConnectionTestService;
    private final JdbcCatalogSyncService jdbcCatalogSyncService;
    private final OdsGenerationService odsGenerationService;
    private final DtsAdminProperties dtsAdminProperties;

    public InfraDataSourceResource(
        InfraManagementService infraManagementService,
        AuditService auditService,
        JdbcConnectionTestService jdbcConnectionTestService,
        JdbcCatalogSyncService jdbcCatalogSyncService,
        OdsGenerationService odsGenerationService,
        DtsAdminProperties dtsAdminProperties
    ) {
        this.infraManagementService = infraManagementService;
        this.auditService = auditService;
        this.jdbcConnectionTestService = jdbcConnectionTestService;
        this.jdbcCatalogSyncService = jdbcCatalogSyncService;
        this.odsGenerationService = odsGenerationService;
        this.dtsAdminProperties = dtsAdminProperties;
    }

    @GetMapping
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
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
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataSourceDetailDto> runtimeDetail(
        @PathVariable UUID id,
        @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken
    ) {
        String principal = SecurityUtils.getCurrentUserLogin().orElse("");
        if (!principal.startsWith("service:")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "运行时凭据详情仅允许内部服务调用");
        }
        if (!serviceTokenMatches(serviceToken)) {
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

    private boolean serviceTokenMatches(String supplied) {
        String expected = dtsAdminProperties != null ? dtsAdminProperties.getServiceToken() : null;
        return StringUtils.hasText(expected) && StringUtils.hasText(supplied) && expected.trim().equals(supplied.trim());
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非 JDBC 数据源无需测试连接");
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
