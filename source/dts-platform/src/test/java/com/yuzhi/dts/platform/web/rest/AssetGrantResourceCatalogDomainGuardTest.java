package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssetGrantResourceCatalogDomainGuardTest {

    private static final String DOMAIN_ID = "04f15180-df7c-431f-b747-3dafb3a2b149";

    @Mock
    private AssetGrantRepository grantRepository;

    @Mock
    private AssetOwnershipRepository ownershipRepository;

    @Mock
    private AssetPermissionAuditService auditService;

    @Mock
    private AssetPermissionService permissionService;

    @InjectMocks
    private AssetGrantResource resource;

    @BeforeEach
    void authenticateDepartmentMaintainer() {
        authenticate("dept-owner", "ROLE_DEPT_DATA_OWNER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentMaintainerCannotListOrSelfGrantARestrictedDomainWithoutManagePermission() {
        when(permissionService.check(eq("dept-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID)))
            .thenReturn(PermissionResult.denied());

        assertThatThrownBy(() -> resource.listByAsset("CATALOG_DOMAIN", DOMAIN_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> resource.create(request("CATALOG_DOMAIN", DOMAIN_ID, "forged-admin")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(grantRepository, never()).findByAssetTypeAndAssetId(any(), any());
        verify(grantRepository, never()).save(any());
    }

    @Test
    void instituteManagerCanGrantAndServerDerivesGrantedByFromAuthentication() {
        authenticate("inst-owner", "ROLE_INST_DATA_OWNER");
        when(permissionService.check(eq("inst-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID)))
            .thenReturn(PermissionResult.allowed("MANAGE", "inst_manage"));
        when(grantRepository.save(any(AssetGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = resource.create(
            request(" catalog_domain ", "  " + DOMAIN_ID + "  ", "forged-admin")
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(AssetGrant.class);
        AssetGrant saved = (AssetGrant) response.getBody();
        assertThat(saved.getAssetType()).isEqualTo("CATALOG_DOMAIN");
        assertThat(saved.getAssetId()).isEqualTo(DOMAIN_ID);
        assertThat(saved.getGrantedBy()).isEqualTo("inst-owner");
        verify(permissionService).check(eq("inst-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID));
        verify(ownershipRepository).findByAssetTypeAndAssetId("CATALOG_DOMAIN", DOMAIN_ID);
        verify(auditService).recordGrant(
            "CATALOG_DOMAIN",
            DOMAIN_ID,
            "dept-owner",
            "MANAGE",
            "test",
            "inst-owner"
        );
    }

    @Test
    void catalogDomainListAndBulkRevokeUseTheSameCanonicalReferenceAsAuthorization() {
        when(permissionService.check(eq("dept-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID)))
            .thenReturn(PermissionResult.allowed("MANAGE", "explicit_grant"));
        AssetGrant existing = new AssetGrant();
        existing.setAssetType("CATALOG_DOMAIN");
        existing.setAssetId(DOMAIN_ID);
        existing.setGranteeId("alice");
        existing.setPermission("READ");
        when(grantRepository.findByAssetTypeAndAssetId("CATALOG_DOMAIN", DOMAIN_ID))
            .thenReturn(List.of(existing));

        assertThat(resource.listByAsset("catalog_domain", " " + DOMAIN_ID + " ").getBody())
            .containsExactly(existing);
        ResponseEntity<?> response = resource.revokeByAsset(" catalog_domain ", " " + DOMAIN_ID + " ");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(grantRepository, org.mockito.Mockito.times(2))
            .findByAssetTypeAndAssetId("CATALOG_DOMAIN", DOMAIN_ID);
        verify(auditService).recordRevoke("CATALOG_DOMAIN", DOMAIN_ID, "alice", "READ");
    }

    @Test
    void catalogDomainRevokeEndpointsAlsoRequireManagePermission() {
        AssetGrant existing = new AssetGrant();
        existing.setId(42L);
        existing.setAssetType("CATALOG_DOMAIN");
        existing.setAssetId(DOMAIN_ID);
        when(grantRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(permissionService.check(eq("dept-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID)))
            .thenReturn(PermissionResult.denied());

        assertThatThrownBy(() -> resource.revokeByAsset("CATALOG_DOMAIN", DOMAIN_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> resource.revoke(42L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(grantRepository, never()).deleteAll(any());
        verify(grantRepository, never()).delete(any(AssetGrant.class));
    }

    @Test
    void catalogDomainRevokeByIdCanonicalizesLegacyReferenceForAuthorizationAndAudit() {
        AssetGrant existing = new AssetGrant();
        existing.setId(42L);
        existing.setAssetType(" catalog_domain ");
        existing.setAssetId("  " + DOMAIN_ID + "  ");
        existing.setGranteeId("alice");
        existing.setPermission("READ");
        when(grantRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(permissionService.check(eq("dept-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID)))
            .thenReturn(PermissionResult.allowed("MANAGE", "explicit_grant"));

        ResponseEntity<?> response = resource.revoke(42L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(permissionService).check(eq("dept-owner"), anyList(), eq(null), eq("CATALOG_DOMAIN"), eq(DOMAIN_ID));
        verify(grantRepository).delete(existing);
        verify(auditService).recordRevoke("CATALOG_DOMAIN", DOMAIN_ID, "alice", "READ");
    }

    @Test
    void nonCatalogAssetsKeepTheirExistingGrantBehaviorWithoutDomainPermissionChecks() {
        AssetGrant created = new AssetGrant();
        created.setAssetType(" TABLE ");
        created.setAssetId(" table-1 ");
        when(grantRepository.save(any(AssetGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(grantRepository.findByAssetTypeAndAssetId(" TABLE ", " table-1 ")).thenReturn(List.of(created));

        ResponseEntity<?> createResponse = resource.create(request(" TABLE ", " table-1 ", "forged-admin"));
        AssetGrant saved = (AssetGrant) createResponse.getBody();

        assertThat(saved).isNotNull();
        assertThat(saved.getAssetType()).isEqualTo(" TABLE ");
        assertThat(saved.getAssetId()).isEqualTo(" table-1 ");
        assertThat(resource.listByAsset(" TABLE ", " table-1 ").getBody()).containsExactly(created);
        verify(ownershipRepository).findByAssetTypeAndAssetId(" TABLE ", " table-1 ");
        verify(auditService).recordGrant(
            " TABLE ",
            " table-1 ",
            "dept-owner",
            "MANAGE",
            "test",
            "dept-owner"
        );

        AssetGrant bulkGrant = new AssetGrant();
        bulkGrant.setAssetType("TABLE");
        bulkGrant.setAssetId("table-1");
        bulkGrant.setGranteeId("alice");
        bulkGrant.setPermission("READ");
        when(grantRepository.findByAssetTypeAndAssetId("TABLE", "table-1")).thenReturn(List.of(bulkGrant));

        ResponseEntity<?> revokeResponse = resource.revokeByAsset(" TABLE ", " table-1 ");

        assertThat(revokeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(grantRepository).deleteAll(List.of(bulkGrant));
        verify(auditService).recordRevoke("TABLE", "table-1", "alice", "READ");

        verify(permissionService, never()).check(any(), anyList(), any(), any(), any());
    }

    @Test
    void nonCatalogRevokeByIdPreservesTheStoredReference() {
        AssetGrant existing = new AssetGrant();
        existing.setId(43L);
        existing.setAssetType(" TABLE ");
        existing.setAssetId(" table-1 ");
        existing.setGranteeId("alice");
        existing.setPermission("READ");
        when(grantRepository.findById(43L)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = resource.revoke(43L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(grantRepository).delete(existing);
        verify(auditService).recordRevoke(" TABLE ", " table-1 ", "alice", "READ");
        verify(permissionService, never()).check(any(), anyList(), any(), any(), any());
    }

    private static AssetGrantResource.CreateGrantRequest request(String assetType, String assetId, String grantedBy) {
        return new AssetGrantResource.CreateGrantRequest(
            assetType,
            assetId,
            "USER",
            "dept-owner",
            "MANAGE",
            null,
            null,
            "test",
            grantedBy
        );
    }

    private static void authenticate(String username, String authority) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    username,
                    "n/a",
                    List.of(new SimpleGrantedAuthority(authority))
                )
            );
    }
}
