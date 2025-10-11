package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.api.ResultStatus;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.OrganizationSyncService;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.service.PortalMenuService;
import com.yuzhi.dts.admin.domain.PortalMenu;
import com.yuzhi.dts.admin.domain.PortalMenuVisibility;
import com.yuzhi.dts.admin.domain.AdminDataset;
import com.yuzhi.dts.admin.domain.AdminCustomRole;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import com.yuzhi.dts.admin.domain.AdminApprovalRequest;
import com.yuzhi.dts.admin.repository.AdminDatasetRepository;
import com.yuzhi.dts.admin.repository.AdminCustomRoleRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.SystemConfigRepository;
import com.yuzhi.dts.admin.domain.SystemConfig;
import com.yuzhi.dts.admin.repository.PortalMenuRepository;
import com.yuzhi.dts.admin.repository.PortalMenuVisibilityRepository;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.service.notify.DtsCommonNotifyClient;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.common.audit.AuditStage;

@RestController
@RequestMapping("/api/admin")
@Transactional
@io.swagger.v3.oas.annotations.tags.Tag(name = "admin")
public class AdminApiResource {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AdminApiResource.class);

    public record WhoAmI(boolean allowed, String role, String username, String email) {}

    private static boolean hasAny(Collection<? extends GrantedAuthority> auths, String... roles) {
        Set<String> set = new HashSet<>();
        for (GrantedAuthority a : auths) set.add(a.getAuthority());
        for (String r : roles) if (set.contains(r)) return true;
        return false;
    }

    private static String canonicalReservedRole(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return normalized.replace("_", "");
    }

    private static boolean isReservedRealmRoleName(String role) {
        return RESERVED_REALM_ROLES.contains(canonicalReservedRole(role));
    }

    private static String stripRolePrefix(String name) {
        if (name == null) {
            return null;
        }
        String upper = name.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) {
            upper = upper.substring(5);
        }
        upper = upper.replaceAll("\\s+", "");
        return ROLE_ALIASES.getOrDefault(upper, upper);
    }

    private static String resolveLegacyRole(String canonical) {
        if (canonical == null) {
            return null;
        }
        return ROLE_REVERSE_ALIASES.get(canonical);
    }

    private static List<String> authorityCandidates(String canonical) {
        if (!StringUtils.hasText(canonical)) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.add("ROLE_" + canonical);
        String legacy = resolveLegacyRole(canonical);
        if (legacy != null) {
            codes.add("ROLE_" + legacy);
        }
        return new ArrayList<>(codes);
    }

    private static List<String> roleNameCandidates(String canonical) {
        if (!StringUtils.hasText(canonical)) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(canonical);
        String legacy = resolveLegacyRole(canonical);
        if (legacy != null) {
            names.add(legacy);
        }
        return new ArrayList<>(names);
    }

    @GetMapping("/whoami")
    public ResponseEntity<ApiResponse<WhoAmI>> whoami(Principal principal) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Be tolerant to legacy/alias role names to avoid false 403 in prod tokens
        boolean allowed = auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).map(AdminApiResource::toCanonicalTriadRole).anyMatch(
            r -> r != null && (
                r.equals(AuthoritiesConstants.SYS_ADMIN) ||
                r.equals(AuthoritiesConstants.AUTH_ADMIN) ||
                r.equals(AuthoritiesConstants.AUDITOR_ADMIN)
            )
        );
        String role = auth == null
            ? null
            : auth
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(AdminApiResource::toCanonicalTriadRole)
                .filter(Objects::nonNull)
                .filter(r ->
                    r.equals(AuthoritiesConstants.SYS_ADMIN) ||
                    r.equals(AuthoritiesConstants.AUTH_ADMIN) ||
                    r.equals(AuthoritiesConstants.AUDITOR_ADMIN)
                )
                .findFirst()
                .orElse(null);
        WhoAmI payload = new WhoAmI(allowed, role, principal != null ? principal.getName() : null, null);
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            "whoami",
            Map.of()
        );
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    private static String toCanonicalTriadRole(String authority) {
        if (authority == null || authority.isBlank()) return null;
        String a = authority.trim().toUpperCase(Locale.ROOT);
        // Already canonical
        if (
            a.equals(AuthoritiesConstants.SYS_ADMIN) ||
            a.equals(AuthoritiesConstants.AUTH_ADMIN) ||
            a.equals(AuthoritiesConstants.AUDITOR_ADMIN)
        ) {
            return a;
        }
        // Common alias variants observed in legacy realms/tokens
        if (a.equals("ROLE_SYSADMIN") || a.equals("ROLE_SYSTEM_ADMIN") || a.equals("SYSADMIN") || a.equals("SYSTEM_ADMIN")) {
            return AuthoritiesConstants.SYS_ADMIN;
        }
        if (a.equals("ROLE_AUTHADMIN") || a.equals("ROLE_IAM_ADMIN") || a.equals("AUTHADMIN") || a.equals("IAM_ADMIN")) {
            return AuthoritiesConstants.AUTH_ADMIN;
        }
        if (
            a.equals("ROLE_AUDITOR_ADMIN") ||
            a.equals("ROLE_AUDIT_ADMIN") ||
            a.equals("ROLE_AUDITADMIN") ||
            a.equals("AUDITADMIN") ||
            a.equals("SECURITYAUDITOR")
        ) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        return null;
    }

    // --- Minimal audit endpoints ---
    private final AdminAuditService auditService;
    private final OrganizationService orgService;
    private final OrganizationSyncService organizationSyncService;
    private final ChangeRequestRepository crRepo;
    private final AdminApprovalRequestRepository approvalRepo;
    private final ChangeRequestService changeRequestService;
    private final PortalMenuService portalMenuService;
    private final AdminDatasetRepository datasetRepo;
    private final AdminCustomRoleRepository customRoleRepo;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final SystemConfigRepository sysCfgRepo;
    private final PortalMenuRepository portalMenuRepo;
    private final PortalMenuVisibilityRepository visibilityRepo;
    private final DtsCommonNotifyClient notifyClient;
    private final OrganizationRepository organizationRepository;
    private final AdminUserService adminUserService;

    private static final Set<String> MENU_SECURITY_LEVELS = Set.of("NON_SECRET", "GENERAL", "IMPORTANT", "CORE");
    private static final Set<String> VISIBILITY_DATA_LEVELS = Set.of("PUBLIC", "INTERNAL", "SECRET", "TOP_SECRET");
    private static final Map<String, String> MENU_DATA_LEVEL_ALIAS = Map.of(
        "GENERAL", "INTERNAL",
        "NON_SECRET", "PUBLIC",
        "IMPORTANT", "SECRET",
        "CORE", "TOP_SECRET"
    );
    // Tighten default visibility: ROLE_USER is non-binding and should not be added by default
    private static final List<String> DEFAULT_PORTAL_ROLES = List.of(AuthoritiesConstants.OP_ADMIN);
    private static final Set<String> RESERVED_REALM_ROLES = Set.of("SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN");

    private static final List<String> OPERATION_ORDER = List.of("read", "write", "export");

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        Map.entry("DEPT_OWNER", "DEPT_DATA_OWNER"),
        Map.entry("DEPT_EDITOR", "DEPT_DATA_DEV"),
        Map.entry("DEPT_VIEWER", "DEPT_DATA_VIEWER"),
        Map.entry("INST_OWNER", "INST_DATA_OWNER"),
        Map.entry("INST_EDITOR", "INST_DATA_DEV"),
        Map.entry("INST_VIEWER", "INST_DATA_VIEWER")
    );

    private static final Map<String, String> ROLE_REVERSE_ALIASES;

    static {
        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, String> entry : ROLE_ALIASES.entrySet()) {
            reverse.putIfAbsent(entry.getValue(), entry.getKey());
        }
        ROLE_REVERSE_ALIASES = Collections.unmodifiableMap(reverse);
    }

    private record BuiltinRoleSpec(String scope, List<String> operations, String titleCn, String titleEn, String description) {}

    private static final Map<String, BuiltinRoleSpec> BUILTIN_DATA_ROLES = Map.ofEntries(
        Map.entry(
            "DEPT_DATA_OWNER",
            new BuiltinRoleSpec(
                "DEPARTMENT",
                List.of("read", "write", "export"),
                "部门数据管理员",
                "department data administrator",
                "负责本部门数据；可读取本部门且密级不超的资源，具备部门范围写入和授权能力；导出高敏数据需审批。"
            )
        ),
        Map.entry(
            "DEPT_DATA_DEV",
            new BuiltinRoleSpec(
                "DEPARTMENT",
                List.of("read", "write", "export"),
                "部门数据开发员",
                "department data developer",
                "覆盖本部门数据开发；可读取密级不超的部门数据并在部门范围内写入；不具备密级或共享策略调整、授权管理能力；导出受策略限制。"
            )
        ),
        Map.entry(
            "DEPT_DATA_VIEWER",
            new BuiltinRoleSpec(
                "DEPARTMENT",
                List.of("read"),
                "部门数据查看员",
                "department data viewer",
                "浏览本部门密级不超的数据；无写入、导出或授权能力。"
            )
        ),
        Map.entry(
            "INST_DATA_OWNER",
            new BuiltinRoleSpec(
                "INSTITUTE",
                List.of("read", "write", "export"),
                "研究所数据管理员",
                "institute data administrator",
                "面向全所共享区；可读取全所共享区内密级不超的数据，并写入和管理共享策略；负责编辑/查看授权；导出高敏数据需审批。"
            )
        ),
        Map.entry(
            "INST_DATA_DEV",
            new BuiltinRoleSpec(
                "INSTITUTE",
                List.of("read", "write", "export"),
                "研究所数据开发员",
                "institute data developer",
                "在全所共享区开展数据开发；可读取共享区密级不超的数据并写入共享区；无密级或共享策略调整、授权能力；导出受策略限制。"
            )
        ),
        Map.entry(
            "INST_DATA_VIEWER",
            new BuiltinRoleSpec(
                "INSTITUTE",
                List.of("read"),
                "研究所数据查看员",
                "institute data viewer",
                "浏览全所共享且密级不超的数据；无写入、导出或授权能力。"
            )
        )
    );

    public AdminApiResource(
        AdminAuditService auditService,
        OrganizationService orgService,
        OrganizationSyncService organizationSyncService,
        ChangeRequestRepository crRepo,
        AdminApprovalRequestRepository approvalRepo,
        ChangeRequestService changeRequestService,
        PortalMenuService portalMenuService,
        AdminDatasetRepository datasetRepo,
        AdminCustomRoleRepository customRoleRepo,
        AdminRoleAssignmentRepository roleAssignRepo,
        SystemConfigRepository sysCfgRepo,
        PortalMenuRepository portalMenuRepo,
        PortalMenuVisibilityRepository visibilityRepo,
        DtsCommonNotifyClient notifyClient,
        OrganizationRepository organizationRepository,
        AdminUserService adminUserService
    ) {
        this.auditService = auditService;
        this.orgService = orgService;
        this.organizationSyncService = organizationSyncService;
        this.crRepo = crRepo;
        this.approvalRepo = approvalRepo;
        this.changeRequestService = changeRequestService;
        this.portalMenuService = portalMenuService;
        this.datasetRepo = datasetRepo;
        this.customRoleRepo = customRoleRepo;
        this.roleAssignRepo = roleAssignRepo;
        this.sysCfgRepo = sysCfgRepo;
        this.portalMenuRepo = portalMenuRepo;
        this.visibilityRepo = visibilityRepo;
        this.notifyClient = notifyClient;
        this.organizationRepository = organizationRepository;
        this.adminUserService = adminUserService;
    }

    @org.springframework.beans.factory.annotation.Value("${dts.admin.require-approval.portal-menu.visibility:true}")
    private boolean requireMenuVisibilityApproval;

    @org.springframework.beans.factory.annotation.Value("${dts.admin.require-approval.portal-menu.structure:false}")
    private boolean requireMenuStructureApproval;

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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            "system-config/list",
            Map.of("count", list.size())
        );
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            Objects.toString(cfg.getOrDefault("key", "config")),
            Map.of("draft", Boolean.TRUE)
        );
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/portal/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portalMenus() {
        Map<String, Object> payload = buildPortalMenuCollection();
        // 非 OP_ADMIN 隐藏“基础数据功能”区（foundation）
        if (SecurityUtils.hasCurrentUserNoneOfAuthorities(AuthoritiesConstants.OP_ADMIN)) {
            payload = filterFoundationForNonOpAdmin(payload);
        }
        Object menus = payload.get("menus");
        int sectionCount = menus instanceof java.util.Collection<?> col ? col.size() : 0;
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_MENU_VIEW_TREE",
            AuditStage.SUCCESS,
            "admin",
            Map.of("sections", sectionCount)
        );
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PostMapping("/portal/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMenu(@RequestBody Map<String, Object> body) {
        Map<String, Object> payload = readPortalMenuPayload(body);
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("request", payload);
        String pendingRef = Objects.toString(payload.get("name"), "portal-menu");
        auditService.recordAction(actor, "ADMIN_MENU_CREATE", AuditStage.BEGIN, pendingRef, auditDetail);
        try {
            boolean visibilityTouched = payload.containsKey("visibilityRules") || payload.containsKey("allowedRoles") || payload.containsKey("allowedPermissions") || payload.containsKey("maxDataLevel");
            boolean structureTouched = payload.containsKey("name") || payload.containsKey("path") || payload.containsKey("component") || payload.containsKey("icon") || payload.containsKey("sortOrder") || payload.containsKey("parentId");
            if ((requireMenuVisibilityApproval && visibilityTouched) || (requireMenuStructureApproval && structureTouched)) {
                ChangeRequest cr = changeRequestService.draft(
                    "PORTAL_MENU",
                    "CREATE",
                    null,
                    payload,
                    null,
                    Objects.toString(body.get("reason"), null)
                );
                try {
                    notifyClient.trySend(
                        "approval_pending",
                        Map.of(
                            "id",
                            String.valueOf(cr.getId()),
                            "type",
                            cr.getResourceType(),
                            "category",
                            cr.getCategory(),
                            "requestedBy",
                            cr.getRequestedBy()
                        )
                    );
                } catch (Exception ignored) {}
                Map<String, Object> approvalPayload = new LinkedHashMap<>(auditDetail);
                approvalPayload.put("status", "APPROVAL_PENDING");
                approvalPayload.put("changeRequestId", cr.getId());
                auditService.recordAction(actor, "ADMIN_MENU_CREATE", AuditStage.SUCCESS, String.valueOf(cr.getId()), approvalPayload);
                return ResponseEntity.status(202).body(ApiResponse.ok(toChangeVM(cr)));
            }
            String name = trimToNull(payload.get("name"));
            String path = trimToNull(payload.get("path"));
            if (!StringUtils.hasText(name) || !StringUtils.hasText(path)) {
                throw new IllegalArgumentException("菜单名称和路径不能为空");
            }

            PortalMenu menu = new PortalMenu();
            menu.setName(name);
            menu.setPath(path);
            if (payload.containsKey("component")) {
                menu.setComponent(trimToNull(payload.get("component")));
            }
            if (payload.containsKey("icon")) {
                menu.setIcon(trimToNull(payload.get("icon")));
            }
            if (payload.containsKey("sortOrder")) {
                menu.setSortOrder(payload.get("sortOrder") == null ? null : Integer.valueOf(payload.get("sortOrder").toString()));
            }
            menu.setMetadata(normalizeMenuMetadata(payload.get("metadata")));
            menu.setSecurityLevel(normalizeMenuSecurityLevel(payload.get("securityLevel")));
            menu.setDeleted(payload.containsKey("deleted") ? toBoolean(payload.get("deleted")) : false);

            Long parentId = toNullableLong(payload.get("parentId"));
            if (parentId != null) {
                PortalMenu parent = portalMenuRepo
                    .findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("父菜单不存在"));
                menu.setParent(parent);
            }

            menu = portalMenuRepo.save(menu);
            List<PortalMenuVisibility> visibilities = buildVisibilityEntities(payload, menu);
            portalMenuService.replaceVisibilities(menu, visibilities);

            PortalMenu persisted = portalMenuRepo.findById(menu.getId()).orElse(menu);
            Map<String, Object> successDetail = new LinkedHashMap<>(auditDetail);
            successDetail.put("created", toPortalMenuPayload(persisted));
            auditService.recordAction(actor, "ADMIN_MENU_CREATE", AuditStage.SUCCESS, String.valueOf(persisted.getId()), successDetail);
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "create", "id", String.valueOf(persisted.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
            failureDetail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_MENU_CREATE", AuditStage.FAIL, pendingRef, failureDetail);
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
            failureDetail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_MENU_CREATE", AuditStage.FAIL, pendingRef, failureDetail);
            log.error("Failed to create portal menu", ex);
            return ResponseEntity.internalServerError().body(ApiResponse.error("创建菜单失败: " + ex.getMessage()));
        }
    }

    @PostMapping("/portal/menus/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetMenus() {
        portalMenuService.resetMenusToSeed();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_MENU_UPDATE",
            AuditStage.SUCCESS,
            "seed",
            Map.of("action", "reset")
        );
        return portalMenus();
    }

    @PutMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMenu(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Long menuId = Long.valueOf(id);
        PortalMenu beforeEntity = portalMenuRepo.findById(menuId).orElse(null);
        if (beforeEntity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> payload = readPortalMenuPayload(body);
        Map<String, Object> before = toPortalMenuPayload(beforeEntity);
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditBase = new LinkedHashMap<>();
        auditBase.put("before", before);
        auditBase.put("payload", payload);
        auditService.recordAction(actor, "ADMIN_MENU_UPDATE", AuditStage.BEGIN, id, auditBase);
        try {
            boolean visibilityTouchedGate = payload.containsKey("visibilityRules") || payload.containsKey("allowedRoles") || payload.containsKey("allowedPermissions") || payload.containsKey("maxDataLevel");
            boolean structureTouchedGate = payload.containsKey("name") || payload.containsKey("path") || payload.containsKey("component") || payload.containsKey("icon") || payload.containsKey("sortOrder") || payload.containsKey("parentId") || payload.containsKey("deleted");
            if ((requireMenuVisibilityApproval && visibilityTouchedGate) || (requireMenuStructureApproval && structureTouchedGate)) {
                ChangeRequest cr = changeRequestService.draft(
                    "PORTAL_MENU",
                    "UPDATE",
                    id,
                    payload,
                    before,
                    Objects.toString(body.get("reason"), null)
                );
                try {
                    notifyClient.trySend(
                        "approval_pending",
                        Map.of(
                            "id",
                            String.valueOf(cr.getId()),
                            "type",
                            cr.getResourceType(),
                            "category",
                            cr.getCategory(),
                            "requestedBy",
                            cr.getRequestedBy()
                        )
                    );
                } catch (Exception ignored) {}
                Map<String, Object> approvalDetail = new LinkedHashMap<>(auditBase);
                approvalDetail.put("status", "APPROVAL_PENDING");
                approvalDetail.put("changeRequestId", cr.getId());
                auditService.recordAction(actor, "ADMIN_MENU_UPDATE", AuditStage.SUCCESS, String.valueOf(cr.getId()), approvalDetail);
                return ResponseEntity.status(202).body(ApiResponse.ok(toChangeVM(cr)));
            }
            applyMenuUpdates(beforeEntity, payload);
            boolean visibilityTouched = payload.containsKey("visibilityRules") || payload.containsKey("allowedRoles") || payload.containsKey("allowedPermissions") || payload.containsKey("maxDataLevel");
            if (visibilityTouched) {
                List<PortalMenuVisibility> visibilities = buildVisibilityEntities(payload, beforeEntity);
                portalMenuService.replaceVisibilities(beforeEntity, visibilities);
            } else {
                portalMenuRepo.save(beforeEntity);
            }
            PortalMenu persisted = portalMenuRepo.findById(menuId).orElse(beforeEntity);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", toPortalMenuPayload(persisted));
            auditService.recordAction(actor, "ADMIN_MENU_UPDATE", AuditStage.SUCCESS, id, detail);
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "update", "id", String.valueOf(persisted.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_MENU_UPDATE", AuditStage.FAIL, id, detail);
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_MENU_UPDATE", AuditStage.FAIL, id, detail);
            log.error("Failed to update portal menu {}", id, ex);
            return ResponseEntity.internalServerError().body(ApiResponse.error("更新菜单失败: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMenu(@PathVariable String id) {
        Long menuId = Long.valueOf(id);
        PortalMenu entity = portalMenuRepo.findById(menuId).orElse(null);
        if (entity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> before = toPortalMenuPayload(entity);
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("before", before);
        auditService.recordAction(actor, "ADMIN_MENU_DELETE", AuditStage.BEGIN, id, auditDetail);
        if (requireMenuStructureApproval) {
            ChangeRequest cr = changeRequestService.draft(
                "PORTAL_MENU",
                "DELETE",
                id,
                Map.of("id", menuId),
                before,
                null
            );
            try {
                notifyClient.trySend(
                    "approval_pending",
                    Map.of(
                        "id",
                        String.valueOf(cr.getId()),
                        "type",
                        cr.getResourceType(),
                        "category",
                        cr.getCategory(),
                        "requestedBy",
                        cr.getRequestedBy()
                    )
                );
            } catch (Exception ignored) {}
            Map<String, Object> approvalDetail = new LinkedHashMap<>(auditDetail);
            approvalDetail.put("status", "APPROVAL_PENDING");
            approvalDetail.put("changeRequestId", cr.getId());
            auditService.recordAction(actor, "ADMIN_MENU_DELETE", AuditStage.SUCCESS, String.valueOf(cr.getId()), approvalDetail);
            return ResponseEntity.status(202).body(ApiResponse.ok(toChangeVM(cr)));
        }
        try {
            markMenuDeleted(entity);
            portalMenuRepo.save(entity);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", toPortalMenuPayload(entity));
            auditService.recordAction(actor, "ADMIN_MENU_DELETE", AuditStage.SUCCESS, id, detail);
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "disable", "id", String.valueOf(entity.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (Exception ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_MENU_DELETE", AuditStage.FAIL, id, detail);
            log.error("Failed to delete portal menu {}", id, ex);
            return ResponseEntity.internalServerError().body(ApiResponse.error("删除菜单失败: " + ex.getMessage()));
        }
    }

    @GetMapping("/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> orgs() {
        List<Map<String, Object>> tree = buildOrgTree();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ORG_VIEW_TREE",
            AuditStage.SUCCESS,
            "tree",
            Map.of("nodeCount", tree.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    // Platform-friendly orgs endpoint (no triad token required; see SecurityConfiguration)
    @GetMapping("/platform/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> orgsForPlatform() {
        List<Map<String, Object>> tree = buildOrgTree();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ORG_VIEW_TREE",
            AuditStage.SUCCESS,
            "tree",
            Map.of("nodeCount", tree.size(), "audience", "platform")
        );
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @PostMapping("/platform/orgs/sync")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> syncOrgsForPlatform() {
        try {
            organizationSyncService.ensureUnassignedRoot();
        } catch (RuntimeException ex) {
            log.warn("ensureUnassignedRoot failed: {}", ex.getMessage());
        }
        List<Map<String, Object>> tree = buildOrgTree();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ORG_VIEW_TREE",
            AuditStage.SUCCESS,
            "tree",
            Map.of("nodeCount", tree.size(), "synced", true)
        );
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @PostMapping("/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> createOrg(@RequestBody Map<String, Object> payload) {
        String name = trimToNull(payload.get("name"));
        Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
        String description = trimToNull(payload.get("description"));
        String dataLevel = trimToNull(payload.get("dataLevel"));
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        String parentRef = parentId == null ? "root" : String.valueOf(parentId);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("name", name);
        if (parentId != null) {
            auditDetail.put("parentId", parentId);
        }
        if (StringUtils.hasText(description)) {
            auditDetail.put("description", description);
        }
        if (StringUtils.hasText(dataLevel)) {
            auditDetail.put("dataLevel", dataLevel);
        }

        if (!StringUtils.hasText(name)) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "部门名称不能为空");
            auditService.recordAction(actor, "ADMIN_ORG_CREATE", AuditStage.FAIL, parentRef, failure);
            return ResponseEntity.badRequest().body(ApiResponse.error("部门名称不能为空"));
        }

        auditService.recordAction(actor, "ADMIN_ORG_CREATE", AuditStage.BEGIN, parentRef, auditDetail);
        try {
            OrganizationNode created = orgService.create(name, parentId, description, dataLevel);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", created.getName());
            if (created.getParent() != null) {
                detail.put("parentId", created.getParent().getId());
            }
            if (StringUtils.hasText(description)) {
                detail.put("description", description);
            }
            detail.put("dataLevel", created.getDataLevel());
            detail.put("created", Map.of("id", created.getId(), "name", created.getName()));
            auditService.recordAction(actor, "ADMIN_ORG_CREATE", AuditStage.SUCCESS, String.valueOf(created.getId()), detail);

            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_ORG_CREATE", AuditStage.FAIL, parentRef, failure);
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> updateOrg(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        OrganizationNode existing = organizationRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }

        Map<String, Object> beforeView = new LinkedHashMap<>();
        beforeView.put("id", existing.getId());
        beforeView.put("name", existing.getName());
        if (existing.getParent() != null) {
            beforeView.put("parentId", existing.getParent().getId());
        }
        if (StringUtils.hasText(existing.getDescription())) {
            beforeView.put("description", existing.getDescription());
        }
        beforeView.put("dataLevel", existing.getDataLevel());

        String name = payload.containsKey("name") ? trimToNull(payload.get("name")) : existing.getName();
        String description = payload.containsKey("description") ? trimToNull(payload.get("description")) : existing.getDescription();
        Long parentId = payload.containsKey("parentId")
            ? (payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString()))
            : (existing.getParent() == null ? null : existing.getParent().getId());
        String dataLevel = payload.containsKey("dataLevel") ? trimToNull(payload.get("dataLevel")) : null;

        if (!StringUtils.hasText(name)) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("before", beforeView);
            failure.put("error", "部门名称不能为空");
            auditService.recordAction(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "ADMIN_ORG_UPDATE", AuditStage.FAIL, String.valueOf(id), failure);
            return ResponseEntity.badRequest().body(ApiResponse.error("部门名称不能为空"));
        }

        Map<String, Object> requestView = new LinkedHashMap<>();
        requestView.put("name", name);
        if (parentId != null) {
            requestView.put("parentId", parentId);
        }
        if (StringUtils.hasText(description)) {
            requestView.put("description", description);
        }
        if (StringUtils.hasText(dataLevel)) {
            requestView.put("dataLevel", dataLevel);
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("before", beforeView);
        auditDetail.put("request", requestView);
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");

        auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.BEGIN, String.valueOf(id), auditDetail);

        Optional<OrganizationNode> updated;
        try {
            updated = orgService.update(id, name, description, parentId, dataLevel);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.FAIL, String.valueOf(id), failure);
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
        if (updated.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }

        OrganizationNode updatedNode = updated.orElseThrow();
        Map<String, Object> afterView = new LinkedHashMap<>();
        afterView.put("id", updatedNode.getId());
        afterView.put("name", updatedNode.getName());
        if (updatedNode.getParent() != null) {
            afterView.put("parentId", updatedNode.getParent().getId());
        }
        if (StringUtils.hasText(updatedNode.getDescription())) {
            afterView.put("description", updatedNode.getDescription());
        }
        afterView.put("dataLevel", updatedNode.getDataLevel());
        Map<String, Object> successDetail = new LinkedHashMap<>();
        successDetail.put("before", beforeView);
        successDetail.put("after", afterView);
        auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.SUCCESS, String.valueOf(id), successDetail);

        return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
    }

    @DeleteMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> deleteOrg(@PathVariable long id) {
        OrganizationNode existing = organizationRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", existing.getId());
        detail.put("name", existing.getName());
        if (existing.getParent() != null) {
            detail.put("parentId", existing.getParent().getId());
        }
        auditService.recordAction(actor, "ADMIN_ORG_DELETE", AuditStage.BEGIN, String.valueOf(id), detail);
        try {
            orgService.delete(id);
            Map<String, Object> success = new LinkedHashMap<>(detail);
            success.put("status", "DELETED");
            auditService.recordAction(actor, "ADMIN_ORG_DELETE", AuditStage.SUCCESS, String.valueOf(id), success);
            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (RuntimeException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(detail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_ORG_DELETE", AuditStage.FAIL, String.valueOf(id), failure);
            return ResponseEntity.internalServerError().body(ApiResponse.error("删除部门失败: " + ex.getMessage()));
        }
    }

    @PostMapping("/orgs/sync")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> syncOrganizations() {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("sync", "keycloak");
        auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.BEGIN, "sync", auditDetail);
        try {
            organizationSyncService.syncAll();
            Map<String, Object> success = new LinkedHashMap<>(auditDetail);
            success.put("status", "SUCCESS");
            auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.SUCCESS, "sync", success);
            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (RuntimeException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_ORG_UPDATE", AuditStage.FAIL, "sync", failure);
            return ResponseEntity.status(500).body(ApiResponse.error("同步失败: " + ex.getMessage()));
        }
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AdminDataset d : datasetRepo.findAll()) out.add(toDatasetVM(d));
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_DATASET_VIEW",
            AuditStage.SUCCESS,
            "datasets",
            Map.of("count", out.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/custom-roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> customRoles() {
        var list = customRoleRepo.findAll().stream().map(this::toCustomRoleVM).toList();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_CUSTOM_ROLE_VIEW",
            AuditStage.SUCCESS,
            "custom-roles",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/custom-roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCustomRole(@RequestBody Map<String, Object> payload) {
        String rawName = Objects.toString(payload.get("name"), "").trim();
        if (rawName.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("角色 ID 不能为空"));
        }
        String normalizedName = stripRolePrefix(rawName);
        if (isReservedRealmRoleName(normalizedName)) {
            return ResponseEntity.status(409).body(ApiResponse.error("内置角色不可创建"));
        }
        if (locateCustomRole(normalizedName).isPresent()) {
            return ResponseEntity.status(409).body(ApiResponse.error("角色名称已存在"));
        }
        String titleCn = Objects.toString(payload.getOrDefault("titleCn", payload.get("nameZh")), "").trim();
        if (titleCn.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("角色名称不能为空"));
        }
        String displayName = Objects.toString(payload.getOrDefault("displayName", titleCn), titleCn).trim();
        String titleEn = Objects.toString(payload.get("titleEn"), null);
        LinkedHashSet<String> operations = readStringList(payload.get("operations"))
            .stream()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(op -> op.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (operations.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请选择角色权限"));
        }
        if (!operations.stream().allMatch(op -> Set.of("read", "write", "export").contains(op))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("不支持的操作"));
        }
        String scope = Objects.toString(payload.get("scope"), "").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DEPARTMENT", "INSTITUTE").contains(scope)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("作用域无效"));
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", normalizedName);
        after.put("scope", scope);
        after.put("operations", new LinkedHashSet<>(operations));
        after.put("maxRows", payload.get("maxRows"));
        after.put("allowDesensitizeJson", Boolean.TRUE.equals(payload.get("allowDesensitizeJson")));
        after.put("description", Objects.toString(payload.get("description"), null));
        after.put("titleCn", titleCn);
        after.put("nameZh", titleCn);
        after.put("displayName", displayName);
        if (StringUtils.hasText(titleEn)) {
            after.put("titleEn", titleEn.trim());
        }
        ChangeRequest cr = changeRequestService.draft(
            "CUSTOM_ROLE",
            "CREATE",
            normalizedName,
            after,
            null,
            Objects.toString(payload.get("reason"), null)
        );
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_CUSTOM_ROLE_REQUEST",
            AuditStage.SUCCESS,
            normalizedName,
            Map.of("changeRequestId", cr.getId())
        );
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/role-assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roleAssignments() {
        var list = roleAssignRepo.findAll().stream().map(this::toRoleAssignmentVM).toList();
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_ASSIGNMENT_VIEW",
            AuditStage.SUCCESS,
            "role-assignments",
            Map.of("count", list.size())
        );
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_ROLE_ASSIGNMENT_CREATE",
            AuditStage.SUCCESS,
            username,
            Map.of("changeRequestId", cr.getId(), "role", role)
        );
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/change-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> changeRequests(@RequestParam(required = false) String status, @RequestParam(required = false, name = "type") String resourceType) {
        List<ChangeRequest> list;
        if (status != null && resourceType != null) list = crRepo.findByStatusAndResourceType(status, resourceType); else if (status != null) list = crRepo.findByStatus(status); else list = crRepo.findAll();
        LinkedHashMap<Long, Map<String, Object>> viewById = new LinkedHashMap<>();
        for (ChangeRequest cr : list) {
            Map<String, Object> vm = toChangeVM(cr);
            viewById.put(cr.getId(), vm);
        }
        augmentChangeRequestViewsFromApprovals(viewById);
        List<Map<String, Object>> responseList = new ArrayList<>(viewById.values());
        responseList.sort((a, b) -> compareByRequestedAtDesc(a, b));
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_CHANGE_REQUEST_VIEW",
            AuditStage.SUCCESS,
            "change-requests",
            Map.of("count", responseList.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(responseList));
    }

    @GetMapping("/change-requests/mine")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myChangeRequests() {
        String me = SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
        LinkedHashMap<Long, Map<String, Object>> viewById = new LinkedHashMap<>();
        crRepo
            .findByRequestedBy(me)
            .forEach(cr -> viewById.put(cr.getId(), toChangeVM(cr)));
        augmentChangeRequestViewsFromApprovalsForActor(viewById, me);
        List<Map<String, Object>> list = new ArrayList<>(viewById.values());
        list.sort((a, b) -> compareByRequestedAtDesc(a, b));
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_CHANGE_REQUEST_VIEW",
            AuditStage.SUCCESS,
            me,
            Map.of("scope", "mine")
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 维护：清理所有“提交申请(变更请求)”与“审批请求”的历史数据。
     * 仅限系统管理员或授权管理员调用。
     */
    @PostMapping("/maintenance/purge-requests")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_AUTH_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purgeRequests(@RequestBody(required = false) Map<String, Object> body) {
        String actor = com.yuzhi.dts.admin.security.SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
        long approvals = approvalRepo.count();
        long changes = crRepo.count();

        // 先清理审批请求（级联删除其 items），再清理变更请求
        approvalRepo.deleteAllInBatch();
        crRepo.deleteAllInBatch();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedApprovals", approvals);
        result.put("deletedChangeRequests", changes);
        auditService.recordAction(
            actor,
            "ADMIN_CHANGE_REQUEST_MANAGE",
            AuditStage.SUCCESS,
            "purge",
            Map.of("removed", result)
        );
        return ResponseEntity.ok(ApiResponse.ok(result));
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
        cr.setCategory(Objects.toString(payload.get("category"), "GENERAL"));
        cr.setLastError(null);
        crRepo.save(cr);
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_CHANGE_REQUEST_MANAGE",
            AuditStage.SUCCESS,
            String.valueOf(cr.getId()),
            Map.of("action", "CREATE")
        );
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitChangeRequest(@PathVariable String id) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        // Normalize category if client didn't set it explicitly
        if (cr.getCategory() == null || cr.getCategory().isBlank()) {
            String type = cr.getResourceType();
            String category = changeRequestService
                .getClass() // no-op to keep bean reference
                .getSimpleName() // avoid unused warning
                .isEmpty() ? null : null; // noop
            // Reuse resolver logic by mirroring ChangeRequestService behavior
            String resolved = switch (type == null ? "" : type.toUpperCase(java.util.Locale.ROOT)) {
                case "USER" -> "USER_MANAGEMENT";
                case "ROLE" -> "ROLE_MANAGEMENT";
                case "PORTAL_MENU" -> "ROLE_MANAGEMENT";
                case "CONFIG" -> "SYSTEM_CONFIG";
                case "ORG" -> "ORGANIZATION";
                case "CUSTOM_ROLE" -> "CUSTOM_ROLE";
                case "ROLE_ASSIGNMENT" -> "ROLE_ASSIGNMENT";
                default -> "GENERAL";
            };
            cr.setCategory(resolved);
        }
        cr.setStatus("PENDING");
        crRepo.save(cr);
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_CHANGE_REQUEST_MANAGE",
            AuditStage.SUCCESS,
            id,
            Map.of("action", "SUBMIT")
        );
        try {
            // Notify approvers (e.g., AUTH_ADMIN) that a new request arrived
            notifyClient.trySend(
                "approval_pending",
                Map.of(
                    "id",
                    id,
                    "type",
                    cr.getResourceType(),
                    "category",
                    cr.getCategory(),
                    "requestedBy",
                    cr.getRequestedBy()
                )
            );
        } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveChangeRequest(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        String actor = SecurityUtils.getCurrentUserLogin().orElse("authadmin");
        cr.setStatus("APPROVED");
        cr.setDecidedBy(actor);
        cr.setDecidedAt(Instant.now());
        cr.setReason(body != null ? Objects.toString(body.get("reason"), null) : null);
        applyChangeRequest(cr, actor);
        crRepo.save(cr);
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_CHANGE_REQUEST_MANAGE",
            AuditStage.SUCCESS,
            id,
            Map.of("action", "APPROVE")
        );
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
        cr.setLastError(null);
        crRepo.save(cr);
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("unknown"),
            "ADMIN_CHANGE_REQUEST_MANAGE",
            AuditStage.SUCCESS,
            id,
            Map.of("action", "REJECT")
        );
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminRoles() {
        List<KeycloakRoleDTO> realmRoles = adminUserService.listRealmRoles();
        List<Map<String, Object>> list = new ArrayList<>();
        Instant now = Instant.now();
        Set<String> emitted = new HashSet<>();
        for (KeycloakRoleDTO role : realmRoles) {
            String name = role.getName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (isReservedRealmRoleName(name)) {
                continue;
            }
            String canonical = stripRolePrefix(name);
            BuiltinRoleSpec builtin = BUILTIN_DATA_ROLES.get(canonical);
            Map<String, Object> summary = toRoleSummary(role, canonical, builtin, now);
            // Include legacy role name if Keycloak尚未迁移
            String rawUpper = name.trim().toUpperCase(Locale.ROOT);
            if (!rawUpper.equals(canonical)) {
                summary.put("legacyName", name);
            }
            // Enrich with Keycloak member count (realm role membership)
            try {
                int kcMembers = adminUserService.countUsersByRealmRole(canonical);
                if (kcMembers == 0) {
                    String legacy = resolveLegacyRole(canonical);
                    if (legacy != null) {
                        kcMembers = adminUserService.countUsersByRealmRole(legacy);
                    }
                }
                summary.put("memberCount", kcMembers);
                summary.put("kcMemberCount", kcMembers);
            } catch (Exception ignored) {}
            try {
                int menuBindings = 0;
                for (String authority : authorityCandidates(canonical)) {
                    menuBindings += visibilityRepo.findByRoleCode(authority).size();
                }
                summary.put("menuBindings", menuBindings);
            } catch (Exception ignored) {
                summary.put("menuBindings", 0);
            }
            try {
                Optional<AdminCustomRole> customRole = locateCustomRole(canonical);
                boolean custom = customRole.isPresent();
                summary.put("customRole", custom);
                summary.put("customRoleId", custom ? customRole.map(AdminCustomRole::getId).orElse(null) : null);
            } catch (Exception ignored) {
                summary.put("customRole", false);
            }
            try {
                long assignmentCount = roleAssignRepo
                    .findAll()
                    .stream()
                    .map(AdminRoleAssignment::getRole)
                    .map(AdminApiResource::stripRolePrefix)
                    .filter(roleName -> canonical.equalsIgnoreCase(roleName))
                    .count();
                summary.put("assignments", (int) assignmentCount);
            } catch (Exception ignored) {
                summary.put("assignments", 0);
            }
            list.add(summary);
            emitted.add(canonical);
        }

        for (Map.Entry<String, BuiltinRoleSpec> entry : BUILTIN_DATA_ROLES.entrySet()) {
            if (emitted.contains(entry.getKey())) {
                continue;
            }
            Map<String, Object> summary = toRoleSummary(null, entry.getKey(), entry.getValue(), now);
            // Builtin roles may not exist in Keycloak; memberCount stays 0
            list.add(summary);
        }

        list.sort(Comparator.comparing(o -> Objects.toString(o.get("name"), "")));

        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            "admin-roles",
            Map.of("count", list.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 角色删除前的影响面预检：返回是否保留、Keycloak 成员数、菜单绑定数量、是否为自定义角色以及业务授权计数。
     */
    @GetMapping("/roles/{name}/pre-delete-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preDeleteRole(@PathVariable String name) {
        String raw = Objects.toString(name, "");
        String normalized = stripRolePrefix(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", normalized);
        boolean reserved = isReservedRealmRoleName(raw);
        out.put("reserved", reserved);
        out.put("deletable", !reserved);
        try {
            boolean existsInKeycloak = adminUserService.realmRoleExists(normalized);
            if (!existsInKeycloak) {
                String legacy = resolveLegacyRole(normalized);
                if (legacy != null) {
                    existsInKeycloak = adminUserService.realmRoleExists(legacy);
                }
            }
            out.put("existsInKeycloak", existsInKeycloak);
        } catch (Exception ex) {
            out.put("existsInKeycloak", false);
            out.put("kcCheckError", ex.getMessage());
        }
        try {
            int kcMembers = adminUserService.countUsersByRealmRole(normalized);
            if (kcMembers == 0) {
                String legacy = resolveLegacyRole(normalized);
                if (legacy != null) {
                    kcMembers = adminUserService.countUsersByRealmRole(legacy);
                }
            }
            out.put("kcMemberCount", kcMembers);
        } catch (Exception ex) {
            out.put("kcMemberCount", 0);
        }
        try {
            int menuBindings = 0;
            for (String authority : authorityCandidates(normalized)) {
                menuBindings += visibilityRepo.findByRoleCode(authority).size();
            }
            out.put("menuBindings", menuBindings);
        } catch (Exception ex) {
            out.put("menuBindings", 0);
        }
        try {
            Optional<AdminCustomRole> customRole = locateCustomRole(normalized);
            boolean custom = customRole.isPresent();
            out.put("customRole", custom);
            out.put("customRoleId", custom ? customRole.map(AdminCustomRole::getId).orElse(null) : null);
        } catch (Exception ex) {
            out.put("customRole", false);
        }
        try {
            long assignmentCount = roleAssignRepo
                .findAll()
                .stream()
                .map(AdminRoleAssignment::getRole)
                .map(AdminApiResource::stripRolePrefix)
                .filter(roleName -> normalized.equalsIgnoreCase(roleName))
                .count();
            out.put("assignments", (int) assignmentCount);
        } catch (Exception ex) {
            out.put("assignments", 0);
        }
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_ROLE_VIEW",
            AuditStage.SUCCESS,
            normalized,
            out
        );
        return ResponseEntity.ok(ApiResponse.ok(out));
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
        auditService.recordAction(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            "ADMIN_SETTING_VIEW",
            AuditStage.SUCCESS,
            "permissions",
            Map.of("count", sections.size())
        );
        return ResponseEntity.ok(ApiResponse.ok(sections));
    }

    private List<Map<String, Object>> buildOrgTree() {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (OrganizationNode node : orgService.findTree()) {
            tree.add(toOrgVM(node, List.of()));
        }
        return tree;
    }

    private static Map<String, Object> toOrgVM(OrganizationNode e, List<String> ancestors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("dataLevel", e.getDataLevel());
        m.put("sensitivity", e.getDataLevel());
        m.put("parentId", e.getParent() != null ? e.getParent().getId() : null);
        m.put("contact", e.getContact());
        m.put("phone", e.getPhone());
        m.put("description", e.getDescription());
        if (StringUtils.hasText(e.getKeycloakGroupId())) {
            m.put("keycloakGroupId", e.getKeycloakGroupId());
        }
        List<String> path = new ArrayList<>(ancestors);
        path.add(e.getName());
        m.put("path", path);
        m.put("groupPath", "/" + String.join("/", path));
        if (e.getChildren() != null && !e.getChildren().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (OrganizationNode c : e.getChildren()) {
                children.add(toOrgVM(c, path));
            }
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
        m.put("keycloakGroupId", node.getKeycloakGroupId());
        return m;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
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
        m.put("securityLevel", menu.getSecurityLevel());
        m.put("parentId", menu.getParent() != null ? menu.getParent().getId() : null);
        List<Map<String, Object>> visibilityRules = menu
            .getVisibilities()
            .stream()
            .map(this::toVisibilityRule)
            .toList();
        m.put("visibilityRules", visibilityRules);
        m.put("allowedRoles", visibilityRules.stream().map(rule -> Objects.toString(rule.get("role"), null)).filter(Objects::nonNull).distinct().toList());
        m.put("allowedPermissions", visibilityRules
            .stream()
            .map(rule -> Objects.toString(rule.get("permission"), null))
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList());
        m.put("maxDataLevel", deriveMenuMaxDataLevel(visibilityRules));
        return m;
    }

    private Map<String, Object> buildPortalMenuCollection() {
        List<PortalMenu> allMenus = portalMenuService.findAllMenusOrdered();
        Comparator<PortalMenu> ordering = menuOrdering();
        Map<Long, PortalMenu> idIndex = new LinkedHashMap<>();
        Map<Long, List<PortalMenu>> childrenLookup = new LinkedHashMap<>();
        List<PortalMenu> roots = new ArrayList<>();

        for (PortalMenu menu : allMenus) {
            if (menu.getId() != null) {
                idIndex.put(menu.getId(), menu);
            }
        }

        for (PortalMenu menu : allMenus) {
            Long parentId = menu.getParent() != null ? menu.getParent().getId() : null;
            if (parentId == null || !idIndex.containsKey(parentId)) {
                roots.add(menu);
            } else {
                childrenLookup.computeIfAbsent(parentId, key -> new ArrayList<>()).add(menu);
            }
        }

        roots.sort(ordering);
        for (List<PortalMenu> children : childrenLookup.values()) {
            children.sort(ordering);
        }

        List<Map<String, Object>> activeTree = new ArrayList<>();
        List<Map<String, Object>> fullTree = new ArrayList<>();
        for (PortalMenu root : roots) {
            Map<String, Object> fullNode = toMenuTreeNode(root, childrenLookup, true);
            if (fullNode != null) {
                fullTree.add(fullNode);
            }
            Map<String, Object> activeNode = toMenuTreeNode(root, childrenLookup, false);
            if (activeNode != null) {
                activeTree.add(activeNode);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("menus", activeTree);
        payload.put("allMenus", fullTree);
        return payload;
    }

    private Map<String, Object> filterFoundationForNonOpAdmin(Map<String, Object> payload) {
        try {
            Object all = payload.get("allMenus");
            if (all instanceof java.util.Collection<?> col) {
                java.util.List<Map<String, Object>> filtered = new java.util.ArrayList<>();
                for (Object o : col) {
                    if (!(o instanceof java.util.Map<?, ?> m)) continue;
                    if (!isFoundationNode((java.util.Map<String, Object>) m)) filtered.add((java.util.Map<String, Object>) m);
                }
                payload.put("allMenus", filtered);
            }
            Object act = payload.get("menus");
            if (act instanceof java.util.Collection<?> col2) {
                java.util.List<Map<String, Object>> filtered2 = new java.util.ArrayList<>();
                for (Object o : col2) {
                    if (!(o instanceof java.util.Map<?, ?> m)) continue;
                    if (!isFoundationNode((java.util.Map<String, Object>) m)) filtered2.add((java.util.Map<String, Object>) m);
                }
                payload.put("menus", filtered2);
            }
        } catch (Exception ignore) {}
        return payload;
    }

    private boolean isFoundationNode(Map<String, Object> node) {
        Object meta = node.get("metadata");
        if (meta instanceof String s) {
            String lower = s.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("\"sectionkey\":\"foundation\"")) {
                return true;
            }
        }
        return false;
    }

    private void applyMenuUpdates(PortalMenu target, Map<String, Object> payload) {
        if (payload.containsKey("name")) {
            String name = trimToNull(payload.get("name"));
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("菜单名称不能为空");
            }
            target.setName(name);
        }
        if (payload.containsKey("path")) {
            String path = trimToNull(payload.get("path"));
            if (!StringUtils.hasText(path)) {
                throw new IllegalArgumentException("菜单路径不能为空");
            }
            target.setPath(path);
        }
        if (payload.containsKey("component")) {
            target.setComponent(trimToNull(payload.get("component")));
        }
        if (payload.containsKey("icon")) {
            target.setIcon(trimToNull(payload.get("icon")));
        }
        if (payload.containsKey("sortOrder")) {
            Object sort = payload.get("sortOrder");
            target.setSortOrder(sort == null ? null : Integer.valueOf(sort.toString()));
        }
        if (payload.containsKey("metadata")) {
            target.setMetadata(normalizeMenuMetadata(payload.get("metadata")));
        }
        if (payload.containsKey("securityLevel")) {
            target.setSecurityLevel(normalizeMenuSecurityLevel(payload.get("securityLevel")));
        }
        if (payload.containsKey("parentId")) {
            Long parentId = toNullableLong(payload.get("parentId"));
            if (parentId == null) {
                target.setParent(null);
            } else {
                PortalMenu parent = portalMenuRepo
                    .findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("父菜单不存在"));
                if (target.getId() != null && Objects.equals(parent.getId(), target.getId())) {
                    throw new IllegalArgumentException("父菜单不能选择自身");
                }
                if (isDescendantOf(target, parent)) {
                    throw new IllegalArgumentException("父菜单不能选择当前菜单的子节点");
                }
                target.setParent(parent);
            }
        }
        if (payload.containsKey("deleted")) {
            boolean deleted = toBoolean(payload.get("deleted"));
            if (deleted) {
                markMenuDeleted(target);
            } else {
                restoreMenu(target);
            }
        }
    }

    private String normalizeMenuMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        try {
            return JSON_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("菜单元数据格式错误", ex);
        }
    }

    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return Long.valueOf(number.longValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.valueOf(text);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b.booleanValue();
        }
        if (value == null) {
            return false;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private boolean isDescendantOf(PortalMenu root, PortalMenu candidateParent) {
        if (root == null || candidateParent == null) {
            return false;
        }
        PortalMenu cursor = candidateParent;
        while (cursor != null) {
            if (root.getId() != null && cursor.getId() != null && Objects.equals(cursor.getId(), root.getId())) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
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
        if (body.containsKey("securityLevel")) m.put("securityLevel", body.get("securityLevel"));
        if (body.containsKey("sortOrder")) {
            Object v = body.get("sortOrder");
            m.put("sortOrder", v == null ? null : Integer.valueOf(v.toString()));
        }
        if (body.containsKey("parentId")) {
            Object v = body.get("parentId");
            m.put("parentId", v == null ? null : Long.valueOf(v.toString()));
        }
        if (body.containsKey("deleted")) {
            Object v = body.get("deleted");
            if (v instanceof Boolean b) {
                m.put("deleted", b);
            } else if (v != null) {
                m.put("deleted", Boolean.valueOf(v.toString()));
            }
        }
        List<String> allowedRoles = readStringList(body.get("allowedRoles"));
        if (!allowedRoles.isEmpty()) {
            m.put("allowedRoles", allowedRoles);
        }
        List<String> allowedPermissions = readStringList(body.get("allowedPermissions"));
        if (!allowedPermissions.isEmpty()) {
            m.put("allowedPermissions", allowedPermissions);
        }
        if (body.containsKey("maxDataLevel")) {
            m.put("maxDataLevel", Objects.toString(body.get("maxDataLevel"), null));
        }
        if (body.containsKey("visibilityRules")) {
            Object rules = body.get("visibilityRules");
            m.put("visibilityRules", normalizeVisibilityRules(rules));
        }
        return m;
    }


    private Map<String, Object> toVisibilityRule(PortalMenuVisibility visibility) {
        Map<String, Object> rule = new LinkedHashMap<>();
        String normalizedCode = normalizeRoleAuthority(visibility.getRoleCode());
        rule.put("role", normalizedCode != null ? normalizedCode : visibility.getRoleCode());
        if (visibility.getPermissionCode() != null && !visibility.getPermissionCode().isBlank()) {
            rule.put("permission", visibility.getPermissionCode());
        }
        rule.put("dataLevel", visibility.getDataLevel());
        return rule;
    }

    private String deriveMenuMaxDataLevel(List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) {
            return "INTERNAL";
        }
        String max = "INTERNAL";
        int priority = dataLevelPriority(max);
        for (Map<String, Object> rule : rules) {
            String level = normalizeDataLevelForVisibility(rule.get("dataLevel"));
            int candidate = dataLevelPriority(level);
            if (candidate > priority) {
                priority = candidate;
                max = level;
            }
        }
        return max;
    }

    private String normalizeRoleAuthority(String roleName) {
        String canonical = stripRolePrefix(roleName);
        if (!StringUtils.hasText(canonical)) {
            return null;
        }
        return "ROLE_" + canonical;
    }

    private List<Map<String, Object>> normalizeVisibilityRules(Object rules) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (!(rules instanceof Collection<?> collection)) {
            return normalized;
        }
        for (Object element : collection) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            String role = normalizeRoleCode(map.get("role"));
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String permission = map.get("permission") == null ? null : map.get("permission").toString().trim();
            String dataLevel = normalizeDataLevelForVisibility(map.get("dataLevel"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", role);
            if (StringUtils.hasText(permission)) {
                entry.put("permission", permission);
            }
            entry.put("dataLevel", dataLevel);
            normalized.add(entry);
        }
        return normalized;
    }

    private List<PortalMenuVisibility> buildVisibilityEntities(Map<String, Object> payload, PortalMenu menu) {
        List<PortalMenuVisibility> visibilities = new ArrayList<>();
        Object rulesObj = payload.get("visibilityRules");
        List<Map<String, Object>> rules = rulesObj instanceof List<?> list ? (List<Map<String, Object>>) rulesObj : List.of();
        if (!rules.isEmpty()) {
            for (Map<String, Object> rule : rules) {
                String role = normalizeRoleCode(rule.get("role"));
                if (!StringUtils.hasText(role)) {
                    continue;
                }
                String permission = rule.get("permission") == null ? null : rule.get("permission").toString().trim();
                String level = normalizeDataLevelForVisibility(rule.get("dataLevel"));
                if (isFoundationMenu(menu) && !AuthoritiesConstants.OP_ADMIN.equals(role)) {
                    // 基础数据功能仅允许 OP_ADMIN 绑定
                    continue;
                }
                visibilities.add(newVisibility(menu, role, permission, level));
            }
        } else {
            List<String> allowedRoles = payload.containsKey("allowedRoles") ? (List<String>) payload.get("allowedRoles") : List.of();
            List<String> allowedPermissions = payload.containsKey("allowedPermissions") ? (List<String>) payload.get("allowedPermissions") : List.of();
            String level = normalizeDataLevelForVisibility(payload.get("maxDataLevel"));
            if (!allowedRoles.isEmpty()) {
                if (allowedPermissions.isEmpty()) {
                    for (String role : allowedRoles) {
                        String normalized = normalizeRoleCode(role);
                        if (isFoundationMenu(menu) && !AuthoritiesConstants.OP_ADMIN.equals(normalized)) {
                            continue;
                        }
                        visibilities.add(newVisibility(menu, normalized, null, level));
                    }
                } else {
                    for (String role : allowedRoles) {
                        for (String permission : allowedPermissions) {
                            String normalized = normalizeRoleCode(role);
                            if (isFoundationMenu(menu) && !AuthoritiesConstants.OP_ADMIN.equals(normalized)) {
                                continue;
                            }
                            visibilities.add(newVisibility(menu, normalized, permission, level));
                        }
                    }
                }
            }
        }
        if (visibilities.isEmpty()) {
            visibilities = defaultVisibilities(menu);
        }
        // 基础数据功能：强制包含 OP_ADMIN 可见性，且忽略其它角色
        if (isFoundationMenu(menu)) {
            boolean hasOp = visibilities.stream().anyMatch(v -> AuthoritiesConstants.OP_ADMIN.equals(v.getRoleCode()));
            if (!hasOp) {
                visibilities.add(newVisibility(menu, AuthoritiesConstants.OP_ADMIN, null, "INTERNAL"));
            }
            // 仅保留 OP_ADMIN 规则
            visibilities = visibilities.stream().filter(v -> AuthoritiesConstants.OP_ADMIN.equals(v.getRoleCode())).toList();
        }
        return visibilities;
    }

    private List<PortalMenuVisibility> defaultVisibilities(PortalMenu menu) {
        List<PortalMenuVisibility> defaults = new ArrayList<>();
        boolean foundation = isFoundationMenu(menu);
        for (String role : DEFAULT_PORTAL_ROLES) {
            if (foundation && !AuthoritiesConstants.OP_ADMIN.equals(role)) {
                continue; // 基础数据功能仅 OP_ADMIN 默认可见
            }
            defaults.add(newVisibility(menu, role, null, "INTERNAL"));
        }
        return defaults;
    }

    private boolean isFoundationMenu(PortalMenu menu) {
        if (menu == null || menu.getMetadata() == null) return false;
        try {
            String meta = menu.getMetadata();
            String lc = meta.toLowerCase(java.util.Locale.ROOT);
            return lc.contains("\"sectionkey\":\"foundation\"");
        } catch (Exception e) {
            return false;
        }
    }

    private PortalMenuVisibility newVisibility(PortalMenu menu, String role, String permission, String dataLevel) {
        PortalMenuVisibility visibility = new PortalMenuVisibility();
        visibility.setMenu(menu);
        visibility.setRoleCode(normalizeRoleCode(role));
        visibility.setPermissionCode(StringUtils.hasText(permission) ? permission.trim() : null);
        visibility.setDataLevel(normalizeDataLevelForVisibility(dataLevel));
        return visibility;
    }

    private String normalizeRoleCode(Object role) {
        if (role == null) {
            return null;
        }
        String canonical = stripRolePrefix(role.toString());
        if (!StringUtils.hasText(canonical)) {
            return null;
        }
        return "ROLE_" + canonical;
    }

    private String normalizeDataLevelForVisibility(Object rawLevel) {
        if (rawLevel == null) {
            return "INTERNAL";
        }
        String text = rawLevel.toString().trim();
        if (text.isEmpty()) {
            return "INTERNAL";
        }
        String normalized = text.toUpperCase(Locale.ROOT).replace('-', '_');
        normalized = MENU_DATA_LEVEL_ALIAS.getOrDefault(normalized, normalized);
        if (!VISIBILITY_DATA_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException("菜单可见性密级不受支持: " + rawLevel);
        }
        return normalized;
    }

    private int dataLevelPriority(String level) {
        return switch (level == null ? "" : level.toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "NON_SECRET" -> 1;
            case "INTERNAL", "GENERAL" -> 2;
            case "SECRET", "IMPORTANT" -> 3;
            case "TOP_SECRET", "CORE" -> 4;
            default -> 0;
        };
    }

    private void applyChangeRequest(ChangeRequest cr, String actor) {
        try {
            cr.setLastError(null);
            if ("PORTAL_MENU".equalsIgnoreCase(cr.getResourceType())) {
                applyPortalMenuChange(cr);
            } else if ("CONFIG".equalsIgnoreCase(cr.getResourceType())) {
                applySystemConfigChange(cr);
            } else if ("ORG".equalsIgnoreCase(cr.getResourceType())) {
                applyOrganizationChange(cr);
            } else if ("ROLE".equalsIgnoreCase(cr.getResourceType())) {
                applyRoleChange(cr, actor);
            } else if ("CUSTOM_ROLE".equalsIgnoreCase(cr.getResourceType())) {
                applyCustomRoleChange(cr, actor);
            } else if ("ROLE_ASSIGNMENT".equalsIgnoreCase(cr.getResourceType())) {
                applyRoleAssignmentChange(cr, actor);
            }
        } catch (Exception e) {
            cr.setStatus("FAILED");
            cr.setLastError(e.getMessage());
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
            entity.setSecurityLevel(normalizeMenuSecurityLevel(payload.get("securityLevel")));
            entity.setDeleted(false);
            if (parentId != null) {
                // simple parent attach via find; if not found, leave as root
                portalMenuRepo.findById(parentId).ifPresent(entity::setParent);
            }
            portalMenuRepo.save(entity);
            List<PortalMenuVisibility> visibilities = buildVisibilityEntities(payload, entity);
            portalMenuService.replaceVisibilities(entity, visibilities);
            cr.setResourceId(String.valueOf(entity.getId()));
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
                    if (payload.containsKey("securityLevel")) {
                        target.setSecurityLevel(normalizeMenuSecurityLevel(payload.get("securityLevel")));
                    }
                    if (payload.containsKey("deleted")) {
                        Object dv = payload.get("deleted");
                        boolean flag;
                        if (dv instanceof Boolean b) {
                            flag = b.booleanValue();
                        } else {
                            String s = Objects.toString(dv, "");
                            flag = "true".equalsIgnoreCase(s) || "1".equals(s);
                        }
                        target.setDeleted(flag);
                    }
                    if (
                        payload.containsKey("visibilityRules") ||
                        payload.containsKey("allowedRoles") ||
                        payload.containsKey("allowedPermissions") ||
                        payload.containsKey("maxDataLevel")
                    ) {
                        List<PortalMenuVisibility> updatedVisibilities = buildVisibilityEntities(payload, target);
                        portalMenuService.replaceVisibilities(target, updatedVisibilities);
                    } else {
                        portalMenuRepo.save(target);
                    }
                });
        } else if ("BATCH_UPDATE".equalsIgnoreCase(action) || "BULK_UPDATE".equalsIgnoreCase(action)) {
            // Expect payload: { updates: [ { id: number, allowedRoles: string[], allowedPermissions?: string[], maxDataLevel?: string } ] }
            Object rawItems = payload.get("updates");
            if (rawItems instanceof java.util.Collection<?> col) {
                for (Object it : col) {
                    if (!(it instanceof java.util.Map<?, ?> m)) {
                        continue;
                    }
                    Object idObj = m.get("id");
                    if (idObj == null) {
                        continue;
                    }
                    Long id;
                    try {
                        id = Long.valueOf(idObj.toString());
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    final Long fid = id;
                    portalMenuRepo
                        .findById(fid)
                        .ifPresent(target -> {
                            // Build a minimal payload map for visibility update using existing helpers
                            java.util.Map<String, Object> updatePayload = new java.util.LinkedHashMap<>();
                            if (m.containsKey("allowedRoles")) updatePayload.put("allowedRoles", m.get("allowedRoles"));
                            if (m.containsKey("allowedPermissions")) updatePayload.put("allowedPermissions", m.get("allowedPermissions"));
                            if (m.containsKey("maxDataLevel")) updatePayload.put("maxDataLevel", m.get("maxDataLevel"));
                            if (m.containsKey("visibilityRules")) updatePayload.put("visibilityRules", m.get("visibilityRules"));
                            java.util.List<PortalMenuVisibility> updatedVisibilities = buildVisibilityEntities(updatePayload, target);
                            portalMenuService.replaceVisibilities(target, updatedVisibilities);
                        });
                }
            }
        } else if ("DELETE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            portalMenuRepo
                .findById(id)
                .ifPresent(target -> {
                    markMenuDeleted(target);
                    portalMenuRepo.save(target);
                });
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
            Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
            String description = Objects.toString(payload.get("description"), null);
            OrganizationNode created = orgService.create(name, parentId, description);
            cr.setResourceId(String.valueOf(created.getId()));
        } else if ("UPDATE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            organizationRepository
                .findById(id)
                .ifPresent(entity -> {
                    String nextName = Objects.toString(payload.getOrDefault("name", entity.getName()), entity.getName());
                    String nextDescription = Objects
                        .toString(payload.getOrDefault("description", entity.getDescription()), entity.getDescription());
                    Long nextParentId;
                    if (payload.containsKey("parentId")) {
                        Object parentRaw = payload.get("parentId");
                        nextParentId = parentRaw == null ? null : Long.valueOf(parentRaw.toString());
                    } else {
                        nextParentId = entity.getParent() == null ? null : entity.getParent().getId();
                    }
                    orgService.update(id, nextName, nextDescription, nextParentId);
                });
        } else if ("DELETE".equalsIgnoreCase(action)) {
            Long id = Long.valueOf(cr.getResourceId());
            orgService.delete(id);
        }
        cr.setStatus("APPLIED");
    }

    private void applyRoleChange(ChangeRequest cr, String actor) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        String rawName = Objects.toString(payload.get("name"), cr.getResourceId());
        String normalizedName = stripRolePrefix(rawName);
        org.slf4j.LoggerFactory.getLogger(AdminApiResource.class)
            .info("FE payload(role-change): action={}, rawName={}, normalizedName={}, scope={}, hasOps={}",
                action,
                rawName,
                normalizedName,
                Objects.toString(payload.get("scope"), null),
                payload.get("operations") != null);
        String auditAction = action == null ? "UNKNOWN" : action.toUpperCase(Locale.ROOT);
        try {
            if ("UPDATE".equalsIgnoreCase(action)) {
                if (!StringUtils.hasText(normalizedName)) {
                    throw new IllegalArgumentException("角色名称不能为空");
                }
                String scopeValue = Objects.toString(payload.get("scope"), null);
                String normalizedScope = scopeValue == null ? null : scopeValue.trim().toUpperCase(Locale.ROOT);
                LinkedHashSet<String> operations = readStringList(payload.get("operations"))
                    .stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(op -> op.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                if (operations.isEmpty()) {
                    throw new IllegalArgumentException("请至少保留一个操作权限");
                }
                String description = Objects.toString(payload.get("description"), null);
                String explicitDesc = Objects.toString(payload.getOrDefault("roleDesc", description), description);
                String titleCn = Objects.toString(payload.getOrDefault("titleCn", payload.get("nameZh")), null);
                String titleEn = Objects.toString(payload.getOrDefault("titleEn", payload.get("nameEn")), null);
                Integer maxRows = payload.get("maxRows") == null ? null : Integer.valueOf(payload.get("maxRows").toString());
                Boolean allowDesensitize = payload.containsKey("allowDesensitizeJson")
                    ? Boolean.TRUE.equals(payload.get("allowDesensitizeJson"))
                    : null;

                detail.put("scope", normalizedScope);
                detail.put("operations", new ArrayList<>(operations));
                detail.put("description", explicitDesc);
                if (maxRows != null) {
                    detail.put("maxRows", maxRows);
                }
                if (allowDesensitize != null) {
                    detail.put("allowDesensitizeJson", allowDesensitize);
                }
                if (StringUtils.hasText(titleCn)) {
                    detail.put("titleCn", titleCn);
                }
                if (StringUtils.hasText(titleEn)) {
                    detail.put("titleEn", titleEn);
                }

                if (!StringUtils.hasText(cr.getResourceId())) {
                    cr.setResourceId(normalizedName);
                }

                Map<String, String> roleAttributes = new LinkedHashMap<>();
                if (StringUtils.hasText(titleCn)) {
                    roleAttributes.put("titleCn", titleCn.trim());
                }
                if (StringUtils.hasText(titleEn)) {
                    roleAttributes.put("titleEn", titleEn.trim());
                }
                if (StringUtils.hasText(explicitDesc)) {
                    roleAttributes.put("roleDesc", explicitDesc.trim());
                }

                adminUserService.syncRealmRole(normalizedName, normalizedScope, operations, explicitDesc, roleAttributes);

                locateCustomRole(normalizedName)
                    .ifPresent(role -> {
                        role.setScope(normalizedScope);
                        role.setOperationsCsv(String.join(",", operations));
                        role.setDescription(explicitDesc);
                        if (maxRows != null) {
                            role.setMaxRows(maxRows);
                        }
                        if (allowDesensitize != null) {
                            role.setAllowDesensitizeJson(allowDesensitize);
                        }
                        customRoleRepo.save(role);
                    });

                cr.setStatus("APPLIED");
                auditService.recordAction(actor, "ADMIN_ROLE_UPDATE", AuditStage.SUCCESS, normalizedName, detail);
            } else if ("DELETE".equalsIgnoreCase(action)) {
                if (!StringUtils.hasText(normalizedName)) {
                    throw new IllegalArgumentException("角色名称不能为空");
                }
                if (isReservedRealmRoleName(normalizedName)) {
                    throw new IllegalArgumentException("内置角色不可删除");
                }
                // 1) 清理菜单可见性
                List<String> visibilityErrors = new ArrayList<>();
                for (String authority : authorityCandidates(normalizedName)) {
                    try {
                        visibilityRepo.deleteByRoleCode(authority);
                    } catch (Exception ex) {
                        visibilityErrors.add(authority + ":" + ex.getMessage());
                    }
                }
                if (!visibilityErrors.isEmpty()) {
                    detail.put("menuVisibilityCleanupError", String.join("; ", visibilityErrors));
                }
                // 2) 从所有用户移除该 Keycloak 角色并删除角色
                List<String> kcErrors = new ArrayList<>();
                for (String candidate : roleNameCandidates(normalizedName)) {
                    try {
                        adminUserService.deleteRealmRoleAndRemoveFromUsers(candidate);
                    } catch (Exception ex) {
                        kcErrors.add(candidate + ":" + ex.getMessage());
                    }
                }
                if (!kcErrors.isEmpty()) {
                    detail.put("keycloakCleanupError", String.join("; ", kcErrors));
                }
                // 3) 删除自定义角色记录（如果存在）
                try {
                    deleteCustomRoleIfExists(normalizedName);
                } catch (Exception ex) {
                    detail.put("customRoleCleanupError", ex.getMessage());
                }
                cr.setStatus("APPLIED");
                auditService.recordAction(actor, "ADMIN_ROLE_DELETE", AuditStage.SUCCESS, normalizedName, detail);
            } else {
                throw new IllegalStateException("未支持的角色操作: " + action);
            }
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            String failureCode = "DELETE".equalsIgnoreCase(auditAction) ? "ADMIN_ROLE_DELETE" : "ADMIN_ROLE_UPDATE";
            auditService.recordAction(actor, failureCode, AuditStage.FAIL, normalizedName, detail);
            throw ex;
        }
    }

    private void applyCustomRoleChange(ChangeRequest cr, String actor) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        String rawName = Objects.toString(payload.get("name"), cr.getResourceId());
        String normalizedName = stripRolePrefix(rawName);
        String auditAction = action == null ? "UNKNOWN" : action.toUpperCase(Locale.ROOT);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        try {
            if ("CREATE".equalsIgnoreCase(action)) {
                AdminCustomRole role = new AdminCustomRole();
                role.setName(normalizedName);
                String scopeValue = Objects.toString(payload.get("scope"), null);
                role.setScope(scopeValue == null ? null : scopeValue.toUpperCase(Locale.ROOT));
                LinkedHashSet<String> ops = readStringList(payload.get("operations"))
                    .stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(op -> op.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                role.setOperationsCsv(String.join(",", ops));
                Object maxRows = payload.get("maxRows");
                role.setMaxRows(maxRows == null ? null : Integer.valueOf(maxRows.toString()));
                role.setAllowDesensitizeJson(Boolean.TRUE.equals(payload.get("allowDesensitizeJson")));
                role.setDescription(Objects.toString(payload.get("description"), null));
                role = customRoleRepo.save(role);
                cr.setResourceId(String.valueOf(role.getId()));

                String titleCn = Objects.toString(payload.getOrDefault("titleCn", payload.get("nameZh")), "").trim();
                String titleEn = Objects.toString(payload.get("titleEn"), "").trim();
                String displayName = Objects.toString(payload.getOrDefault("displayName", titleCn), titleCn).trim();
                LinkedHashMap<String, String> roleAttributes = new LinkedHashMap<>();
                if (StringUtils.hasText(titleCn)) {
                    roleAttributes.put("titleCn", titleCn);
                    roleAttributes.put("nameZh", titleCn);
                    detail.put("titleCn", titleCn);
                }
                if (StringUtils.hasText(titleEn)) {
                    roleAttributes.put("titleEn", titleEn);
                    roleAttributes.put("nameEn", titleEn);
                    detail.put("titleEn", titleEn);
                }
                if (StringUtils.hasText(displayName)) {
                    roleAttributes.put("displayName", displayName);
                }

                // 注意：不再在创建自定义角色时批量同步“默认菜单可见性”，
                // 以免覆盖前端在同一流程中提交的“自定义菜单绑定(BATCH_UPDATE)”。
                // 若需要默认可见性，应通过单独的变更单或在无自定义绑定时由运维手工触发。
                try {
                    adminUserService.syncRealmRole(role.getName(), role.getScope(), ops, role.getDescription(), roleAttributes);
                } catch (Exception ex) {
                    // 同步失败不阻塞审批，通过审计日志追踪
                }
                detail.put("roleId", role.getId());
                detail.put("scope", role.getScope());
                detail.put("operations", ops);
            } else {
                throw new IllegalStateException("未支持的自定义角色操作: " + action);
            }
            cr.setStatus("APPLIED");
            auditService.recordAction(actor, "ADMIN_CUSTOM_ROLE_EXECUTE", AuditStage.SUCCESS, normalizedName, detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_CUSTOM_ROLE_EXECUTE", AuditStage.FAIL, normalizedName, detail);
            throw ex;
        }
    }

    private void applyRoleAssignmentChange(ChangeRequest cr, String actor) throws Exception {
        Map<String, Object> payload = fromJson(cr.getPayloadJson());
        String action = cr.getAction();
        String auditAction = action == null ? "UNKNOWN" : action.toUpperCase(Locale.ROOT);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        try {
            if ("CREATE".equalsIgnoreCase(action)) {
                AdminRoleAssignment assignment = new AdminRoleAssignment();
                assignment.setRole(stripRolePrefix(Objects.toString(payload.get("role"), null)));
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
                detail.put("assignmentId", assignment.getId());
                detail.put("role", assignment.getRole());
                detail.put("username", assignment.getUsername());
                detail.put("operations", ops);
                detail.put("datasetIds", datasetIds);
                try {
                    notifyClient.trySend(
                        "role_assignment_created",
                        Map.of("id", assignment.getId(), "username", assignment.getUsername(), "role", assignment.getRole())
                    );
                } catch (Exception ignored) {}
                auditService.recordAction(actor, "ADMIN_ROLE_ASSIGNMENT_CREATE", AuditStage.SUCCESS, assignment.getUsername(), detail);
            } else {
                throw new IllegalStateException("未支持的角色指派操作: " + action);
            }
            cr.setStatus("APPLIED");
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditService.recordAction(actor, "ADMIN_ROLE_ASSIGNMENT_CREATE", AuditStage.FAIL, Objects.toString(payload.get("username"), null), detail);
            throw ex;
        }
    }

    private String normalizeMenuSecurityLevel(Object rawLevel) {
        if (rawLevel == null) {
            return "GENERAL";
        }
        String text = rawLevel.toString().trim();
        if (text.isEmpty()) {
            return "GENERAL";
        }
        String normalized = text.toUpperCase(Locale.ROOT).replace('-', '_');
        if (!MENU_SECURITY_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的菜单密级: " + rawLevel);
        }
        return normalized;
    }

    private String normalizeReason(Object value) {
        if (value == null) {
            return null;
        }
        String reason = Objects.toString(value, "").trim();
        return reason.isEmpty() ? null : reason;
    }

    private void markMenuDeleted(PortalMenu menu) {
        menu.setDeleted(true);
        if (menu.getChildren() != null) {
            for (PortalMenu child : menu.getChildren()) {
                markMenuDeleted(child);
            }
        }
    }

    private void restoreMenu(PortalMenu menu) {
        menu.setDeleted(false);
        Long id = menu.getId();
        if (id == null) {
            if (menu.getChildren() != null) {
                for (PortalMenu child : menu.getChildren()) {
                    restoreMenu(child);
                }
            }
            return;
        }
        List<PortalMenu> children = portalMenuRepo.findByParentIdOrderBySortOrderAscIdAsc(id);
        for (PortalMenu child : children) {
            restoreMenu(child);
        }
    }

    private String resolveMenuDisplayName(PortalMenu menu) {
        String metadata = menu.getMetadata();
        if (metadata != null && !metadata.isBlank()) {
            try {
                Map<String, Object> meta = fromJson(metadata);
                Object title = meta.get("title");
                if (title instanceof String s && !s.isBlank()) {
                    return s;
                }
                Object label = meta.get("label");
                if (label instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (Exception ignored) {
                // fallback to raw name when metadata is not valid JSON
            }
        }
        return menu.getName();
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
        m.put("category", cr.getCategory());
        m.put("originalValue", extractValue(cr, "before"));
        m.put("updatedValue", extractValue(cr, "after"));
        m.put("lastError", cr.getLastError());
        return m;
    }

    private void augmentChangeRequestViewsFromApprovals(LinkedHashMap<Long, Map<String, Object>> viewById) {
        List<AdminApprovalRequest> approvals = approvalRepo.findAll();
        for (AdminApprovalRequest approval : approvals) {
            addChangeRequestViewsFromApproval(viewById, approval, null);
        }
    }

    private void augmentChangeRequestViewsFromApprovalsForActor(LinkedHashMap<Long, Map<String, Object>> viewById, String actor) {
        List<AdminApprovalRequest> approvals = approvalRepo.findAll();
        for (AdminApprovalRequest approval : approvals) {
            if (actor != null && !actor.equalsIgnoreCase(approval.getRequester())) {
                continue;
            }
            addChangeRequestViewsFromApproval(viewById, approval, actor);
        }
    }

    private void addChangeRequestViewsFromApproval(LinkedHashMap<Long, Map<String, Object>> viewById, AdminApprovalRequest approval, String actorScope) {
        if (approval == null || approval.getItems() == null || approval.getItems().isEmpty()) {
            return;
        }
        for (AdminApprovalItem item : approval.getItems()) {
            Map<String, Object> payload = parsePayload(item.getPayloadJson());
            Long crId = payload != null ? toLong(payload.get("changeRequestId")) : null;
            if (crId == null) {
                continue;
            }
            Map<String, Object> existing = viewById.get(crId);
            if (existing != null) {
                existing.put("status", approval.getStatus());
                existing.put("decidedBy", approval.getApprover());
                existing.put("decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
                if (!existing.containsKey("payloadJson") || existing.get("payloadJson") == null) {
                    existing.put("payloadJson", item.getPayloadJson());
                }
                if (approval.getErrorMessage() != null) {
                    existing.put("lastError", approval.getErrorMessage());
                }
                if (approval.getReason() != null) {
                    existing.put("reason", approval.getReason());
                }
                continue;
            }
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", crId);
            String targetKind = item.getTargetKind() != null ? item.getTargetKind().trim().toUpperCase(Locale.ROOT) : null;
            String action = payload != null ? stringValue(payload.get("action")) : null;
            vm.put("resourceType", targetKind != null && !targetKind.isBlank() ? targetKind : inferResourceTypeFromAction(action));
            vm.put("resourceId", item.getTargetId());
            vm.put("action", action != null ? action.toUpperCase(Locale.ROOT) : approval.getType());
            vm.put("payloadJson", item.getPayloadJson());
            vm.put("diffJson", null);
            vm.put("status", approval.getStatus());
            vm.put("requestedBy", approval.getRequester());
            vm.put("requestedAt", approval.getCreatedDate() != null ? approval.getCreatedDate().toString() : null);
            vm.put("decidedBy", approval.getApprover());
            vm.put("decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
            vm.put("reason", approval.getReason());
            vm.put("category", resolveApprovalCategory(approval.getType()));
            vm.put("originalValue", null);
            vm.put("updatedValue", null);
            vm.put("lastError", approval.getErrorMessage());
            if (actorScope == null || actorScope.equalsIgnoreCase(approval.getRequester())) {
                viewById.put(crId, vm);
            }
        }
    }

    private int compareByRequestedAtDesc(Map<String, Object> left, Map<String, Object> right) {
        Instant a = parseInstant(stringValue(left.get("requestedAt")));
        Instant b = parseInstant(stringValue(right.get("requestedAt")));
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    private Instant parseInstant(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String inferResourceTypeFromAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "USER";
        }
        String upper = action.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("ROLE")) {
            return "ROLE";
        }
        return "USER";
    }

    private String resolveApprovalCategory(String type) {
        if (type == null) {
            return "GENERAL";
        }
        if (type.startsWith("ROLE_")) {
            return "ROLE_MANAGEMENT";
        }
        return "USER_MANAGEMENT";
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(payloadJson, Map.class);
        } catch (Exception ex) {
            log.debug("Failed to parse approval payload: {}", ex.getMessage());
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return text.isBlank() ? null : Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Object extractValue(ChangeRequest cr, String key) {
        Object value = extractChangedValues(cr.getDiffJson(), key);
        if (value == null && "after".equals(key)) {
            value = parseJsonOrRaw(cr.getPayloadJson());
        }
        return value;
    }

    private static Object extractChangedValues(String diffJson, String key) {
        if (diffJson == null || diffJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(diffJson);
            JsonNode changes = root.path("changes");
            if (changes.isArray() && changes.size() > 0) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (JsonNode change : changes) {
                    JsonNode fieldNode = change.path("field");
                    if (fieldNode.isMissingNode() || fieldNode.isNull()) {
                        continue;
                    }
                    JsonNode valueNode = change.path(key);
                    if (valueNode.isMissingNode()) {
                        continue;
                    }
                    try {
                        Object converted = convertNode(valueNode);
                        result.put(fieldNode.asText(), converted);
                    } catch (JsonProcessingException ignored) {
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
            JsonNode beforeNode = root.path("before");
            JsonNode afterNode = root.path("after");
            if (beforeNode.isObject() && afterNode.isObject()) {
                Map<String, Object> result = new LinkedHashMap<>();
                Set<String> fields = new LinkedHashSet<>();
                beforeNode.fieldNames().forEachRemaining(fields::add);
                afterNode.fieldNames().forEachRemaining(fields::add);
                for (String field : fields) {
                    JsonNode beforeValue = beforeNode.get(field);
                    JsonNode afterValue = afterNode.get(field);
                    if (Objects.equals(beforeValue, afterValue)) {
                        continue;
                    }
                    JsonNode targetNode = "before".equals(key) ? beforeValue : afterValue;
                    if (targetNode == null) {
                        continue;
                    }
                    try {
                        result.put(field, convertNode(targetNode));
                    } catch (JsonProcessingException ignored) {
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (JsonProcessingException e) {
            return null;
        }
        return null;
    }

    private static Object parseJsonOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(raw);
            return convertNode(node);
        } catch (JsonProcessingException e) {
            return raw;
        }
    }

    private static Object convertNode(JsonNode node) throws JsonProcessingException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray() || node.isObject()) {
            return JSON_MAPPER.treeToValue(node, Object.class);
        }
        return node.toString();
    }

    private Comparator<PortalMenu> menuOrdering() {
        return Comparator
            .comparing((PortalMenu menu) -> menu.getSortOrder() == null ? Integer.MAX_VALUE : menu.getSortOrder())
            .thenComparingLong(menu -> menu.getId() == null ? Long.MAX_VALUE : menu.getId());
    }

    private Map<String, Object> toMenuTreeNode(PortalMenu menu, Map<Long, List<PortalMenu>> childrenLookup, boolean includeDeleted) {
        if (!includeDeleted && menu.isDeleted()) {
            return null;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", menu.getId());
        node.put("name", menu.getName());
        node.put("displayName", resolveMenuDisplayName(menu));
        node.put("path", menu.getPath());
        node.put("component", menu.getComponent());
        node.put("icon", menu.getIcon());
        node.put("sortOrder", menu.getSortOrder());
        node.put("metadata", menu.getMetadata());
        node.put("securityLevel", menu.getSecurityLevel());
        node.put("deleted", menu.isDeleted());
        node.put("parentId", menu.getParent() != null ? menu.getParent().getId() : null);
        List<Map<String, Object>> visibilityRules = menu.getVisibilities().stream().map(this::toVisibilityRule).toList();
        node.put("visibilityRules", visibilityRules);
        node.put(
            "allowedRoles",
            visibilityRules.stream().map(rule -> Objects.toString(rule.get("role"), null)).filter(Objects::nonNull).distinct().toList()
        );
        node.put(
            "allowedPermissions",
            visibilityRules
                .stream()
                .map(rule -> Objects.toString(rule.get("permission"), null))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList()
        );
        node.put("maxDataLevel", deriveMenuMaxDataLevel(visibilityRules));

        Long id = menu.getId();
        List<PortalMenu> children = id == null ? List.of() : childrenLookup.getOrDefault(id, List.of());
        if (!children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (PortalMenu child : children) {
                Map<String, Object> childNode = toMenuTreeNode(child, childrenLookup, includeDeleted);
                if (childNode != null) {
                    childNodes.add(childNode);
                }
            }
            if (!childNodes.isEmpty()) {
                node.put("children", childNodes);
            }
        }
        return node;
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
        m.put("description", r.getDescription());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedDate() != null ? r.getCreatedDate().toString() : null);
        return m;
    }



    private Map<String, Object> toRoleSummary(KeycloakRoleDTO role, String normalizedName, BuiltinRoleSpec builtin, Instant fallbackInstant) {
        Map<String, String> attributes = role != null && role.getAttributes() != null ? role.getAttributes() : Collections.emptyMap();
        LinkedHashSet<String> operations = parseOperations(attributes);
        if (operations.isEmpty() && builtin != null) {
            operations.addAll(builtin.operations());
        }
        String scope = firstNonBlank(attributes.get("scope"), builtin != null ? builtin.scope() : null);
        String description = firstNonBlank(
            role != null ? role.getDescription() : null,
            attributes.get("description"),
            attributes.get("roleDesc"),
            builtin != null ? builtin.description() : null
        );
        String securityLevel = firstNonBlank(attributes.get("securityLevel"), "GENERAL");
        int memberCount = parseInteger(attributes.get("memberCount"), attributes.get("members"));
        String approvalFlow = firstNonBlank(attributes.get("approvalFlow"), "SYSADMIN/AUTHADMIN");
        String updatedAt = firstNonBlank(attributes.get("updatedAt"), fallbackInstant.toString());

        // New fields aligned with worklog spec (minimal, derived when possible)
        String nameZh = firstNonBlank(attributes.get("nameZh"), attributes.get("titleCn"), builtin != null ? builtin.titleCn() : null);
        String nameEn = firstNonBlank(attributes.get("nameEn"), attributes.get("titleEn"), builtin != null ? builtin.titleEn() : null);
        String code = normalizedName;
        String zone = null;
        if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            zone = "DEPT";
        } else if ("INSTITUTE".equalsIgnoreCase(scope)) {
            zone = "INST";
        }
        boolean canRead = operations.contains("read");
        boolean canWrite = operations.contains("write");
        boolean canExport = operations.contains("export");
        boolean canManage =
            // Prefer explicit attribute, otherwise infer for *_OWNER
            Boolean.parseBoolean(firstNonBlank(attributes.get("canManage"), "false")) || normalizedName.endsWith("_OWNER");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", firstNonBlank(role != null ? role.getId() : null, "role-" + normalizedName));
        String displayName = firstNonBlank(attributes.get("displayName"), nameZh, role != null ? role.getName() : null, normalizedName);
        summary.put("name", displayName);
        summary.put("description", description);
        summary.put("securityLevel", securityLevel);
        summary.put("permissions", new ArrayList<>(operations));
        summary.put("memberCount", memberCount);
        summary.put("approvalFlow", approvalFlow);
        summary.put("updatedAt", updatedAt);
        if (scope != null) {
            summary.put("scope", scope);
        }
        summary.put("operations", new ArrayList<>(operations));
        summary.put("source", role != null ? "keycloak" : "builtin");

        // Additional presentation fields
        summary.put("code", code);
        summary.put("roleId", code);
        if (nameZh != null) summary.put("nameZh", nameZh);
        if (nameEn != null) summary.put("nameEn", nameEn);
        if (zone != null) summary.put("zone", zone);
        summary.put("canRead", canRead);
        summary.put("canWrite", canWrite);
        summary.put("canExport", canExport);
        summary.put("canManage", canManage);
        return summary;
    }

    private Optional<AdminCustomRole> locateCustomRole(String canonicalName) {
        if (!StringUtils.hasText(canonicalName)) {
            return Optional.empty();
        }
        Optional<AdminCustomRole> current = customRoleRepo.findByName(canonicalName);
        if (current.isEmpty()) {
            String legacy = resolveLegacyRole(canonicalName);
            if (legacy != null) {
                current = customRoleRepo.findByName(legacy);
            }
        }
        return current;
    }

    private void deleteCustomRoleIfExists(String canonicalName) {
        locateCustomRole(canonicalName).ifPresent(customRoleRepo::delete);
    }

    private static LinkedHashSet<String> parseOperations(Map<String, String> attributes) {
        LinkedHashSet<String> ops = new LinkedHashSet<>();
        if (attributes == null) {
            return ops;
        }
        String raw = attributes.get("operations");
        if (StringUtils.hasText(raw)) {
            LinkedHashSet<String> parsed = Arrays
                .stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            for (String op : OPERATION_ORDER) {
                if (parsed.remove(op)) {
                    ops.add(op);
                }
            }
            ops.addAll(parsed);
        }
        return ops;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static int parseInteger(String... values) {
        if (values == null) {
            return 0;
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
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
        if (orgId == null) return "全所共享区";
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
        if (isReservedRealmRoleName(role)) return "系统内置角色不支持在线授权";
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
                if (!Boolean.TRUE.equals(d.getIsInstituteShared())) return "数据集 " + d.getBusinessCode() + " 未进入全所共享区";
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
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        String canonical = canonicalReservedRole(normalized);
        if (RESERVED_REALM_ROLES.contains(canonical)) {
            return switch (canonical) {
                case "SYSADMIN", "OPADMIN" -> Set.of("read", "write", "export");
                case "AUTHADMIN" -> Set.of("read");
                case "AUDITADMIN" -> Set.of("read", "export");
                default -> Set.of("read");
            };
        }
        BuiltinRoleSpec builtin = BUILTIN_DATA_ROLES.get(normalized);
        if (builtin != null) {
            return new LinkedHashSet<>(builtin.operations());
        }
        return customRoleRepo
            .findByName(normalized)
            .map(cr -> Arrays
                .stream(cr.getOperationsCsv().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
            )
            .orElseGet(() -> new LinkedHashSet<>(List.of("read")));
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
