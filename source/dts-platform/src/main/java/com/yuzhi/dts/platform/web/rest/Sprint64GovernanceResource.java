package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION;
import static com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService.AuditSurface.LEGACY;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusMatrixLinkDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusMatrixRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ConformedDimensionRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.DimensionInUseException;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.GrainValidationRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.ModelingCandidateConfirmationResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/governance/sprint64")
@Transactional
public class Sprint64GovernanceResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private final Sprint64GovernanceService service;
    private final AuditService audit;
    private final BusinessProcessApplicationService businessProcesses;

    public Sprint64GovernanceResource(
        Sprint64GovernanceService service,
        AuditService audit,
        BusinessProcessApplicationService businessProcesses
    ) {
        this.service = service;
        this.audit = audit;
        this.businessProcesses = businessProcesses;
    }

    @GetMapping("/domains/{domainId}/processes")
    @Transactional(readOnly = true)
    public ApiResponse<List<BusinessProcessDto>> listProcesses(@PathVariable UUID domainId) {
        return ApiResponses.ok(businessProcesses.list(domainId, LEGACY));
    }

    @PostMapping("/domains/{domainId}/processes")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<BusinessProcessDto> createProcess(@PathVariable UUID domainId, @RequestBody BusinessProcessRequest request) {
        return ApiResponses.ok(businessProcesses.create(domainId, request, LEGACY));
    }

    @DeleteMapping("/domains/{domainId}/processes/{processId}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<Boolean> deleteProcess(@PathVariable UUID domainId, @PathVariable String processId) {
        businessProcesses.delete(domainId, processId, LEGACY);
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

    @PostMapping("/domains/{domainId}/conformed-dimensions")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceContract.ConformedDimensionDto> createConformedDimension(
        @PathVariable UUID domainId,
        @RequestBody ConformedDimensionRequest request
    ) {
        var data = service.createConformedDimension(domainId, request);
        audit.auditAction("SPRINT64_DIMENSION_CREATE", AuditStage.SUCCESS, data.dimensionId(), Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(data);
    }

    @DeleteMapping("/domains/{domainId}/conformed-dimensions/{dimensionId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteConformedDimension(@PathVariable UUID domainId, @PathVariable String dimensionId) {
        try {
            service.deleteConformedDimension(domainId, dimensionId);
        } catch (DimensionInUseException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        audit.auditAction("SPRINT64_DIMENSION_DELETE", AuditStage.SUCCESS, dimensionId, Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/domains/{domainId}/modeling-candidates/confirm")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingCandidateConfirmationResult> confirmModelingCandidates(
        @PathVariable UUID domainId,
        @RequestBody ModelingCandidateConfirmationRequest request
    ) {
        var data = service.confirmModelingCandidates(domainId, request);
        audit.auditAction(
            "SPRINT64_MODELING_CANDIDATES_CONFIRM",
            AuditStage.SUCCESS,
            domainId.toString(),
            Map.of("confirmedProcesses", data.confirmedProcesses(), "confirmedDimensions", data.confirmedDimensions())
        );
        return ApiResponses.ok(data);
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
