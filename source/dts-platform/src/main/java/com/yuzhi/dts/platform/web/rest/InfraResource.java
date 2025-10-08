package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.InfraTaskSchedule;
import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.repository.service.InfraTaskScheduleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator.IntegrationStatus;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.InfraManagementService;
import com.yuzhi.dts.platform.service.infra.dto.ConnectionTestLogDto;
import com.yuzhi.dts.platform.service.infra.dto.DataSourceRequest;
import com.yuzhi.dts.platform.service.infra.dto.DataStorageRequest;
import com.yuzhi.dts.platform.service.infra.dto.HiveConnectionPersistRequest;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraDataStorageDto;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/infra")
@Transactional
public class InfraResource {

    private final InfraTaskScheduleRepository schedRepo;
    private final AuditService audit;
    private final InfraManagementService managementService;
    private final HiveConnectionService hiveConnectionService;
    private final HiveExecutionProperties hiveProps;
    private final InceptorDataSourceRegistry inceptorRegistry;
    private final InceptorIntegrationCoordinator integrationCoordinator;

    public InfraResource(
        InfraTaskScheduleRepository schedRepo,
        AuditService audit,
        InfraManagementService managementService,
        HiveConnectionService hiveConnectionService,
        HiveExecutionProperties hiveProps,
        InceptorDataSourceRegistry inceptorRegistry,
        InceptorIntegrationCoordinator integrationCoordinator
    ) {
        this.schedRepo = schedRepo;
        this.audit = audit;
        this.managementService = managementService;
        this.hiveConnectionService = hiveConnectionService;
        this.hiveProps = hiveProps;
        this.inceptorRegistry = inceptorRegistry;
        this.integrationCoordinator = integrationCoordinator;
    }

