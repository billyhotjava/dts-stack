package com.yuzhi.dts.admin.service.identity;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.domain.AdminRoleMember;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F11-T02 统一身份解析（admin 侧当前目录组装）。
 *
 * <p>授权统一用稳定账号键（kc_id）：
 *
 * <ul>
 *   <li>人员行按 kc_id 精确查找；username 只做展示，不做身份回退；</li>
 *   <li>角色/授权优先按 keycloak_id 读取；回填完成前缺失时才回退 username，并标记
 *       {@code legacyFallback=true} 供 T08 追踪（不静默当成稳定授权）；</li>
 *   <li>assignment 的组织/数据集/操作范围随同一绑定原样返回，不展平；</li>
 *   <li>Keycloak 三员/保留角色仍以 Keycloak 为权威源，本服务只组装 DTS 侧数据/自定义角色，
 *       合并规则（分域、禁止无条件 union 注入保留角色）由调用方按冻结矩阵执行。</li>
 * </ul>
 */
@Service
public class IdentityResolutionService {

    private final AdminKeycloakUserRepository users;
    private final AdminRoleMemberRepository roleMembers;
    private final AdminRoleAssignmentRepository roleAssignments;

    public IdentityResolutionService(
        AdminKeycloakUserRepository users,
        AdminRoleMemberRepository roleMembers,
        AdminRoleAssignmentRepository roleAssignments
    ) {
        this.users = users;
        this.roleMembers = roleMembers;
        this.roleAssignments = roleAssignments;
    }

    public record RoleGrant(
        String role,
        Long scopeOrgId,
        String datasetIdsCsv,
        String operationsCsv,
        GrantSource source
    ) {}

    public enum GrantSource {
        STABLE,
        LEGACY_USERNAME_FALLBACK
    }

    public record ResolvedIdentity(
        String stableId,
        String username,
        String displayName,
        String deptCode,
        String accessState,
        boolean enabled,
        List<String> roles,
        List<RoleGrant> grants,
        boolean legacyFallback
    ) {}

    @Transactional(readOnly = true)
    public ResolvedIdentity resolveByStableId(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalArgumentException("稳定账号 ID 不能为空；缺 ID 会话必须重新登录，不能按用户名补齐");
        }
        AdminKeycloakUser user = users.findByKeycloakId(keycloakId.trim())
            .orElseThrow(() -> new IdentityNotFoundException(keycloakId));
        return assemble(user);
    }

    private ResolvedIdentity assemble(AdminKeycloakUser user) {
        boolean legacyFallback = false;
        Set<String> roles = new LinkedHashSet<>();
        List<RoleGrant> grants = new ArrayList<>();

        List<AdminRoleMember> stableMembers = roleMembers.findByKeycloakId(user.getKeycloakId());
        if (stableMembers.isEmpty()) {
            legacyFallback = true;
            for (AdminRoleMember member : roleMembers.findByUsernameIgnoreCase(user.getUsername())) {
                addRole(roles, member.getRole());
            }
        } else {
            for (AdminRoleMember member : stableMembers) {
                addRole(roles, member.getRole());
            }
        }

        List<AdminRoleAssignment> stableAssignments = roleAssignments.findByKeycloakId(user.getKeycloakId());
        List<AdminRoleAssignment> effectiveAssignments;
        if (stableAssignments.isEmpty()) {
            legacyFallback = true;
            effectiveAssignments = roleAssignments.findByUsernameIgnoreCase(user.getUsername());
        } else {
            effectiveAssignments = stableAssignments;
        }
        GrantSource source = legacyFallback ? GrantSource.LEGACY_USERNAME_FALLBACK : GrantSource.STABLE;
        for (AdminRoleAssignment assignment : effectiveAssignments) {
            addRole(roles, assignment.getRole());
            grants.add(new RoleGrant(
                normalizeRole(assignment.getRole()),
                assignment.getScopeOrgId(),
                assignment.getDatasetIdsCsv(),
                assignment.getOperationsCsv(),
                source
            ));
        }

        boolean enabled = user.isEnabled() && "ACTIVE".equals(user.getAccessState());
        return new ResolvedIdentity(
            user.getKeycloakId(),
            user.getUsername(),
            user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName(),
            user.getDeptCode(),
            user.getAccessState(),
            enabled,
            List.copyOf(roles),
            List.copyOf(grants),
            legacyFallback
        );
    }

    private static void addRole(Set<String> roles, String role) {
        String normalized = normalizeRole(role);
        if (normalized != null) {
            roles.add(normalized);
        }
    }

    static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String upper = role.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    public static class IdentityNotFoundException extends RuntimeException {
        public IdentityNotFoundException(String keycloakId) {
            super("目录中不存在该稳定账号: " + Objects.toString(keycloakId));
        }
    }
}
