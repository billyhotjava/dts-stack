package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.OrganizationNode;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakGroupDTO;
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
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * 创建或更新 Keycloak 用户，并把用户对齐到 payload 指定的部门组；
     * 返回 keycloakUserId 及本次绑定的部门组路径。
     * 调用方负责区分成功/失败（异常抛出）。
     *
     * <p>传播模式使用 {@link Propagation#MANDATORY}：本方法会通过 {@code organizationRepository.save(...)}
     * 把 {@code keycloakGroupId} 回写到 {@code organization_node}，必须运行在调用方（例如
     * {@code PersonnelImportService.processBatch}）已开启的事务中，和 person_import_record 写入
     * 处于同一事务。若未来有新的调用方忘记开事务，这里会直接抛
     * {@link org.springframework.transaction.IllegalTransactionStateException}，暴露问题而不是
     * 悄悄产生跨事务写入的一致性漏洞。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProvisionResult provision(PersonnelPayload payload) {
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

        var existingOpt = keycloakAdminClient.findByUsernameStrict(username, token);
        boolean preexisting = existingOpt.isPresent();
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
            if (StringUtils.isBlank(keycloakUserId)) {
                throw new PersonnelImportException("Keycloak 返回的 user id 为空，无法确定 " + username + " 的 keycloakUserId");
            }
            if (mdmGatewayProperties != null && mdmGatewayProperties.isAutoProvisionEnableLogin()) {
                String pwd = RANDOM_PASSWORD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                try {
                    keycloakAdminClient.resetPassword(keycloakUserId, pwd, true, token);
                } catch (Exception ex) {
                    LOG.warn("set temp password failed for user {}: {}", username, ex.getMessage());
                }
            }
        }
        assignBaseRoles(keycloakUserId, token);
        String deptGroupPath = syncDeptGroup(keycloakUserId, payload, token, preexisting);
        return new ProvisionResult(keycloakUserId, deptGroupPath);
    }

    /**
     * {@link #provision(PersonnelPayload)} 的返回值。
     *
     * @param keycloakUserId Keycloak 侧的 user id
     * @param deptGroupPath  本次实际绑定的部门组路径；部门无法解析时为 {@code null}
     */
    public record ProvisionResult(String keycloakUserId, String deptGroupPath) {}

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

    /**
     * 把用户对齐到 payload 指定的部门组：先摘掉其它部门组，再挂到目标组。
     *
     * <p>此前这里只调 {@code addUserToGroup}，人员调岗后会同时留在新旧两个部门组下，
     * 既让页面部门显示错乱，也放大了按部门授权的数据可见范围。
     *
     * <p>只摘除能在 {@code organization_node} 里按 keycloakGroupId 反查到的组，
     * 即「本系统认定的部门组」；专项组、权限组等非部门组一律不动。
     *
     * @param preexisting 该用户在本次调用前就已存在于 Keycloak；为 {@code false} 时（刚创建）
     *                    不可能挂着旧部门组，跳过清理以省掉一次 listUserGroups 远程调用
     * @return 实际绑定的部门组路径；未能解析部门或未能绑定时返回 {@code null}
     */
    private String syncDeptGroup(String userId, PersonnelPayload payload, String token, boolean preexisting) {
        if (userId == null || token == null) {
            return null;
        }
        String deptCode = payload.deptCode();
        if (StringUtils.isBlank(deptCode)) {
            return null;
        }
        Optional<OrganizationNode> targetOpt = organizationRepository.findFirstByDeptCodeIgnoreCase(deptCode);
        if (targetOpt.isEmpty()) {
            LOG.warn("dept {} not found in organization_node; user {} dept group unchanged", deptCode, userId);
            return null;
        }
        OrganizationNode node = targetOpt.orElseThrow();
        String groupPath = buildGroupPath(node);
        String groupId = node.getKeycloakGroupId();
        if (StringUtils.isBlank(groupId)) {
            if (StringUtils.isBlank(groupPath)) {
                groupPath = buildGroupPathFromRepository(node);
            }
            if (StringUtils.isNotBlank(groupPath)) {
                String resolvedPath = groupPath;
                keycloakAdminClient
                    .findGroupByPath(resolvedPath, token)
                    .ifPresent(found -> {
                        node.setKeycloakGroupId(found.getId());
                        organizationRepository.save(node);
                    });
                groupId = node.getKeycloakGroupId();
            }
        }
        if (StringUtils.isBlank(groupId)) {
            LOG.warn("dept {} has no Keycloak group id; user {} not bound to group", deptCode, userId);
            return null;
        }
        if (StringUtils.isBlank(groupPath)) {
            groupPath = buildGroupPathFromRepository(node);
        }
        if (preexisting) {
            removeStaleDeptGroups(userId, groupId, token);
        }
        try {
            keycloakAdminClient.addUserToGroup(userId, groupId, token);
        } catch (Exception ex) {
            LOG.warn("bind user {} to dept {} group failed: {}", userId, deptCode, ex.getMessage());
            return null;
        }
        return groupPath;
    }

    /** 摘除用户当前挂着的、除 {@code keepGroupId} 以外的所有部门组。 */
    private void removeStaleDeptGroups(String userId, String keepGroupId, String token) {
        List<KeycloakGroupDTO> current;
        try {
            current = keycloakAdminClient.listUserGroups(userId, token);
        } catch (Exception ex) {
            LOG.warn("list groups for user {} failed, skip stale dept group cleanup: {}", userId, ex.getMessage());
            return;
        }
        if (current == null || current.isEmpty()) {
            return;
        }
        for (KeycloakGroupDTO group : current) {
            if (group == null || StringUtils.isBlank(group.getId()) || StringUtils.equals(group.getId(), keepGroupId)) {
                continue;
            }
            // 只有能反查到组织节点的组才是部门组；其余（专项组、权限组等）不属于 MDM 管辖范围。
            if (organizationRepository.findByKeycloakGroupId(group.getId()).isEmpty()) {
                continue;
            }
            try {
                keycloakAdminClient.removeUserFromGroup(userId, group.getId(), token);
                LOG.info("removed user {} from stale dept group {} ({})", userId, group.getId(), group.getPath());
            } catch (Exception ex) {
                LOG.warn("remove user {} from stale dept group {} failed: {}", userId, group.getId(), ex.getMessage());
            }
        }
    }

    private String buildGroupPath(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        List<String> segments = new java.util.ArrayList<>();
        OrganizationNode cursor = node;
        while (cursor != null) {
            String name = StringUtils.trimToNull(cursor.getName());
            if (name != null) {
                segments.add(0, name);
            }
            cursor = cursor.getParent();
        }
        if (segments.isEmpty()) {
            return null;
        }
        return "/" + String.join("/", segments);
    }

    /**
     * 当 JPA 未加载 parent 时，使用递归 CTE 一次性拉回整条祖先链，避免之前
     * 每层都 {@code findFirstByDeptCodeIgnoreCase(...)} 造成的 N+1 查询。
     * 1000 条人员导入、最坏每人部门 20 层祖先 → 从 20,000 次 SELECT 降到 1000 次。
     *
     * <p>CTE 返回按 depth DESC 排序（根节点在前，目标节点在后），因此可以直接
     * 顺序拼接 segment，无需再反转。
     */
    private String buildGroupPathFromRepository(OrganizationNode node) {
        if (node == null) {
            return null;
        }
        String deptCode = StringUtils.trimToNull(node.getDeptCode());
        if (deptCode == null) {
            // 没有 deptCode 无法走 CTE，退化到只用当前节点名构造单段路径。
            String name = StringUtils.trimToNull(node.getName());
            return name == null ? null : "/" + name;
        }
        List<Object[]> rows = organizationRepository.findAncestorChainByDeptCode(deptCode);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<String> segments = new java.util.ArrayList<>(rows.size());
        for (Object[] row : rows) {
            // row layout: [id, dept_code, parent_id, name]
            String name = row.length > 3 ? StringUtils.trimToNull(Objects.toString(row[3], null)) : null;
            if (name != null) {
                segments.add(name);
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        return "/" + String.join("/", segments);
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
