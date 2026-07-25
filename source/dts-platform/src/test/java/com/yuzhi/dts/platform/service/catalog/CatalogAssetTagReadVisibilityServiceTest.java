package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CatalogAssetTagReadVisibilityServiceTest {

    @Mock
    private CatalogAssetTagPermissionIdentityResolver identityResolver;

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private CatalogDatasetGrantRepository datasetGrantRepository;

    @Mock
    private AssetPermissionService permissionService;

    private CatalogAssetTagReadVisibilityService service;

    @BeforeEach
    void setUp() {
        authenticate("ROLE_DEPT_LEADER", "D01");
        service =
            new CatalogAssetTagReadVisibilityService(
                identityResolver,
                accessChecker,
                datasetGrantRepository,
                permissionService
            );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtersEverySupportedAssetTypeAgainstOnlyTheCurrentCandidates() {
        CatalogDataset allowedDataset = dataset("D01");
        CatalogDataset deniedDataset = dataset("D02");
        AssetRef allowedDatasetRef = new AssetRef("DATASET", "dataset:allowed");
        AssetRef deniedDatasetRef = new AssetRef("DATASET", "dataset:denied");
        AssetRef allowedApiRef = new AssetRef("API_SERVICE", "tenant:default/env:prod/dialect:generic/api_service:orders");
        AssetRef deniedApiRef = new AssetRef("API_SERVICE", "tenant:default/env:prod/dialect:generic/api_service:payroll");
        AssetRef deniedMetric = new AssetRef("METRIC", "metric:core/revenue");
        UUID deniedMetricId = UUID.fromString(
            "10000000-0000-0000-0000-000000000003"
        );
        List<AssetRef> supported = List.of(
            allowedDatasetRef,
            deniedDatasetRef,
            allowedApiRef,
            deniedApiRef,
            deniedMetric
        );

        when(identityResolver.resolveAll(supported))
            .thenReturn(
                List.of(
                    identity(allowedDatasetRef, allowedDataset.getId(), allowedDataset),
                    identity(deniedDatasetRef, deniedDataset.getId(), deniedDataset),
                    identity(allowedApiRef, UUID.fromString("10000000-0000-0000-0000-000000000001"), null),
                    identity(deniedApiRef, UUID.fromString("10000000-0000-0000-0000-000000000002"), null),
                    identity(deniedMetric, deniedMetricId, null)
                )
            );
        when(accessChecker.canRead(allowedDataset)).thenReturn(true);
        when(accessChecker.canRead(deniedDataset)).thenReturn(true);
        when(accessChecker.departmentAllowedExact(allowedDataset, "D01")).thenReturn(true);
        when(accessChecker.departmentAllowedExact(deniedDataset, "D01")).thenReturn(false);
        when(datasetGrantRepository.findDatasetIdsByUser(null, "analyst")).thenReturn(Set.of());
        when(permissionService.batchCheck(
            "analyst",
            List.of("ROLE_DEPT_LEADER"),
            "D01",
            List.of(
                new AssetPermissionService.AssetRef(
                    "API_SERVICE",
                    "10000000-0000-0000-0000-000000000001"
                ),
                new AssetPermissionService.AssetRef(
                    "API_SERVICE",
                    "10000000-0000-0000-0000-000000000002"
                ),
                new AssetPermissionService.AssetRef(
                    "METRIC",
                    deniedMetricId.toString()
                )
            )
        ))
            .thenReturn(
                java.util.Map.of(
                    "API_SERVICE:10000000-0000-0000-0000-000000000001",
                    PermissionResult.allowed("READ", "explicit_grant"),
                    "API_SERVICE:10000000-0000-0000-0000-000000000002",
                    PermissionResult.denied(),
                    "METRIC:" + deniedMetricId,
                    PermissionResult.denied()
                )
            );

        List<AssetRef> readable = service.filterReadable(
            supported,
            "D01"
        );

        assertThat(readable).containsExactly(allowedDatasetRef, allowedApiRef);
        verify(identityResolver).resolveAll(supported);
        verify(permissionService).batchCheck(
            "analyst",
            List.of("ROLE_DEPT_LEADER"),
            "D01",
            List.of(
                new AssetPermissionService.AssetRef(
                    "API_SERVICE",
                    "10000000-0000-0000-0000-000000000001"
                ),
                new AssetPermissionService.AssetRef(
                    "API_SERVICE",
                    "10000000-0000-0000-0000-000000000002"
                ),
                new AssetPermissionService.AssetRef(
                    "METRIC",
                    deniedMetricId.toString()
                )
            )
        );
    }

    @Test
    void departmentHeaderCannotSwitchAReaderIntoAnotherDepartment() {
        AssetRef asset = new AssetRef(
            "API_SERVICE",
            "tenant:default/env:prod/dialect:generic/api_service:payroll"
        );
        UUID grantId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        when(identityResolver.resolveAll(List.of(asset)))
            .thenReturn(List.of(identity(asset, grantId, null)));
        when(
            permissionService.batchCheck(
                org.mockito.ArgumentMatchers.eq("analyst"),
                org.mockito.ArgumentMatchers.eq(List.of("ROLE_DEPT_LEADER")),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.eq(
                    List.of(
                        new AssetPermissionService.AssetRef(
                            "API_SERVICE",
                            grantId.toString()
                        )
                    )
                )
            )
        )
            .thenAnswer(invocation ->
                "D02".equals(invocation.getArgument(2))
                    ? java.util.Map.of(
                        "API_SERVICE:" + grantId,
                        PermissionResult.allowed("READ", "dept_ownership")
                    )
                    : java.util.Map.of(
                        "API_SERVICE:" + grantId,
                        PermissionResult.denied()
                    )
            );

        assertThat(service.filterReadable(List.of(asset), "D02")).isEmpty();
    }

    @Test
    void similarDepartmentCodesDoNotGrantDatasetRead() {
        authenticate("ROLE_DEPT_LEADER", "1");
        CatalogDataset dataset = dataset("101");
        AssetRef asset = new AssetRef("DATASET", "dataset:near-match");
        when(identityResolver.resolveAll(List.of(asset)))
            .thenReturn(List.of(identity(asset, dataset.getId(), dataset)));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(datasetGrantRepository.findDatasetIdsByUser(null, "analyst")).thenReturn(Set.of());
        when(accessChecker.departmentAllowedExact(dataset, "1")).thenReturn(false);

        assertThat(service.filterReadable(List.of(asset), null)).isEmpty();
    }

    @Test
    void unresolvableAssetFailsClosed() {
        AssetRef unresolved = new AssetRef("SCREEN", "screen:missing");
        when(identityResolver.resolveAll(List.of(unresolved)))
            .thenThrow(
                new CatalogAssetTagPermissionException(
                    HttpStatus.NOT_FOUND,
                    "ASSET_NOT_FOUND",
                    CatalogAssetType.SCREEN,
                    unresolved.assetKey(),
                    "资产不存在"
                )
            );

        assertThat(service.filterReadable(List.of(unresolved), "D01")).isEmpty();
    }

    private CatalogDataset dataset(String ownerDept) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName("dataset-" + ownerDept);
        dataset.setEnabled(true);
        dataset.setClassification("INTERNAL");
        dataset.setOwnerDept(ownerDept);
        return dataset;
    }

    private ResolvedPermissionIdentity identity(AssetRef ref, UUID grantId, CatalogDataset dataset) {
        return new ResolvedPermissionIdentity(
            CatalogAssetType.from(ref.assetType()),
            ref.assetKey(),
            ref.assetType(),
            grantId.toString(),
            dataset,
            "test"
        );
    }

    private void authenticate(String role, String department) {
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "analyst")
            .claim("roles", List.of(role))
            .claim("dept_code", department)
            .build();
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new JwtAuthenticationToken(
                    jwt,
                    List.of(new SimpleGrantedAuthority(role))
                )
            );
    }
}
