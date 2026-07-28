package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogAssetPortalServicePermissionParityTest {

    @Mock
    private OpenMetadataAssetCacheRepository assetRepository;
    @Mock
    private OpenMetadataColumnCacheRepository columnRepository;
    @Mock
    private OpenMetadataLineageCacheRepository lineageRepository;
    @Mock
    private CatalogAssetExtensionRepository extensionRepository;
    @Mock
    private CatalogAssetMappingRepository mappingRepository;
    @Mock
    private CatalogDatasetRepository datasetRepository;
    @Mock
    private CatalogTableSchemaRepository tableSchemaRepository;
    @Mock
    private CatalogColumnSchemaRepository catalogColumnSchemaRepository;
    @Mock
    private AccessChecker accessChecker;
    @Mock
    private CatalogClassificationService classificationService;
    @Mock
    private CatalogAssetTagService assetTagService;

    private CatalogAssetPortalService service;

    @BeforeEach
    void setUp() {
        service =
            new CatalogAssetPortalService(
                assetRepository,
                columnRepository,
                lineageRepository,
                extensionRepository,
                mappingRepository,
                datasetRepository,
                tableSchemaRepository,
                catalogColumnSchemaRepository,
                accessChecker,
                classificationService,
                assetTagService
            );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAssets_shouldHideOpenMetadataSummaryWhenDetailWouldDeny() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        asset.setFqn("dwd.orders");
        asset.setTableName("orders");
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(asset), PageRequest.of(0, 20), 1));
        when(extensionRepository.findByOmAssetIn(List.of(asset))).thenReturn(List.of());
        when(mappingRepository.findByNormalizedFqnIn(Set.of("dwd.orders"))).thenReturn(List.of());
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        CatalogAssetPortalService.AssetPage result = service.listAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listAssets_shouldHideLegacySummaryWhenDetailWouldDeny() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        dataset.setName("Orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        dataset.setOwnerDept("D01");
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));
        when(accessChecker.canRead(dataset)).thenReturn(false);

        CatalogAssetPortalService.AssetPage result = service.listAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void governanceIntake_shouldExposeUnclassifiedLegacyAssetOnlyWithinDepartmentScope() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("25252525-2525-2525-2525-252525252525"));
        dataset.setName("Unclassified orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        dataset.setOwnerDept("D01");
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));
        when(accessChecker.departmentAllowedExact(dataset, "D01")).thenReturn(true);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetPage result = service.listGovernanceIntakeAssets(query(), "D01");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).legacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void governanceIntake_shouldHideUnownedAssetFromDepartmentMaintainer() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("27272727-2727-2727-2727-272727272727"));
        dataset.setName("Unowned orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));

        CatalogAssetPortalService.AssetPage result = service.listGovernanceIntakeAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void governanceIntake_shouldExposeUnownedAssetToInstituteMaintainer() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER, "INST");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("28282828-2828-2828-2828-282828282828"));
        dataset.setName("Unowned orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetPage result = service.listGovernanceIntakeAssets(query(), "D01");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).legacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void governanceMaintainerGetsOnlyMinimalDetailUntilClassificationAllowsConsumption() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("29292929-2929-2929-2929-292929292929"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        asset.setRawJson("{\"secret\":\"raw\"}");
        asset.setProfileJson("{\"secret\":\"profile\"}");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(true);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetDetail detail = service.getAsset(asset.getId(), "D01");

        assertThat(detail.asset().id()).isEqualTo(asset.getId());
        assertThat(detail.columns()).isEmpty();
        assertThat(detail.rawJson()).isNull();
        assertThat(detail.profileJson()).isNull();
        verifyNoInteractions(columnRepository);
    }

    @Test
    void governanceIntakeVisibilityDoesNotOpenContractOrSchemaContract() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("30303030-3030-3030-3030-303030303030"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));

        assertThatThrownBy(() -> service.getAssetContract(asset.getId(), "D01"))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        assertThatThrownBy(() -> service.getAssetSchemaContract(asset.getId(), "D01"))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    void governanceMaintainerCanCompleteClassificationWithoutReceivingRawMetadata() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("31313131-3131-3131-3131-313131313131"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        asset.setRawJson("{\"secret\":\"raw\"}");
        asset.setProfileJson("{\"secret\":\"profile\"}");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(true);
        when(extensionRepository.save(any(CatalogAssetExtension.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());
        when(classificationService.sealOrRaise(any())).thenReturn(classificationSnapshot("INTERNAL"));

        CatalogAssetPortalService.AssetDetail detail = service.updateGovernance(
            asset.getId(),
            new CatalogAssetPortalService.GovernanceUpdate(
                null,
                "DATA_INTERNAL",
                null,
                null,
                null,
                null,
                null,
                null
            ),
            "D01"
        );

        assertThat(extension.getClassification()).isEqualTo("INTERNAL");
        assertThat(detail.rawJson()).isNull();
        assertThat(detail.profileJson()).isNull();
        assertThat(detail.columns()).isEmpty();
        verifyNoInteractions(columnRepository);
    }

    @Test
    void governanceClassificationCannotBeDowngraded() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("32313131-3131-3131-3131-313131313131"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        extension.setClassification("DATA_SECRET");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(true);

        assertThatThrownBy(() ->
            service.updateGovernance(
                asset.getId(),
                new CatalogAssetPortalService.GovernanceUpdate(
                    null,
                    "PUBLIC",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                "D01"
            )
        )
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );

        assertThat(extension.getClassification()).isEqualTo("DATA_SECRET");
        verifyNoInteractions(classificationService);
        verify(extensionRepository, never()).save(any(CatalogAssetExtension.class));
    }

    @Test
    void mappedGovernanceUsesHighestLegacyClassificationAsFloor() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("33313131-3131-3131-3131-313131313131"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        CatalogDataset legacy = new CatalogDataset();
        legacy.setId(UUID.fromString("34313131-3131-3131-3131-313131313131"));
        legacy.setClassification("DATA_SECRET");
        legacy.setOwnerDept("D01");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setLegacyDatasetId(legacy.getId());
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        extension.setClassification("INTERNAL");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(datasetRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(true);

        assertThatThrownBy(() ->
            service.updateGovernance(
                asset.getId(),
                new CatalogAssetPortalService.GovernanceUpdate(
                    null,
                    "INTERNAL",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                "D01"
            )
        )
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );

        assertThat(extension.getClassification()).isEqualTo("INTERNAL");
        assertThat(legacy.getClassification()).isEqualTo("DATA_SECRET");
        verifyNoInteractions(classificationService);
        verify(extensionRepository, never()).save(any(CatalogAssetExtension.class));
    }

    @Test
    void mappedAssetReadUsesExtensionGovernanceInsteadOfLegacyGovernance() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("32323232-3232-3232-3232-323232323232"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        CatalogDataset legacy = new CatalogDataset();
        legacy.setId(UUID.fromString("33323232-3232-3232-3232-323232323232"));
        legacy.setEnabled(Boolean.TRUE);
        legacy.setClassification("DATA_PUBLIC");
        legacy.setOwnerDept("D01");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setLegacyDatasetId(legacy.getId());
        extension.setEnabled(Boolean.TRUE);
        extension.setClassification("DATA_SECRET");
        extension.setOwnerDept("D02");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(datasetRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(accessChecker.canRead(any(CatalogDataset.class))).thenReturn(true);
        when(accessChecker.departmentAllowed(any(CatalogDataset.class), eq("D01"))).thenReturn(false);

        assertThatThrownBy(() -> service.getAsset(asset.getId(), "D01"))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );

        ArgumentCaptor<CatalogDataset> effectiveGovernance = ArgumentCaptor.forClass(CatalogDataset.class);
        verify(accessChecker).canRead(effectiveGovernance.capture());
        assertThat(effectiveGovernance.getValue().getClassification()).isEqualTo("SECRET");
        assertThat(effectiveGovernance.getValue().getOwnerDept()).isEqualTo("D02");
    }

    @Test
    void mappedAssetReadUsesHighestSealedClassification() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("33323232-3232-3232-3232-323232323232"));
        asset.setFqn("svc.db.public.orders");
        asset.setTableName("orders");
        CatalogDataset legacy = new CatalogDataset();
        legacy.setId(UUID.fromString("34323232-3232-3232-3232-323232323232"));
        legacy.setName("orders");
        legacy.setHiveDatabase("public");
        legacy.setHiveTable("orders");
        legacy.setEnabled(Boolean.TRUE);
        legacy.setClassification("DATA_SECRET");
        legacy.setOwnerDept("D01");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setLegacyDatasetId(legacy.getId());
        extension.setEnabled(Boolean.TRUE);
        extension.setClassification("INTERNAL");
        extension.setOwnerDept("D01");
        CatalogClassificationSnapshot sealed = classificationSnapshot("CONFIDENTIAL");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(datasetRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(classificationService.resolve("ASSET", CatalogAssetKey.dataset(legacy))).thenReturn(Optional.of(sealed));
        when(accessChecker.canRead(any(CatalogDataset.class)))
            .thenAnswer(invocation -> !"CONFIDENTIAL".equals(invocation.<CatalogDataset>getArgument(0).getClassification()));

        assertThatThrownBy(() -> service.getAsset(asset.getId(), "D01"))
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );

        ArgumentCaptor<CatalogDataset> effectiveGovernance = ArgumentCaptor.forClass(CatalogDataset.class);
        verify(accessChecker).canRead(effectiveGovernance.capture());
        assertThat(effectiveGovernance.getValue().getClassification()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void classifiedCrossDepartmentAssetCannotBeGovernedByDepartmentMaintainer() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("34343434-3434-3434-3434-343434343434"));
        asset.setFqn("svc.db.public.orders");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setClassification("DATA_INTERNAL");
        extension.setOwnerDept("D02");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(false);

        assertThatThrownBy(() ->
            service.updateGovernance(
                asset.getId(),
                new CatalogAssetPortalService.GovernanceUpdate(
                    null,
                    "DATA_SECRET",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ),
                "D01"
            )
        )
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        verify(extensionRepository, never()).save(any(CatalogAssetExtension.class));
    }

    @Test
    void departmentMaintainerCannotReassignAssetToAnotherDepartment() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("35353535-3535-3535-3535-353535353535"));
        asset.setFqn("svc.db.public.orders");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(Boolean.TRUE);
        extension.setOwnerDept("D01");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.of(extension));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01")))
            .thenAnswer(invocation -> "D01".equals(invocation.<CatalogDataset>getArgument(0).getOwnerDept()));

        assertThatThrownBy(() ->
            service.updateGovernance(
                asset.getId(),
                new CatalogAssetPortalService.GovernanceUpdate(
                    null,
                    null,
                    null,
                    "D02",
                    null,
                    null,
                    null,
                    null
                ),
                "D01"
            )
        )
            .isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
        assertThat(extension.getOwnerDept()).isEqualTo("D01");
        verify(extensionRepository, never()).save(any(CatalogAssetExtension.class));
    }

    @Test
    void governanceMaintainerCanClassifyLegacyAssetFromTheUnifiedIntake() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "D01");
        UUID datasetId = UUID.fromString("36363636-3636-3636-3636-363636363636");
        CatalogDataset legacy = new CatalogDataset();
        legacy.setId(datasetId);
        legacy.setName("Legacy orders");
        legacy.setEnabled(Boolean.TRUE);
        legacy.setOwnerDept("D01");
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(legacy));
        when(accessChecker.departmentAllowedExact(any(CatalogDataset.class), eq("D01"))).thenReturn(true);
        when(datasetRepository.save(legacy)).thenReturn(legacy);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());
        when(classificationService.sealOrRaise(any())).thenReturn(classificationSnapshot("INTERNAL"));

        CatalogAssetPortalService.AssetDetail detail = service.updateGovernance(
            datasetId,
            new CatalogAssetPortalService.GovernanceUpdate(
                null,
                "DATA_INTERNAL",
                null,
                null,
                null,
                null,
                null,
                null
            ),
            "D01"
        );

        assertThat(legacy.getClassification()).isEqualTo("INTERNAL");
        assertThat(detail.asset().classification()).isEqualTo("INTERNAL");
        assertThat(detail.columns()).isEmpty();
    }

    @Test
    void governanceIntake_shouldNotBypassClassificationGateForClassifiedAssets() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("26262626-2626-2626-2626-262626262626"));
        dataset.setName("Secret orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        dataset.setClassification("DATA_SECRET");
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));
        when(accessChecker.canRead(dataset)).thenReturn(false);

        CatalogAssetPortalService.AssetPage result = service.listGovernanceIntakeAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listAssets_shouldKeepTheFilteredTotalWhenTheCurrentPageIsSmaller() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        asset.setFqn("dwd.customers");
        asset.setTableName("customers");
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(true);
        extension.setClassification("DATA_INTERNAL");

        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(asset), PageRequest.of(0, 10), 50));
        when(extensionRepository.findByOmAssetIn(List.of(asset))).thenReturn(List.of(extension));
        when(mappingRepository.findByNormalizedFqnIn(Set.of("dwd.customers"))).thenReturn(List.of());
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(accessChecker.canRead(any(CatalogDataset.class))).thenReturn(true);
        when(accessChecker.departmentAllowed(any(CatalogDataset.class), any())).thenReturn(true);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetPage result = service.listAssets(
            new CatalogAssetPortalService.AssetQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                10
            ),
            "D01"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.total()).isEqualTo(50);
    }

    @Test
    void listAssets_shouldKeepTheLegacyFilteredTotalWhenTheCurrentPageIsSmaller() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        dataset.setName("Legacy customers");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("customers");
        dataset.setEnabled(true);
        dataset.setClassification("DATA_INTERNAL");
        dataset.setHarvestStatus("STALE");

        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 10), 50));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "D01")).thenReturn(true);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetPage result = service.listAssets(
            new CatalogAssetPortalService.AssetQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                10
            ),
            "D01"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.total()).isEqualTo(50);
        assertThat(result.content().get(0).syncStatus()).isEqualTo("STALE");
    }

    private CatalogAssetPortalService.AssetQuery query() {
        return new CatalogAssetPortalService.AssetQuery(null, null, null, null, null, null, null, null, null, null, null, null, false, 0, 20);
    }

    private CatalogClassificationSnapshot classificationSnapshot(String effectiveLevel) {
        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setEffectiveLevel(effectiveLevel);
        return snapshot;
    }

    private void authenticate(String authority, String department) {
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "catalog-test-user")
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
