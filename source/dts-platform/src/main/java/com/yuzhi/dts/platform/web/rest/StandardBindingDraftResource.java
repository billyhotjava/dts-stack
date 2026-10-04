package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.StandardBindingDraftService;
import com.yuzhi.dts.platform.service.modeling.StandardBindingDraftService.StandardBindingDraftDto;
import com.yuzhi.dts.platform.service.modeling.StandardBindingDraftService.StandardBindingDraftRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modeling/standard-binding-drafts")
public class StandardBindingDraftResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final StandardBindingDraftService service;
    private final AuditService auditService;

    public StandardBindingDraftResource(StandardBindingDraftService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<StandardBindingDraftDto> create(@Valid @RequestBody StandardBindingDraftRequest request) {
        StandardBindingDraftDto dto = service.create(request);
        auditService.auditAction(
            "MODELING_STANDARD_BINDING_DRAFT_CREATE",
            AuditStage.SUCCESS,
            dto.id().toString(),
            Map.of("summary", "生成字段落标草稿", "fieldCount", dto.fieldCount())
        );
        return ApiResponses.ok(dto);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<StandardBindingDraftDto> get(@PathVariable UUID id) {
        StandardBindingDraftDto dto = service.get(id);
        auditService.auditAction(
            "MODELING_STANDARD_BINDING_DRAFT_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "读取字段落标草稿", "fieldCount", dto.fieldCount())
        );
        return ApiResponses.ok(dto);
    }
}
