package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeSeedService;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.ReferenceCodeDirectoryRequest;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.ReferenceCodeItemBatchRequest;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.ReferenceCodeItemRequest;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.ReferenceCodeMappingRequest;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeMappingDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeDirectoryDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeItemDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
@RequestMapping("/api/governance/reference-codes")
@Transactional
public class GovernanceReferenceCodeResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final ReferenceCodeService referenceCodes;
    private final ReferenceCodeSeedService seedService;
    private final AuditService audit;

    public GovernanceReferenceCodeResource(
        ReferenceCodeService referenceCodes,
        ReferenceCodeSeedService seedService,
        AuditService audit
    ) {
        this.referenceCodes = referenceCodes;
        this.seedService = seedService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listDirectories(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
        Page<ReferenceCodeDirectoryDto> result = referenceCodes.listDirectories(keyword, pageable, activeDept);
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
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看公共码表列表");
        auditPayload.put("page", page);
        auditPayload.put("size", size);
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        if (StringUtils.hasText(activeDept)) auditPayload.put("activeDept", activeDept.trim());
        audit.auditAction("GOV_REFERENCE_CODE_LIST", AuditStage.SUCCESS, "LIST", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/{codeTypeId}")
    public ApiResponse<ReferenceCodeDirectoryDto> getDirectory(
        @PathVariable String codeTypeId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeDirectoryDto dto = referenceCodes.getDirectory(codeTypeId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看码表详情");
        detail.put("codeTypeId", codeTypeId);
        detail.put("codeTypeCode", dto.getCodeTypeCode());
        detail.put("codeTypeName", dto.getCodeTypeName());
        audit.auditAction("GOV_REFERENCE_CODE_VIEW", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(dto);
    }

    @PostMapping
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeDirectoryDto> createDirectory(
        @RequestBody ReferenceCodeDirectoryRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeDirectoryDto saved = referenceCodes.createDirectory(request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "创建码表：" + saved.getCodeTypeName());
        detail.put("codeTypeId", saved.getCodeTypeId());
        detail.put("codeTypeCode", saved.getCodeTypeCode());
        audit.auditAction("GOV_REFERENCE_CODE_EDIT", AuditStage.SUCCESS, saved.getCodeTypeId(), detail);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{codeTypeId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeDirectoryDto> updateDirectory(
        @PathVariable String codeTypeId,
        @RequestBody ReferenceCodeDirectoryRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeDirectoryDto saved = referenceCodes.updateDirectory(codeTypeId, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "更新码表：" + saved.getCodeTypeName());
        detail.put("codeTypeId", codeTypeId);
        detail.put("codeTypeCode", saved.getCodeTypeCode());
        audit.auditAction("GOV_REFERENCE_CODE_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{codeTypeId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDirectory(
        @PathVariable String codeTypeId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        referenceCodes.deleteDirectory(codeTypeId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "删除码表");
        detail.put("codeTypeId", codeTypeId);
        audit.auditAction("GOV_REFERENCE_CODE_DELETE", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/{codeTypeId}/items")
    public ApiResponse<List<ReferenceCodeItemDto>> listItems(
        @PathVariable String codeTypeId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<ReferenceCodeItemDto> list = referenceCodes.listItems(codeTypeId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看码表项列表");
        detail.put("codeTypeId", codeTypeId);
        audit.auditAction("GOV_REFERENCE_CODE_ITEM_LIST", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(list);
    }

    @PostMapping("/{codeTypeId}/items")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeItemDto> createItem(
        @PathVariable String codeTypeId,
        @RequestBody ReferenceCodeItemRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeItemDto saved = referenceCodes.createItem(codeTypeId, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "创建码表项");
        detail.put("codeTypeId", codeTypeId);
        detail.put("codeValue", saved.getCodeValue());
        audit.auditAction("GOV_REFERENCE_CODE_ITEM_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/{codeTypeId}/items/batch")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> batchImportItems(
        @PathVariable String codeTypeId,
        @RequestBody ReferenceCodeItemBatchRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeService.BatchImportResult result = referenceCodes.batchImportItems(codeTypeId, request, activeDept);
        Map<String, Object> payload = Map.of(
            "total",
            result.total(),
            "created",
            result.created(),
            "skipped",
            result.skipped(),
            "invalid",
            result.invalid()
        );
        Map<String, Object> detail = new LinkedHashMap<>(payload);
        detail.put("summary", "批量导入码表项");
        detail.put("codeTypeId", codeTypeId);
        audit.auditAction("GOV_REFERENCE_CODE_ITEM_IMPORT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(payload);
    }

    @PutMapping("/{codeTypeId}/items/{itemId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeItemDto> updateItem(
        @PathVariable String codeTypeId,
        @PathVariable Long itemId,
        @RequestBody ReferenceCodeItemRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeItemDto saved = referenceCodes.updateItem(codeTypeId, itemId, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "更新码表项");
        detail.put("codeTypeId", codeTypeId);
        detail.put("itemId", itemId);
        detail.put("codeValue", saved.getCodeValue());
        audit.auditAction("GOV_REFERENCE_CODE_ITEM_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{codeTypeId}/items/{itemId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteItem(
        @PathVariable String codeTypeId,
        @PathVariable Long itemId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        referenceCodes.deleteItem(codeTypeId, itemId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "删除码表项");
        detail.put("codeTypeId", codeTypeId);
        detail.put("itemId", itemId);
        audit.auditAction("GOV_REFERENCE_CODE_ITEM_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/{codeTypeId}/mappings")
    public ApiResponse<List<ReferenceCodeMappingDto>> listMappings(
        @PathVariable String codeTypeId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<ReferenceCodeMappingDto> list = referenceCodes.listMappings(codeTypeId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "查看码表映射列表");
        detail.put("codeTypeId", codeTypeId);
        audit.auditAction("GOV_REFERENCE_CODE_MAPPING_LIST", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(list);
    }

    @PostMapping("/{codeTypeId}/mappings")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeMappingDto> createMapping(
        @PathVariable String codeTypeId,
        @RequestBody ReferenceCodeMappingRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeMappingDto saved = referenceCodes.createMapping(codeTypeId, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "创建码表映射");
        detail.put("codeTypeId", codeTypeId);
        detail.put("sourceSys", saved.getSourceSys());
        detail.put("srcCode", saved.getSrcCode());
        audit.auditAction("GOV_REFERENCE_CODE_MAPPING_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{codeTypeId}/mappings/{mapId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<ReferenceCodeMappingDto> updateMapping(
        @PathVariable String codeTypeId,
        @PathVariable Long mapId,
        @RequestBody ReferenceCodeMappingRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ReferenceCodeMappingDto saved = referenceCodes.updateMapping(codeTypeId, mapId, request, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "更新码表映射");
        detail.put("codeTypeId", codeTypeId);
        detail.put("mapId", mapId);
        audit.auditAction("GOV_REFERENCE_CODE_MAPPING_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{codeTypeId}/mappings/{mapId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteMapping(
        @PathVariable String codeTypeId,
        @PathVariable Long mapId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        referenceCodes.deleteMapping(codeTypeId, mapId, activeDept);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", "删除码表映射");
        detail.put("codeTypeId", codeTypeId);
        detail.put("mapId", mapId);
        audit.auditAction("GOV_REFERENCE_CODE_MAPPING_EDIT", AuditStage.SUCCESS, codeTypeId, detail);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/seeds")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> syncSeeds(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> result = seedService.syncSeeds(activeDept);
        Map<String, Object> detail = new LinkedHashMap<>(result);
        detail.put("summary", "更新 dbt Seeds");
        audit.auditAction("GOV_REFERENCE_CODE_SEED_SYNC", AuditStage.SUCCESS, "SEEDS", detail);
        return ApiResponses.ok(result);
    }
}
