package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.services.DataProductService;
import com.yuzhi.dts.platform.service.services.dto.DataProductDetailDto;
import com.yuzhi.dts.platform.service.services.dto.DataProductSummaryDto;
import com.yuzhi.dts.platform.service.services.dto.DataProductUpsertRequest;
import com.yuzhi.dts.platform.service.services.dto.DataProductVersionDto;
import com.yuzhi.dts.platform.service.services.dto.DataProductVersionRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/products")
public class DataProductsResource {

    private final DataProductService dataProductService;
    private final AuditService auditService;
    private static final String SERVICES_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).SERVICE_MAINTAINERS)";

    public DataProductsResource(DataProductService dataProductService, AuditService auditService) {
        this.dataProductService = dataProductService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<DataProductSummaryDto>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        List<DataProductSummaryDto> items = dataProductService.list(keyword, type, status);
        auditService.auditAction("SVC_DATAPRODUCT_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(items);
    }

    @GetMapping("/{id}")
    public ApiResponse<DataProductDetailDto> detail(@PathVariable UUID id) {
        DataProductDetailDto dto = dataProductService.detail(id);
        auditService.auditAction("SVC_DATAPRODUCT_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(dto);
    }

    @PostMapping
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<DataProductDetailDto> create(@RequestBody DataProductUpsertRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        DataProductDetailDto dto = dataProductService.create(request, user);
        auditService.auditAction("SERVICE_PRODUCT_CREATE", AuditStage.SUCCESS, dto.id() != null ? dto.id().toString() : "create", Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<DataProductDetailDto> update(@PathVariable UUID id, @RequestBody DataProductUpsertRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        DataProductDetailDto dto = dataProductService.update(id, request, user);
        auditService.auditAction("SERVICE_PRODUCT_EDIT", AuditStage.SUCCESS, id.toString(), Map.of("name", dto.name()));
        return ApiResponses.ok(dto);
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<DataProductVersionDto> addVersion(@PathVariable UUID id, @RequestBody DataProductVersionRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        DataProductVersionDto dto = dataProductService.addVersion(id, request, user);
        auditService.auditAction(
            "SERVICE_PRODUCT_VERSION_CREATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("version", dto.version(), "status", dto.status())
        );
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SERVICES_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("system");
        dataProductService.delete(id, user);
        auditService.auditAction("SERVICE_PRODUCT_DELETE", AuditStage.SUCCESS, id.toString(), Map.of());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
