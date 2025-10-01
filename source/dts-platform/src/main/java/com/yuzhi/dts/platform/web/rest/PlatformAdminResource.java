package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.menu.PortalMenuService;
import com.yuzhi.dts.platform.service.menu.PortalMenuService.PortalMenuTreeNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.*;

/**
 * Minimal admin endpoints consumed by the platform-webapp admin views.
 * Only implements read of portal menus for now, backed by in-memory data.
 */
@RestController
@RequestMapping("/api/admin")
public class PlatformAdminResource {

    private final PortalMenuService portalMenuService;

    public PlatformAdminResource(PortalMenuService portalMenuService) {
        this.portalMenuService = portalMenuService;
    }

    /**
     * Return portal menu tree for admin view. Nested structure with children[] matching PortalMenuItem.
     */
    @GetMapping("/portal/menus")
    public ApiResponse<List<PortalMenuTreeNode>> portalMenus() {
        return ApiResponses.ok(portalMenuService.getMenuTreeView());
    }

    // ---- Admin whoami ----
    @GetMapping("/whoami")
    public ApiResponse<Map<String, Object>> whoami() {
        return ApiResponses.ok(Map.of("allowed", Boolean.TRUE, "role", "SYSADMIN", "username", "oadmin", "email", "oadmin@example.com"));
    }

    // ---- Change Requests (in-memory) ----
    private final java.util.concurrent.atomic.AtomicLong crSeq = new java.util.concurrent.atomic.AtomicLong(1);
    private final java.util.List<java.util.Map<String, Object>> changeRequests = new java.util.concurrent.CopyOnWriteArrayList<>();

    @GetMapping("/change-requests")
    public ApiResponse<List<Map<String, Object>>> listChangeRequests(@RequestParam(required = false) String status, @RequestParam(required = false) String type) {
        List<Map<String, Object>> out = changeRequests
            .stream()
            .filter(cr -> status == null || status.equals(cr.get("status")))
            .filter(cr -> type == null || type.equals(cr.get("resourceType")))
            .toList();
        return ApiResponses.ok(out);
    }

    @GetMapping("/change-requests/mine")
    public ApiResponse<List<Map<String, Object>>> myChangeRequests() { return ApiResponses.ok(changeRequests); }

    @PostMapping("/change-requests")
    public ApiResponse<Map<String, Object>> createChangeRequest(@RequestBody Map<String, Object> payload) {
        long id = crSeq.getAndIncrement();
        Map<String, Object> cr = new LinkedHashMap<>();
        cr.put("id", id);
        cr.put("resourceType", payload.getOrDefault("resourceType", "GENERIC"));
        cr.put("resourceId", payload.getOrDefault("resourceId", null));
        cr.put("action", payload.getOrDefault("action", "CREATE"));
        cr.put("payloadJson", String.valueOf(payload.getOrDefault("payloadJson", "{}")));
        cr.put("diffJson", String.valueOf(payload.getOrDefault("diffJson", "{}")));
        cr.put("status", "DRAFT");
        cr.put("requestedBy", "oadmin");
        cr.put("requestedAt", java.time.Instant.now().toString());
        changeRequests.add(cr);
        return ApiResponses.ok(cr);
    }

    @PostMapping("/change-requests/{id}/submit")
    public ApiResponse<Map<String, Object>> submitChangeRequest(@PathVariable long id) { return ApiResponses.ok(updateCrStatus(id, "SUBMITTED")); }

    @PostMapping("/change-requests/{id}/approve")
    public ApiResponse<Map<String, Object>> approveChangeRequest(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) { return ApiResponses.ok(updateCrStatus(id, "APPROVED")); }

