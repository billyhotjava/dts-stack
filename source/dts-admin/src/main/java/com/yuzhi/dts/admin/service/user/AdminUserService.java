package com.yuzhi.dts.admin.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.service.ChangeRequestService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;
    private final ChangeRequestService changeRequestService;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_PERSON_LEVEL = "GENERAL";

    public AdminUserService(
        AdminKeycloakUserRepository userRepository,
        KeycloakAdminClient keycloakAdminClient,
        AdminAuditService auditService,
        ObjectMapper objectMapper,
        ChangeRequestService changeRequestService
    ) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.changeRequestService = changeRequestService;
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

    public ChangeRequest submitCreate(UserOperationRequest request, String requester, String ip) {
        validateOperation(request, true);
        Map<String, Object> payload = buildCreatePayload(request);
        ChangeRequest cr = changeRequestService.draft("USER", "CREATE", request.getUsername(), payload, null, request.getReason());
        recordAudit(requester, "USER_CREATE_REQUEST", request.getUsername(), ip, request);
        return cr;
    }

    public ChangeRequest submitUpdate(String username, UserOperationRequest request, String requester, String ip) {
        validateOperation(request, false);
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> payload = buildUpdatePayload(request, snapshot);
        ChangeRequest cr = changeRequestService.draft("USER", "UPDATE", username, payload, snapshotPayload(snapshot), request.getReason());
        recordAudit(requester, "USER_UPDATE_REQUEST", username, ip, request);
        return cr;
    }

    public ChangeRequest submitDelete(String username, String reason, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> detail = new HashMap<>();
        detail.put("action", "USER_DELETE");
        detail.put("username", username);
        detail.put("reason", reason);
        detail.put("ip", ip);
        recordAudit(requester, "USER_DELETE_REQUEST", username, ip, detail);
        Map<String, Object> payload = buildDeletePayload(snapshot, reason);
        ChangeRequest cr = changeRequestService.draft("USER", "DELETE", username, payload, snapshotPayload(snapshot), reason);
        return cr;
    }

    public ChangeRequest submitGrantRoles(String username, List<String> roles, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("请选择要分配的角色");
        }
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "grantRoles");
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        recordAudit(requester, "USER_GRANT_ROLE_REQUEST", username, ip, Map.of("roles", roles));
        ChangeRequest cr = changeRequestService.draft("USER", "GRANT_ROLE", username, payload, snapshotPayload(snapshot), null);
        return cr;
    }

    public ChangeRequest submitRevokeRoles(String username, List<String> roles, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("请选择要移除的角色");
        }
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "revokeRoles");
        payload.put("username", username);
        payload.put("roles", new ArrayList<>(roles));
        payload.put("currentRoles", snapshot.getRealmRoles());
        recordAudit(requester, "USER_REVOKE_ROLE_REQUEST", username, ip, Map.of("roles", roles));
        ChangeRequest cr = changeRequestService.draft("USER", "REVOKE_ROLE", username, payload, snapshotPayload(snapshot), null);
        return cr;
    }

    public ChangeRequest submitSetEnabled(String username, boolean enabled, String requester, String ip) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "setEnabled");
        payload.put("username", username);
        payload.put("enabled", enabled);
        payload.put("currentEnabled", snapshot.isEnabled());
        recordAudit(requester, "USER_ENABLE_REQUEST", username, ip, Map.of("enabled", enabled));
        ChangeRequest cr = changeRequestService.draft("USER", "SET_ENABLED", username, payload, snapshotPayload(snapshot), null);
        return cr;
    }

    public ChangeRequest submitSetPersonLevel(
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "setPersonLevel");
        payload.put("username", username);
        payload.put("personSecurityLevel", normalizeSecurityLevel(personLevel));
        payload.put("dataLevels", dataLevels == null ? Collections.emptyList() : new ArrayList<>(dataLevels));
        payload.put("currentPersonSecurityLevel", snapshot.getPersonSecurityLevel());
        payload.put("currentDataLevels", snapshot.getDataLevels());
        recordAudit(requester, "USER_SET_PERSON_LEVEL_REQUEST", username, ip, Map.of("personLevel", personLevel, "dataLevels", dataLevels));
        ChangeRequest cr = changeRequestService.draft("USER", "SET_PERSON_LEVEL", username, payload, snapshotPayload(snapshot), reason);
        return cr;
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

    public void applyChange(ChangeRequest cr, String accessToken) throws Exception {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("缺少授权令牌，无法执行审批操作");
        }
        Map<String, Object> payload = readPayload(cr.getPayloadJson());
        String action = Optional.ofNullable(cr.getAction()).map(String::toUpperCase).orElse("");
        if (action.isEmpty()) {
            action = Optional.ofNullable(stringValue(payload.get("action"))).map(String::toUpperCase).orElse("");
        }
        switch (action) {
            case "CREATE" -> applyCreate(payload, accessToken);
            case "UPDATE" -> applyUpdate(payload, accessToken);
            case "DELETE" -> applyDelete(payload, accessToken);
            case "GRANT_ROLE" -> applyGrantRoles(payload, accessToken);
            case "REVOKE_ROLE" -> applyRevokeRoles(payload, accessToken);
            case "SET_ENABLED" -> applySetEnabled(payload, accessToken);
            case "SET_PERSON_LEVEL" -> applySetPersonLevel(payload, accessToken);
            case "RESET_PASSWORD" -> applyResetPassword(payload, accessToken);
            default -> throw new IllegalStateException("未支持的审批操作: " + action);
        }
    }

    public ChangeRequest submitResetPassword(String username, String password, boolean temporary, String requester, String ip) {
        AdminKeycloakUser snapshot = ensureSnapshot(username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "resetPassword");
        payload.put("username", username);
        payload.put("password", password);
        payload.put("temporary", temporary);
        payload.put("keycloakId", snapshot.getKeycloakId());
        recordAudit(requester, "USER_RESET_PASSWORD_REQUEST", username, ip, Map.of("temporary", temporary));
        return changeRequestService.draft("USER", "RESET_PASSWORD", username, payload, snapshotPayload(snapshot), null);
    }

    private Map<String, Object> readPayload(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private String resolveKeycloakId(Map<String, Object> payload) {
        String direct = stringValue(payload.get("keycloakId"));
        if (direct != null) {
            return direct;
        }
        Object target = payload.get("target");
        if (target instanceof Map<?, ?> targetMap) {
            Object value = ((Map<String, Object>) targetMap).get("keycloakId");
            return stringValue(value);
        }
        return null;
    }

    private void applyCreate(Map<String, Object> payload, String accessToken) {
        KeycloakUserDTO dto = toUserDto(payload);
        KeycloakUserDTO created = keycloakAdminClient.createUser(dto, accessToken);
        syncSnapshot(created);
    }

    private void applyUpdate(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        KeycloakUserDTO update = toUserDto(payload);
        update.setId(existing.getId());
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), update, accessToken);
        syncSnapshot(updated);
    }

    private void applyDelete(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        keycloakAdminClient.deleteUser(existing.getId(), accessToken);
        userRepository.findByUsernameIgnoreCase(existing.getUsername()).ifPresent(userRepository::delete);
    }

    private void applyGrantRoles(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        LinkedHashSet<String> roles = new LinkedHashSet<>(existing.getRealmRoles() == null ? List.of() : existing.getRealmRoles());
        roles.addAll(stringList(payload.get("roles")));
        existing.setRealmRoles(new ArrayList<>(roles));
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
        syncSnapshot(updated);
    }

    private void applyRevokeRoles(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        List<String> remove = stringList(payload.get("roles"));
        if (existing.getRealmRoles() != null) {
            existing.setRealmRoles(
                existing
                    .getRealmRoles()
                    .stream()
                    .filter(role -> !remove.contains(role))
                    .toList()
            );
        }
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
        syncSnapshot(updated);
    }

    private void applySetEnabled(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        existing.setEnabled(enabled);
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
        syncSnapshot(updated);
    }

    private void applySetPersonLevel(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        Map<String, List<String>> attributes = existing.getAttributes() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(existing.getAttributes());
        String level = stringValue(payload.get("personSecurityLevel"));
        if (level != null) {
            attributes.put("person_level", List.of(level));
        }
        List<String> dataLevels = stringList(payload.get("dataLevels"));
        if (!dataLevels.isEmpty()) {
            attributes.put("data_levels", dataLevels);
        }
        existing.setAttributes(attributes);
        KeycloakUserDTO updated = keycloakAdminClient.updateUser(existing.getId(), existing, accessToken);
        syncSnapshot(updated);
    }

    private void applyResetPassword(Map<String, Object> payload, String accessToken) {
        String username = stringValue(payload.get("username"));
        String keycloakId = resolveKeycloakId(payload);
        KeycloakUserDTO existing = locateUser(username, keycloakId, accessToken);
        String password = stringValue(payload.get("password"));
        boolean temporary = Boolean.TRUE.equals(payload.get("temporary"));
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("审批载荷缺少密码信息");
        }
        keycloakAdminClient.resetPassword(existing.getId(), password, temporary, accessToken);
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
        List<String> dataLevels = stringList(payload.get("dataLevels"));
        if (!dataLevels.isEmpty()) {
            attributes.put("data_levels", dataLevels);
        }
        dto.setAttributes(attributes);
        return dto;
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
