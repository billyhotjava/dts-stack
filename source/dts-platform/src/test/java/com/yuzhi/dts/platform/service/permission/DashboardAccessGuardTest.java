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
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
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

    @Mock
    private CatalogConsumerClassificationService consumerClassificationService;

    private DashboardAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DashboardAccessGuard(grantRepository, consumerClassificationService);
    }

    // ---------------------------------------------------------------------
    // canView — 8 个核心场景
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("owner 密级不足且无越级审批时仍被阻断")
    void owner_withInsufficientLevel_isDenied() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller alice = caller("alice", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, alice);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
        assertThat(decision.overrideUsed()).isFalse();
    }

    @Test
    @DisplayName("owner 密级达标时可以查看大屏")
    void owner_withClearance_canView() {
        BiReportLink report = report("SECRET", null, null, "alice");
        Caller alice = caller("alice", IMPORTANT_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, alice);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("OWNER_PLUS_LEVEL");
        assertThat(decision.overrideUsed()).isFalse();
    }

    @Test
    @DisplayName("MANAGE grant 只提供基础访问，人员密级不足仍被阻断")
    void manageGrant_withInsufficientLevel_isDenied() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(eq("DASHBOARD"), eq(CODE), eq("bob"), any(Instant.class)))
            .thenReturn(List.of(grant("MANAGE", false)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_LEVEL_BLOCKED");
    }

    @Test
    @DisplayName("MANAGE grant 且人员密级达标时允许查看")
    void manageGrant_withClearance_canView() {
        BiReportLink report = report("SECRET", null, null, "alice");
        Caller bob = caller("bob", IMPORTANT_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(eq("DASHBOARD"), eq(CODE), eq("bob"), any(Instant.class)))
            .thenReturn(List.of(grant("MANAGE", false)));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("MANAGE_GRANT_PLUS_LEVEL");
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
    @DisplayName("内部裸大屏（roleCodes/deptCodes 都为空）不可全员可见")
    void unrestrictedInternalReport_deniedWithoutGrant() {
        BiReportLink report = report("INTERNAL", null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), null);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("公开大屏即便未命中 role/dept 也允许查看")
    void publicReport_anyUserCanView() {
        BiReportLink report = report("PUBLIC", "ROLE_FINANCE", "DEPT_A", "alice");
        Caller bob = caller("bob", Set.of("PUBLIC"), Set.of("ROLE_OTHER"), "DEPT_B");
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of());

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("PUBLIC");
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
    @DisplayName("内部大屏无角色 / 无部门 / 无共享 → DENY_NO_BASE_ACCESS")
    void noBase_deny() {
        BiReportLink report = report("INTERNAL", "ROLE_FINANCE", null, "alice");
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

    @Test
    @DisplayName("同步大屏公开密级无需授权即可查看")
    void syncedScreenPublic_canViewWithoutGrant() {
        BiReportLink report = screenReport("PUBLIC", "ROLE_PTR", "DEPT_A", "xiezm");
        Caller ptrdemo = caller("ptrdemo", Set.of("PUBLIC"), Set.of("ROLE_OTHER"), "DEPT_B");
        when(grantRepository.findActiveGrantsForUser(eq("SCREEN"), eq("7"), eq("ptrdemo"), any(), any(), any(Instant.class)))
            .thenReturn(List.of());

        AccessDecision decision = guard.canView(report, ptrdemo);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("同步大屏内部密级不再靠 roleCodes/deptCodes 放行，必须有 SCREEN grant")
    void syncedScreenInternal_requiresPlatformGrant() {
        BiReportLink report = screenReport("INTERNAL", "ROLE_PTR", "DEPT_A", "xiezm");
        Caller ptrdemo = caller("ptrdemo", GENERAL_LEVELS, Set.of("ROLE_PTR"), "DEPT_A");
        when(grantRepository.findActiveGrantsForUser(eq("SCREEN"), eq("7"), eq("ptrdemo"), any(), any(), any(Instant.class)))
            .thenReturn(List.of());

        AccessDecision decision = guard.canView(report, ptrdemo);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("同步大屏 ROLE 类型 SCREEN:7 READ 授权可见")
    void syncedScreenRoleGrant_canView() {
        BiReportLink report = screenReport("INTERNAL", null, null, "xiezm");
        Caller ptrdemo = caller("ptrdemo", GENERAL_LEVELS, Set.of("ROLE_PTR"), "DEPT_A");
        AssetGrant grant = grant("READ", false);
        grant.setAssetType("SCREEN");
        grant.setAssetId("7");
        grant.setGranteeType("ROLE");
        grant.setGranteeId("ROLE_PTR");
        when(grantRepository.findActiveGrantsForUser(
            eq("SCREEN"),
            eq("7"),
            eq("ptrdemo"),
            any(),
            eq("DEPT_A"),
            any(Instant.class)
        )).thenReturn(List.of(grant));

        AccessDecision decision = guard.canView(report, ptrdemo);

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
    @DisplayName("classification 为空时 fail-closed")
    void nullClassification_isDenied() {
        BiReportLink report = report(null, null, null, "alice");
        Caller bob = caller("bob", GENERAL_LEVELS, Set.of(), "DEPT_A");
        report.setDeptCodes("DEPT_A");

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_CLASSIFICATION_REQUIRED");
        verifyNoInteractions(grantRepository);
    }

    @Test
    @DisplayName("未知 classification 即使与用户声明相同也 fail-closed")
    void unknownClassification_isDenied() {
        BiReportLink report = report("UNKNOWN_LEVEL", null, null, "alice");
        Caller bob = caller("bob", Set.of("UNKNOWN_LEVEL"), Set.of(), "DEPT_A");
        report.setDeptCodes("DEPT_A");

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_CLASSIFICATION_REQUIRED");
        verifyNoInteractions(grantRepository);
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
    @DisplayName("上游升密使共享快照过期后，旧 VIEW grant 不再提供基础访问")
    void staleShareBinding_isDenied() {
        BiReportLink report = report("SECRET", null, null, "alice");
        report.setQueryDatasetId(java.util.UUID.randomUUID());
        Caller bob = caller("bob", IMPORTANT_LEVELS, Set.of(), null);
        AssetGrant staleGrant = grant("VIEW", false);
        staleGrant.setId(77L);
        when(grantRepository.findActiveUserGrants(any(), any(), any(), any())).thenReturn(List.of(staleGrant));
        when(
            consumerClassificationService.requireCurrentAccessBinding(
                "SHARE_GRANT",
                DashboardShareService.shareBindingKey(77L)
            )
        )
            .thenThrow(new IllegalStateException("classification snapshot changed"));

        AccessDecision decision = guard.canView(report, bob);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_NO_BASE_ACCESS");
    }

    @Test
    @DisplayName("superAdmin 旁路必须显式标记 override 供强审计")
    void superAdmin_override_isExplicit() {
        BiReportLink report = report("CONFIDENTIAL", null, null, "alice");
        Caller god = new Caller("god", GENERAL_LEVELS, Set.of(), null, false, true);

        AccessDecision decision = guard.canView(report, god);

        assertThat(decision.allow()).isTrue();
        assertThat(decision.reason()).isEqualTo("SUPER_ADMIN_OVERRIDE");
        assertThat(decision.overrideUsed()).isTrue();
        verifyNoInteractions(grantRepository);
    }

    @Test
    @DisplayName("superAdmin 也不能绕过缺失密级")
    void superAdmin_cannotBypassMissingClassification() {
        BiReportLink report = report(null, null, null, "alice");
        Caller god = new Caller("god", IMPORTANT_LEVELS, Set.of(), null, false, true);

        AccessDecision decision = guard.canView(report, god);

        assertThat(decision.allow()).isFalse();
        assertThat(decision.reason()).isEqualTo("DENY_CLASSIFICATION_REQUIRED");
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

    private BiReportLink screenReport(String classification, String roleCodes, String deptCodes, String createdBy) {
        BiReportLink r = report(classification, roleCodes, deptCodes, createdBy);
        r.setCode("screen-7");
        r.setReportType("SCREEN");
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
