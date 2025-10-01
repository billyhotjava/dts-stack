package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.api.ResultStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.service.PortalMenuService;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.AdminDataset;
import com.yuzhi.dts.admin.domain.AdminCustomRole;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.service.notify.DtsCommonNotifyClient;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.user.AdminUserService;

@RestController
@RequestMapping("/api/admin")
@Transactional
@io.swagger.v3.oas.annotations.tags.Tag(name = "admin")
public class AdminApiResource {

    public record WhoAmI(boolean allowed, String role, String username, String email) {}

    private static boolean hasAny(Collection<? extends GrantedAuthority> auths, String... roles) {
        Set<String> set = new HashSet<>();
        for (GrantedAuthority a : auths) set.add(a.getAuthority());
        for (String r : roles) if (set.contains(r)) return true;
        return false;
    }

    @GetMapping("/whoami")
    public ResponseEntity<ApiResponse<WhoAmI>> whoami(Principal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && hasAny(auth.getAuthorities(), AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN);
        String role = auth
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(r ->
                r.equals(AuthoritiesConstants.SYS_ADMIN) ||
                r.equals(AuthoritiesConstants.AUTH_ADMIN) ||
                r.equals(AuthoritiesConstants.AUDITOR_ADMIN)
            )
            .findFirst()
            .orElse(null);
        WhoAmI payload = new WhoAmI(allowed, role, principal != null ? principal.getName() : null, null);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "WHOAMI", "ADMIN", "self", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    // --- Minimal audit endpoints ---
    private final AdminAuditService auditService;
    private final OrganizationService orgService;
    private final ChangeRequestRepository crRepo;
    private final ChangeRequestService changeRequestService;
    private final PortalMenuService portalMenuService;
    private final AdminDatasetRepository datasetRepo;
    private final AdminCustomRoleRepository customRoleRepo;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final SystemConfigRepository sysCfgRepo;
    private final PortalMenuRepository portalMenuRepo;
    private final DtsCommonNotifyClient notifyClient;
    private final OrganizationRepository organizationRepository;
    private final AdminUserService adminUserService;

    public AdminApiResource(
        AdminAuditService auditService,
        OrganizationService orgService,
        ChangeRequestRepository crRepo,
        ChangeRequestService changeRequestService,
        PortalMenuService portalMenuService,
        AdminDatasetRepository datasetRepo,
        AdminCustomRoleRepository customRoleRepo,
        AdminRoleAssignmentRepository roleAssignRepo,
        SystemConfigRepository sysCfgRepo,
        PortalMenuRepository portalMenuRepo,
        DtsCommonNotifyClient notifyClient,
        OrganizationRepository organizationRepository,
        AdminUserService adminUserService
    ) {
        this.auditService = auditService;
        this.orgService = orgService;
        this.crRepo = crRepo;
        this.changeRequestService = changeRequestService;
        this.portalMenuService = portalMenuService;
        this.datasetRepo = datasetRepo;
        this.customRoleRepo = customRoleRepo;
        this.roleAssignRepo = roleAssignRepo;
        this.sysCfgRepo = sysCfgRepo;
        this.portalMenuRepo = portalMenuRepo;
        this.notifyClient = notifyClient;
        this.organizationRepository = organizationRepository;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditEvent>>> listAudit(
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String resource,
        @RequestParam(required = false) String outcome
    ) {
        var list = auditService.list(actor, action, resource, outcome);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "AUDIT_LIST", "AUDIT", "query", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping(value = "/audit/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public void exportAudit(HttpServletResponse response) throws IOException {
        String header = "id,timestamp,actor,action,resource,outcome\n";
        StringBuilder sb = new StringBuilder(header);
        for (com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditEvent e : auditService.list(null, null, null, null)) {
                sb.append(e.id)
                    .append(',')
                    .append(e.timestamp)
                    .append(',')
                    .append(Optional.ofNullable(e.actor).orElse(""))
                    .append(',')
                    .append(Optional.ofNullable(e.action).orElse(""))
                    .append(',')
                    .append(Optional.ofNullable(e.resource).orElse(""))
                    .append(',')
                    .append(Optional.ofNullable(e.outcome).orElse(""))
                    .append('\n');
        }
        response.setContentType("text/csv");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // --- Placeholders to align with adminApi ---
    @GetMapping("/system/config")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> systemConfig() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SystemConfig c : sysCfgRepo.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("key", c.getKey());
            m.put("value", c.getValue());
            m.put("description", c.getDescription());
            list.add(m);
        }
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "SYSTEM_CONFIG_LIST", "CONFIG", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/system/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftSystemConfig(@RequestBody Map<String, Object> cfg) {
        String key = Objects.toString(cfg.get("key"), "").trim();
        if (key.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("配置项 key 不能为空"));
        }
        Map<String, Object> before = sysCfgRepo.findByKey(key).map(this::toSystemConfigMap).orElse(null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("key", key);
        after.put("value", cfg.get("value"));
        after.put("description", cfg.get("description"));
        ChangeRequest cr = changeRequestService.draft("CONFIG", "CONFIG_SET", key, after, before, Objects.toString(cfg.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "SYSTEM_CONFIG_DRAFT", "CONFIG", (String) cfg.getOrDefault("key", "config"), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/portal/menus")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> portalMenus() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PortalMenu m : portalMenuService.findTree()) out.add(toMenuVM(m));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "PORTAL_MENU_LIST", "MENU", "admin", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping("/portal/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftCreateMenu(@RequestBody Map<String, Object> body) {
        Map<String, Object> after = readPortalMenuPayload(body);
        ChangeRequest cr = changeRequestService.draft("PORTAL_MENU", "CREATE", null, after, null, Objects.toString(body.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "PORTAL_MENU_CREATE", "PORTAL_MENU", String.valueOf(body.getOrDefault("name", "menu")), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PutMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftUpdateMenu(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Long menuId = Long.valueOf(id);
        PortalMenu beforeEntity = portalMenuRepo.findById(menuId).orElse(null);
        if (beforeEntity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> before = toPortalMenuPayload(beforeEntity);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.putAll(readPortalMenuPayload(body));
        ChangeRequest cr = changeRequestService.draft("PORTAL_MENU", "UPDATE", id, after, before, Objects.toString(body.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "PORTAL_MENU_UPDATE", "PORTAL_MENU", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @DeleteMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftDeleteMenu(@PathVariable String id) {
        Long menuId = Long.valueOf(id);
        PortalMenu entity = portalMenuRepo.findById(menuId).orElse(null);
        if (entity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> before = toPortalMenuPayload(entity);
        ChangeRequest cr = changeRequestService.draft("PORTAL_MENU", "DELETE", id, Map.of("deleted", true), before, null);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "PORTAL_MENU_DELETE", "PORTAL_MENU", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> orgs() {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (OrganizationNode n : orgService.findTree()) tree.add(toOrgVM(n));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "ORG_LIST", "ORG", "tree", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @PostMapping("/orgs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrg(@RequestBody Map<String, Object> payload) {
        String name = Objects.toString(payload.get("name"), null);
        String dataLevel = Objects.toString(payload.get("dataLevel"), null);
        Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
        String contact = Objects.toString(payload.get("contact"), null);
        String phone = Objects.toString(payload.get("phone"), null);
        String description = Objects.toString(payload.get("description"), null);
        if (name == null || name.isBlank() || dataLevel == null || dataLevel.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("部门名称和数据密级不能为空"));
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", name);
        after.put("dataLevel", dataLevel);
        after.put("parentId", parentId);
        after.put("contact", contact);
        after.put("phone", phone);
        after.put("description", description);
        ChangeRequest cr = changeRequestService.draft("ORG", "CREATE", null, after, null, Objects.toString(payload.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ORG_CREATE_REQUEST", "ORG", name, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PutMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateOrg(@PathVariable int id, @RequestBody Map<String, Object> payload) {
        OrganizationNode existing = organizationRepository.findById((long) id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }
        Map<String, Object> before = toOrgPayload(existing);
        Map<String, Object> after = new LinkedHashMap<>(before);
        if (payload.containsKey("name")) after.put("name", Objects.toString(payload.get("name"), null));
        if (payload.containsKey("dataLevel")) after.put("dataLevel", Objects.toString(payload.get("dataLevel"), null));
        if (payload.containsKey("contact")) after.put("contact", Objects.toString(payload.get("contact"), null));
        if (payload.containsKey("phone")) after.put("phone", Objects.toString(payload.get("phone"), null));
        if (payload.containsKey("description")) after.put("description", Objects.toString(payload.get("description"), null));
        ChangeRequest cr = changeRequestService.draft("ORG", "UPDATE", String.valueOf(id), after, before, Objects.toString(payload.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ORG_UPDATE_REQUEST", "ORG", String.valueOf(id), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @DeleteMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteOrg(@PathVariable int id) {
        OrganizationNode existing = organizationRepository.findById((long) id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }
        Map<String, Object> before = toOrgPayload(existing);
        ChangeRequest cr = changeRequestService.draft("ORG", "DELETE", String.valueOf(id), Map.of("deleted", true), before, null);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ORG_DELETE_REQUEST", "ORG", String.valueOf(id), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AdminDataset d : datasetRepo.findAll()) out.add(toDatasetVM(d));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "DATASET_LIST", "DATASET", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/custom-roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> customRoles() {
        var list = customRoleRepo.findAll().stream().map(this::toCustomRoleVM).toList();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "CUSTOM_ROLE_LIST", "ROLE", "custom", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/custom-roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCustomRole(@RequestBody Map<String, Object> payload) {
        String name = Objects.toString(payload.get("name"), "").trim();
        if (name.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        if (Set.of("SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN").contains(name.toUpperCase())) {
            return ResponseEntity.status(409).body(ApiResponse.error("内置角色不可创建"));
        }
        if (customRoleRepo.findByName(name).isPresent()) return ResponseEntity.status(409).body(ApiResponse.error("角色名称已存在"));
        List<String> ops = readStringList(payload.get("operations"));
        if (ops.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("请选择角色权限"));
        if (!ops.stream().allMatch(o -> Set.of("read", "write", "export").contains(o))) return ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
        String scope = Objects.toString(payload.get("scope"), "");
        if (!Set.of("DEPARTMENT", "INSTITUTE").contains(scope)) return ResponseEntity.badRequest().body(ApiResponse.error("作用域无效"));
        String maxDataLevel = Objects.toString(payload.get("maxDataLevel"), "");
        if (maxDataLevel.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("请选择最大数据密级"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", name);
        after.put("scope", scope);
        after.put("operations", new LinkedHashSet<>(ops));
        after.put("maxRows", payload.get("maxRows"));
        after.put("allowDesensitizeJson", Boolean.TRUE.equals(payload.get("allowDesensitizeJson")));
        after.put("maxDataLevel", maxDataLevel);
        after.put("description", Objects.toString(payload.get("description"), null));
        ChangeRequest cr = changeRequestService.draft("CUSTOM_ROLE", "CREATE", name, after, null, Objects.toString(payload.get("reason"), null));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ROLE_CUSTOM_CREATE_REQUEST", "ROLE", name, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/role-assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roleAssignments() {
        var list = roleAssignRepo.findAll().stream().map(this::toRoleAssignmentVM).toList();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "ROLE_ASSIGNMENT_LIST", "ROLE_ASSIGNMENT", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/role-assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoleAssignment(@RequestBody Map<String, Object> payload) {
        String role = Objects.toString(payload.get("role"), "").trim();
        String username = Objects.toString(payload.get("username"), "").trim();
        String displayName = Objects.toString(payload.get("displayName"), "").trim();
        String userSecurityLevel = Objects.toString(payload.get("userSecurityLevel"), "").trim();
        Long scopeOrgId = payload.get("scopeOrgId") == null ? null : Long.valueOf(payload.get("scopeOrgId").toString());
        List<String> ops = readStringList(payload.get("operations"));
        List<Long> datasetIds = readLongList(payload.get("datasetIds"));
        String error = validateAssignment(role, username, displayName, userSecurityLevel, scopeOrgId, ops, datasetIds);
        if (error != null) return ResponseEntity.badRequest().body(ApiResponse.error(error));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("role", role);
        after.put("username", username);
        after.put("displayName", displayName);
        after.put("userSecurityLevel", userSecurityLevel);
        after.put("scopeOrgId", scopeOrgId);
        after.put("datasetIds", datasetIds);
        after.put("operations", new LinkedHashSet<>(ops));
        ChangeRequest cr = changeRequestService.draft(
            "ROLE_ASSIGNMENT",
            "CREATE",
            null,
            after,
            null,
            Objects.toString(payload.get("reason"), null)
        );
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ROLE_ASSIGNMENT_CREATE_REQUEST", "ROLE_ASSIGNMENT", username, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/change-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> changeRequests(@RequestParam(required = false) String status, @RequestParam(required = false, name = "type") String resourceType) {
        List<ChangeRequest> list;
        if (status != null && resourceType != null) list = crRepo.findByStatusAndResourceType(status, resourceType); else if (status != null) list = crRepo.findByStatus(status); else list = crRepo.findAll();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "CR_LIST", "CHANGE_REQUEST", "list", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list.stream().map(AdminApiResource::toChangeVM).toList()));
    }

    @GetMapping("/change-requests/mine")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myChangeRequests() {
        String me = SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
        var list = crRepo.findByRequestedBy(me).stream().map(AdminApiResource::toChangeVM).toList();
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "CR_LIST_MINE", "CHANGE_REQUEST", me, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/change-requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChangeRequest(@RequestBody Map<String, Object> payload) {
        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType(Objects.toString(payload.get("resourceType"), "UNKNOWN"));
        cr.setResourceId(Objects.toString(payload.get("resourceId"), null));
        cr.setAction(Objects.toString(payload.get("action"), "UNKNOWN"));
        cr.setPayloadJson(Objects.toString(payload.get("payloadJson"), null));
        cr.setDiffJson(Objects.toString(payload.get("diffJson"), null));
        cr.setStatus("DRAFT");
        cr.setRequestedBy(SecurityUtils.getCurrentUserLogin().orElse("sysadmin"));
        cr.setRequestedAt(Instant.now());
        crRepo.save(cr);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "CHANGE_REQUEST_CREATE", "CHANGE_REQUEST", String.valueOf(cr.getId()), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitChangeRequest(@PathVariable String id) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        cr.setStatus("PENDING");
        crRepo.save(cr);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "CHANGE_REQUEST_SUBMIT", "CHANGE_REQUEST", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveChangeRequest(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        cr.setStatus("APPROVED");
        cr.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse("authadmin"));
        cr.setDecidedAt(Instant.now());
        cr.setReason(body != null ? Objects.toString(body.get("reason"), null) : null);
        applyChangeRequest(cr);
        crRepo.save(cr);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "CHANGE_REQUEST_APPROVE", "CHANGE_REQUEST", id, "SUCCESS", null);
        try { notifyClient.trySend("approval_approved", Map.of("id", id, "type", cr.getResourceType(), "status", cr.getStatus())); } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectChangeRequest(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        cr.setStatus("REJECTED");
        cr.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse("authadmin"));
        cr.setDecidedAt(Instant.now());
        cr.setReason(body != null ? Objects.toString(body.get("reason"), null) : null);
        crRepo.save(cr);
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "CHANGE_REQUEST_REJECT", "CHANGE_REQUEST", id, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminRoles() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("id", 1, "name", "SYSADMIN", "memberCount", 1, "updatedAt", new Date().toInstant().toString()));
        list.add(Map.of("id", 2, "name", "AUTHADMIN", "memberCount", 1, "updatedAt", new Date().toInstant().toString()));
        list.add(Map.of("id", 3, "name", "AUDITADMIN", "memberCount", 1, "updatedAt", new Date().toInstant().toString()));
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "ADMIN_ROLES_LIST", "ADMIN", "roles", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/permissions/catalog")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> permissionCatalog() {
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(
            new LinkedHashMap<>() {
                {
                    put("category", "portal");
                    put("description", "门户菜单访问");
                    put(
                        "permissions",
                        List.of(
                            Map.of("code", "portal:read", "name", "访问门户", "description", "允许进入门户主页"),
                            Map.of("code", "portal:dataset:read", "name", "查看数据目录", "description", "浏览数据目录与资产")
                        )
                    );
                }
            }
        );
        sections.add(
            new LinkedHashMap<>() {
                {
                    put("category", "admin");
                    put("description", "管理端权限");
                    put(
                        "permissions",
                        List.of(
                            Map.of("code", "admin:user:manage", "name", "用户管理"),
                            Map.of("code", "admin:role:manage", "name", "角色管理"),
                            Map.of("code", "admin:org:manage", "name", "组织管理")
                        )
                    );
                }
            }
        );
        auditService.record(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), "PERMISSION_CATALOG_LIST", "ADMIN", "permissions", "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.ok(sections));
    }

    private static Map<String, Object> toOrgVM(OrganizationNode e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("dataLevel", e.getDataLevel());
        m.put("sensitivity", e.getDataLevel());
        m.put("parentId", e.getParent() != null ? e.getParent().getId() : null);
        m.put("contact", e.getContact());
        m.put("phone", e.getPhone());
        m.put("description", e.getDescription());
        if (e.getChildren() != null && !e.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (OrganizationNode c : e.getChildren()) children.add(toOrgVM(c));
            m.put("children", children);
        }
        return m;
    }

    private Map<String, Object> toOrgPayload(OrganizationNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", node.getId());
        m.put("name", node.getName());
        m.put("dataLevel", node.getDataLevel());
        m.put("parentId", node.getParent() != null ? node.getParent().getId() : null);
        m.put("contact", node.getContact());
        m.put("phone", node.getPhone());
        m.put("description", node.getDescription());
        return m;
    }

    private Map<String, Object> toSystemConfigMap(SystemConfig cfg) {
        if (cfg == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cfg.getId());
        m.put("key", cfg.getKey());
        m.put("value", cfg.getValue());
        m.put("description", cfg.getDescription());
        return m;
    }

    private Map<String, Object> toPortalMenuPayload(PortalMenu menu) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", menu.getId());
        m.put("name", menu.getName());
        m.put("path", menu.getPath());
        m.put("component", menu.getComponent());
        m.put("icon", menu.getIcon());
        m.put("sortOrder", menu.getSortOrder());
        m.put("metadata", menu.getMetadata());
        m.put("parentId", menu.getParent() != null ? menu.getParent().getId() : null);
        return m;
    }

    private Map<String, Object> readPortalMenuPayload(Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (body == null) {
            return m;
        }
        if (body.containsKey("name")) m.put("name", Objects.toString(body.get("name"), null));
        if (body.containsKey("path")) m.put("path", Objects.toString(body.get("path"), null));
        if (body.containsKey("component")) m.put("component", Objects.toString(body.get("component"), null));
        if (body.containsKey("icon")) m.put("icon", Objects.toString(body.get("icon"), null));
        if (body.containsKey("metadata")) m.put("metadata", body.get("metadata"));
        if (body.containsKey("sortOrder")) {
            Object v = body.get("sortOrder");
            m.put("sortOrder", v == null ? null : Integer.valueOf(v.toString()));
        }
        if (body.containsKey("parentId")) {
            Object v = body.get("parentId");
            m.put("parentId", v == null ? null : Long.valueOf(v.toString()));
        }
        return m;
    }

    private void applyChangeRequest(ChangeRequest cr) {
        try {
            if ("PORTAL_MENU".equalsIgnoreCase(cr.getResourceType())) {
                applyPortalMenuChange(cr);
            } else if ("CONFIG".equalsIgnoreCase(cr.getResourceType())) {
                applySystemConfigChange(cr);
            } else if ("ORG".equalsIgnoreCase(cr.getResourceType())) {
                applyOrganizationChange(cr);
            } else if ("CUSTOM_ROLE".equalsIgnoreCase(cr.getResourceType())) {
                applyCustomRoleChange(cr);
            } else if ("ROLE_ASSIGNMENT".equalsIgnoreCase(cr.getResourceType())) {
                applyRoleAssignmentChange(cr);
            }
        } catch (Exception e) {
            // swallow apply failures to keep approval result; could store error
        }
    }

    private void applyPortalMenuChange(ChangeRequest cr) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        if ("CREATE".equalsIgnoreCase(action)) {
            Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
            PortalMenu entity = new PortalMenu();
            entity.setName(Objects.toString(payload.get("name"), ""));
            entity.setPath(Objects.toString(payload.get("path"), ""));
            entity.setComponent(Objects.toString(payload.get("component"), null));
            entity.setIcon(Objects.toString(payload.get("icon"), null));
            entity.setSortOrder(payload.get("sortOrder") == null ? null : Integer.valueOf(payload.get("sortOrder").toString()));
            entity.setMetadata(Objects.toString(payload.get("metadata"), null));
            if (parentId != null) {
                // simple parent attach via find; if not found, leave as root
                portalMenuRepo.findById(parentId).ifPresent(entity::setParent);
            }
            portalMenuRepo.save(entity);
        } else if ("UPDATE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            portalMenuRepo
                .findById(id)
                .ifPresent(target -> {
                    if (payload.containsKey("name")) target.setName(Objects.toString(payload.get("name"), target.getName()));
                    if (payload.containsKey("path")) target.setPath(Objects.toString(payload.get("path"), target.getPath()));
                    if (payload.containsKey("component")) target.setComponent(Objects.toString(payload.get("component"), target.getComponent()));
                    if (payload.containsKey("icon")) target.setIcon(Objects.toString(payload.get("icon"), target.getIcon()));
                    if (payload.containsKey("sortOrder")) target.setSortOrder(payload.get("sortOrder") == null ? null : Integer.valueOf(payload.get("sortOrder").toString()));
                    if (payload.containsKey("metadata")) target.setMetadata(Objects.toString(payload.get("metadata"), target.getMetadata()));
                    portalMenuRepo.save(target);
                });
        } else if ("DELETE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            if (portalMenuRepo.existsById(id)) portalMenuRepo.deleteById(id);
        }
        cr.setStatus("APPLIED");
    }

    private void applySystemConfigChange(ChangeRequest cr) throws Exception {
        Map<String, Object> cfg = fromJson(cr.getPayloadJson());
        String key = Objects.toString(cfg.get("key"), null);
        if (key == null || key.isBlank()) return;
        SystemConfig c = sysCfgRepo.findByKey(key).orElseGet(SystemConfig::new);
        c.setKey(key);
        c.setValue(Objects.toString(cfg.get("value"), null));
        c.setDescription(Objects.toString(cfg.get("description"), null));
        sysCfgRepo.save(c);
        cr.setStatus("APPLIED");
    }

    private void applyOrganizationChange(ChangeRequest cr) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        if ("CREATE".equalsIgnoreCase(action)) {
            String name = Objects.toString(payload.get("name"), null);
            String dataLevel = Objects.toString(payload.get("dataLevel"), null);
            Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
            String contact = Objects.toString(payload.get("contact"), null);
            String phone = Objects.toString(payload.get("phone"), null);
            String description = Objects.toString(payload.get("description"), null);
            OrganizationNode created = orgService.create(name, dataLevel, parentId, contact, phone, description);
            cr.setResourceId(String.valueOf(created.getId()));
        } else if ("UPDATE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            organizationRepository
                .findById(id)
                .ifPresent(entity -> {
                    if (payload.containsKey("name")) entity.setName(Objects.toString(payload.get("name"), entity.getName()));
                    if (payload.containsKey("dataLevel")) entity.setDataLevel(Objects.toString(payload.get("dataLevel"), entity.getDataLevel()));
                    if (payload.containsKey("contact")) entity.setContact(Objects.toString(payload.get("contact"), entity.getContact()));
                    if (payload.containsKey("phone")) entity.setPhone(Objects.toString(payload.get("phone"), entity.getPhone()));
                    if (payload.containsKey("description")) entity.setDescription(Objects.toString(payload.get("description"), entity.getDescription()));
                    organizationRepository.save(entity);
                });
        } else if ("DELETE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            orgService.delete(id);
        }
        cr.setStatus("APPLIED");
    }

    private void applyCustomRoleChange(ChangeRequest cr) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        if ("CREATE".equalsIgnoreCase(action)) {
            AdminCustomRole role = new AdminCustomRole();
            role.setName(Objects.toString(payload.get("name"), null));
            role.setScope(Objects.toString(payload.get("scope"), null));
            var ops = new LinkedHashSet<>(readStringList(payload.get("operations")));
            role.setOperationsCsv(String.join(",", ops));
            Object maxRows = payload.get("maxRows");
            role.setMaxRows(maxRows == null ? null : Integer.valueOf(maxRows.toString()));
            role.setAllowDesensitizeJson(Boolean.TRUE.equals(payload.get("allowDesensitizeJson")));
            role.setMaxDataLevel(Objects.toString(payload.get("maxDataLevel"), null));
            role.setDescription(Objects.toString(payload.get("description"), null));
            role = customRoleRepo.save(role);
            cr.setResourceId(String.valueOf(role.getId()));
        }
        cr.setStatus("APPLIED");
    }

    private void applyRoleAssignmentChange(ChangeRequest cr) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        if ("CREATE".equalsIgnoreCase(action)) {
            AdminRoleAssignment assignment = new AdminRoleAssignment();
            assignment.setRole(Objects.toString(payload.get("role"), null));
            assignment.setUsername(Objects.toString(payload.get("username"), null));
            assignment.setDisplayName(Objects.toString(payload.get("displayName"), null));
            assignment.setUserSecurityLevel(Objects.toString(payload.get("userSecurityLevel"), null));
            Object scope = payload.get("scopeOrgId");
            assignment.setScopeOrgId(scope == null ? null : Long.valueOf(scope.toString()));
            List<Long> datasetIds = readLongList(payload.get("datasetIds"));
            assignment.setDatasetIdsCsv(joinCsv(datasetIds));
            var ops = new LinkedHashSet<>(readStringList(payload.get("operations")));
            assignment.setOperationsCsv(String.join(",", ops));
            assignment = roleAssignRepo.save(assignment);
            cr.setResourceId(String.valueOf(assignment.getId()));
            try {
                notifyClient.trySend(
                    "role_assignment_created",
                    Map.of("id", assignment.getId(), "username", assignment.getUsername(), "role", assignment.getRole())
                );
            } catch (Exception ignored) {}
        }
        cr.setStatus("APPLIED");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) throws Exception {
        if (json == null || json.isBlank()) return Map.of();
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
    }

    private PortalMenu findMenu(PortalMenu n, Long id) {
        if (n.getId().equals(id)) return n;
        if (n.getChildren() != null) for (PortalMenu c : n.getChildren()) { PortalMenu f = findMenu(c, id); if (f != null) return f; }
        return null;
    }

    private static Map<String, Object> toChangeVM(ChangeRequest cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cr.getId());
        m.put("resourceType", cr.getResourceType());
        m.put("resourceId", cr.getResourceId());
        m.put("action", cr.getAction());
        m.put("payloadJson", cr.getPayloadJson());
        m.put("diffJson", cr.getDiffJson());
        m.put("status", cr.getStatus());
        m.put("requestedBy", cr.getRequestedBy());
        m.put("requestedAt", cr.getRequestedAt() != null ? cr.getRequestedAt().toString() : null);
        m.put("decidedBy", cr.getDecidedBy());
        m.put("decidedAt", cr.getDecidedAt() != null ? cr.getDecidedAt().toString() : null);
        m.put("reason", cr.getReason());
        return m;
    }

    private static Map<String, Object> toMenuVM(PortalMenu p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("path", p.getPath());
        m.put("component", p.getComponent());
        m.put("icon", p.getIcon());
        m.put("sortOrder", p.getSortOrder());
        m.put("metadata", p.getMetadata());
        m.put("parentId", p.getParent() != null ? p.getParent().getId() : null);
        if (p.getChildren() != null && !p.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (PortalMenu c : p.getChildren()) children.add(toMenuVM(c));
            m.put("children", children);
        }
        return m;
    }

    private Map<String, Object> toDatasetVM(AdminDataset d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("businessCode", d.getBusinessCode());
        m.put("description", d.getDescription());
        m.put("dataLevel", d.getDataLevel());
        m.put("ownerOrgId", d.getOwnerOrgId());
        m.put("ownerOrgName", resolveOrgName(d.getOwnerOrgId()));
        m.put("isInstituteShared", Boolean.TRUE.equals(d.getIsInstituteShared()));
        m.put("rowCount", d.getRowCount() == null ? 0 : d.getRowCount());
        m.put("updatedAt", d.getLastModifiedDate() != null ? d.getLastModifiedDate().toString() : null);
        return m;
    }

    private Map<String, Object> toCustomRoleVM(AdminCustomRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("scope", r.getScope());
        m.put("operations", Arrays.asList(r.getOperationsCsv().split(",")));
        m.put("maxRows", r.getMaxRows());
        m.put("allowDesensitizeJson", Boolean.TRUE.equals(r.getAllowDesensitizeJson()));
        m.put("maxDataLevel", r.getMaxDataLevel());
        m.put("description", r.getDescription());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedDate() != null ? r.getCreatedDate().toString() : null);
        return m;
    }

    private Map<String, Object> toRoleAssignmentVM(AdminRoleAssignment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("role", a.getRole());
        m.put("username", a.getUsername());
        m.put("displayName", a.getDisplayName());
        m.put("userSecurityLevel", a.getUserSecurityLevel());
        m.put("scopeOrgId", a.getScopeOrgId());
        m.put("scopeOrgName", resolveOrgName(a.getScopeOrgId()));
        m.put("datasetIds", a.getDatasetIdsCsv() == null || a.getDatasetIdsCsv().isBlank() ? List.of() : Arrays.stream(a.getDatasetIdsCsv().split(",")).map(Long::valueOf).toList());
        m.put("operations", a.getOperationsCsv() == null ? List.of() : Arrays.asList(a.getOperationsCsv().split(",")));
        m.put("grantedBy", a.getCreatedBy());
        m.put("grantedAt", a.getCreatedDate() != null ? a.getCreatedDate().toString() : null);
        return m;
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) return "全院共享区";
        for (OrganizationNode root : orgService.findTree()) {
            OrganizationNode found = findOrg(root, orgId);
            if (found != null) return found.getName();
        }
        return "组织 " + orgId;
    }

    private OrganizationNode findOrg(OrganizationNode n, Long id) {
        if (n.getId().equals(id)) return n;
        if (n.getChildren() != null) for (OrganizationNode c : n.getChildren()) { OrganizationNode f = findOrg(c, id); if (f != null) return f; }
        return null;
    }

    private static List<String> readStringList(Object v) {
        if (v instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of();
    }

    private static List<Long> readLongList(Object v) {
        if (v instanceof List<?> list) return list.stream().map(String::valueOf).map(Long::valueOf).toList();
        return List.of();
    }

    private static String joinCsv(List<Long> ids) { return ids.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse(""); }

    private String validateAssignment(String role, String username, String displayName, String userSecLevel, Long scopeOrgId, List<String> ops, List<Long> datasetIds) {
        if (role.isEmpty()) return "请选择角色";
        if (username.isEmpty() || displayName.isEmpty()) return "请填写用户信息";
        if (datasetIds.isEmpty()) return "请至少选择一个数据集";
        if (ops.isEmpty()) return "请选择需要授权的操作";
        Set<String> allowed = allowedOpsForRole(role);
        if (!ops.stream().allMatch(allowed::contains)) return "角色不支持所选操作";
        Integer userRank = securityRank(userSecLevel);
        if (userRank == null) return "无效的用户密级";
        // verify datasets exist + rank + scope
        List<AdminDataset> datasets = datasetRepo.findAllById(datasetIds);
        if (datasets.size() != datasetIds.size()) return "存在无效的数据集ID";
        for (AdminDataset d : datasets) {
            Integer dr = dataRank(d.getDataLevel());
            if (dr == null) return "数据密级无效";
            if (userRank < dr) return "用户密级不足以访问数据集 " + d.getBusinessCode();
            if (scopeOrgId == null) {
                if (!Boolean.TRUE.equals(d.getIsInstituteShared())) return "数据集 " + d.getBusinessCode() + " 未进入全院共享区";
            } else if (!Objects.equals(scopeOrgId, d.getOwnerOrgId())) {
                return "数据集 " + d.getBusinessCode() + " 不属于所选机构";
            }
        }
        return null;
    }

    private static Integer securityRank(String level) {
        return switch (level) { case "NON_SECRET" -> 0; case "GENERAL" -> 1; case "IMPORTANT" -> 2; case "CORE" -> 3; default -> null; };
    }

    private Set<String> allowedOpsForRole(String role) {
        String r = role.toUpperCase();
        if (r.equals("SYSADMIN") || r.equals("OPADMIN")) return Set.of("read","write","export");
        if (r.equals("AUTHADMIN")) return Set.of("read");
        if (r.equals("AUDITADMIN")) return Set.of("read","export");
        // custom role
        return customRoleRepo
            .findByName(role)
            .map(cr -> {
                Set<String> s = new LinkedHashSet<>(Arrays.asList(cr.getOperationsCsv().split(",")));
                return s;
            })
            .orElse(Set.of("read"));
    }

    private static Integer dataRank(String level) {
        return switch (level) {
            case "DATA_PUBLIC" -> 0;
            case "DATA_INTERNAL" -> 1;
            case "DATA_SECRET" -> 2;
            case "DATA_TOP_SECRET" -> 3;
            default -> null;
        };
    }

    private static final class Jsons {
        static String toJson(Object obj) {
            try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); } catch (Exception e) { return null; }
        }
    }
}
