package com.yuzhi.dts.admin.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import com.yuzhi.dts.admin.domain.AdminApprovalRequest;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminApprovalRequestRepository;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.service.approval.ApprovalStatus;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminUserService {

    private static final Set<String> SUPPORTED_SECURITY_LEVELS = Set.of("NONE_SECRET", "NON_SECRET", "GENERAL", "IMPORTANT", "CORE");
    private static final Set<String> SUPPORTED_DATA_LEVELS = Set.of("DATA_INTERNAL", "DATA_PUBLIC", "DATA_SECRET", "DATA_TOP_SECRET");

    private final AdminKeycloakUserRepository userRepository;
    private final AdminApprovalRequestRepository approvalRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminUserService(
        AdminKeycloakUserRepository userRepository,
        AdminApprovalRequestRepository approvalRepository,
        KeycloakAdminClient keycloakAdminClient,
        AdminAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.approvalRepository = approvalRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
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
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, request.getReason(), "USER_CREATE");
        approval.addItem(buildPayloadItem(request.getUsername(), buildCreatePayload(request)));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_CREATE_REQUEST", request.getUsername(), ip, request);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitUpdate(String username, UserOperationRequest request, String requester, String ip) {
        validateOperation(request, false);
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, request.getReason(), "USER_UPDATE");
        approval.addItem(buildPayloadItem(username, buildUpdatePayload(request, snapshot)));
        approval = approvalRepository.save(approval);
        recordAudit(requester, "USER_UPDATE_REQUEST", username, ip, request);
        return toDetailDto(approval);
    }

    public ApprovalDTOs.ApprovalRequestDetail submitDelete(String username, String reason, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        AdminApprovalRequest approval = buildApprovalSkeleton(requester, reason, "USER_DELETE");
        approval.addItem(buildPayloadItem(username, buildDeletePayload(snapshot, reason)));
        approval = approvalRepository.save(approval);
        Map<String, Object> detail = new HashMap<>();
        detail.put("action", "USER_DELETE");
        detail.put("username", username);
        detail.put("reason", reason);
        detail.put("ip", ip);
        recordAudit(requester, "USER_DELETE_REQUEST", username, ip, detail);
        return toDetailDto(approval);
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
        if (request.getDataLevels() == null || request.getDataLevels().isEmpty()) {
            throw new IllegalArgumentException("数据密级范围不能为空");
        }
        for (String level : request.getDataLevels()) {
            if (!SUPPORTED_DATA_LEVELS.contains(level)) {
                throw new IllegalArgumentException("不支持的数据密级: " + level);
            }
        }
    }

    private AdminApprovalRequest buildApprovalSkeleton(String requester, String reason, String type) {
        AdminApprovalRequest approval = new AdminApprovalRequest();
        approval.setRequester(requester);
        approval.setType(type);
        approval.setReason(reason);
        approval.setStatus(ApprovalStatus.PENDING.name());
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
        payload.put("personSecurityLevel", normalizeSecurityLevel(request.getPersonSecurityLevel()));
        payload.put("dataLevels", request.getDataLevels());
        payload.put("realmRoles", request.getRealmRoles());
        payload.put("groupPaths", request.getGroupPaths());
        payload.put("enabled", request.getEnabled());
        payload.put("reason", request.getReason());
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
        auditService.record(actor, action, "USER", target, "SUCCESS", writeJson(detail));
    }

    private String normalizeSecurityLevel(String level) {
        if (level == null) {
            return null;
        }
        return level.trim().toUpperCase().replace('-', '_');
    }

    private AdminKeycloakUser ensureSnapshot(String username) {
        return userRepository
            .findByUsernameIgnoreCase(username)
            .or(() -> keycloakAdminClient.findByUsername(username, null).map(this::syncSnapshot))
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
    }

    private AdminKeycloakUser syncSnapshot(KeycloakUserDTO dto) {
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
        entity.setDataLevels(
            extractList(dto, "data_levels").stream().map(s -> s == null ? null : s.toUpperCase()).filter(s -> s != null).collect(Collectors.toList())
        );
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

    private ApprovalDTOs.ApprovalRequestDetail toDetailDto(AdminApprovalRequest approval) {
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
}
