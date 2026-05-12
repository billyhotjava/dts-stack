package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.service.ScreenPermissionService.PermissionSnapshot;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenPermissionServiceTest {

    private AnalyticsScreenAccessRepository repo;
    private AnalyticsScreenRepository screenRepository;
    private ScreenPermissionService service;

    private AnalyticsUser user(long id, boolean superuser) {
        AnalyticsUser u = new AnalyticsUser();
        u.setId(id);
        u.setSuperuser(superuser);
        u.setEmail("user" + id + "@test.com");
        u.setFirstName("User");
        u.setLastName(String.valueOf(id));
        u.setPasswordHash("x");
        return u;
    }

    private AnalyticsScreen screen(long id) {
        AnalyticsScreen s = new AnalyticsScreen();
        s.setId(id);
        return s;
    }

    private AnalyticsScreenAccess access(long screenId, String granteeType, String granteeId, String permission) {
        AnalyticsScreenAccess a = new AnalyticsScreenAccess();
        a.setScreenId(screenId);
        a.setGranteeType(granteeType);
        a.setGranteeId(granteeId);
        a.setPermission(permission);
        return a;
    }

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(AnalyticsScreenAccessRepository.class);
        screenRepository = Mockito.mock(AnalyticsScreenRepository.class);
        when(screenRepository.findPublicIds()).thenReturn(List.of());
        service = new ScreenPermissionService(repo, screenRepository);
    }

    @Test
    void superuser_gets_all_permissions() {
        AnalyticsUser su = user(1L, true);
        PermissionSnapshot snap = service.snapshot(screen(10L), su, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void superuser_list_returns_sentinel() {
        AnalyticsUser su = user(1L, true);
        List<Long> ids = service.listAccessibleScreenIds(su, List.of());
        assertThat(service.isAllAccessible(ids)).isTrue();
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void owner_gets_all_permissions() {
        AnalyticsUser u = user(2L, false);
        when(repo.findGrantsForUser(eq(10L), eq("2"), any())).thenReturn(
            List.of(access(10L, "USER", "2", "OWNER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
    }

    @Test
    void creator_gets_all_permissions_without_grant() {
        AnalyticsUser u = user(22L, false);
        AnalyticsScreen s = screen(10L);
        s.setCreatorId(22L);

        PermissionSnapshot snap = service.snapshot(s, u, List.of());

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void manager_can_read_edit_and_manage_grants() {
        AnalyticsUser u = user(3L, false);
        when(repo.findGrantsForUser(eq(10L), eq("3"), any())).thenReturn(
            List.of(access(10L, "USER", "3", "MANAGER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
    }

    @Test
    void viewer_can_read_only() {
        AnalyticsUser u = user(4L, false);
        when(repo.findGrantsForUser(eq(10L), eq("4"), any())).thenReturn(
            List.of(access(10L, "USER", "4", "VIEWER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
    }

    @Test
    void no_grant_gets_none() {
        AnalyticsUser u = user(5L, false);
        when(repo.findGrantsForUser(eq(10L), eq("5"), any())).thenReturn(List.of());
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isFalse();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
    }

    @Test
    void role_grant_gives_viewer_permission() {
        AnalyticsUser u = user(6L, false);
        when(repo.findGrantsForUser(eq(10L), eq("6"), eq(List.of("ROLE_ANALYST")))).thenReturn(
            List.of(access(10L, "ROLE", "ROLE_ANALYST", "VIEWER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of("ROLE_ANALYST"));
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
    }

    @Test
    void role_grant_with_platform_clearance_can_read_internal_screen() {
        AnalyticsUser u = user(61L, false);
        when(repo.findGrantsForUser(eq(10L), eq("61"), eq(List.of("ROLE_PTR_DEMO")))).thenReturn(
            List.of(access(10L, "ROLE", "ROLE_PTR_DEMO", "VIEWER")));

        PermissionSnapshot snap = service.snapshot(
            screenWithLevel(10L, "INTERNAL"),
            u,
            new PlatformContext(null, "SECRET", "ROLE_PTR_DEMO")
        );

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
    }

    @Test
    void public_screen_without_grant_is_read_only() {
        AnalyticsUser u = user(62L, false);
        when(repo.findGrantsForUser(eq(10L), eq("62"), any())).thenReturn(List.of());

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "PUBLIC"), u, ctx(null));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
    }

    @Test
    void platform_context_roles_are_parsed_before_permission_lookup() {
        AnalyticsUser u = user(8L, false);
        when(repo.findGrantsForUser(eq(10L), eq("8"), eq(List.of("ROLE_ANALYST", "ROLE_OWNER")))).thenReturn(
                List.of(access(10L, "ROLE", "ROLE_ANALYST", "VIEWER")));

        PermissionSnapshot snap = service.snapshot(
                screen(10L),
                u,
                new PlatformContext(null, null, " ROLE_ANALYST, ROLE_OWNER , ROLE_ANALYST "));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
    }

    @Test
    void list_accessible_uses_no_role_sentinel_when_empty_roles() {
        AnalyticsUser u = user(7L, false);
        when(screenRepository.findIdsByCreatorIdAndArchivedFalse(7L)).thenReturn(List.of());
        when(repo.findAccessibleScreenIds(eq("7"), eq(List.of("__NO_ROLE__")))).thenReturn(List.of(1L, 2L));
        List<Long> ids = service.listAccessibleScreenIds(u, List.of());
        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    void list_accessible_includes_owned_screens_even_without_grant() {
        AnalyticsUser u = user(9L, false);
        when(screenRepository.findIdsByCreatorIdAndArchivedFalse(9L)).thenReturn(List.of(101L));
        when(repo.findAccessibleScreenIds(eq("9"), eq(List.of("__NO_ROLE__")))).thenReturn(List.of(202L));

        List<Long> ids = service.listAccessibleScreenIds(u, List.of());

        assertThat(ids).containsExactly(101L, 202L);
    }

    @Test
    void list_accessible_includes_public_screens_even_without_grant() {
        AnalyticsUser u = user(21L, false);
        when(screenRepository.findIdsByCreatorIdAndArchivedFalse(21L)).thenReturn(List.of());
        when(screenRepository.findPublicIds()).thenReturn(List.of(303L));
        when(repo.findAccessibleScreenIds(eq("21"), eq(List.of("__NO_ROLE__")))).thenReturn(List.of());

        List<Long> ids = service.listAccessibleScreenIds(u, List.of());

        assertThat(ids).containsExactly(303L);
    }

    @Test
    void platform_permission_allows_read_without_local_lookup() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenPermissionService platformService = new ScreenPermissionService(repo, screenRepository, client, true, true);
        AnalyticsUser u = user(41L, false);
        u.setPlatformUsername("ptrdemo");
        AnalyticsScreen s = screenWithLevel(10L, "INTERNAL");
        when(client.check("ptrdemo", "ROLE_PTR", "D01", "SCREEN", "10", "INTERNAL", "INTERNAL"))
            .thenReturn(new PlatformPermissionClient.PermissionResult(true, "READ", "explicit_grant"));

        PermissionSnapshot snap = platformService.snapshot(s, u, new PlatformContext("D01", "INTERNAL", "ROLE_PTR"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        verify(repo, never()).findGrantsForUser(any(), any(), any());
    }

    @Test
    void platform_mode_creator_keeps_owner_permissions_even_when_platform_returns_read() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenPermissionService platformService = new ScreenPermissionService(repo, screenRepository, client, true, false);
        AnalyticsUser u = user(44L, false);
        u.setPlatformUsername("creator");
        AnalyticsScreen s = screenWithLevel(10L, "CONFIDENTIAL");
        s.setCreatorId(44L);
        when(client.check("creator", "ROLE_PTR", "D01", "SCREEN", "10", "PUBLIC", "CONFIDENTIAL"))
            .thenReturn(new PlatformPermissionClient.PermissionResult(true, "READ", "explicit_grant"));

        PermissionSnapshot snap = platformService.snapshot(s, u, new PlatformContext("D01", "PUBLIC", "ROLE_PTR"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
        assertThat(snap.overrideUsed()).isFalse();
        verify(client, never()).check(any(), any(), any(), any(), any(), any(), any());
        verify(repo, never()).findGrantsForUser(any(), any(), any());
    }

    @Test
    void platform_permission_denied_can_fallback_to_local_read_only_grant() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenPermissionService platformService = new ScreenPermissionService(repo, screenRepository, client, true, true);
        AnalyticsUser u = user(42L, false);
        when(client.check("42", "", null, "SCREEN", "10", "INTERNAL", "INTERNAL"))
            .thenReturn(PlatformPermissionClient.PermissionResult.DENIED);
        when(repo.findGrantsForUser(eq(10L), eq("42"), eq(List.of("__NO_ROLE__")))).thenReturn(
            List.of(access(10L, "USER", "42", "VIEWER")));

        PermissionSnapshot snap = platformService.snapshot(screenWithLevel(10L, "INTERNAL"), u, List.of(), "INTERNAL");

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
    }

    @Test
    void platform_accessible_ids_are_used_when_platform_source_enabled() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenPermissionService platformService = new ScreenPermissionService(repo, screenRepository, client, true, false);
        AnalyticsUser u = user(43L, false);
        u.setPlatformUsername("ptrdemo");
        when(client.listAccessibleAssetIds("ptrdemo", "ROLE_PTR", "D01", "SCREEN", 0, 10000, "INTERNAL"))
            .thenReturn(new PlatformPermissionClient.AccessibleAssetsResult(List.of("101", "bad-id"), 1, "FILTERED"));

        List<Long> ids = platformService.listAccessibleScreenIds(u, new PlatformContext("D01", "INTERNAL", "ROLE_PTR"));

        assertThat(ids).containsExactly(101L);
        verify(repo, never()).findAccessibleScreenIds(any(), any());
    }

    @Test
    void null_user_returns_none_snapshot() {
        PermissionSnapshot snap = service.snapshot(screen(10L), null, List.of());
        assertThat(snap.canRead()).isFalse();
    }

    // -----------------------------------------------------------------
    // 密级闸门 + 越级共享
    // -----------------------------------------------------------------

    private AnalyticsScreen screenWithLevel(long id, String classification) {
        AnalyticsScreen s = screen(id);
        s.setClassification(classification);
        return s;
    }

    private AnalyticsScreenAccess viewerAccess(long screenId, String granteeId, boolean override) {
        AnalyticsScreenAccess a = access(screenId, "USER", granteeId, "VIEWER");
        a.setLevelOverride(override);
        return a;
    }

    private PlatformContext ctx(String classification) {
        return new PlatformContext(null, classification, null);
    }

    @Test
    void viewer_with_clearance_meets_screen_classification_returns_readOnly() {
        AnalyticsUser u = user(11L, false);
        when(repo.findGrantsForUser(eq(10L), eq("11"), any()))
            .thenReturn(List.of(viewerAccess(10L, "11", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "SECRET"), u, ctx("SECRET"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.overrideUsed()).isFalse();
    }

    @Test
    void viewer_with_insufficient_clearance_no_override_returns_none() {
        AnalyticsUser u = user(12L, false);
        when(repo.findGrantsForUser(eq(10L), eq("12"), any()))
            .thenReturn(List.of(viewerAccess(10L, "12", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "CONFIDENTIAL"), u, ctx("INTERNAL"));

        assertThat(snap.canRead()).isFalse();
        assertThat(snap.overrideUsed()).isFalse();
    }

    @Test
    void viewer_with_insufficient_clearance_but_override_grant_returns_readOnlyOverride() {
        AnalyticsUser u = user(13L, false);
        when(repo.findGrantsForUser(eq(10L), eq("13"), any()))
            .thenReturn(List.of(viewerAccess(10L, "13", true)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "CONFIDENTIAL"), u, ctx("INTERNAL"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
        assertThat(snap.overrideUsed()).isTrue();
    }

    @Test
    void screen_classification_null_treated_as_public_allows_viewer() {
        AnalyticsUser u = user(14L, false);
        when(repo.findGrantsForUser(eq(10L), eq("14"), any()))
            .thenReturn(List.of(viewerAccess(10L, "14", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, null), u, ctx(null));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.overrideUsed()).isFalse();
    }

    @Test
    void caller_classification_null_with_screen_classified_denies_viewer() {
        AnalyticsUser u = user(15L, false);
        when(repo.findGrantsForUser(eq(10L), eq("15"), any()))
            .thenReturn(List.of(viewerAccess(10L, "15", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "SECRET"), u, ctx(null));

        assertThat(snap.canRead()).isFalse();
    }

    @Test
    void clearance_below_one_step_denies() {
        AnalyticsUser u = user(16L, false);
        when(repo.findGrantsForUser(eq(10L), eq("16"), any()))
            .thenReturn(List.of(viewerAccess(10L, "16", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "SECRET"), u, ctx("INTERNAL"));

        assertThat(snap.canRead()).isFalse();
    }

    @Test
    void clearance_above_screen_level_allows() {
        AnalyticsUser u = user(17L, false);
        when(repo.findGrantsForUser(eq(10L), eq("17"), any()))
            .thenReturn(List.of(viewerAccess(10L, "17", false)));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "INTERNAL"), u, ctx("CONFIDENTIAL"));

        assertThat(snap.canRead()).isTrue();
    }

    @Test
    void owner_grant_bypasses_classification_gate() {
        // OWNER grant 即便 caller 密级低于 screen 密级也应放行，与既有 bypass 语义一致。
        AnalyticsUser u = user(18L, false);
        when(repo.findGrantsForUser(eq(10L), eq("18"), any()))
            .thenReturn(List.of(access(10L, "USER", "18", "OWNER")));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "CONFIDENTIAL"), u, ctx("PUBLIC"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
        assertThat(snap.overrideUsed()).isFalse();
    }

    @Test
    void manager_grant_bypasses_classification_gate() {
        AnalyticsUser u = user(19L, false);
        when(repo.findGrantsForUser(eq(10L), eq("19"), any()))
            .thenReturn(List.of(access(10L, "USER", "19", "MANAGER")));

        PermissionSnapshot snap = service.snapshot(screenWithLevel(10L, "CONFIDENTIAL"), u, ctx("PUBLIC"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.overrideUsed()).isFalse();
    }

    @Test
    void creator_bypasses_classification_gate() {
        AnalyticsUser u = user(20L, false);
        AnalyticsScreen s = screenWithLevel(10L, "CONFIDENTIAL");
        s.setCreatorId(20L);

        PermissionSnapshot snap = service.snapshot(s, u, ctx("PUBLIC"));

        assertThat(snap.canRead()).isTrue();
        assertThat(snap.isOwner()).isTrue();
        Mockito.verifyNoInteractions(repo);
    }
}
