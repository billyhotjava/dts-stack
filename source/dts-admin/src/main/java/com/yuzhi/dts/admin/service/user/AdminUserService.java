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
import com.yuzhi.dts.admin.service.approval.ApprovalStatus;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakRoleDTO;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
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
    private static final Set<String> SUPPORTED_DATA_LEVELS = Set.of("DATA_PUBLIC", "DATA_INTERNAL", "DATA_SECRET", "DATA_TOP_SECRET");
    private static final Map<String, String> DATA_LEVEL_ALIASES = Map.ofEntries(
        Map.entry("PUBLIC", "DATA_PUBLIC"),
        Map.entry("DATA_PUBLIC", "DATA_PUBLIC"),
        Map.entry("INTERNAL", "DATA_INTERNAL"),
        Map.entry("DATA_INTERNAL", "DATA_INTERNAL"),
        Map.entry("SECRET", "DATA_SECRET"),
        Map.entry("DATA_SECRET", "DATA_SECRET"),
        Map.entry("TOP_SECRET", "DATA_TOP_SECRET"),
        Map.entry("DATA_TOP_SECRET", "DATA_TOP_SECRET")
    );
    private static final Map<String, String> INTERNAL_TO_KEYCLOAK_DATA_LEVEL = Map.of(
        "DATA_PUBLIC",
        "PUBLIC",
        "DATA_INTERNAL",
        "INTERNAL",
        "DATA_SECRET",
        "SECRET",
        "DATA_TOP_SECRET",
        "TOP_SECRET"
    );

    private final AdminKeycloakUserRepository userRepository;
    private final AdminApprovalRequestRepository approvalRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminAuditService auditService;
    private final ChangeRequestService changeRequestService;
    private final ChangeRequestRepository changeRequestRepository;
    private final ObjectMapper objectMapper;
    private final KeycloakAuthService keycloakAuthService;
    private final String managementClientId;
    private final String managementClientSecret;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";

    public AdminUserService(
        AdminKeycloakUserRepository userRepository,
        AdminApprovalRequestRepository approvalRepository,
        KeycloakAdminClient keycloakAdminClient,
        AdminAuditService auditService,
        ChangeRequestService changeRequestService,
        ChangeRequestRepository changeRequestRepository,
        ObjectMapper objectMapper,
        KeycloakAuthService keycloakAuthService,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret
    ) {
        this.userRepository = userRepository;
        this.approvalRepository = approvalRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditService = auditService;
        this.changeRequestService = changeRequestService;
        this.changeRequestRepository = changeRequestRepository;
        this.objectMapper = objectMapper;
        this.keycloakAuthService = keycloakAuthService;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret;
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
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        payload.put("addedRoles", new ArrayList<>(roles));
        payload.put("resultRoles", mergeRoles(snapshot.getRealmRoles(), roles, true));
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
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        payload.put("removedRoles", new ArrayList<>(roles));
        payload.put("resultRoles", mergeRoles(snapshot.getRealmRoles(), roles, false));
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
        List<String> dataLevels,
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
        payload.put("dataLevels", dataLevels == null ? Collections.emptyList() : new ArrayList<>(dataLevels));
        payload.put("currentPersonSecurityLevel", snapshot.getPersonSecurityLevel());
        payload.put("currentDataLevels", snapshot.getDataLevels());
        Map<String, Object> before = new LinkedHashMap<>();
        if (snapshot.getPersonSecurityLevel() != null) {
            before.put("personSecurityLevel", snapshot.getPersonSecurityLevel());
        }
        before.put("dataLevels", copyList(snapshot.getDataLevels()));
        ChangeRequest changeRequest = createChangeRequest("USER", "SET_PERSON_LEVEL", username, payload, before, reason);
        payload.put("changeRequestId", changeRequest.getId());
        approval.addItem(buildPayloadItem(username, payload));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_SET_PERSON_LEVEL_REQUEST", username, ip, Map.of("personLevel", personLevel, "dataLevels", dataLevels));
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
        if (request.getDataLevels() != null) {
            List<String> normalizedLevels = new ArrayList<>();
            for (String level : request.getDataLevels()) {
                if (StringUtils.isBlank(level)) {
                    continue;
                }
                String normalized = normalizeDataLevel(level);
                if (!SUPPORTED_DATA_LEVELS.contains(normalized)) {
                    throw new IllegalArgumentException("不支持的数据密级: " + level);
                }
                normalizedLevels.add(normalized);
                break;
            }
            request.setDataLevels(normalizedLevels);
        }
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
        payload.put("username", snapshot.getUsername());
        payload.put("reason", reason);
        payload.put("target", snapshotPayload(snapshot));
        return payload;
    }

    private Map<String, Object> basePayload(String action, String username, UserOperationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("username", username);
        payload.put("fullName", request.getFullName());
        payload.put("email", request.getEmail());
        payload.put("phone", request.getPhone());
        String normalizedPersonLevel = normalizeSecurityLevel(request.getPersonSecurityLevel());
        ensureAllowedSecurityLevel(normalizedPersonLevel, "人员密级不允许为非密");
        payload.put("personSecurityLevel", normalizedPersonLevel);
        payload.put("dataLevels", request.getDataLevels());
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
        payload.put("dataLevels", snapshot.getDataLevels());
        payload.put("realmRoles", snapshot.getRealmRoles());
        payload.put("groupPaths", snapshot.getGroupPaths());
        payload.put("enabled", snapshot.isEnabled());
        payload.put("attributes", Map.of());
        payload.put("keycloakId", snapshot.getKeycloakId());
        return payload;
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
        auditService.record(actor, action, "USER", target, "SUCCESS", detail);
    }

    private void auditUserChange(String actor, String action, String target, String result, Object detail) {
        String normalizedTarget = target == null ? "UNKNOWN" : target;
        auditService.record(actor, action, "USER", normalizedTarget, result, detail);
    }

    private String normalizeSecurityLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = level.trim().toUpperCase().replace('-', '_');
        if ("NONE_SECRET".equals(normalized)) {
            return "NON_SECRET";
        }
        return normalized;
    }

    private String normalizeDataLevel(String level) {
        if (level == null) {
            return null;
        }
        String cleaned = level.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return DATA_LEVEL_ALIASES.getOrDefault(cleaned, cleaned);
    }

    private List<String> toInternalDataLevels(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (String level : source) {
            String normalized = normalizeDataLevel(level);
            if (normalized != null && SUPPORTED_DATA_LEVELS.contains(normalized)) {
                results.add(normalized);
                break;
            }
        }
        return results;
    }

    private List<String> toKeycloakDataLevels(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (String level : source) {
            String normalized = normalizeDataLevel(level);
            if (normalized == null) {
                continue;
            }
            String mapped = INTERNAL_TO_KEYCLOAK_DATA_LEVEL.getOrDefault(normalized, normalized);
            results.add(mapped);
            break;
        }
        return results;
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
        entity.setDataLevels(toInternalDataLevels(extractList(dto, "data_levels")));
        entity.setRealmRoles(dto.getRealmRoles());
        entity.setGroupPaths(dto.getGroups());
        entity.setPhone(extractSingle(dto, "phone"));
        entity.setLastSyncAt(Instant.now());
        if (!SUPPORTED_SECURITY_LEVELS.contains(securityLevel)) {
            throw new IllegalStateException("用户密级无效: " + securityLevel);
        }
        for (String level : entity.getDataLevels()) {
            if (!SUPPORTED_DATA_LEVELS.contains(level)) {
                throw new IllegalStateException("数据密级无效: " + level);
            }
        }
    }

    private String resolveFullName(KeycloakUserDTO dto) {
        if (StringUtils.isNotBlank(dto.getFirstName()) || StringUtils.isNotBlank(dto.getLastName())) {
            return StringUtils.trim(StringUtils.defaultString(dto.getLastName()) + " " + StringUtils.defaultString(dto.getFirstName()));
        }
        return dto.getUsername();
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
            // Always use service account for reliability (caller token may lack admin privileges)
            String tokenToUse = resolveManagementToken();
            LOG.info("Applying approval id={} type={} items={} by approver={}", id, approval.getType(), approval.getItems().size(), approver);
            applyApproval(approval, tokenToUse, approver);
            approval.setStatus(ApprovalStatus.APPLIED.name());
            approval.setDecidedAt(now);
            approval.setApprover(approver);
            approval.setDecisionNote(note);
            approval.setErrorMessage(null);
            approvalRepository.save(approval);
            auditService.record(approver, "APPROVAL_APPROVE", "APPROVAL", String.valueOf(id), "SUCCESS", note);
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.APPLIED.name(), approver, now, null);
            return toDetailDto(approval);
        } catch (Exception ex) {
            auditService.record(approver, "APPROVAL_APPROVE", "APPROVAL", String.valueOf(id), "FAILURE", ex.getMessage());
            scheduleRetry(approval, note, ex.getMessage());
            approvalRepository.save(approval);
            updateChangeRequestStatus(changeRequestIds, ApprovalStatus.PENDING.name(), null, null, ex.getMessage());
            auditService.record(approver, "APPROVAL_REQUEUE", "APPROVAL", String.valueOf(id), "SUCCESS", ex.getMessage());
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
        auditService.record(approver, "APPROVAL_REJECT", "APPROVAL", String.valueOf(id), "SUCCESS", note);
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
        auditService.record(approver, "APPROVAL_PROCESS", "APPROVAL", String.valueOf(id), "SUCCESS", note);
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
        syncRealmRole(roleName, scope, operations, null);
    }

    public void syncRealmRole(String roleName, String scope, Set<String> operations, String description) {
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
            LOG.info("Synchronized realm role {} to Keycloak (scope={}, ops={}, hasCustomDesc={})", nameToUse, scope, operations, StringUtils.isNotBlank(description));
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
                    target = keycloakAdminClient.createUser(dto, accessToken);
                }
            } else {
                target = keycloakAdminClient.createUser(dto, accessToken);
            }
            // Apply realm roles via role-mappings if provided in payload
            List<String> requestedRoles = stringList(payload.get("realmRoles"));
            if (requestedRoles != null && !requestedRoles.isEmpty()) {
                LOG.info("Approval applyCreate assign roles username={}, id={}, roles={}", target.getUsername(), target.getId(), requestedRoles);
                try {
                    keycloakAdminClient.addRealmRolesToUser(target.getId(), requestedRoles, accessToken);
                    // Refresh assigned role names from Keycloak
                    List<String> names = keycloakAdminClient.listUserRealmRoles(target.getId(), accessToken);
                    if (names != null && !names.isEmpty()) {
                        target.setRealmRoles(new ArrayList<>(names));
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to assign realm roles on create for user {}: {}", target.getUsername(), e.getMessage());
                }
            }
            syncSnapshot(target);
            detail.put("keycloakId", target.getId());
            detail.put("realmRoles", target.getRealmRoles());
            String resolvedTarget = target.getUsername() != null ? target.getUsername() : username;
            auditUserChange(actor, auditAction, resolvedTarget, "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
            ensureAllowedSecurityLevel(extractPersonLevel(update), "人员密级不允许为非密");
            update.setId(existing.getId());
            // If realmRoles present in payload, apply via role-mappings API instead of user PUT
            List<String> requestedRoles = stringList(payload.get("realmRoles"));
            if (requestedRoles != null && !requestedRoles.isEmpty()) {
                List<String> currentRoles = existing.getRealmRoles() == null ? List.of() : existing.getRealmRoles();
                LinkedHashSet<String> req = new LinkedHashSet<>(requestedRoles);
                LinkedHashSet<String> cur = new LinkedHashSet<>(currentRoles);
                List<String> toAdd = req.stream().filter(r -> !cur.contains(r)).toList();
                List<String> toRemove = cur.stream().filter(r -> !req.contains(r)).toList();
                LOG.info("Approval applyUpdate roles delta for user username={}, id={}: toAdd={}, toRemove={}", existing.getUsername(), existing.getId(), toAdd, toRemove);
                if (!toAdd.isEmpty()) {
                    keycloakAdminClient.addRealmRolesToUser(existing.getId(), toAdd, accessToken);
                }
                if (!toRemove.isEmpty()) {
                    keycloakAdminClient.removeRealmRolesFromUser(existing.getId(), toRemove, accessToken);
                }
                // Avoid including roles in representation update
                update.setRealmRoles(new ArrayList<>());
            }
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), update, accessToken);
            // refresh role names from role-mappings to keep snapshot accurate
            try {
                List<String> names = keycloakAdminClient.listUserRealmRoles(existing.getId(), accessToken);
                if (names != null && !names.isEmpty()) {
                    updated.setRealmRoles(new ArrayList<>(names));
                }
            } catch (Exception ignored) {}
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            detail.put("realmRoles", updated.getRealmRoles());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
            boolean removedSnapshot = userRepository.findByUsernameIgnoreCase(existing.getUsername()).map(entity -> {
                userRepository.delete(entity);
                return true;
            }).orElse(false);
            detail.put("keycloakId", existing.getId());
            detail.put("snapshotRemoved", removedSnapshot);
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
                keycloakAdminClient.addRealmRolesToUser(existing.getId(), rolesToAdd, accessToken);
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
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
                keycloakAdminClient.removeRealmRolesFromUser(existing.getId(), remove, accessToken);
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
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
            throw ex;
        }
    }

    private void applySetPersonLevel(Map<String, Object> payload, String accessToken, String actor) {
        String username = stringValue(payload.get("username"));
        String auditAction = "USER_SET_PERSON_LEVEL_EXECUTE";
        String level = stringValue(payload.get("personSecurityLevel"));
        List<String> requestedDataLevels = stringList(payload.get("dataLevels"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payload", payload);
        detail.put("personLevel", level);
        detail.put("dataLevels", requestedDataLevels);
        try {
            String keycloakId = stringValue(payload.get("keycloakId"));
            KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
            Map<String, List<String>> attributes = existing.getAttributes() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing.getAttributes());
            if (level != null) {
                attributes.put("person_level", List.of(level));
            }
            List<String> dataLevels = toKeycloakDataLevels(requestedDataLevels);
            if (!dataLevels.isEmpty()) {
                attributes.put("data_levels", dataLevels);
            } else {
                attributes.remove("data_levels");
            }
            existing.setAttributes(attributes);
            KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
            syncSnapshot(updated);
            detail.put("keycloakId", existing.getId());
            auditUserChange(actor, auditAction, existing.getUsername(), "SUCCESS", detail);
        } catch (Exception ex) {
            detail.put("error", ex.getMessage());
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
            auditUserChange(actor, auditAction, username, "FAILURE", detail);
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
        if (personLevel != null && !personLevel.isBlank()) {
            attributes.put("person_level", List.of(personLevel));
        }
        List<String> dataLevels = toKeycloakDataLevels(stringList(payload.get("dataLevels")));
        if (!dataLevels.isEmpty()) {
            attributes.put("data_levels", dataLevels);
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
        }
        return null;
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
}
