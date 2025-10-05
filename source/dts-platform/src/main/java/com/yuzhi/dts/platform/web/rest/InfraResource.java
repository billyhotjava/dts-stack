package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.InfraTaskSchedule;
import com.yuzhi.dts.platform.config.HiveExecutionProperties;
import com.yuzhi.dts.platform.repository.service.InfraTaskScheduleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
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
import java.util.List;
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

    public InfraResource(
        InfraTaskScheduleRepository schedRepo,
        AuditService audit,
        InfraManagementService managementService,
        HiveConnectionService hiveConnectionService,
        HiveExecutionProperties hiveProps
    ) {
        this.schedRepo = schedRepo;
        this.audit = audit;
        this.managementService = managementService;
        this.hiveConnectionService = hiveConnectionService;
        this.hiveProps = hiveProps;
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
        return ApiResponses.ok(Map.of(
            "multiSourceEnabled", managementService.isMultiSourceEnabled(),
            "defaultJdbcUrl", hiveProps.getJdbcUrl()
        ));
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
}
