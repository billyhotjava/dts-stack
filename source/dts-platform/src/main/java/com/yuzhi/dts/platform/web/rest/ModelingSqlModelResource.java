package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/sql-models")
@Transactional
public class ModelingSqlModelResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelingSqlModelService sqlModelService;
    private final AuditService auditService;
    private final DataStandardSecurity security;

    public ModelingSqlModelResource(ModelingSqlModelService sqlModelService, AuditService auditService, DataStandardSecurity security) {
        this.sqlModelService = sqlModelService;
        this.auditService = auditService;
        this.security = security;
    }

    @GetMapping
    public ApiResponse<List<SqlModelDto>> list(
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<SqlModelDto> list = sqlModelService.list(planId, keyword, activeDept);
        auditService.audit("READ", "modeling.sql-model", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<SqlModelDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.get(id, activeDept);
        auditService.audit("READ", "modeling.sql-model", id.toString());
        return ApiResponses.ok(dto);
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> create(
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.create(request, activeDept);
        auditService.audit("CREATE", "modeling.sql-model", dto.id().toString());
        return ApiResponses.ok(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.update(id, request, activeDept);
        auditService.audit("UPDATE", "modeling.sql-model", id.toString());
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        sqlModelService.delete(id, activeDept);
        auditService.audit("DELETE", "modeling.sql-model", id.toString());
        return ApiResponses.ok(null);
    }
}
