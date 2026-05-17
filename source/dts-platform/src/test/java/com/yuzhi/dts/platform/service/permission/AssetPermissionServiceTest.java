package com.yuzhi.dts.platform.service.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
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
    @Mock
    private BiReportLinkRepository reportLinkRepository;

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
        PermissionResult result = service.check("owner", List.of("ROLE_INST_DATA_OWNER"), "DEPT_A", "TABLE", "5");
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

    @Test
    void checkAction_shouldUseAssetKeyWhenAssetIdIsMissing() {
        when(ownershipRepository.findByAssetTypeAndAssetId("DATASET", "source:demo/schema:dwd/table:orders")).thenReturn(Optional.empty());
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(
            grantRepository.findActiveGrantsForUser(
                eq("DATASET"),
                eq("source:demo/schema:dwd/table:orders"),
                eq("emp"),
                anyList(),
                eq("DEPT_A"),
                any(Instant.class)
            )
        ).thenReturn(List.of(grant));

        PermissionDecision result = service.checkAction(
            new PermissionCheckCommand(
                "emp",
                List.of("ROLE_EMPLOYEE"),
                "DEPT_A",
                "INTERNAL",
                "DATASET",
                null,
                "source:demo/schema:dwd/table:orders",
                "PREVIEW",
                "INTERNAL"
            )
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.assetId()).isEqualTo("source:demo/schema:dwd/table:orders");
        assertThat(result.requiredPermission()).isEqualTo("READ");
        assertThat(result.grantSource()).isEqualTo("explicit_grant");
        assertThat(result.classificationDecision()).isEqualTo("ALLOWED");
    }

    @Test
    void checkAction_shouldDenyWhenActionRequiresHigherPermission() {
        when(ownershipRepository.findByAssetTypeAndAssetId("DATASET", "1")).thenReturn(Optional.empty());
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grantRepository.findActiveGrantsForUser(eq("DATASET"), eq("1"), eq("emp"), anyList(), eq("DEPT_A"), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionDecision result = service.checkAction(
            new PermissionCheckCommand("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "INTERNAL", "DATASET", "1", null, "PUBLISH", "INTERNAL")
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("insufficient_permission");
        assertThat(result.requiredPermission()).isEqualTo("MANAGE");
        assertThat(result.permission()).isEqualTo("READ");
    }

    @Test
    void checkAction_shouldDenyWhenClassificationIsTooLow() {
        when(ownershipRepository.findByAssetTypeAndAssetId("DATASET", "1")).thenReturn(Optional.empty());
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grantRepository.findActiveGrantsForUser(eq("DATASET"), eq("1"), eq("emp"), anyList(), eq("DEPT_A"), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionDecision result = service.checkAction(
            new PermissionCheckCommand("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "PUBLIC", "DATASET", "1", null, "READ", "INTERNAL")
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("classification_denied");
        assertThat(result.classificationDecision()).isEqualTo("DENIED");
    }

    @Test
    void checkAction_shouldDenyWhenAssetClassificationIsMissing() {
        when(ownershipRepository.findByAssetTypeAndAssetId("DATASET", "1")).thenReturn(Optional.empty());
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grantRepository.findActiveGrantsForUser(eq("DATASET"), eq("1"), eq("emp"), anyList(), eq("DEPT_A"), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionDecision result = service.checkAction(
            new PermissionCheckCommand("emp", List.of("ROLE_EMPLOYEE"), "DEPT_A", "INTERNAL", "DATASET", "1", null, "READ", null)
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("classification_required");
        assertThat(result.classificationDecision()).isEqualTo("MISSING_ASSET_CLASSIFICATION");
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

    @Test
    void screenPublic_shouldAllowReadWithoutGrant() {
        AssetPermissionService screenService = new AssetPermissionService(ownershipRepository, grantRepository, reportLinkRepository);
        when(reportLinkRepository.findEnabledScreenByCode("screen-10")).thenReturn(Optional.of(screenLink("screen-10", "PUBLIC")));

        PermissionResult result = screenService.check("ptrdemo", List.of(), null, "SCREEN", "10", null, null);

        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("READ");
        assertThat(result.reason()).isEqualTo("public");
        verifyNoInteractions(ownershipRepository);
        verifyNoMoreInteractions(grantRepository);
    }

    @Test
    void screenInternalViewerGrant_shouldRequireClassification() {
        AssetPermissionService screenService = new AssetPermissionService(ownershipRepository, grantRepository, reportLinkRepository);
        when(reportLinkRepository.findEnabledScreenByCode("screen-10")).thenReturn(Optional.of(screenLink("screen-10", "INTERNAL")));
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        grant.setLevelOverride(false);
        when(grantRepository.findActiveGrantsForUser(eq("SCREEN"), eq("10"), eq("ptrdemo"), anyList(), eq("D01"), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionResult result = screenService.check("ptrdemo", List.of("ROLE_PTR"), "D01", "SCREEN", "10", "PUBLIC", null);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("classification_denied");
    }

    @Test
    void screenViewerLevelOverride_shouldAllowReadAcrossClassification() {
        AssetPermissionService screenService = new AssetPermissionService(ownershipRepository, grantRepository, reportLinkRepository);
        when(reportLinkRepository.findEnabledScreenByCode("screen-10")).thenReturn(Optional.of(screenLink("screen-10", "CONFIDENTIAL")));
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        grant.setLevelOverride(true);
        when(grantRepository.findActiveGrantsForUser(eq("SCREEN"), eq("10"), eq("ptrdemo"), anyList(), eq("D01"), any(Instant.class)))
            .thenReturn(List.of(grant));

        PermissionResult result = screenService.check("ptrdemo", List.of("ROLE_PTR"), "D01", "SCREEN", "10", "INTERNAL", null);

        assertThat(result.allowed()).isTrue();
        assertThat(result.permission()).isEqualTo("READ");
        assertThat(result.reason()).isEqualTo("level_override");
    }

    @Test
    void screenAccessibleIds_shouldIncludePublicAndAllowedExplicitGrants() {
        AssetPermissionService screenService = new AssetPermissionService(ownershipRepository, grantRepository, reportLinkRepository);
        when(reportLinkRepository.findEnabledScreensForPermission()).thenReturn(List.of(
            screenLink("screen-10", "PUBLIC"),
            screenLink("screen-11", "INTERNAL")
        ));
        when(reportLinkRepository.findEnabledScreenByCode("screen-11")).thenReturn(Optional.of(screenLink("screen-11", "INTERNAL")));
        when(grantRepository.findAccessibleAssetIdsByGrant(eq("SCREEN"), eq("ptrdemo"), anyList(), eq("D01"), any(Instant.class)))
            .thenReturn(List.of("11"));
        AssetGrant grant = new AssetGrant();
        grant.setPermission("READ");
        when(grantRepository.findActiveGrantsForUser(eq("SCREEN"), eq("11"), eq("ptrdemo"), anyList(), eq("D01"), any(Instant.class)))
            .thenReturn(List.of(grant));

        AccessibleAssetsResult result = screenService.listAccessibleAssetIds(
            "ptrdemo", List.of("ROLE_PTR"), "D01", "SCREEN", PageRequest.of(0, 100), "INTERNAL"
        );

        assertThat(result.assetIds()).containsExactly("10", "11");
        assertThat(result.total()).isEqualTo(2);
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

    private BiReportLink screenLink(String code, String classification) {
        BiReportLink link = new BiReportLink();
        link.setCode(code);
        link.setReportType("SCREEN");
        link.setEnabled(true);
        link.setClassification(classification);
        link.setTitle(code);
        link.setUrl("/bi/screens/" + code);
        return link;
    }
}
