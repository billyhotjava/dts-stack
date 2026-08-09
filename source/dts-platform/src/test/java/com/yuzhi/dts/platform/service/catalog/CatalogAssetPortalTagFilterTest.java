package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogAssetPortalTagFilterTest {

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
    private CatalogDomainRepository domainRepository;
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
    @Mock
    private CatalogAssetRegistrationService assetRegistrationService;

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
                domainRepository,
                tableSchemaRepository,
                catalogColumnSchemaRepository,
                accessChecker,
                classificationService,
                assetTagService,
                assetRegistrationService
            );
    }

    @Test
    void tagFilterUsesMappedAndUnmappedCanonicalKeysBeforePaginationAndHydratesOnlyThePage() {
        UUID tagId = UUID.randomUUID();
        CatalogDataset mappedLegacy = legacyDataset("mapped-orders");
        OpenMetadataAssetCache excluded = openMetadataAsset("svc.db.public.excluded", "excluded", Instant.parse("2026-07-25T03:00:00Z"));
        OpenMetadataAssetCache mapped = openMetadataAsset("svc.db.public.orders", "orders", Instant.parse("2026-07-25T02:00:00Z"));
        OpenMetadataAssetCache unmapped = openMetadataAsset("svc.db.public.customers", "customers", Instant.parse("2026-07-25T01:00:00Z"));
        CatalogAssetMapping mappedRelation = new CatalogAssetMapping();
        mappedRelation.setFqn(mapped.getFqn());
        mappedRelation.setLegacyDatasetId(mappedLegacy.getId());
        mappedRelation.setMatchStatus("MATCHED");
        CatalogAssetExtension excludedExtension = enabledExtension();
        excludedExtension.setOmAsset(excluded);
        CatalogAssetExtension mappedExtension = enabledExtension();
        mappedExtension.setOmAsset(mapped);
        CatalogAssetExtension unmappedExtension = enabledExtension();
        unmappedExtension.setOmAsset(unmapped);
        String mappedKey = CatalogAssetKey.dataset(mappedLegacy);
        String unmappedKey = CatalogAssetKey.openMetadataDataset(unmapped);
        when(
            assetTagService.findMatchingAssetKeysWithin(
                org.mockito.ArgumentMatchers.eq("DATASET"),
                any(),
                org.mockito.ArgumentMatchers.eq(List.of(tagId))
            )
        )
            .thenReturn(Set.of(mappedKey, unmappedKey));
        when(assetRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(excluded, mapped, unmapped));
        when(extensionRepository.findByOmAssetIn(List.of(excluded, mapped, unmapped)))
            .thenReturn(List.of(excludedExtension, mappedExtension, unmappedExtension));
        when(mappingRepository.findByNormalizedFqnIn(any())).thenReturn(List.of(mappedRelation));
        when(datasetRepository.findAllById(Set.of(mappedLegacy.getId()))).thenReturn(List.of(mappedLegacy));
        when(datasetRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(mappedLegacy));
        when(accessChecker.canRead(any(CatalogDataset.class))).thenReturn(true);
        when(accessChecker.departmentAllowed(any(CatalogDataset.class), any())).thenReturn(true);
        CatalogTagDto tag = tag(tagId);
        when(assetTagService.listAssetTags(List.of(new AssetRef("DATASET", mappedKey))))
            .thenReturn(Map.of(new AssetRef("DATASET", mappedKey), List.of(tag)));
        when(assetTagService.listAssetTags(List.of(new AssetRef("DATASET", unmappedKey))))
            .thenReturn(Map.of(new AssetRef("DATASET", unmappedKey), List.of(tag)));

        CatalogAssetPortalService.AssetPage firstPage = service.listAssets(query(List.of(tagId), 0, 1), "D01");
        CatalogAssetPortalService.AssetPage secondPage = service.listAssets(query(List.of(tagId), 1, 1), "D01");

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.content()).singleElement().satisfies(summary -> {
            assertThat(summary.assetType()).isEqualTo("DATASET");
            assertThat(summary.assetKey()).isEqualTo(mappedKey);
            assertThat(summary.assetTags()).containsExactly(tag);
        });
        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(secondPage.content()).singleElement().satisfies(summary -> {
            assertThat(summary.assetKey()).isEqualTo(unmappedKey);
            assertThat(summary.assetTags()).containsExactly(tag);
        });
        verify(assetTagService, org.mockito.Mockito.times(2))
            .findMatchingAssetKeysWithin(
                org.mockito.ArgumentMatchers.eq("DATASET"),
                org.mockito.ArgumentMatchers.argThat(keys ->
                    keys != null &&
                    keys.size() <= 3 &&
                    keys.contains(mappedKey) &&
                    keys.contains(unmappedKey)
                ),
                org.mockito.ArgumentMatchers.eq(List.of(tagId))
            );
    }

    @Test
    void tagFilterExcludesUnreadableCandidatesFromTheFilteredTotal() {
        UUID tagId = UUID.randomUUID();
        OpenMetadataAssetCache allowed = openMetadataAsset("svc.db.public.allowed", "allowed", Instant.parse("2026-07-25T02:00:00Z"));
        OpenMetadataAssetCache denied = openMetadataAsset("svc.db.public.denied", "denied", Instant.parse("2026-07-25T01:00:00Z"));
        CatalogAssetExtension allowedExtension = enabledExtension();
        CatalogAssetExtension deniedExtension = enabledExtension();
        String allowedKey = CatalogAssetKey.openMetadataDataset(allowed);
        String deniedKey = CatalogAssetKey.openMetadataDataset(denied);
        when(
            assetTagService.findMatchingAssetKeysWithin(
                org.mockito.ArgumentMatchers.eq("DATASET"),
                any(),
                org.mockito.ArgumentMatchers.eq(List.of(tagId))
            )
        )
            .thenReturn(Set.of(allowedKey, deniedKey));
        when(assetRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(allowed, denied));
        allowedExtension.setOmAsset(allowed);
        deniedExtension.setOmAsset(denied);
        when(extensionRepository.findByOmAssetIn(List.of(allowed, denied))).thenReturn(List.of(allowedExtension, deniedExtension));
        when(mappingRepository.findByNormalizedFqnIn(any())).thenReturn(List.of());
        when(accessChecker.canRead(any(CatalogDataset.class))).thenReturn(true, false);
        when(accessChecker.departmentAllowed(any(CatalogDataset.class), any())).thenReturn(true);
        when(datasetRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(assetTagService.listAssetTags(List.of(new AssetRef("DATASET", allowedKey))))
            .thenReturn(Map.of(new AssetRef("DATASET", allowedKey), List.of()));

        CatalogAssetPortalService.AssetPage page = service.listAssets(query(List.of(tagId), 0, 10), "D01");

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.content()).extracting(CatalogAssetPortalService.AssetSummary::assetKey).containsExactly(allowedKey);
    }

    @Test
    void tagFilterBatchLoadsCandidateMetadataAndUsesCanonicalKeyAsStableTieBreaker() {
        UUID tagId = UUID.randomUUID();
        Instant sharedTimestamp = Instant.parse("2026-07-25T02:00:00Z");
        OpenMetadataAssetCache secondByKey = openMetadataAsset("svc.db.public.zeta", "zeta", sharedTimestamp);
        OpenMetadataAssetCache firstByKey = openMetadataAsset("svc.db.public.alpha", "alpha", sharedTimestamp);
        CatalogAssetExtension secondExtension = enabledExtension();
        secondExtension.setOmAsset(secondByKey);
        CatalogAssetExtension firstExtension = enabledExtension();
        firstExtension.setOmAsset(firstByKey);
        String firstKey = CatalogAssetKey.openMetadataDataset(firstByKey);
        String secondKey = CatalogAssetKey.openMetadataDataset(secondByKey);

        when(
            assetTagService.findMatchingAssetKeysWithin(
                org.mockito.ArgumentMatchers.eq("DATASET"),
                any(),
                org.mockito.ArgumentMatchers.eq(List.of(tagId))
            )
        )
            .thenReturn(Set.of(firstKey, secondKey));
        when(assetRepository.count(any(Specification.class))).thenReturn(2L);
        when(datasetRepository.count(any(Specification.class))).thenReturn(0L);
        when(assetRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(secondByKey, firstByKey));
        when(datasetRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(extensionRepository.findByOmAssetIn(List.of(secondByKey, firstByKey)))
            .thenReturn(List.of(secondExtension, firstExtension));
        when(mappingRepository.findByNormalizedFqnIn(any())).thenReturn(List.of());
        when(accessChecker.canRead(any(CatalogDataset.class))).thenReturn(true);
        when(accessChecker.departmentAllowed(any(CatalogDataset.class), any())).thenReturn(true);
        when(assetTagService.listAssetTags(any())).thenReturn(Map.of());

        CatalogAssetPortalService.AssetPage page = service.listAssets(query(List.of(tagId), 0, 20), "D01");

        assertThat(page.content())
            .extracting(CatalogAssetPortalService.AssetSummary::assetKey)
            .containsExactly(firstKey, secondKey);
        verify(extensionRepository).findByOmAssetIn(List.of(secondByKey, firstByKey));
        verify(mappingRepository).findByNormalizedFqnIn(Set.of("svc.db.public.alpha", "svc.db.public.zeta"));
        verify(extensionRepository, never()).findFirstByOmAsset(any());
        verify(mappingRepository, never()).findFirstByFqnIgnoreCase(any());
        verify(datasetRepository, never()).findById(any());
    }

    @Test
    void tagFilterRejectsAnUnboundedCandidateScanBeforeMaterializingRows() {
        UUID tagId = UUID.randomUUID();
        when(assetRepository.count(any(Specification.class))).thenReturn(5_001L);
        when(datasetRepository.count(any(Specification.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.listAssets(query(List.of(tagId), 0, 20), "D01"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            );
        verify(assetRepository, never()).findAll(any(Specification.class), any(Sort.class));
        verify(datasetRepository, never()).findAll(any(Specification.class), any(Sort.class));
        verify(assetTagService, never())
            .findMatchingAssetKeysWithin(any(), any(), any());
    }

    @Test
    void emptyTagFilterKeepsTheExistingPagedRepositoryPath() {
        when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));

        service.listAssets(query(List.of(), 0, 20), "D01");

        verify(assetRepository, never()).findAll(any(Specification.class), any(Sort.class));
        verify(assetTagService, never())
            .findMatchingAssetKeysWithin(any(), any(), any());
    }

    @Test
    void detailUsesTheSameMappedCanonicalKeyAndStructuredTagsAsTheList() {
        UUID tagId = UUID.randomUUID();
        CatalogDataset mappedLegacy = legacyDataset("mapped-orders");
        OpenMetadataAssetCache mapped = openMetadataAsset(
            "svc.db.public.orders",
            "orders",
            Instant.parse("2026-07-25T02:00:00Z")
        );
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        mapping.setFqn(mapped.getFqn());
        mapping.setLegacyDatasetId(mappedLegacy.getId());
        mapping.setMatchStatus("MATCHED");
        String mappedKey = CatalogAssetKey.dataset(mappedLegacy);
        CatalogTagDto tag = tag(tagId);
        AssetRef ref = new AssetRef("DATASET", mappedKey);
        when(assetRepository.findById(mapped.getId())).thenReturn(Optional.of(mapped));
        when(extensionRepository.findFirstByOmAsset(mapped)).thenReturn(Optional.of(enabledExtension()));
        when(mappingRepository.findFirstByFqnIgnoreCase(mapped.getFqn())).thenReturn(Optional.of(mapping));
        when(datasetRepository.findById(mappedLegacy.getId())).thenReturn(Optional.of(mappedLegacy));
        when(accessChecker.canRead(mappedLegacy)).thenReturn(true);
        when(accessChecker.departmentAllowed(mappedLegacy, "D01")).thenReturn(true);
        when(columnRepository.findByAssetOrderByOrdinalPositionAsc(mapped)).thenReturn(List.of());
        when(assetTagService.listAssetTags(List.of(ref))).thenReturn(Map.of(ref, List.of(tag)));

        CatalogAssetPortalService.AssetDetail detail = service.getAsset(mapped.getId(), "D01");

        assertThat(detail.asset().assetKey()).isEqualTo(mappedKey);
        assertThat(detail.asset().assetTags()).containsExactly(tag);
    }

    private CatalogAssetPortalService.AssetQuery query(List<UUID> tagIds, int page, int size) {
        return new CatalogAssetPortalService.AssetQuery(
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
            tagIds,
            page,
            size,
            null,
            null
        );
    }

    private OpenMetadataAssetCache openMetadataAsset(String fqn, String table, Instant lastSyncedAt) {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.randomUUID());
        asset.setOmEntityId(UUID.randomUUID().toString());
        asset.setFqn(fqn);
        asset.setTableName(table);
        asset.setDisplayName(table);
        asset.setSourceType("mysql");
        asset.setSyncStatus("SYNCED");
        asset.setLastSyncedAt(lastSyncedAt);
        return asset;
    }

    private CatalogDataset legacyDataset(String table) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSourceId(UUID.randomUUID());
        dataset.setName(table);
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable(table);
        dataset.setEnabled(true);
        return dataset;
    }

    private CatalogAssetExtension enabledExtension() {
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setEnabled(true);
        return extension;
    }

    private CatalogTagDto tag(UUID id) {
        return new CatalogTagDto(id, UUID.randomUUID(), "QUALITY", "高可信", "#1677ff", false, true, null, 3);
    }
}
