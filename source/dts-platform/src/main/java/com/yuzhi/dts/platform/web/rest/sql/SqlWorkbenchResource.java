package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.service.sql.SqlCatalogService;
import com.yuzhi.dts.platform.service.sql.SqlExecutionService;
import com.yuzhi.dts.platform.service.sql.SqlValidationService;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNode;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlStatusResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlWorkbenchResource {

    private final SqlCatalogService catalogService;
    private final SqlValidationService validationService;
    private final SqlExecutionService executionService;

    public SqlWorkbenchResource(
        SqlCatalogService catalogService,
        SqlValidationService validationService,
        SqlExecutionService executionService
    ) {
        this.catalogService = catalogService;
        this.validationService = validationService;
        this.executionService = executionService;
    }

    @PostMapping("/catalog")
    public ApiResponse<SqlCatalogNode> catalog(@RequestBody SqlCatalogRequest request, Principal principal) {
        return ApiResponses.ok(catalogService.fetchTree(request, principal));
    }

    @PostMapping("/validate")
    public ApiResponse<SqlValidateResponse> validate(@RequestBody SqlValidateRequest request, Principal principal) {
        return ApiResponses.ok(validationService.validate(request, principal));
    }

    @PostMapping("/submit")
    public ApiResponse<SqlSubmitResponse> submit(@RequestBody SqlSubmitRequest request, Principal principal) {
        return ApiResponses.ok(executionService.submit(request, principal));
    }

    @GetMapping("/status/{id}")
    public ApiResponse<SqlStatusResponse> status(@PathVariable UUID id) {
        return ApiResponses.ok(executionService.status(id));
    }

    @PostMapping("/cancel/{id}")
    public ApiResponse<Boolean> cancel(@PathVariable UUID id, Principal principal) {
        executionService.cancel(id, principal);
        return ApiResponses.ok(Boolean.TRUE);
    }
}
