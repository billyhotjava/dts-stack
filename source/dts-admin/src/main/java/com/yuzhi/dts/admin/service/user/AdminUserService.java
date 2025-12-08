package com.yuzhi.dts.admin.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import com.yuzhi.dts.admin.domain.AdminApprovalRequest;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.approval.ApprovalStatus;
import com.yuzhi.dts.admin.service.auditv2.AdminAuditOperation;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationType;
import com.yuzhi.dts.admin.service.auditv2.ChangeSnapshotFormatter;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.repository.PersonProfileRepository;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import com.yuzhi.dts.common.net.IpAddressUtils;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.domain.PersonProfile;
import com.yuzhi.dts.admin.security.SecurityUtils;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminUserService {

    private static final Logger LOG = LoggerFactory.getLogger(AdminUserService.class);
    private static final Set<String> SUPPRESS_PENDING_APPROVAL_ACTIONS = Set.of(
        "ADMIN_USER_CREATE",
        "ADMIN_USER_UPDATE",
        "ADMIN_CUSTOM_ROLE_CREATE",
        "ADMIN_ROLE_CREATE"
    );

    private static final Set<String> SUPPORTED_SECURITY_LEVELS = Set.of("NONE_SECRET", "NON_SECRET", "GENERAL", "IMPORTANT", "CORE");
    private static final Set<String> FORBIDDEN_SECURITY_LEVELS = Set.of("NONE_SECRET", "NON_SECRET");
    private static final Set<String> KEYCLOAK_DEFAULT_REALM_ROLE_NAMES = Set.of("offline_access", "uma_authorization");
    private static final Map<String, String> SECURITY_LEVEL_ALIASES = Map.ofEntries(
        Map.entry("NONE_SECRET", "NON_SECRET"),
        Map.entry("NON_SECRET", "NON_SECRET"),
        Map.entry("NS", "NON_SECRET"),
        Map.entry("GENERAL", "GENERAL"),
        Map.entry("GN", "GENERAL"),
        Map.entry("GE", "GENERAL"),
        Map.entry("G", "GENERAL"),
        Map.entry("IMPORTANT", "IMPORTANT"),
        Map.entry("IM", "IMPORTANT"),
        Map.entry("I", "IMPORTANT"),
        Map.entry("CORE", "CORE"),
        Map.entry("CO", "CORE"),
        Map.entry("C", "CORE")
    );

    private final AdminKeycloakUserRepository userRepository;
    private final AdminApprovalRequestRepository approvalRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditV2Service auditV2Service;
    private final ChangeRequestService changeRequestService;
    private final ChangeRequestRepository changeRequestRepository;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final AdminRoleMemberRepository roleMemberRepo;
    private final OrganizationRepository organizationRepository;
    private final PersonProfileRepository personProfileRepository;
    private final ObjectMapper objectMapper;
    private final KeycloakAuthService keycloakAuthService;
    private final ChangeSnapshotFormatter changeSnapshotFormatter;
    private final String managementClientId;
    private final String managementClientSecret;
    private final String targetClientId;
    private final boolean useClientRoles;
    private final ThreadLocal<ApprovalAuditCollector> approvalAuditCollector = new ThreadLocal<>();
    private final ThreadLocal<Boolean> suppressAuditFailure = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";
    private static final String DEFAULT_INITIAL_PASSWORD = "sa";
    private static final Map<String, String> BUILTIN_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("sysadmin", "系统管理员"),
        Map.entry("authadmin", "授权管理员"),
        Map.entry("auditadmin", "审计管理员")
    );

    public AdminUserService(
        AdminKeycloakUserRepository userRepository,
        AdminApprovalRequestRepository approvalRepository,
        KeycloakAdminClient keycloakAdminClient,
        AuditV2Service auditV2Service,
        ChangeRequestService changeRequestService,
        ChangeRequestRepository changeRequestRepository,
        AdminRoleAssignmentRepository roleAssignRepo,
        AdminRoleMemberRepository roleMemberRepo,
        OrganizationRepository organizationRepository,
        PersonProfileRepository personProfileRepository,
        ChangeSnapshotFormatter changeSnapshotFormatter,
        ObjectMapper objectMapper,
        KeycloakAuthService keycloakAuthService,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret,
        @Value("${dts.keycloak.target-client-id:${DTS_KEYCLOAK_TARGET_CLIENT_ID:${KC_SYNC_TARGET_CLIENT_ID:dts-system}}}") String targetClientId,
        @Value("${dts.keycloak.use-client-roles:false}") boolean useClientRoles
    ) {
        this.userRepository = userRepository;
        this.approvalRepository = approvalRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditV2Service = auditV2Service;
        this.changeRequestService = changeRequestService;
        this.changeRequestRepository = changeRequestRepository;
        this.roleAssignRepo = roleAssignRepo;
        this.roleMemberRepo = roleMemberRepo;
        this.organizationRepository = organizationRepository;
        this.personProfileRepository = personProfileRepository;
        this.changeSnapshotFormatter = changeSnapshotFormatter;
        this.objectMapper = objectMapper;
        this.keycloakAuthService = keycloakAuthService;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret;
        this.targetClientId = targetClientId == null ? "dts-system" : targetClientId.trim();
        this.useClientRoles = useClientRoles;
    }

