package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusMatrixLinkDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusMatrixRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.GrainValidationRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/sprint64")
@Transactional
public class Sprint64GovernanceResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS, T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final Sprint64GovernanceService service;
    private final AuditService audit;

    public Sprint64GovernanceResource(Sprint64GovernanceService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/domains/{domainId}/processes")
    @Transactional(readOnly = true)
    public ApiResponse<List<BusinessProcessDto>> listProcesses(@PathVariable UUID domainId) {
        List<BusinessProcessDto> data = service.listProcesses(domainId);
        audit.auditAction("SPRINT64_PROCESS_LIST", AuditStage.SUCCESS, domainId.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping("/domains/{domainId}/processes")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<BusinessProcessDto> createProcess(@PathVariable UUID domainId, @RequestBody BusinessProcessRequest request) {
        BusinessProcessDto data = service.createProcess(domainId, request);
        audit.auditAction("SPRINT64_PROCESS_CREATE", AuditStage.SUCCESS, data.processId(), Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(data);
    }

    @DeleteMapping("/domains/{domainId}/processes/{processId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteProcess(@PathVariable UUID domainId, @PathVariable String processId) {
        service.deleteProcess(domainId, processId);
        audit.auditAction("SPRINT64_PROCESS_DELETE", AuditStage.SUCCESS, processId, Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/warehouse-layers")
    @Transactional(readOnly = true)
    public ApiResponse<List<com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract.WarehouseLayerDto>> listWarehouseLayers() {
        return ApiResponses.ok(service.listWarehouseLayers());
    }

    @GetMapping("/domains/{domainId}/conformed-dimensions")
    @Transactional(readOnly = true)
    public ApiResponse<List<com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract.ConformedDimensionDto>> listConformedDimensions(@PathVariable UUID domainId) {
        return ApiResponses.ok(service.listConformedDimensions(domainId));
    }

    @GetMapping("/domains/{domainId}/bus-matrix")
    @Transactional(readOnly = true)
    public ApiResponse<List<BusMatrixLinkDto>> listBusMatrix(@PathVariable UUID domainId) {
        return ApiResponses.ok(service.listBusMatrix(domainId));
    }

    @PutMapping("/domains/{domainId}/bus-matrix")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<BusMatrixLinkDto> saveBusMatrix(@PathVariable UUID domainId, @RequestBody BusMatrixRequest request) {
        return ApiResponses.ok(service.saveBusMatrix(domainId, request));
    }

    @PostMapping("/grain/validate")
    @Transactional(readOnly = true)
    public ApiResponse<com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract.GrainValidation> validateGrain(@RequestBody GrainValidationRequest request) {
        return ApiResponses.ok(service.validateGrain(request));
    }
}
