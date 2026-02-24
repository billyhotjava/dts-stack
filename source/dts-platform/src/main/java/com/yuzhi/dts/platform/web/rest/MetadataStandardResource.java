package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardFilter;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardImportService;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardService;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardImportResultDto;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modeling/metadata-standards")
@Transactional
public class MetadataStandardResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final MetadataStandardService service;
    private final MetadataStandardImportService importService;
    private final AuditService audit;

    public MetadataStandardResource(MetadataStandardService service, MetadataStandardImportService importService, AuditService audit) {
        this.service = service;
        this.importService = importService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String dataType,
        @RequestParam(required = false) String sourceSystem
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        MetadataStandardFilter filter = new MetadataStandardFilter();
        filter.setKeyword(keyword);
        filter.setDomain(domain);
        filter.setDataType(dataType);
        filter.setSourceSystem(sourceSystem);

        Page<MetadataStandardDto> result = service.list(filter, pageable);
        Map<String, Object> payload = Map.of(
            "content",
            result.getContent(),
            "total",
            result.getTotalElements(),
            "page",
            result.getNumber(),
            "size",
            result.getSize()
        );
        audit.auditAction("MODELING_METADATA_STANDARD_LIST", AuditStage.SUCCESS, "page=" + page, Map.of("summary", "查看元数据标准列表"));
        return ApiResponses.ok(payload);
    }

    @GetMapping("/{id}")
    public ApiResponse<MetadataStandardDto> get(@PathVariable UUID id) {
        MetadataStandardDto dto = service.get(id);
        audit.auditAction("MODELING_METADATA_STANDARD_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看元数据标准详情"));
        return ApiResponses.ok(dto);
    }

    @GetMapping("/{id}/references")
    public ApiResponse<Map<String, Object>> references(@PathVariable UUID id) {
        Map<String, Object> payload = service.references(id);
        int impactCount = payload.get("totalReferences") instanceof Number number ? number.intValue() : 0;
        audit.auditAction(
            "MODELING_METADATA_STANDARD_REFERENCE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看元数据标准引用关系", "impactCount", impactCount)
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetadataStandardDto> create(@Valid @RequestBody MetadataStandardUpsertRequest request) {
        MetadataStandardDto dto = service.create(request);
        audit.auditAction("MODELING_METADATA_STANDARD_EDIT", AuditStage.SUCCESS, dto.getId().toString(), Map.of("summary", "新建元数据标准"));
        return ApiResponses.ok(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetadataStandardDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody MetadataStandardUpsertRequest request
    ) {
        MetadataStandardDto dto = service.update(id, request);
        audit.auditAction("MODELING_METADATA_STANDARD_EDIT", AuditStage.SUCCESS, id.toString(), Map.of("summary", "更新元数据标准"));
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        Map<String, Object> references = service.references(id);
        int impactCount = references.get("totalReferences") instanceof Number number ? number.intValue() : 0;
        service.delete(id);
        audit.auditAction(
            "MODELING_METADATA_STANDARD_DELETE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "删除元数据标准", "impactCount", impactCount)
        );
        return ApiResponses.ok(null);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<MetadataStandardImportResultDto> importMetadataStandards(@RequestPart("file") MultipartFile file) {
        MetadataStandardImportResultDto result = importService.importCsv(file);
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>();
        auditPayload.put("summary", "导入元数据标准");
        auditPayload.put("totalRows", result.getTotalRows());
        auditPayload.put("created", result.getCreated());
        auditPayload.put("updated", result.getUpdated());
        auditPayload.put("skipped", result.getSkipped());
        auditPayload.put("errorCount", result.getErrors() != null ? result.getErrors().size() : 0);
        audit.auditAction("MODELING_METADATA_STANDARD_IMPORT", AuditStage.SUCCESS, "import", auditPayload);
        return ApiResponses.ok(result);
    }
}