    @PostMapping("/change-requests/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectChangeRequest(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) { return ApiResponses.ok(updateCrStatus(id, "REJECTED")); }

    private Map<String, Object> updateCrStatus(long id, String status) {
        for (Map<String, Object> cr : changeRequests) {
            if (Long.valueOf(String.valueOf(cr.get("id"))) == id) {
                cr.put("status", status);
                cr.put("decidedAt", java.time.Instant.now().toString());
                return cr;
            }
        }
        return Map.of();
    }

    // ---- Admin audit ----
    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> adminAudit(@RequestParam Map<String, String> params) {
        return ApiResponses.ok(
            List.of(
                Map.of(
                    "id",
                    1,
                    "timestamp",
                    java.time.Instant.now().toString(),
                    "actor",
                    "oadmin",
                    "actorRoles",
                    "SYSADMIN",
                    "ip",
                    "127.0.0.1",
                    "action",
                    "LOGIN",
                    "resource",
                    "ADMIN_PORTAL",
                    "outcome",
                    "SUCCESS",
                    "detailJson",
                    "{}"
                )
            )
        );
    }

    // ---- System Config ----
    private final java.util.concurrent.atomic.AtomicLong cfgSeq = new java.util.concurrent.atomic.AtomicLong(1);
    private final java.util.List<java.util.Map<String, Object>> systemConfig = new java.util.concurrent.CopyOnWriteArrayList<>(
        java.util.List.of(
            new java.util.LinkedHashMap<>(java.util.Map.of("id", 1, "key", "portal.title", "value", "数据管理平台", "description", "门户标题")),
            new java.util.LinkedHashMap<>(java.util.Map.of("id", 2, "key", "portal.theme", "value", "light", "description", "默认主题"))
        )
    );

    @GetMapping("/system/config")
    public ApiResponse<List<Map<String, Object>>> systemConfigList() { return ApiResponses.ok(systemConfig); }

    @PostMapping("/system/config")
    public ApiResponse<Map<String, Object>> draftSystemConfig(@RequestBody Map<String, Object> cfg) {
        // 保存为变更请求
        Map<String, Object> cr = new LinkedHashMap<>();
        long id = crSeq.getAndIncrement();
        cr.put("id", id);
        cr.put("resourceType", "SYSTEM_CONFIG");
        cr.put("action", "UPDATE");
        cr.put("payloadJson", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(cfg).toString());
        cr.put("status", "DRAFT");
        cr.put("requestedBy", "oadmin");
        cr.put("requestedAt", java.time.Instant.now().toString());
        changeRequests.add(cr);
        return ApiResponses.ok(cr);
    }

    // ---- Portal menus CR endpoints ----
    @PostMapping("/portal/menus")
    public ApiResponse<Map<String, Object>> draftCreateMenu(@RequestBody Map<String, Object> menu) { return ApiResponses.ok(proxyMenuChange("CREATE", null, menu)); }

    @PutMapping("/portal/menus/{id}")
    public ApiResponse<Map<String, Object>> draftUpdateMenu(@PathVariable long id, @RequestBody Map<String, Object> menu) {
        return ApiResponses.ok(proxyMenuChange("UPDATE", id, menu));
    }

    @DeleteMapping("/portal/menus/{id}")
    public ApiResponse<Map<String, Object>> draftDeleteMenu(@PathVariable long id) {
        return ApiResponses.ok(proxyMenuChange("DELETE", id, Map.of("id", id)));
    }

    private Map<String, Object> proxyMenuChange(String action, Long id, Map<String, Object> payload) {
        Map<String, Object> result = switch (action) {
            case "CREATE" -> portalMenuService.createMenu(payload);
            case "UPDATE" -> {
                Objects.requireNonNull(id, "Menu id required for update");
                yield portalMenuService.updateMenu(id, payload);
            }
            case "DELETE" -> {
                Objects.requireNonNull(id, "Menu id required for delete");
                yield portalMenuService.deleteMenu(id);
            }
            default -> Map.of("status", "UNSUPPORTED", "action", action);
        };
        if (result != null) {
            changeRequests.add(new LinkedHashMap<>(result));
        }
        return result;
    }

    // ---- Organizations ----
    private final java.util.concurrent.atomic.AtomicLong orgSeq = new java.util.concurrent.atomic.AtomicLong(100);
    private final java.util.Map<Long, java.util.Map<String, Object>> orgs = new java.util.concurrent.ConcurrentHashMap<>();

    {
        // seed orgs (avoid Map.of for entries with null values)
        {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", 1L);
            m.put("name", "总公司");
            m.put("dataLevel", "DATA_INTERNAL");
            m.put("parentId", null);
            orgs.put(1L, m);
        }
        {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", 2L);
            m.put("name", "研发中心");
            m.put("dataLevel", "DATA_INTERNAL");
            m.put("parentId", 1L);
            orgs.put(2L, m);
        }
        {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", 3L);
            m.put("name", "安全部");
            m.put("dataLevel", "DATA_SECRET");
            m.put("parentId", 1L);
            orgs.put(3L, m);
        }
    }

    @GetMapping("/orgs")
    public ApiResponse<List<Map<String, Object>>> orgTree() {
        Map<Long, Map<String, Object>> copy = new LinkedHashMap<>();
        for (var e : orgs.entrySet()) copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        for (var n : copy.values()) n.put("children", new java.util.ArrayList<>());
        List<Map<String, Object>> roots = new ArrayList<>();
        for (var n : copy.values()) {
            Long pid = (Long) n.get("parentId");
            if (pid != null && copy.containsKey(pid)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cs = (List<Map<String, Object>>) copy.get(pid).get("children");
                cs.add(n);
            } else {
                roots.add(n);
            }
        }
        return ApiResponses.ok(roots);
    }

    public record OrgPayload(String name, String dataLevel, Long parentId, String contact, String phone, String description) {}

    @PostMapping("/orgs")
    public ApiResponse<Map<String, Object>> createOrg(@RequestBody OrgPayload p) {
        long id = orgSeq.getAndIncrement();
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("name", p.name());
        n.put("dataLevel", p.dataLevel());
        n.put("parentId", p.parentId());
        n.put("contact", p.contact());
        n.put("phone", p.phone());
        n.put("description", p.description());
        orgs.put(id, n);
        return ApiResponses.ok(n);
    }

    @PutMapping("/orgs/{id}")
    public ApiResponse<Map<String, Object>> updateOrg(@PathVariable long id, @RequestBody OrgPayload p) {
        Map<String, Object> n = orgs.get(id);
        if (n == null) return ApiResponses.ok(Map.of());
        n.put("name", p.name());
        n.put("dataLevel", p.dataLevel());
        n.put("parentId", p.parentId());
        n.put("contact", p.contact());
        n.put("phone", p.phone());
        n.put("description", p.description());
        return ApiResponses.ok(n);
    }

    @DeleteMapping("/orgs/{id}")
    public ApiResponse<Void> deleteOrg(@PathVariable long id) {
        orgs.remove(id);
        return ApiResponses.ok(null);
    }

    // ---- Admin users/roles/permissions ----
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> adminUsers() {
        return ApiResponses.ok(
            List.of(
            Map.of("id", 1, "username", "alice", "displayName", "爱丽丝", "email", "alice@example.com", "orgPath", List.of("总公司", "研发中心"), "roles", List.of("DATA_ADMIN"), "securityLevel", "DATA_INTERNAL", "status", "ACTIVE", "lastLoginAt", java.time.Instant.now().toString()),
            Map.of("id", 2, "username", "bob", "displayName", "鲍勃", "email", "bob@example.com", "orgPath", List.of("总公司", "安全部"), "roles", List.of("DATA_VIEWER"), "securityLevel", "DATA_SECRET", "status", "ACTIVE", "lastLoginAt", java.time.Instant.now().toString())
            )
        );
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> adminRoles() {
        return ApiResponses.ok(
            List.of(
            Map.of("id", 11, "name", "DATA_ADMIN", "description", "数据管理员", "securityLevel", "DATA_INTERNAL", "permissions", List.of("catalog.manage", "policy.apply"), "memberCount", 3, "approvalFlow", "4-eyes", "updatedAt", java.time.Instant.now().toString()),
            Map.of("id", 12, "name", "DATA_VIEWER", "description", "数据查看者", "securityLevel", "DATA_PUBLIC", "permissions", List.of("catalog.read"), "memberCount", 10, "approvalFlow", "none", "updatedAt", java.time.Instant.now().toString())
            )
        );
    }

    @GetMapping("/permissions/catalog")
    public ApiResponse<List<Map<String, Object>>> permissionCatalog() {
        return ApiResponses.ok(
            List.of(
            Map.of("category", "catalog", "description", "目录管理", "permissions", List.of(Map.of("code", "catalog.read", "name", "读取目录", "description", "查看元数据"), Map.of("code", "catalog.manage", "name", "管理目录", "description", "维护域/数据集"))),
            Map.of("category", "policy", "description", "权限策略", "permissions", List.of(Map.of("code", "policy.preview", "name", "预览策略", "description", "模拟可见性"), Map.of("code", "policy.apply", "name", "应用策略", "description", "批量授权")))
            )
        );
    }

}
