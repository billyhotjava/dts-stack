package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.DataProductService;
import com.yuzhi.dts.platform.service.services.dto.DataProductDetailDto;
import com.yuzhi.dts.platform.service.services.dto.DataProductSummaryDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/products")
public class DataProductsResource {

    private final DataProductService dataProductService;
    private final AuditService auditService;

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
        auditService.audit("READ", "svc.dataProduct", "list");
        return ApiResponses.ok(items);
    }

    @GetMapping("/{id}")
    public ApiResponse<DataProductDetailDto> detail(@PathVariable UUID id) {
        DataProductDetailDto dto = dataProductService.detail(id);
        auditService.audit("READ", "svc.dataProduct", id.toString());
        return ApiResponses.ok(dto);
    }
}
