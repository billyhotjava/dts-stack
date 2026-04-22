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
    void null_user_returns_none_snapshot() {
        PermissionSnapshot snap = service.snapshot(screen(10L), null, List.of());
        assertThat(snap.canRead()).isFalse();
    }
}
