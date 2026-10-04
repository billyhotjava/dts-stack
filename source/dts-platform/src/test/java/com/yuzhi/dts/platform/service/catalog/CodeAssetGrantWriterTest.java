package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeAssetGrantWriterTest {

    @Mock
    private AssetOwnershipRepository ownershipRepository;

    @Mock
    private AssetPermissionService permissionService;

    @Test
    void upsertsOwnerDeptOwnershipAndManageGrantForCodeAsset() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = new CatalogAssetIdentity(
            CatalogAssetType.GOV_INDICATOR,
            CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "flowerbiz", "contract_amount"),
            "indicator-001",
            "gov-indicator:contract_amount"
        );
        when(ownershipRepository.findByAssetTypeAndAssetId("GOV_INDICATOR", "indicator-001")).thenReturn(Optional.empty());
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.upsertCodeAsset(identity, "D01", "sysadmin", "INTERNAL", "ACTIVE");

        ArgumentCaptor<AssetOwnership> ownershipCaptor = ArgumentCaptor.forClass(AssetOwnership.class);
        verify(ownershipRepository).save(ownershipCaptor.capture());
        AssetOwnership ownership = ownershipCaptor.getValue();
        assertThat(ownership.getAssetType()).isEqualTo("GOV_INDICATOR");
        assertThat(ownership.getAssetId()).isEqualTo("indicator-001");
        assertThat(ownership.getOwnerDeptCode()).isEqualTo("D01");
        assertThat(ownership.getSourceId()).isEqualTo(identity.assetKey());

        ArgumentCaptor<GrantCommand> grantCaptor = ArgumentCaptor.forClass(GrantCommand.class);
        verify(permissionService).upsertGrant(grantCaptor.capture());
        GrantCommand grant = grantCaptor.getValue();
        assertThat(grant.assetType()).isEqualTo("GOV_INDICATOR");
        assertThat(grant.assetId()).isEqualTo("indicator-001");
        assertThat(grant.granteeType()).isEqualTo("DEPT");
        assertThat(grant.granteeId()).isEqualTo("D01");
        assertThat(grant.permission()).isEqualTo("MANAGE");
        assertThat(grant.grantedBy()).isEqualTo("sysadmin");
        assertThat(grant.grantReason()).contains("assetKey=").contains("classification=INTERNAL").contains("lifecycle=ACTIVE");
    }

    @Test
    void externalSyncRevokesManagedGrantWhenOwnerDepartmentChanges() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership ownership = managedOwnership(identity, "D01");
        AssetGrant oldGrant = managedGrant(identity, "D01", 71L);
        stubManagedOwnershipLookup(ownership);
        when(permissionService.listGrants("METRIC", "metric-revenue-v1")).thenReturn(List.of(oldGrant));
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.synchronizeExternalCodeAsset(identity, "D02", "dts-metrics", "INTERNAL", "ACTIVE");

        verify(permissionService).revokeGrant("METRIC", "metric-revenue-v1", 71L);
        ArgumentCaptor<AssetOwnership> ownershipCaptor = ArgumentCaptor.forClass(AssetOwnership.class);
        verify(ownershipRepository).save(ownershipCaptor.capture());
        assertThat(ownershipCaptor.getValue().getOwnerDeptCode()).isEqualTo("D02");
        assertThat(ownershipCaptor.getValue().getSourceId()).startsWith("catalog-external:");
        ArgumentCaptor<GrantCommand> grantCaptor = ArgumentCaptor.forClass(GrantCommand.class);
        verify(permissionService).upsertGrant(grantCaptor.capture());
        assertThat(grantCaptor.getValue().granteeId()).isEqualTo("D02");
    }

    @Test
    void externalSyncClearsManagedOwnershipAndGrantWhenDepartmentIsRemoved() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership ownership = managedOwnership(identity, "D01");
        AssetGrant oldGrant = managedGrant(identity, "D01", 72L);
        stubManagedOwnershipLookup(ownership);
        when(permissionService.listGrants("METRIC", "metric-revenue-v1")).thenReturn(List.of(oldGrant));

        writer.synchronizeExternalCodeAsset(identity, null, "dts-metrics", "INTERNAL", "ACTIVE");

        verify(permissionService).revokeGrant("METRIC", "metric-revenue-v1", 72L);
        verify(ownershipRepository).delete(ownership);
        verify(permissionService, never()).upsertGrant(any());
    }

    @Test
    void externalSyncRemovesManagedPermissionForReplacedRemoteIdentity() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity previousIdentity = metricIdentity();
        CatalogAssetIdentity replacement = new CatalogAssetIdentity(
            CatalogAssetType.METRIC,
            previousIdentity.assetKey(),
            "metric-revenue-v2",
            "dts-metrics:metric-revenue-v2"
        );
        AssetOwnership oldOwnership = managedOwnership(previousIdentity, "D01");
        AssetGrant oldGrant = managedGrant(previousIdentity, "D01", 74L);
        stubManagedOwnershipLookup(oldOwnership);
        when(ownershipRepository.findByAssetTypeAndAssetId("METRIC", "metric-revenue-v2"))
            .thenReturn(Optional.empty());
        when(permissionService.listGrants("METRIC", "metric-revenue-v1")).thenReturn(List.of(oldGrant));
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.synchronizeExternalCodeAsset(replacement, "D02", "dts-metrics", "INTERNAL", "ACTIVE");

        verify(permissionService).revokeGrant("METRIC", "metric-revenue-v1", 74L);
        verify(ownershipRepository).delete(oldOwnership);
        ArgumentCaptor<AssetOwnership> ownershipCaptor = ArgumentCaptor.forClass(AssetOwnership.class);
        verify(ownershipRepository).save(ownershipCaptor.capture());
        assertThat(ownershipCaptor.getValue().getAssetId()).isEqualTo("metric-revenue-v2");
        assertThat(ownershipCaptor.getValue().getOwnerDeptCode()).isEqualTo("D02");
    }

    @Test
    void externalSyncIsIdempotentAndDoesNotRevokeUnrelatedManualGrant() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership ownership = managedOwnership(identity, "D01");
        AssetGrant manualGrant = managedGrant(identity, "D01", 73L);
        manualGrant.setGrantedBy("security-admin");
        manualGrant.setGrantReason("manual approval");
        stubManagedOwnershipLookup(ownership);
        when(permissionService.listGrants("METRIC", "metric-revenue-v1")).thenReturn(List.of(manualGrant));
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.synchronizeExternalCodeAsset(identity, "D02", "dts-metrics", "INTERNAL", "ACTIVE");
        writer.synchronizeExternalCodeAsset(identity, "D02", "dts-metrics", "INTERNAL", "ACTIVE");

        verify(permissionService, never()).revokeGrant(anyString(), anyString(), any());
        verify(ownershipRepository, never()).delete(any(AssetOwnership.class));
        verify(permissionService, times(2)).upsertGrant(any());
    }

    @Test
    void externalSyncDoesNotTakeOverManualOwnershipForTheCurrentAsset() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership manualOwnership = managedOwnership(identity, "D09");
        manualOwnership.setSourceId("manual-registration");
        manualOwnership.setAssignedBy("security-admin");
        when(ownershipRepository.findBySourceId(anyString())).thenReturn(List.of());
        when(ownershipRepository.findByAssetTypeAndAssetId("METRIC", "metric-revenue-v1"))
            .thenReturn(Optional.of(manualOwnership));

        writer.synchronizeExternalCodeAsset(identity, "D02", "dts-metrics", "INTERNAL", "ACTIVE");

        assertThat(manualOwnership.getOwnerDeptCode()).isEqualTo("D09");
        assertThat(manualOwnership.getSourceId()).isEqualTo("manual-registration");
        assertThat(manualOwnership.getAssignedBy()).isEqualTo("security-admin");
        verify(ownershipRepository, never()).save(any(AssetOwnership.class));
        verify(ownershipRepository, never()).delete(any(AssetOwnership.class));
        verify(permissionService, never()).upsertGrant(any());
        verify(permissionService, never()).revokeGrant(anyString(), anyString(), any());
    }

    @Test
    void externalSyncDoesNotTreatManualOwnershipWithLegacySourceIdAsManaged() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership manualOwnership = managedOwnership(identity, "D09");
        manualOwnership.setAssignedBy("security-admin");
        stubManagedOwnershipLookup(manualOwnership);

        writer.synchronizeExternalCodeAsset(identity, null, "dts-metrics", "INTERNAL", "ACTIVE");

        verify(ownershipRepository, never()).delete(any(AssetOwnership.class));
        verify(permissionService, never()).revokeGrant(anyString(), anyString(), any());
    }

    @Test
    void externalSyncPreservesManualGrantForTheNewOwnerDepartment() {
        CodeAssetGrantWriter writer = new CodeAssetGrantWriter(ownershipRepository, permissionService);
        CatalogAssetIdentity identity = metricIdentity();
        AssetOwnership ownership = managedOwnership(identity, "D01");
        AssetGrant oldManagedGrant = managedGrant(identity, "D01", 75L);
        AssetGrant manualTargetGrant = managedGrant(identity, "D02", 76L);
        manualTargetGrant.setPermission("EDIT");
        manualTargetGrant.setGrantedBy("security-admin");
        manualTargetGrant.setGrantReason("manual approval");
        stubManagedOwnershipLookup(ownership);
        when(permissionService.listGrants("METRIC", "metric-revenue-v1"))
            .thenReturn(List.of(oldManagedGrant, manualTargetGrant));
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        writer.synchronizeExternalCodeAsset(identity, "D02", "dts-metrics", "INTERNAL", "ACTIVE");

        verify(permissionService).revokeGrant("METRIC", "metric-revenue-v1", 75L);
        verify(permissionService, never()).revokeGrant("METRIC", "metric-revenue-v1", 76L);
        verify(permissionService, never()).upsertGrant(any());
        ArgumentCaptor<AssetOwnership> ownershipCaptor = ArgumentCaptor.forClass(AssetOwnership.class);
        verify(ownershipRepository).save(ownershipCaptor.capture());
        assertThat(ownershipCaptor.getValue().getOwnerDeptCode()).isEqualTo("D02");
    }

    private void stubManagedOwnershipLookup(AssetOwnership ownership) {
        when(ownershipRepository.findBySourceId(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0).equals(ownership.getSourceId()) ? List.of(ownership) : List.of());
    }

    private static CatalogAssetIdentity metricIdentity() {
        return new CatalogAssetIdentity(
            CatalogAssetType.METRIC,
            CatalogAssetKey.metric("core", "revenue"),
            "metric-revenue-v1",
            "dts-metrics:metric-revenue-v1"
        );
    }

    private static AssetOwnership managedOwnership(CatalogAssetIdentity identity, String ownerDept) {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setAssetType(identity.grantAssetType());
        ownership.setAssetId(identity.grantAssetId());
        ownership.setOwnerDeptCode(ownerDept);
        ownership.setSourceId(identity.assetKey());
        ownership.setAssignedBy("dts-metrics");
        return ownership;
    }

    private static AssetGrant managedGrant(CatalogAssetIdentity identity, String ownerDept, long id) {
        AssetGrant grant = new AssetGrant();
        grant.setId(id);
        grant.setAssetType(identity.grantAssetType());
        grant.setAssetId(identity.grantAssetId());
        grant.setGranteeType("DEPT");
        grant.setGranteeId(ownerDept);
        grant.setPermission("MANAGE");
        grant.setGrantedBy("dts-metrics");
        grant.setGrantReason(
            "code asset sync; assetKey="
                + identity.assetKey()
                + "; classification=INTERNAL; lifecycle=ACTIVE"
        );
        return grant;
    }
}
