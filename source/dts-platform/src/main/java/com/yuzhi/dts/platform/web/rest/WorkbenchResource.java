package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.workbench.WorkbenchService;
import com.yuzhi.dts.platform.domain.portal.PortalUserFavorite;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench")
@Transactional
public class WorkbenchResource {

    private final WorkbenchService workbenchService;
    private final AuditService auditService;

    public WorkbenchResource(WorkbenchService workbenchService, AuditService auditService) {
        this.workbenchService = workbenchService;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        Map<String, Object> payload = workbenchService.overview(user);
        auditService.audit("READ", "workbench.overview", user);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/todos")
    public ApiResponse<List<Map<String, Object>>> todos(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> items = workbenchService.todoItems(activeDept);
        auditService.audit("READ", "workbench.todos", "count=" + items.size());
        return ApiResponses.ok(items);
    }

    @GetMapping("/favorites")
    public ApiResponse<List<PortalUserFavorite>> favorites() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<PortalUserFavorite> list = workbenchService.listFavorites(user);
        auditService.audit("READ", "workbench.favorites", "count=" + list.size());
        return ApiResponses.ok(list);
    }

    @PostMapping("/favorites")
    public ApiResponse<PortalUserFavorite> createFavorite(@RequestBody WorkbenchService.FavoriteRequest request) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        PortalUserFavorite saved = workbenchService.createFavorite(user, request);
        auditService.audit("CREATE", "workbench.favorites", saved.getId() != null ? saved.getId().toString() : "create");
        return ApiResponses.ok(saved);
    }

    @PutMapping("/favorites/{id}")
    public ApiResponse<PortalUserFavorite> updateFavorite(
        @PathVariable UUID id,
        @RequestBody WorkbenchService.FavoriteRequest request
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        PortalUserFavorite saved = workbenchService.updateFavorite(id, user, request);
        auditService.audit("UPDATE", "workbench.favorites", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/favorites/{id}")
    public ApiResponse<Boolean> deleteFavorite(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        workbenchService.deleteFavorite(id, user);
        auditService.audit("DELETE", "workbench.favorites", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
