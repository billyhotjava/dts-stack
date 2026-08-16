package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetAccessRegistrationService.RegistrationCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetAccessRegistrationServiceTest {

    @Mock
    private AssetOwnershipRepository ownershipRepository;

    @Mock
    private AssetPermissionService permissionService;

    @Mock
    private AssetPermissionAuditService auditService;

    @Test
    void registersDepartmentOwnershipAndCreatorManageGrant() {
        when(ownershipRepository.findByAssetTypeAndAssetId("CARD", "42")).thenReturn(Optional.empty());
        when(ownershipRepository.save(any(AssetOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionService.upsertGrant(any(GrantCommand.class))).thenReturn(new AssetGrant());
        AssetAccessRegistrationService service = service();

        var result = service.register(
            new RegistrationCommand("card", "42", " D1 ", "xiezm", "xiezm", "dts-analytics")
        );

        ArgumentCaptor<AssetOwnership> ownershipCaptor = ArgumentCaptor.forClass(AssetOwnership.class);
        verify(ownershipRepository).save(ownershipCaptor.capture());
        assertThat(ownershipCaptor.getValue().getAssetType()).isEqualTo("CARD");
        assertThat(ownershipCaptor.getValue().getAssetId()).isEqualTo("42");
        assertThat(ownershipCaptor.getValue().getOwnerDeptCode()).isEqualTo("D1");
        assertThat(ownershipCaptor.getValue().getAssignedBy()).isEqualTo("xiezm");
        assertThat(ownershipCaptor.getValue().getSourceId()).isEqualTo("dts-analytics");
        verify(auditService).recordOwnershipChange("CARD", "42", null, "D1");

        ArgumentCaptor<GrantCommand> grantCaptor = ArgumentCaptor.forClass(GrantCommand.class);
        verify(permissionService).upsertGrant(grantCaptor.capture());
        assertThat(grantCaptor.getValue().granteeType()).isEqualTo("USER");
        assertThat(grantCaptor.getValue().granteeId()).isEqualTo("xiezm");
        assertThat(grantCaptor.getValue().permission()).isEqualTo("MANAGE");
        assertThat(result.ownershipRegistered()).isTrue();
        assertThat(result.creatorGrantRegistered()).isTrue();
    }

    @Test
    void missingDepartmentStillRegistersCreatorWithoutInventingOwnership() {
        when(permissionService.upsertGrant(any(GrantCommand.class))).thenReturn(new AssetGrant());
        AssetAccessRegistrationService service = service();

        var result = service.register(
            new RegistrationCommand("DASHBOARD", "7", null, "xiezm", "xiezm", "dts-analytics")
        );

        verify(ownershipRepository, never()).save(any());
        verify(auditService, never()).recordOwnershipChange(any(), any(), any(), any());
        assertThat(result.ownershipRegistered()).isFalse();
        assertThat(result.creatorGrantRegistered()).isTrue();
    }

    private AssetAccessRegistrationService service() {
        return new AssetAccessRegistrationService(ownershipRepository, permissionService, auditService);
    }
}
