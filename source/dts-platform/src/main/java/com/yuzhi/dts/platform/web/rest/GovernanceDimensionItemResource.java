package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.DimensionItemService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@RequestMapping("/api/governance/dimensions")
@Transactional
public class GovernanceDimensionItemResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";

    private final DimensionItemService dimensionItems;
    private final AuditService audit;

    public GovernanceDimensionItemResource(DimensionItemService dimensionItems, AuditService audit) {
        this.dimensionItems = dimensionItems;
        this.audit = audit;
    }

    @GetMapping("/{dimensionId}/items")
    public ApiResponse<List<Map<String, Object>>> list(
        @PathVariable UUID dimensionId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> list = dimensionItems.list(dimensionId, keyword, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看维度字典项列表");
        auditPayload.put("dimensionId", dimensionId.toString());
        auditPayload.put("count", list.size());
        if (StringUtils.hasText(keyword)) auditPayload.put("keyword", keyword.trim());
        audit.recordAuxiliary("READ", "governance.dimension.item", "governance.dimension.item", "LIST", "SUCCESS", auditPayload);
        return ApiResponses.ok(list);
    }

    @GetMapping("/{dimensionId}/items/tree")
    public ApiResponse<List<Map<String, Object>>> tree(
        @PathVariable UUID dimensionId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> tree = dimensionItems.tree(dimensionId, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看维度字典层级树");
        auditPayload.put("dimensionId", dimensionId.toString());
        audit.recordAuxiliary("READ", "governance.dimension.item", "governance.dimension.item", "TREE", "SUCCESS", auditPayload);
        return ApiResponses.ok(tree);
    }

    @PostMapping("/{dimensionId}/items")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> create(
        @PathVariable UUID dimensionId,
        @RequestBody DimensionItemService.DimensionItemUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> saved = dimensionItems.create(dimensionId, activeDept, request);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "新增维度字典项");
        auditPayload.put("dimensionId", dimensionId.toString());
        audit.recordAs(currentUser(), "WRITE", "governance.dimension.item", "governance.dimension.item", "CREATE", "SUCCESS", auditPayload, null);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/{dimensionId}/items/{itemId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> update(
        @PathVariable UUID dimensionId,
        @PathVariable UUID itemId,
        @RequestBody DimensionItemService.DimensionItemUpsertRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> saved = dimensionItems.update(dimensionId, itemId, activeDept, request);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新维度字典项");
        auditPayload.put("dimensionId", dimensionId.toString());
        auditPayload.put("itemId", itemId.toString());
        audit.recordAs(currentUser(), "WRITE", "governance.dimension.item", "governance.dimension.item", itemId.toString(), "SUCCESS", auditPayload, null);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{dimensionId}/items/{itemId}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(
        @PathVariable UUID dimensionId,
        @PathVariable UUID itemId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        dimensionItems.delete(dimensionId, itemId, activeDept);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "删除维度字典项");
        auditPayload.put("dimensionId", dimensionId.toString());
        auditPayload.put("itemId", itemId.toString());
        audit.recordAs(currentUser(), "WRITE", "governance.dimension.item", "governance.dimension.item", itemId.toString(), "SUCCESS", auditPayload, null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    private String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