@Transactional(propagation = Propagation.REQUIRED)
    public Page<AdminKeycloakUser> listSnapshots(int page, int size, String keyword) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username"));
        Page<AdminKeycloakUser> result;
        LOG.debug("listSnapshots start page={} size={} keyword='{}'", safePage, safeSize, StringUtils.trimToEmpty(keyword));
        if (StringUtils.isNotBlank(keyword)) {
            result = userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            result = userRepository.findAll(pageable);
        }
        if (result.getTotalElements() == 0) {
            LOG.info("user snapshots empty, refreshing from keycloak then profiles");
            refreshSnapshotsFromKeycloak();
            if (result.getTotalElements() == 0) {
                refreshSnapshotsFromProfiles();
            }
            if (StringUtils.isNotBlank(keyword)) {
                result = userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
            } else {
                result = userRepository.findAll(pageable);
            }
            LOG.info("user snapshots after refresh total={}", result.getTotalElements());
        } else if (page == 0 && result.getNumberOfElements() < safeSize) {
            // 数据量明显偏少时尝试补齐（兼容同步后快照缺失的场景）
            LOG.info("user snapshots count={} (<pageSize={}), refreshing profiles+keycloak", result.getNumberOfElements(), safeSize);
            refreshSnapshotsFromProfiles();
            refreshSnapshotsFromKeycloak();
            if (StringUtils.isNotBlank(keyword)) {
                result = userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
            } else {
                result = userRepository.findAll(pageable);
            }
            LOG.info("user snapshots after top-up total={}", result.getTotalElements());
        }
        LOG.debug("listSnapshots end totalElements={} pageElements={}", result.getTotalElements(), result.getNumberOfElements());
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<AdminKeycloakUser> findSnapshotByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshSnapshotsFromKeycloak() {
        String token = resolveManagementToken();
        if (!StringUtils.isNotBlank(token)) {
            LOG.warn("skip snapshot refresh: management token unavailable");
            return;
        }
        try {
            List<KeycloakUserDTO> users = keycloakAdminClient.listUsers(0, 500, token);
            int saved = 0;
            for (KeycloakUserDTO dto : users) {
                if (dto == null || !StringUtils.isNotBlank(dto.getId()) || !StringUtils.isNotBlank(dto.getUsername())) {
                    continue;
                }
                AdminKeycloakUser snapshot = userRepository
                    .findByKeycloakId(dto.getId())
                    .orElseGet(() -> userRepository.findByUsernameIgnoreCase(dto.getUsername()).orElseGet(AdminKeycloakUser::new));
                snapshot.setKeycloakId(dto.getId());
                snapshot.setUsername(dto.getUsername());
                if (StringUtils.isNotBlank(dto.getFullName())) {
                    snapshot.setFullName(dto.getFullName());
                }
                String secLevel = normalizeSecurityLevel(extractSingle(dto, "person_security_level"));
                snapshot.setPersonSecurityLevel(StringUtils.defaultIfBlank(secLevel, "GENERAL"));
                snapshot.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
                List<String> groupPaths = normalizeGroupPathList(dto.getGroups());
                if (groupPaths.isEmpty()) {
                    groupPaths = resolveGroupPathsFromProfiles(dto.getUsername());
                }
                if (!groupPaths.isEmpty()) {
                    snapshot.setGroupPaths(mergeGroupPaths(snapshot.getGroupPaths(), groupPaths));
                }
                snapshot.setLastSyncAt(Instant.now());
                userRepository.save(snapshot);
                saved++;
            }
            LOG.info("refreshed {} user snapshots from keycloak", saved);
        } catch (Exception ex) {
            LOG.warn("refresh snapshots from keycloak failed: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshSnapshotsFromProfiles() {
        try {
            var page = personProfileRepository.findAll(PageRequest.of(0, 1000));
            if (page.isEmpty()) {
                return;
            }
            int saved = 0;
            for (var profile : page.getContent()) {
                String username = firstNonBlank(profile.getAccount(), profile.getPersonCode(), profile.getExternalId());
                if (!StringUtils.isNotBlank(username)) {
                    continue;
                }
                AdminKeycloakUser snapshot = userRepository
                    .findByUsernameIgnoreCase(username)
                    .orElseGet(() -> {
                        AdminKeycloakUser u = new AdminKeycloakUser();
                        u.setUsername(username);
                        return u;
                    });
                Object kcIdAttr = profile.getAttributes() == null ? null : profile.getAttributes().get("keycloakId");
                if (StringUtils.isBlank(snapshot.getKeycloakId()) && kcIdAttr != null) {
                    snapshot.setKeycloakId(String.valueOf(kcIdAttr));
                }
                if (StringUtils.isBlank(snapshot.getKeycloakId())) {
                    String token = resolveManagementToken();
                    if (StringUtils.isNotBlank(token)) {
                        try {
                            keycloakAdminClient.findByUsername(username, token).ifPresent(dto -> snapshot.setKeycloakId(dto.getId()));
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (StringUtils.isNotBlank(profile.getFullName())) {
                    snapshot.setFullName(profile.getFullName());
                }
                snapshot.setPersonSecurityLevel(
                    StringUtils.defaultIfBlank(
                        normalizeSecurityLevel(
                            extractSingle(profile.getAttributes(), "person_security_level", "securityLevel", "person_level")
                        ),
                        DEFAULT_PERSON_LEVEL
                    )
                );
                snapshot.setEnabled(true);
                List<String> resolvedPaths = resolveGroupPathsFromProfile(profile);
                if (!resolvedPaths.isEmpty()) {
                    snapshot.setGroupPaths(mergeGroupPaths(snapshot.getGroupPaths(), resolvedPaths));
                }
                snapshot.setLastSyncAt(Instant.now());
                if (StringUtils.isNotBlank(snapshot.getKeycloakId())) {
                    userRepository.save(snapshot);
                    saved++;
                } else {
                    LOG.debug("skip snapshot without kcId (username={} dept={})", snapshot.getUsername(), profile.getDeptCode());
                }
            }
            LOG.info("refreshed {} user snapshots from person profiles", saved);
        } catch (Exception ex) {
            LOG.warn("refresh snapshots from profiles failed: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> resolveDisplayNames(Collection<String> usernames) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : usernames) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return result;
        }
        Set<String> lowerCase = normalized
            .stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> cached = new HashMap<>();
        if (!lowerCase.isEmpty()) {
            userRepository
                .findByUsernameInIgnoreCase(lowerCase)
                .forEach(entity -> {
                    String display = StringUtils.firstNonBlank(StringUtils.trimToNull(entity.getFullName()), entity.getUsername());
                    if (StringUtils.isNotBlank(display)) {
                        cached.put(entity.getUsername().toLowerCase(Locale.ROOT), display.trim());
                    }
                });
        }
        String managementToken = null;
        boolean tokenResolved = false;
        for (String username : normalized) {
            String lower = username.toLowerCase(Locale.ROOT);
            String display = cached.get(lower);
            if (StringUtils.isBlank(display)) {
                if (!tokenResolved) {
                    try {
                        managementToken = resolveManagementToken();
                    } catch (Exception ex) {
                        LOG.warn("Failed to obtain Keycloak admin token for display-name lookup: {}", ex.getMessage());
                        managementToken = null;
                    } finally {
                        tokenResolved = true;
                    }
                }
                if (StringUtils.isBlank(display) && StringUtils.isNotBlank(managementToken)) {
                    try {
                        display =
                            keycloakAdminClient
                                .findByUsername(username, managementToken)
                                .map(this::resolveFullName)
                                .map(StringUtils::trimToNull)
                                .orElse(null);
                    } catch (Exception ex) {
                        LOG.warn("Failed to resolve display name for {} via Keycloak: {}", username, ex.getMessage());
                    }
                }
            }
            if (StringUtils.isBlank(display)) {
                display = resolveBuiltinDisplayName(lower).orElse(username);
            } else if (display.equalsIgnoreCase(username)) {
                display = resolveBuiltinDisplayName(lower).orElse(display);
            }
            result.put(username, display);
            if (!result.containsKey(lower)) {
                result.put(lower, display);
            }
        }
        return result;
    }

    public RoleMemberDeltaResult applyRoleMemberDelta(List<String> roleNameCandidates, Collection<String> addUsernames, Collection<String> removeUsernames) {
        LinkedHashSet<String> additions = sanitizeUsernameSet(addUsernames);
        LinkedHashSet<String> removals = sanitizeUsernameSet(removeUsernames);
        removals.removeAll(additions);
        if (additions.isEmpty() && removals.isEmpty()) {
            return RoleMemberDeltaResult.empty();
        }
        if (roleNameCandidates == null || roleNameCandidates.isEmpty()) {
            throw new IllegalArgumentException("缺少角色名称信息");
        }
        String token = resolveManagementToken();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> normalizedCandidates = roleNameCandidates
            .stream()
            .filter(name -> name != null && !name.trim().isEmpty())
            .map(String::trim)
            .toList();
        if (normalizedCandidates.isEmpty()) {
            throw new IllegalArgumentException("缺少有效的角色名称信息");
        }
        String canonicalRole = normalizeRole(normalizedCandidates.get(0));

        for (String username : additions) {
            try {
                AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
                String kcId = snapshot.getKeycloakId();
                if (StringUtils.isBlank(kcId)) {
                    throw new IllegalStateException("未找到用户的 Keycloak 标识");
                }
                Exception lastFailure = null;
                boolean success = false;
                for (String candidate : normalizedCandidates) {
                    try {
                        keycloakAdminClient.addRealmRolesToUser(kcId, List.of(candidate), token);
                        success = true;
                        break;
                    } catch (Exception ex) {
                        lastFailure = ex;
                    }
                }
                if (!success && lastFailure != null) {
                    throw lastFailure;
                }
                refreshSnapshotFromKeycloak(username);
                added.add(username);
            } catch (Exception ex) {
                errors.put(username, ex.getMessage());
            }
        }

        for (String username : removals) {
            try {
                AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
                String kcId = snapshot.getKeycloakId();
                if (StringUtils.isBlank(kcId)) {
                    throw new IllegalStateException("未找到用户的 Keycloak 标识");
                }
                Exception lastFailure = null;
                boolean success = false;
                for (String candidate : normalizedCandidates) {
                    try {
                        keycloakAdminClient.removeRealmRolesFromUser(kcId, List.of(candidate), token);
                        success = true;
                        break;
                    } catch (Exception ex) {
                        lastFailure = ex;
                    }
                }
                if (!success && lastFailure != null) {
                    throw lastFailure;
                }
                refreshSnapshotFromKeycloak(username);
                removed.add(username);
                if (StringUtils.isNotBlank(canonicalRole)) {
                    roleAssignRepo
                        .findByUsernameIgnoreCaseAndRoleIgnoreCase(username, canonicalRole)
                        .forEach(roleAssignRepo::delete);
                }
            } catch (Exception ex) {
                errors.put(username, ex.getMessage());
            }
        }

        return new RoleMemberDeltaResult(added, removed, errors);
    }

    private Optional<String> resolveBuiltinDisplayName(String normalizedUsername) {
        if (StringUtils.isBlank(normalizedUsername)) {
            return Optional.empty();
        }
        String key = normalizedUsername.toLowerCase(Locale.ROOT);
        String display = BUILTIN_DISPLAY_NAMES.get(key);
        if (StringUtils.isBlank(display)) {
            return Optional.empty();
        }
        return Optional.of(display);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitCreate(UserOperationRequest request, String requester, String ip) {
        validateOperation(request, true);
        String username = request.getUsername().trim();
        request.setUsername(username);
        userRepository
            .findByUsernameIgnoreCase(username)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("用户已存在: " + username);
            });
        try {
            String managementToken = resolveManagementToken();
            keycloakAdminClient
                .findByUsername(username, managementToken)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Keycloak 中已存在同名用户: " + username);
                });
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warn("Failed to check existing Keycloak user {} before submission: {}", username, ex.getMessage());
        }
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, request.getReason(), "USER_CREATE");
        Map<String, Object> payload = buildCreatePayload(request);
        ChangeRequest changeRequest = createChangeRequest(
            "USER",
            "CREATE",
            username,
            payload,
            null,
            request.getReason()
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_CREATE_REQUEST", username, ip, request, changeRequest);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitUpdate(String username, UserOperationRequest request, String requester, String ip) {
        validateOperation(request, false);
        AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
        if (!hasUserModifications(request, snapshot)) {
            throw new IllegalArgumentException("未检测到用户信息变更，无需提交审批");
        }
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, request.getReason(), "USER_UPDATE");
        Map<String, Object> payload = buildUpdatePayload(request, snapshot);
        ChangeRequest changeRequest = createChangeRequest(
            "USER",
            "UPDATE",
            username,
            payload,
            snapshotPayload(snapshot),
            request.getReason()
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_UPDATE_REQUEST", username, ip, request, changeRequest);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitDelete(String username, String reason, String requester, String ip) {
        throw new UnsupportedOperationException("用户删除功能已禁用，请改用停用操作");
    }

    public ApprovalDTOs.ApprovalRequestDetail submitGrantRoles(String username, List<String> roles, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("请选择要分配的角色");
        }
        List<String> sanitizedRoles = sanitizeRealmRoleList(roles);
        if (sanitizedRoles.isEmpty()) {
            throw new IllegalArgumentException("请选择要分配的角色");
        }
        AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
        List<String> currentRoles = sanitizeRealmRoleList(snapshot.getRealmRoles());
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "GRANT_ROLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "grantRoles");
        payload.put("actionDisplay", actionDisplay("grantRoles"));
        payload.put("username", username);
        payload.put("roles", sanitizedRoles);
        payload.put("currentRoles", currentRoles);
        payload.put("addedRoles", sanitizedRoles);
        payload.put("resultRoles", mergeRoles(currentRoles, sanitizedRoles, true));
        // Include keycloakId to avoid KC user search that may trigger FGAP NPEs
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        sanitizeDefaultRealmRolesInMap(payload);
        ChangeRequest changeRequest = createChangeRequest(
            "ROLE",
            "GRANT_ROLE",
            username,
            payload,
            Map.of("roles", currentRoles),
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_GRANT_ROLE_REQUEST", username, ip, Map.of("roles", roles), changeRequest);
        return toDetailDto(approval);
    }

    // Overload for KC REST: carry keycloakId from path param to avoid FGAP user search
    public ApprovalDTOs.ApprovalRequestDetail submitGrantRoles(String username, List<String> roles, String keycloakId, String requester, String ip) {
        ApprovalDTOs.ApprovalRequestDetail result = submitGrantRoles(username, roles, requester, ip);
        // Patch payload to include explicit keycloakId if snapshot didn’t have it yet
        try {
            approvalRepository.findWithItemsById(result.id).ifPresent(approval -> {
                for (AdminApprovalItem item : approval.getItems()) {
                    try {
                        Map<String, Object> payload = readPayload(item.getPayloadJson());
                        if (payload != null && username.equals(payload.get("username"))) {
                            if (!payload.containsKey("keycloakId") && keycloakId != null && !keycloakId.isBlank()) {
                                payload.put("keycloakId", keycloakId);
                                item.setPayloadJson(writeJson(payload));
                            }
                        }
                    } catch (Exception ignored) {}
                }
                approvalRepository.save(approval);
            });
        } catch (Exception ignored) {}
        return result;
    }

    public ApprovalDTOs.ApprovalRequestDetail submitRevokeRoles(String username, List<String> roles, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("请选择要移除的角色");
        }
        List<String> sanitizedRoles = sanitizeRealmRoleList(roles);
        if (sanitizedRoles.isEmpty()) {
            throw new IllegalArgumentException("请选择要移除的角色");
        }
        AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
        List<String> currentRoles = sanitizeRealmRoleList(snapshot.getRealmRoles());
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "REVOKE_ROLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "revokeRoles");
        payload.put("actionDisplay", actionDisplay("revokeRoles"));
        payload.put("username", username);
        payload.put("roles", sanitizedRoles);
        payload.put("currentRoles", currentRoles);
        payload.put("removedRoles", sanitizedRoles);
        payload.put("resultRoles", mergeRoles(currentRoles, sanitizedRoles, false));
        // Include keycloakId to avoid KC user search that may trigger FGAP NPEs
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        sanitizeDefaultRealmRolesInMap(payload);
        ChangeRequest changeRequest = createChangeRequest(
            "ROLE",
            "REVOKE_ROLE",
            username,
            payload,
            Map.of("roles", currentRoles),
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_REVOKE_ROLE_REQUEST", username, ip, Map.of("roles", roles), changeRequest);
        return toDetailDto(approval);
    }

    // Overload for KC REST: carry keycloakId from path param to avoid FGAP user search
    public ApprovalDTOs.ApprovalRequestDetail submitRevokeRoles(String username, List<String> roles, String keycloakId, String requester, String ip) {
        ApprovalDTOs.ApprovalRequestDetail result = submitRevokeRoles(username, roles, requester, ip);
        try {
            approvalRepository.findWithItemsById(result.id).ifPresent(approval -> {
                for (AdminApprovalItem item : approval.getItems()) {
                    try {
                        Map<String, Object> payload = readPayload(item.getPayloadJson());
                        if (payload != null && username.equals(payload.get("username"))) {
                            if (!payload.containsKey("keycloakId") && keycloakId != null && !keycloakId.isBlank()) {
                                payload.put("keycloakId", keycloakId);
                                item.setPayloadJson(writeJson(payload));
                            }
                        }
                    } catch (Exception ignored) {}
                }
                approvalRepository.save(approval);
            });
        } catch (Exception ignored) {}
        return result;
    }

    public ApprovalDTOs.ApprovalRequestDetail submitSetEnabled(String username, boolean enabled, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "SET_ENABLED");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "setEnabled");
        payload.put("username", username);
        payload.put("enabled", enabled);
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        payload.put("currentEnabled", snapshot.isEnabled());
        ChangeRequest changeRequest = createChangeRequest(
            "USER",
            enabled ? "ENABLE" : "DISABLE",
            username,
            payload,
            Map.of("enabled", snapshot.isEnabled()),
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_ENABLE_REQUEST", username, ip, Map.of("enabled", enabled), changeRequest);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitSetPersonLevel(
        String username,
        String personLevel,
        String requester,
        String ip,
        String reason
    ) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        AdminKeycloakUser snapshot = ensureFreshSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, reason, "SET_PERSON_LEVEL");
        String normalizedPersonLevel = normalizeSecurityLevel(personLevel);
        ensureAllowedSecurityLevel(normalizedPersonLevel, "人员密级不允许为非密");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "setPersonLevel");
        payload.put("username", username);
        payload.put("personSecurityLevel", normalizedPersonLevel);
        payload.put("currentPersonSecurityLevel", snapshot.getPersonSecurityLevel());
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        if (snapshot.getPersonSecurityLevel() != null) {
            before.put("personSecurityLevel", snapshot.getPersonSecurityLevel());
        }
        ChangeRequest changeRequest = createChangeRequest("USER", "SET_PERSON_LEVEL", username, payload, before, reason);
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_SET_PERSON_LEVEL_REQUEST", username, ip, Map.of("personLevel", personLevel), changeRequest);
        return toDetailDto(approval);
    }

    private void ensureAllowedSecurityLevel(String normalizedLevel, String message) {
        if (normalizedLevel != null && FORBIDDEN_SECURITY_LEVELS.contains(normalizedLevel)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateOperation(UserOperationRequest request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (creating && StringUtils.isBlank(request.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (StringUtils.isBlank(request.getPersonSecurityLevel())) {
            throw new IllegalArgumentException("用户密级不能为空");
        }
        String normalizedSecurity = normalizeSecurityLevel(request.getPersonSecurityLevel());
        if (!SUPPORTED_SECURITY_LEVELS.contains(normalizedSecurity)) {
            throw new IllegalArgumentException("不支持的用户密级: " + request.getPersonSecurityLevel());
        }
        ensureAllowedSecurityLevel(normalizedSecurity, "人员密级不允许为非密");
    }

    private AdminApprovalRequest buildApprovalSkeleton(String requester, String reason, String type) {
        AdminApprovalRequest approval = new AdminApprovalRequest();
        approval.setRequester(requester);
        approval.setType(type);
        approval.setReason(reason);
        approval.setStatus(ApprovalStatus.PENDING.name());
        approval.setRetryCount(0);
        return approval;
    }

    private AdminApprovalItem buildPayloadItem(String username, Map<String, Object> payload) {
        AdminApprovalItem item = new AdminApprovalItem();
        item.setSeqNumber(1);
        item.setTargetKind("USER");
        item.setTargetId(username);
        item.setPayloadJson(writeJson(payload));
        return item;
    }

    private Map<String, Object> buildCreatePayload(UserOperationRequest request) {
        Map<String, Object> payload = basePayload("create", request.getUsername(), request);
        payload.put("target", Map.of());
        return payload;
    }

    private Map<String, Object> buildUpdatePayload(UserOperationRequest request, AdminKeycloakUser snapshot) {
        Map<String, Object> payload = basePayload("update", snapshot.getUsername(), request);
        Map<String, Object> target = snapshotPayload(snapshot);
        target.remove("realmRoles");
        payload.remove("realmRoles");
        payload.put("target", target);
        return payload;
    }

    private Map<String, Object> buildDeletePayload(AdminKeycloakUser snapshot, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "delete");
        payload.put("actionDisplay", actionDisplay("delete"));
        payload.put("username", snapshot.getUsername());
        payload.put("reason", reason);
        payload.put("target", snapshotPayload(snapshot));
        return payload;
    }

    private Map<String, Object> basePayload(String action, String username, UserOperationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("actionDisplay", actionDisplay(action));
        payload.put("username", username);
        payload.put("fullName", normalizeStringValue(request.getFullName()));
        payload.put("email", normalizeStringValue(request.getEmail()));
        payload.put("phone", normalizeStringValue(request.getPhone()));
        String normalizedPersonLevel = normalizeSecurityLevel(request.getPersonSecurityLevel());
        ensureAllowedSecurityLevel(normalizedPersonLevel, "人员密级不允许为非密");
        payload.put("personSecurityLevel", normalizedPersonLevel);
        if (request.isRealmRolesSpecified() && "create".equals(action)) {
            payload.put("realmRoles", sanitizeRealmRoleList(request.getRealmRoles()));
        }
        if (request.isGroupPathsSpecified()) {
            payload.put("groupPaths", request.getGroupPaths());
        }
        payload.put("enabled", request.getEnabled());
        payload.put("reason", request.getReason());
        payload.put("attributes", normalizeAttributesMap(request.getAttributes()));
        sanitizeDefaultRealmRolesInMap(payload);
        return payload;
    }

    private Map<String, Object> snapshotPayload(AdminKeycloakUser snapshot) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", snapshot.getUsername());
        payload.put("fullName", snapshot.getFullName());
        payload.put("email", snapshot.getEmail());
        payload.put("phone", snapshot.getPhone());
        payload.put("personSecurityLevel", snapshot.getPersonSecurityLevel());
        payload.put("realmRoles", sanitizeRealmRoleList(snapshot.getRealmRoles()));
        payload.put("groupPaths", snapshot.getGroupPaths());
        payload.put("enabled", snapshot.isEnabled());
        payload.put("attributes", Map.of());
        sanitizeDefaultRealmRolesInMap(payload);
        // Do not expose keycloakId to clients
        return payload;
    }

    private static String actionDisplay(String code) {
        if (code == null) return "";
        String c = code.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "create" -> "新增";
            case "update" -> "更新";
            case "delete" -> "删除";
            case "grantroles" -> "分配角色";
            case "revokeroles" -> "撤销角色";
            case "enable" -> "启用";
            case "disable" -> "禁用";
            case "resetpassword" -> "重置密码";
            default -> code;
        };
    }

    private static boolean isKeycloakDefaultRealmRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (KEYCLOAK_DEFAULT_REALM_ROLE_NAMES.contains(lower)) {
            return true;
        }
        return lower.startsWith("default-roles-");
    }

    private static List<String> sanitizeRealmRoleList(Collection<?> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> filtered = new ArrayList<>();
        for (Object item : roles) {
            if (item == null) {
                continue;
            }
            String role = item.toString();
            if (!isKeycloakDefaultRealmRole(role)) {
                filtered.add(role);
            }
        }
        return filtered;
    }

    private LinkedHashSet<String> sanitizeUsernameSet(Collection<String> usernames) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (usernames == null) {
            return set;
        }
        for (String username : usernames) {
            if (username == null) {
                continue;
            }
            String trimmed = username.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private boolean hasUserModifications(UserOperationRequest request, AdminKeycloakUser snapshot) {
        if (!Objects.equals(normalizeStringValue(request.getFullName()), normalizeStringValue(snapshot.getFullName()))) {
            return true;
        }
        if (!Objects.equals(normalizeStringValue(request.getEmail()), normalizeStringValue(snapshot.getEmail()))) {
            return true;
        }
        if (!Objects.equals(normalizeStringValue(request.getPhone()), normalizeStringValue(snapshot.getPhone()))) {
            return true;
        }
        String requestedLevel = normalizeSecurityLevel(request.getPersonSecurityLevel());
        String currentLevel = normalizeSecurityLevel(snapshot.getPersonSecurityLevel());
        if (!Objects.equals(requestedLevel, currentLevel)) {
            return true;
        }
        Boolean enabled = request.getEnabled();
        if (enabled != null && enabled.booleanValue() != snapshot.isEnabled()) {
            return true;
        }
        if (request.isRealmRolesSpecified()) {
            List<String> requestedRoles = normalizeRoleList(request.getRealmRoles());
            List<String> currentRoles = normalizeRoleList(snapshot.getRealmRoles());
            if (!requestedRoles.equals(currentRoles)) {
                return true;
            }
        }
        if (request.isGroupPathsSpecified()) {
            List<String> requestedGroups = normalizeGroupPathListForDiff(request.getGroupPaths());
            List<String> currentGroups = normalizeGroupPathListForDiff(snapshot.getGroupPaths());
            if (!requestedGroups.equals(currentGroups)) {
                return true;
            }
        }
        Map<String, List<String>> requestedAttributes = normalizeAttributesMap(request.getAttributes());
        return !requestedAttributes.isEmpty();
    }

    private String normalizeStringValue(String value) {
        return StringUtils.trimToNull(value);
    }

    private List<String> normalizeRoleList(Collection<?> roles) {
        List<String> sanitized = sanitizeRealmRoleList(roles);
        if (sanitized == null || sanitized.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String role : sanitized) {
            if (StringUtils.isBlank(role)) {
                continue;
            }
            set.add(role.trim().toUpperCase(Locale.ROOT));
        }
        List<String> result = new ArrayList<>(set);
        result.sort(String::compareTo);
        return result;
    }

    private Map<String, List<String>> normalizeAttributesMap(Map<String, List<String>> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isBlank(key)) {
                continue;
            }
            List<String> values = normalizeAttributeValues(entry.getValue());
            if (!values.isEmpty()) {
                normalized.put(key.trim(), values);
            }
        }
        return normalized.isEmpty() ? Map.of() : normalized;
    }

    private List<String> normalizeAttributeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            set.add(value.trim());
        }
        List<String> result = new ArrayList<>(set);
        result.sort(String::compareTo);
        return result;
    }

    private static void sanitizeDefaultRealmRolesInMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : new ArrayList<>(map.entrySet())) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedCopy = new LinkedHashMap<>();
                nested.forEach((k, v) -> nestedCopy.put(String.valueOf(k), v));
                sanitizeDefaultRealmRolesInMap(nestedCopy);
                map.put(key, nestedCopy);
                continue;
            }
            if (value instanceof Collection<?> collection) {
                if ("realmRoles".equalsIgnoreCase(key)) {
                    map.put(key, sanitizeRealmRoleList(collection));
                    continue;
                }
                List<Object> sanitizedList = new ArrayList<>();
                boolean modified = false;
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> nested = new LinkedHashMap<>();
                        itemMap.forEach((k, v) -> nested.put(String.valueOf(k), v));
                        sanitizeDefaultRealmRolesInMap(nested);
                        sanitizedList.add(nested);
                        modified = true;
                    } else {
                        sanitizedList.add(item);
                    }
                }
                if (modified) {
                    map.put(key, sanitizedList);
                }
                continue;
            }
            if ("realmRoles".equalsIgnoreCase(key) && value instanceof String str) {
                if (isKeycloakDefaultRealmRole(str)) {
                    map.remove(key);
                }
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化审批请求", e);
        }
    }

    private void recordAudit(String actor, String action, String target, String ip, Object detailSource) {
        recordAudit(actor, action, target, ip, detailSource, null);
    }

    private void recordAudit(
        String actor,
        String action,
        String target,
        String ip,
        Object detailSource,
        Object correlationRef
    ) {
        Map<String, Object> detail = new HashMap<>();
        if (detailSource instanceof Map<?, ?> mapSource) {
            Map<String, Object> payloadCopy = new LinkedHashMap<>();
            mapSource.forEach((k, v) -> payloadCopy.put(String.valueOf(k), v));
            sanitizeDefaultRealmRolesInMap(payloadCopy);
            detail.put("payload", payloadCopy);
        } else {
            detail.put("payload", detailSource);
        }
        detail.put("ip", ip);
        detail.put("timestamp", Instant.now().toString());
        SubjectInfo subject = resolveAuditSubject(target, detailSource);
        if (subject != null && StringUtils.isNotBlank(subject.id())) {
            detail.put("targetIds", List.of(subject.id()));
            if (StringUtils.isNotBlank(subject.label())) {
                detail.put("targetLabels", Map.of(subject.id(), subject.label()));
            }
        }
        String code = switch (action) {
            case "USER_CREATE_REQUEST" -> "ADMIN_USER_CREATE";
            case "USER_UPDATE_REQUEST", "USER_SET_PERSON_LEVEL_REQUEST" -> "ADMIN_USER_UPDATE";
            case "USER_GRANT_ROLE_REQUEST", "USER_REVOKE_ROLE_REQUEST" -> "ADMIN_USER_ASSIGN_ROLE";
            case "USER_ENABLE_REQUEST" -> "ADMIN_USER_ENABLE";
            case "USER_DISABLE_REQUEST" -> "ADMIN_USER_DISABLE";
            case "USER_RESET_PASSWORD_REQUEST" -> "ADMIN_USER_RESET_PASSWORD";
            default -> action;
        };
        String summary = buildApprovalRequestSummary(action, subject, detailSource);
        if (StringUtils.isNotBlank(summary)) {
            detail.put("summary", summary);
            detail.put("operationName", summary);
        }
        detail.put("operationType", AuditOperationType.REQUEST.getCode());
        detail.put("operationTypeText", AuditOperationType.REQUEST.getDisplayName());
        String changeRequestRef = changeRequestRefFrom(correlationRef);
        if (org.springframework.util.StringUtils.hasText(changeRequestRef)) {
            detail.put("changeRequestRef", changeRequestRef);
        }
        ChangeSnapshot snapshot = extractChangeSnapshot(detailSource, correlationRef);
        if (snapshot != null && (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty())) {
            detail.put("changeSnapshot", snapshot.toMap());
        }
        sanitizeDefaultRealmRolesInMap(detail);
        String effectiveTarget = org.springframework.util.StringUtils.hasText(changeRequestRef) ? changeRequestRef : target;
        ChangeRequest changeRequest = correlationRef instanceof ChangeRequest cr ? cr : null;
        SubjectInfo finalSubject = subject;
        recordUserApprovalRequestV2(actor, code, finalSubject, changeRequest, changeRequestRef, summary, detail, ip);
    }

    private ChangeSnapshot extractChangeSnapshot(Object detailSource, Object correlationRef) {
        ChangeSnapshot snapshot = tryExtractSnapshot(correlationRef);
        if (snapshot == null || (!snapshot.hasChanges() && snapshot.getBefore().isEmpty() && snapshot.getAfter().isEmpty())) {
            ChangeSnapshot fallback = tryExtractSnapshot(detailSource);
            if (fallback != null) {
                snapshot = fallback;
            }
        }
        return snapshot;
    }

    private ChangeSnapshot tryExtractSnapshot(Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof Optional<?> optional) {
            return tryExtractSnapshot(optional.orElse(null));
        }
        if (candidate instanceof ChangeRequest changeRequest) {
            ChangeSnapshot fromDiff = ChangeSnapshot.fromJson(changeRequest.getDiffJson(), objectMapper);
            if (fromDiff != null && (fromDiff.hasChanges() || !fromDiff.getBefore().isEmpty() || !fromDiff.getAfter().isEmpty())) {
                return fromDiff;
            }
            Map<String, Object> after = readJsonMap(changeRequest.getPayloadJson());
            if (!after.isEmpty()) {
                return ChangeSnapshot.of(Map.of(), after);
            }
            return null;
        }
        if (candidate instanceof Map<?, ?> map) {
            Map<String, Object> normalized = toStringKeyMap(map);
            sanitizeDefaultRealmRolesInMap(normalized);
            Object embedded = normalized.get("changeSnapshot");
            if (embedded instanceof Map<?, ?> embeddedMap) {
                return ChangeSnapshot.fromMap(toStringKeyMap(embeddedMap));
            }
            boolean hasExplicit = normalized.containsKey("before") || normalized.containsKey("after");
            if (hasExplicit) {
                Map<String, Object> before = toStringKeyMap(normalized.get("before"));
                Map<String, Object> after = toStringKeyMap(normalized.get("after"));
                return ChangeSnapshot.of(before, after);
            }
        }
        return null;
    }

    private Map<String, Object> toStringKeyMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            sanitizeDefaultRealmRolesInMap(result);
            return result;
        }
        try {
            return objectMapper.convertValue(value, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
            sanitizeDefaultRealmRolesInMap(map);
            return map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private SubjectInfo resolveAuditSubject(String target, Object detailSource) {
        String username = stringValue(target);
        String fullName = null;
        if (detailSource instanceof UserOperationRequest req) {
            if (StringUtils.isNotBlank(req.getUsername())) {
                username = req.getUsername().trim();
            }
            if (StringUtils.isNotBlank(req.getFullName())) {
                fullName = req.getFullName().trim();
            }
        } else if (detailSource instanceof Map<?, ?> map) {
            Object directUsername = map.get("username");
            if (directUsername != null && StringUtils.isNotBlank(stringValue(directUsername))) {
                username = stringValue(directUsername);
            }
            Object directFullName = map.get("fullName");
            if (directFullName != null) {
                fullName = stringValue(directFullName);
            }
        }
        if (StringUtils.isBlank(username) && StringUtils.isBlank(fullName)) {
            return null;
        }
        String label = buildDisplayLabel(username, fullName);
        return new SubjectInfo(username, label);
    }

    private String buildApprovalRequestSummary(String action, SubjectInfo subject, Object detailSource) {
        String label = subject != null ? StringUtils.defaultIfBlank(subject.label(), subject.id()) : null;
        switch (action) {
            case "USER_CREATE_REQUEST":
                return label == null ? null : "提交新增用户审批：" + label;
            case "USER_UPDATE_REQUEST":
                return label == null ? null : "提交修改用户审批：" + label;
            case "USER_GRANT_ROLE_REQUEST":
                return label == null ? null : "提交用户授权审批：" + label + describeRoles(detailSource);
            case "USER_REVOKE_ROLE_REQUEST":
                return label == null ? null : "提交撤销用户授权审批：" + label + describeRoles(detailSource);
            case "USER_ENABLE_REQUEST":
                return label == null ? null : "提交" + resolveEnableAction(detailSource) + "审批：" + label;
            case "USER_DISABLE_REQUEST":
                return label == null ? null : "提交禁用用户审批：" + label;
            case "USER_SET_PERSON_LEVEL_REQUEST":
                return label == null ? null : "提交调整用户密级审批：" + label + describePersonLevel(detailSource);
            case "USER_RESET_PASSWORD_REQUEST":
                return label == null ? null : "提交重置用户密码审批：" + label + describeResetPassword(detailSource);
            default:
                return label == null ? null : "提交用户操作审批：" + label;
        }
    }

    private String describeRoles(Object detailSource) {
        if (!(detailSource instanceof Map<?, ?> map)) {
            return "";
        }
        Object rolesValue = map.get("roles");
        if (!(rolesValue instanceof Collection<?> roles) || roles.isEmpty()) {
            return "";
        }
        String joined = roles
            .stream()
            .map(this::stringValue)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(","));
        return joined.isEmpty() ? "" : "，角色：" + joined;
    }

    private String resolveEnableAction(Object detailSource) {
        if (detailSource instanceof Map<?, ?> map) {
            Boolean enabled = booleanValue(map.get("enabled"));
            if (Boolean.TRUE.equals(enabled)) {
                return "启用用户";
            }
            if (Boolean.FALSE.equals(enabled)) {
                return "禁用用户";
            }
        }
        return "设置用户状态";
    }

    private String describePersonLevel(Object detailSource) {
        if (!(detailSource instanceof Map<?, ?> map)) {
            return "";
        }
        String level = stringValue(map.get("personLevel"));
        return StringUtils.isBlank(level) ? "" : "，目标密级：" + level;
    }

    private String describeResetPassword(Object detailSource) {
        if (!(detailSource instanceof Map<?, ?> map)) {
            return "";
        }
        Boolean temporary = booleanValue(map.get("temporary"));
        return Boolean.TRUE.equals(temporary) ? "（临时口令）" : "";
    }

    private String buildDisplayLabel(String username, String fullName) {
        String trimmedUsername = StringUtils.trimToNull(username);
        String trimmedFullName = StringUtils.trimToNull(fullName);
        if (trimmedUsername == null && trimmedFullName == null) {
            return null;
        }
        if (trimmedFullName == null || trimmedFullName.equals(trimmedUsername)) {
            return trimmedUsername != null ? trimmedUsername : trimmedFullName;
        }
        if (trimmedUsername == null) {
            return trimmedFullName;
        }
        return trimmedFullName + "（" + trimmedUsername + "）";
    }

    private void auditUserChange(String actor, String action, String target, String result, Object detail) {
        String changeRequestRef = extractChangeRequestRef(detail);
        String payloadUsername = extractPayloadUsername(detail);
        String payloadFullName = extractPayloadFullName(detail);
        String subjectLabel = buildDisplayLabel(payloadUsername, payloadFullName);
        String normalizedTarget = target == null ? "UNKNOWN" : target;
        // Prefer recording the local DB primary key for admin_keycloak_user
        // If target looks like a username (non-numeric), try resolve to PK id
        if (normalizedTarget != null) {
            try {
                // if it's a number already, keep as-is
                Long.parseLong(normalizedTarget);
            } catch (NumberFormatException ignore) {
                try {
                    String resolved = userRepository
                        .findByUsernameIgnoreCase(normalizedTarget)
                        .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::getId)
                        .map(String::valueOf)
                        .orElse(normalizedTarget);
                    normalizedTarget = resolved;
                } catch (Exception ignored) {
                    // fall through with original target
                }
            }
        }
        if (StringUtils.isBlank(normalizedTarget) || "UNKNOWN".equalsIgnoreCase(normalizedTarget)) {
            normalizedTarget = payloadUsername != null ? payloadUsername : normalizedTarget;
        } else if (payloadUsername != null && normalizedTarget.equalsIgnoreCase(actor)) {
            normalizedTarget = payloadUsername;
        }
        Map<String, Object> payload;
        if (detail instanceof Map<?, ?> map) {
            payload = new HashMap<>();
            map.forEach((k, v) -> payload.put(String.valueOf(k), v));
            if (StringUtils.isNotBlank(changeRequestRef) && !payload.containsKey("changeRequestRef")) {
                payload.put("changeRequestRef", changeRequestRef);
            }
            if (payloadUsername != null && !payload.containsKey("username")) {
                payload.put("username", payloadUsername);
            }
        } else {
            payload = new HashMap<>();
            payload.put("detail", detail);
        }
        Long primaryKey = resolveLocalUserId(normalizedTarget, payloadUsername);
        if (primaryKey != null) {
            payload.put("sourcePrimaryKey", primaryKey);
            payload.put("sourceTable", "admin_keycloak_user");
        }
        if (detail instanceof Map<?, ?>) {
            enrichExecutionPayloadWithLabel(payload, payloadUsername, payloadFullName, subjectLabel);
            if (StringUtils.isNotBlank(subjectLabel)) {
                Object summaryValue = payload.get("summary");
                if (summaryValue instanceof String summaryText) {
                    payload.put("summary", normalizeSummaryTarget(summaryText, subjectLabel));
                }
                Object operationNameValue = payload.get("operationName");
                if (operationNameValue instanceof String operationNameText) {
                    payload.put("operationName", normalizeSummaryTarget(operationNameText, subjectLabel));
                }
            }
        }
        String code = switch (action) {
            case "USER_CREATE_EXECUTE" -> "ADMIN_USER_CREATE";
            case "USER_UPDATE_EXECUTE", "USER_SET_PERSON_LEVEL_EXECUTE" -> "ADMIN_USER_UPDATE";
            case "USER_DELETE_EXECUTE" -> "ADMIN_USER_DELETE";
            case "USER_DISABLE_EXECUTE" -> "ADMIN_USER_DISABLE";
            case "USER_ENABLE_EXECUTE" -> "ADMIN_USER_ENABLE";
            case "USER_GRANT_ROLE_EXECUTE", "USER_REVOKE_ROLE_EXECUTE" -> "ADMIN_USER_ASSIGN_ROLE";
            case "USER_RESET_PASSWORD_EXECUTE" -> "ADMIN_USER_RESET_PASSWORD";
            default -> action;
        };
        AuditStage stage = "SUCCESS".equalsIgnoreCase(result) ? AuditStage.SUCCESS : AuditStage.FAIL;
        if (stage != AuditStage.SUCCESS && Boolean.TRUE.equals(suppressAuditFailure.get())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Suppressing intermediate failure audit for action={} target={} due to retry", action, normalizedTarget);
            }
            return;
        }
        ApprovalAuditCollector collector = approvalAuditCollector.get();
        if (collector != null && stage == AuditStage.SUCCESS) {
            collector.collect(code, stage, normalizedTarget, new LinkedHashMap<>(payload));
            if (StringUtils.isNotBlank(changeRequestRef)) {
                return;
            }
        }
        recordUserApprovalExecutionV2(actor, code, normalizedTarget, payloadUsername, subjectLabel, changeRequestRef, stage, payload);
    }

    private String extractChangeRequestRef(Object detail) {
        if (!(detail instanceof Map<?, ?> map)) {
            return null;
        }
        Object direct = map.get("changeRequestRef");
        if (direct == null) {
            Object payload = map.get("payload");
            if (payload instanceof Map<?, ?> payloadMap) {
                direct = payloadMap.get("changeRequestRef");
                if (direct == null) {
                    direct = payloadMap.get("changeRequestId");
                }
            }
        }
        if (direct == null) {
            direct = map.get("changeRequestId");
        }
        if (direct == null) {
            direct = map.get("approvalRequestId");
        }
        if (direct == null) {
            direct = map.get("requestId");
        }
        return changeRequestRefFrom(direct);
    }

    private record SubjectInfo(String id, String label) {}

    private static final class ApprovalAuditCollector {

        private final Map<String, LinkedHashSet<String>> targetIdsByTable = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> targetLabelsByTable = new LinkedHashMap<>();
        private final List<Map<String, Object>> steps = new ArrayList<>();
        private final LinkedHashSet<String> summaryLines = new LinkedHashSet<>();

        void collect(String operationCode, AuditStage stage, String targetId, Map<String, Object> payload) {
            AdminAuditOperation op = AdminAuditOperation.fromCode(operationCode).orElse(null);
            String table = op != null ? op.targetTable() : null;
            String opName = op != null ? op.defaultName() : operationCode;
            String effectiveId = determineEffectiveId(targetId, payload);
            String label = extractLabel(payload);
            if (stage == AuditStage.SUCCESS) {
                if (StringUtils.isNotBlank(table) && StringUtils.isNotBlank(effectiveId)) {
                    targetIdsByTable.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(effectiveId);
                    if (StringUtils.isNotBlank(label)) {
                        targetLabelsByTable.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(effectiveId, label);
                    }
                }
                summaryLines.add(buildSummaryLine(opName, effectiveId, label));
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("operationCode", operationCode);
            step.put("operationName", opName);
            step.put("result", stage == AuditStage.SUCCESS ? "SUCCESS" : "FAILED");
            if (StringUtils.isNotBlank(table)) {
                step.put("targetTable", table);
            }
            if (StringUtils.isNotBlank(effectiveId)) {
                step.put("targetId", effectiveId);
            }
            if (StringUtils.isNotBlank(label)) {
                step.put("targetLabel", label);
            }
            step.put("payload", sanitizePayload(payload));
            steps.add(step);
        }

        boolean hasData() {
            return !steps.isEmpty();
        }

        Optional<CollectorTarget> primaryTarget() {
            if (targetIdsByTable.size() == 1) {
                Map.Entry<String, LinkedHashSet<String>> entry = targetIdsByTable.entrySet().iterator().next();
                String table = entry.getKey();
                List<String> ids = List.copyOf(entry.getValue());
                Map<String, String> labels = targetLabelsByTable.containsKey(table)
                    ? Map.copyOf(targetLabelsByTable.get(table))
                    : Map.of();
                return Optional.of(new CollectorTarget(table, ids, labels));
            }
            return Optional.empty();
        }

        String summaryText() {
            if (summaryLines.isEmpty()) {
                return null;
            }
            return String.join("；", summaryLines);
        }

        String primaryOperationName() {
            if (summaryLines.isEmpty()) {
                return null;
            }
            return summaryLines.iterator().next();
        }

        Optional<String> primaryOperationCode() {
            if (steps.isEmpty()) {
                return Optional.empty();
            }
            Object code = steps.get(0).get("operationCode");
            return code != null ? Optional.of(code.toString()) : Optional.empty();
        }

        void appendDetails(Map<String, Object> detail) {
            if (!targetIdsByTable.isEmpty()) {
                Map<String, Object> targets = new LinkedHashMap<>();
                targetIdsByTable.forEach((table, ids) -> targets.put(table, List.copyOf(ids)));
                detail.put("approvalTargets", targets);
            }
            if (!steps.isEmpty()) {
                detail.put("steps", steps);
            }
        }

        private static String buildSummaryLine(String operationName, String targetId, String label) {
            String base = StringUtils.defaultIfBlank(operationName, "审批操作");
            if (StringUtils.isNotBlank(label)) {
                return base + "：" + label;
            }
            if (StringUtils.isNotBlank(targetId)) {
                return base + "：" + targetId;
            }
            return base;
        }

        private static Map<String, Object> sanitizePayload(Map<String, Object> payload) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            if (payload == null || payload.isEmpty()) {
        return sanitized;
    }
            Object inner = payload.get("payload");
            if (inner instanceof Map<?, ?> innerMap) {
                Map<String, Object> compact = new LinkedHashMap<>();
                copyIfPresent(innerMap, compact, "action");
                copyIfPresent(innerMap, compact, "actionDisplay");
                copyIfPresent(innerMap, compact, "username");
                copyIfPresent(innerMap, compact, "fullName");
                copyIfPresent(innerMap, compact, "changeRequestId");
                copyIfPresent(innerMap, compact, "target");
                sanitized.put("payload", compact);
            }
            copyIfPresent(payload, sanitized, "keycloakId");
            copyIfPresent(payload, sanitized, "realmRoles");
            copyIfPresent(payload, sanitized, "defaultPasswordApplied");
            copyIfPresent(payload, sanitized, "defaultPasswordError");
            copyIfPresent(payload, sanitized, "error");
            sanitizeDefaultRealmRolesInMap(sanitized);
            return sanitized;
        }

        private static void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
            if (source == null) {
                return;
            }
            Object value = source.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }

        private static String determineEffectiveId(String rawTarget, Map<String, Object> payload) {
            String candidate = StringUtils.trimToNull(rawTarget);
            if (candidate != null && !"UNKNOWN".equalsIgnoreCase(candidate)) {
                return candidate;
            }
            Object inner = payload != null ? payload.get("payload") : null;
            if (inner instanceof Map<?, ?> map) {
                Object value = map.get("username");
                if (value == null) {
                    Object target = map.get("target");
                    if (target instanceof Map<?, ?> targetMap) {
                        value = targetMap.get("id");
                        if (value == null) {
                            value = targetMap.get("username");
                        }
                    }
                }
                if (value != null) {
                    return value.toString();
                }
            }
            return null;
        }

        private static String extractLabel(Map<String, Object> payload) {
            Object inner = payload != null ? payload.get("payload") : null;
            if (!(inner instanceof Map<?, ?> map)) {
                return null;
            }
            String fullName = text(map.get("fullName"));
            String username = text(map.get("username"));
            if (StringUtils.isNotBlank(fullName) && StringUtils.isNotBlank(username) && !fullName.equals(username)) {
                return fullName + "（" + username + "）";
            }
            return StringUtils.defaultIfBlank(fullName, username);
        }

        private static String text(Object value) {
            if (value == null) {
                return null;
            }
            String s = value.toString().trim();
            return s.isEmpty() ? null : s;
        }

        private record CollectorTarget(String table, List<String> ids, Map<String, String> labels) {}
    }

    private ApprovalAuditCollector collectApprovalSnapshot(AdminApprovalRequest approval) {
        ApprovalAuditCollector collector = new ApprovalAuditCollector();
        if (approval == null || approval.getItems() == null) {
            return collector;
        }
        for (AdminApprovalItem item : approval.getItems()) {
            Map<String, Object> payload = safeReadPayload(item.getPayloadJson());
            if (payload.isEmpty()) {
                continue;
            }
            String operationCode = resolveOperationCodeForApprovalPayload(item.getTargetKind(), payload);
            if (operationCode == null) {
                continue;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("payload", payload);
            collector.collect(operationCode, AuditStage.SUCCESS, item.getTargetId(), detail);
        }
        return collector;
    }

    private Map<String, Object> safeReadPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            LOG.warn("Failed to parse approval payload: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String resolveOperationCodeForApprovalPayload(String targetKind, Map<String, Object> payload) {
        String action = stringValue(payload.get("action"));
        if (action == null) {
            return null;
        }
        return switch (action) {
            case "create" -> "ADMIN_USER_CREATE";
            case "update" -> "ADMIN_USER_UPDATE";
            case "delete" -> "ADMIN_USER_DELETE";
            case "grantRoles", "revokeRoles" -> "ADMIN_USER_ASSIGN_ROLE";
            case "setEnabled" -> Boolean.TRUE.equals(booleanValue(payload.get("enabled"))) ? "ADMIN_USER_ENABLE" : "ADMIN_USER_DISABLE";
            case "setPersonLevel" -> "ADMIN_USER_UPDATE";
            case "resetPassword" -> "ADMIN_USER_RESET_PASSWORD";
            default -> null;
        };
    }

    private String buildFallbackApprovalSummary(String status, Long primaryChangeRequestId, long approvalId) {
        String label = translateApprovalStatus(status);
        if (primaryChangeRequestId != null && primaryChangeRequestId > 0) {
            return label + "：CR-" + primaryChangeRequestId;
        }
        return label + "：审批单#" + approvalId;
    }

    private String translateApprovalStatus(String status) {
        if (status == null) {
            return "审批结果";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "审批通过";
            case "REJECTED" -> "审批拒绝";
            case "PROCESSING" -> "审批待定";
            default -> "审批结果";
        };
    }

    private String extractPayloadUsername(Object detail) {
        if (!(detail instanceof Map<?, ?> map)) {
            return null;
        }
        Object payloadObj = map.get("payload");
        if (!(payloadObj instanceof Map<?, ?> payloadMap)) {
            return null;
        }
        Object username = payloadMap.get("username");
        if (username == null) {
            Object target = payloadMap.get("target");
            if (target instanceof Map<?, ?> targetMap) {
                username = targetMap.get("username");
            }
        }
        if (username == null) {
            Object request = payloadMap.get("request");
            if (request instanceof Map<?, ?> requestMap) {
                username = requestMap.get("username");
            }
        }
        return stringValue(username);
    }

    private String extractPayloadFullName(Object detail) {
        if (!(detail instanceof Map<?, ?> map)) {
            return null;
        }
        Object payloadObj = map.get("payload");
        if (!(payloadObj instanceof Map<?, ?> payloadMap)) {
            return null;
        }
        String directFullName = stringValue(payloadMap.get("fullName"));
        if (StringUtils.isNotBlank(directFullName)) {
            return directFullName;
        }
        String requestFullName = nestedFullName(payloadMap.get("request"));
        if (StringUtils.isNotBlank(requestFullName)) {
            return requestFullName;
        }
        String targetFullName = nestedFullName(payloadMap.get("target"));
        if (StringUtils.isNotBlank(targetFullName)) {
            return targetFullName;
        }
        Object changeSnapshot = payloadMap.get("changeSnapshot");
        if (changeSnapshot instanceof Map<?, ?> changeMap) {
            String afterFullName = nestedFullName(changeMap.get("after"));
            if (StringUtils.isNotBlank(afterFullName)) {
                return afterFullName;
            }
            String beforeFullName = nestedFullName(changeMap.get("before"));
            if (StringUtils.isNotBlank(beforeFullName)) {
                return beforeFullName;
            }
        }
        return null;
    }

    private String nestedFullName(Object candidate) {
        if (candidate instanceof Map<?, ?> map) {
            Object value = map.get("fullName");
            return stringValue(value);
        }
        return null;
    }

    private void enrichExecutionPayloadWithLabel(
        Map<String, Object> detail,
        String username,
        String fullName,
        String label
    ) {
        if (detail == null) {
            return;
        }
        Object payloadObj = detail.get("payload");
        if (payloadObj instanceof Map<?, ?> rawPayload) {
            Map<String, Object> payloadMap = toStringKeyMap(rawPayload);
            detail.put("payload", payloadMap);
            if (StringUtils.isNotBlank(username) && StringUtils.isBlank(stringValue(payloadMap.get("username")))) {
                payloadMap.put("username", username);
            }
            if (StringUtils.isNotBlank(fullName) && StringUtils.isBlank(stringValue(payloadMap.get("fullName")))) {
                payloadMap.put("fullName", fullName);
            }
            Object requestObj = payloadMap.get("request");
            if (requestObj instanceof Map<?, ?> requestRaw) {
                Map<String, Object> requestMap = toStringKeyMap(requestRaw);
                if (StringUtils.isNotBlank(username) && StringUtils.isBlank(stringValue(requestMap.get("username")))) {
                    requestMap.put("username", username);
                }
                if (StringUtils.isNotBlank(fullName) && StringUtils.isBlank(stringValue(requestMap.get("fullName")))) {
                    requestMap.put("fullName", fullName);
                }
                payloadMap.put("request", requestMap);
            }
            Object targetObj = payloadMap.get("target");
            if (targetObj instanceof Map<?, ?> targetRaw) {
                Map<String, Object> targetMap = toStringKeyMap(targetRaw);
                if (StringUtils.isNotBlank(username) && StringUtils.isBlank(stringValue(targetMap.get("username")))) {
                    targetMap.put("username", username);
                }
                if (StringUtils.isNotBlank(fullName) && StringUtils.isBlank(stringValue(targetMap.get("fullName")))) {
                    targetMap.put("fullName", fullName);
                }
                payloadMap.put("target", targetMap);
            }
        }
        if (StringUtils.isNotBlank(label)) {
            detail.put("targetLabel", label);
        }
    }

    private String changeRequestRefFrom(Object reference) {
        if (reference == null) {
            return null;
        }
        Object raw = reference;
        if (raw instanceof Optional<?> optional) {
            return changeRequestRefFrom(optional.orElse(null));
        }
        if (raw instanceof ChangeRequest changeRequest) {
            return changeRequestRefFrom(changeRequest.getId());
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value <= 0) {
                return null;
            }
            return "CR-" + value;
        }
        String text = raw.toString();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("CR-")) {
            return trimmed;
        }
        boolean digits = trimmed.chars().allMatch(Character::isDigit);
        if (digits) {
            return "CR-" + trimmed;
        }
        return trimmed;
    }

    private String normalizeSecurityLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return SECURITY_LEVEL_ALIASES.getOrDefault(normalized, normalized);
    }

    private AdminKeycloakUser ensureSnapshot(String username) {
        return userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
    }

    public AdminKeycloakUser syncSnapshot(KeycloakUserDTO dto) {
        AdminKeycloakUser entity = userRepository.findByKeycloakId(dto.getId()).orElseGet(AdminKeycloakUser::new);
        mapFromDto(entity, dto);
        return userRepository.save(entity);
    }

    private void mapFromDto(AdminKeycloakUser entity, KeycloakUserDTO dto) {
        entity.setKeycloakId(dto.getId());
        entity.setUsername(dto.getUsername());
        entity.setFullName(resolveFullName(dto));
        entity.setEmail(dto.getEmail());
        entity.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        String securityLevel = normalizeSecurityLevel(extractSingle(dto, "person_security_level"));
        entity.setPersonSecurityLevel(securityLevel);
        entity.setRealmRoles(dto.getRealmRoles());
        entity.setGroupPaths(dto.getGroups());
        entity.setPhone(extractSingle(dto, "phone"));
        entity.setLastSyncAt(Instant.now());
        if (!SUPPORTED_SECURITY_LEVELS.contains(securityLevel)) {
            throw new IllegalStateException("用户密级无效: " + securityLevel);
        }
    }

    private void refreshSnapshotFromKeycloak(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        String accessToken = resolveSnapshotAccessToken();
        if (StringUtils.isBlank(accessToken)) {
            LOG.debug("Skip refreshing snapshot for {} because no access token is available", username);
            return;
        }
        try {
            keycloakAdminClient
                .findByUsername(username, accessToken)
                .ifPresent(remote -> {
                    refreshUserGroups(remote, accessToken);
                    syncSnapshot(remote);
                });
        } catch (Exception ex) {
            LOG.warn("Failed to refresh snapshot for user {} before submission: {}", username, ex.getMessage());
        }
    }

    private AdminKeycloakUser ensureFreshSnapshot(String username) {
        refreshSnapshotFromKeycloak(username);
        return ensureSnapshot(username);
    }

    private String resolveSnapshotAccessToken() {
        try {
            return resolveManagementToken();
        } catch (Exception ex) {
            String fallback = currentRequestAccessToken();
            if (StringUtils.isNotBlank(fallback)) {
                LOG.debug("Falling back to current request token for snapshot refresh: {}", ex.getMessage());
                return fallback;
            }
            LOG.warn("Unable to obtain access token for snapshot refresh: {}", ex.getMessage());
            return null;
        }
    }

    private String currentRequestAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        if (authentication instanceof BearerTokenAuthentication bearer) {
            return bearer.getToken().getTokenValue();
        }
        return null;
    }

    private void refreshUserGroups(KeycloakUserDTO dto, String accessToken) {
        if (dto == null) {
            return;
        }
        List<String> current = normalizeGroupPathList(dto.getGroups());
        if (!current.isEmpty()) {
            dto.setGroups(current);
            ensureDeptCodeAttribute(dto, current);
            return;
        }
        String username = StringUtils.trimToNull(dto.getUsername());
        if (username != null) {
            userRepository
                .findByUsernameIgnoreCase(username)
                .map(AdminKeycloakUser::getGroupPaths)
                .ifPresent(paths -> {
                    List<String> normalized = normalizeGroupPathList(paths);
                    dto.setGroups(normalized);
                    ensureDeptCodeAttribute(dto, normalized);
                });
        }
        if (dto.getGroups() == null) {
            dto.setGroups(List.of());
        }
    }

    private String normalizeGroupPath(String path) {
        if (!StringUtils.isNotBlank(path)) {
            return null;
        }
        String trimmed = path.trim().replaceAll("/{2,}", "/");
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private List<String> normalizeGroupPathList(Collection<?> source) {
        List<String> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Object item : source) {
            if (item == null) {
                continue;
            }
            String normalized = normalizeGroupPath(item.toString());
            if (normalized != null && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> mergeGroupPaths(Collection<String> existing, Collection<String> additions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(normalizeGroupPathList(existing));
        merged.addAll(normalizeGroupPathList(additions));
        return new ArrayList<>(merged);
    }

    private List<String> normalizeGroupPathListForDiff(Collection<?> source) {
        List<String> normalized = normalizeGroupPathList(source);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(normalized);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private String resolveGroupPathByDept(String deptCode) {
        if (!StringUtils.isNotBlank(deptCode)) {
            return null;
        }
        return organizationRepository
            .findFirstByDeptCodeIgnoreCase(deptCode)
            .map(this::buildGroupPath)
            .orElse(null);
    }

    private List<String> resolveGroupPathsFromProfile(PersonProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        String fromDeptCode = resolveGroupPathByDept(profile.getDeptCode());
        if (StringUtils.isNotBlank(fromDeptCode)) {
            paths.add(fromDeptCode);
        }
        String fromDeptPath = StringUtils.firstNonBlank(
            profile.getDeptPath(),
            extractSingle(profile.getAttributes(), "dept_path", "deptPath", "departmentPath", "org_path")
        );
        if (StringUtils.isNotBlank(fromDeptPath)) {
            String normalized = normalizeGroupPath(fromDeptPath);
            if (normalized != null && !paths.contains(normalized)) {
                paths.add(normalized);
            }
        }
        return normalizeGroupPathList(paths);
    }

    private List<String> resolveGroupPathsFromProfiles(String username) {
        if (!StringUtils.isNotBlank(username)) {
            return List.of();
        }
        // account 优先，其次按 personCode 兜底
        return personProfileRepository
            .findByAccountIgnoreCase(username.trim())
            .map(this::resolveGroupPathsFromProfile)
            .orElseGet(() ->
                personProfileRepository
                    .findByPersonCodeIgnoreCase(username.trim())
                    .map(this::resolveGroupPathsFromProfile)
                    .orElse(List.of())
            );
    }

    private String buildGroupPath(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        OrganizationNode cursor = node;
        int guard = 20;
        while (cursor != null && guard-- > 0) {
            String name = StringUtils.trimToNull(cursor.getName());
            if (name != null) {
                segments.add(0, name);
            }
            OrganizationNode parent = cursor.getParent();
            if (parent == null && StringUtils.isNotBlank(cursor.getParentCode())) {
                parent = organizationRepository.findFirstByDeptCodeIgnoreCase(cursor.getParentCode()).orElse(null);
            }
            cursor = parent;
        }
        if (segments.isEmpty()) {
            return null;
        }
        return normalizeGroupPath("/" + String.join("/", segments));
    }

    private void ensureDeptCodeAttribute(KeycloakUserDTO dto, List<String> groupPaths) {
        if (dto == null || groupPaths == null || groupPaths.isEmpty()) {
            return;
        }
        resolveOrganizationIdFromPath(groupPaths.get(0)).ifPresent(orgId -> {
            Map<String, List<String>> attributes = dto.getAttributes() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(dto.getAttributes());
            attributes.put("dept_code", List.of(String.valueOf(orgId)));
            dto.setAttributes(attributes);
        });
    }

    private Optional<Long> resolveOrganizationIdFromPath(String groupPath) {
        if (!StringUtils.isNotBlank(groupPath)) {
            return Optional.empty();
        }
        String normalized = groupPath.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String[] segments = Arrays.stream(normalized.split("/")).map(String::trim).filter(segment -> !segment.isEmpty()).toArray(String[]::new);
        if (segments.length == 0) {
            return Optional.empty();
        }
        Optional<OrganizationNode> current = organizationRepository.findFirstByNameAndParentIsNull(segments[0]);
        for (int i = 1; i < segments.length && current.isPresent(); i++) {
            OrganizationNode parent = current.orElseThrow();
            current = organizationRepository.findFirstByParentIdAndName(parent.getId(), segments[i]);
        }
        return current.map(OrganizationNode::getId);
    }

    private String resolveFullName(KeycloakUserDTO dto) {
        if (dto == null) {
            return "";
        }
        String attributeName = StringUtils.firstNonBlank(
            extractSingle(dto, "fullName"),
            extractSingle(dto, "fullname"),
            extractSingle(dto, "display_name"),
            extractSingle(dto, "displayName")
        );
        String combined = buildName(dto.getFirstName(), dto.getLastName());
        String fallback = StringUtils.firstNonBlank(
            StringUtils.trimToNull(dto.getFullName()),
            combined,
            StringUtils.trimToNull(dto.getUsername())
        );
        String candidate = StringUtils.firstNonBlank(StringUtils.trimToNull(attributeName), fallback);
        return StringUtils.isNotBlank(candidate) ? candidate.trim() : dto.getUsername();
    }

    private String buildName(String firstName, String lastName) {
        String first = StringUtils.trimToNull(firstName);
        String last = StringUtils.trimToNull(lastName);
        if (first == null && last == null) {
            return null;
        }
        if (last == null) {
            return first;
        }
        if (first == null) {
            return last;
        }
        return last + " " + first;
    }

    private String extractSingle(KeycloakUserDTO dto, String key) {
        List<String> values = dto.getAttributes().get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String extractSingle(Map<String, Object> attrs, String... keys) {
        if (attrs == null || attrs.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object val = attrs.get(key);
            if (val == null) {
                continue;
            }
            if (val instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first != null) {
                    return StringUtils.trimToNull(String.valueOf(first));
                }
            } else {
                String str = StringUtils.trimToNull(String.valueOf(val));
                if (str != null) {
                    return str;
                }
            }
        }
        return null;
    }

    private List<String> extractList(KeycloakUserDTO dto, String key) {
        List<String> values = dto.getAttributes().get(key);
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values);
    }

    public List<ApprovalDTOs.ApprovalRequest> listApprovals() {
        return approvalRepository
            .findAll(Sort.by(Sort.Direction.DESC, "id"))
            .stream()
            .map(this::toSummaryDto)
            .toList();
    }

    public Optional<ApprovalDTOs.ApprovalRequestDetail> findApprovalDetail(long id) {
        return approvalRepository.findWithItemsById(id).map(this::toDetailDto);
    }

    public Optional<AdminApprovalRequest> findApprovalEntity(long id) {
        return approvalRepository.findWithItemsById(id);
    }

    public Optional<AdminKeycloakUser> findSnapshotByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    public ApprovalDTOs.ApprovalRequestDetail toDetailDto(AdminApprovalRequest approval) {
        ApprovalDTOs.ApprovalRequestDetail dto = new ApprovalDTOs.ApprovalRequestDetail();
        dto.id = approval.getId();
        dto.requester = approval.getRequester();
        dto.type = approval.getType();
        dto.reason = approval.getReason();
        dto.createdAt = approval.getCreatedDate() != null ? approval.getCreatedDate().toString() : null;
        dto.decidedAt = approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null;
        dto.status = approval.getStatus();
        dto.approver = approval.getApprover();
        dto.decisionNote = approval.getDecisionNote();
        dto.errorMessage = approval.getErrorMessage();
        dto.retryCount = approval.getRetryCount() == null ? 0 : approval.getRetryCount();
        dto.category = resolveApprovalCategory(approval.getType());
        dto.items = approval
            .getItems()
            .stream()
            .map(item -> {
                ApprovalDTOs.ApprovalItem it = new ApprovalDTOs.ApprovalItem();
                it.id = item.getId() == null ? 0 : item.getId();
                it.targetKind = item.getTargetKind();
                it.targetId = item.getTargetId();
                it.seqNumber = item.getSeqNumber();
                it.payload = item.getPayloadJson();
                return it;
            })
            .toList();
        return dto;
    }

    private ApprovalDTOs.ApprovalRequest toSummaryDto(AdminApprovalRequest approval) {
        ApprovalDTOs.ApprovalRequest dto = new ApprovalDTOs.ApprovalRequest();
        dto.id = approval.getId();
        dto.requester = approval.getRequester();
        dto.type = approval.getType();
        dto.reason = approval.getReason();
        dto.createdAt = approval.getCreatedDate() != null ? approval.getCreatedDate().toString() : null;
        dto.decidedAt = approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null;
        dto.status = approval.getStatus();
        dto.approver = approval.getApprover();
        dto.decisionNote = approval.getDecisionNote();
        dto.errorMessage = approval.getErrorMessage();
        dto.retryCount = approval.getRetryCount() == null ? 0 : approval.getRetryCount();
        dto.category = resolveApprovalCategory(approval.getType());
        return dto;
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

    public ApprovalDTOs.ApprovalRequestDetail approve(
        long id,
        String approver,
        String note,
        String accessToken,
        String clientIp
    ) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Long primaryChangeRequestId = changeRequestIds.stream().findFirst().orElse(null);
        String changeRequestRef = changeRequestRefFrom(primaryChangeRequestId);
        Instant now = Instant.now();
        ApprovalAuditCollector collector = new ApprovalAuditCollector();
        approvalAuditCollector.set(collector);
        try {
            // Prefer caller token when provided; fall back to service account for reliability
            List<String> candidateTokens = new ArrayList<>();
            if (accessToken != null && !accessToken.isBlank()) {
                candidateTokens.add(accessToken);
            }
            candidateTokens.add(resolveManagementToken());

            Exception last = null;
            for (int i = 0; i < candidateTokens.size(); i++) {
                String token = candidateTokens.get(i);
                boolean lastAttempt = i == candidateTokens.size() - 1;
                suppressAuditFailure.set(!lastAttempt);
                try {
                    LOG.info(
                        "Applying approval id={} type={} items={} by approver={} using {} token",
                        id,
                        approval.getType(),
                        approval.getItems().size(),
                        approver,
                        token == accessToken ? "caller" : "service-account"
                    );
                    applyApproval(approval, token, approver);
                    last = null;
                    break;
                } catch (Exception exInner) {
                    last = exInner;
                    LOG.warn("Approval apply attempt failed with current token: {}", exInner.getMessage());
                } finally {
                    suppressAuditFailure.remove();
                }
            }
            if (last != null) {
                throw last;
            }
            approval.setStatus(ApprovalStatus.APPLIED.name());
            approval.setDecidedAt(now);
            approval.setApprover(approver);
            approval.setDecisionNote(note);
            approval.setErrorMessage(null);
            approvalRepository.save(approval);
            recordApprovalDecisionEntry(
                approver,
                collector,
                changeRequestRef,
                id,
                primaryChangeRequestId,
                changeRequestIds,
                note,
                approval,
                "APPROVED",
                "APPROVED",
                AuditOperationType.APPROVE,
                AuditOperationType.APPROVE.getDisplayName(),
                clientIp
            );
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.APPLIED.name(), approver, now, null);
            return toDetailDto(approval);
        } catch (Exception ex) {
            Map<String, Object> retryDetail = new LinkedHashMap<>();
            retryDetail.put("error", ex.getMessage());
            retryDetail.put("status", "RETRY_SCHEDULED");
            retryDetail.put("type", approval.getType());
            if (StringUtils.isNotBlank(changeRequestRef)) {
                retryDetail.put("changeRequestRef", changeRequestRef);
            }
            scheduleRetry(approval, note, ex.getMessage());
            approvalRepository.save(approval);
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.PENDING.name(), null, null, ex.getMessage());
            Map<String, Object> requeueDetail = new LinkedHashMap<>();
            requeueDetail.put("status", "REQUEUE");
            requeueDetail.put("note", ex.getMessage());
            requeueDetail.put("type", approval.getType());
            if (StringUtils.isNotBlank(changeRequestRef)) {
                requeueDetail.put("changeRequestRef", changeRequestRef);
            }
            Map<String, Object> v2Detail = new LinkedHashMap<>(retryDetail);
            OperationContext failureContext = resolveApprovalOperationContext(collector, AuditOperationType.APPROVE);
            v2Detail.put("operationType", failureContext.operationType().getCode());
            v2Detail.put("operationTypeText", failureContext.operationTypeText());
            recordApprovalDecisionV2(
                approver,
                "FAILED",
                "FAILED",
                id,
                approval,
                changeRequestRef,
                primaryChangeRequestId,
                changeRequestIds,
                note,
                v2Detail,
                collector,
                buildFallbackApprovalSummary("FAILED", primaryChangeRequestId, id),
                failureContext,
                clientIp
            );
            LOG.warn("Approval id={} failed to apply: {}", id, ex.getMessage());
            throw new IllegalStateException("审批执行失败: " + ex.getMessage(), ex);
        } finally {
            approvalAuditCollector.remove();
        }
    }

    private void recordApprovalDecisionEntry(
        String approver,
        ApprovalAuditCollector collector,
        String changeRequestRef,
        long approvalId,
        Long primaryChangeRequestId,
        Set<Long> changeRequestIds,
        String note,
        AdminApprovalRequest approval,
        String statusCode,
        String resultCode,
        AuditOperationType operationType,
        String operationTypeText,
        String clientIp
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("result", resultCode);
        detail.put("status", statusCode);
        detail.put("approvalId", approvalId);
        detail.put("type", approval.getType());
        if (StringUtils.isNotBlank(note)) {
            detail.put("note", note);
        }
        if (primaryChangeRequestId != null) {
            detail.put("changeRequestId", primaryChangeRequestId);
        }
        if (StringUtils.isNotBlank(changeRequestRef)) {
            detail.put("changeRequestRef", changeRequestRef);
        }
        attachChangeRequestSnapshots(detail, changeRequestIds);
        if (collector != null && collector.hasData()) {
            collector.appendDetails(detail);
        }

        OperationContext operationContext = resolveApprovalOperationContext(collector, operationType);
        String resolvedOperationText;
        if (operationContext.descriptor() != null) {
            resolvedOperationText = operationContext.operationTypeText();
        } else if (StringUtils.isNotBlank(operationTypeText)) {
            resolvedOperationText = operationTypeText;
        } else {
            resolvedOperationText = operationContext.operationTypeText();
        }
        detail.put("operationType", operationContext.operationType().getCode());
        detail.put("operationTypeText", resolvedOperationText);
        Map<String, Object> detailForV2 = new LinkedHashMap<>(detail);
        String summaryText = collector != null ? collector.summaryText() : null;
        summaryText = buildApprovalDecisionSummary(collector, summaryText, statusCode, primaryChangeRequestId, approvalId);
        recordApprovalDecisionV2(
            approver,
            statusCode,
            resultCode,
            approvalId,
            approval,
            changeRequestRef,
            primaryChangeRequestId,
            changeRequestIds,
            note,
            detailForV2,
            collector,
            summaryText,
            operationContext,
            clientIp
        );
    }

    private void recordApprovalDecisionV2(
        String approver,
        String statusCode,
        String resultCode,
        long approvalId,
        AdminApprovalRequest approval,
        String changeRequestRef,
        Long primaryChangeRequestId,
        Set<Long> changeRequestIds,
        String note,
        Map<String, Object> detail,
        ApprovalAuditCollector collector,
        String summaryText,
        OperationContext operationContext,
        String clientIp
    ) {
        if (StringUtils.isBlank(approver)) {
            return;
        }
        try {
            String normalizedStatus = StringUtils.defaultString(statusCode).trim().toUpperCase(Locale.ROOT);
            String buttonCode = switch (normalizedStatus) {
                case "REJECTED", "REJECT" -> ButtonCodes.APPROVAL_REJECT;
                case "PROCESSING", "PROCESS", "PENDING" -> ButtonCodes.APPROVAL_DELAY;
                default -> ButtonCodes.APPROVAL_APPROVE;
            };
            AuditResultStatus resultStatus = switch (normalizedStatus) {
                case "REJECTED", "REJECT", "FAILED", "FAIL" -> AuditResultStatus.FAILED;
                case "PROCESSING", "PROCESS", "PENDING" -> AuditResultStatus.PENDING;
                default -> AuditResultStatus.SUCCESS;
            };
            String resolvedSummary = org.springframework.util.StringUtils.hasText(summaryText)
                ? summaryText
                : buildFallbackApprovalSummary(statusCode, primaryChangeRequestId, approvalId);

            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(approver, buttonCode)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(resolvedSummary)
                .result(resultStatus)
                .request("/api/admin/approvals/" + approvalId + "/decision", "POST");
            String resolvedIp = IpAddressUtils.resolveClientIp(clientIp);
            if (StringUtils.isNotBlank(resolvedIp)) {
                builder.client(resolvedIp, null);
            }
            builder.metadata("approvalId", approvalId);
            if (approval != null) {
                builder.metadata("approvalType", approval.getType());
                builder.metadata("requester", approval.getRequester());
            }
            builder.metadata("status", statusCode);
            builder.metadata("resultCode", resultCode);
            OperationContext ctx = operationContext != null
                ? operationContext
                : resolveApprovalOperationContext(collector, AuditOperationType.APPROVE);
            if (ctx != null) {
                builder.metadata("operationType", ctx.operationType().getCode());
                builder.metadata("operationTypeText", ctx.operationTypeText());
                AdminAuditOperation descriptor = ctx.descriptor();
                if (descriptor != null) {
                    builder.moduleOverride(descriptor.moduleKey(), descriptor.moduleLabel());
                }
                AuditOperationKind overrideKind = mapOperationKind(ctx.operationType());
                builder.operationOverride(
                    descriptor != null ? descriptor.code() : null,
                    descriptor != null ? descriptor.defaultName() : ctx.operationTypeText(),
                    overrideKind != null ? overrideKind : AuditOperationKind.OTHER
                );
            }
            if (StringUtils.isNotBlank(note)) {
                builder.metadata("note", note);
            }
            if (changeRequestIds != null && !changeRequestIds.isEmpty()) {
                builder.metadata("changeRequestIds", new ArrayList<>(changeRequestIds));
            }
            if (org.springframework.util.StringUtils.hasText(changeRequestRef)) {
                builder.changeRequestRef(changeRequestRef);
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }

            if (collector != null) {
                collector
                    .primaryTarget()
                    .ifPresent(target -> {
                        if (StringUtils.isNotBlank(target.table())) {
                            for (String id : target.ids()) {
                                String label = target.labels().getOrDefault(id, id);
                                builder.target(target.table(), id, label);
                            }
                        }
                    });
            }

            if (primaryChangeRequestId != null) {
                String label = org.springframework.util.StringUtils.hasText(changeRequestRef) ? changeRequestRef : "CR-" + primaryChangeRequestId;
                builder.target("change_request", primaryChangeRequestId, label);
                if (!org.springframework.util.StringUtils.hasText(changeRequestRef)) {
                    builder.changeRequestRef(label);
                }
            }

            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 approval decision [{}]: {}", statusCode, ex.getMessage());
        }
    }

    private String buildApprovalDecisionSummary(
        ApprovalAuditCollector collector,
        String existingSummary,
        String statusCode,
        Long primaryChangeRequestId,
        long approvalId
    ) {
        String fallback = StringUtils.isNotBlank(existingSummary)
            ? existingSummary
            : buildFallbackApprovalSummary(statusCode, primaryChangeRequestId, approvalId);
        if (collector == null || !collector.hasData()) {
            return fallback;
        }
        String operationCode = collector.primaryOperationCode().orElse(null);
        String targetLabel = collector.primaryTarget()
            .flatMap(target -> {
                if (target.ids().isEmpty()) {
                    return Optional.<String>empty();
                }
                String firstId = target.ids().get(0);
                String label = target.labels().getOrDefault(firstId, firstId);
                return Optional.ofNullable(label);
            })
            .orElse(null);
        String prefix;
        if (operationCode != null) {
            prefix = switch (operationCode) {
                case "ADMIN_USER_CREATE" -> "审批用户新增请求";
                case "ADMIN_USER_UPDATE" -> "审批用户修改请求";
                case "ADMIN_USER_ASSIGN_ROLE" -> "审批用户角色调整请求";
                case "ADMIN_USER_DELETE" -> "审批用户删除请求";
                case "ADMIN_USER_ENABLE" -> "审批用户启用请求";
                case "ADMIN_USER_DISABLE" -> "审批用户停用请求";
                case "ADMIN_USER_RESET_PASSWORD" -> "审批用户重置密码请求";
                case "ADMIN_USER_SET_PERSON_LEVEL", "ADMIN_USER_SET_LEVEL" -> "审批用户密级调整请求";
                default -> null;
            };
        } else {
            prefix = null;
        }
        if (prefix == null) {
            String primaryName = collector.primaryOperationName();
            if (StringUtils.isNotBlank(primaryName)) {
                int colonIndex = primaryName.indexOf('：');
                String operationName = colonIndex >= 0 ? primaryName.substring(0, colonIndex) : primaryName;
                String subject = colonIndex >= 0 ? primaryName.substring(colonIndex + 1) : targetLabel;
                if (StringUtils.isNotBlank(operationName)) {
                    prefix = "审批" + operationName + "请求";
                }
                if (StringUtils.isBlank(targetLabel) && StringUtils.isNotBlank(subject)) {
                    targetLabel = subject;
                }
            }
        }
        if (StringUtils.isBlank(prefix)) {
            return fallback;
        }
        if (StringUtils.isNotBlank(targetLabel)) {
            return prefix + "：" + targetLabel;
        }
        return prefix;
    }

    private void recordUserApprovalRequestV2(
        String actor,
        String actionCode,
        SubjectInfo subject,
        ChangeRequest changeRequest,
        String changeRequestRef,
        String summary,
        Map<String, Object> detail,
        String clientIp
    ) {
        String buttonCode = resolveUserButtonCode(actionCode);
        if (!StringUtils.isNotBlank(actor) || buttonCode == null) {
            return;
        }
        if (shouldSuppressPendingApprovalAudit(actionCode)) {
            return;
        }
        try {
            String subjectLabel = subject != null ? firstNonBlank(subject.label(), subject.id()) : null;
            String fallbackSummary = buildUserRequestSummary(actionCode, subjectLabel);
            enrichDetailWithSnapshotSummary(detail, changeRequest);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(firstNonBlank(summary, fallbackSummary))
                .result(AuditResultStatus.PENDING)
                .client(clientIp, null)
                .request("/internal/admin/users/approval", "POST")
                .allowEmptyTargets();
            builder.metadata("actionCode", actionCode);
            builder.metadata("status", "PENDING");
            if (subject != null) {
                if (StringUtils.isNotBlank(subject.id())) {
                    builder.metadata("username", subject.id());
                }
                if (StringUtils.isNotBlank(subject.label())) {
                    builder.metadata("displayName", subject.label());
                }
            }
            if (detail != null && !detail.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(detail));
            }
            if (changeRequest != null) {
                String ref = org.springframework.util.StringUtils.hasText(changeRequestRef)
                    ? changeRequestRef
                    : "CR-" + changeRequest.getId();
                builder.target("change_request", changeRequest.getId(), ref);
                builder.changeRequestRef(ref);
            } else if (org.springframework.util.StringUtils.hasText(changeRequestRef)) {
                builder.changeRequestRef(changeRequestRef);
            } else if (subject != null && StringUtils.isNotBlank(subject.id())) {
                builder.target("admin_keycloak_user", subject.id(), firstNonBlank(subject.label(), subject.id()));
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 user approval request [{}]: {}", actionCode, ex.getMessage());
        }
    }

    private void enrichDetailWithSnapshotSummary(Map<String, Object> detail, ChangeRequest changeRequest) {
        if (detail == null) {
            return;
        }
        try {
            ChangeSnapshot snapshot = extractChangeSnapshot(detail, changeRequest);
            if (snapshot == null || !hasSnapshotContent(snapshot)) {
                return;
            }
            if (!detail.containsKey("changeSnapshot")) {
                detail.put("changeSnapshot", snapshot.toMap());
            }
            if (detail.containsKey("changeSummary")) {
                return;
            }
            String resourceType = changeRequest != null && org.springframework.util.StringUtils.hasText(changeRequest.getResourceType())
                ? changeRequest.getResourceType()
                : "USER";
            if (changeSnapshotFormatter != null) {
                List<Map<String, String>> summaryRows = changeSnapshotFormatter.format(snapshot, resourceType);
                if (summaryRows != null && !summaryRows.isEmpty()) {
                    detail.put("changeSummary", summaryRows);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to enrich change summary for approval request: {}", ex.getMessage());
        }
    }

    private boolean hasSnapshotContent(ChangeSnapshot snapshot) {
        return snapshot != null && (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty());
    }


    private void recordUserApprovalExecutionV2(
        String actor,
        String actionCode,
        String normalizedTarget,
        String username,
        String subjectLabel,
        String changeRequestRef,
        AuditStage stage,
        Map<String, Object> payload
    ) {
        ApprovalAuditCollector collector = approvalAuditCollector.get();
        if (collector != null && stage == AuditStage.SUCCESS) {
            return;
        }
        String buttonCode = resolveUserButtonCode(actionCode);
        if (!StringUtils.isNotBlank(actor) || buttonCode == null) {
            return;
        }
        try {
            if (payload != null) {
                sanitizeDefaultRealmRolesInMap(payload);
            }
            String label = firstNonBlank(subjectLabel, username, normalizedTarget);
            String summary = firstNonBlank(
                stringValue(payload != null ? payload.get("summary") : null),
                stringValue(payload != null ? payload.get("operationName") : null),
                buildUserExecutionSummary(actionCode, label)
            );
            summary = normalizeSummaryTarget(summary, label);
            AuditResultStatus resultStatus = stage == AuditStage.SUCCESS ? AuditResultStatus.SUCCESS : AuditResultStatus.FAILED;
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(actor, buttonCode)
                .actorRoles(SecurityUtils.getCurrentUserAuthorities())
                .summary(summary)
                .result(resultStatus)
                .client(null, null)
                .request("/internal/admin/users/execute", "POST")
                .allowEmptyTargets();
            builder.metadata("actionCode", actionCode);
            builder.metadata("stage", stage.name());
            if (StringUtils.isNotBlank(username)) {
                builder.metadata("username", username);
            }
            if (StringUtils.isNotBlank(subjectLabel)) {
                builder.metadata("displayName", subjectLabel);
            }
            if (StringUtils.isNotBlank(label)) {
                builder.metadata("targetLabel", label);
            }
            if (StringUtils.isNotBlank(normalizedTarget)) {
                builder.metadata("targetId", normalizedTarget);
            }
            if (org.springframework.util.StringUtils.hasText(changeRequestRef)) {
                builder.changeRequestRef(changeRequestRef);
            }
            if (payload != null && !payload.isEmpty()) {
                builder.detail("detail", new LinkedHashMap<>(payload));
            }
            Object sourcePrimaryKey = payload != null ? payload.get("sourcePrimaryKey") : null;
            Object sourceTable = payload != null ? payload.get("sourceTable") : null;
            if (sourceTable != null) {
                builder.metadata("sourceTable", sourceTable);
            }
            if (sourcePrimaryKey != null) {
                builder.metadata("sourcePrimaryKey", sourcePrimaryKey);
            }
            String targetId = firstNonBlank(normalizedTarget, username);
            if (StringUtils.isNotBlank(targetId)) {
                String targetDisplay = firstNonBlank(subjectLabel, label, username, targetId);
                builder.target("admin_keycloak_user", targetId, targetDisplay);
            }
            auditV2Service.record(builder.build());
        } catch (Exception ex) {
            LOG.warn("Failed to record V2 user approval execution [{}]: {}", actionCode, ex.getMessage());
        }
    }
    private Long resolveLocalUserId(String normalizedTarget, String username) {
        Long primaryKey = null;
        String candidate = StringUtils.trimToNull(normalizedTarget);
        if (candidate != null) {
            try {
                primaryKey = Long.valueOf(candidate);
            } catch (NumberFormatException ignored) {
                primaryKey =
                    userRepository
                        .findByKeycloakId(candidate)
                        .map(AdminKeycloakUser::getId)
                        .orElse(null);
                if (primaryKey == null) {
                    primaryKey =
                        userRepository
                            .findByUsernameIgnoreCase(candidate)
                            .map(AdminKeycloakUser::getId)
                            .orElse(null);
                }
            }
        }
        if (primaryKey == null && org.springframework.util.StringUtils.hasText(username)) {
            primaryKey =
                userRepository
                    .findByUsernameIgnoreCase(username.trim())
                    .map(AdminKeycloakUser::getId)
                    .orElse(null);
        }
        return primaryKey;
    }


    private String resolveUserButtonCode(String actionCode) {
        if (!StringUtils.isNotBlank(actionCode)) {
            return null;
        }
        return switch (actionCode) {
            case "ADMIN_USER_CREATE" -> ButtonCodes.USER_CREATE;
            case "ADMIN_USER_UPDATE" -> ButtonCodes.USER_UPDATE;
            case "ADMIN_USER_ASSIGN_ROLE" -> ButtonCodes.USER_ASSIGN_ROLES;
            case "ADMIN_USER_REMOVE_ROLES", "ADMIN_USER_REMOVE_ROLE" -> ButtonCodes.USER_REMOVE_ROLES;
            case "ADMIN_USER_ENABLE" -> ButtonCodes.USER_ENABLE;
            case "ADMIN_USER_DISABLE" -> ButtonCodes.USER_DISABLE;
            case "ADMIN_USER_RESET_PASSWORD" -> ButtonCodes.USER_RESET_PASSWORD;
            case "ADMIN_USER_SET_PERSON_LEVEL" -> ButtonCodes.USER_SET_PERSON_LEVEL;
            default -> null;
        };
    }

    private boolean shouldSuppressPendingApprovalAudit(String actionCode) {
        if (!StringUtils.isNotBlank(actionCode)) {
            return false;
        }
        return SUPPRESS_PENDING_APPROVAL_ACTIONS.contains(actionCode.trim().toUpperCase(Locale.ROOT));
    }

    private String buildUserRequestSummary(String actionCode, String subjectLabel) {
        String target = subjectLabel != null ? subjectLabel : "用户";
        return switch (actionCode) {
            case "ADMIN_USER_CREATE" -> "提交新增用户审批：" + target;
            case "ADMIN_USER_UPDATE" -> "提交修改用户审批：" + target;
            case "ADMIN_USER_ASSIGN_ROLE" -> "提交角色授权审批：" + target;
            case "ADMIN_USER_DISABLE" -> "提交停用用户审批：" + target;
            case "ADMIN_USER_ENABLE" -> "提交启用用户审批：" + target;
            case "ADMIN_USER_RESET_PASSWORD" -> "提交重置密码审批：" + target;
            case "ADMIN_USER_SET_PERSON_LEVEL" -> "提交调整密级审批：" + target;
            default -> "提交用户操作审批：" + target;
        };
    }

    private String buildUserExecutionSummary(String actionCode, String targetLabel) {
        String target = targetLabel != null ? targetLabel : "用户";
        return switch (actionCode) {
            case "ADMIN_USER_CREATE" -> "执行新增用户：" + target;
            case "ADMIN_USER_UPDATE" -> "执行修改用户：" + target;
            case "ADMIN_USER_ASSIGN_ROLE" -> "执行角色授权：" + target;
            case "ADMIN_USER_DISABLE" -> "执行停用用户：" + target;
            case "ADMIN_USER_ENABLE" -> "执行启用用户：" + target;
            case "ADMIN_USER_RESET_PASSWORD" -> "执行重置密码：" + target;
            case "ADMIN_USER_SET_PERSON_LEVEL" -> "执行调整密级：" + target;
            default -> "执行用户操作：" + target;
        };
    }

    private String normalizeSummaryTarget(String summary, String label) {
        if (StringUtils.isBlank(summary) || StringUtils.isBlank(label)) {
            return summary;
        }
        String trimmedSummary = summary.trim();
        if (trimmedSummary.endsWith(label)) {
            return trimmedSummary;
        }
        int idx = trimmedSummary.lastIndexOf('：');
        if (idx >= 0) {
            if (idx == trimmedSummary.length() - 1) {
                return trimmedSummary + label;
            }
            return trimmedSummary.substring(0, idx + 1) + label;
        }
        idx = trimmedSummary.lastIndexOf(':');
        if (idx >= 0) {
            if (idx == trimmedSummary.length() - 1) {
                return trimmedSummary + label;
            }
            return trimmedSummary.substring(0, idx + 1) + label;
        }
        return trimmedSummary + "：" + label;
    }

    private OperationContext resolveApprovalOperationContext(ApprovalAuditCollector collector, AuditOperationType fallbackType) {
        AuditOperationType effectiveType = fallbackType != null ? fallbackType : AuditOperationType.UNKNOWN;
        String effectiveText = effectiveType.getDisplayName();
        AdminAuditOperation descriptor = null;
        if (collector != null) {
            descriptor = collector
                .primaryOperationCode()
                .flatMap(AdminAuditOperation::fromCode)
                .orElse(null);
            if (descriptor != null) {
                effectiveType = descriptor.type();
                effectiveText = descriptor.type().getDisplayName();
            }
        }
        if (effectiveType == null) {
            effectiveType = AuditOperationType.UNKNOWN;
        }
        if (StringUtils.isBlank(effectiveText)) {
            effectiveText = effectiveType.getDisplayName();
        }
        return new OperationContext(effectiveType, effectiveText, descriptor);
    }

    private AuditOperationKind mapOperationKind(AuditOperationType type) {
        if (type == null) {
            return AuditOperationKind.OTHER;
        }
        return switch (type) {
            case CREATE -> AuditOperationKind.CREATE;
            case UPDATE -> AuditOperationKind.UPDATE;
            case DELETE -> AuditOperationKind.DELETE;
            case READ, LIST -> AuditOperationKind.QUERY;
            case LOGIN -> AuditOperationKind.LOGIN;
            case LOGOUT -> AuditOperationKind.LOGOUT;
            case EXPORT -> AuditOperationKind.EXPORT;
            case IMPORT -> AuditOperationKind.IMPORT;
            case EXECUTE, TEST -> AuditOperationKind.EXECUTE;
            case GRANT -> AuditOperationKind.GRANT;
            case REVOKE -> AuditOperationKind.REVOKE;
            case ENABLE -> AuditOperationKind.ENABLE;
            case DISABLE -> AuditOperationKind.DISABLE;
            case APPROVE -> AuditOperationKind.APPROVE;
            case REJECT -> AuditOperationKind.REJECT;
            case PUBLISH -> AuditOperationKind.PUBLISH;
            case REFRESH -> AuditOperationKind.EXECUTE;
            case UPLOAD -> AuditOperationKind.UPLOAD;
            case DOWNLOAD -> AuditOperationKind.DOWNLOAD;
            case CLEAN -> AuditOperationKind.DELETE;
            case ARCHIVE -> AuditOperationKind.ARCHIVE;
            case REQUEST -> AuditOperationKind.OTHER;
            case UNKNOWN -> AuditOperationKind.OTHER;
        };
    }

    private record OperationContext(
        AuditOperationType operationType,
        String operationTypeText,
        AdminAuditOperation descriptor
    ) {}

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }


    private void attachChangeRequestSnapshots(Map<String, Object> detail, Set<Long> changeRequestIds) {
        if (detail == null || changeRequestIds == null || changeRequestIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> snapshotEntries = new ArrayList<>();
        for (Long crId : changeRequestIds) {
            if (crId == null || crId <= 0) {
                continue;
            }
            changeRequestRepository
                .findById(crId)
                .ifPresent(cr -> {
                    ChangeSnapshot snapshot = ChangeSnapshot.fromJson(cr.getDiffJson(), objectMapper);
                    if (snapshot != null && (snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty())) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("changeRequestId", crId);
                        entry.put("changeRequestRef", changeRequestRefFrom(crId));
                        entry.put("resourceType", cr.getResourceType());
                        entry.put("snapshot", snapshot.toMap());
                        List<Map<String, String>> summary = changeSnapshotFormatter.format(snapshot, cr.getResourceType());
                        if (!summary.isEmpty()) {
                            entry.put("summary", summary);
                        }
                        snapshotEntries.add(entry);
                    }
                });
        }
        if (snapshotEntries.isEmpty()) {
            return;
        }
        if (snapshotEntries.size() == 1) {
            Map<String, Object> single = snapshotEntries.get(0);
            detail.putIfAbsent("changeRequestId", single.get("changeRequestId"));
            if (single.containsKey("changeRequestRef")) {
                detail.putIfAbsent("changeRequestRef", single.get("changeRequestRef"));
            }
            detail.putIfAbsent("changeSnapshot", single.get("snapshot"));
            if (single.containsKey("summary")) {
                detail.putIfAbsent("changeSummary", single.get("summary"));
            }
        } else if (!detail.containsKey("changeSnapshots")) {
            detail.put("changeSnapshots", snapshotEntries);
            List<Map<String, String>> combinedSummary = new ArrayList<>();
            for (Map<String, Object> entry : snapshotEntries) {
                Object summaryObj = entry.get("summary");
                if (summaryObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            Map<String, String> converted = new LinkedHashMap<>();
                            map.forEach((k, v) -> {
                                if (k != null && v != null) {
                                    converted.put(String.valueOf(k), String.valueOf(v));
                                }
                            });
                            if (!converted.isEmpty()) {
                                if (entry.get("changeRequestRef") != null) {
                                    converted.putIfAbsent("source", String.valueOf(entry.get("changeRequestRef")));
                                }
                                combinedSummary.add(converted);
                            }
                        }
                    }
                }
            }
            if (!combinedSummary.isEmpty()) {
                detail.put("changeSummaries", combinedSummary);
            }
        }
    }

    public ApprovalDTOs.ApprovalRequestDetail reject(long id, String approver, String note, String clientIp) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Long primaryChangeRequestId = changeRequestIds.stream().findFirst().orElse(null);
        String changeRequestRef = changeRequestRefFrom(primaryChangeRequestId);
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.REJECTED.name());
        approval.setDecidedAt(now);
        approval.setApprover(approver);
        approval.setDecisionNote(note);
        approvalRepository.save(approval);
        ApprovalAuditCollector snapshotCollector = collectApprovalSnapshot(approval);
        recordApprovalDecisionEntry(
            approver,
            snapshotCollector,
            changeRequestRef,
            id,
            primaryChangeRequestId,
            changeRequestIds,
            note,
            approval,
            "REJECTED",
            "REJECTED",
            AuditOperationType.REJECT,
            AuditOperationType.REJECT.getDisplayName(),
            clientIp
        );
        updateChangeRequestStatus(changeRequestIds, ApprovalStatus.REJECTED.name(), approver, now, null);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail delay(long id, String approver, String note, String clientIp) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Long primaryChangeRequestId = changeRequestIds.stream().findFirst().orElse(null);
        String changeRequestRef = changeRequestRefFrom(primaryChangeRequestId);
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.PROCESSING.name());
        approval.setDecidedAt(now);
        approval.setApprover(approver);
        approval.setDecisionNote(note);
        approvalRepository.save(approval);
        ApprovalAuditCollector snapshotCollector = collectApprovalSnapshot(approval);
        recordApprovalDecisionEntry(
            approver,
            snapshotCollector,
            changeRequestRef,
            id,
            primaryChangeRequestId,
            changeRequestIds,
            note,
            approval,
            "PROCESSING",
            "PROCESSING",
            AuditOperationType.REQUEST,
            "待定",
            clientIp
        );
        updateChangeRequestStatus(changeRequestIds, ApprovalStatus.PROCESSING.name(), approver, now, null);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitResetPassword(
        String username,
        String password,
        boolean temporary,
        String requester,
        String ip
    ) {
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "RESET_PASSWORD");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "resetPassword");
        payload.put("username", username);
        payload.put("password", password);
        payload.put("temporary", temporary);
        payload.put("keycloakId", snapshot.getKeycloakId());
        ChangeRequest changeRequest = createChangeRequest(
            "USER",
            "RESET_PASSWORD",
            username,
            payload,
            null,
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_RESET_PASSWORD_REQUEST", username, ip, Map.of("temporary", temporary), changeRequest);
        return toDetailDto(approval);
    }

    private void ensurePending(AdminApprovalRequest approval) {
        if (!ApprovalStatus.PENDING.name().equals(approval.getStatus())) {
            throw new IllegalStateException("审批请求状态已变更，无法执行该操作");
        }
    }

    private void applyApproval(AdminApprovalRequest approval, String accessToken, String actor) throws Exception {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("缺少授权令牌，无法执行审批操作");
        }
        for (AdminApprovalItem item : approval.getItems()) {
            Map<String, Object> payload = readPayload(item.getPayloadJson());
            String action = stringValue(payload.get("action"));
            if (action == null) {
                throw new IllegalStateException("审批项缺少 action 字段");
            }
            switch (action) {
                case "create" -> applyCreate(payload, accessToken, actor);
                case "update" -> applyUpdate(payload, accessToken, actor);
                case "delete" -> applyDelete(payload, accessToken, actor);
                case "grantRoles" -> applyGrantRoles(payload, accessToken, actor);
                case "revokeRoles" -> applyRevokeRoles(payload, accessToken, actor);
                case "setEnabled" -> applySetEnabled(payload, accessToken, actor);
                case "setPersonLevel" -> applySetPersonLevel(payload, accessToken, actor);
                case "resetPassword" -> applyResetPassword(payload, accessToken, actor);
                default -> throw new IllegalStateException("未支持的审批操作: " + action);
            }
        }
    }

    private ChangeRequest createChangeRequest(
        String resourceType,
        String action,
        String resourceId,
        Map<String, Object> payload,
        Map<String, Object> before,
        String reason
    ) {
        Map<String, Object> afterPayload = sanitizeChangePayload(payload);
        Map<String, Object> beforePayload = before == null ? null : sanitizeChangePayload(before);
        return changeRequestService.draft(resourceType, action, resourceId, afterPayload, beforePayload, reason);
    }

    private Map<String, Object> sanitizeChangePayload(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if ("action".equals(key) || key.startsWith("current") || "changeRequestId".equals(key)) {
                continue;
            }
            copy.put(key, sanitizeValue(key, entry.getValue()));
        }
        return copy;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if ("password".equalsIgnoreCase(key)) {
            return "******";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                nested.put(nestedKey, sanitizeValue(nestedKey, entry.getValue()));
            }
            return nested;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> sanitizeValue(key, item)).collect(Collectors.toList());
        }
        return value;
    }

    private List<String> copyList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }

    private List<String> mergeRoles(List<String> current, List<String> delta, boolean adding) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(sanitizeRealmRoleList(current));
        List<String> deltaSanitized = sanitizeRealmRoleList(delta);
        if (!deltaSanitized.isEmpty()) {
            if (adding) {
                merged.addAll(deltaSanitized);
            } else {
                for (String role : deltaSanitized) {
                    if (role != null) {
                        merged.remove(role);
                    }
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private Set<Long> extractChangeRequestIds(AdminApprovalRequest approval) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AdminApprovalItem item : approval.getItems()) {
            if (item == null || item.getPayloadJson() == null) {
                continue;
            }
            try {
                Map<String, Object> payload = readPayload(item.getPayloadJson());
                Long id = toLong(payload.get("changeRequestId"));
                if (id != null) {
                    ids.add(id);
                }
            } catch (JsonProcessingException ignored) {
            }
        }
        return ids;
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

    private void updateChangeRequestStatus(
        Set<Long> changeRequestIds,
        String status,
        String approver,
        Instant decidedAt,
        String errorMessage
    ) {
        if (changeRequestIds == null || changeRequestIds.isEmpty()) {
            return;
        }
        Instant timestamp = decidedAt == null ? Instant.now() : decidedAt;
        for (Long id : changeRequestIds) {
            changeRequestRepository
                .findById(id)
                .ifPresent(cr -> {
                    cr.setStatus(status);
                    if (ApprovalStatus.PENDING.name().equals(status)) {
                        cr.setDecidedBy(null);
                        cr.setDecidedAt(null);
                    } else {
                        cr.setDecidedBy(approver);
                        cr.setDecidedAt(timestamp);
                    }
                    cr.setLastError(errorMessage);
                    changeRequestRepository.save(cr);
                });
        }
    }

    private void scheduleRetry(AdminApprovalRequest approval, String note, String errorMessage) {
        approval.setStatus(ApprovalStatus.PENDING.name());
        approval.setDecidedAt(null);
        approval.setApprover(null);
        approval.setDecisionNote(note);
        approval.setErrorMessage(errorMessage);
        Integer current = approval.getRetryCount() == null ? 0 : approval.getRetryCount();
        approval.setRetryCount(current + 1);
    }

    private String resolveManagementToken() {
        if (StringUtils.isBlank(managementClientId)) {
            throw new IllegalStateException("缺少 Keycloak 管理客户端 ID 配置，无法通过 Service Account 执行审批操作");
        }
        if (StringUtils.isBlank(managementClientSecret)) {
            throw new IllegalStateException("缺少 Keycloak 管理客户端密钥配置，无法通过 Service Account 执行审批操作");
        }

        try {
            var tokenResponse = keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new IllegalStateException("Keycloak 管理客户端未返回 access_token");
            }
            LOG.info("Using client credentials token for Keycloak admin operations (clientId={})", managementClientId);
            return tokenResponse.accessToken();
        } catch (Exception ex) {
            throw new IllegalStateException("获取 Keycloak 管理客户端访问令牌失败: " + ex.getMessage(), ex);
        }
    }

    public void syncRealmRole(String roleName, String scope, Set<String> operations) {
        syncRealmRole(roleName, scope, operations, null, Collections.emptyMap());
    }

    public void syncRealmRole(String roleName, String scope, Set<String> operations, String description) {
        syncRealmRole(roleName, scope, operations, description, Collections.emptyMap());
    }

    public void syncRealmRole(String roleName, String scope, Set<String> operations, String description, Map<String, String> additionalAttributes) {
        if (StringUtils.isBlank(roleName)) {
            return;
        }
        String normalizedRole = roleName.trim();
        KeycloakRoleDTO dto = new KeycloakRoleDTO();
        dto.setName(normalizedRole);
        String computed = buildRoleDescription(scope, operations);
        String desc = StringUtils.isNotBlank(description) ? description.trim() : computed;
        dto.setDescription(desc);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(scope)) {
            attributes.put("scope", scope.trim().toUpperCase(Locale.ROOT));
        }
        if (operations != null && !operations.isEmpty()) {
            attributes.put(
                "operations",
                operations.stream().map(op -> op.toLowerCase(Locale.ROOT)).collect(Collectors.joining(","))
            );
        }
        if (StringUtils.isNotBlank(description)) {
            attributes.put("description", desc);
        }
        if (additionalAttributes != null && !additionalAttributes.isEmpty()) {
            additionalAttributes.forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                    attributes.put(key.trim(), value.trim());
                }
            });
        }
        if (!attributes.isEmpty()) {
            dto.setAttributes(attributes);
        }
        try {
            String token = resolveManagementToken();
            // Backward-compatible update: if role with given name not found, also try ROLE_ prefix
            String nameToUse = dto.getName();
            try {
                if (keycloakAdminClient.findRealmRole(nameToUse, token).isEmpty()) {
                    String alt = nameToUse.startsWith("ROLE_") ? nameToUse.substring(5) : ("ROLE_" + nameToUse);
                    if (keycloakAdminClient.findRealmRole(alt, token).isPresent()) {
                        dto.setName(alt);
                        nameToUse = alt;
                    }
                }
            } catch (Exception ignored) {}
            keycloakAdminClient.upsertRealmRole(dto, token);
            LOG.info("Synchronized realm role {} to Keycloak (scope={}, ops={}, hasCustomDesc={})", nameToUse, scope, operations, StringUtils.isNotBlank(desc));
        } catch (Exception ex) {
            LOG.warn("Failed to synchronize realm role {}: {}", normalizedRole, ex.getMessage());
        }
    }

    public List<KeycloakRoleDTO> listRealmRoles() {
        try {
            String token = resolveManagementToken();
            return keycloakAdminClient
                .listRealmRoles(token)
                .stream()
                .filter(dto -> dto != null && !isKeycloakDefaultRealmRole(dto.getName()))
                .toList();
        } catch (Exception ex) {
            LOG.warn("Failed to list Keycloak realm roles: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Count users who currently have the given Keycloak realm role.
     * Returns 0 if the role does not exist or on any error.
     */
    public int countUsersByRealmRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return 0;
        }
        try {
            String token = resolveManagementToken();
            return keycloakAdminClient.listUsersByRealmRole(roleName.trim(), token).size();
        } catch (Exception ex) {
            LOG.warn("Failed to count users by realm role {}: {}", roleName, ex.getMessage());
            return 0;
        }
    }

    /**
     * Check if a Keycloak realm role exists.
     */
    public boolean realmRoleExists(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        try {
            String token = resolveManagementToken();
            return keycloakAdminClient.findRealmRole(roleName.trim(), token).isPresent();
        } catch (Exception ex) {
            LOG.warn("Failed to check realm role exists {}: {}", roleName, ex.getMessage());
            return false;
        }
    }

    /**
     * Remove a Keycloak realm role from all users and delete the role.
     * Safe no-op if the role does not exist.
     */
    public void deleteRealmRoleAndRemoveFromUsers(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return;
        }
        String name = roleName.trim();
        try {
            String token = resolveManagementToken();
            List<com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO> users = keycloakAdminClient.listUsersByRealmRole(name, token);
            if (users != null && !users.isEmpty()) {
                List<String> single = java.util.List.of(name);
                for (var u : users) {
                    try {
                        if (u.getId() != null) {
                            keycloakAdminClient.removeRealmRolesFromUser(u.getId(), single, token);
                        }
                    } catch (Exception ex) {
                        LOG.warn("Failed to remove role {} from user {}: {}", name, u.getUsername(), ex.getMessage());
                    }
                }
            }
            try {
                keycloakAdminClient.deleteRealmRole(name, token);
            } catch (Exception ex) {
                LOG.warn("Failed to delete realm role {}: {}", name, ex.getMessage());
            }
        } catch (Exception ex) {
            LOG.warn("deleteRealmRoleAndRemoveFromUsers failed for {}: {}", name, ex.getMessage());
        }
    }

    public Optional<KeycloakRoleDTO> findRealmRoleDetail(String roleName) {
        if (StringUtils.isBlank(roleName)) {
            return Optional.empty();
        }
        List<String> candidates = roleNameCandidates(roleName);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        try {
            String token = resolveManagementToken();
            for (String candidate : candidates) {
                try {
                    Optional<KeycloakRoleDTO> found = keycloakAdminClient.findRealmRole(candidate, token);
                    if (found.isPresent()) {
                        KeycloakRoleDTO dto = found.orElseThrow();
                        if (dto.getAttributes() == null) {
                            dto.setAttributes(new java.util.LinkedHashMap<>());
                        }
                        return Optional.of(dto);
                    }
                } catch (Exception ex) {
                    LOG.debug("Failed to resolve Keycloak role {} via candidate {}: {}", roleName, candidate, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to resolve Keycloak role {}: {}", roleName, ex.getMessage());
        }
        return Optional.empty();
    }

    public List<KeycloakUserDTO> listRealmRoleMembersDetail(String roleName) {
        if (StringUtils.isBlank(roleName)) {
            return List.of();
        }
        Optional<KeycloakRoleDTO> resolved = findRealmRoleDetail(roleName);
        if (resolved.isEmpty()) {
            return List.of();
        }
        KeycloakRoleDTO resolvedRole = resolved.orElseThrow();
        String actualName = firstNonBlank(resolvedRole.getName(), roleName.trim());
        if (StringUtils.isBlank(actualName)) {
            return List.of();
        }
        try {
            String token = resolveManagementToken();
            List<KeycloakUserDTO> users = keycloakAdminClient.listUsersByRealmRole(actualName, token);
            return users == null ? List.of() : users;
        } catch (Exception ex) {
            LOG.warn("Failed to list Keycloak users for role {}: {}", actualName, ex.getMessage());
            return List.of();
        }
    }

    private List<String> roleNameCandidates(String raw) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (!trimmed.isEmpty()) {
            candidates.add(trimmed);
        }
        String canonical = canonicalRoleName(trimmed);
        if (StringUtils.isNotBlank(canonical)) {
            candidates.add(canonical);
            candidates.add("ROLE_" + canonical);
        }
        return new ArrayList<>(candidates);
    }

    private String canonicalRoleName(String raw) {
        if (StringUtils.isBlank(raw)) {
            return raw;
        }
        String upper = raw.trim().replace('-', '_');
        upper = upper.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) {
            return upper.substring(5);
        }
        return upper;
    }

    private String buildRoleDescription(String scope, Set<String> operations) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(scope)) {
            builder.append(scope.toUpperCase(Locale.ROOT));
        }
        if (operations != null && !operations.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("ops=").append(operations.stream().map(op -> op.toLowerCase(Locale.ROOT)).collect(Collectors.joining("/")));
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private Map<String, Object> readPayload(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, MAP_TYPE);
    }

    private void applyCreate(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_CREATE_EXECUTE";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        try {
            KeycloakUserDTO dto = toUserDto(payload);
            ensureAllowedSecurityLevel(extractPersonLevel(dto), "人员密级不允许为非密");
            List<String> requestedGroupPaths = normalizeGroupPathList(stringList(payload.get("groupPaths")));
            if (!requestedGroupPaths.isEmpty()) {
                dto.setGroups(new ArrayList<>(requestedGroupPaths));
                ensureDeptCodeAttribute(dto, requestedGroupPaths);
            }
            Optional<KeycloakUserDTO> existing = keycloakAdminClient.findByUsername(dto.getUsername(), accessToken);
            KeycloakUserDTO target;
            if (existing.isPresent()) {
                KeycloakUserDTO current = existing.orElseThrow();
                String keycloakId = current.getId();
                if (keycloakId != null && !keycloakId.isBlank()) {
                    dto.setId(keycloakId);
                    try {
                        target = keycloakAdminClient.updateUser(keycloakId, dto, accessToken);
                        LOG.info("Keycloak user {} already existed; attributes updated", dto.getUsername());
                    } catch (RuntimeException ex) {
                        LOG.warn("Failed to update existing Keycloak user {}, fallback to create: {}", dto.getUsername(), ex.getMessage());
                        dto.setId(null);
                        target = keycloakAdminClient.createUser(dto, accessToken);
                    }
                } else {
                    dto.setId(null);
                    try {
                        target = keycloakAdminClient.createUser(dto, accessToken);
                    } catch (RuntimeException exCreate) {
                        // Some realms enforce strict User Profile validations that may reject
                        // rich attribute payloads with opaque 'unknown_error'. Try a minimal
                        // creation first, then patch attributes via update as a fallback.
                        LOG.warn("Create user failed with full payload for {}: {}. Retrying with minimal representation", dto.getUsername(), exCreate.getMessage());
                        com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO minimal = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO();
                        minimal.setUsername(dto.getUsername());
                        minimal.setEmail(dto.getEmail());
                        minimal.setFirstName(dto.getFullName() != null && !dto.getFullName().isBlank() ? dto.getFullName() : dto.getFirstName());
                        minimal.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
                        try {
                            com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO created = keycloakAdminClient.createUser(minimal, accessToken);
                            // Patch back full attributes (best-effort)
                            created = keycloakAdminClient.updateUser(created.getId(), dto, accessToken);
                            target = created;
                        } catch (RuntimeException exMinimal) {
                            LOG.warn("Minimal user creation fallback failed for {}: {}", dto.getUsername(), exMinimal.getMessage());
                            throw exMinimal;
                        }
                    }
                }
            } else {
                try {
                    target = keycloakAdminClient.createUser(dto, accessToken);
                } catch (RuntimeException exCreate) {
                    LOG.warn("Create user failed with full payload for {}: {}. Retrying with minimal representation", dto.getUsername(), exCreate.getMessage());
                    com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO minimal = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO();
                    minimal.setUsername(dto.getUsername());
                    minimal.setEmail(dto.getEmail());
                    minimal.setFirstName(dto.getFullName() != null && !dto.getFullName().isBlank() ? dto.getFullName() : dto.getFirstName());
                    minimal.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
                    try {
                        com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO created = keycloakAdminClient.createUser(minimal, accessToken);
                        created = keycloakAdminClient.updateUser(created.getId(), dto, accessToken);
                        target = created;
                    } catch (RuntimeException exMinimal) {
                        LOG.warn("Minimal user creation fallback failed for {}: {}", dto.getUsername(), exMinimal.getMessage());
                        throw exMinimal;
                    }
                }
            }
            if (target == null || target.getId() == null || target.getId().isBlank()) {
                throw new IllegalStateException("Keycloak 未返回用户标识，无法设置默认口令");
            }
            try {
                keycloakAdminClient.resetPassword(target.getId(), DEFAULT_INITIAL_PASSWORD, false, accessToken);
                detail.put("defaultPasswordApplied", true);
            } catch (Exception passwordEx) {
                detail.put("defaultPasswordApplied", false);
                detail.put("defaultPasswordError", passwordEx.getMessage());
                throw new IllegalStateException("设置默认口令失败: " + passwordEx.getMessage(), passwordEx);
            }

            // Apply roles via role-mappings if provided in payload (split into realm/client)
            List<String> requestedRoles = stringList(payload.get("realmRoles"));
            if (requestedRoles != null && !requestedRoles.isEmpty()) {
                LOG.info("Approval applyCreate assign roles username={}, id={}, roles={}", target.getUsername(), target.getId(), requestedRoles);
                try {
                    List<String> realm = new java.util.ArrayList<>();
                    List<String> client = new java.util.ArrayList<>();
                    for (String r : requestedRoles) if (isDataRole(r)) client.add(normalizeRole(r)); else realm.add(normalizeRole(r));
                    if (!realm.isEmpty()) {
                        for (String r : realm) {
                            com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO roleDto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                            roleDto.setName(r);
                            keycloakAdminClient.upsertRealmRole(roleDto, accessToken);
                        }
                        keycloakAdminClient.addRealmRolesToUser(target.getId(), realm, accessToken);
                    }
                    if (!client.isEmpty() && useClientRoles) {
                        for (String r : client) {
                            com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO roleDto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                            roleDto.setName(r);
                            keycloakAdminClient.upsertClientRole(targetClientId, roleDto, accessToken);
                        }
                        keycloakAdminClient.addClientRolesToUser(target.getId(), targetClientId, client, accessToken);
                    }
                    if (!client.isEmpty() && !useClientRoles) {
                        // DB-authority mode: persist data role assignments to admin DB
                        ensureDbAssignments(target.getUsername(), client, target);
                    }
                    List<String> names = keycloakAdminClient.listUserRealmRoles(target.getId(), accessToken);
                    if (names != null && !names.isEmpty()) target.setRealmRoles(new ArrayList<>(names));
                } catch (Exception e) {
                    LOG.warn("Failed to assign roles on create for user {}: {}", target.getUsername(), e.getMessage());
                }
            }
            if (!requestedGroupPaths.isEmpty()) {
                target.setGroups(new ArrayList<>(requestedGroupPaths));
                ensureDeptCodeAttribute(target, requestedGroupPaths);
            }
            refreshUserGroups(target, accessToken);
            AdminKeycloakUser savedEntity = syncSnapshot(target);
            detail.put("keycloakId", target.getId());
            String resolvedTarget = (savedEntity != null && savedEntity.getId() != null)
                ? String.valueOf(savedEntity.getId())
                : (target.getUsername() != null ? target.getUsername() : username);
            auditUserChange(actor, auditAction, resolvedTarget, "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applyUpdate(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_UPDATE_EXECUTE";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            KeycloakUserDTO update = toUserDto(payload);
            boolean groupSpecified = payload.containsKey("groupPaths");
            List<String> requestedGroupPaths;
            if (groupSpecified) {
                requestedGroupPaths = normalizeGroupPathList(stringList(payload.get("groupPaths")));
            } else {
                requestedGroupPaths = normalizeGroupPathList(existing.getGroups());
                if (requestedGroupPaths.isEmpty()) {
                    requestedGroupPaths = userRepository
                        .findByUsernameIgnoreCase(existing.getUsername())
                        .map(AdminKeycloakUser::getGroupPaths)
                        .map(this::normalizeGroupPathList)
                        .orElseGet(ArrayList::new);
                }
            }
            update.setGroups(new ArrayList<>(requestedGroupPaths));
            ensureDeptCodeAttribute(update, requestedGroupPaths);
            ensureAllowedSecurityLevel(extractPersonLevel(update), "人员密级不允许为非密");
            update.setId(existing.getId());
            // If roles present, split to realm/client and apply via role-mappings APIs
            List<String> requestedRoles = stringList(payload.get("realmRoles"));
            if (requestedRoles != null && !requestedRoles.isEmpty()) {
                List<String> realmReq = new java.util.ArrayList<>();
                List<String> clientReq = new java.util.ArrayList<>();
                for (String r : requestedRoles) if (isDataRole(r)) clientReq.add(normalizeRole(r)); else realmReq.add(normalizeRole(r));

                // Realm role delta (best-effort based on existing snapshot)
                List<String> currentRoles = existing.getRealmRoles() == null ? List.of() : existing.getRealmRoles();
                LinkedHashSet<String> cur = new LinkedHashSet<>(currentRoles);
                LinkedHashSet<String> reqRealm = new LinkedHashSet<>(realmReq);
                List<String> toAddRealm = reqRealm.stream().filter(r -> !cur.contains(r)).toList();
                List<String> toRemoveRealm = cur.stream().filter(r -> !reqRealm.contains(r)).toList();
                LOG.info("[applyUpdate] realm roles delta user={} add={} remove={}", existing.getUsername(), toAddRealm, toRemoveRealm);
                if (!toAddRealm.isEmpty()) {
                    for (String r : toAddRealm) {
                        if (r != null && !r.isBlank()) {
                            com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO dto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                            dto.setName(r);
                            keycloakAdminClient.upsertRealmRole(dto, accessToken);
                        }
                    }
                    keycloakAdminClient.addRealmRolesToUser(existing.getId(), toAddRealm, accessToken);
                }
                if (!toRemoveRealm.isEmpty()) {
                    keycloakAdminClient.removeRealmRolesFromUser(existing.getId(), toRemoveRealm, accessToken);
                }

                // Client roles apply (no snapshot delta here; assign requested set)
                if (!clientReq.isEmpty() && useClientRoles) {
                    for (String r : clientReq) {
                        com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO dto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                        dto.setName(r);
                        try { keycloakAdminClient.upsertClientRole(targetClientId, dto, accessToken); } catch (RuntimeException ignored) {}
                    }
                    keycloakAdminClient.addClientRolesToUser(existing.getId(), targetClientId, clientReq, accessToken);
                }
                if (!clientReq.isEmpty() && !useClientRoles) {
                    // DB-authority mode: upsert requested data role assignments
                    ensureDbAssignments(existing.getUsername(), clientReq, existing);
                }
                // DB-authority mode: reflect removal of data roles by deleting assignments not requested anymore
                if (!useClientRoles) {
                    try {
                        java.util.Set<String> requestedData = new java.util.HashSet<>(clientReq);
                        java.util.List<com.yuzhi.dts.admin.domain.AdminRoleAssignment> currentAssignments = roleAssignRepo.findByUsernameIgnoreCase(existing.getUsername());
                        for (var a : currentAssignments) {
                            String role = a.getRole();
                            if (role != null) {
                                String norm = normalizeRole(role);
                                if (isDataRole(norm) && !requestedData.contains(norm)) {
                                    roleAssignRepo.deleteByUsernameIgnoreCaseAndRoleIgnoreCase(existing.getUsername(), role);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                // Avoid including roles in representation update
                update.setRealmRoles(new ArrayList<>());
            }
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), update, accessToken);
            updated.setGroups(new ArrayList<>(requestedGroupPaths));
            ensureDeptCodeAttribute(updated, requestedGroupPaths);
            // refresh role names from role-mappings to keep snapshot accurate
            try {
                List<String> names = keycloakAdminClient.listUserRealmRoles(existing.getId(), accessToken);
                if (names != null && !names.isEmpty()) {
                    updated.setRealmRoles(new ArrayList<>(names));
                }
            } catch (Exception ignored) {}
            refreshUserGroups(updated, accessToken);
            AdminKeycloakUser updatedEntity = syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            String targetId = (updatedEntity != null && updatedEntity.getId() != null)
                ? String.valueOf(updatedEntity.getId())
                : existing.getUsername();
            auditUserChange(actor, auditAction, targetId, "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applyDelete(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_DELETE_EXECUTE";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            keycloakAdminClient.deleteUser(existing.getId(), accessToken);
            Long pkId = userRepository
                .findByUsernameIgnoreCase(existing.getUsername())
                .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::getId)
                .orElse(null);
            boolean removedSnapshot = userRepository.findByUsernameIgnoreCase(existing.getUsername()).isPresent();
            userRepository.findByUsernameIgnoreCase(existing.getUsername()).ifPresent(userRepository::delete);
            detail.put("keycloakId", existing.getId());
            detail.put("snapshotRemoved", removedSnapshot);
            auditUserChange(actor, auditAction, pkId != null ? String.valueOf(pkId) : existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applyGrantRoles(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_GRANT_ROLE_EXECUTE";
        List<String> rolesToAdd = stringList(payload.get("roles"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("roles", rolesToAdd);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            LOG.info("Approval applyGrantRoles username={}, id={}, roles={}", existing.getUsername(), existing.getId(), rolesToAdd);
            if (!rolesToAdd.isEmpty()) {
                List<String> realm = new java.util.ArrayList<>();
                List<String> client = new java.util.ArrayList<>();
                for (String r : rolesToAdd) if (isDataRole(r)) client.add(normalizeRole(r)); else realm.add(normalizeRole(r));
                if (!realm.isEmpty()) {
                    for (String r : realm) {
                        if (r == null || r.isBlank()) continue;
                        com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO dto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                        dto.setName(r);
                        try { keycloakAdminClient.upsertRealmRole(dto, accessToken); } catch (RuntimeException ex) { LOG.warn("Upsert realm role '{}' failed before assignment: {}", r, ex.getMessage()); }
                    }
                    keycloakAdminClient.addRealmRolesToUser(existing.getId(), realm, accessToken);
                }
                if (!client.isEmpty() && useClientRoles) {
                    for (String r : client) {
                        if (r == null || r.isBlank()) continue;
                        com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO dto = new com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO();
                        dto.setName(r);
                        try { keycloakAdminClient.upsertClientRole(targetClientId, dto, accessToken); } catch (RuntimeException ex) { LOG.warn("Upsert client role '{}' failed before assignment: {}", r, ex.getMessage()); }
                    }
                    keycloakAdminClient.addClientRolesToUser(existing.getId(), targetClientId, client, accessToken);
                }
                if (!client.isEmpty() && !useClientRoles) {
                    // DB-authority mode: upsert data role assignments
                    ensureDbAssignments(existing.getUsername(), client, existing);
                }
            }
            KeycloakUserDTO updated = keycloakAdminClient.findById(existing.getId(), accessToken).orElse(existing);
            try {
                List<String> names = keycloakAdminClient.listUserRealmRoles(existing.getId(), accessToken);
                if (names != null && !names.isEmpty()) {
                    updated.setRealmRoles(new ArrayList<>(names));
                }
            } catch (Exception ignored) {}
            refreshUserGroups(updated, accessToken);
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            detail.put("resultRoles", updated.getRealmRoles());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applyRevokeRoles(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_REVOKE_ROLE_EXECUTE";
        List<String> remove = stringList(payload.get("roles"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("removedRoles", remove);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            LOG.info("Approval applyRevokeRoles username={}, id={}, roles={}", existing.getUsername(), existing.getId(), remove);
            if (!remove.isEmpty()) {
                List<String> realmRm = new java.util.ArrayList<>();
                List<String> clientRm = new java.util.ArrayList<>();
                for (String r : remove) if (isDataRole(r)) clientRm.add(normalizeRole(r)); else realmRm.add(normalizeRole(r));
                if (!realmRm.isEmpty()) keycloakAdminClient.removeRealmRolesFromUser(existing.getId(), realmRm, accessToken);
                if (!clientRm.isEmpty() && useClientRoles) {
                    keycloakAdminClient.removeClientRolesFromUser(existing.getId(), targetClientId, clientRm, accessToken);
                }
                if (!clientRm.isEmpty() && !useClientRoles) {
                    try {
                        for (String r : clientRm) {
                            if (r == null || r.isBlank()) continue;
                            roleAssignRepo.deleteByUsernameIgnoreCaseAndRoleIgnoreCase(existing.getUsername(), r);
                        }
                    } catch (Exception ignored) {}
                }
            }
            KeycloakUserDTO updated = keycloakAdminClient.findById(existing.getId(), accessToken).orElse(existing);
            try {
                List<String> names = keycloakAdminClient.listUserRealmRoles(existing.getId(), accessToken);
                updated.setRealmRoles(new ArrayList<>(names));
            } catch (Exception ignored) {}
            refreshUserGroups(updated, accessToken);
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            detail.put("resultRoles", updated.getRealmRoles());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applySetEnabled(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        String auditAction = enabled ? "USER_ENABLE_EXECUTE" : "USER_DISABLE_EXECUTE";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("enabled", enabled);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            existing.setEnabled(enabled);
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
            refreshUserGroups(updated, accessToken);
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applySetPersonLevel(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_SET_PERSON_LEVEL_EXECUTE";
        String level = stringValue(payload.get("personSecurityLevel"));
        String normalizedLevel = normalizeSecurityLevel(level);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("personLevel", normalizedLevel == null ? level : normalizedLevel);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            Map<String, List<String>> attributes = existing.getAttributes() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing.getAttributes());
            if (normalizedLevel != null) {
                attributes.put("person_level", List.of(normalizedLevel));
                attributes.put("person_security_level", List.of(normalizedLevel));
            }
            attributes.remove("data_levels");
            existing.setAttributes(attributes);
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
            refreshUserGroups(updated, accessToken);
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private void applyResetPassword(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        boolean temporary = Boolean.TRUE.equals(payload.get("temporary"));
        String auditAction = "USER_RESET_PASSWORD_EXECUTE";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("temporary", temporary);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            String password = stringValue(payload.get("password"));
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("审批载荷缺少密码信息");
            }
            keycloakAdminClient.resetPassword(existing.getId(), password, temporary, accessToken);
            detail.put("keycloakId", existing.getId());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILED", detail);
            throw ex;
        }
    }

    private KeycloakUserDTO locateUser(String username, String keycloakId, String accessToken) {
        if (keycloakId != null && !keycloakId.isBlank()) {
            return keycloakAdminClient
                .findById(keycloakId, accessToken)
                .orElseThrow(() -> new IllegalStateException("无法在 Keycloak 中找到用户: " + keycloakId));
        }
        if (username != null && !username.isBlank()) {
            return keycloakAdminClient
                .findByUsername(username, accessToken)
                .orElseThrow(() -> new IllegalStateException("无法在 Keycloak 中找到用户: " + username));
        }
        throw new IllegalStateException("审批载荷缺少用户标识");
    }

    private KeycloakUserDTO toUserDto(Map<String, Object> payload) {
        KeycloakUserDTO dto = new KeycloakUserDTO();
        dto.setId(stringValue(payload.get("keycloakId")));
        dto.setUsername(stringValue(payload.get("username")));
        dto.setEmail(stringValue(payload.get("email")));
        String fullName = stringValue(payload.get("fullName"));
        if ((fullName == null || fullName.isBlank()) && payload.get("firstName") != null) {
            fullName = stringValue(payload.get("firstName"));
        }
        dto.setFullName(fullName);
        dto.setFirstName(fullName);
        dto.setLastName(null);
        dto.setEnabled(booleanValue(payload.get("enabled")));
        dto.setEmailVerified(booleanValue(payload.get("emailVerified")));
        dto.setRealmRoles(new ArrayList<>(stringList(payload.get("realmRoles"))));
        dto.setGroups(new ArrayList<>(stringList(payload.get("groupPaths"))));
        Map<String, List<String>> attributes = stringListMap(payload.get("attributes"));
        if (fullName != null && !fullName.isBlank()) {
            attributes.put("fullName", List.of(fullName));
        }
        String phone = stringValue(payload.get("phone"));
        if (phone != null && !phone.isBlank()) {
            attributes.put("phone", List.of(phone));
        }
        String personLevel = stringValue(payload.get("personSecurityLevel"));
        String normalizedPersonLevel = normalizeSecurityLevel(personLevel);
        if (normalizedPersonLevel != null && !normalizedPersonLevel.isBlank()) {
            // Keep both keys for compatibility with realm mappers and local extraction logic
            attributes.put("person_level", List.of(normalizedPersonLevel));
            attributes.put("person_security_level", List.of(normalizedPersonLevel));
        }
        // Ensure dept_code present when department is selected; prefer explicit value.
        // Do NOT derive from group path leaf (legacy behavior) to avoid persisting names.
        if (!attributes.containsKey("dept_code")) {
            String explicitDept = stringValue(payload.get("deptCode"));
            if (explicitDept != null && !explicitDept.isBlank()) {
                attributes.put("dept_code", List.of(explicitDept));
            }
            // If explicit value not provided, leave unset here. applyCreate/applyUpdate will
            // derive dept_code from the selected group (dts_org_id) using Keycloak metadata.
        }
        dto.setAttributes(attributes);
        return dto;
    }

    private String extractPersonLevel(KeycloakUserDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getAttributes() != null) {
            List<String> levels = dto.getAttributes().get("person_level");
            if (levels != null && !levels.isEmpty()) {
                return normalizeSecurityLevel(levels.get(0));
            }
            List<String> levels2 = dto.getAttributes().get("personnel_security_level");
            if (levels2 != null && !levels2.isEmpty()) {
                return normalizeSecurityLevel(levels2.get(0));
            }
            List<String> levels3 = dto.getAttributes().get("person_security_level");
            if (levels3 != null && !levels3.isEmpty()) {
                return normalizeSecurityLevel(levels3.get(0));
            }
        }
        return null;
    }

    private boolean isDataRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String r = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (r.startsWith("ROLE_")) {
            r = r.substring(5);
        }
        r = r.replace('-', '_');
        return r.startsWith("DEPT_") || r.startsWith("INST_") || "EMPLOYEE".equals(r);
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        r = r.replaceAll("[^A-Z0-9_]", "_").replaceAll("_+", "_");
        return r;
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            List<String> list = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    String v = item.toString().trim();
                    if (!v.isEmpty()) list.add(v);
                }
            }
            return list;
        }
        if (value.getClass().isArray()) {
            List<String> list = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                if (element != null) {
                    String v = element.toString().trim();
                    if (!v.isEmpty()) {
                        list.add(v);
                    }
                }
            }
            return list;
        }
        String text = value.toString();
        if (text.contains(",")) {
            String[] parts = text.split(",");
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            return list;
        }
        return List.of(text.trim());
    }

    public List<String> aggregateRealmRoles(String username, Collection<String> directRoles) {
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        collectRoles(resolved, directRoles, false);
        if (StringUtils.isNotBlank(username)) {
            List<String> localRoles = roleMemberRepo
                .findByUsernameIgnoreCase(username)
                .stream()
                .map(member -> member != null ? member.getRole() : null)
                .filter(Objects::nonNull)
                .toList();
            collectRoles(resolved, localRoles, true);
        }
        return resolved
            .values()
            .stream()
            .filter(v -> v != null && !v.trim().isEmpty())
            .map(String::trim)
            .toList();
    }

    private void collectRoles(Map<String, String> target, Collection<String> roles, boolean canonicalize) {
        if (roles == null) {
            return;
        }
        for (String role : roles) {
            String key = normalizeRoleKey(role);
            if (key == null || target.containsKey(key)) {
                continue;
            }
            String value = canonicalize ? canonicalRoleValue(role) : safeRoleValue(role);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private String safeRoleValue(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String canonicalRoleValue(String role) {
        String base = stripRolePrefix(role);
        if (!StringUtils.isNotBlank(base)) {
            return safeRoleValue(role);
        }
        String normalized = base.replace('-', '_').toUpperCase(Locale.ROOT);
        return "ROLE_" + normalized;
    }

    private String normalizeRoleKey(String role) {
        if (role == null) {
            return null;
        }
        String base = stripRolePrefix(role);
        if (!StringUtils.isNotBlank(base)) {
            return null;
        }
        return base.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private String stripRolePrefix(String role) {
        if (role == null) {
            return null;
        }
        String text = role.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("ROLE_") || text.startsWith("ROLE-")) {
            return text.substring(5);
        }
        if (text.startsWith("ROLE")) {
            return text.substring(4);
        }
        return text;
    }

    private Map<String, List<String>> stringListMap(Object value) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;
                result.put(entry.getKey().toString(), stringList(entry.getValue()));
            }
        }
        normalizeFullNameAttribute(result);
        return result;
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    private void normalizeFullNameAttribute(Map<String, List<String>> attributes) {
        if (attributes == null) {
            return;
        }
        List<String> legacy = attributes.remove("fullname");
        if (legacy != null && !legacy.isEmpty()) {
            List<String> current = attributes.get("fullName");
            List<String> normalized = current == null ? new ArrayList<>() : new ArrayList<>(current);
            for (String value : legacy) {
                if (value == null) continue;
                String trimmed = value.trim();
                if (trimmed.isEmpty()) continue;
                if (normalized.stream().noneMatch(trimmed::equals)) {
                    normalized.add(trimmed);
                }
            }
            if (!normalized.isEmpty()) {
                attributes.put("fullName", normalized);
            }
        }
    }

    private void ensureDbAssignments(String username, List<String> dataRoles, KeycloakUserDTO user) {
        if (username == null || dataRoles == null || dataRoles.isEmpty()) return;
        String display = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : username;
        String sec = extractPersonLevel(user);
        if (sec == null || sec.isBlank()) sec = DEFAULT_PERSON_LEVEL;
        for (String role : new java.util.LinkedHashSet<>(dataRoles)) {
            if (role == null || role.isBlank()) continue;
            String norm = normalizeRole(role);
            if (!isDataRole(norm)) continue;
            // If exists, skip
            java.util.List<AdminRoleAssignment> exists = roleAssignRepo.findByUsernameIgnoreCaseAndRoleIgnoreCase(username, norm);
            if (exists != null && !exists.isEmpty()) continue;
            AdminRoleAssignment a = new AdminRoleAssignment();
            a.setUsername(username);
            a.setRole(norm);
            a.setDisplayName(display);
            a.setUserSecurityLevel(sec);
            a.setScopeOrgId(null);
            a.setDatasetIdsCsv(null);
            a.setOperationsCsv(defaultOpsForRole(norm));
            try { roleAssignRepo.save(a); } catch (Exception ignored) {}
        }
    }

    private String defaultOpsForRole(String roleCode) {
        if (roleCode == null) return "read";
        String r = roleCode.toUpperCase(java.util.Locale.ROOT);
        if (r.endsWith("_OWNER") || r.endsWith("_LEADER")) return "read,write,export";
        if (r.endsWith("_DEV") || r.endsWith("_DATA_DEV")) return "read,write";
        return "read"; // VIEWER and others default to read
    }

    public static class RoleMemberDeltaResult {

        private final List<String> added;
        private final List<String> removed;
        private final Map<String, String> errors;

        public RoleMemberDeltaResult(List<String> added, List<String> removed, Map<String, String> errors) {
            this.added = added == null ? List.of() : List.copyOf(added);
            this.removed = removed == null ? List.of() : List.copyOf(removed);
            this.errors = errors == null ? Map.of() : Map.copyOf(errors);
        }

        public static RoleMemberDeltaResult empty() {
            return new RoleMemberDeltaResult(List.of(), List.of(), Map.of());
        }

        public List<String> getAdded() {
            return added;
        }

        public List<String> getRemoved() {
            return removed;
        }

        public Map<String, String> getErrors() {
            return errors;
        }
    }
}
