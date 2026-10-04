package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CatalogAssetTagWriteGuardTest {

    private static final String API_KEY =
        "tenant:default/env:prod/dialect:generic/api_service:customer_query";
    private static final UUID API_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private CatalogAssetTagPermissionIdentityResolver identityResolver;

    @Mock
    private AssetOwnershipRepository ownershipRepository;

    @Mock
    private AssetGrantRepository grantRepository;

    @Mock
    private BiReportLinkRepository reportLinkRepository;

    @Mock
    private AssetPermissionAuditService permissionAuditService;

    @Mock
    private CatalogResourceHelper catalogResourceHelper;

    private CatalogAssetTagWriteGuard guard;

    @BeforeEach
    void setUp() {
        AssetPermissionService permissionService = new AssetPermissionService(
            ownershipRepository,
            grantRepository,
            reportLinkRepository
        );
        guard = new CatalogAssetTagWriteGuard(
            identityResolver,
            permissionService,
            permissionAuditService,
            catalogResourceHelper
        );
        authenticate("analyst", "ROLE_EMPLOYEE");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryUserWithExplicitEditGrantPassesUsingResolvedUuidGrantIdentity() {
        AssetRef requested = new AssetRef("API_SERVICE", API_KEY);
        when(identityResolver.resolveAll(List.of(requested))).thenReturn(List.of(apiIdentity(API_KEY, API_ID)));
        when(ownershipRepository.findByAssetTypeAndAssetId("API_SERVICE", API_ID.toString())).thenReturn(Optional.empty());
        AssetGrant editGrant = grant("EDIT");
        when(
            grantRepository.findActiveGrantsForUser(
                eq("API_SERVICE"),
                eq(API_ID.toString()),
                eq("analyst"),
                anyList(),
                eq(""),
                any(Instant.class)
            )
        ).thenReturn(List.of(editGrant));

        CatalogAssetTagWriteGuard.AuthorizedAssets authorized = guard.authorizeAll(List.of(requested));

        assertThat(authorized.assets()).containsExactly(requested);
        assertThat(authorized.actor()).isEqualTo("analyst");
        ArgumentCaptor<PermissionDecision> decision = ArgumentCaptor.forClass(PermissionDecision.class);
        verify(permissionAuditService).recordDecision(decision.capture(), eq("analyst"), eq("analyst"));
        assertThat(decision.getValue().allowed()).isTrue();
        assertThat(decision.getValue().permission()).isEqualTo("EDIT");
        assertThat(decision.getValue().assetId()).isEqualTo(API_ID.toString());
        assertThat(decision.getValue().assetKey()).isEqualTo(API_KEY);
    }

    @Test
    void capabilityProbeUsesTheSameWriteDecisionWithoutCreatingAnAuditEntry() {
        AssetRef requested = new AssetRef("API_SERVICE", API_KEY);
        when(identityResolver.resolveAll(List.of(requested))).thenReturn(List.of(apiIdentity(API_KEY, API_ID)));
        when(ownershipRepository.findByAssetTypeAndAssetId("API_SERVICE", API_ID.toString())).thenReturn(Optional.empty());
        when(
            grantRepository.findActiveGrantsForUser(
                eq("API_SERVICE"),
                eq(API_ID.toString()),
                eq("analyst"),
                anyList(),
                eq(""),
                any(Instant.class)
            )
        ).thenReturn(List.of(grant("EDIT")));

        assertThat(guard.canTag(requested)).isTrue();

        verifyNoInteractions(permissionAuditService);
    }

    @Test
    void capabilityProbeFailsClosedWhenTheCanonicalPermissionIdentityCannotBeResolved() {
        AssetRef requested = new AssetRef("METRIC", "metric:core/revenue");
        when(identityResolver.resolveAll(List.of(requested)))
            .thenThrow(
                new CatalogAssetTagPermissionException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "UNSUPPORTED_PERMISSION_IDENTITY",
                    CatalogAssetType.METRIC,
                    requested.assetKey(),
                    "unsupported"
                )
            );

        assertThat(guard.canTag(requested)).isFalse();

        verifyNoInteractions(permissionAuditService);
    }

    @Test
    void readGrantIsDeniedEvenWhenBasePermissionServiceMarksItAllowed() {
        AssetRef requested = new AssetRef("API_SERVICE", API_KEY);
        when(identityResolver.resolveAll(List.of(requested))).thenReturn(List.of(apiIdentity(API_KEY, API_ID)));
        when(ownershipRepository.findByAssetTypeAndAssetId("API_SERVICE", API_ID.toString())).thenReturn(Optional.empty());
        when(
            grantRepository.findActiveGrantsForUser(
                eq("API_SERVICE"),
                eq(API_ID.toString()),
                eq("analyst"),
                anyList(),
                eq(""),
                any(Instant.class)
            )
        ).thenReturn(List.of(grant("READ")));

        assertThatThrownBy(() -> guard.authorizeAll(List.of(requested)))
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .extracting("reasonCode")
            .isEqualTo("INSUFFICIENT_PERMISSION");

        ArgumentCaptor<PermissionDecision> decision = ArgumentCaptor.forClass(PermissionDecision.class);
        verify(permissionAuditService).recordDecision(decision.capture(), eq("analyst"), eq("analyst"));
        assertThat(decision.getValue().allowed()).isFalse();
        assertThat(decision.getValue().permission()).isEqualTo("READ");
        assertThat(decision.getValue().reasonCode()).isEqualTo("INSUFFICIENT_PERMISSION");
    }

    @Test
    void datasetAuthorizationDelegatesToTheExistingDatasetEditBoundary() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        dataset.setName("orders");
        AssetRef requested = new AssetRef("DATASET", "source:unknown/schema:dwd/table:orders");
        ResolvedPermissionIdentity identity = new ResolvedPermissionIdentity(
            CatalogAssetType.DATASET,
            requested.assetKey(),
            "DATASET",
            dataset.getId().toString(),
            dataset,
            "legacy-dataset"
        );
        when(identityResolver.resolveAll(List.of(requested))).thenReturn(List.of(identity));

        guard.authorizeAll(List.of(requested));

        verify(catalogResourceHelper).ensureDatasetEditPermission(dataset);
        verify(ownershipRepository, never()).findByAssetTypeAndAssetId(anyString(), anyString());
        ArgumentCaptor<PermissionDecision> decision = ArgumentCaptor.forClass(PermissionDecision.class);
        verify(permissionAuditService).recordDecision(decision.capture(), eq("analyst"), eq("analyst"));
        assertThat(decision.getValue().allowed()).isTrue();
        assertThat(decision.getValue().grantSource()).isEqualTo("dataset_edit_boundary");
    }

    @Test
    void securityPolicyAuthorizationReusesItsDatasetEditBoundary() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(
            UUID.fromString("22222222-2222-2222-2222-222222222222")
        );
        dataset.setName("secured-orders");
        AssetRef requested = new AssetRef(
            "SECURITY_POLICY",
            CatalogAssetKey.codeAsset(
                CatalogAssetType.SECURITY_POLICY,
                "default",
                dataset.getId().toString()
            )
        );
        ResolvedPermissionIdentity identity = new ResolvedPermissionIdentity(
            CatalogAssetType.SECURITY_POLICY,
            requested.assetKey(),
            CatalogAssetType.DATASET.name(),
            dataset.getId().toString(),
            dataset,
            "dataset-security-policy"
        );
        when(identityResolver.resolveAll(List.of(requested)))
            .thenReturn(List.of(identity));

        guard.authorizeAll(List.of(requested));

        verify(catalogResourceHelper).ensureDatasetEditPermission(dataset);
        verify(ownershipRepository, never())
            .findByAssetTypeAndAssetId(anyString(), anyString());
    }

    @Test
    void unsupportedIdentityPersistsAStableDenyDecisionBeforeFailingClosed() {
        AssetRef unsupported = new AssetRef("METRIC", "metric:core/revenue");
        when(identityResolver.resolveAll(List.of(unsupported)))
            .thenThrow(
                new CatalogAssetTagPermissionException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "UNSUPPORTED_PERMISSION_IDENTITY",
                    CatalogAssetType.METRIC,
                    unsupported.assetKey(),
                    "unsupported"
                )
            );

        assertThatThrownBy(() -> guard.authorizeAll(List.of(unsupported)))
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .extracting("reasonCode")
            .isEqualTo("UNSUPPORTED_PERMISSION_IDENTITY");

        ArgumentCaptor<PermissionDecision> decision = ArgumentCaptor.forClass(PermissionDecision.class);
        verify(permissionAuditService).recordDecision(decision.capture(), eq("analyst"), eq("analyst"));
        assertThat(decision.getValue().allowed()).isFalse();
        assertThat(decision.getValue().reasonCode()).isEqualTo("UNSUPPORTED_PERMISSION_IDENTITY");
        assertThat(decision.getValue().assetId()).isNull();
        assertThat(decision.getValue().assetKey()).isEqualTo(unsupported.assetKey());
    }

    @Test
    void mixedBatchChecksEveryResolvedAssetAndReturnsNoAuthorizationWhenOneIsDenied() {
        String secondKey = "tenant:default/env:prod/dialect:generic/api_service:order_query";
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AssetRef first = new AssetRef("API_SERVICE", API_KEY);
        AssetRef second = new AssetRef("API_SERVICE", secondKey);
        when(identityResolver.resolveAll(List.of(first, second)))
            .thenReturn(List.of(apiIdentity(API_KEY, API_ID), apiIdentity(secondKey, secondId)));
        when(ownershipRepository.findByAssetTypeAndAssetId(eq("API_SERVICE"), anyString())).thenReturn(Optional.empty());
        when(
            grantRepository.findActiveGrantsForUser(
                eq("API_SERVICE"),
                eq(API_ID.toString()),
                eq("analyst"),
                anyList(),
                eq(""),
                any(Instant.class)
            )
        ).thenReturn(List.of(grant("EDIT")));
        when(
            grantRepository.findActiveGrantsForUser(
                eq("API_SERVICE"),
                eq(secondId.toString()),
                eq("analyst"),
                anyList(),
                eq(""),
                any(Instant.class)
            )
        ).thenReturn(List.of(grant("READ")));

        assertThatThrownBy(() -> guard.authorizeAll(List.of(first, second)))
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .satisfies(failure -> {
                CatalogAssetTagPermissionException permissionFailure =
                    (CatalogAssetTagPermissionException) failure;
                assertThat(permissionFailure.reasonCode()).isEqualTo("INSUFFICIENT_PERMISSION");
                assertThat(permissionFailure.deniedCount()).isEqualTo(1);
            });

        ArgumentCaptor<PermissionDecision> decisions = ArgumentCaptor.forClass(PermissionDecision.class);
        verify(permissionAuditService, org.mockito.Mockito.times(2))
            .recordDecision(decisions.capture(), eq("analyst"), eq("analyst"));
        assertThat(decisions.getAllValues()).extracting(PermissionDecision::allowed).containsExactly(true, false);
    }

    private static ResolvedPermissionIdentity apiIdentity(String key, UUID id) {
        return new ResolvedPermissionIdentity(
            CatalogAssetType.API_SERVICE,
            key,
            "API_SERVICE",
            id.toString(),
            null,
            "api-code"
        );
    }

    private static AssetGrant grant(String permission) {
        AssetGrant grant = new AssetGrant();
        grant.setPermission(permission);
        return grant;
    }

    private static void authenticate(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = java.util.Arrays
            .stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContextHolder
            .getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(username, "n/a", granted));
    }
}
