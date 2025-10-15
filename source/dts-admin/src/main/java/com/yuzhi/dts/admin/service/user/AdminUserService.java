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
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.service.approval.ApprovalStatus;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminUserService {

    private static final Logger LOG = LoggerFactory.getLogger(AdminUserService.class);

    private static final Set<String> SUPPORTED_SECURITY_LEVELS = Set.of("NONE_SECRET", "NON_SECRET", "GENERAL", "IMPORTANT", "CORE");
    private static final Set<String> FORBIDDEN_SECURITY_LEVELS = Set.of("NONE_SECRET", "NON_SECRET");
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
    private final AdminAuditService auditService;
    private final ChangeRequestService changeRequestService;
    private final ChangeRequestRepository changeRequestRepository;
    private final AdminRoleAssignmentRepository roleAssignRepo;
    private final ObjectMapper objectMapper;
    private final KeycloakAuthService keycloakAuthService;
    private final String managementClientId;
    private final String managementClientSecret;
    private final String targetClientId;
    private final boolean useClientRoles;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";
    private static final String DEFAULT_INITIAL_PASSWORD = "sa";

    public AdminUserService(
        AdminKeycloakUserRepository userRepository,
        AdminApprovalRequestRepository approvalRepository,
        KeycloakAdminClient keycloakAdminClient,
        AdminAuditService auditService,
        ChangeRequestService changeRequestService,
        ChangeRequestRepository changeRequestRepository,
        AdminRoleAssignmentRepository roleAssignRepo,
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
        this.auditService = auditService;
        this.changeRequestService = changeRequestService;
        this.changeRequestRepository = changeRequestRepository;
        this.roleAssignRepo = roleAssignRepo;
        this.objectMapper = objectMapper;
        this.keycloakAuthService = keycloakAuthService;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret;
        this.targetClientId = targetClientId == null ? "dts-system" : targetClientId.trim();
        this.useClientRoles = useClientRoles;
    }

    @Transactional(readOnly = true)
    public Page<AdminKeycloakUser> listSnapshots(int page, int size, String keyword) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username"));
        if (StringUtils.isNotBlank(keyword)) {
            return userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<AdminKeycloakUser> findSnapshotByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
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
                display = username;
            }
            result.put(username, display);
        }
        return result;
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
        recordAudit(requester, "USER_CREATE_REQUEST", username, ip, request);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitUpdate(String username, UserOperationRequest request, String requester, String ip) {
        validateOperation(request, false);
        AdminKeycloakUser snapshot = ensureSnapshot(username);
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
        recordAudit(requester, "USER_UPDATE_REQUEST", username, ip, request);
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
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "GRANT_ROLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "grantRoles");
        payload.put("actionDisplay", actionDisplay("grantRoles"));
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        payload.put("addedRoles", new ArrayList<>(roles));
        payload.put("resultRoles", mergeRoles(snapshot.getRealmRoles(), roles, true));
        // Include keycloakId to avoid KC user search that may trigger FGAP NPEs
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        ChangeRequest changeRequest = createChangeRequest(
            "ROLE",
            "GRANT_ROLE",
            username,
            payload,
            Map.of("roles", copyList(snapshot.getRealmRoles())),
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_GRANT_ROLE_REQUEST", username, ip, Map.of("roles", roles));
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
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, null, "REVOKE_ROLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "revokeRoles");
        payload.put("actionDisplay", actionDisplay("revokeRoles"));
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        payload.put("removedRoles", new ArrayList<>(roles));
        payload.put("resultRoles", mergeRoles(snapshot.getRealmRoles(), roles, false));
        // Include keycloakId to avoid KC user search that may trigger FGAP NPEs
        if (snapshot.getKeycloakId() != null) {
            payload.put("keycloakId", snapshot.getKeycloakId());
        }
        ChangeRequest changeRequest = createChangeRequest(
            "ROLE",
            "REVOKE_ROLE",
            username,
            payload,
            Map.of("roles", copyList(snapshot.getRealmRoles())),
            null
        );
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_REVOKE_ROLE_REQUEST", username, ip, Map.of("roles", roles));
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
        AdminKeycloakUser snapshot = ensureSnapshot(username);
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
        recordAudit(requester, "USER_ENABLE_REQUEST", username, ip, Map.of("enabled", enabled));
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
        AdminKeycloakUser snapshot = ensureSnapshot(username);
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
        recordAudit(requester, "USER_SET_PERSON_LEVEL_REQUEST", username, ip, Map.of("personLevel", personLevel));
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
        payload.put("target", snapshotPayload(snapshot));
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
        payload.put("fullName", request.getFullName());
        payload.put("email", request.getEmail());
        payload.put("phone", request.getPhone());
        String normalizedPersonLevel = normalizeSecurityLevel(request.getPersonSecurityLevel());
        ensureAllowedSecurityLevel(normalizedPersonLevel, "人员密级不允许为非密");
        payload.put("personSecurityLevel", normalizedPersonLevel);
        payload.put("realmRoles", request.getRealmRoles());
        payload.put("groupPaths", request.getGroupPaths());
        payload.put("enabled", request.getEnabled());
        payload.put("reason", request.getReason());
        payload.put("attributes", request.getAttributes());
        return payload;
    }

    private Map<String, Object> snapshotPayload(AdminKeycloakUser snapshot) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", snapshot.getUsername());
        payload.put("fullName", snapshot.getFullName());
        payload.put("email", snapshot.getEmail());
        payload.put("phone", snapshot.getPhone());
        payload.put("personSecurityLevel", snapshot.getPersonSecurityLevel());
        payload.put("realmRoles", snapshot.getRealmRoles());
        payload.put("groupPaths", snapshot.getGroupPaths());
        payload.put("enabled", snapshot.isEnabled());
        payload.put("attributes", Map.of());
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化审批请求", e);
        }
    }

    private void recordAudit(String actor, String action, String target, String ip, Object detailSource) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("payload", detailSource);
        detail.put("ip", ip);
        detail.put("timestamp", Instant.now().toString());
        String code = switch (action) {
            case "USER_CREATE_REQUEST" -> "ADMIN_USER_CREATE";
            case "USER_UPDATE_REQUEST", "USER_SET_PERSON_LEVEL_REQUEST" -> "ADMIN_USER_UPDATE";
            case "USER_GRANT_ROLE_REQUEST", "USER_REVOKE_ROLE_REQUEST" -> "ADMIN_USER_ASSIGN_ROLE";
            case "USER_ENABLE_REQUEST" -> "ADMIN_USER_ENABLE";
            case "USER_DISABLE_REQUEST" -> "ADMIN_USER_DISABLE";
            case "USER_RESET_PASSWORD_REQUEST" -> "ADMIN_USER_RESET_PASSWORD";
            default -> action;
        };
        auditService.recordAction(actor, code, AuditStage.SUCCESS, target, detail);
    }

    private void auditUserChange(String actor, String action, String target, String result, Object detail) {
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
        Map<String, Object> payload;
        if (detail instanceof Map<?, ?> map) {
            payload = new HashMap<>();
            map.forEach((k, v) -> payload.put(String.valueOf(k), v));
        } else {
            payload = Map.of("detail", detail);
        }
        String code = switch (action) {
            case "USER_CREATE_EXECUTE" -> "ADMIN_USER_CREATE";
            case "USER_UPDATE_EXECUTE", "USER_SET_PERSON_LEVEL_EXECUTE" -> "ADMIN_USER_UPDATE";
            case "USER_DELETE_EXECUTE", "USER_DISABLE_EXECUTE" -> "ADMIN_USER_DISABLE";
            case "USER_ENABLE_EXECUTE" -> "ADMIN_USER_ENABLE";
            case "USER_GRANT_ROLE_EXECUTE", "USER_REVOKE_ROLE_EXECUTE" -> "ADMIN_USER_ASSIGN_ROLE";
            case "USER_RESET_PASSWORD_EXECUTE" -> "ADMIN_USER_RESET_PASSWORD";
            default -> action;
        };
        AuditStage stage = "SUCCESS".equalsIgnoreCase(result) ? AuditStage.SUCCESS : AuditStage.FAIL;
        auditService.recordAction(actor, code, stage, normalizedTarget, payload);
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

    public ApprovalDTOs.ApprovalRequestDetail approve(long id, String approver, String note, String accessToken) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Instant now = Instant.now();
        try {
            // Prefer caller token when provided; fall back to service account for reliability
            List<String> candidateTokens = new ArrayList<>();
            if (accessToken != null && !accessToken.isBlank()) {
                candidateTokens.add(accessToken);
            }
            candidateTokens.add(resolveManagementToken());

            Exception last = null;
            for (String token : candidateTokens) {
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
            // Per-item audit: target_table=admin_approval_item, target_id=item.id
            try {
                if (approval.getItems() != null) {
                    for (AdminApprovalItem item : approval.getItems()) {
                        if (item != null && item.getId() != null) {
                            Map<String, Object> itemDetail = new java.util.LinkedHashMap<>();
                            itemDetail.put("approvalId", approval.getId());
                            itemDetail.put("type", approval.getType());
                            if (item.getSeqNumber() != null) itemDetail.put("seq", item.getSeqNumber());
                            auditService.record(
                                approver,
                                "ADMIN_APPROVAL_APPLY",
                                "admin.approvals",
                                "admin_approval_item",
                                String.valueOf(item.getId()),
                                "SUCCESS",
                                itemDetail
                            );
                        }
                    }
                }
            } catch (Exception ignore) {}
            // Decision audit: keep request-level entry for correlation
            auditService.recordAction(
                approver,
                "ADMIN_APPROVAL_DECIDE",
                AuditStage.SUCCESS,
                String.valueOf(id),
                new java.util.LinkedHashMap<String, Object>() {{
                    put("result", "APPROVED");
                    put("note", note);
                    put("type", approval.getType());
                }}
            );
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.APPLIED.name(), approver, now, null);
            return toDetailDto(approval);
        } catch (Exception ex) {
            auditService.recordAction(
                approver,
                "ADMIN_APPROVAL_DECIDE",
                AuditStage.FAIL,
                String.valueOf(id),
                new java.util.LinkedHashMap<String, Object>() {{
                    put("error", ex.getMessage());
                    put("result", "APPROVED");
                    put("type", approval.getType());
                }}
            );
            scheduleRetry(approval, note, ex.getMessage());
            approvalRepository.save(approval);
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.PENDING.name(), null, null, ex.getMessage());
            auditService.recordAction(
                approver,
                "ADMIN_APPROVAL_DECIDE",
                AuditStage.SUCCESS,
                String.valueOf(id),
                new java.util.LinkedHashMap<String, Object>() {{
                    put("result", "REQUEUE");
                    put("note", ex.getMessage());
                    put("type", approval.getType());
                }}
            );
            LOG.warn("Approval id={} failed to apply: {}", id, ex.getMessage());
            throw new IllegalStateException("审批执行失败: " + ex.getMessage(), ex);
        }
    }

    public ApprovalDTOs.ApprovalRequestDetail reject(long id, String approver, String note) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.REJECTED.name());
        approval.setDecidedAt(now);
        approval.setApprover(approver);
        approval.setDecisionNote(note);
        approvalRepository.save(approval);
        auditService.recordAction(
            approver,
            "ADMIN_APPROVAL_DECIDE",
            AuditStage.SUCCESS,
            String.valueOf(id),
            new java.util.LinkedHashMap<String, Object>() {{
                put("result", "REJECTED");
                put("note", note);
                put("type", approval.getType());
            }}
        );
        updateChangeRequestStatus(changeRequestIds, ApprovalStatus.REJECTED.name(), approver, now, null);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail delay(long id, String approver, String note) {
        AdminApprovalRequest approval = approvalRepository
            .findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + id));
        ensurePending(approval);
        Set<Long> changeRequestIds = extractChangeRequestIds(approval);
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.PROCESSING.name());
        approval.setDecidedAt(now);
        approval.setApprover(approver);
        approval.setDecisionNote(note);
        approvalRepository.save(approval);
        auditService.recordAction(
            approver,
            "ADMIN_APPROVAL_DECIDE",
            AuditStage.SUCCESS,
            String.valueOf(id),
            new java.util.LinkedHashMap<String, Object>() {{
                put("result", "PROCESS");
                put("note", note);
                put("type", approval.getType());
            }}
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
        recordAudit(requester, "USER_RESET_PASSWORD_REQUEST", username, ip, Map.of("temporary", temporary));
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
        LinkedHashSet<String> merged = new LinkedHashSet<>(copyList(current));
        if (delta != null) {
            if (adding) {
                merged.addAll(delta);
            } else {
                for (String role : delta) {
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
                    attributes.putIfAbsent(key.trim(), value.trim());
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
            return keycloakAdminClient.listRealmRoles(token);
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
            Optional<KeycloakUserDTO> existing = keycloakAdminClient.findByUsername(dto.getUsername(), accessToken);
            KeycloakUserDTO target;
            if (existing.isPresent()) {
                KeycloakUserDTO current = existing.orElseThrow();
                String keycloakId = current.getId();
                if (keycloakId != null && !keycloakId.isBlank()) {
                    dto.setId(keycloakId);
                    // Ensure dept_code aligns with Keycloak group dts_org_id if groups are provided
                    try {
                        List<String> groupPaths = stringList(payload.get("groupPaths"));
                        if (groupPaths != null && !groupPaths.isEmpty()) {
                            String firstPath = groupPaths.get(0);
                            if (firstPath != null && !firstPath.isBlank()) {
                                keycloakAdminClient
                                    .findGroupByPath(firstPath.trim(), accessToken)
                                    .ifPresent(grp -> {
                                        Map<String, List<String>> attrs = dto.getAttributes();
                                        if (attrs == null) attrs = new java.util.LinkedHashMap<>();
                                        String deptCode = null;
                                        if (grp.getAttributes() != null) {
                                            List<String> vals = grp.getAttributes().get("dts_org_id");
                                            if (vals != null && !vals.isEmpty()) deptCode = stringValue(vals.get(0));
                                        }
                                        if (deptCode != null && !deptCode.isBlank()) {
                                            attrs.put("dept_code", java.util.List.of(deptCode));
                                            dto.setAttributes(attrs);
                                        }
                                    });
                            }
                        }
                    } catch (Exception ignored) {}
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
                    // Ensure dept_code aligns with Keycloak group dts_org_id if groups are provided
                    try {
                        List<String> groupPaths = stringList(payload.get("groupPaths"));
                        if (groupPaths != null && !groupPaths.isEmpty()) {
                            String firstPath = groupPaths.get(0);
                            if (firstPath != null && !firstPath.isBlank()) {
                                keycloakAdminClient
                                    .findGroupByPath(firstPath.trim(), accessToken)
                                    .ifPresent(grp -> {
                                        Map<String, List<String>> attrs = dto.getAttributes();
                                        if (attrs == null) attrs = new java.util.LinkedHashMap<>();
                                        String deptCode = null;
                                        if (grp.getAttributes() != null) {
                                            List<String> vals = grp.getAttributes().get("dts_org_id");
                                            if (vals != null && !vals.isEmpty()) deptCode = stringValue(vals.get(0));
                                        }
                                        if (deptCode != null && !deptCode.isBlank()) {
                                            attrs.put("dept_code", java.util.List.of(deptCode));
                                            dto.setAttributes(attrs);
                                        }
                                    });
                            }
                        }
                    } catch (Exception ignored) {}
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
                // Ensure dept_code aligns with Keycloak group dts_org_id if groups are provided
                try {
                    List<String> groupPaths = stringList(payload.get("groupPaths"));
                    if (groupPaths != null && !groupPaths.isEmpty()) {
                        String firstPath = groupPaths.get(0);
                        if (firstPath != null && !firstPath.isBlank()) {
                            keycloakAdminClient
                                .findGroupByPath(firstPath.trim(), accessToken)
                                .ifPresent(grp -> {
                                    Map<String, List<String>> attrs = dto.getAttributes();
                                    if (attrs == null) attrs = new java.util.LinkedHashMap<>();
                                    String deptCode = null;
                                    if (grp.getAttributes() != null) {
                                        List<String> vals = grp.getAttributes().get("dts_org_id");
                                        if (vals != null && !vals.isEmpty()) deptCode = stringValue(vals.get(0));
                                    }
                                    if (deptCode != null && !deptCode.isBlank()) {
                                        attrs.put("dept_code", java.util.List.of(deptCode));
                                        dto.setAttributes(attrs);
                                    }
                                });
                        }
                    }
                } catch (Exception ignored) {}
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
            // Apply group memberships if provided (resolve by group path)
            try {
                List<String> groupPaths = stringList(payload.get("groupPaths"));
                if (groupPaths != null && !groupPaths.isEmpty()) {
                    for (String p : groupPaths) {
                        if (p == null || p.isBlank()) continue;
                        KeycloakUserDTO finalTarget = target;
                        keycloakAdminClient.findGroupByPath(p.trim(), accessToken).ifPresent(grp -> {
                            keycloakAdminClient.addUserToGroup(finalTarget.getId(), grp.getId(), accessToken);
                        });
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to assign groups on create for {}: {}", target.getUsername(), e.getMessage());
            }
            AdminKeycloakUser savedEntity = syncSnapshot(target);
            detail.put("keycloakId", target.getId());
            detail.put("realmRoles", target.getRealmRoles());
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
            // Ensure dept_code aligns with Keycloak group dts_org_id when group selection provided
            try {
                List<String> requestedGroupPaths = stringList(payload.get("groupPaths"));
                if (requestedGroupPaths != null && !requestedGroupPaths.isEmpty()) {
                    String firstPath = requestedGroupPaths.get(0);
                    if (firstPath != null && !firstPath.isBlank()) {
                        keycloakAdminClient.findGroupByPath(firstPath.trim(), accessToken).ifPresent(grp -> {
                            Map<String, List<String>> attrs = update.getAttributes();
                            if (attrs == null) attrs = new java.util.LinkedHashMap<>();
                            String deptCode = null;
                            if (grp.getAttributes() != null) {
                                List<String> vals = grp.getAttributes().get("dts_org_id");
                                if (vals != null && !vals.isEmpty()) deptCode = stringValue(vals.get(0));
                            }
                            if (deptCode != null && !deptCode.isBlank()) {
                                attrs.put("dept_code", java.util.List.of(deptCode));
                                update.setAttributes(attrs);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
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
            // Handle group membership delta separately (Keycloak expects user-group endpoints)
            try {
                List<String> requestedGroupPaths = stringList(payload.get("groupPaths"));
                if (requestedGroupPaths != null) {
                    List<com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO> currentGroups = keycloakAdminClient.listUserGroups(existing.getId(), accessToken);
                    java.util.Set<String> currentPaths = new java.util.HashSet<>();
                    java.util.Map<String, String> currentPathToId = new java.util.HashMap<>();
                    for (var g : currentGroups) {
                        if (g.getPath() != null) {
                            currentPaths.add(g.getPath());
                            currentPathToId.put(g.getPath(), g.getId());
                        }
                    }
                    java.util.Set<String> requestedPaths = new java.util.HashSet<>();
                    for (String p : requestedGroupPaths) if (p != null && !p.isBlank()) requestedPaths.add(p.trim());
                    for (String p : requestedPaths) {
                        if (!currentPaths.contains(p)) {
                            keycloakAdminClient.findGroupByPath(p, accessToken).ifPresent(grp -> keycloakAdminClient.addUserToGroup(existing.getId(), grp.getId(), accessToken));
                        }
                    }
                    for (String p : currentPaths) {
                        if (!requestedPaths.contains(p)) {
                            String gid = currentPathToId.get(p);
                            if (gid != null) keycloakAdminClient.removeUserFromGroup(existing.getId(), gid, accessToken);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to apply group membership delta for {}: {}", existing.getUsername(), e.getMessage());
            }
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), update, accessToken);
            // refresh role names from role-mappings to keep snapshot accurate
            try {
                List<String> names = keycloakAdminClient.listUserRealmRoles(existing.getId(), accessToken);
                if (names != null && !names.isEmpty()) {
                    updated.setRealmRoles(new ArrayList<>(names));
                }
            } catch (Exception ignored) {}
            AdminKeycloakUser updatedEntity = syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            detail.put("realmRoles", updated.getRealmRoles());
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
            attributes.put("fullname", List.of(fullName));
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
        if (role == null || role.isBlank()) return false;
        String r = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        return r.startsWith("DEPT_DATA_") || r.startsWith("INST_DATA_");
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

    private Map<String, List<String>> stringListMap(Object value) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;
                result.put(entry.getKey().toString(), stringList(entry.getValue()));
            }
        }
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
        if (r.endsWith("_OWNER")) return "read,write,export";
        if (r.endsWith("_DEV") || r.endsWith("_DATA_DEV")) return "read,write";
        return "read"; // VIEWER and others default to read
    }
}
