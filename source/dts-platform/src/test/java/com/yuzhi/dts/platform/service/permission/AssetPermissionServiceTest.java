package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AssetPermissionServiceTest {

    @Mock
    private AssetOwnershipRepository ownershipRepository;
    @Mock
    private AssetGrantRepository grantRepository;

    private AssetPermissionService service;

    @BeforeEach
    void setUp() {
        service = new AssetPermissionService(ownershipRepository, grantRepository);
    }

    // --- Superuser roles ---

    @Test
    void sysAdmin_shouldHaveManageOnAnyAsset() {
        PermissionResult result = service.check("admin", List.of("ROLE_OP_ADMIN"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("MANAGE");
        assertThat(result.reason()).isEqualTo("superuser");
    }

    @Test
    void sysAdmin_shouldHaveManageOnCrossDeptAsset() {
        PermissionResult result = service.check("admin", List.of("ROLE_ADMIN"), "DEPT_B", "DASHBOARD", "99");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("MANAGE");
    }

    // --- Institute roles ---

    @Test
    void instLeader_shouldHaveReadOnAnyAsset() {
        PermissionResult result = service.check("leader", List.of("ROLE_INST_LEADER"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("READ");
        assertThat(result.reason()).isEqualTo("inst_read");
    }

    @Test
    void instDataOwner_shouldHaveManageOnAnyAsset() {
        PermissionResult result = service.check("owner", List.of("ROLE_INST_DATA_OWNER"), "DEPT_A", "SCREEN", "5");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("MANAGE");
        assertThat(result.reason()).isEqualTo("inst_manage");
    }

    // --- Department roles with ownership ---

    @Test
    void deptLeader_shouldReadOwnDeptAsset() {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setOwnerDeptCode("DEPT_A");
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.of(ownership));

        PermissionResult result = service.check("leader", List.of("ROLE_DEPT_LEADER"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("READ");
        assertThat(result.reason()).isEqualTo("dept_ownership");
    }

    @Test
    void deptDataOwner_shouldManageOwnDeptAsset() {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setOwnerDeptCode("DEPT_A");
        when(ownershipRepository.findByAssetTypeAndAssetId("CARD", "10")).thenReturn(Optional.of(ownership));

        PermissionResult result = service.check("mgr", List.of("ROLE_DEPT_DATA_OWNER"), "DEPT_A", "CARD", "10");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("MANAGE");
    }

    @Test
    void deptLeader_shouldBeDeniedCrossDeptAsset() {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setOwnerDeptCode("DEPT_B");
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.of(ownership));
        when(grantRepository.findActiveGrantsForUser(anyString(), anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
            .thenReturn(List.of());

        PermissionResult result = service.check("leader", List.of("ROLE_DEPT_LEADER"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isFalse();
    }

    // --- Employee always needs explicit grant ---

    @Test
    void employee_shouldBeDeniedWithoutGrant() {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setOwnerDeptCode("DEPT_A");
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.of(ownership));
        when(grantRepository.findActiveGrantsForUser(anyString(), anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
            .thenReturn(List.of());

        PermissionResult result = service.check("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void employee_shouldBeAllowedWithGrant() {
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.empty());

        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grantRepository.findActiveGrantsForUser(eq("TABLE"), eq("1"), eq("emp"), anyList(), anyString(), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionResult result = service.check("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("READ");
        assertThat(result.reason()).isEqualTo("explicit_grant");
    }

    @Test
    void employee_shouldGetHighestPermissionFromMultipleGrants() {
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.empty());

        AssetGrant readGrant = new AssetGrant();
        readGrant.setPermission("READ");
        AssetGrant editGrant = new AssetGrant();
        editGrant.setPermission("EDIT");

        when(grantRepository.findActiveGrantsForUser(eq("TABLE"), eq("1"), eq("emp"), anyList(), anyString(), any(Instant.class)))
            .thenReturn(List.of(readGrant, editGrant));

        PermissionResult result = service.check("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "TABLE", "1");
        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("EDIT");
    }

    // --- Batch check ---

    @Test
    void batchCheck_shouldShortCircuitForSuperuser() {
        List<AssetRef> assets = List.of(
            new AssetRef("TABLE", "1"),
            new AssetRef("CARD", "2"),
            new AssetRef("DASHBOARD", "3")
        );

        Map<String, PermissionResult> results = service.batchCheck("admin", List.of("ROLE_OP_ADMIN"), "DEPT_A", assets);
        assertThat(results).hasSize(3);
        assertThat(results.values()).allMatch(r -> r.allowed() && "MANAGE".equals(r.permission()));
        // Should not call repositories (short-circuit)
        verifyNoInteractions(ownershipRepository, grantRepository);
    }

    // --- Accessible IDs ---

    @Test
    void accessibleIds_shouldReturnAllForGlobalRoles() {
        AccessibleAssetsResult result = service.listAccessibleAssetIds(
            "admin", List.of("ROLE_OP_ADMIN"), "DEPT_A", "TABLE", PageRequest.of(0, 100)
        );
        assertThat(result.scope()).isEqualTo("ALL");
        verifyNoInteractions(ownershipRepository, grantRepository);
    }

    @Test
    void accessibleIds_shouldReturnFilteredForDeptLeader() {
        when(ownershipRepository.findAssetIdsByTypeAndDeptCode("TABLE", "DEPT_A"))
            .thenReturn(List.of("1", "2", "3"));
        when(grantRepository.findAccessibleAssetIdsByGrant(eq("TABLE"), eq("leader"), anyList(), eq("DEPT_A"), any(Instant.class)))
            .thenReturn(List.of("5"));

        AccessibleAssetsResult result = service.listAccessibleAssetIds(
            "leader", List.of("ROLE_DEPT_LEADER"), "DEPT_A", "TABLE", PageRequest.of(0, 100)
        );
        assertThat(result.scope()).isEqualTo("FILTERED");
        assertThat(result.assetIds()).containsExactlyInAnyOrder("1", "2", "3", "5");
        assertThat(result.total()).isEqualTo(4);
    }

    // --- Null / edge cases ---

    @Test
    void nullRoles_shouldDeny() {
        when(ownershipRepository.findByAssetTypeAndAssetId("TABLE", "1")).thenReturn(Optional.empty());
        when(grantRepository.findActiveGrantsForUser(anyString(), anyString(), anyString(), anyList(), anyString(), any(Instant.class)))
            .thenReturn(List.of());

        PermissionResult result = service.check("user", null, null, "TABLE", "1");
        assertThat(result.allowed()).isFalse();
    }
}
