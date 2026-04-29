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
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
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
        Caller alice = caller("alice", PersonnelLevel.GENERAL, Set.of(), null);

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
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), null);
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
        Caller bob = caller("bob", PersonnelLevel.IMPORTANT, Set.of("ROLE_FINANCE"), null);
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
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
    }

    @Test
    @DisplayName("角色匹配 + 密级不够 + 越级共享 → 允许并标记 overrideUsed")
    void roleAndOverrideGrant_allowOverride() {
        BiReportLink report = report("CONFIDENTIAL", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("VIEW", true)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("OVERRIDE_USED");
        assertThat(decision.overrideUsed()).isTrue();
    }

    @Test
    @DisplayName("仅有 VIEW 共享、无角色无部门 + 密级达标 → 允许")
    void shareOnlyAndLevel_canView() {
        BiReportLink report = report("INTERNAL", null, null, "alice");
        Caller bob = caller("bob", PersonnelLevel.IMPORTANT, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("VIEW", false)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    @Test
    @DisplayName("无角色 / 无部门 / 无共享 → DENY_NO_BASE_ACCESS")
    void noBase_deny() {
        BiReportLink report = report("PUBLIC", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", PersonnelLevel.IMPORTANT, Set.of("ROLE_OTHER"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("仅部门匹配也算 baseAccess（且密级达标即放行）")
    void deptMatch_canView() {
        BiReportLink report = report("INTERNAL", null, "DEPT_A,DEPT_B", "alice");
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), "DEPT_B");
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
        Caller alice = caller("alice", PersonnelLevel.GENERAL, Set.of(), null);

        assertThat(guard.canManage(report, alice)).isTrue();
        verify(grantRepository, never()).findActiveUserGrants(any(), any(), any(), any());
    }

    @Test
    @DisplayName("canManage：被授予 MANAGE 的用户可以管理")
    void canManage_managerGrant() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any()))
            .thenReturn(List.of(grant("MANAGE", false)));

        assertThat(guard.canManage(report, bob)).isTrue();
    }

    @Test
    @DisplayName("canGrant：策略 1—— MANAGE 持有者不能再授予 MANAGE")
    void canGrantManage_managerForbidden() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), null);
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
        Caller alice = caller("alice", PersonnelLevel.GENERAL, Set.of(), null);

        assertThat(guard.canGrant(report, alice, "MANAGE")).isTrue();
        assertThat(guard.canGrant(report, alice, "VIEW")).isTrue();
    }

    @Test
    @DisplayName("canRevoke：MANAGE 持有者只能撤 VIEW")
    void canRevoke_managerOnlyView() {
        BiReportLink report = report("PUBLIC", null, null, "alice");
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), null);
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
        Caller bob = caller("bob", PersonnelLevel.GENERAL, Set.of(), "DEPT_A");
        report.setDeptCodes("DEPT_A");
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("BASE_ACCESS_PLUS_LEVEL");
    }

    @Test
    @DisplayName("personnelLevel 为 null 兜底为 GENERAL（最严）")
    void nullPersonnelLevel_treatedAsGeneral() {
        BiReportLink report = report("CONFIDENTIAL", "ROLE_FINANCE", null, "alice");
        Caller bob = caller("bob", null, Set.of("ROLE_FINANCE"), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        // GENERAL 不允许 CONFIDENTIAL
        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
    }

    @Test
    @DisplayName("superAdmin 短路全部检查")
    void superAdmin_shortCircuits() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller god = new Caller("god", PersonnelLevel.GENERAL, Set.of(), null, false, true);

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

    private Caller caller(String username, PersonnelLevel level, Set<String> roles, String dept) {
        return Caller.of(username, level, roles, dept);
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
