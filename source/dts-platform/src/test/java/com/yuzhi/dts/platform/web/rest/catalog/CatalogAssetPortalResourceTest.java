package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetContract;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetContractMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogAssetPortalResourceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsResolutionFailuresForAssetPortal() {
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetMappingReportService mappingReportService = mock(CatalogAssetMappingReportService.class);
        CatalogAssetIdentityResolutionAuditService resolutionAuditService = mock(CatalogAssetIdentityResolutionAuditService.class);
        OpenMetadataAssetSyncService syncService = mock(OpenMetadataAssetSyncService.class);
        AuditService audit = mock(AuditService.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        CatalogAssetTagWriteGuard assetTagWriteGuard = mock(CatalogAssetTagWriteGuard.class);
        Instant since = Instant.parse("2026-05-17T00:00:00Z");
        CatalogAssetResolutionFailure failure = new CatalogAssetResolutionFailure();
        UUID id = UUID.randomUUID();
        failure.setId(id);
        failure.setRef("gov_indicator:missing_metric");
        failure.setRequestedAt(since);
        failure.setCaller("MetricPackPreview");
        failure.setTypeHintGuess("gov_indicator");
        failure.setReason("TYPE_REPOSITORY_MISS");
        when(resolutionAuditService.recentFailures(since, 50)).thenReturn(List.of(failure));

        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            assetPortalService,
            mappingReportService,
            resolutionAuditService,
            syncService,
            audit,
            helper,
            assetTagWriteGuard
        );

        List<CatalogAssetPortalResource.ResolutionFailureResponse> response = resource.resolutionFailures(since, 50).getData();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(id);
        assertThat(response.get(0).ref()).isEqualTo("gov_indicator:missing_metric");
        assertThat(response.get(0).reason()).isEqualTo("TYPE_REPOSITORY_MISS");
    }

    @Test
    void assetContractExposesAnExplicitFailClosedTagWriteCapability() throws Exception {
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetTagWriteGuard assetTagWriteGuard = mock(CatalogAssetTagWriteGuard.class);
        UUID assetId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(assetId);
        dataset.setName("orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        CatalogAssetContract contract = CatalogAssetContractMapper.fromLegacy(dataset);
        String assetKey = contract.assetKey();
        when(assetPortalService.getAssetContract(assetId, null)).thenReturn(contract);
        when(assetTagWriteGuard.canTag(new AssetRef("DATASET", assetKey))).thenReturn(true);
        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            assetPortalService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            mock(CatalogResourceHelper.class),
            assetTagWriteGuard
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(resource).build();

        mockMvc
            .perform(get("/api/catalog/assets-v2/{id}/contract", assetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assetKey").value(assetKey))
            .andExpect(jsonPath("$.data.canTag").value(true))
            .andExpect(jsonPath("$.data.canMaintain").value(false));
    }

    @Test
    void assetContractExposesMaintenanceOnlyForTheSameCatalogRoleAndOwnerDepartment() {
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = dataset(datasetId, "dept-a");
        CatalogAssetContract contract = CatalogAssetContractMapper.fromLegacy(dataset);
        CatalogAssetPortalService service = mock(CatalogAssetPortalService.class);
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        CatalogResourceHelper helper = helper(datasetRepository);
        when(service.getAssetContract(datasetId, "dept-a")).thenReturn(contract);
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        CatalogAssetPortalResource resource = resource(service, helper, datasetRepository, mock(CatalogAssetTagWriteGuard.class));

        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "dept-a");
        assertThat(resource.getAssetContract(datasetId, null).getData().canMaintain()).isTrue();

        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "dept-b");
        when(service.getAssetContract(datasetId, "dept-b")).thenReturn(contract);
        assertThat(resource.getAssetContract(datasetId, null).getData().canMaintain()).isFalse();

        authenticate(AuthoritiesConstants.EMPLOYEE, "dept-a");
        assertThat(resource.getAssetContract(datasetId, null).getData().canMaintain()).isFalse();
    }

    @Test
    void assetContractFailsClosedWhenNoLegacyDatasetCanReceiveThePatch() {
        UUID assetId = UUID.randomUUID();
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(assetId);
        asset.setFqn("postgres.dwd.orders");
        asset.setSourceType("TABLE");
        CatalogAssetContract contract = CatalogAssetContractMapper.fromOpenMetadata(asset, null, null, null);
        CatalogAssetPortalService service = mock(CatalogAssetPortalService.class);
        CatalogAssetTagWriteGuard tagWriteGuard = mock(CatalogAssetTagWriteGuard.class);
        when(service.getAssetContract(assetId, "dept-a")).thenReturn(contract);
        when(tagWriteGuard.canTag(new AssetRef(contract.grantAssetType(), contract.assetKey()))).thenReturn(true);
        CatalogAssetPortalResource resource = resource(
            service,
            helper(mock(CatalogDatasetRepository.class)),
            mock(CatalogDatasetRepository.class),
            tagWriteGuard
        );

        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "dept-a");
        CatalogAssetPortalResource.AssetContractResponse response = resource.getAssetContract(assetId, null).getData();

        assertThat(response.canMaintain()).isFalse();
        assertThat(response.canTag()).isTrue();
    }

    @Test
    void departmentMaintainerCannotSwitchScopeWithActiveDepartmentHeader() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "dept-token");
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetPortalService.AssetPage emptyPage = new CatalogAssetPortalService.AssetPage(
            List.of(),
            0,
            0,
            20,
            0,
            "openmetadata-cache"
        );
        when(
            assetPortalService.listGovernanceIntakeAssets(
                any(CatalogAssetPortalService.AssetQuery.class),
                eq("dept-token")
            )
        ).thenReturn(emptyPage);
        CatalogAssetPortalResource resource = resource(assetPortalService);

        resource.governanceIntake(null, null, null, null, 0, 20, "dept-forged");

        verify(assetPortalService).listGovernanceIntakeAssets(
            any(CatalogAssetPortalService.AssetQuery.class),
            eq("dept-token")
        );
    }

    @Test
    void instituteMaintainerMaySelectAnActiveDepartment() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER, "institute-home");
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetPortalService.AssetPage emptyPage = new CatalogAssetPortalService.AssetPage(
            List.of(),
            0,
            0,
            20,
            0,
            "openmetadata-cache"
        );
        when(
            assetPortalService.listGovernanceIntakeAssets(
                any(CatalogAssetPortalService.AssetQuery.class),
                eq("dept-selected")
            )
        ).thenReturn(emptyPage);
        CatalogAssetPortalResource resource = resource(assetPortalService);

        resource.governanceIntake(null, null, null, null, 0, 20, " dept-selected ");

        verify(assetPortalService).listGovernanceIntakeAssets(
            any(CatalogAssetPortalService.AssetQuery.class),
            eq("dept-selected")
        );
    }

    private CatalogAssetPortalResource resource(CatalogAssetPortalService assetPortalService) {
        return new CatalogAssetPortalResource(
            assetPortalService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            mock(CatalogResourceHelper.class),
            mock(CatalogAssetTagWriteGuard.class)
        );
    }

    private CatalogAssetPortalResource resource(
        CatalogAssetPortalService assetPortalService,
        CatalogResourceHelper helper,
        CatalogDatasetRepository datasetRepository,
        CatalogAssetTagWriteGuard assetTagWriteGuard
    ) {
        return new CatalogAssetPortalResource(
            assetPortalService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            helper,
            assetTagWriteGuard,
            null,
            datasetRepository
        );
    }

    private CatalogResourceHelper helper(CatalogDatasetRepository datasetRepository) {
        return new CatalogResourceHelper(
            datasetRepository,
            mock(com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository.class),
            mock(com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository.class),
            mock(com.yuzhi.dts.platform.repository.catalog.CatalogMetadataChangeLogRepository.class),
            mock(DataStandardRepository.class),
            mock(InfraDataSourceRepository.class),
            mock(CatalogFeatureProperties.class),
            mock(OrganizationVisibilityService.class)
        );
    }

    private CatalogDataset dataset(UUID id, String ownerDept) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setName("orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setOwnerDept(ownerDept);
        dataset.setEnabled(true);
        return dataset;
    }

    private void authenticate(String authority, String department) {
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "portal-test-user")
            .claim("roles", List.of(authority))
            .claim("dept_code", department)
            .build();
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new JwtAuthenticationToken(
                    jwt,
                    List.of(new SimpleGrantedAuthority(authority))
                )
            );
    }
}
