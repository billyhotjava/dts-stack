package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.domain.service.InfraDataStorage;
import com.yuzhi.dts.platform.domain.service.InfraTaskSchedule;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataStorageRepository;
import com.yuzhi.dts.platform.repository.service.InfraTaskScheduleRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
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

    private final InfraDataSourceRepository dsRepo;
    private final InfraDataStorageRepository storageRepo;
    private final InfraTaskScheduleRepository schedRepo;
    private final AuditService audit;

    public InfraResource(InfraDataSourceRepository dsRepo, InfraDataStorageRepository storageRepo, InfraTaskScheduleRepository schedRepo, AuditService audit) {
        this.dsRepo = dsRepo;
        this.storageRepo = storageRepo;
        this.schedRepo = schedRepo;
        this.audit = audit;
    }

    // Data sources
    @GetMapping("/data-sources")
    public ApiResponse<List<InfraDataSource>> listDataSources() {
        var list = dsRepo.findAll();
        audit.audit("READ", "infra.dataSource", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/data-sources")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<InfraDataSource> createDataSource(@Valid @RequestBody InfraDataSource ds) {
        var saved = dsRepo.save(ds);
        audit.audit("CREATE", "infra.dataSource", String.valueOf(saved.getId()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<InfraDataSource> updateDataSource(@PathVariable UUID id, @Valid @RequestBody InfraDataSource patch) {
        var existing = dsRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setType(patch.getType());
        existing.setJdbcUrl(patch.getJdbcUrl());
        existing.setUsername(patch.getUsername());
        existing.setProps(patch.getProps());
        existing.setDescription(patch.getDescription());
        var saved = dsRepo.save(existing);
        audit.audit("UPDATE", "infra.dataSource", String.valueOf(id));
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/data-sources/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<Boolean> deleteDataSource(@PathVariable UUID id) {
        dsRepo.deleteById(id);
        audit.audit("DELETE", "infra.dataSource", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Data storages
    @GetMapping("/data-storages")
    public ApiResponse<List<InfraDataStorage>> listDataStorages() {
        var list = storageRepo.findAll();
        audit.audit("READ", "infra.dataStorage", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/data-storages")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<InfraDataStorage> createDataStorage(@Valid @RequestBody InfraDataStorage st) {
        var saved = storageRepo.save(st);
        audit.audit("CREATE", "infra.dataStorage", String.valueOf(saved.getId()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/data-storages/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<InfraDataStorage> updateDataStorage(@PathVariable UUID id, @Valid @RequestBody InfraDataStorage patch) {
        var existing = storageRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setType(patch.getType());
        existing.setLocation(patch.getLocation());
        existing.setProps(patch.getProps());
        existing.setDescription(patch.getDescription());
        var saved = storageRepo.save(existing);
        audit.audit("UPDATE", "infra.dataStorage", String.valueOf(id));
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/data-storages/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<Boolean> deleteDataStorage(@PathVariable UUID id) {
        storageRepo.deleteById(id);
        audit.audit("DELETE", "infra.dataStorage", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Task schedules
    @GetMapping("/schedules")
    public ApiResponse<List<InfraTaskSchedule>> listSchedules() {
        var list = schedRepo.findAll();
        audit.audit("READ", "infra.schedule", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<InfraTaskSchedule> createSchedule(@Valid @RequestBody InfraTaskSchedule sc) {
        var saved = schedRepo.save(sc);
        audit.audit("CREATE", "infra.schedule", String.valueOf(saved.getId()));
        return ApiResponses.ok(saved);
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.CATALOG_ADMIN + "')")
    public ApiResponse<Boolean> deleteSchedule(@PathVariable UUID id) {
        schedRepo.deleteById(id);
        audit.audit("DELETE", "infra.schedule", String.valueOf(id));
        return ApiResponses.ok(Boolean.TRUE);
    }
}

