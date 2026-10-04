package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenOwnershipServiceLocalTest {

    private AnalyticsScreenAccessRepository repo;
    private ScreenOwnershipService service;

    private AnalyticsScreenAccess access(Long id, Long screenId, String type, String granteeId, String perm) {
        AnalyticsScreenAccess a = new AnalyticsScreenAccess();
        a.setId(id);
        a.setScreenId(screenId);
        a.setGranteeType(type);
        a.setGranteeId(granteeId);
        a.setPermission(perm);
        a.setGrantedAt(Instant.now());
        return a;
    }

    private AnalyticsUser user(Long id, String platformUsername, String firstName, String lastName, String email) {
        AnalyticsUser u = new AnalyticsUser();
        u.setId(id);
        u.setPlatformUsername(platformUsername);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        return u;
    }

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(AnalyticsScreenAccessRepository.class);
        service = new ScreenOwnershipService(repo);
    }

    @Test
    void listGrants_returns_mapped_records() {
        when(repo.findByScreenId(1L)).thenReturn(List.of(
            access(10L, 1L, "USER", "42", "OWNER"),
            access(11L, 1L, "USER", "99", "VIEWER")));
        List<Map<String, Object>> grants = service.listGrants(1L);
        assertThat(grants).hasSize(2);
        assertThat(grants.get(0)).containsEntry("granteeId", "42");
        assertThat(grants.get(0)).containsEntry("permission", "OWNER");
    }

    @Test
    void createGrant_inserts_new_record() {
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.empty());
        ArgumentCaptor<AnalyticsScreenAccess> captor = ArgumentCaptor.forClass(AnalyticsScreenAccess.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "OWNER", 7L);

        verify(repo).save(captor.capture());
        AnalyticsScreenAccess saved = captor.getValue();
        assertThat(saved.getScreenId()).isEqualTo(1L);
        assertThat(saved.getGranteeId()).isEqualTo("42");
        assertThat(saved.getPermission()).isEqualTo("OWNER");
        assertThat(saved.getGrantedBy()).isEqualTo(7L);
    }

    @Test
    void createGrant_updates_existing_record() {
        AnalyticsScreenAccess existing = access(10L, 1L, "USER", "42", "VIEWER");
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "MANAGER", 7L);

        verify(repo).save(existing);
        assertThat(existing.getPermission()).isEqualTo("MANAGER");
    }

    @Test
    void removeAllGrants_deletes_by_screen_id() {
        service.removeAllGrants(1L);
        verify(repo).deleteByScreenId(1L);
    }

    @Test
    void revokeGrant_deletes_by_id() {
        service.revokeGrant(10L);
        verify(repo).deleteById(10L);
    }

    @Test
    void platform_mode_createGrant_writes_platform_asset_grant_only() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, client, true, true, false);
        when(client.upsertGrant(
            "SCREEN",
            "1",
            "ROLE",
            "ROLE_PTR",
            "READ",
            true,
            "7",
            "analytics_screen_permission:VIEWER"
        )).thenReturn(Map.of(
            "id", 99L,
            "granteeType", "ROLE",
            "granteeId", "ROLE_PTR",
            "permission", "READ",
            "levelOverride", true,
            "grantedBy", "7",
            "grantReason", "analytics_screen_permission:VIEWER",
            "grantedAt", Instant.parse("2026-05-11T00:00:00Z")
        ));

        AnalyticsScreenAccess grant = platformService.createGrant(1L, "ROLE", "ROLE_PTR", "VIEWER", 7L, true);

        assertThat(grant.getId()).isEqualTo(99L);
        assertThat(grant.getScreenId()).isEqualTo(1L);
        assertThat(grant.getPermission()).isEqualTo("VIEWER");
        assertThat(grant.isLevelOverride()).isTrue();
        verify(repo, never()).save(any());
    }

    @Test
    void platform_mode_listGrants_maps_manage_owner_reason_to_owner() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, client, true, false, false);
        when(client.listGrants("SCREEN", "1")).thenReturn(List.of(Map.of(
            "id", 99L,
            "granteeType", "USER",
            "granteeId", "xiezm",
            "permission", "MANAGE",
            "levelOverride", false,
            "grantedBy", "7",
            "grantReason", "analytics_screen_permission:OWNER",
            "grantedAt", Instant.parse("2026-05-11T00:00:00Z")
        )));

        List<Map<String, Object>> grants = platformService.listGrants(1L);

        assertThat(grants).hasSize(1);
        assertThat(grants.getFirst()).containsEntry("permission", "OWNER");
        assertThat(grants.getFirst()).containsEntry("screenId", 1L);
        verify(repo, never()).findByScreenId(any());
    }

    @Test
    void platform_mode_listGrants_enriches_platform_username_grant() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        AnalyticsUserRepository userRepo = Mockito.mock(AnalyticsUserRepository.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, userRepo, client, true, false, false);
        when(client.listGrants("SCREEN", "1")).thenReturn(List.of(Map.of(
            "id", 99L,
            "granteeType", "USER",
            "granteeId", "xiezm",
            "permission", "READ",
            "levelOverride", false,
            "grantedBy", "7",
            "grantReason", "analytics_screen_permission:VIEWER",
            "grantedAt", Instant.parse("2026-05-11T00:00:00Z")
        )));
        when(userRepo.findByPlatformUsernameIgnoreCase("xiezm"))
            .thenReturn(Optional.of(user(42L, "xiezm", "10S测试员工1", "", "xiezm@example.com")));

        List<Map<String, Object>> grants = platformService.listGrants(1L);

        assertThat(grants).hasSize(1);
        assertThat(grants.getFirst())
            .containsEntry("granteeUsername", "xiezm")
            .containsEntry("granteeName", "10S测试员工1");
    }

    @Test
    void platform_mode_listGrants_keeps_legacy_local_grants_visible() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        AnalyticsUserRepository userRepo = Mockito.mock(AnalyticsUserRepository.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, userRepo, client, true, true, false);
        when(client.listGrants("SCREEN", "1")).thenReturn(List.of(Map.of(
            "id", 99L,
            "granteeType", "ROLE",
            "granteeId", "DEPT_LEADER",
            "permission", "READ",
            "levelOverride", false,
            "grantedBy", "7",
            "grantReason", "analytics_screen_permission:VIEWER",
            "grantedAt", Instant.parse("2026-05-11T00:00:00Z")
        )));
        when(repo.findByScreenId(1L)).thenReturn(List.of(
            access(10L, 1L, "USER", "42", "VIEWER"),
            access(11L, 1L, "ROLE", "DEPT_LEADER", "VIEWER")
        ));
        when(userRepo.findById(42L)).thenReturn(Optional.of(user(42L, "xiezm", "10S测试员工1", "", "xiezm@example.com")));

        List<Map<String, Object>> grants = platformService.listGrants(1L);

        assertThat(grants).hasSize(2);
        assertThat(grants.get(0)).containsEntry("grantSource", "platform");
        assertThat(grants.get(1))
            .containsEntry("grantSource", "local")
            .containsEntry("granteeId", "42")
            .containsEntry("granteeUsername", "xiezm")
            .containsEntry("granteeName", "10S测试员工1");
    }

    @Test
    void platform_mode_revokeGrantForScreen_deletes_platform_grant() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, client, true, true, false);
        when(client.revokeGrant("SCREEN", "1", 99L)).thenReturn(true);

        boolean deleted = platformService.revokeGrantForScreen(99L, 1L);

        assertThat(deleted).isTrue();
        verify(repo, never()).deleteByIdAndScreenId(any(), any());
    }

    @Test
    void platform_mode_revokeGrantForScreen_can_delete_legacy_local_grant() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, client, true, true, false);
        when(client.revokeGrant("SCREEN", "1", 10L)).thenReturn(false);
        when(repo.deleteByIdAndScreenId(10L, 1L)).thenReturn(1);

        boolean deleted = platformService.revokeGrantForScreen(10L, 1L);

        assertThat(deleted).isTrue();
        verify(repo).deleteByIdAndScreenId(10L, 1L);
    }

    @Test
    void local_iam_read_only_blocks_local_write_when_platform_unavailable() {
        PlatformPermissionClient client = Mockito.mock(PlatformPermissionClient.class);
        ScreenOwnershipService platformService = new ScreenOwnershipService(repo, client, true, true, true, true);
        when(client.upsertGrant(
            "SCREEN",
            "1",
            "ROLE",
            "ROLE_PTR",
            "READ",
            false,
            "7",
            "analytics_screen_permission:VIEWER"
        )).thenThrow(new PlatformPermissionClient.PlatformPermissionException("platform down", new RuntimeException("boom")));

        assertThatThrownBy(() -> platformService.createGrant(1L, "ROLE", "ROLE_PTR", "VIEWER", 7L, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("platform permission service unavailable");
        verify(repo, never()).save(any());
    }
}
