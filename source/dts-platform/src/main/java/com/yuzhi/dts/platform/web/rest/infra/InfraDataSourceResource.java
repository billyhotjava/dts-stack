package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDetailDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
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

    private final InfraManagementService infraManagementService;
    private final AuditService auditService;
    private final JdbcConnectionTestService jdbcConnectionTestService;

    public InfraDataSourceResource(
        InfraManagementService infraManagementService,
        AuditService auditService,
        JdbcConnectionTestService jdbcConnectionTestService
    ) {
        this.infraManagementService = infraManagementService;
        this.auditService = auditService;
        this.jdbcConnectionTestService = jdbcConnectionTestService;
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
            Map.of("summary", "查看数据源详情(含密文)", "id", id.toString())
        );
        return ApiResponses.ok(detail);
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
                Map.of("summary", "新增数据源", "name", dto.name(), "operator", operator)
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
        Map<String, Object> secrets = infraManagementService.getDataSourceDetail(id).secrets();
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
}
