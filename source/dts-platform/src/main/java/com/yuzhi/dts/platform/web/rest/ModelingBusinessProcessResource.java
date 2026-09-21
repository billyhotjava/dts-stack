package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION;
import static com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService.AuditSurface.CANONICAL;

import com.yuzhi.dts.platform.service.modeling.BusinessProcessApplicationService;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessDto;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessRequest;
import com.yuzhi.dts.platform.service.sprint64.Sprint64GovernanceService.BusinessProcessUpdateRequest;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical REST boundary for planning business processes. The legacy sprint-numbered endpoints
 * and this resource share one application command boundary and one existing ledger.
 */
@RestController
@RequestMapping("/api/modeling/business-processes")
public class ModelingBusinessProcessResource {

    private final BusinessProcessApplicationService service;

    public ModelingBusinessProcessResource(BusinessProcessApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<BusinessProcessDto>> list(@RequestParam UUID domainId) {
        return ApiResponses.ok(service.list(domainId, CANONICAL));
    }

    @PostMapping
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<BusinessProcessDto> create(
        @RequestParam UUID domainId,
        @RequestBody BusinessProcessRequest request
    ) {
        return ApiResponses.ok(service.create(domainId, request, CANONICAL));
    }

    @PutMapping("/{processId}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<BusinessProcessDto> update(
        @RequestParam UUID domainId,
        @PathVariable String processId,
        @RequestBody BusinessProcessUpdateRequest request
    ) {
        return ApiResponses.ok(service.update(domainId, processId, request));
    }

    @DeleteMapping("/{processId}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<Boolean> delete(
        @RequestParam UUID domainId,
        @PathVariable String processId
    ) {
        service.delete(domainId, processId, CANONICAL);
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
