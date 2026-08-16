package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalyticsAssetAuthorizationTest {

    private final AssetOwnershipRepository ownershipRepository = mock(AssetOwnershipRepository.class);
    private final AssetGrantRepository grantRepository = mock(AssetGrantRepository.class);
    private final AssetPermissionService service = new AssetPermissionService(ownershipRepository, grantRepository);

    @Test
    void keepsClassificationSeparateForInstituteDataOwnerAssetAdministration() {
        PermissionDecision result = service.checkAuthorization(
            command("xiezm", "ROLE_INST_DATA_OWNER", "DEPT_A", "CARD", "10", "EDIT")
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("MANAGE");
        assertThat(result.requiredPermission()).isEqualTo("EDIT");
        assertThat(result.classificationDecision()).isEqualTo("NOT_APPLIED");
    }

    @Test
    void keepsDepartmentOwnershipBoundary() {
        AssetOwnership ownership = new AssetOwnership();
        ownership.setOwnerDeptCode("DEPT_A");
        when(ownershipRepository.findByAssetTypeAndAssetId("DASHBOARD", "20"))
            .thenReturn(Optional.of(ownership));
        when(
            grantRepository.findActiveGrantsForUser(
                anyString(), anyString(), anyString(), anyList(), anyString(), any(Instant.class)
            )
        ).thenReturn(List.of());

        PermissionDecision ownDepartment = service.checkAuthorization(
            command("dept-owner", "ROLE_DEPT_DATA_OWNER", "DEPT_A", "DASHBOARD", "20", "MANAGE")
        );
        PermissionDecision otherDepartment = service.checkAuthorization(
            command("dept-owner", "ROLE_DEPT_DATA_OWNER", "DEPT_B", "DASHBOARD", "20", "MANAGE")
        );

        assertThat(ownDepartment.allowed()).isTrue();
        assertThat(ownDepartment.classificationDecision()).isEqualTo("NOT_APPLIED");
        assertThat(otherDepartment.allowed()).isFalse();
        assertThat(otherDepartment.reason()).isEqualTo("denied");
    }

    @Test
    void protectedDataCheckStillRequiresClassification() {
        PermissionDecision result = service.checkAction(
            command("xiezm", "ROLE_INST_DATA_OWNER", "DEPT_A", "CARD", "10", "READ")
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("classification_required");
    }

    private static PermissionCheckCommand command(
        String username,
        String role,
        String department,
        String assetType,
        String assetId,
        String action
    ) {
        return new PermissionCheckCommand(
            username,
            List.of(role),
            department,
            null,
            assetType,
            assetId,
            null,
            action,
            null
        );
    }
}
