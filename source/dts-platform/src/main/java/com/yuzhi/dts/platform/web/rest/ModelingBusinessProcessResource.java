package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical REST boundary for planning business processes. Delegates to the existing
 * {@link Sprint64GovernanceService} ledger (no parallel implementation); the legacy
 * sprint-numbered endpoints stay for compatibility.
 */
@RestController
@RequestMapping("/api/modeling/business-processes")
public class ModelingBusinessProcessResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private final Sprint64GovernanceService service;
    private final AuditService audit;

    public ModelingBusinessProcessResource(Sprint64GovernanceService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<BusinessProcessDto>> list(@RequestParam UUID domainId) {
        List<BusinessProcessDto> data = service.listProcesses(domainId);
        audit.auditAction("MODELING_BUSINESS_PROCESS_LIST", AuditStage.SUCCESS, domainId.toString(), Map.of("count", data.size()));
        return ApiResponses.ok(data);
    }

    @PostMapping
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<BusinessProcessDto> create(
        @RequestParam UUID domainId,
        @RequestBody BusinessProcessRequest request
    ) {
        BusinessProcessDto data = service.createProcess(domainId, request);
        audit.auditAction("MODELING_BUSINESS_PROCESS_CREATE", AuditStage.SUCCESS, data.processId(), Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(data);
    }

    @DeleteMapping("/{processId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(
        @RequestParam UUID domainId,
        @PathVariable String processId
    ) {
        service.deleteProcess(domainId, processId);
        audit.auditAction("MODELING_BUSINESS_PROCESS_DELETE", AuditStage.SUCCESS, processId, Map.of("domainId", domainId.toString()));
        return ApiResponses.ok(true);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidProcess(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "BUSINESS_PROCESS_INVALID", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateProcess(DataIntegrityViolationException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), "业务过程已存在，请勿重复创建", "BUSINESS_PROCESS_DUPLICATE", null));
    }
}
