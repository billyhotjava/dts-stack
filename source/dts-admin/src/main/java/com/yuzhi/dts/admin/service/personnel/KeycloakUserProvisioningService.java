package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 封装「按 PersonnelPayload 在 Keycloak 中创建/更新 user」的直写流程。
 * 只负责 Keycloak 侧写入 + 返回 keycloakUserId，不管本地表。
 */
@Service
public class KeycloakUserProvisioningService {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserProvisioningService.class);
    private static final String RANDOM_PASSWORD_PREFIX = "mdm$";

    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAuthService keycloakAuthService;
    private final OrganizationRepository organizationRepository;
    private final MdmGatewayProperties mdmGatewayProperties;
    private final String managementClientId;
    private final String managementClientSecret;

    public KeycloakUserProvisioningService(
        KeycloakAdminClient keycloakAdminClient,
        KeycloakAuthService keycloakAuthService,
        OrganizationRepository organizationRepository,
        MdmGatewayProperties mdmGatewayProperties,
        @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}") String managementClientId,
        @Value("${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}") String managementClientSecret
    ) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakAuthService = keycloakAuthService;
        this.organizationRepository = organizationRepository;
        this.mdmGatewayProperties = mdmGatewayProperties;
        this.managementClientId = managementClientId == null ? "" : managementClientId.trim();
        this.managementClientSecret = managementClientSecret == null ? "" : managementClientSecret.trim();
    }

    /**
     * 创建或更新 Keycloak 用户；返回 keycloakUserId。
     * 调用方负责区分成功/失败（异常抛出）。
     */
    public String provision(PersonnelPayload payload) {
        String username = firstNonBlank(payload.account(), payload.personCode());
        if (StringUtils.isBlank(username)) {
            throw new PersonnelImportException("无法确定 username：account/personCode 均为空");
        }
        String token = resolveManagementToken();
        if (token == null) {
            throw new PersonnelImportException("Keycloak management token 不可用");
        }
        int mdmEnabled = resolveMdmEnabled(payload);
        Map<String, List<String>> desiredAttrs = KeycloakUserAttributesMapper.toAttributes(payload);

        var existingOpt = keycloakAdminClient.findByUsername(username, token);
        String keycloakUserId;
        if (existingOpt.isPresent()) {
            KeycloakUserDTO existing = existingOpt.orElseThrow();
            boolean dirty = false;
            if (!StringUtils.equals(existing.getFullName(), payload.fullName())) {
                existing.setFullName(payload.fullName());
                dirty = true;
            }
            if (!attributesEqual(existing.getAttributes(), desiredAttrs)) {
                existing.setAttributes(desiredAttrs);
                dirty = true;
            }
            if (dirty) {
                keycloakAdminClient.updateUser(existing.getId(), existing, token);
            }
            keycloakUserId = existing.getId();
        } else {
            KeycloakUserDTO dto = new KeycloakUserDTO();
            dto.setUsername(username);
            dto.setFullName(payload.fullName());
            dto.setEnabled(mdmEnabled != 0);
            dto.setEmailVerified(false);
            dto.setAttributes(desiredAttrs);
            KeycloakUserDTO created = keycloakAdminClient.createUser(dto, token);
            keycloakUserId = created != null && StringUtils.isNotBlank(created.getId()) ? created.getId() : dto.getId();
            if (mdmGatewayProperties != null && mdmGatewayProperties.isAutoProvisionEnableLogin() && StringUtils.isNotBlank(keycloakUserId)) {
                String pwd = RANDOM_PASSWORD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                try {
                    keycloakAdminClient.resetPassword(keycloakUserId, pwd, true, token);
                } catch (Exception ex) {
                    LOG.warn("set temp password failed for user {}: {}", username, ex.getMessage());
                }
            }
        }
        assignBaseRoles(keycloakUserId, token);
        assignDeptGroup(keycloakUserId, payload, token);
        return keycloakUserId;
    }

    private int resolveMdmEnabled(PersonnelPayload payload) {
        if (payload == null) {
            return 1;
        }
        Object raw = payload.attributes() == null ? null : payload.attributes().get("status");
        if (raw != null) {
            String v = String.valueOf(raw).trim();
            if ("0".equals(v)) return 0;
            if ("1".equals(v)) return 1;
        }
        String lifecycle = payload.status() == null ? "" : payload.status().trim().toUpperCase(Locale.ROOT);
        return ("INACTIVE".equals(lifecycle) || "DISABLED".equals(lifecycle)) ? 0 : 1;
    }

    private boolean attributesEqual(Map<String, List<String>> left, Map<String, List<String>> right) {
        return normalizeAttributes(left).equals(normalizeAttributes(right));
    }

    private Map<String, List<String>> normalizeAttributes(Map<String, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new HashMap<>();
        source.forEach((k, v) -> {
            List<String> vals = v == null
                ? List.of()
                : v.stream().filter(Objects::nonNull).map(String::valueOf).map(StringUtils::trimToEmpty).sorted().toList();
            normalized.put(k, vals);
        });
        return normalized;
    }

    private void assignBaseRoles(String userId, String token) {
        if (userId == null || token == null || mdmGatewayProperties == null) {
            return;
        }
        String rolesCsv = mdmGatewayProperties.getAutoProvisionRoles();
        if (StringUtils.isBlank(rolesCsv)) {
            return;
        }
        List<String> roles = Arrays.stream(rolesCsv.split(",")).map(String::trim).filter(StringUtils::isNotBlank).toList();
        if (roles.isEmpty()) {
            return;
        }
        try {
            keycloakAdminClient.addRealmRolesToUser(userId, roles, token);
        } catch (Exception ex) {
            LOG.warn("assign base roles failed for user {} roles={} reason={}", userId, roles, ex.getMessage());
        }
    }

    private void assignDeptGroup(String userId, PersonnelPayload payload, String token) {
        if (userId == null || token == null) {
            return;
        }
        String deptCode = payload.deptCode();
        if (StringUtils.isBlank(deptCode)) {
            return;
        }
        organizationRepository.findFirstByDeptCodeIgnoreCase(deptCode).ifPresent(node -> {
            String groupId = node.getKeycloakGroupId();
            if (StringUtils.isBlank(groupId)) {
                LOG.warn("dept {} has no Keycloak group id; user {} not bound to group", deptCode, userId);
                return;
            }
            try {
                keycloakAdminClient.addUserToGroup(userId, groupId, token);
            } catch (Exception ex) {
                LOG.warn("bind user {} to dept {} group failed: {}", userId, deptCode, ex.getMessage());
            }
        });
    }

    private String resolveManagementToken() {
        if (StringUtils.isBlank(managementClientId)) {
            LOG.warn("skip keycloak auto-provision: management clientId missing");
            return null;
        }
        try {
            return keycloakAuthService.obtainClientCredentialsToken(managementClientId, managementClientSecret).accessToken();
        } catch (Exception ex) {
            LOG.warn("obtain service token failed: {}", ex.getMessage());
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) return v.trim();
        }
        return null;
    }
}
