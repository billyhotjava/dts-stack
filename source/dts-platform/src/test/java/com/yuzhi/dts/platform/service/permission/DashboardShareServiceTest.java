package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard.Caller;
import com.yuzhi.dts.platform.service.permission.dto.AssetGrantDto;
import com.yuzhi.dts.platform.service.permission.dto.DashboardShareRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardShareServiceTest {

    private static final UUID REPORT_ID = UUID.randomUUID();
    private static final String CODE = "dash-share-1";

    @Mock
    private BiReportLinkRepository reportRepo;

    @Mock
    private AssetGrantRepository grantRepo;

    @Mock
    private DashboardAccessGuard accessGuard;

    @Mock
    private DashboardCallerResolver callerResolver;

    @Mock
    private AuditService audit;

    private DashboardShareService service;

    @BeforeEach
    void setUp() {
        service = new DashboardShareService(reportRepo, grantRepo, accessGuard, callerResolver, audit);
    }

    // ---------------------------------------------------------------------
    // share() — owner / manager / 策略 1 / 边界
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("share：owner 给别人授 VIEW 成功，AssetGrant 字段都正确")
    void share_ownerGrantsView_persistsCorrectly() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canGrant(eq(report), any(), eq("VIEW"))).thenReturn(true);
        when(grantRepo.save(any(AssetGrant.class))).thenAnswer(inv -> {
            AssetGrant g = inv.getArgument(0);
            g.setId(42L);
            return g;
        });

        AssetGrantDto dto = service.share(
            REPORT_ID,
            new DashboardShareRequest("bob", "view", false, "财报会议授权")
        );

        ArgumentCaptor<AssetGrant> captor = ArgumentCaptor.forClass(AssetGrant.class);
        verify(grantRepo).save(captor.capture());
        AssetGrant saved = captor.getValue();
        assertThat(saved.getAssetType()).isEqualTo("DASHBOARD");
        assertThat(saved.getAssetId()).isEqualTo(CODE);
        assertThat(saved.getGranteeType()).isEqualTo("USER");
        assertThat(saved.getGranteeId()).isEqualTo("bob");
        assertThat(saved.getPermission()).isEqualTo("VIEW");
        assertThat(saved.isLevelOverride()).isFalse();
        assertThat(saved.getGrantedBy()).isEqualTo("alice");
        assertThat(saved.getGrantReason()).isEqualTo("财报会议授权");

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.permission()).isEqualTo("VIEW");
        verify(audit).audit(eq("GRANT"), eq("vis.dashboard.share"), anyString());
    }

    @Test
    @DisplayName("share：owner 授 MANAGE 成功（策略 1 仅 owner 可授 MANAGE）")
    void share_ownerGrantsManage_succeeds() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canGrant(eq(report), any(), eq("MANAGE"))).thenReturn(true);
        when(grantRepo.save(any(AssetGrant.class))).thenAnswer(inv -> {
            AssetGrant g = inv.getArgument(0);
            g.setId(7L);
            return g;
        });

        AssetGrantDto dto = service.share(
            REPORT_ID,
            new DashboardShareRequest("bob", "MANAGE", true, null)  // levelOverride 对 MANAGE 应被忽略
        );

        ArgumentCaptor<AssetGrant> captor = ArgumentCaptor.forClass(AssetGrant.class);
        verify(grantRepo).save(captor.capture());
        AssetGrant saved = captor.getValue();
        assertThat(saved.getPermission()).isEqualTo("MANAGE");
        // levelOverride 仅对 VIEW 有意义；MANAGE 时必须为 false
        assertThat(saved.isLevelOverride()).isFalse();
        assertThat(dto.levelOverride()).isFalse();
    }

    @Test
    @DisplayName("share：levelOverride=true + permission=VIEW → 落库为越级共享")
    void share_viewWithLevelOverride_marksOverride() {
        BiReportLink report = report("CONFIDENTIAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canGrant(eq(report), any(), eq("VIEW"))).thenReturn(true);
        when(grantRepo.save(any(AssetGrant.class))).thenAnswer(inv -> inv.getArgument(0));

        service.share(
            REPORT_ID,
            new DashboardShareRequest("low_clearance_user", "view", true, "ad-hoc 越级")
        );

        ArgumentCaptor<AssetGrant> captor = ArgumentCaptor.forClass(AssetGrant.class);
        verify(grantRepo).save(captor.capture());
        assertThat(captor.getValue().isLevelOverride()).isTrue();
    }

    @Test
    @DisplayName("share：策略 1 —— manager 试图授 MANAGE 时 Guard 拒绝，返回 AccessDeniedException + audit failure")
    void share_managerCannotGrantManage_denied() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerManager("bob"));
        when(accessGuard.canGrant(eq(report), any(), eq("MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> service.share(
            REPORT_ID,
            new DashboardShareRequest("carol", "MANAGE", false, null)
        )).isInstanceOf(AccessDeniedException.class);

        verify(grantRepo, never()).save(any(AssetGrant.class));
        verify(audit).auditFailure(eq("GRANT"), eq("vis.dashboard.share"), eq(CODE), anyString());
    }

    @Test
    @DisplayName("share：给自己授权 → IllegalArgumentException（避免污染审计）")
    void share_toSelf_rejected() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canGrant(eq(report), any(), eq("VIEW"))).thenReturn(true);

        assertThatThrownBy(() -> service.share(
            REPORT_ID,
            new DashboardShareRequest("alice", "VIEW", false, null)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("yourself");

        verify(grantRepo, never()).save(any(AssetGrant.class));
    }

    @Test
    @DisplayName("share：未知 permission → IllegalArgumentException（不进 Guard）")
    void share_unknownPermission_rejected() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());

        assertThatThrownBy(() -> service.share(
            REPORT_ID,
            new DashboardShareRequest("bob", "EXECUTE", false, null)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("VIEW")
          .hasMessageContaining("MANAGE");
    }

    @Test
    @DisplayName("share：报表 id 不存在 → IllegalArgumentException(not_found)")
    void share_reportMissing_rejected() {
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.share(
            REPORT_ID,
            new DashboardShareRequest("bob", "VIEW", false, null)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not_found");
    }

    // ---------------------------------------------------------------------
    // revoke() — Guard 决定 + 跨大屏防御
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("revoke：owner 撤销 VIEW grant → 删除 + audit")
    void revoke_owner_succeeds() {
        BiReportLink report = report("INTERNAL", "alice");
        AssetGrant target = grant("VIEW", false, CODE);
        target.setId(99L);
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(grantRepo.findById(99L)).thenReturn(Optional.of(target));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canRevoke(eq(report), any(), eq(target))).thenReturn(true);

        service.revoke(REPORT_ID, 99L);

        verify(grantRepo).delete(target);
        verify(audit).audit(eq("REVOKE"), eq("vis.dashboard.share"), anyString());
    }

    @Test
    @DisplayName("revoke：grant 不属于该 dashboard → IllegalArgumentException（防跨大屏越权）")
    void revoke_grantOnDifferentDashboard_rejected() {
        BiReportLink report = report("INTERNAL", "alice");
        AssetGrant otherGrant = grant("VIEW", false, "dash-other"); // assetId 不一致
        otherGrant.setId(101L);
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(grantRepo.findById(101L)).thenReturn(Optional.of(otherGrant));

        assertThatThrownBy(() -> service.revoke(REPORT_ID, 101L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong");

        verify(grantRepo, never()).delete(any(AssetGrant.class));
    }

    @Test
    @DisplayName("revoke：Guard 拒绝 → AccessDeniedException + audit failure")
    void revoke_unauthorized_denied() {
        BiReportLink report = report("INTERNAL", "alice");
        AssetGrant target = grant("MANAGE", false, CODE);
        target.setId(55L);
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(grantRepo.findById(55L)).thenReturn(Optional.of(target));
        when(callerResolver.current()).thenReturn(callerManager("bob"));
        // manager 不能撤 MANAGE 类 grant
        when(accessGuard.canRevoke(eq(report), any(), eq(target))).thenReturn(false);

        assertThatThrownBy(() -> service.revoke(REPORT_ID, 55L))
            .isInstanceOf(AccessDeniedException.class);

        verify(grantRepo, never()).delete(any(AssetGrant.class));
        verify(audit).auditFailure(eq("REVOKE"), eq("vis.dashboard.share"), eq(CODE), anyString());
    }

    // ---------------------------------------------------------------------
    // listGrants() — 仅 manager 可见
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("listGrants：manager 能列出该 dashboard 的所有 grant")
    void listGrants_manager_returnsAll() {
        BiReportLink report = report("INTERNAL", "alice");
        AssetGrant g1 = grant("VIEW", false, CODE);
        g1.setId(1L);
        g1.setGranteeId("u1");
        AssetGrant g2 = grant("MANAGE", false, CODE);
        g2.setId(2L);
        g2.setGranteeId("u2");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerOwner());
        when(accessGuard.canManage(eq(report), any())).thenReturn(true);
        when(grantRepo.findByAssetTypeAndAssetId("DASHBOARD", CODE)).thenReturn(List.of(g1, g2));

        List<AssetGrantDto> result = service.listGrants(REPORT_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AssetGrantDto::granteeId).containsExactly("u1", "u2");
        verify(audit, never()).auditFailure(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("listGrants：非 manager 调用 → AccessDeniedException + audit failure")
    void listGrants_nonManager_denied() {
        BiReportLink report = report("INTERNAL", "alice");
        when(reportRepo.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(callerResolver.current()).thenReturn(callerManager("eve"));
        when(accessGuard.canManage(eq(report), any())).thenReturn(false);

        assertThatThrownBy(() -> service.listGrants(REPORT_ID))
            .isInstanceOf(AccessDeniedException.class);

        verify(audit, times(1)).auditFailure(eq("READ"), eq("vis.dashboard.share"), eq(CODE), anyString());
    }

    // ---------------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------------

    private BiReportLink report(String classification, String createdBy) {
        BiReportLink r = new BiReportLink();
        r.setId(REPORT_ID);
        r.setCode(CODE);
        r.setClassification(classification);
        r.setCreatedBy(createdBy);
        r.setEnabled(true);
        return r;
    }

    private AssetGrant grant(String permission, boolean override, String assetCode) {
        AssetGrant g = new AssetGrant();
        g.setAssetType("DASHBOARD");
        g.setAssetId(assetCode);
        g.setGranteeType("USER");
        g.setGranteeId("anyone");
        g.setPermission(permission);
        g.setLevelOverride(override);
        g.setGrantedBy("system");
        return g;
    }

    private Caller callerOwner() {
        return new Caller(
            "alice",
            Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL"),
            Set.of("ROLE_DASHBOARD_OWNER"),
            null,
            false,
            false
        );
    }

    private Caller callerManager(String username) {
        return new Caller(
            username,
            Set.of("PUBLIC", "INTERNAL", "SECRET"),
            Set.of("ROLE_EMPLOYEE"),
            null,
            false,
            false
        );
    }
}
