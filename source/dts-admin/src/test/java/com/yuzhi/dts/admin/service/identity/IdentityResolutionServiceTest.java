package com.yuzhi.dts.admin.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.AdminRoleAssignment;
import com.yuzhi.dts.admin.domain.AdminRoleMember;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AdminRoleAssignmentRepository;
import com.yuzhi.dts.admin.repository.AdminRoleMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * F11 UT-010/UT-011/UT-012/UT-031 的可执行覆盖：稳定键优先、legacy 回退标记、
 * 范围随绑定保留。真实 Keycloak 联调与并发撤销由集成补证据。
 */
@ExtendWith(MockitoExtension.class)
class IdentityResolutionServiceTest {

    @Mock
    AdminKeycloakUserRepository users;

    @Mock
    AdminRoleMemberRepository roleMembers;

    @Mock
    AdminRoleAssignmentRepository roleAssignments;

    @InjectMocks
    IdentityResolutionService service;

    private static AdminKeycloakUser user(String kcId, String username) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setKeycloakId(kcId);
        user.setUsername(username);
        user.setFullName("测试用户");
        user.setPersonSecurityLevel("GENERAL");
        user.setDeptCode("1153");
        user.setEnabled(true);
        user.setAccessState("ACTIVE");
        return user;
    }

    @Test
    @DisplayName("F11-UT-010：缺稳定ID直接拒绝，不查用户名")
    void blankStableIdIsRejectedWithoutLookup() {
        assertThatThrownBy(() -> service.resolveByStableId("  "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveByStableId(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("F11-UT-011/012：稳定键优先；回填缺失时回退username并标记legacy")
    void stableKeyFirstWithMarkedLegacyFallback() {
        AdminKeycloakUser user = user("kc-1", "zhangsan");
        when(users.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));

        AdminRoleMember stable = new AdminRoleMember();
        stable.setRole("DEPT_DATA_OWNER");
        stable.setUsername("zhangsan");
        stable.setKeycloakId("kc-1");
        when(roleMembers.findByKeycloakId("kc-1")).thenReturn(List.of(stable));
        AdminRoleAssignment stableAssignment = new AdminRoleAssignment();
        stableAssignment.setRole("DEPT_DATA_OWNER");
        stableAssignment.setUsername("zhangsan");
        stableAssignment.setKeycloakId("kc-1");
        stableAssignment.setDisplayName("测试用户");
        stableAssignment.setUserSecurityLevel("GENERAL");
        stableAssignment.setOperationsCsv("read");
        when(roleAssignments.findByKeycloakId("kc-1")).thenReturn(List.of(stableAssignment));

        var resolved = service.resolveByStableId("kc-1");
        assertThat(resolved.roles()).containsExactly("ROLE_DEPT_DATA_OWNER");
        assertThat(resolved.legacyFallback()).isFalse();
        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.grants()).hasSize(1);
        assertThat(resolved.grants().getFirst().source())
            .isEqualTo(IdentityResolutionService.GrantSource.STABLE);

        // 回填缺失分支：双表均无稳定键时回退 username 并标记
        when(roleMembers.findByKeycloakId("kc-1")).thenReturn(List.of());
        when(roleAssignments.findByKeycloakId("kc-1")).thenReturn(List.of());
        AdminRoleMember legacy = new AdminRoleMember();
        legacy.setRole("dept_leader");
        legacy.setUsername("ZhangSan");
        when(roleMembers.findByUsernameIgnoreCase("zhangsan")).thenReturn(List.of(legacy));

        var fallback = service.resolveByStableId("kc-1");
        assertThat(fallback.roles()).containsExactly("ROLE_DEPT_LEADER");
        assertThat(fallback.legacyFallback()).isTrue();
    }

    @Test
    @DisplayName("F11-UT-031：assignment范围/动作随同一绑定保留，不展平为全局角色")
    void assignmentScopeTravelsWithBinding() {
        AdminKeycloakUser user = user("kc-2", "lisi");
        when(users.findByKeycloakId("kc-2")).thenReturn(Optional.of(user));
        AdminRoleMember stableMember = new AdminRoleMember();
        stableMember.setRole("DEPT_DATA_OWNER");
        stableMember.setUsername("lisi");
        stableMember.setKeycloakId("kc-2");
        when(roleMembers.findByKeycloakId("kc-2")).thenReturn(List.of(stableMember));

        AdminRoleAssignment assignment = new AdminRoleAssignment();
        assignment.setRole("DEPT_DATA_OWNER");
        assignment.setUsername("lisi");
        assignment.setKeycloakId("kc-2");
        assignment.setDisplayName("李四");
        assignment.setUserSecurityLevel("GENERAL");
        assignment.setScopeOrgId(42L);
        assignment.setDatasetIdsCsv("7,9");
        assignment.setOperationsCsv("read,export");
        when(roleAssignments.findByKeycloakId("kc-2")).thenReturn(List.of(assignment));

        var resolved = service.resolveByStableId("kc-2");
        assertThat(resolved.grants()).hasSize(1);
        var grant = resolved.grants().getFirst();
        assertThat(grant.scopeOrgId()).isEqualTo(42L);
        assertThat(grant.datasetIdsCsv()).isEqualTo("7,9");
        assertThat(grant.operationsCsv()).isEqualTo("read,export");
        assertThat(grant.source()).isEqualTo(IdentityResolutionService.GrantSource.STABLE);
    }

    @Test
    @DisplayName("F11-UT-013/071：停用或业务准入非ACTIVE均不可用，不恢复允许")
    void disabledOrSuspendedIsNotEnabled() {
        AdminKeycloakUser disabled = user("kc-3", "wangwu");
        disabled.setEnabled(false);
        when(users.findByKeycloakId("kc-3")).thenReturn(Optional.of(disabled));
        when(roleMembers.findByKeycloakId("kc-3")).thenReturn(List.of());
        when(roleAssignments.findByKeycloakId("kc-3")).thenReturn(List.of());
        assertThat(service.resolveByStableId("kc-3").enabled()).isFalse();

        AdminKeycloakUser suspended = user("kc-4", "zhaoliu");
        suspended.setAccessState("SUSPENDED");
        when(users.findByKeycloakId("kc-4")).thenReturn(Optional.of(suspended));
        when(roleMembers.findByKeycloakId("kc-4")).thenReturn(List.of());
        when(roleAssignments.findByKeycloakId("kc-4")).thenReturn(List.of());
        assertThat(service.resolveByStableId("kc-4").enabled()).isFalse();
    }
}