    // Data sources
    @GetMapping("/data-sources")
    public ApiResponse<List<InfraDataSourceDto>> listDataSources() {
        var list = managementService.listDataSources();
        audit.audit("READ", "infra.dataSource", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/data-sources/test-connection")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<HiveConnectionTestResult> testDataSourceConnection(@Valid @RequestBody HiveConnectionTestRequest request) {
        var result = hiveConnectionService.testConnection(request);
        var user = SecurityUtils.getCurrentUserLogin().orElse("system");
        managementService.recordConnectionTest(null, request, result, user);
        audit.audit("TEST", "infra.dataSource", "test-connection:" + request.getLoginPrincipal());
        return ApiResponses.ok(result);
    }

    @PostMapping("/data-sources")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataSourceDto> createDataSource(@Valid @RequestBody DataSourceRequest request) {
        var user = SecurityUtils.getCurrentUserLogin().orElse("system");
        var saved = managementService.createDataSource(request, user);
        audit.audit("CREATE", "infra.dataSource", String.valueOf(saved.id()));
        return ApiResponses.ok(saved);
    }

    @PostMapping("/data-sources/inceptor/publish")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataSourceDto> publishInceptorDataSource(@Valid @RequestBody HiveConnectionPersistRequest request) {
        var user = SecurityUtils.getCurrentUserLogin().orElse("system");
        var saved = managementService.publishInceptorDataSource(request, user);
        audit.audit("PUBLISH", "infra.dataSource.inceptor", String.valueOf(saved.id()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataSourceDto> updateDataSource(@PathVariable UUID id, @Valid @RequestBody DataSourceRequest request) {
        var user = SecurityUtils.getCurrentUserLogin().orElse("system");
        var saved = managementService.updateDataSource(id, request, user);
        audit.audit("UPDATE", "infra.dataSource", String.valueOf(id));
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteDataSource(@PathVariable UUID id) {
        managementService.deleteDataSource(id);
        audit.audit("DELETE", "infra.dataSource", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Data storages
    @GetMapping("/data-storages")
    public ApiResponse<List<InfraDataStorageDto>> listDataStorages() {
        var list = managementService.listDataStorages();
        audit.audit("READ", "infra.dataStorage", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/data-storages")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataStorageDto> createDataStorage(@Valid @RequestBody DataStorageRequest request) {
        var user = SecurityUtils.getCurrentUserLogin().orElse("system");
        var saved = managementService.createDataStorage(request, user);
        audit.audit("CREATE", "infra.dataStorage", String.valueOf(saved.id()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/data-storages/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraDataStorageDto> updateDataStorage(@PathVariable UUID id, @Valid @RequestBody DataStorageRequest request) {
        var user = com.yuzhi.dts.platform.security.SecurityUtils.getCurrentUserLogin().orElse("system");
        var saved = managementService.updateDataStorage(id, request, user);
        audit.audit("UPDATE", "infra.dataStorage", String.valueOf(id));
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/data-storages/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteDataStorage(@PathVariable UUID id) {
        managementService.deleteDataStorage(id);
        audit.audit("DELETE", "infra.dataStorage", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/data-sources/test-logs")
    public ApiResponse<List<ConnectionTestLogDto>> recentTestLogs(@RequestParam(required = false) UUID dataSourceId) {
        var logs = managementService.recentTestLogs(dataSourceId);
        audit.audit("READ", "infra.dataSource.testLogs", dataSourceId != null ? dataSourceId.toString() : "recent");
        return ApiResponses.ok(logs);
    }

    @GetMapping("/features")
    public ApiResponse<Map<String, Object>> features() {
        // Auto-kick catalog sync when active Inceptor is present but datasets are not yet imported
        try {
            var active = inceptorRegistry.getActive().isPresent();
            var status = integrationCoordinator.currentStatus();
            if (active && (status == null || status.catalogDatasetCount() <= 0)) {
                Thread t = new Thread(() -> {
                    try {
                        integrationCoordinator.synchronize("features-auto");
                    } catch (Exception ignore) {}
                }, "inceptor-features-auto-sync");
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception ignore) {}
        return ApiResponses.ok(buildFeaturesPayload());
    }

    @PostMapping("/data-sources/inceptor/refresh")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> refreshInceptorDataSource() {
        inceptorRegistry.refresh();
        integrationCoordinator.synchronize("manual-refresh");
        audit.audit("REFRESH", "infra.dataSource.inceptor", "manual");
        return ApiResponses.ok(buildFeaturesPayload());
    }

    // Task schedules
    @GetMapping("/schedules")
    public ApiResponse<List<InfraTaskSchedule>> listSchedules() {
        var list = schedRepo.findAll();
        audit.audit("READ", "infra.schedule", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraTaskSchedule> createSchedule(@Valid @RequestBody InfraTaskSchedule sc) {
        var saved = schedRepo.save(sc);
        audit.audit("CREATE", "infra.schedule", String.valueOf(saved.getId()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<InfraTaskSchedule> updateSchedule(@PathVariable UUID id, @Valid @RequestBody InfraTaskSchedule patch) {
        var existing = schedRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setCron(patch.getCron());
        existing.setStatus(patch.getStatus());
        existing.setLastRunAt(patch.getLastRunAt());
        existing.setDescription(patch.getDescription());
        var saved = schedRepo.save(existing);
        audit.audit("UPDATE", "infra.schedule", String.valueOf(id));
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteSchedule(@PathVariable UUID id) {
        schedRepo.deleteById(id);
        audit.audit("DELETE", "infra.schedule", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }

    private Map<String, Object> buildFeaturesPayload() {
        InceptorDataSourceState state = inceptorRegistry.getActive().orElse(null);
        IntegrationStatus status = integrationCoordinator.currentStatus();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("syncInProgress", integrationCoordinator.isSyncInProgress());
        payload.put("multiSourceEnabled", managementService.isMultiSourceEnabled());
        payload.put("hasActiveInceptor", state != null);
        payload.put("inceptorStatus", state != null ? "ACTIVE" : "NONE");
        payload.put("defaultJdbcUrl", state != null ? state.jdbcUrl() : hiveProps.getJdbcUrl());
        payload.put("loginPrincipal", state != null ? state.loginPrincipal() : null);
        payload.put("lastVerifiedAt", state != null ? state.lastVerifiedAt() : null);
        payload.put("lastUpdatedAt", state != null ? state.lastUpdatedAt() : null);
        if (state != null) {
            payload.put("dataSourceName", state.name());
            payload.put("description", state.description());
            payload.put("authMethod", state.authMethod().name());
            payload.put("database", state.database());
            payload.put("proxyUser", state.proxyUser());
            payload.put("engineVersion", state.engineVersion());
            payload.put("driverVersion", state.driverVersion());
            payload.put("lastTestElapsedMillis", state.lastTestElapsedMillis());
        }
        payload.put("moduleStatuses", buildModuleStatuses(state, status));
        payload.put("integrationStatus", buildIntegrationStatus(status));
        return payload;
    }

    private List<Map<String, Object>> buildModuleStatuses(InceptorDataSourceState state, IntegrationStatus status) {
        List<Map<String, Object>> modules = new ArrayList<>();
        boolean active = state != null;
        Instant updatedAt = status != null ? status.timestamp() : null;
        long datasetCount = status != null ? status.catalogDatasetCount() : 0L;

        boolean catalogReady = active && datasetCount > 0;
        String catalogMessage;
        if (!active) {
            catalogMessage = "未配置 Inceptor 数据源";
        } else if (datasetCount == 0) {
            catalogMessage = "未发现 Hive 表，请在 Inceptor 中建表后点击重新同步";
        } else {
            catalogMessage = "已同步 " + datasetCount + " 个 Hive 表";
        }

        modules.add(moduleStatus("catalog", catalogReady, updatedAt, catalogMessage));
        modules.add(moduleStatus("governance", active, updatedAt, active ? "数据治理可执行 Hive 规则" : "无法执行质量规则"));
        modules.add(moduleStatus("development", active, updatedAt, active ? "SQL 工具将直接连接 Inceptor" : "SQL 工具不可用"));
        return modules;
    }

    private Map<String, Object> moduleStatus(String module, boolean active, Instant updatedAt, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("module", module);
        map.put("status", active ? "READY" : "BLOCKED");
        map.put("message", message);
        if (updatedAt != null) {
            map.put("updatedAt", updatedAt);
        }
        return map;
    }

    private Map<String, Object> buildIntegrationStatus(IntegrationStatus status) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (status != null) {
            map.put("lastSyncAt", status.timestamp());
            map.put("reason", status.reason());
            map.put("actions", status.actions());
            map.put("catalogDatasetCount", status.catalogDatasetCount());
            map.put("database", status.database());
            map.put("tablesDiscovered", status.tablesDiscovered());
            map.put("datasetsCreated", status.datasetsCreated());
            map.put("tablesCreated", status.tablesCreated());
            map.put("columnsImported", status.columnsImported());
            if (status.error() != null) {
                map.put("error", status.error());
            }
        }
        return map;
    }
}
