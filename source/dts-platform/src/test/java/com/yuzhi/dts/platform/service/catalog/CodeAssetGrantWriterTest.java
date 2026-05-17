package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
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
}
