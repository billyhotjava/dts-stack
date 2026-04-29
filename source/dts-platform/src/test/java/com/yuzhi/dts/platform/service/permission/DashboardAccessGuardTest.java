package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard.AccessDecision;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard.Caller;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardAccessGuardTest {

    private static final String CODE = "dash-1";
    // 与 PersonnelLevel.GENERAL.allowedClassifications() / .IMPORTANT.allowedClassifications() 等价。
    // ClassificationUtils.currentAllowedClassifications() 在生产代码中负责注入这些 Set。
    private static final Set<String> GENERAL_LEVELS = Set.of("PUBLIC", "INTERNAL", "SECRET");
    private static final Set<String> IMPORTANT_LEVELS = Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");

    @Mock
    private AssetGrantRepository grantRepository;

    private DashboardAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DashboardAccessGuard(grantRepository);
    }

    // ---------------------------------------------------------------------
    // canView — 8 个核心场景
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("owner 即便密级低也能查看大屏")
    void owner_canView_evenIfLevelTooLow() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller alice = caller("alice", GENERAL_LEVELS, Set.of(), null);

        AccessDecision decision = guard.canView(report, alice);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("OWNER");
        assertThat(decision.overrideUsed()).isFalse();
        verifyNoInteractions(grantRepository);
    }

    @Test
    @DisplayName("被授予 MANAGE 的用户可以查看大屏（不受密级影响）")
    void manageGrant_canView() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(eq("DASHBOARD"), eq(CODE), eq("bob"), any(Instant.class)))
            .thenReturn(List.of(grant("MANAGE", false)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("MANAGE_GRANT");
    }

    @Test
    @DisplayName("角色匹配且密级达标 → 允许")
    void roleAndLevel_canView() {
        BiReportLink report = report("SECRET", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", IMPORTANT_LEVELS, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
        assertThat(decision.overrideUsed()).isFalse();
    }

    @Test
    @DisplayName("角色匹配但密级不够、无越级共享 → 拒绝（DENY_LEVEL_BLOCKED）")
    void roleButLevelTooLow_deny() {
        BiReportLink report = report("CONFIDENTIAL", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
    }

    @Test
    @DisplayName("角色匹配 + 密级不够 + 越级共享 → 允许并标记 overrideUsed")
    void roleAndOverrideGrant_allowOverride() {
        BiReportLink report = report("CONFIDENTIAL", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("VIEW", true)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("OVERRIDE_USED");
        assertThat(decision.overrideUsed()).isTrue();
    }

    @Test
    @DisplayName("角色不命中但持有 VIEW grant + 密级达标 → 允许")
    void roleMismatchButViewGrant_canView() {
        // 历史语义：roleCodes 不为空且 caller.roles 不命中 → role 维度 fail。
        // 此时 baseAccess 必须由显式 VIEW grant 兜底。
        BiReportLink report = report("INTERNAL", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", IMPORTANT_LEVELS, Set.of("ROLE_OTHER"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("VIEW", false)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    @Test
    @DisplayName("裸大屏（roleCodes/deptCodes 都为空）+ 密级达标 → 任意已登录用户可见")
    void unrestrictedReport_anyUserCanView() {
        // 历史语义：listPublished 把"未配置 roleCodes/deptCodes"视为不设限。
        // Guard 必须保留这条行为，否则现网"裸大屏"会全部不可见。
        BiReportLink report = report("INTERNAL", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    @Test
    @DisplayName("institutePrivileged 仅豁免部门门禁，不豁免角色门禁")
    void institutePrivileged_onlyBypassesDept() {
        BiReportLink report = report("INTERNAL", "ROLE_FINANCE", "DEPT_A", "alice");
        Caller bob = new Caller(
            "bob",
            IMPORTANT_LEVELS,
            Set.of("ROLE_OTHER"),  // 不含 ROLE_FINANCE
            "DEPT_B",              // 也不在 DEPT_A
            true,                  // institutePrivileged
            false
        );
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        // dept 门禁被豁免，但 role 仍 fail → baseAccess=false
        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("无角色 / 无部门 / 无共享 → DENY_NO_BASE_ACCESS")
    void noBase_deny() {
        BiReportLink report = report("PUBLIC", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", IMPORTANT_LEVELS, Set.of("ROLE_OTHER"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("仅部门匹配也算 baseAccess（且密级达标即放行）")
    void deptMatch_canView() {
        BiReportLink report = report("INTERNAL", null, "DEPT_A,DEPT_B", "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), "DEPT_B");
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    // ---------------------------------------------------------------------
    // canManage / canGrant / canRevoke — 关键策略 1 检查
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("canManage：owner 总是可以管理")
    void canManage_owner() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller alice = caller("alice", GENERAL_LEVELS, Set.of(), null);

        assertThat(guard.canManage(report, alice)).isTrue();
        verify(grantRepository, never()).findActiveUserGrants(any(), any(), any(), any());
    }

    @Test
    @DisplayName("canManage：被授予 MANAGE 的用户可以管理")
    void canManage_managerGrant() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("MANAGE", false)));

        assertThat(guard.canManage(report, bob)).isTrue();
    }

    @Test
    @DisplayName("canGrant：策略 1—— MANAGE 持有者不能再授予 MANAGE")
    void canGrantManage_managerForbidden() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("MANAGE", false)));

        // bob 是 manager，但只能授 VIEW，不能再授 MANAGE
        assertThat(guard.canGrant(report, bob, "VIEW")).isTrue();
        assertThat(guard.canGrant(report, bob, "MANAGE")).isFalse();
    }

    @Test
    @DisplayName("canGrant：owner 可以授 MANAGE 与 VIEW")
    void canGrant_ownerCanGrantBoth() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller alice = caller("alice", GENERAL_LEVELS, Set.of(), null);

        assertThat(guard.canGrant(report, alice, "MANAGE")).isTrue();
        assertThat(guard.canGrant(report, alice, "VIEW")).isTrue();
    }

    @Test
    @DisplayName("canRevoke：MANAGE 持有者只能撤 VIEW")
    void canRevoke_managerOnlyView() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("MANAGE", false)));

        AssetGrant viewGrant = grant("VIEW", false);
        AssetGrant manageGrant = grant("MANAGE", false);

        assertThat(guard.canRevoke(report, bob, viewGrant)).isTrue();
        assertThat(guard.canRevoke(report, bob, manageGrant)).isFalse();
    }

    // ---------------------------------------------------------------------
    // 边界 / 兜底
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("classification 为空视同 PUBLIC，无密级阻挡")
    void nullClassification_treatedAsPublic() {
        BiReportLink report = report(null, null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), "DEPT_A");
        report.setDeptCodes("DEPT_A");
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    @Test
    @DisplayName("allowedClassifications 不含目标密级 → DENY_LEVEL_BLOCKED")
    void levelNotInAllowedSet_deny() {
        BiReportLink report = report("CONFIDENTIAL", "ROLE_FINANCE", null, "alice");
        // GENERAL 等价的清单，不含 CONFIDENTIAL
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
    }

    @Test
    @DisplayName("superAdmin 短路全部检查")
    void superAdmin_shortCircuits() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller god = new Caller("god", GENERAL_LEVELS, Set.of(), null, false, true);

        AccessDecision decision = guard.canView(report, god);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("SUPER_ADMIN");
        verifyNoInteractions(grantRepository);
    }

    // ---------------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------------

    private BiReportLink report(String classification, String roleCodes, String deptCodes, String createdBy) {
        BiReportLink r = new BiReportLink();
        r.setCode(CODE);
        r.setClassification(classification);
        r.setRoleCodes(roleCodes);
        r.setDeptCodes(deptCodes);
        r.setCreatedBy(createdBy);
        r.setEnabled(true);
        return r;
    }

    private Caller caller(String username, Set<String> allowedClassifications, Set<String> roles, String dept) {
        return Caller.of(username, allowedClassifications, roles, dept);
    }

    private AssetGrant grant(String permission, boolean override) {
        AssetGrant g = new AssetGrant();
        g.setAssetType("DASHBOARD");
        g.setAssetId(CODE);
        g.setGranteeType("USER");
        g.setGranteeId("bob");
        g.setPermission(permission);
        g.setLevelOverride(override);
        g.setGrantedBy("alice");
        return g;
    }
}
