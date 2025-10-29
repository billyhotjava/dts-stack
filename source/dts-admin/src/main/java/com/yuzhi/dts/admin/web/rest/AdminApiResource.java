package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.MenuAuditContext;
import com.yuzhi.dts.admin.service.audit.RoleAuditContext;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.auditv2.AdminAuditOperation;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.api.ResultStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
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
import com.yuzhi.dts.common.audit.ChangeSnapshot;

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
        if (role == null) return false;
        // Business-reserved roles (platform governance/admin)
        if (RESERVED_REALM_ROLES.contains(canonicalReservedRole(role))) return true;
        // Keycloak built-ins that should never appear in admin role catalogs
        return isKeycloakBuiltInRealmRole(role);
    }

    /**
     * Detect Keycloak built-in realm roles that we must hide from role catalogs.
     * - offline_access
     * - uma_authorization
     * - default-roles-<clientId>
     */
    private static boolean isKeycloakBuiltInRealmRole(String role) {
        if (role == null) return false;
        String lower = role.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.isEmpty()) return false;
        if ("offline_access".equals(lower)) return true;
        if ("uma_authorization".equals(lower)) return true;
        if (lower.startsWith("default-roles-")) return true;
        return false;
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
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    private static String toCanonicalTriadRole(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        String upper = authority.trim().toUpperCase(Locale.ROOT);
        if (
            upper.equals(AuthoritiesConstants.SYS_ADMIN) ||
            upper.equals(AuthoritiesConstants.AUTH_ADMIN) ||
            upper.equals(AuthoritiesConstants.AUDITOR_ADMIN)
        ) {
            return upper;
        }
        if (upper.startsWith("ROLE")) {
            upper = upper.replaceFirst("^ROLE[_\\-]?", "");
        }
        String compact = upper.replaceAll("[^A-Z0-9]", "");
        if (compact.startsWith("SYS")) {
            return AuthoritiesConstants.SYS_ADMIN;
        }
        if (compact.startsWith("AUTH") || compact.startsWith("IAM")) {
            return AuthoritiesConstants.AUTH_ADMIN;
        }
        if (compact.startsWith("AUDIT") || compact.startsWith("AUDITOR") || compact.startsWith("SECURITYAUDITOR")) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        return null;
    }

    // --- Minimal audit endpoints ---
    private final AuditV2Service auditV2Service;
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
    private final TransactionTemplate changeApplyTx;
    private final ChangeSnapshotFormatter changeSnapshotFormatter;
    private final AdminAuditService adminAuditService;

    private static final Set<String> MENU_SECURITY_LEVELS = Set.of("NON_SECRET", "GENERAL", "IMPORTANT", "CORE");
    private static final Set<String> VISIBILITY_DATA_LEVELS = Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");
    private static final Map<String, String> MENU_DATA_LEVEL_ALIAS = Map.of(
        "GENERAL", "INTERNAL",
        "NON_SECRET", "PUBLIC",
        "IMPORTANT", "CONFIDENTIAL",
        "CORE", "CONFIDENTIAL"
    );
    private static final Map<String, String> MENU_DATA_LEVEL_LABELS = Map.of(
        "PUBLIC", "公开",
        "NON_SECRET", "非密",
        "GENERAL", "一般",
        "INTERNAL", "内部",
        "IMPORTANT", "重要",
        "SECRET", "秘密",
        "CONFIDENTIAL", "机密",
        "CORE", "核心"
    );
    private static final Map<String, String> MENU_SECURITY_LEVEL_LABELS = Map.of(
        "NON_SECRET", "非密",
        "PUBLIC", "公开",
        "GENERAL", "一般",
        "INTERNAL", "内部",
        "IMPORTANT", "重要",
        "CORE", "核心"
    );
    // Tighten default visibility: ROLE_USER is non-binding and should not be added by default
    private static final List<String> DEFAULT_PORTAL_ROLES = List.of(AuthoritiesConstants.OP_ADMIN);
    private static final Set<String> RESERVED_REALM_ROLES = Set.of("SYSADMIN", "OPADMIN", "AUTHADMIN", "AUDITADMIN");

    private static final List<String> OPERATION_ORDER = List.of("read", "write", "export");

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        Map.entry("DEPT_OWNER", "DEPT_DATA_OWNER"),
        Map.entry("DEPT_EDITOR", "DEPT_DATA_DEV"),
        Map.entry("INST_OWNER", "INST_DATA_OWNER"),
        Map.entry("INST_EDITOR", "INST_DATA_DEV")
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
        )
    );

    public AdminApiResource(
        AuditV2Service auditV2Service,
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
        AdminUserService adminUserService,
        ChangeSnapshotFormatter changeSnapshotFormatter,
        PlatformTransactionManager transactionManager,
        AdminAuditService adminAuditService
    ) {
        this.auditV2Service = auditV2Service;
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
        this.changeSnapshotFormatter = changeSnapshotFormatter;
        TransactionTemplate template = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(false);
        this.changeApplyTx = template;
        this.adminAuditService = Objects.requireNonNull(adminAuditService, "adminAuditService");
    }

    @org.springframework.beans.factory.annotation.Value("${dts.admin.require-approval.portal-menu.visibility:true}")
    private boolean requireMenuVisibilityApproval;

    @org.springframework.beans.factory.annotation.Value("${dts.admin.require-approval.portal-menu.structure:false}")
    private boolean requireMenuStructureApproval;

    // --- Placeholders to align with adminApi ---
    @GetMapping("/system/config")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> systemConfig(HttpServletRequest request) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SystemConfig c : sysCfgRepo.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("key", c.getKey());
            m.put("value", c.getValue());
            m.put("description", c.getDescription());
            list.add(m);
        }
        String actor = SecurityUtils.getCurrentAuditableLogin();
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.SYSTEM_CONFIG_VIEW)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("查看系统配置（共 " + list.size() + " 项）")
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", list.size())
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(request != null ? request.getRequestURI() : "/api/admin/system/config", "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for systemConfig: {}", ex.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/system/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftSystemConfig(@RequestBody Map<String, Object> cfg, HttpServletRequest request) {
        String key = Objects.toString(cfg.get("key"), "").trim();
        if (key.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("配置项 key 不能为空"));
        }
        Map<String, Object> before = sysCfgRepo.findByKey(key).map(this::toSystemConfigMap).orElse(null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("key", key);
        after.put("value", cfg.get("value"));
        after.put("description", cfg.get("description"));
        try {
            ChangeRequest cr = changeRequestService.draft("CONFIG", "CONFIG_SET", key, after, before, Objects.toString(cfg.get("reason"), null));
            Map<String, Object> auditDetail = new LinkedHashMap<>();
            auditDetail.put("draft", Boolean.TRUE);
            attachChangeRequestMetadata(auditDetail, cr);
            recordSystemConfigSubmitV2(
                SecurityUtils.getCurrentAuditableLogin(),
                cfg,
                before,
                after,
                cr,
                resolveClientIp(request),
                request != null ? request.getHeader("User-Agent") : null,
                request != null ? request.getRequestURI() : "/api/admin/system/config"
            );
            return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
        } catch (IllegalStateException ex) {
            recordSystemConfigSubmitFailureV2(
                SecurityUtils.getCurrentAuditableLogin(),
                cfg,
                before,
                ex,
                resolveClientIp(request),
                request != null ? request.getHeader("User-Agent") : null,
                request != null ? request.getRequestURI() : "/api/admin/system/config"
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/portal/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portalMenus(HttpServletRequest request) {
        Map<String, Object> payload = buildPortalMenuCollection();
        // 非 OP_ADMIN 隐藏“基础数据功能”区（foundation）
        if (SecurityUtils.hasCurrentUserNoneOfAuthorities(AuthoritiesConstants.OP_ADMIN)) {
            payload = filterFoundationForNonOpAdmin(payload);
        }
        Object menus = payload.get("menus");
        int sectionCount = menus instanceof java.util.Collection<?> col ? col.size() : 0;
        String actor = SecurityUtils.getCurrentAuditableLogin();
        if (!isAuditSuppressed(request)) {
            try {
                auditV2Service.record(
                    AuditActionRequest
                        .builder(actor, ButtonCodes.PORTAL_MENU_VIEW)
                        .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                        .summary("查看门户菜单（共 " + sectionCount + " 类）")
                        .result(AuditResultStatus.SUCCESS)
                        .metadata("sectionCount", sectionCount)
                        .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                        .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/portal/menus"), request != null ? request.getMethod() : "GET")
                        .allowEmptyTargets()
                        .build()
                );
            } catch (Exception ex) {
                log.warn("Failed to record V2 audit for portal menu view: {}", ex.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PostMapping("/portal/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMenu(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> payload = readPortalMenuPayload(body);
        String actor = SecurityUtils.getCurrentAuditableLogin();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("request", payload);
        String pendingRef = Objects.toString(payload.get("name"), "portal-menu");
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
                attachChangeRequestMetadata(approvalPayload, cr);
                // 记录变更单本身：target_table=change_request, target_id=cr.id
                recordPortalMenuActionV2(
                    actor,
                    MenuAuditContext.Operation.SUBMIT_APPROVAL,
                    AuditResultStatus.PENDING,
                    null,
                    pendingRef,
                    cr,
                    new LinkedHashMap<>(approvalPayload),
                    request,
                    "/api/admin/portal/menus",
                    "POST",
                    "提交新增菜单审批：" + pendingRef
                );
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
            Map<String, Object> createdPayload = toPortalMenuPayload(persisted);
            successDetail.put("created", createdPayload);
            appendChangeSummary(successDetail, "PORTAL_MENU", Map.of(), createdPayload);
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.CREATE,
                AuditResultStatus.SUCCESS,
                persisted.getId(),
                persisted.getName(),
                null,
                new LinkedHashMap<>(successDetail),
                request,
                "/api/admin/portal/menus",
                "POST",
                "新增菜单：" + persisted.getName()
            );
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "create", "id", String.valueOf(persisted.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (IllegalStateException ex) {
            Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
            failureDetail.put("error", ex.getMessage());
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.CREATE,
                AuditResultStatus.FAILED,
                null,
                pendingRef,
                null,
                new LinkedHashMap<>(failureDetail),
                request,
                "/api/admin/portal/menus",
                "POST",
                "新增菜单失败：" + pendingRef
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
            failureDetail.put("error", ex.getMessage());
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.CREATE,
                AuditResultStatus.FAILED,
                null,
                pendingRef,
                null,
                new LinkedHashMap<>(failureDetail),
                request,
                "/api/admin/portal/menus",
                "POST",
                "新增菜单失败：" + pendingRef
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
            failureDetail.put("error", ex.getMessage());
            log.error("Failed to create portal menu", ex);
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.CREATE,
                AuditResultStatus.FAILED,
                null,
                pendingRef,
                null,
                new LinkedHashMap<>(failureDetail),
                request,
                "/api/admin/portal/menus",
                "POST",
                "新增菜单失败：" + pendingRef
            );
            return ResponseEntity.internalServerError().body(ApiResponse.error("创建菜单失败: " + ex.getMessage()));
        }
    }

    @PostMapping("/portal/menus/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetMenus(HttpServletRequest request) {
        portalMenuService.resetMenusToSeed();
        String actor = SecurityUtils.getCurrentAuditableLogin();
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.PORTAL_MENU_UPDATE)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("重置门户菜单")
                    .result(AuditResultStatus.SUCCESS)
                    .detail("action", "reset")
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/portal/menus/reset"), request != null ? request.getMethod() : "POST")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for portal menu reset: {}", ex.getMessage());
        }
        return portalMenus(null);
    }

    @PutMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMenu(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long menuId = Long.valueOf(id);
        PortalMenu beforeEntity = portalMenuRepo.findById(menuId).orElse(null);
        if (beforeEntity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> payload = readPortalMenuPayload(body);
        Map<String, Object> before = toPortalMenuPayload(beforeEntity);
        String menuLabel = firstNonBlank(resolveMenuDisplayName(beforeEntity), beforeEntity.getName(), beforeEntity.getPath(), id);
        String actor = SecurityUtils.getCurrentAuditableLogin();
        Map<String, Object> auditBase = new LinkedHashMap<>();
        auditBase.put("before", before);
        auditBase.put("payload", payload);
        try {
            boolean visibilityTouchedGate = payload.containsKey("visibilityRules") || payload.containsKey("allowedRoles") || payload.containsKey("allowedPermissions") || payload.containsKey("maxDataLevel");
            boolean structureTouchedGate = payload.containsKey("name") || payload.containsKey("path") || payload.containsKey("component") || payload.containsKey("icon") || payload.containsKey("sortOrder") || payload.containsKey("parentId") || payload.containsKey("deleted");
            if ((requireMenuVisibilityApproval && visibilityTouchedGate) || (requireMenuStructureApproval && structureTouchedGate)) {
                boolean beforeDeleted = Boolean.TRUE.equals(before.get("deleted"));
                boolean togglingDeleted = payload.containsKey("deleted");
                boolean afterDeleted = togglingDeleted ? toBoolean(payload.get("deleted")) : beforeDeleted;
                boolean enabling = togglingDeleted && beforeDeleted && !afterDeleted;
                boolean disabling = togglingDeleted && !beforeDeleted && afterDeleted;
                String changeAction = enabling ? "ENABLE" : disabling ? "DISABLE" : "UPDATE";
                String pendingSummary = enabling
                    ? "提交菜单启用审批：" + menuLabel
                    : disabling ? "提交菜单禁用审批：" + menuLabel : "提交菜单修改审批：" + menuLabel;
                ChangeRequest cr = changeRequestService.draft(
                    "PORTAL_MENU",
                    changeAction,
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
                approvalDetail.put("summary", pendingSummary);
                approvalDetail.put("operationName", pendingSummary);
                if (enabling) {
                    approvalDetail.put("operationType", AuditOperationType.ENABLE.getCode());
                    approvalDetail.put("operationTypeText", AuditOperationType.ENABLE.getDisplayName());
                } else if (disabling) {
                    approvalDetail.put("operationType", AuditOperationType.DISABLE.getCode());
                    approvalDetail.put("operationTypeText", AuditOperationType.DISABLE.getDisplayName());
                }
                if (menuId != null) {
                    approvalDetail.put("menuId", menuId);
                }
                approvalDetail.put("menuCode", beforeEntity.getName());
                approvalDetail.put("menuTitle", menuLabel);
                approvalDetail.put("menuPath", beforeEntity.getPath());
                attachChangeRequestMetadata(approvalDetail, cr);
                recordPortalMenuActionV2(
                    actor,
                    MenuAuditContext.Operation.SUBMIT_APPROVAL,
                    AuditResultStatus.PENDING,
                    menuId,
                    menuLabel,
                    cr,
                    new LinkedHashMap<>(approvalDetail),
                    request,
                    "/api/admin/portal/menus/" + id,
                    "PUT",
                    pendingSummary
                );
                cr.setSummary(pendingSummary);
                crRepo.save(cr);
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
            Map<String, Object> after = toPortalMenuPayload(persisted);
            detail.put("after", after);
            appendChangeSummary(detail, "PORTAL_MENU", before, after);
            boolean beforeDeleted = Boolean.TRUE.equals(before.get("deleted"));
            boolean afterDeleted = Boolean.TRUE.equals(after.get("deleted"));
            boolean enabling = beforeDeleted && !afterDeleted;
            boolean disabling = !beforeDeleted && afterDeleted;
            String persistedLabel = firstNonBlank(resolveMenuDisplayName(persisted), persisted.getName(), persisted.getPath(), id);
            String successSummary = enabling ? "启用菜单：" + persistedLabel : "修改菜单：" + persistedLabel;
            detail.put("summary", successSummary);
            detail.put("operationName", successSummary);
            if (persisted.getId() != null) {
                detail.put("menuId", persisted.getId());
            }
            detail.put("menuCode", persisted.getName());
            detail.put("menuTitle", persistedLabel);
            detail.put("menuPath", persisted.getPath());
            if (enabling) {
                detail.put("operationType", AuditOperationType.ENABLE.getCode());
                detail.put("operationTypeText", AuditOperationType.ENABLE.getDisplayName());
            }
            MenuAuditContext.Operation auditOp = enabling
                ? MenuAuditContext.Operation.ENABLE
                : (disabling ? MenuAuditContext.Operation.DISABLE : MenuAuditContext.Operation.UPDATE);
            recordPortalMenuActionV2(
                actor,
                auditOp,
                AuditResultStatus.SUCCESS,
                persisted.getId(),
                persistedLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "PUT",
                successSummary
            );
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "update", "id", String.valueOf(persisted.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (IllegalStateException ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            boolean beforeDeleted = Boolean.TRUE.equals(before.get("deleted"));
            boolean togglingDeleted = payload.containsKey("deleted");
            boolean afterDeleted = togglingDeleted ? toBoolean(payload.get("deleted")) : beforeDeleted;
            boolean enabling = togglingDeleted && beforeDeleted && !afterDeleted;
            boolean disabling = togglingDeleted && !beforeDeleted && afterDeleted;
            String failureSummary = enabling ? "启用菜单失败：" + menuLabel : "修改菜单失败：" + menuLabel;
            detail.put("summary", failureSummary);
            detail.put("operationName", failureSummary);
            if (menuId != null) {
                detail.put("menuId", menuId);
            }
            detail.put("menuCode", beforeEntity != null ? beforeEntity.getName() : null);
            detail.put("menuTitle", menuLabel);
            detail.put("menuPath", beforeEntity != null ? beforeEntity.getPath() : null);
            if (enabling) {
                detail.put("operationType", AuditOperationType.ENABLE.getCode());
                detail.put("operationTypeText", AuditOperationType.ENABLE.getDisplayName());
            }
            MenuAuditContext.Operation auditOp = enabling
                ? MenuAuditContext.Operation.ENABLE
                : (disabling ? MenuAuditContext.Operation.DISABLE : MenuAuditContext.Operation.UPDATE);
            recordPortalMenuActionV2(
                actor,
                auditOp,
                AuditResultStatus.FAILED,
                menuId,
                menuLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "PUT",
                failureSummary
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            boolean beforeDeleted = Boolean.TRUE.equals(before.get("deleted"));
            boolean togglingDeleted = payload.containsKey("deleted");
            boolean afterDeleted = togglingDeleted ? toBoolean(payload.get("deleted")) : beforeDeleted;
            boolean enabling = togglingDeleted && beforeDeleted && !afterDeleted;
            boolean disabling = togglingDeleted && !beforeDeleted && afterDeleted;
            String failureSummary = enabling ? "启用菜单失败：" + menuLabel : "修改菜单失败：" + menuLabel;
            detail.put("summary", failureSummary);
            detail.put("operationName", failureSummary);
            if (enabling) {
                detail.put("operationType", AuditOperationType.ENABLE.getCode());
                detail.put("operationTypeText", AuditOperationType.ENABLE.getDisplayName());
            }
            MenuAuditContext.Operation auditOp = enabling
                ? MenuAuditContext.Operation.ENABLE
                : (disabling ? MenuAuditContext.Operation.DISABLE : MenuAuditContext.Operation.UPDATE);
            recordPortalMenuActionV2(
                actor,
                auditOp,
                AuditResultStatus.FAILED,
                menuId,
                menuLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "PUT",
                "修改菜单失败：" + menuLabel
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            log.error("Failed to update portal menu {}", id, ex);
            boolean beforeDeleted = Boolean.TRUE.equals(before.get("deleted"));
            boolean togglingDeleted = payload.containsKey("deleted");
            boolean afterDeleted = togglingDeleted ? toBoolean(payload.get("deleted")) : beforeDeleted;
            boolean enabling = togglingDeleted && beforeDeleted && !afterDeleted;
            boolean disabling = togglingDeleted && !beforeDeleted && afterDeleted;
            String failureSummary = enabling ? "启用菜单失败：" + menuLabel : "修改菜单失败：" + menuLabel;
            detail.put("summary", failureSummary);
            detail.put("operationName", failureSummary);
            if (enabling) {
                detail.put("operationType", AuditOperationType.ENABLE.getCode());
                detail.put("operationTypeText", AuditOperationType.ENABLE.getDisplayName());
            }
            MenuAuditContext.Operation auditOp = enabling
                ? MenuAuditContext.Operation.ENABLE
                : (disabling ? MenuAuditContext.Operation.DISABLE : MenuAuditContext.Operation.UPDATE);
            recordPortalMenuActionV2(
                actor,
                auditOp,
                AuditResultStatus.FAILED,
                menuId,
                menuLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "PUT",
                "修改菜单失败：" + menuLabel
            );
            return ResponseEntity.internalServerError().body(ApiResponse.error("更新菜单失败: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/portal/menus/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMenu(@PathVariable String id, HttpServletRequest request) {
        Long menuId = Long.valueOf(id);
        PortalMenu entity = portalMenuRepo.findById(menuId).orElse(null);
        if (entity == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("菜单不存在"));
        }
        Map<String, Object> before = toPortalMenuPayload(entity);
        String actor = SecurityUtils.getCurrentAuditableLogin();
        String menuLabel = firstNonBlank(resolveMenuDisplayName(entity), entity.getName(), entity.getPath(), id);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("before", before);
        if (requireMenuStructureApproval) {
            try {
                ChangeRequest cr = changeRequestService.draft(
                    "PORTAL_MENU",
                    "DISABLE",
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
                String pendingSummary = "提交菜单禁用审批：" + menuLabel;
                approvalDetail.put("summary", pendingSummary);
                approvalDetail.put("operationName", pendingSummary);
                approvalDetail.put("operationType", AuditOperationType.DISABLE.getCode());
                approvalDetail.put("operationTypeText", AuditOperationType.DISABLE.getDisplayName());
                approvalDetail.put("menuId", menuId);
                approvalDetail.put("menuCode", entity.getName());
                approvalDetail.put("menuTitle", menuLabel);
                approvalDetail.put("menuPath", entity.getPath());
                attachChangeRequestMetadata(approvalDetail, cr);
                recordPortalMenuActionV2(
                    actor,
                    MenuAuditContext.Operation.SUBMIT_APPROVAL,
                    AuditResultStatus.PENDING,
                    menuId,
                    menuLabel,
                    cr,
                    new LinkedHashMap<>(approvalDetail),
                    request,
                    "/api/admin/portal/menus/" + id,
                    "DELETE",
                    pendingSummary
                );
                cr.setSummary(pendingSummary);
                crRepo.save(cr);
                return ResponseEntity.status(202).body(ApiResponse.ok(toChangeVM(cr)));
            } catch (IllegalStateException ex) {
                Map<String, Object> failureDetail = new LinkedHashMap<>(auditDetail);
                failureDetail.put("error", ex.getMessage());
                failureDetail.put("summary", "禁用菜单失败：" + menuLabel);
                failureDetail.put("operationName", "禁用菜单失败：" + menuLabel);
                failureDetail.put("operationType", AuditOperationType.DISABLE.getCode());
                failureDetail.put("operationTypeText", AuditOperationType.DISABLE.getDisplayName());
                failureDetail.put("menuId", menuId);
                failureDetail.put("menuCode", entity.getName());
                failureDetail.put("menuTitle", menuLabel);
                failureDetail.put("menuPath", entity.getPath());
                recordPortalMenuActionV2(
                    actor,
                    MenuAuditContext.Operation.DISABLE,
                    AuditResultStatus.FAILED,
                    menuId,
                    menuLabel,
                    null,
                    new LinkedHashMap<>(failureDetail),
                    request,
                    "/api/admin/portal/menus/" + id,
                    "DELETE",
                    "禁用菜单失败：" + menuLabel
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
            }
        }
        try {
            markMenuDeleted(entity);
            portalMenuRepo.save(entity);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            Map<String, Object> after = toPortalMenuPayload(entity);
            detail.put("after", after);
            appendChangeSummary(detail, "PORTAL_MENU", before, after);
            detail.put("summary", "禁用菜单：" + menuLabel);
            detail.put("operationName", "禁用菜单：" + menuLabel);
            detail.put("operationType", AuditOperationType.DISABLE.getCode());
            detail.put("operationTypeText", AuditOperationType.DISABLE.getDisplayName());
            detail.put("menuId", entity.getId());
            detail.put("menuCode", entity.getName());
            detail.put("menuTitle", menuLabel);
            detail.put("menuPath", entity.getPath());
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.DISABLE,
                AuditResultStatus.SUCCESS,
                entity.getId(),
                menuLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "DELETE",
                "禁用菜单：" + menuLabel
            );
            try {
                notifyClient.trySend("portal_menu_updated", Map.of("action", "disable", "id", String.valueOf(entity.getId())));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(buildPortalMenuCollection()));
        } catch (Exception ex) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("error", ex.getMessage());
            detail.put("summary", "禁用菜单失败：" + menuLabel);
            detail.put("operationName", "禁用菜单失败：" + menuLabel);
            detail.put("operationType", AuditOperationType.DISABLE.getCode());
            detail.put("operationTypeText", AuditOperationType.DISABLE.getDisplayName());
            detail.put("menuId", menuId);
            detail.put("menuCode", entity.getName());
            detail.put("menuTitle", menuLabel);
            detail.put("menuPath", entity.getPath());
            log.error("Failed to disable portal menu {}", id, ex);
            recordPortalMenuActionV2(
                actor,
                MenuAuditContext.Operation.DISABLE,
                AuditResultStatus.FAILED,
                menuId,
                menuLabel,
                null,
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/portal/menus/" + id,
                "DELETE",
                "禁用菜单失败：" + menuLabel
            );
            return ResponseEntity.internalServerError().body(ApiResponse.error("禁用菜单失败: " + ex.getMessage()));
        }
    }

    @GetMapping("/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> orgs(HttpServletRequest request) {
        List<Map<String, Object>> tree = buildOrgTree();
        if (!isAuditSuppressed(request)) {
            recordOrgViewV2(
                SecurityUtils.getCurrentAuditableLogin(),
                tree.size(),
                request,
                false
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    // Platform-friendly orgs endpoint (no triad token required; see SecurityConfiguration)
    @GetMapping("/platform/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> orgsForPlatform(HttpServletRequest request) {
        List<Map<String, Object>> tree = buildOrgTree();
        if (!isAuditSuppressed(request)) {
            recordOrgViewV2(
                SecurityUtils.getCurrentAuditableLogin(),
                tree.size(),
                request,
                true
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @PostMapping("/platform/orgs/sync")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> syncOrgsForPlatform(HttpServletRequest request) {
        try {
            organizationSyncService.ensureUnassignedRoot();
        } catch (RuntimeException ex) {
            log.warn("ensureUnassignedRoot failed: {}", ex.getMessage());
        }
        List<Map<String, Object>> tree = buildOrgTree();
        String actor = SecurityUtils.getCurrentAuditableLogin();
        recordOrgActionV2(
            actor,
            ButtonCodes.ORG_SYNC,
            AuditResultStatus.SUCCESS,
            null,
            null,
            Map.of("nodeCount", tree.size(), "synced", true),
            request,
            "/api/admin/platform/orgs/sync",
            "POST",
            "同步组织结构至平台成功",
            true
        );
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @PostMapping("/orgs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> createOrg(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String name = trimToNull(payload.get("name"));
        Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
        String description = trimToNull(payload.get("description"));
        Boolean isRoot = parseBoolean(payload.get("isRoot"));
        boolean rootFlag = Boolean.TRUE.equals(isRoot);
        String actor = SecurityUtils.getCurrentAuditableLogin();
        String parentRef = parentId == null ? "root" : String.valueOf(parentId);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("name", name);
        if (parentId != null) {
            auditDetail.put("parentId", parentId);
        }
        if (StringUtils.hasText(description)) {
            auditDetail.put("description", description);
        }
        if (isRoot != null) {
            auditDetail.put("isRoot", rootFlag);
        }

        if (!StringUtils.hasText(name)) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", "部门名称不能为空");
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_CREATE,
                AuditResultStatus.FAILED,
                null,
                null,
                failure,
                request,
                "/api/admin/orgs",
                "POST",
                "新增部门失败（名称为空）",
                true
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("部门名称不能为空"));
        }
        try {
            OrganizationNode created = orgService.create(name, parentId, description, rootFlag);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", created.getName());
            if (created.getParent() != null) {
                detail.put("parentId", created.getParent().getId());
            }
            if (StringUtils.hasText(description)) {
                detail.put("description", description);
            }
            if (created.isRoot()) {
                detail.put("isRoot", true);
            }
            detail.put("created", Map.of("id", created.getId(), "name", created.getName()));
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_CREATE,
                AuditResultStatus.SUCCESS,
                created.getId(),
                created.getName(),
                new LinkedHashMap<>(detail),
                request,
                "/api/admin/orgs",
                "POST",
                "新增部门：" + created.getName(),
                false
            );

            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_CREATE,
                AuditResultStatus.FAILED,
                null,
                null,
                failure,
                request,
                "/api/admin/orgs",
                "POST",
                "新增部门失败：" + ex.getMessage(),
                true
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> updateOrg(@PathVariable long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        OrganizationNode existing = organizationRepository.findById(id).orElse(null);
        if (existing == null) {
            recordOrgActionV2(
                SecurityUtils.getCurrentAuditableLogin(),
                ButtonCodes.ORG_UPDATE,
                AuditResultStatus.FAILED,
                id,
                null,
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/admin/orgs/" + id,
                "PUT",
                "修改部门失败：部门不存在",
                false
            );
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
        beforeView.put("isRoot", existing.isRoot());

        String name = payload.containsKey("name") ? trimToNull(payload.get("name")) : existing.getName();
        String description = payload.containsKey("description") ? trimToNull(payload.get("description")) : existing.getDescription();
        Long parentId = payload.containsKey("parentId")
            ? (payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString()))
            : (existing.getParent() == null ? null : existing.getParent().getId());
        Boolean isRoot = payload.containsKey("isRoot") ? parseBoolean(payload.get("isRoot")) : null;

        if (!StringUtils.hasText(name)) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("before", beforeView);
            failure.put("error", "部门名称不能为空");
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
        if (isRoot != null) {
            requestView.put("isRoot", isRoot);
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("before", beforeView);
        auditDetail.put("request", requestView);
        String actor = SecurityUtils.getCurrentAuditableLogin();

        Optional<OrganizationNode> updated;
        try {
            updated = orgService.update(id, name, description, parentId, isRoot);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_UPDATE,
                AuditResultStatus.FAILED,
                id,
                existing.getName(),
                failure,
                request,
                "/api/admin/orgs/" + id,
                "PUT",
                "修改部门失败：" + ex.getMessage(),
                false
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
        if (updated.isEmpty()) {
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_UPDATE,
                AuditResultStatus.FAILED,
                id,
                existing.getName(),
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/admin/orgs/" + id,
                "PUT",
                "修改部门失败：部门不存在",
                false
            );
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
        afterView.put("isRoot", updatedNode.isRoot());
        Map<String, Object> successDetail = new LinkedHashMap<>();
        successDetail.put("before", beforeView);
        successDetail.put("after", afterView);
        recordOrgActionV2(
            actor,
            ButtonCodes.ORG_UPDATE,
            AuditResultStatus.SUCCESS,
            id,
            updatedNode.getName(),
            new LinkedHashMap<>(successDetail),
            request,
            "/api/admin/orgs/" + id,
            "PUT",
            "修改部门：" + updatedNode.getName(),
            false
        );

        return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
    }

    @DeleteMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> deleteOrg(@PathVariable long id, HttpServletRequest request) {
        OrganizationNode existing = organizationRepository.findById(id).orElse(null);
        if (existing == null) {
            recordOrgActionV2(
                SecurityUtils.getCurrentAuditableLogin(),
                ButtonCodes.ORG_DELETE,
                AuditResultStatus.FAILED,
                id,
                null,
                Map.of("error", "NOT_FOUND"),
                request,
                "/api/admin/orgs/" + id,
                "DELETE",
                "删除部门失败：部门不存在",
                false
            );
            return ResponseEntity.status(404).body(ApiResponse.error("部门不存在"));
        }
        String actor = SecurityUtils.getCurrentAuditableLogin();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", existing.getId());
        detail.put("name", existing.getName());
        if (existing.getParent() != null) {
            detail.put("parentId", existing.getParent().getId());
        }
        try {
            orgService.delete(id);
            Map<String, Object> success = new LinkedHashMap<>(detail);
            success.put("status", "DELETED");
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_DELETE,
                AuditResultStatus.SUCCESS,
                id,
                existing.getName(),
                new LinkedHashMap<>(success),
                request,
                "/api/admin/orgs/" + id,
                "DELETE",
                "删除部门：" + existing.getName(),
                false
            );
            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(detail);
            failure.put("error", ex.getMessage());
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_DELETE,
                AuditResultStatus.FAILED,
                id,
                existing.getName(),
                failure,
                request,
                "/api/admin/orgs/" + id,
                "DELETE",
                "删除部门失败：" + ex.getMessage(),
                false
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (RuntimeException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(detail);
            failure.put("error", ex.getMessage());
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_DELETE,
                AuditResultStatus.FAILED,
                id,
                existing.getName(),
                failure,
                request,
                "/api/admin/orgs/" + id,
                "DELETE",
                "删除部门失败：" + ex.getMessage(),
                false
            );
            return ResponseEntity.internalServerError().body(ApiResponse.error("删除部门失败: " + ex.getMessage()));
        }
    }

    @PostMapping("/orgs/sync")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> syncOrganizations(HttpServletRequest request) {
        String actor = SecurityUtils.getCurrentAuditableLogin();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("sync", "keycloak");
        try {
            organizationSyncService.syncAll();
            Map<String, Object> success = new LinkedHashMap<>(auditDetail);
            success.put("status", "SUCCESS");
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_SYNC,
                AuditResultStatus.SUCCESS,
                null,
                null,
                success,
                request,
                "/api/admin/orgs/sync",
                "POST",
                "同步组织结构成功",
                true
            );
            return ResponseEntity.ok(ApiResponse.ok(buildOrgTree()));
        } catch (RuntimeException ex) {
            Map<String, Object> failure = new LinkedHashMap<>(auditDetail);
            failure.put("error", ex.getMessage());
            recordOrgActionV2(
                actor,
                ButtonCodes.ORG_SYNC,
                AuditResultStatus.FAILED,
                null,
                null,
                failure,
                request,
                "/api/admin/orgs/sync",
                "POST",
                "同步组织结构失败：" + ex.getMessage(),
                true
            );
            return ResponseEntity.status(500).body(ApiResponse.error("同步失败: " + ex.getMessage()));
        }
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> datasets(HttpServletRequest request) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AdminDataset d : datasetRepo.findAll()) out.add(toDatasetVM(d));
        String actor = SecurityUtils.getCurrentAuditableLogin();
        recordDatasetListV2(actor, out.size(), request);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/custom-roles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> customRoles(HttpServletRequest request) {
        var list = customRoleRepo.findAll().stream().map(this::toCustomRoleVM).toList();
        String actor = SecurityUtils.getCurrentAuditableLogin();
        recordCustomRoleListV2(actor, list.size(), request);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/custom-roles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCustomRole(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String rawName = Objects.toString(payload.get("name"), "").trim();
        if (rawName.isEmpty()) {
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                payload,
                Map.of("error", "角色 ID 不能为空"),
                AuditResultStatus.FAILED,
                "角色 ID 不能为空",
                request
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("角色 ID 不能为空"));
        }
        String normalizedName = stripRolePrefix(rawName);
        if (isReservedRealmRoleName(normalizedName)) {
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                payload,
                Map.of("error", "内置角色不可创建"),
                AuditResultStatus.FAILED,
                "内置角色不可创建",
                request
            );
            return ResponseEntity.status(409).body(ApiResponse.error("内置角色不可创建"));
        }
        if (locateCustomRole(normalizedName).isPresent()) {
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                payload,
                Map.of("error", "角色名称已存在"),
                AuditResultStatus.FAILED,
                "角色名称已存在",
                request
            );
            return ResponseEntity.status(409).body(ApiResponse.error("角色名称已存在"));
        }
        String titleCn = Objects.toString(payload.getOrDefault("titleCn", payload.get("nameZh")), "").trim();
        if (titleCn.isEmpty()) {
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                payload,
                Map.of("error", "角色名称不能为空"),
                AuditResultStatus.FAILED,
                "角色名称不能为空",
                request
            );
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
        try {
            ChangeRequest cr = changeRequestService.draft(
                "CUSTOM_ROLE",
                "CREATE",
                normalizedName,
                after,
                null,
                Objects.toString(payload.get("reason"), null)
            );
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("changeRequestId", cr.getId());
            detail.put("name", normalizedName);
            attachChangeRequestMetadata(detail, cr);
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                cr,
                after,
                detail,
                AuditResultStatus.SUCCESS,
                null,
                request
            );
            return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
        } catch (IllegalStateException ex) {
            recordCustomRoleCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                after,
                Map.of("error", ex.getMessage()),
                AuditResultStatus.FAILED,
                ex.getMessage(),
                request
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/role-assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roleAssignments(HttpServletRequest request) {
        Map<String, String> roleLabels = buildRoleDisplayNameMap();
        var list = roleAssignRepo.findAll().stream().map(a -> toRoleAssignmentVM(a, roleLabels)).toList();
        String actor = SecurityUtils.getCurrentAuditableLogin();
        if (!isAuditSuppressed(request)) {
            recordRoleAssignmentListV2(actor, list.size(), request);
        }
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/role-assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoleAssignment(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String role = Objects.toString(payload.get("role"), "").trim();
        String username = Objects.toString(payload.get("username"), "").trim();
        String displayName = Objects.toString(payload.get("displayName"), "").trim();
        String userSecurityLevel = Objects.toString(payload.get("userSecurityLevel"), "").trim();
        Long scopeOrgId = payload.get("scopeOrgId") == null ? null : Long.valueOf(payload.get("scopeOrgId").toString());
        List<String> ops = readStringList(payload.get("operations"));
        List<Long> datasetIds = readLongList(payload.get("datasetIds"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("role", role);
        after.put("username", username);
        after.put("displayName", displayName);
        after.put("userSecurityLevel", userSecurityLevel);
        after.put("scopeOrgId", scopeOrgId);
        after.put("datasetIds", datasetIds);
        after.put("operations", new LinkedHashSet<>(ops));
        String error = validateAssignment(role, username, displayName, userSecurityLevel, scopeOrgId, ops, datasetIds);
        if (error != null) {
            recordRoleAssignmentCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                after,
                Map.of("error", error),
                AuditResultStatus.FAILED,
                error,
                request
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(error));
        }
        try {
            ChangeRequest cr = changeRequestService.draft(
                "ROLE_ASSIGNMENT",
                "CREATE",
                null,
                after,
                null,
                Objects.toString(payload.get("reason"), null)
            );
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("changeRequestId", cr.getId());
            detail.put("role", role);
            attachChangeRequestMetadata(detail, cr);
            recordRoleAssignmentCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                cr,
                after,
                detail,
                AuditResultStatus.SUCCESS,
                null,
                request
            );
            return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
        } catch (IllegalStateException ex) {
            recordRoleAssignmentCreateV2(
                SecurityUtils.getCurrentAuditableLogin(),
                null,
                after,
                Map.of("error", ex.getMessage()),
                AuditResultStatus.FAILED,
                ex.getMessage(),
                request
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/change-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> changeRequests(
        @RequestParam(required = false) String status,
        @RequestParam(required = false, name = "type") String resourceType,
        HttpServletRequest request
    ) {
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
        String actor = SecurityUtils.getCurrentAuditableLogin();
        recordChangeRequestListV2(actor, responseList.size(), request, false);
        return ResponseEntity.ok(ApiResponse.ok(responseList));
    }

    @GetMapping("/change-requests/mine")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myChangeRequests(HttpServletRequest request) {
        String me = SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
        LinkedHashMap<Long, Map<String, Object>> viewById = new LinkedHashMap<>();
        crRepo
            .findByRequestedBy(me)
            .forEach(cr -> viewById.put(cr.getId(), toChangeVM(cr)));
        augmentChangeRequestViewsFromApprovalsForActor(viewById, me);
        List<Map<String, Object>> list = new ArrayList<>(viewById.values());
        list.sort((a, b) -> compareByRequestedAtDesc(a, b));
        String actor = SecurityUtils.getCurrentAuditableLogin();
        recordChangeRequestListV2(actor, list.size(), request, true);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/change-requests/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeRequestDetail(@PathVariable long id, HttpServletRequest request) {
        ChangeRequest cr = crRepo.findById(id).orElse(null);
        if (cr == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("变更不存在"));
        }
        Map<String, Object> vm = toChangeVM(cr);
        LinkedHashMap<Long, Map<String, Object>> viewById = new LinkedHashMap<>();
        viewById.put(cr.getId(), vm);
        augmentChangeRequestViewsFromApprovals(viewById);
        Map<String, Object> enriched = viewById.get(cr.getId());
        if (!isAuditSuppressed(request)) {
            String actor = SecurityUtils.getCurrentAuditableLogin();
            Map<String, Object> auditDetail = buildChangeAuditDetail(cr);
            if (enriched != null && !enriched.isEmpty()) {
                auditDetail.put("viewSource", "detail");
            }
            recordChangeRequestViewV2(actor, cr, auditDetail, request);
        }
        return ResponseEntity.ok(ApiResponse.ok(enriched != null ? enriched : vm));
    }

    /**
     * 维护：清理所有“提交申请(变更请求)”与“审批请求”的历史数据。
     * 仅限系统管理员或授权管理员调用。
     */
    @PostMapping("/maintenance/purge-requests")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_AUTH_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purgeRequests(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String actor = com.yuzhi.dts.admin.security.SecurityUtils.getCurrentUserLogin().orElse("sysadmin");
        long approvals = approvalRepo.count();
        long changes = crRepo.count();

        // 先清理审批请求（级联删除其 items），再清理变更请求
        approvalRepo.deleteAllInBatch();
        crRepo.deleteAllInBatch();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedApprovals", approvals);
        result.put("deletedChangeRequests", changes);
        recordChangeRequestPurgeV2(actor, approvals, changes, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/change-requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChangeRequest(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String resourceType = Objects.toString(payload.get("resourceType"), "UNKNOWN");
        String action = Objects.toString(payload.get("action"), "UNKNOWN");
        String resourceId = Objects.toString(payload.get("resourceId"), null);
        String payloadJson = Objects.toString(payload.get("payloadJson"), null);
        try {
            payloadJson = changeRequestService.ensureNoDuplicate(resourceType, action, resourceId, payloadJson);
        } catch (IllegalStateException ex) {
            Map<String, Object> errorDetail = new LinkedHashMap<>();
            errorDetail.put("resourceType", resourceType);
            errorDetail.put("action", action);
            if (resourceId != null) {
                errorDetail.put("resourceId", resourceId);
            }
            errorDetail.put("error", ex.getMessage());
            recordChangeRequestDraftFailure(SecurityUtils.getCurrentAuditableLogin(), errorDetail, request);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType(resourceType);
        cr.setResourceId(resourceId);
        cr.setAction(action);
        cr.setPayloadJson(payloadJson);
        cr.setDiffJson(Objects.toString(payload.get("diffJson"), null));
        cr.setStatus("DRAFT");
        cr.setRequestedBy(SecurityUtils.getCurrentUserLogin().orElse("sysadmin"));
        cr.setRequestedAt(Instant.now());
        cr.setCategory(Objects.toString(payload.get("category"), "GENERAL"));
        cr.setLastError(null);
        crRepo.save(cr);
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("action", "CREATE");
        attachChangeRequestMetadata(auditDetail, cr);
        recordChangeRequestDraftV2(SecurityUtils.getCurrentAuditableLogin(), cr, request, auditDetail);
        return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
    }

    @PostMapping("/change-requests/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitChangeRequest(@PathVariable String id, HttpServletRequest request) {
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
                case "PORTAL_MENU" -> "MENU_MANAGEMENT";
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
        ChangeContext submitContext = computeChangeContext(cr);
        Map<String, Object> submitDetail = buildChangeAuditDetail(cr);
        submitDetail.put("action", "SUBMIT");
        populateDetailWithContext(submitDetail, submitContext);
        String requesterOperation = buildRequesterOperationName(cr, submitContext);
        if (StringUtils.hasText(requesterOperation)) {
            submitDetail.put("operationName", requesterOperation);
            submitDetail.put("summary", requesterOperation);
        }
        recordChangeRequestSubmitV2(
            SecurityUtils.getCurrentAuditableLogin(),
            cr,
            new LinkedHashMap<>(submitDetail),
            submitContext,
            resolveClientIp(request),
            request != null ? request.getHeader("User-Agent") : null,
            request != null ? request.getRequestURI() : "/api/admin/change-requests/" + id + "/submit"
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveChangeRequest(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        String actor = SecurityUtils.getCurrentUserLogin().orElse("authadmin");
        cr.setStatus("APPROVED");
        cr.setDecidedBy(actor);
        cr.setDecidedAt(Instant.now());
        cr.setReason(body != null ? Objects.toString(body.get("reason"), null) : null);
        cr.setLastError(null);

        boolean applied = Boolean.TRUE.equals(
            changeApplyTx.execute(status -> {
                try {
                    applyChangeRequest(cr, actor);
                } catch (Exception ex) {
                    log.error("Error applying change request {}: {}", crid, ex.getMessage(), ex);
                    cr.setStatus("FAILED");
                    cr.setLastError(resolveRootCauseMessage(ex));
                }
                boolean success = !"FAILED".equalsIgnoreCase(cr.getStatus());
                if (!success) {
                    status.setRollbackOnly();
                }
                return success;
            })
        );

        if (!applied && !StringUtils.hasText(cr.getLastError())) {
            cr.setLastError("审批执行失败，请稍后重试");
        }

        AuditStage requesterStage = resolveStageForChangeOutcome(cr, applied);
        ChangeContext changeContext = computeChangeContext(cr);
        // V2 审计已覆盖执行阶段
        crRepo.save(cr);
        Map<String, Object> approverDetail = buildChangeAuditDetail(cr);
        approverDetail.put("action", "APPROVE");
        if (!applied && StringUtils.hasText(cr.getLastError())) {
            approverDetail.put("error", cr.getLastError());
        }
        applyApproverContext(approverDetail, cr, changeContext);
        recordChangeRequestApproveV2(
            actor,
            cr,
            new LinkedHashMap<>(approverDetail),
            changeContext,
            applied,
            resolveClientIp(request),
            request != null ? request.getHeader("User-Agent") : null,
            request != null ? request.getRequestURI() : "/api/admin/change-requests/" + id + "/approve"
        );
        if (applied) {
            try {
                notifyClient.trySend("approval_approved", Map.of("id", id, "type", cr.getResourceType(), "status", cr.getStatus()));
            } catch (Exception ignored) {}
            return ResponseEntity.ok(ApiResponse.ok(toChangeVM(cr)));
        }
        try {
            notifyClient.trySend(
                "approval_failed",
                Map.of("id", id, "type", cr.getResourceType(), "status", cr.getStatus(), "error", Objects.toString(cr.getLastError(), ""))
            );
        } catch (Exception ignored) {}
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(StringUtils.hasText(cr.getLastError()) ? cr.getLastError() : "审批执行失败"));
    }

    @PostMapping("/change-requests/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectChangeRequest(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        long crid = Long.parseLong(id);
        ChangeRequest cr = crRepo.findById(crid).orElse(null);
        if (cr == null) return ResponseEntity.status(404).body(ApiResponse.error("变更不存在"));
        cr.setStatus("REJECTED");
        cr.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse("authadmin"));
        cr.setDecidedAt(Instant.now());
        cr.setReason(body != null ? Objects.toString(body.get("reason"), null) : null);
        cr.setLastError(null);
        ChangeContext changeContext = computeChangeContext(cr);
        // V2 审计已覆盖失败阶段
        crRepo.save(cr);
        Map<String, Object> approverDetail = buildChangeAuditDetail(cr);
        approverDetail.put("action", "REJECT");
        applyApproverContext(approverDetail, cr, changeContext);
        recordChangeRequestRejectV2(
            SecurityUtils.getCurrentAuditableLogin(),
            cr,
            new LinkedHashMap<>(approverDetail),
            changeContext,
            resolveClientIp(request),
            request != null ? request.getHeader("User-Agent") : null,
            request != null ? request.getRequestURI() : "/api/admin/change-requests/" + id + "/reject"
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

        // Include custom roles that may exist only in admin DB even if Keycloak sync failed
        try {
            for (AdminCustomRole cr : customRoleRepo.findAll()) {
                String name = Objects.toString(cr.getName(), "").trim();
                if (name.isEmpty()) continue;
                String canonical = stripRolePrefix(name);
                if (emitted.contains(canonical)) continue;
                Map<String, Object> summary = toRoleSummary(null, canonical, null, now);
                summary.put("customRole", true);
                summary.put("customRoleId", cr.getId());
                summary.put("source", "custom");
                // Derive scope/ops from DB record
                if (StringUtils.hasText(cr.getScope())) summary.put("scope", cr.getScope());
                if (StringUtils.hasText(cr.getOperationsCsv())) {
                    summary.put("operations", Arrays.stream(cr.getOperationsCsv().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
                }
                list.add(summary);
                emitted.add(canonical);
            }
        } catch (Exception ignored) {}

        list.sort(Comparator.comparing(o -> Objects.toString(o.get("name"), "")));

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
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/permissions/catalog")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> permissionCatalog(HttpServletRequest request) {
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
        recordPermissionCatalogV2(SecurityUtils.getCurrentAuditableLogin(), sections.size(), request);
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
        m.put("parentId", e.getParent() != null ? e.getParent().getId() : null);
        m.put("contact", e.getContact());
        m.put("phone", e.getPhone());
        m.put("description", e.getDescription());
        m.put("isRoot", e.isRoot());
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
        m.put("parentId", node.getParent() != null ? node.getParent().getId() : null);
        m.put("contact", node.getContact());
        m.put("phone", node.getPhone());
        m.put("description", node.getDescription());
        m.put("keycloakGroupId", node.getKeycloakGroupId());
        m.put("isRoot", node.isRoot());
        return m;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
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
        m.put("deleted", menu.isDeleted());
        return m;
    }

    private Map<String, Object> buildPortalMenuCollection() {
        return buildPortalMenuCollection(true);
    }

    private Map<String, Object> buildPortalMenuCollection(boolean allowReseed) {
        List<PortalMenu> allMenus = portalMenuService.findAllMenusOrdered();
        if ((allMenus == null || allMenus.isEmpty()) && allowReseed) {
            try {
                portalMenuService.resetMenusToSeed();
                allMenus = portalMenuService.findAllMenusOrdered();
            } catch (Exception ex) {
                log.warn("Failed to reseed portal menus: {}", ex.getMessage());
            }
        }
        if (allMenus == null) {
            allMenus = java.util.Collections.emptyList();
        }
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

        if (allowReseed && fullTree.isEmpty() && !allMenus.isEmpty()) {
            // all menus might have been filtered out (e.g., deleted-only tree). Attempt a soft reset once.
            portalMenuService.resetMenusToSeed();
            return buildPortalMenuCollection(false);
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

    private final class MenuVisibilityBatch {

        private final PortalMenu menu;
        private final LinkedHashMap<String, Map<String, Object>> explicitRuleMap = new LinkedHashMap<>();
        private final LinkedHashSet<String> allowedRoles = new LinkedHashSet<>();
        private final LinkedHashSet<String> allowedPermissions = new LinkedHashSet<>();
        private String maxDataLevel;
        private boolean hasExplicitRules = false;

        MenuVisibilityBatch(PortalMenu menu) {
            this.menu = menu;
        }

        void merge(Map<?, ?> raw) {
            if (raw == null) {
                return;
            }
            List<Map<String, Object>> rules = normalizeVisibilityRules(raw.get("visibilityRules"));
            if (!rules.isEmpty()) {
                hasExplicitRules = true;
                for (Map<String, Object> rule : rules) {
                    String role = normalizeRoleCode(rule.get("role"));
                    if (!StringUtils.hasText(role)) {
                        continue;
                    }
                    String permission = rule.get("permission") == null ? null : rule.get("permission").toString().trim();
                    String level = normalizeDataLevelForVisibility(rule.get("dataLevel"));
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("role", role);
                    if (StringUtils.hasText(permission)) {
                        normalized.put("permission", permission);
                    }
                    normalized.put("dataLevel", level);
                    String key = role + "|" + (permission == null ? "" : permission) + "|" + level;
                    explicitRuleMap.put(key, normalized);
                }
            }
            mergeRoles(raw.get("allowedRoles"));
            mergePermissions(raw.get("allowedPermissions"));
            if (raw.containsKey("maxDataLevel")) {
                String level = normalizeDataLevelForVisibility(raw.get("maxDataLevel"));
                if (maxDataLevel == null || dataLevelPriority(level) > dataLevelPriority(maxDataLevel)) {
                    maxDataLevel = level;
                }
            }
        }

        private void mergeRoles(Object source) {
            if (source == null) {
                return;
            }
            if (source instanceof Collection<?> collection) {
                for (Object item : collection) {
                    addRole(item);
                }
            } else {
                addRole(source);
            }
        }

        private void addRole(Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Map<?, ?> map) {
                addRole(map.get("role"));
                return;
            }
            String normalized = normalizeRoleCode(value);
            if (StringUtils.hasText(normalized)) {
                allowedRoles.add(normalized);
            }
        }

        private void mergePermissions(Object source) {
            if (source == null) {
                return;
            }
            if (source instanceof Collection<?> collection) {
                for (Object item : collection) {
                    addPermission(item);
                }
            } else {
                addPermission(source);
            }
        }

        private void addPermission(Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Map<?, ?> map) {
                addPermission(map.get("permission"));
                return;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                allowedPermissions.add(text);
            }
        }

        Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (hasExplicitRules && !explicitRuleMap.isEmpty()) {
                payload.put("visibilityRules", new ArrayList<>(explicitRuleMap.values()));
                return payload;
            }
            if (!allowedRoles.isEmpty()) {
                payload.put("allowedRoles", new ArrayList<>(allowedRoles));
            }
            if (!allowedPermissions.isEmpty()) {
                payload.put("allowedPermissions", new ArrayList<>(allowedPermissions));
            }
            if (maxDataLevel != null) {
                payload.put("maxDataLevel", maxDataLevel);
            }
            return payload;
        }

        PortalMenu menu() {
            return menu;
        }
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
        if (menu == null) return false;
        try {
            // Prefer metadata sectionKey
            String meta = menu.getMetadata();
            if (meta != null) {
                String lc = meta.toLowerCase(java.util.Locale.ROOT);
                if (lc.contains("\"sectionkey\":\"foundation\"")) return true;
            }
        } catch (Exception ignored) {}
        try {
            // Fallback by path heuristic
            String p = menu.getPath();
            if (p != null) {
                String lp = p.trim().toLowerCase(java.util.Locale.ROOT);
                if ("/foundation".equals(lp) || lp.startsWith("/foundation/")) return true;
            }
        } catch (Exception ignored) {}
        try {
            // Specific deployment might use fixed id 2670 for foundation
            if (menu.getId() != null && menu.getId() == 2670L) return true;
        } catch (Exception ignored) {}
        return false;
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
            case "CONFIDENTIAL", "CORE" -> 4;
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
            cr.setLastError(resolveRootCauseMessage(e));
        }
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (!StringUtils.hasText(message)) {
            message = root.getClass().getSimpleName();
        }
        return message;
    }

    private ChangeContext computeChangeContext(ChangeRequest cr) {
        if (cr == null) {
            return null;
        }
        String resourceType = normalizeActionToken(cr.getResourceType());
        String action = normalizeActionToken(cr.getAction());
        Map<String, Object> payload = safeChangePayload(cr);
        return switch (resourceType) {
            case "ROLE" -> buildRoleLikeContext("ROLE", cr, action, payload);
            case "CUSTOM_ROLE" -> buildRoleLikeContext("CUSTOM_ROLE", cr, action, payload);
            case "ROLE_ASSIGNMENT" -> buildRoleAssignmentContext(cr, action, payload);
            case "PORTAL_MENU" -> buildPortalMenuContext(cr, action, payload);
            case "USER" -> buildUserContext(cr, action, payload);
            default -> null;
        };
    }

    private Map<String, Object> safeChangePayload(ChangeRequest cr) {
        if (cr == null || !StringUtils.hasText(cr.getPayloadJson())) {
            return Map.of();
        }
        try {
            return fromJson(cr.getPayloadJson());
        } catch (Exception ex) {
            log.debug("Failed to parse change payload for CR {}: {}", cr.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private ChangeContext buildRoleLikeContext(String resourceType, ChangeRequest cr, String action, Map<String, Object> payload) {
        String canonical = stripRolePrefix(stringValue(payload != null ? payload.get("name") : null));
        if (!StringUtils.hasText(canonical)) {
            canonical = stripRolePrefix(stringValue(cr.getResourceId()));
        }
        String label = firstNonBlank(
            stringValue(payload != null ? payload.get("titleCn") : null),
            stringValue(payload != null ? payload.get("nameZh") : null),
            stringValue(payload != null ? payload.get("displayName") : null),
            stringValue(payload != null ? payload.get("title") : null),
            canonical
        );
        Map<String, Object> extras = new LinkedHashMap<>();
        if (StringUtils.hasText(canonical)) {
            extras.put("roleName", canonical);
        }
        String primaryId = resolveRolePrimaryId(canonical, cr);
        if (!StringUtils.hasText(label)) {
            label = firstNonBlank(stringValue(cr.getResourceId()), canonical, primaryId);
        }
        if (payload != null) {
            Object operations = payload.get("operations");
            if (operations != null) {
                extras.put("roleOperations", operations);
            }
            String scope = stringValue(payload.get("scope"));
            if (scope != null) {
                extras.put("roleScope", scope);
            }
            String description = stringValue(payload.get("description"));
            if (description != null) {
                extras.put("roleDescription", description);
            }
            Object maxRows = payload.get("maxRows");
            if (maxRows != null) {
                extras.put("maxRows", maxRows);
            }
            Object allowDesensitize = payload.get("allowDesensitizeJson");
            if (allowDesensitize != null) {
                extras.put("allowDesensitize", allowDesensitize);
            }
        }
        if (StringUtils.hasText(label)) {
            extras.put("roleLabel", label);
        }
        return new ChangeContext(resourceType, action, "admin_custom_role", primaryId, label, canonical, extras);
    }

    private String resolveRolePrimaryId(String canonical, ChangeRequest cr) {
        if (StringUtils.hasText(canonical)) {
            AdminCustomRole role = locateCustomRole(canonical).orElse(null);
            if (role != null) {
                return String.valueOf(role.getId());
            }
        }
        String resourceId = stringValue(cr != null ? cr.getResourceId() : null);
        if (StringUtils.hasText(resourceId)) {
            return resourceId;
        }
        return canonical;
    }

    private ChangeContext buildRoleAssignmentContext(ChangeRequest cr, String action, Map<String, Object> payload) {
        String assignmentId = stringValue(cr != null ? cr.getResourceId() : null);
        if (!StringUtils.hasText(assignmentId) && payload != null) {
            assignmentId = stringValue(payload.get("assignmentId"));
        }
        String username = payload != null ? stringValue(payload.get("username")) : null;
        String displayName = payload != null ? stringValue(payload.get("displayName")) : null;
        String userLabel = firstNonBlank(displayName, username);
        String rawRole = payload != null ? stringValue(payload.get("role")) : null;
        String canonicalRole = stripRolePrefix(rawRole);
        String roleLabel = firstNonBlank(
            stringValue(payload != null ? payload.get("roleLabel") : null),
            stringValue(payload != null ? payload.get("roleDisplayName") : null),
            canonicalRole
        );
        String combinedLabel;
        if (StringUtils.hasText(userLabel) && StringUtils.hasText(roleLabel)) {
            combinedLabel = userLabel + " -> " + roleLabel;
        } else {
            combinedLabel = firstNonBlank(userLabel, roleLabel, assignmentId);
        }
        Map<String, Object> extras = new LinkedHashMap<>();
        if (StringUtils.hasText(username)) {
            extras.put("username", username);
        }
        if (StringUtils.hasText(displayName)) {
            extras.put("displayName", displayName);
        }
        if (StringUtils.hasText(canonicalRole)) {
            extras.put("roleName", canonicalRole);
        }
        if (StringUtils.hasText(roleLabel)) {
            extras.put("roleLabel", roleLabel);
        }
        Object datasetIds = payload != null ? payload.get("datasetIds") : null;
        if (datasetIds != null) {
            extras.put("datasetIds", datasetIds);
        }
        Object operations = payload != null ? payload.get("operations") : null;
        if (operations != null) {
            extras.put("operations", operations);
        }
        return new ChangeContext("ROLE_ASSIGNMENT", action, "admin_role_assignment", assignmentId, combinedLabel, username, extras);
    }

    private ChangeContext buildPortalMenuContext(ChangeRequest cr, String action, Map<String, Object> payload) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        Map<String, Object> extras = new LinkedHashMap<>();
        if ("BATCH_UPDATE".equals(action) || "BULK_UPDATE".equals(action)) {
            return buildPortalMenuBatchContext(cr, action, source, extras);
        }
        String resourceId = stringValue(cr != null ? cr.getResourceId() : null);
        Long menuId = parseLongSafe(resourceId);
        if (menuId == null) {
            menuId = parseLongSafe(source.get("id"));
        }
        String name = stringValue(source.get("name"));
        String path = stringValue(source.get("path"));
        String label = extractMenuLabelFromMetadata(source.get("metadata"));
        if (label == null) {
            label = firstNonBlank(
                stringValue(source.get("displayName")),
                stringValue(source.get("title")),
                stringValue(source.get("titleCn")),
                stringValue(source.get("nameZh"))
            );
        }
        PortalMenu existing = null;
        if (menuId != null) {
            existing = portalMenuRepo.findById(menuId).orElse(null);
        }
        if (existing == null && StringUtils.hasText(name)) {
            existing = portalMenuRepo.findFirstByNameIgnoreCase(name.trim()).orElse(null);
        }
        if (existing == null && StringUtils.hasText(path)) {
            existing = portalMenuRepo.findFirstByPath(path.trim()).orElse(null);
        }
        if (existing == null && StringUtils.hasText(resourceId)) {
            String trimmedId = resourceId.trim();
            existing = portalMenuRepo.findFirstByNameIgnoreCase(trimmedId).orElse(null);
            if (existing == null) {
                existing = portalMenuRepo.findFirstByPath(trimmedId).orElse(null);
            }
        }
        if (existing != null) {
            if (!StringUtils.hasText(label)) {
                label = resolveMenuDisplayName(existing);
            } else if (label.equals(existing.getName())) {
                String resolved = resolveMenuDisplayName(existing);
                if (StringUtils.hasText(resolved)) {
                    label = resolved;
                }
            }
            if (!StringUtils.hasText(name)) {
                name = existing.getName();
            }
            if (!StringUtils.hasText(path)) {
                path = existing.getPath();
            }
            extras.putIfAbsent("menuId", existing.getId());
        }
        if (!StringUtils.hasText(label)) {
            label = firstNonBlank(name, path, resourceId);
        }
        if (StringUtils.hasText(name)) {
            extras.put("menuName", name);
        }
        if (StringUtils.hasText(path)) {
            extras.put("menuPath", path);
        }
        if (source.containsKey("parentId")) {
            extras.put("parentId", source.get("parentId"));
        }
        if (source.containsKey("securityLevel")) {
            extras.put("securityLevel", source.get("securityLevel"));
        }
        if (source.containsKey("visibilityRules")) {
            extras.put("visibilityRules", source.get("visibilityRules"));
        }
        if (StringUtils.hasText(label)) {
            extras.put("menuLabel", label);
        }
        String canonical = StringUtils.hasText(name) ? name : path;
        if (!StringUtils.hasText(canonical)) {
            canonical = resourceId;
        }
        String targetId = resourceId;
        if (!StringUtils.hasText(targetId)) {
            if (menuId != null) {
                targetId = String.valueOf(menuId);
            } else if (StringUtils.hasText(path)) {
                targetId = path;
            } else if (StringUtils.hasText(name)) {
                targetId = name;
            } else if (cr != null && cr.getId() != null) {
                targetId = "CR-" + cr.getId();
            }
        }
        return new ChangeContext("PORTAL_MENU", action, "portal_menu", trimToNull(targetId), label, canonical, extras);
    }

    private ChangeContext buildUserContext(ChangeRequest cr, String action, Map<String, Object> payload) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        String username = stringValue(source.get("username"));
        if (!StringUtils.hasText(username)) {
            username = stringValue(cr != null ? cr.getResourceId() : null);
        }
        String displayName = firstNonBlank(
            stringValue(source.get("fullName")),
            stringValue(source.get("displayName")),
            username
        );
        Map<String, Object> extras = new LinkedHashMap<>();
        if (StringUtils.hasText(displayName)) {
            extras.put("fullName", displayName);
        }
        if (StringUtils.hasText(username)) {
            extras.put("username", username);
        }
        String email = stringValue(source.get("email"));
        if (StringUtils.hasText(email)) {
            extras.put("email", email);
        }
        String phone = stringValue(source.get("phone"));
        if (StringUtils.hasText(phone)) {
            extras.put("phone", phone);
        }
        Object enabled = source.get("enabled");
        if (enabled != null) {
            extras.put("enabled", enabled);
        }
        Object personSecurityLevel = source.get("personSecurityLevel");
        if (personSecurityLevel != null) {
            extras.put("personSecurityLevel", personSecurityLevel);
        }
        return new ChangeContext("USER", action, "admin_user", trimToNull(username), displayName, username, extras);
    }

    private ChangeContext buildPortalMenuBatchContext(
        ChangeRequest cr,
        String action,
        Map<String, Object> payload,
        Map<String, Object> extras
    ) {
        Object updatesObj = payload.get("updates");
        List<Map<String, Object>> updates = new ArrayList<>();
        if (updatesObj instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    updates.add(toStringKeyMap(map));
                }
            }
        }
        LinkedHashSet<String> targetIds = new LinkedHashSet<>();
        LinkedHashMap<String, String> labelMap = new LinkedHashMap<>();
        for (Map<String, Object> update : updates) {
            Long menuId = parseLongSafe(update.get("id"));
            String label = extractMenuLabelFromMetadata(update.get("metadata"));
            if (!StringUtils.hasText(label)) {
                label =
                    firstNonBlank(
                        stringValue(update.get("displayName")),
                        stringValue(update.get("title")),
                        stringValue(update.get("titleCn")),
                        stringValue(update.get("name"))
                    );
            }
            if (menuId != null) {
                PortalMenu menu = portalMenuRepo.findById(menuId).orElse(null);
                if (menu != null) {
                    if (!StringUtils.hasText(label)) {
                        label = resolveMenuDisplayName(menu);
                    }
                    targetIds.add(String.valueOf(menu.getId()));
                    labelMap.put(String.valueOf(menu.getId()), StringUtils.hasText(label) ? label : menu.getName());
                    continue;
                }
                targetIds.add(String.valueOf(menuId));
            }
            if (StringUtils.hasText(label)) {
                String key = StringUtils.hasText(menuId != null ? String.valueOf(menuId) : null) ? String.valueOf(menuId) : label;
                labelMap.put(key, label);
                targetIds.add(key);
            }
        }
        extras.put("batchSize", updates.size());
        if (!targetIds.isEmpty()) {
            extras.put("targetIds", List.copyOf(targetIds));
            extras.put("menuIds", List.copyOf(targetIds));
        }
        if (!labelMap.isEmpty()) {
            extras.put("targetLabelMap", Map.copyOf(labelMap));
            extras.put("menuLabels", List.copyOf(labelMap.values()));
        }
        String targetId = targetIds.isEmpty()
            ? stringValue(cr != null ? cr.getResourceId() : null)
            : targetIds.iterator().next();
        if (!StringUtils.hasText(targetId) && cr != null && cr.getId() != null) {
            targetId = "CR-" + cr.getId();
        }
        String targetLabel;
        if (labelMap.isEmpty()) {
            targetLabel = "批量菜单";
        } else {
            List<String> previews = new ArrayList<>(labelMap.values());
            int previewSize = Math.min(3, previews.size());
            targetLabel = previews.subList(0, previewSize).stream().collect(Collectors.joining("、"));
            if (previews.size() > previewSize) {
                targetLabel = targetLabel + " 等" + previews.size() + "项";
            }
        }
        return new ChangeContext("PORTAL_MENU", action, "portal_menu", trimToNull(targetId), targetLabel, null, extras);
    }

    private Long parseLongSafe(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return Long.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((k, v) -> {
            if (k != null) {
                result.put(String.valueOf(k), v);
            }
        });
        return result;
    }

    private String extractMenuLabelFromMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        Map<String, Object> meta = null;
        if (metadata instanceof Map<?, ?> map) {
            meta = toStringKeyMap(map);
        } else if (metadata instanceof String s && !s.isBlank()) {
            try {
                meta = fromJson(s);
            } catch (Exception ignored) {
                meta = null;
            }
        }
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        return firstNonBlank(
            stringValue(meta.get("title")),
            stringValue(meta.get("label")),
            stringValue(meta.get("titleCn")),
            stringValue(meta.get("nameZh")),
            stringValue(meta.get("displayName"))
        );
    }

    private void populateDetailWithContext(Map<String, Object> detail, ChangeContext context) {
        if (detail == null || context == null) {
            return;
        }
        boolean targetIdsSet = false;
        Map<String, Object> extras = context.extras();
        if (extras != null && !extras.isEmpty()) {
            Object extraIds = extras.get("targetIds");
            if (extraIds instanceof Collection<?> collection) {
                List<String> ids = new ArrayList<>();
                for (Object item : collection) {
                    String id = stringValue(item);
                    if (StringUtils.hasText(id)) {
                        String trimmed = id.trim();
                        if (!ids.contains(trimmed)) {
                            ids.add(trimmed);
                        }
                    }
                }
                if (!ids.isEmpty()) {
                    detail.put("targetIds", ids);
                    targetIdsSet = true;
                }
            }
            Object labelMapObj = extras.get("targetLabelMap");
            if (labelMapObj instanceof Map<?, ?> map) {
                Map<String, String> labelMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = stringValue(entry.getKey());
                    String value = stringValue(entry.getValue());
                    if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                        labelMap.put(key.trim(), value.trim());
                    }
                }
                if (!labelMap.isEmpty()) {
                    detail.put("targetLabels", labelMap);
                    if (!detail.containsKey("targetLabel")) {
                        detail.put("targetLabel", labelMap.values().iterator().next());
                    }
                }
            }
        }
        if (!targetIdsSet && StringUtils.hasText(context.targetId())) {
            detail.put("targetIds", List.of(context.targetId()));
        }
        if (!detail.containsKey("targetLabel") && StringUtils.hasText(context.targetLabel())) {
            detail.put("targetLabel", context.targetLabel());
        }
        if (!detail.containsKey("targetLabels") && StringUtils.hasText(context.targetLabel()) && StringUtils.hasText(context.targetId())) {
            detail.put("targetLabels", Map.of(context.targetId(), context.targetLabel()));
        }
        if (extras != null && !extras.isEmpty()) {
            extras.forEach((k, v) -> {
                if ("targetIds".equals(k) || "targetLabelMap".equals(k)) {
                    return;
                }
                detail.putIfAbsent(k, v);
            });
        }
        if (StringUtils.hasText(context.canonical())) {
            detail.putIfAbsent("canonicalTarget", context.canonical());
        }
        if (StringUtils.hasText(context.targetTable())) {
            detail.put("targetTable", context.targetTable());
        }
    }

    private String buildRequesterOperationName(ChangeRequest cr, ChangeContext context) {
        if (context == null) {
            return null;
        }
        String resourceLabel = resolveResourceLabel(context);
        String targetLabel = resolveTargetLabel(context);
        if (targetLabel == null) {
            return null;
        }
        String action = context.action();
        String verb = switch (action) {
            case "CREATE" -> "新增";
            case "DELETE" -> "删除";
            case "UPDATE" -> "修改";
            case "BATCH_UPDATE" -> "批量更新";
            case "ENABLE" -> "启用";
            case "DISABLE" -> "禁用";
            case "BATCH_ENABLE" -> "批量启用";
            case "BATCH_DISABLE" -> "批量禁用";
            case "ASSIGN" -> "授权";
            default -> "处理";
        };
        return verb + resourceLabel + "：" + targetLabel;
    }

    private String buildApprovalOperationName(ChangeRequest cr, ChangeContext context, boolean approved) {
        String base = buildRequesterOperationName(cr, context);
        String verb = approved ? "批准" : "拒绝";
        if (StringUtils.hasText(base)) {
            return verb + base;
        }
        String resourceLabel = resolveResourceLabel(context);
        String targetLabel = resolveTargetLabel(context);
        if (StringUtils.hasText(targetLabel)) {
            return verb + resourceLabel + "：" + targetLabel;
        }
        if (StringUtils.hasText(resourceLabel) && !"变更".equals(resourceLabel)) {
            return verb + resourceLabel + "申请";
        }
        return verb + "变更申请";
    }


    private AuditOperationType determineRequesterOperationType(ChangeContext context) {
        if (context == null) {
            return AuditOperationType.UNKNOWN;
        }
        return mapActionToOperationType(normalizeActionToken(context.action()));
    }

    private AuditOperationType resolveApproverOperationType(ChangeRequest cr, ChangeContext context) {
        AuditOperationType type = determineRequesterOperationType(context);
        if (type != null && type != AuditOperationType.UNKNOWN) {
            return type;
        }
        String action = normalizeActionToken(cr != null ? cr.getAction() : null);
        return mapActionToOperationType(action);
    }

    private AuditOperationType mapActionToOperationType(String action) {
        if (!StringUtils.hasText(action)) {
            return AuditOperationType.UNKNOWN;
        }
        return switch (action) {
            case "CREATE" -> AuditOperationType.CREATE;
            case "DELETE" -> AuditOperationType.DELETE;
            case "UPDATE" -> AuditOperationType.UPDATE;
            case "BATCH_UPDATE", "BULK_UPDATE" -> AuditOperationType.UPDATE;
            case "ASSIGN", "GRANT_ROLE" -> AuditOperationType.GRANT;
            case "REVOKE_ROLE" -> AuditOperationType.REVOKE;
            case "ENABLE" -> AuditOperationType.ENABLE;
            case "DISABLE" -> AuditOperationType.DISABLE;
            case "SET_PERSON_LEVEL" -> AuditOperationType.UPDATE;
            case "RESET_PASSWORD" -> AuditOperationType.UPDATE;
            default -> AuditOperationType.UNKNOWN;
        };
    }

    private String resolveChangeViewSummary(ChangeRequest cr, ChangeContext context, Map<String, Object> detail) {
        String label = extractSummaryFromDetail(detail);
        if (!StringUtils.hasText(label) && cr != null && StringUtils.hasText(cr.getSummary())) {
            label = cr.getSummary();
        }
        if (!StringUtils.hasText(label)) {
            label = buildRequesterOperationName(cr, context);
        }
        if (!StringUtils.hasText(label)) {
            label = changeRequestRef(cr);
        }
        if (!StringUtils.hasText(label) && cr != null && cr.getId() != null) {
            label = "CR-" + cr.getId();
        }
        if (!StringUtils.hasText(label)) {
            label = "变更详情";
        }
        return truncateOperationName(label);
    }

    private String extractSummaryFromDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        String summary = trimToNull(asText(detail.get("summary")));
        if (!StringUtils.hasText(summary)) {
            summary = trimToNull(asText(detail.get("operationName")));
        }
        return summary;
    }

    private String resolveResourceLabel(ChangeContext context) {
        if (context == null) {
            return "变更";
        }
        return switch (context.resourceType()) {
            case "ROLE" -> "角色";
            case "CUSTOM_ROLE" -> "自定义角色";
            case "ROLE_ASSIGNMENT" -> "角色指派";
            case "PORTAL_MENU" -> "菜单";
            default -> "变更";
        };
    }

    private String resolveTargetLabel(ChangeContext context) {
        if (context == null) {
            return null;
        }
        String label = trimToNull(context.targetLabel());
        if (label != null) {
            return label;
        }
        label = trimToNull(context.canonical());
        if (label != null) {
            return label;
        }
        return trimToNull(context.targetId());
    }

    private void applyApproverContext(Map<String, Object> detail, ChangeRequest cr, ChangeContext context) {
        if (detail == null) {
            return;
        }
        populateDetailWithContext(detail, context);
        boolean approverFlow = Optional
            .ofNullable(detail.get("action"))
            .map(Object::toString)
            .map(v -> v.equalsIgnoreCase("APPROVE") || v.equalsIgnoreCase("REJECT"))
            .orElse(false);
        String requesterOperation = truncateOperationName(buildRequesterOperationName(cr, context));
        if (!approverFlow && StringUtils.hasText(requesterOperation)) {
            detail.put("operationName", requesterOperation);
            detail.put("summary", requesterOperation);
        }
        AuditOperationType requesterType = determineRequesterOperationType(context);
        if (requesterType != null && requesterType != AuditOperationType.UNKNOWN) {
            detail.put("operationType", requesterType.getCode());
            detail.put("operationTypeText", requesterType.getDisplayName());
        }
        detail.put("approverAction", normalizeActionToken(cr != null ? cr.getStatus() : null));
    }

    private AuditOperationKind mapOperationKind(AuditOperationType type) {
        if (type == null || type == AuditOperationType.UNKNOWN) {
            return null;
        }
        return switch (type) {
            case CREATE -> AuditOperationKind.CREATE;
            case UPDATE -> AuditOperationKind.UPDATE;
            case DELETE -> AuditOperationKind.DELETE;
            case ENABLE -> AuditOperationKind.ENABLE;
            case DISABLE -> AuditOperationKind.DISABLE;
            case GRANT -> AuditOperationKind.GRANT;
            case REVOKE -> AuditOperationKind.REVOKE;
            case EXECUTE -> AuditOperationKind.EXECUTE;
            default -> null;
        };
    }

    private String truncateOperationName(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return org.apache.commons.lang3.StringUtils.abbreviate(value.trim(), 120);
    }

    private String truncateSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return org.apache.commons.lang3.StringUtils.abbreviate(value.trim(), 240);
    }

    private record ChangeContext(
        String resourceType,
        String action,
        String targetTable,
        String targetId,
        String targetLabel,
        String canonical,
        Map<String, Object> extras
    ) {
        ChangeContext {
            extras = extras == null ? Map.of() : Map.copyOf(extras);
        }
    }

    private static String changeRequestRef(ChangeRequest cr) {
        if (cr == null || cr.getId() == null) {
            return null;
        }
        return "CR-" + cr.getId();
    }

    private void recordSystemConfigSubmitV2(
        String actor,
        Map<String, Object> cfg,
        Map<String, Object> before,
        Map<String, Object> after,
        ChangeRequest cr,
        String clientIp,
        String clientAgent,
        String requestUri
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String key = Objects.toString(cfg.get("key"), "").trim();
            String changeRequestRef = changeRequestRef(cr);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.SYSTEM_CONFIG_SUBMIT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("提交系统配置变更：" + key)
                .result(AuditResultStatus.SUCCESS)
                .metadata("configKey", key)
                .client(clientIp, clientAgent)
                .request(Optional.ofNullable(requestUri).orElse("/api/admin/system/config"), "POST");
            if (StringUtils.hasText(changeRequestRef)) {
                builder.changeRequestRef(changeRequestRef).target("change_request", cr.getId(), changeRequestRef);
            }
            if (after != null && !after.isEmpty()) {
                builder.detail("after", after);
            }
            if (before != null && !before.isEmpty()) {
                builder.detail("before", before);
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for system config submit: {}", ex.getMessage());
        }
    }

    private void recordSystemConfigSubmitFailureV2(
        String actor,
        Map<String, Object> cfg,
        Map<String, Object> before,
        Exception ex,
        String clientIp,
        String clientAgent,
        String requestUri
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String key = Objects.toString(cfg.get("key"), "").trim();
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.SYSTEM_CONFIG_SUBMIT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("提交系统配置变更失败：" + key)
                .result(AuditResultStatus.FAILED)
                .metadata("configKey", key)
                .detail("error", ex.getMessage())
                .client(clientIp, clientAgent)
                .request(Optional.ofNullable(requestUri).orElse("/api/admin/system/config"), "POST")
                .allowEmptyTargets();
            if (before != null && !before.isEmpty()) {
                builder.detail("before", before);
            }
            auditV2Service.record(builder.build());
        } catch (Exception logEx) {
            log.warn("Failed to record V2 audit for system config failure: {}", logEx.getMessage());
        }
    }

    private void recordPortalMenuActionV2(
        String actor,
        MenuAuditContext.Operation operation,
        AuditResultStatus result,
        Long menuId,
        String menuName,
        ChangeRequest cr,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summaryFallback
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            Map<String, Object> safeDetail = detail == null ? Map.of() : new LinkedHashMap<>(detail);
            String summary = firstNonBlank(
                asText(safeDetail.get("summary")),
                asText(safeDetail.get("operationName")),
                summaryFallback
            );
            String operationName = firstNonBlank(asText(safeDetail.get("operationName")), summary);
            String operationTypeCode = asText(safeDetail.get("operationType"));
            AuditOperationType overrideType = StringUtils.hasText(operationTypeCode) ? AuditOperationType.from(operationTypeCode) : operation.operationType();
            if (overrideType == AuditOperationType.UNKNOWN) {
                overrideType = operation.operationType();
            }
            String clientIp = resolveClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            MenuAuditContext.Builder builder = MenuAuditContext
                .builder(actor, operation)
                .result(result != null ? result : AuditResultStatus.SUCCESS)
                .summary(summary)
                .operationName(operationName)
                .detail(safeDetail)
                .client(clientIp, userAgent, uri, method);
            if (menuId != null) {
                builder.menu(menuId, menuName);
            }
            if (cr != null && cr.getId() != null) {
                String changeRequestRef = changeRequestRef(cr.getId());
                builder.changeRequest(cr.getId(), changeRequestRef);
            }
            if (menuId == null && (cr == null || cr.getId() == null)) {
                builder.allowEmptyTargets(true);
            }
            if (overrideType != null && overrideType != AuditOperationType.UNKNOWN && !safeDetail.containsKey("operationType")) {
                builder.detail("operationType", overrideType.getCode());
                builder.detail("operationTypeText", overrideType.getDisplayName());
            }
            adminAuditService.logMenuAction(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for portal menu action: {}", ex.getMessage());
        }
    }

    private static String changeRequestRef(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return "CR-" + id;
    }

    private void recordOrgActionV2(
        String actor,
        String buttonCode,
        AuditResultStatus result,
        Long orgId,
        String orgName,
        Map<String, Object> detail,
        HttpServletRequest request,
        String fallbackUri,
        String fallbackMethod,
        String summary,
        boolean allowEmptyTargets
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(fallbackUri);
            String method = request != null ? request.getMethod() : fallbackMethod;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(result != null ? result : AuditResultStatus.SUCCESS)
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(uri, method);
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            if (orgId != null) {
                String label = StringUtils.hasText(orgName) ? orgName : String.valueOf(orgId);
                builder.target("organization_node", orgId, label);
            } else if (allowEmptyTargets) {
                builder.allowEmptyTargets();
            } else {
                builder.allowEmptyTargets();
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for org action [{}]: {}", buttonCode, ex.getMessage());
        }
    }

    private void recordDatasetListV2(String actor, int count, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.DATASET_LIST)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("查看数据集列表（共 " + count + " 个）")
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", count)
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/datasets"), request != null ? request.getMethod() : "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for dataset list: {}", ex.getMessage());
        }
    }

    private void recordCustomRoleListV2(String actor, int count, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.CUSTOM_ROLE_LIST)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("查看自定义角色列表（共 " + count + " 条）")
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", count)
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/custom-roles"), request != null ? request.getMethod() : "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for custom role list: {}", ex.getMessage());
        }
    }

    private void recordOrgViewV2(String actor, int nodeCount, HttpServletRequest request, boolean platform) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String defaultUri = platform ? "/api/admin/platform/orgs" : "/api/admin/orgs";
            String summary = platform
                ? "查看组织结构（平台视图，节点 " + nodeCount + " 个）"
                : "查看组织结构（节点 " + nodeCount + " 个）";
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.ORG_LIST)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary(summary)
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("nodeCount", nodeCount)
                    .metadata("audience", platform ? "platform" : "admin")
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(defaultUri), request != null ? request.getMethod() : "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for org view (platform={}): {}", platform, ex.getMessage());
        }
    }

    private void recordPermissionCatalogV2(String actor, int count, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.PERMISSION_CATALOG_VIEW)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("查看权限目录（共 " + count + " 类）")
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", count)
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/permissions/catalog"), request != null ? request.getMethod() : "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for permission catalog: {}", ex.getMessage());
        }
    }

    private void recordRoleAssignmentListV2(String actor, int count, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            auditV2Service.record(
                AuditActionRequest
                    .builder(actor, ButtonCodes.ROLE_ASSIGNMENT_LIST)
                    .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                    .summary("查看角色指派列表（共 " + count + " 条）")
                    .result(AuditResultStatus.SUCCESS)
                    .metadata("count", count)
                    .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                    .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/role-assignments"), request != null ? request.getMethod() : "GET")
                    .allowEmptyTargets()
                    .build()
            );
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for role assignment list: {}", ex.getMessage());
        }
    }

    private void recordRoleAssignmentCreateV2(
        String actor,
        ChangeRequest changeRequest,
        Map<String, Object> payload,
        Map<String, Object> detail,
        AuditResultStatus result,
        String failureMessage,
        HttpServletRequest request
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String username = asText(payload != null ? payload.get("username") : null);
            String displayName = asText(payload != null ? payload.get("displayName") : null);
            String role = asText(payload != null ? payload.get("role") : null);
            String successSummary = firstNonBlank(
                StringUtils.hasText(displayName) && StringUtils.hasText(username) ? "提交角色指派申请：" + displayName + "(" + username + ")" : null,
                StringUtils.hasText(displayName) ? "提交角色指派申请：" + displayName : null,
                StringUtils.hasText(username) ? "提交角色指派申请：" + username : null,
                "提交角色指派申请"
            );
            String failureSummary = firstNonBlank(
                StringUtils.hasText(displayName) && StringUtils.hasText(username) ? "角色指派申请失败：" + displayName + "(" + username + ")" : null,
                StringUtils.hasText(displayName) ? "角色指派申请失败：" + displayName : null,
                StringUtils.hasText(username) ? "角色指派申请失败：" + username : null,
                failureMessage != null ? "角色指派申请失败：" + failureMessage : null,
                "角色指派申请失败"
            );
            String summary = result == AuditResultStatus.SUCCESS ? successSummary : failureSummary;
            Map<String, Object> detailPayload = new LinkedHashMap<>();
            if (detail != null) {
                detailPayload.putAll(detail);
            }
            if (payload != null) {
                if (StringUtils.hasText(role)) {
                    detailPayload.put("role", role);
                }
                if (StringUtils.hasText(username)) {
                    detailPayload.put("username", username);
                }
                if (StringUtils.hasText(displayName)) {
                    detailPayload.put("displayName", displayName);
                }
                Object securityLevel = payload.get("userSecurityLevel");
                if (securityLevel != null) {
                    detailPayload.put("userSecurityLevel", securityLevel);
                }
                Object scopeOrgId = payload.get("scopeOrgId");
                if (scopeOrgId != null) {
                    detailPayload.put("scopeOrgId", scopeOrgId);
                }
            }
            if (failureMessage != null) {
                detailPayload.put("error", failureMessage);
            }
            Map<String, Object> afterSnapshot = new LinkedHashMap<>();
            if (StringUtils.hasText(role)) {
                afterSnapshot.put("role", role);
            }
            if (StringUtils.hasText(username)) {
                afterSnapshot.put("username", username);
            }
            if (StringUtils.hasText(displayName)) {
                afterSnapshot.put("displayName", displayName);
            }
            Object userSecurityLevel = payload != null ? payload.get("userSecurityLevel") : null;
            if (userSecurityLevel != null) {
                afterSnapshot.put("userSecurityLevel", userSecurityLevel);
            }
            Object scopeOrgId = payload != null ? payload.get("scopeOrgId") : null;
            if (scopeOrgId != null) {
                afterSnapshot.put("scopeOrgId", scopeOrgId);
            }
            if (!afterSnapshot.isEmpty()) {
                detailPayload.put("after", afterSnapshot);
            }
            String clientIp = resolveClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/role-assignments");
            String method = request != null ? request.getMethod() : "POST";
            RoleAuditContext.Builder builder = RoleAuditContext
                .builder(actor, RoleAuditContext.Operation.ASSIGN_ROLE)
                .result(result)
                .summary(summary)
                .operationName(summary)
                .detail(detailPayload)
                .client(clientIp, userAgent, uri, method)
                .allowEmptyTargets(true);
            if (changeRequest != null && changeRequest.getId() != null) {
                String ref = changeRequestRef(changeRequest);
                builder.changeRequest(changeRequest.getId(), ref);
            }
            adminAuditService.logRoleAction(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for role assignment create: {}", ex.getMessage());
        }
    }

    private void recordCustomRoleCreateV2(
        String actor,
        ChangeRequest changeRequest,
        Map<String, Object> payload,
        Map<String, Object> detail,
        AuditResultStatus result,
        String failureMessage,
        HttpServletRequest request
    ) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        if (result == AuditResultStatus.SUCCESS && changeRequest != null) {
            return;
        }
        try {
            String name = asText(payload != null ? payload.get("name") : null);
            String scope = asText(payload != null ? payload.get("scope") : null);
            Object ops = payload != null ? payload.get("operations") : null;
            String successSummary = StringUtils.hasText(name) ? "提交自定义角色申请：" + name : "提交自定义角色申请";
            String failureSummary = failureMessage != null
                ? (StringUtils.hasText(name) ? "自定义角色申请失败：" + name + "（" + failureMessage + "）" : "自定义角色申请失败：" + failureMessage)
                : (StringUtils.hasText(name) ? "自定义角色申请失败：" + name : "自定义角色申请失败");
            String summary = result == AuditResultStatus.SUCCESS ? successSummary : failureSummary;
            Map<String, Object> detailPayload = new LinkedHashMap<>();
            if (detail != null) {
                detailPayload.putAll(detail);
            }
            if (payload != null) {
                if (StringUtils.hasText(name)) {
                    detailPayload.put("name", name);
                }
                if (StringUtils.hasText(scope)) {
                    detailPayload.put("scope", scope);
                }
                if (payload.containsKey("operations")) {
                    detailPayload.put("operations", payload.get("operations"));
                }
                if (payload.containsKey("description")) {
                    detailPayload.put("description", payload.get("description"));
                }
            }
            if (failureMessage != null) {
                detailPayload.put("error", failureMessage);
            }
            Map<String, Object> afterSnapshot = new LinkedHashMap<>();
            if (payload != null) {
                if (StringUtils.hasText(name)) {
                    afterSnapshot.put("name", name);
                }
                if (StringUtils.hasText(scope)) {
                    afterSnapshot.put("scope", scope);
                }
                if (payload.containsKey("operations")) {
                    Object operations = payload.get("operations");
                    if (operations instanceof java.util.Collection<?> collection) {
                        afterSnapshot.put("operations", new java.util.ArrayList<>(collection));
                    } else {
                        afterSnapshot.put("operations", operations);
                    }
                }
                if (payload.containsKey("maxRows")) {
                    afterSnapshot.put("maxRows", payload.get("maxRows"));
                }
                if (payload.containsKey("allowDesensitizeJson")) {
                    afterSnapshot.put("allowDesensitizeJson", payload.get("allowDesensitizeJson"));
                }
                if (payload.containsKey("titleCn")) {
                    afterSnapshot.put("titleCn", payload.get("titleCn"));
                }
                if (payload.containsKey("titleEn")) {
                    afterSnapshot.put("titleEn", payload.get("titleEn"));
                }
                if (payload.containsKey("displayName")) {
                    afterSnapshot.put("displayName", payload.get("displayName"));
                }
            }
            if (!afterSnapshot.isEmpty()) {
                detailPayload.put("after", afterSnapshot);
            }
            String clientIp = resolveClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            String uri = Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/custom-roles");
            String method = request != null ? request.getMethod() : "POST";
            RoleAuditContext.Builder builder = RoleAuditContext
                .builder(actor, RoleAuditContext.Operation.ROLE_CREATE)
                .result(result)
                .summary(summary)
                .operationName(summary)
                .detail(detailPayload)
                .client(clientIp, userAgent, uri, method)
                .allowEmptyTargets(true);
            if (changeRequest != null && changeRequest.getId() != null) {
                String ref = changeRequestRef(changeRequest);
                builder.changeRequest(changeRequest.getId(), ref);
            }
            adminAuditService.logRoleAction(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for custom role create: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestListV2(String actor, int count, HttpServletRequest request, boolean mine) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            String summary = mine ? "查看我的变更请求（共 " + count + " 条）" : "查看变更请求列表（共 " + count + " 条）";
            String defaultUri = mine ? "/api/admin/change-requests/mine" : "/api/admin/change-requests";
            String buttonCode = mine ? ButtonCodes.CHANGE_REQUEST_LIST_MINE : ButtonCodes.CHANGE_REQUEST_LIST;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .metadata("count", count)
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse(defaultUri), request != null ? request.getMethod() : "GET")
                .allowEmptyTargets();
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request list [{}]: {}", mine ? "mine" : "all", ex.getMessage());
        }
    }

    private void recordChangeRequestViewV2(String actor, ChangeRequest cr, Map<String, Object> detail, HttpServletRequest request) {
        if (!StringUtils.hasText(actor) || cr == null) {
            return;
        }
        try {
            ChangeContext context = computeChangeContext(cr);
            String changeRequestRef = changeRequestRef(cr);
            String summaryLabel = resolveChangeViewSummary(cr, context, detail);
            String summary = truncateSummary("查看变更详情：" + summaryLabel);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_VIEW)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .changeRequestRef(changeRequestRef)
                .metadata("changeRequestId", cr.getId())
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/change-requests/" + cr.getId()), request != null ? request.getMethod() : "GET");
            if (StringUtils.hasText(cr.getResourceType())) {
                builder.metadata("resourceType", cr.getResourceType());
            }
            if (StringUtils.hasText(cr.getResourceId())) {
                builder.metadata("resourceId", cr.getResourceId());
            }
            if (StringUtils.hasText(cr.getAction())) {
                builder.metadata("action", cr.getAction());
            }
            if (StringUtils.hasText(cr.getCategory())) {
                builder.metadata("category", cr.getCategory());
            }
            builder.operationOverride(
                AdminAuditOperation.ADMIN_CHANGE_REQUEST_VIEW.code(),
                "查看变更详情",
                AuditOperationKind.QUERY
            );
            builder.target("change_request", cr.getId(), StringUtils.hasText(changeRequestRef) ? changeRequestRef : String.valueOf(cr.getId()));
            if (context != null) {
                if (StringUtils.hasText(context.targetTable()) && StringUtils.hasText(context.targetId())) {
                    builder.target(context.targetTable(), context.targetId(), context.targetLabel());
                }
                if (context.extras() != null && !context.extras().isEmpty()) {
                    builder.detail("context", new LinkedHashMap<>(context.extras()));
                }
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request view: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestPurgeV2(String actor, long approvals, long changes, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_PURGE)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("清理历史审批/变更数据（审批 " + approvals + " 条，变更 " + changes + " 条）")
                .result(AuditResultStatus.SUCCESS)
                .metadata("deletedApprovals", approvals)
                .metadata("deletedChangeRequests", changes)
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/maintenance/purge-requests"), request != null ? request.getMethod() : "POST")
                .allowEmptyTargets();
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for purge requests: {}", ex.getMessage());
        }
    }


    private void recordChangeRequestDraftFailure(String actor, Map<String, Object> detail, HttpServletRequest request) {
        if (!StringUtils.hasText(actor)) {
            return;
        }
        try {
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_DRAFT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary("创建变更草稿失败")
                .result(AuditResultStatus.FAILED)
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/change-requests"), request != null ? request.getMethod() : "POST")
                .allowEmptyTargets();
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request draft failure: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestDraftV2(String actor, ChangeRequest cr, HttpServletRequest request, Map<String, Object> detail) {
        if (!StringUtils.hasText(actor) || cr == null) {
            return;
        }
        try {
            ChangeContext context = computeChangeContext(cr);
            String summary = firstNonBlank(
                buildRequesterOperationName(cr, context),
                "创建变更草稿"
            );
            String ref = changeRequestRef(cr);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_DRAFT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .client(resolveClientIp(request), request != null ? request.getHeader("User-Agent") : null)
                .request(Optional.ofNullable(request).map(HttpServletRequest::getRequestURI).orElse("/api/admin/change-requests"), request != null ? request.getMethod() : "POST");
            if (StringUtils.hasText(ref)) {
                builder.changeRequestRef(ref);
            }
            builder.target("change_request", cr.getId(), StringUtils.hasText(ref) ? ref : String.valueOf(cr.getId()));
            if (StringUtils.hasText(cr.getResourceType())) {
                builder.metadata("resourceType", cr.getResourceType());
            }
            if (StringUtils.hasText(cr.getResourceId())) {
                builder.metadata("resourceId", cr.getResourceId());
            }
            if (StringUtils.hasText(cr.getAction())) {
                builder.metadata("action", cr.getAction());
            }
            if (StringUtils.hasText(cr.getCategory())) {
                builder.metadata("category", cr.getCategory());
            }
            if (context != null && context.extras() != null && !context.extras().isEmpty()) {
                builder.detail("context", new LinkedHashMap<>(context.extras()));
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request draft: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestSubmitV2(
        String actor,
        ChangeRequest cr,
        Map<String, Object> detail,
        ChangeContext context,
        String clientIp,
        String clientAgent,
        String requestUri
    ) {
        if (!StringUtils.hasText(actor) || cr == null) {
            return;
        }
        try {
            String changeRequestRef = changeRequestRef(cr);
            String summary = firstNonBlank(
                asText(detail != null ? detail.get("operationName") : null),
                asText(detail != null ? detail.get("summary") : null),
                buildRequesterOperationName(cr, context),
                "提交变更申请"
            );
            summary = truncateSummary(summary);
            String overrideName = truncateOperationName(summary);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_SUBMIT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(AuditResultStatus.SUCCESS)
                .changeRequestRef(changeRequestRef)
                .metadata("changeRequestId", cr.getId())
                .metadata("resourceType", cr.getResourceType())
                .metadata("action", cr.getAction())
                .client(clientIp, clientAgent)
                .request(Optional.ofNullable(requestUri).orElse("/api/admin/change-requests/" + cr.getId() + "/submit"), "POST");

            builder.target("change_request", cr.getId(), changeRequestRef);
            if (context != null) {
                if (StringUtils.hasText(context.targetTable()) && StringUtils.hasText(context.targetId())) {
                    builder.target(context.targetTable(), context.targetId(), context.targetLabel());
                }
                if (context.extras() != null && !context.extras().isEmpty()) {
                    builder.detail("context", new LinkedHashMap<>(context.extras()));
                }
            }
            AuditOperationType requesterType = determineRequesterOperationType(context);
            if (detail != null && requesterType != null && requesterType != AuditOperationType.UNKNOWN) {
                detail.put("operationType", requesterType.getCode());
                detail.put("operationTypeText", requesterType.getDisplayName());
            }
            AuditOperationKind overrideKind = mapOperationKind(requesterType);
            if (overrideKind != null) {
                builder.operationOverride(
                    AdminAuditOperation.ADMIN_CHANGE_REQUEST_MANAGE.code(),
                    overrideName,
                    overrideKind
                );
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request submit: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestApproveV2(
        String actor,
        ChangeRequest cr,
        Map<String, Object> approverDetail,
        ChangeContext context,
        boolean applied,
        String clientIp,
        String clientAgent,
        String requestUri
    ) {
        if (!StringUtils.hasText(actor) || cr == null) {
            return;
        }
        try {
            String primaryRef = changeRequestRef(cr);
            String baseSummary = truncateOperationName(buildApprovalOperationName(cr, context, true));
            String decision = applied ? "APPROVED" : "FAILED";
            String displaySummary = truncateSummary(applied ? baseSummary : baseSummary + "（失败）");
            if (approverDetail != null) {
                approverDetail.put("summary", baseSummary);
                approverDetail.put("operationName", baseSummary);
                approverDetail.put("decision", decision);
            }
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_APPROVE)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(displaySummary)
                .result(applied ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED)
                .changeRequestRef(primaryRef)
                .metadata("changeRequestId", cr.getId())
                .metadata("resourceType", cr.getResourceType())
                .metadata("action", cr.getAction())
                .metadata("category", cr.getCategory())
                .metadata("decision", decision)
                .client(clientIp, clientAgent)
                .request(Optional.ofNullable(requestUri).orElse("/api/admin/change-requests/" + cr.getId() + "/approve"), "POST");

            builder.target("change_request", cr.getId(), primaryRef);
            if (context != null) {
                if (StringUtils.hasText(context.targetTable()) && StringUtils.hasText(context.targetId())) {
                    builder.target(context.targetTable(), context.targetId(), context.targetLabel());
                }
                if (context.extras() != null && !context.extras().isEmpty()) {
                    builder.detail("context", new LinkedHashMap<>(context.extras()));
                }
            }
            AuditOperationType requesterType = resolveApproverOperationType(cr, context);
            if (approverDetail != null && requesterType != null && requesterType != AuditOperationType.UNKNOWN) {
                approverDetail.put("operationType", requesterType.getCode());
                approverDetail.put("operationTypeText", requesterType.getDisplayName());
            }
            AuditOperationKind overrideKind = mapOperationKind(requesterType);
            if (overrideKind != null) {
                builder.operationOverride(
                    AdminAuditOperation.ADMIN_CHANGE_REQUEST_MANAGE.code(),
                    baseSummary,
                    overrideKind
                );
            }
            if (approverDetail != null && !approverDetail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(approverDetail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request approval: {}", ex.getMessage());
        }
    }

    private void recordChangeRequestRejectV2(
        String actor,
        ChangeRequest cr,
        Map<String, Object> detail,
        ChangeContext context,
        String clientIp,
        String clientAgent,
        String requestUri
    ) {
        if (!StringUtils.hasText(actor) || cr == null) {
            return;
        }
        try {
            String changeRequestRef = changeRequestRef(cr);
            String decision = "REJECTED";
            String baseSummary = truncateOperationName(buildApprovalOperationName(cr, context, false));
            if (detail != null) {
                detail.put("summary", baseSummary);
                detail.put("operationName", baseSummary);
                detail.put("decision", decision);
            }
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, ButtonCodes.CHANGE_REQUEST_REJECT)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(truncateSummary(baseSummary))
                .result(AuditResultStatus.SUCCESS)
                .changeRequestRef(changeRequestRef)
                .metadata("changeRequestId", cr.getId())
                .metadata("resourceType", cr.getResourceType())
                .metadata("action", cr.getAction())
                .metadata("category", cr.getCategory())
                .metadata("reason", cr.getReason())
                .metadata("decision", decision)
                .client(clientIp, clientAgent)
                .request(Optional.ofNullable(requestUri).orElse("/api/admin/change-requests/" + cr.getId() + "/reject"), "POST");
            builder.target("change_request", cr.getId(), changeRequestRef);
            if (context != null) {
                if (StringUtils.hasText(context.targetTable()) && StringUtils.hasText(context.targetId())) {
                    builder.target(context.targetTable(), context.targetId(), context.targetLabel());
                }
                if (context.extras() != null && !context.extras().isEmpty()) {
                    builder.detail("context", new LinkedHashMap<>(context.extras()));
                }
            }
            AuditOperationType requesterType = resolveApproverOperationType(cr, context);
            if (detail != null && requesterType != null && requesterType != AuditOperationType.UNKNOWN) {
                detail.put("operationType", requesterType.getCode());
                detail.put("operationTypeText", requesterType.getDisplayName());
            }
            AuditOperationKind overrideKind = mapOperationKind(requesterType);
            if (overrideKind != null) {
                builder.operationOverride(
                    AdminAuditOperation.ADMIN_CHANGE_REQUEST_MANAGE.code(),
                    baseSummary,
                    overrideKind
                );
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            log.warn("Failed to record V2 audit for change request rejection: {}", ex.getMessage());
        }
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] segments = forwarded.split(",");
            if (segments.length > 0) {
                return segments[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    AuditStage resolveStageForChangeOutcome(ChangeRequest cr, boolean applied) {
        String status = cr != null && StringUtils.hasText(cr.getStatus())
            ? cr.getStatus().trim().toUpperCase(Locale.ROOT)
            : "";
        if ("FAILED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status)) {
            return AuditStage.FAIL;
        }
        if (cr != null && StringUtils.hasText(cr.getLastError())) {
            return AuditStage.FAIL;
        }
        if ("APPROVED".equals(status) || "APPLIED".equals(status) || "COMPLETED".equals(status)) {
            return AuditStage.SUCCESS;
        }
        if ("PENDING".equals(status) || "SUBMITTED".equals(status) || "APPROVAL_PENDING".equals(status)) {
            return AuditStage.BEGIN;
        }
        if (applied) {
            return AuditStage.SUCCESS;
        }
        return AuditStage.BEGIN;
    }

    Map<String, Object> buildChangeAuditDetail(ChangeRequest cr) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (cr == null) {
            return detail;
        }
        attachChangeRequestMetadata(detail, cr);
        detail.put("changeRequestId", cr.getId());
        if (StringUtils.hasText(cr.getResourceType())) detail.put("resourceType", cr.getResourceType());
        if (StringUtils.hasText(cr.getAction())) detail.put("action", cr.getAction());
        if (StringUtils.hasText(cr.getCategory())) detail.put("category", cr.getCategory());
        if (StringUtils.hasText(cr.getStatus())) detail.put("status", cr.getStatus());
        if (StringUtils.hasText(cr.getRequestedBy())) detail.put("requestedBy", cr.getRequestedBy());
        if (StringUtils.hasText(cr.getDecidedBy())) detail.put("decidedBy", cr.getDecidedBy());
        if (cr.getRequestedAt() != null) detail.put("submittedAt", cr.getRequestedAt().toString());
        if (cr.getDecidedAt() != null) detail.put("decidedAt", cr.getDecidedAt().toString());
        if (StringUtils.hasText(cr.getReason())) detail.put("reason", cr.getReason());
        if (StringUtils.hasText(cr.getLastError())) detail.put("error", cr.getLastError());
        if (StringUtils.hasText(cr.getResourceId())) detail.put("resourceId", cr.getResourceId());
        return detail;
    }

    private void attachChangeRequestMetadata(Map<String, Object> target, ChangeRequest cr) {
        if (target == null || cr == null) {
            return;
        }
        if (!target.containsKey("changeRequestId") && cr.getId() != null) {
            target.put("changeRequestId", cr.getId());
        }
        String ref = changeRequestRef(cr);
        if (StringUtils.hasText(ref) && !target.containsKey("changeRequestRef")) {
            target.put("changeRequestRef", ref);
        }
        if (StringUtils.hasText(cr.getSummary()) && !target.containsKey("summary")) {
            target.put("summary", cr.getSummary());
        }
        if (StringUtils.hasText(cr.getReason()) && !target.containsKey("reason")) {
            target.put("reason", cr.getReason());
        }
        ChangeSnapshot snapshot = ChangeSnapshot.fromJson(cr.getDiffJson(), JSON_MAPPER);
        if (snapshot != null && (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty())) {
            target.putIfAbsent("changeSnapshot", snapshot.toMap());
            List<Map<String, String>> summary = changeSnapshotFormatter.format(snapshot, cr.getResourceType());
            if (!summary.isEmpty()) {
                target.putIfAbsent("changeSummary", summary);
            }
        }
    }

    private void appendChangeSummary(
        Map<String, Object> detail,
        String resourceType,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        if (detail == null) {
            return;
        }

        List<Map<String, String>> summaryEntries = new ArrayList<>();
        if (changeSnapshotFormatter != null) {
            summaryEntries.addAll(changeSnapshotFormatter.format(before, after, resourceType));
        }

        if ("PORTAL_MENU".equalsIgnoreCase(resourceType)) {
            MenuChangeSummaryResult menuSummary = buildPortalMenuSummary(before, after);
            if (!menuSummary.menuChanges().isEmpty()) {
                mergeMenuChanges(detail, menuSummary.menuChanges());
            }
            if (!menuSummary.summaryLines().isEmpty()) {
                summaryEntries.addAll(menuSummary.summaryLines());
            }
        }

        if (summaryEntries.isEmpty()) {
            return;
        }
        mergeChangeSummary(detail, summaryEntries);

        if (!detail.containsKey("changeSnapshot")) {
            ChangeSnapshot snapshot = ChangeSnapshot.of(before, after);
            if (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty()) {
                detail.put("changeSnapshot", snapshot.toMap());
            }
        }
    }

    private void mergeMenuChanges(Map<String, Object> detail, List<Map<String, Object>> additions) {
        if (CollectionUtils.isEmpty(additions)) {
            return;
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        Object existing = detail.get("menuChanges");
        if (existing instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Map<?, ?> map) {
                    merged.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        merged.addAll(additions);
        detail.put("menuChanges", merged);
    }

    private void mergeChangeSummary(Map<String, Object> detail, List<Map<String, String>> additions) {
        if (CollectionUtils.isEmpty(additions)) {
            return;
        }
        List<Map<String, String>> merged = new ArrayList<>();
        Object existing = detail.get("changeSummary");
        if (existing instanceof Collection<?> col) {
            for (Object item : col) {
                if (item instanceof Map<?, ?> map) {
                    merged.add(new LinkedHashMap<>((Map<String, String>) map));
                }
            }
        }
        merged.addAll(additions);
        detail.put("changeSummary", merged);
    }

    private MenuChangeSummaryResult buildPortalMenuSummary(Map<String, Object> before, Map<String, Object> after) {
        MenuChangeSummaryResult result = new MenuChangeSummaryResult();
        Map<String, Object> beforeMap = before == null ? Map.of() : before;
        Map<String, Object> afterMap = after == null ? Map.of() : after;

        LinkedHashSet<String> beforeRoles = extractMenuRoles(beforeMap);
        LinkedHashSet<String> afterRoles = extractMenuRoles(afterMap);

        List<String> beforeRoleList = beforeRoles
            .stream()
            .map(this::displayRoleCode)
            .filter(StringUtils::hasText)
            .toList();
        List<String> afterRoleList = afterRoles
            .stream()
            .map(this::displayRoleCode)
            .filter(StringUtils::hasText)
            .toList();

        List<String> addedRoles = new ArrayList<>();
        for (String role : afterRoles) {
            if (!beforeRoles.contains(role)) {
                addedRoles.add(displayRoleCode(role));
            }
        }
        List<String> removedRoles = new ArrayList<>();
        for (String role : beforeRoles) {
            if (!afterRoles.contains(role)) {
                removedRoles.add(displayRoleCode(role));
            }
        }

        LinkedHashSet<String> beforePermissions = extractMenuPermissions(beforeMap);
        LinkedHashSet<String> afterPermissions = extractMenuPermissions(afterMap);
        List<String> beforePermissionList = new ArrayList<>(beforePermissions);
        List<String> afterPermissionList = new ArrayList<>(afterPermissions);

        List<String> addedPermissions = new ArrayList<>();
        for (String perm : afterPermissions) {
            if (!beforePermissions.contains(perm)) {
                addedPermissions.add(perm);
            }
        }
        List<String> removedPermissions = new ArrayList<>();
        for (String perm : beforePermissions) {
            if (!afterPermissions.contains(perm)) {
                removedPermissions.add(perm);
            }
        }

        LinkedHashSet<VisibilityRuleKey> beforeRules = extractMenuVisibilityRules(beforeMap);
        LinkedHashSet<VisibilityRuleKey> afterRules = extractMenuVisibilityRules(afterMap);
        List<VisibilityRuleKey> addedRuleKeys = new ArrayList<>();
        for (VisibilityRuleKey key : afterRules) {
            if (!beforeRules.contains(key)) {
                addedRuleKeys.add(key);
            }
        }
        List<VisibilityRuleKey> removedRuleKeys = new ArrayList<>();
        for (VisibilityRuleKey key : beforeRules) {
            if (!afterRules.contains(key)) {
                removedRuleKeys.add(key);
            }
        }

        String beforeMaxLevel = normalizeMenuLevel(beforeMap.get("maxDataLevel"), true);
        String afterMaxLevel = normalizeMenuLevel(afterMap.get("maxDataLevel"), true);
        boolean maxLevelChanged = !Objects.equals(beforeMaxLevel, afterMaxLevel);

        String beforeSecurity = normalizeMenuLevel(beforeMap.get("securityLevel"), false);
        String afterSecurity = normalizeMenuLevel(afterMap.get("securityLevel"), false);
        boolean securityChanged = !Objects.equals(beforeSecurity, afterSecurity);

        boolean beforeDeleted = toBoolean(beforeMap.get("deleted"));
        boolean afterDeleted = toBoolean(afterMap.get("deleted"));
        boolean statusChanged = (beforeMap.containsKey("deleted") || afterMap.containsKey("deleted")) && beforeDeleted != afterDeleted;

        if (
            addedRoles.isEmpty() &&
            removedRoles.isEmpty() &&
            addedPermissions.isEmpty() &&
            removedPermissions.isEmpty() &&
            addedRuleKeys.isEmpty() &&
            removedRuleKeys.isEmpty() &&
            !maxLevelChanged &&
            !securityChanged &&
            !statusChanged
        ) {
            return result;
        }

        String menuId = stringValue(afterMap.get("id"));
        if (!StringUtils.hasText(menuId)) {
            menuId = stringValue(beforeMap.get("id"));
        }
        String menuTitle = firstNonBlank(
            resolveMenuTitle(afterMap),
            resolveMenuTitle(beforeMap),
            stringValue(afterMap.get("name")),
            stringValue(beforeMap.get("name")),
            stringValue(afterMap.get("path")),
            stringValue(beforeMap.get("path")),
            menuId
        );
        String menuPath = firstNonBlank(stringValue(afterMap.get("path")), stringValue(beforeMap.get("path")));
        String menuName = firstNonBlank(stringValue(afterMap.get("name")), stringValue(beforeMap.get("name")));

        Map<String, Object> entry = new LinkedHashMap<>();
        if (StringUtils.hasText(menuId)) {
            entry.put("menuId", menuId);
        }
        if (StringUtils.hasText(menuName)) {
            entry.put("menuName", menuName);
        }
        if (StringUtils.hasText(menuTitle)) {
            entry.put("menuTitle", menuTitle);
        }
        if (StringUtils.hasText(menuPath)) {
            entry.put("menuPath", menuPath);
        }
        if (!beforeRoleList.isEmpty()) {
            entry.put("allowedRolesBefore", beforeRoleList);
        }
        if (!afterRoleList.isEmpty()) {
            entry.put("allowedRolesAfter", afterRoleList);
        }
        if (!addedRoles.isEmpty()) {
            entry.put("addedRoles", addedRoles);
        }
        if (!removedRoles.isEmpty()) {
            entry.put("removedRoles", removedRoles);
        }
        if (!beforePermissionList.isEmpty()) {
            entry.put("allowedPermissionsBefore", beforePermissionList);
        }
        if (!afterPermissionList.isEmpty()) {
            entry.put("allowedPermissionsAfter", afterPermissionList);
        }
        if (!addedPermissions.isEmpty()) {
            entry.put("addedPermissions", addedPermissions);
        }
        if (!removedPermissions.isEmpty()) {
            entry.put("removedPermissions", removedPermissions);
        }
        if (!addedRuleKeys.isEmpty()) {
            entry.put("addedRules", addedRuleKeys.stream().map(this::toRuleDetail).toList());
        }
        if (!removedRuleKeys.isEmpty()) {
            entry.put("removedRules", removedRuleKeys.stream().map(this::toRuleDetail).toList());
        }
        if (maxLevelChanged) {
            entry.put("maxDataLevelBefore", beforeMaxLevel);
            entry.put("maxDataLevelAfter", afterMaxLevel);
            entry.put("maxDataLevelBeforeLabel", labelForMenuLevel(beforeMaxLevel, null));
            entry.put("maxDataLevelAfterLabel", labelForMenuLevel(afterMaxLevel, null));
        }
        if (securityChanged) {
            entry.put("securityLevelBefore", beforeSecurity);
            entry.put("securityLevelAfter", afterSecurity);
            entry.put("securityLevelBeforeLabel", labelForSecurityLevel(beforeSecurity, null));
            entry.put("securityLevelAfterLabel", labelForSecurityLevel(afterSecurity, null));
        }
        if (statusChanged) {
            entry.put("deletedBefore", beforeDeleted);
            entry.put("deletedAfter", afterDeleted);
            entry.put("statusBeforeLabel", beforeDeleted ? "禁用" : "启用");
            entry.put("statusAfterLabel", afterDeleted ? "禁用" : "启用");
        }
        result.menuChanges().add(entry);

        List<String> summaryParts = new ArrayList<>();
        if (!addedRoles.isEmpty()) {
            summaryParts.add("新增角色：" + formatRoleList(addedRoles));
        }
        if (!removedRoles.isEmpty()) {
            summaryParts.add("移除角色：" + formatRoleList(removedRoles));
        }
        if (!addedPermissions.isEmpty()) {
            summaryParts.add("新增权限：" + String.join("、", addedPermissions));
        }
        if (!removedPermissions.isEmpty()) {
            summaryParts.add("移除权限：" + String.join("、", removedPermissions));
        }
        if (!addedRuleKeys.isEmpty()) {
            summaryParts.add("新增规则：" + formatRoleList(addedRuleKeys.stream().map(this::formatRuleLabel).toList()));
        }
        if (!removedRuleKeys.isEmpty()) {
            summaryParts.add("移除规则：" + formatRoleList(removedRuleKeys.stream().map(this::formatRuleLabel).toList()));
        }
        if (maxLevelChanged) {
            summaryParts.add("最大数据密级：" + labelForMenuLevel(beforeMaxLevel, "未设置") + " → " + labelForMenuLevel(afterMaxLevel, "未设置"));
        }
        if (securityChanged) {
            summaryParts.add("访问密级：" + labelForSecurityLevel(beforeSecurity, "未设置") + " → " + labelForSecurityLevel(afterSecurity, "未设置"));
        }
        if (statusChanged) {
            summaryParts.add("状态：" + (beforeDeleted ? "禁用" : "启用") + " → " + (afterDeleted ? "禁用" : "启用"));
        }

        if (!summaryParts.isEmpty()) {
            String menuLabel = firstNonBlank(menuTitle, menuName, menuPath, StringUtils.hasText(menuId) ? "菜单#" + menuId : "菜单");
            String summaryText = "菜单「" + menuLabel + "」：" + String.join("；", summaryParts);
            Map<String, String> summaryLine = new LinkedHashMap<>();
            summaryLine.put("field", StringUtils.hasText(menuId) ? "menu#" + menuId : "menuChanges");
            summaryLine.put("label", menuLabel);
            summaryLine.put("before", "");
            summaryLine.put("after", summaryText);
            result.summaryLines().add(summaryLine);
        }

        return result;
    }

    private LinkedHashSet<String> extractMenuRoles(Map<String, Object> payload) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        if (payload == null) {
            return roles;
        }
        Object raw = payload.get("allowedRoles");
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                String normalized = normalizeRoleCode(item);
                if (StringUtils.hasText(normalized)) {
                    roles.add(normalized);
                }
            }
        } else if (raw != null) {
            String normalized = normalizeRoleCode(raw);
            if (StringUtils.hasText(normalized)) {
                roles.add(normalized);
            }
        }
        if (roles.isEmpty()) {
            for (VisibilityRuleKey key : extractMenuVisibilityRules(payload)) {
                if (StringUtils.hasText(key.role())) {
                    roles.add("ROLE_" + key.role());
                }
            }
        }
        return roles;
    }

    private LinkedHashSet<String> extractMenuPermissions(Map<String, Object> payload) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (payload == null) {
            return permissions;
        }
        Object raw = payload.get("allowedPermissions");
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    String text = item.toString().trim();
                    if (!text.isEmpty()) {
                        permissions.add(text);
                    }
                }
            }
        } else if (raw != null) {
            String text = raw.toString().trim();
            if (!text.isEmpty()) {
                permissions.add(text);
            }
        }
        if (permissions.isEmpty()) {
            for (VisibilityRuleKey key : extractMenuVisibilityRules(payload)) {
                if (StringUtils.hasText(key.permission())) {
                    permissions.add(key.permission());
                }
            }
        }
        return permissions;
    }

    private String resolveMenuTitle(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String directTitle = stringValue(payload.get("title"));
        if (StringUtils.hasText(directTitle)) {
            return directTitle;
        }
        String displayName = stringValue(payload.get("displayName"));
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        Map<String, Object> metadata = extractMetadataMap(payload.get("metadata"));
        if (!metadata.isEmpty()) {
            String title = stringValue(metadata.get("title"));
            if (StringUtils.hasText(title)) {
                return title;
            }
            String label = stringValue(metadata.get("label"));
            if (StringUtils.hasText(label)) {
                return label;
            }
            String titleKey = stringValue(metadata.get("titleKey"));
            if (StringUtils.hasText(titleKey)) {
                String resolved = portalMenuService.resolveTitleByKey(titleKey).orElse(null);
                if (StringUtils.hasText(resolved)) {
                    return resolved;
                }
                return titleKey;
            }
        }
        String nameCode = stringValue(payload.get("name"));
        if (StringUtils.hasText(nameCode)) {
            String resolved = portalMenuService.resolveTitleByKey(nameCode).orElse(null);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private Map<String, Object> extractMetadataMap(Object metadata) {
        if (metadata == null) {
            return Map.of();
        }
        if (metadata instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), v));
            return converted;
        }
        if (metadata instanceof String text && StringUtils.hasText(text)) {
            try {
                Map<String, Object> parsed = JSON_MAPPER.readValue(text, Map.class);
                Map<String, Object> converted = new LinkedHashMap<>();
                parsed.forEach((k, v) -> converted.put(String.valueOf(k), v));
                return converted;
            } catch (Exception ignored) {}
        }
        return Map.of();
    }

    private LinkedHashSet<VisibilityRuleKey> extractMenuVisibilityRules(Map<String, Object> payload) {
        LinkedHashSet<VisibilityRuleKey> rules = new LinkedHashSet<>();
        if (payload == null) {
            return rules;
        }
        Object raw = payload.get("visibilityRules");
        if (!(raw instanceof Collection<?> collection)) {
            return rules;
        }
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String normalizedRole = normalizeRoleCode(map.get("role"));
            if (!StringUtils.hasText(normalizedRole)) {
                continue;
            }
            String permission = map.get("permission") == null ? null : map.get("permission").toString().trim();
            if (StringUtils.hasText(permission)) {
                permission = permission;
            } else {
                permission = null;
            }
            String dataLevel = normalizeMenuLevel(map.get("dataLevel"), true);
            rules.add(new VisibilityRuleKey(stripRolePrefix(normalizedRole), permission, dataLevel));
        }
        return rules;
    }

    private Map<String, Object> toRuleDetail(VisibilityRuleKey key) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", displayRoleCode(key.role()));
        if (StringUtils.hasText(key.permission())) {
            map.put("permission", key.permission());
        }
        if (StringUtils.hasText(key.dataLevel())) {
            map.put("dataLevel", key.dataLevel());
            map.put("dataLevelLabel", labelForMenuLevel(key.dataLevel(), key.dataLevel()));
        }
        return map;
    }

    private String formatRuleLabel(VisibilityRuleKey key) {
        StringBuilder sb = new StringBuilder(displayRoleCode(key.role()));
        List<String> attrs = new ArrayList<>();
        if (StringUtils.hasText(key.permission())) {
            attrs.add("权限: " + key.permission());
        }
        if (StringUtils.hasText(key.dataLevel())) {
            attrs.add("密级: " + labelForMenuLevel(key.dataLevel(), key.dataLevel()));
        }
        if (!attrs.isEmpty()) {
            sb.append("【").append(String.join("，", attrs)).append("】");
        }
        return sb.toString();
    }

    private String formatRoleList(List<String> roles) {
        return roles.stream().filter(StringUtils::hasText).collect(Collectors.joining("、"));
    }

    private String displayRoleCode(String role) {
        if (!StringUtils.hasText(role)) {
            return role;
        }
        String canonical = stripRolePrefix(role);
        if (!StringUtils.hasText(canonical)) {
            canonical = role.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        }
        return "ROLE_" + canonical;
    }

    private String normalizeMenuLevel(Object value, boolean applyAlias) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT).replace('-', '_');
        if (applyAlias) {
            normalized = MENU_DATA_LEVEL_ALIAS.getOrDefault(normalized, normalized);
        }
        return normalized;
    }

    private String labelForMenuLevel(String level, String fallback) {
        if (!StringUtils.hasText(level)) {
            return fallback;
        }
        return MENU_DATA_LEVEL_LABELS.getOrDefault(level, fallback != null ? fallback : level);
    }

    private String labelForSecurityLevel(String level, String fallback) {
        if (!StringUtils.hasText(level)) {
            return fallback;
        }
        return MENU_SECURITY_LEVEL_LABELS.getOrDefault(level, fallback != null ? fallback : level);
    }

    private static final class MenuChangeSummaryResult {

        private final List<Map<String, Object>> menuChanges = new ArrayList<>();
        private final List<Map<String, String>> summaryLines = new ArrayList<>();

        List<Map<String, Object>> menuChanges() {
            return menuChanges;
        }

        List<Map<String, String>> summaryLines() {
            return summaryLines;
        }
    }

    private record VisibilityRuleKey(String role, String permission, String dataLevel) {}

    static String buildChangeActionCode(ChangeRequest cr) {
        if (cr == null) {
            return null;
        }
        String resourceType = normalizeActionToken(cr.getResourceType());
        String action = normalizeActionToken(cr.getAction());
        if (!StringUtils.hasText(resourceType)) {
            resourceType = "GENERAL";
        }
        if (!StringUtils.hasText(action)) {
            action = "UPDATE";
        }
        return "ADMIN_" + resourceType + "_" + action;
    }

    private static String normalizeActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        String normalized = upper.replaceAll("[^A-Z0-9]+", "_");
        return normalized.replaceAll("_+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
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
            Object rawItems = payload.get("updates");
            java.util.Map<Long, MenuVisibilityBatch> batchByMenu = new java.util.LinkedHashMap<>();
            if (rawItems instanceof java.util.Collection<?> col) {
                for (Object it : col) {
                    if (!(it instanceof java.util.Map<?, ?> updateMap)) {
                        continue;
                    }
                    Object idObj = updateMap.get("id");
                    if (idObj == null) {
                        continue;
                    }
                    Long menuId;
                    try {
                        menuId = Long.valueOf(idObj.toString());
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    PortalMenu target = portalMenuRepo.findById(menuId).orElse(null);
                    if (target == null) {
                        continue;
                    }
                    MenuVisibilityBatch batch = batchByMenu.computeIfAbsent(menuId, key -> new MenuVisibilityBatch(target));
                    batch.merge(updateMap);
                }
            }
            java.util.Set<Long> touchedMenuIds = new java.util.LinkedHashSet<>();
            for (MenuVisibilityBatch batch : batchByMenu.values()) {
                java.util.Map<String, Object> updatePayload = batch.toPayload();
                if (updatePayload.isEmpty()) {
                    continue;
                }
                java.util.List<PortalMenuVisibility> updatedVisibilities = buildVisibilityEntities(updatePayload, batch.menu());
                portalMenuService.replaceVisibilities(batch.menu(), updatedVisibilities);
                touchedMenuIds.add(batch.menu().getId());
            }
            if (!touchedMenuIds.isEmpty()) {
                try {
                    notifyClient.trySend(
                        "portal_menu_updated",
                        java.util.Map.of(
                            "action",
                            "binding-update",
                            "ids",
                            touchedMenuIds.stream().map(String::valueOf).toList()
                        )
                    );
                } catch (Exception ignored) {}
            }
        } else if ("DISABLE".equalsIgnoreCase(action) || "DELETE".equalsIgnoreCase(action)) {
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
        Boolean isRoot = parseBoolean(payload.get("isRoot"));
        if ("CREATE".equalsIgnoreCase(action)) {
            String name = Objects.toString(payload.get("name"), null);
            Long parentId = payload.get("parentId") == null ? null : Long.valueOf(payload.get("parentId").toString());
            String description = Objects.toString(payload.get("description"), null);
            OrganizationNode created = orgService.create(name, parentId, description, Boolean.TRUE.equals(isRoot));
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
                    orgService.update(id, nextName, nextDescription, nextParentId, isRoot);
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
                String roleIdForAudit = locateCustomRole(normalizedName).map(AdminCustomRole::getId).map(String::valueOf).orElse(normalizedName);
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
                String roleIdForDelete = locateCustomRole(normalizedName).map(AdminCustomRole::getId).map(String::valueOf).orElse(normalizedName);
            } else {
                throw new IllegalStateException("未支持的角色操作: " + action);
            }
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            String failureCode = "DELETE".equalsIgnoreCase(auditAction) ? "ADMIN_ROLE_DELETE" : "ADMIN_ROLE_UPDATE";
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
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
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
            } else {
                throw new IllegalStateException("未支持的角色指派操作: " + action);
            }
            cr.setStatus("APPLIED");
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
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
                Object titleKey = meta.get("titleKey");
                if (titleKey instanceof String s && !s.isBlank()) {
                    String resolved = portalMenuService.resolveTitleByKey(s).orElse(null);
                    if (StringUtils.hasText(resolved)) {
                        return resolved;
                    }
                    return s;
                }
            } catch (Exception ignored) {
                // fallback below
            }
        }
        String code = menu.getName();
        if (StringUtils.hasText(code)) {
            String resolved = portalMenuService.resolveTitleByKey(code).orElse(null);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }
        return code;
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

    // Redaction: hide sensitive Keycloak IDs in all change request views
    private static final String SENSITIVE_KEYCLOAK_ID = "7f3868a1-9c8c-4122-b7e4-7f921a40c019";

    private static String redactSensitiveText(String raw) {
        if (raw == null) return null;
        try {
            return raw.replace(SENSITIVE_KEYCLOAK_ID, "");
        } catch (Exception ignored) {
            return raw;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object redactSensitiveObject(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            return redactSensitiveText(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if ("keycloakId".equalsIgnoreCase(k)) {
                    // drop entirely: do not expose keycloakId
                    continue;
                } else {
                    out.put(k, redactSensitiveObject(v));
                }
            }
            return out;
        }
        if (value instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            for (Object v : it) out.add(redactSensitiveObject(v));
            return out;
        }
        return value;
    }

    private static Map<String, Object> toChangeVM(ChangeRequest cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cr.getId());
        String ref = changeRequestRef(cr);
        if (ref != null) {
            m.put("changeRequestRef", ref);
        }
        m.put("resourceType", cr.getResourceType());
        m.put("resourceId", cr.getResourceId());
        m.put("action", cr.getAction());
        // Redact sensitive IDs within raw JSON strings
        m.put("payloadJson", redactSensitiveText(cr.getPayloadJson()));
        m.put("diffJson", redactSensitiveText(cr.getDiffJson()));
        m.put("status", cr.getStatus());
        m.put("requestedBy", cr.getRequestedBy());
        m.put("requestedAt", cr.getRequestedAt() != null ? cr.getRequestedAt().toString() : null);
        m.put("decidedBy", cr.getDecidedBy());
        m.put("decidedAt", cr.getDecidedAt() != null ? cr.getDecidedAt().toString() : null);
        m.put("reason", cr.getReason());
        m.put("category", cr.getCategory());
        m.put("originalValue", redactSensitiveObject(extractValue(cr, "before")));
        m.put("updatedValue", redactSensitiveObject(extractValue(cr, "after")));
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
        if (type.startsWith("MENU_") || "MENU_MANAGEMENT".equalsIgnoreCase(type)) {
            return "MENU_MANAGEMENT";
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
        List<PortalMenuVisibility> visibilities = menu.getVisibilities();
        List<Map<String, Object>> visibilityRules = (visibilities == null ? java.util.List.<PortalMenuVisibility>of() : visibilities)
            .stream()
            .map(this::toVisibilityRule)
            .toList();
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

    private boolean isAuditSuppressed(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        try {
            String header = request.getHeader("X-Audit-Silent");
            if (header != null && header.trim().equalsIgnoreCase("true")) {
                return true;
            }
            String param = request.getParameter("auditSilent");
            if (param != null && param.trim().equalsIgnoreCase("true")) {
                return true;
            }
        } catch (Exception ex) {
            log.debug("Audit suppression detection failed: {}", ex.getMessage());
        }
        return false;
    }

    private Map<String, Object> toRoleAssignmentVM(AdminRoleAssignment a, Map<String, String> roleLabels) {
        Map<String, Object> m = new LinkedHashMap<>();
        String rawRole = a.getRole();
        String canonical = stripRolePrefix(rawRole);
        String normalized = canonical == null ? null : "ROLE_" + canonical;
        String displayLabel = firstNonBlank(
            a.getDisplayName(),
            normalized != null ? roleLabels.get(normalized) : null,
            canonical != null ? roleLabels.get(canonical) : null,
            canonical,
            rawRole
        );
        m.put("id", a.getId());
        m.put("role", rawRole);
        m.put("username", a.getUsername());
        m.put("displayName", displayLabel);
        m.put("roleDisplayName", displayLabel);
        m.put("userSecurityLevel", a.getUserSecurityLevel());
        m.put("scopeOrgId", a.getScopeOrgId());
        m.put("scopeOrgName", resolveOrgName(a.getScopeOrgId()));
        m.put("datasetIds", a.getDatasetIdsCsv() == null || a.getDatasetIdsCsv().isBlank() ? List.of() : Arrays.stream(a.getDatasetIdsCsv().split(",")).map(Long::valueOf).toList());
        m.put("operations", a.getOperationsCsv() == null ? List.of() : Arrays.asList(a.getOperationsCsv().split(",")));
        m.put("grantedBy", a.getCreatedBy());
        m.put("grantedAt", a.getCreatedDate() != null ? a.getCreatedDate().toString() : null);
        return m;
    }

    private Map<String, String> buildRoleDisplayNameMap() {
        Map<String, String> labels = new LinkedHashMap<>();
        // Governance triad
        labels.put(AuthoritiesConstants.SYS_ADMIN, "系统管理员");
        labels.put(stripRolePrefix(AuthoritiesConstants.SYS_ADMIN), "系统管理员");
        labels.put(AuthoritiesConstants.AUTH_ADMIN, "授权管理员");
        labels.put(stripRolePrefix(AuthoritiesConstants.AUTH_ADMIN), "授权管理员");
        labels.put(AuthoritiesConstants.AUDITOR_ADMIN, "安全审计员");
        labels.put(stripRolePrefix(AuthoritiesConstants.AUDITOR_ADMIN), "安全审计员");
        labels.put(AuthoritiesConstants.OP_ADMIN, "运维管理员");
        labels.put(stripRolePrefix(AuthoritiesConstants.OP_ADMIN), "运维管理员");

        // Built-in data roles
        for (Map.Entry<String, BuiltinRoleSpec> entry : BUILTIN_DATA_ROLES.entrySet()) {
            String canonical = entry.getKey();
            String label = entry.getValue().titleCn();
            labels.putIfAbsent(canonical, label);
            labels.putIfAbsent("ROLE_" + canonical, label);
        }

        // Custom roles from repository
        for (AdminCustomRole role : customRoleRepo.findAll()) {
            String canonical = stripRolePrefix(role.getName());
            if (StringUtils.hasText(canonical)) {
                String label = firstNonBlank(role.getDescription(), role.getName(), canonical);
                labels.putIfAbsent(canonical, label);
                labels.putIfAbsent("ROLE_" + canonical, label);
            }
        }

        // Realm roles from Keycloak (attributes may contain localized names)
        try {
            for (KeycloakRoleDTO dto : adminUserService.listRealmRoles()) {
                String canonical = stripRolePrefix(dto.getName());
                if (!StringUtils.hasText(canonical)) {
                    continue;
                }
                Map<String, String> attributes = dto.getAttributes();
                String label = firstNonBlank(
                    attributes != null ? attributes.get("displayName") : null,
                    attributes != null ? attributes.get("titleCn") : null,
                    attributes != null ? attributes.get("nameZh") : null,
                    dto.getDescription(),
                    canonical
                );
                labels.putIfAbsent(canonical, label);
                labels.putIfAbsent("ROLE_" + canonical, label);
            }
        } catch (Exception ignored) {
            // Fallback to existing labels when Keycloak lookup fails
        }

        return labels;
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
            case "DATA_CONFIDENTIAL" -> 3;
            default -> null;
        };
    }

    private static final class Jsons {
        static String toJson(Object obj) {
            try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); } catch (Exception e) { return null; }
        }
    }
}
