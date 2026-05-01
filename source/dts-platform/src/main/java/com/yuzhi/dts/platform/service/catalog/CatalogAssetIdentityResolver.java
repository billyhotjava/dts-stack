package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CatalogAssetIdentityResolver {

    private final OpenMetadataAssetCacheRepository assetRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;

    public CatalogAssetIdentityResolver(
        OpenMetadataAssetCacheRepository assetRepository,
        CatalogAssetMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository
    ) {
        this.assetRepository = assetRepository;
        this.mappingRepository = mappingRepository;
        this.datasetRepository = datasetRepository;
    }

    public Optional<ResolvedAsset> resolve(String ref) {
        if (!StringUtils.hasText(ref)) {
            return Optional.empty();
        }
        String trimmed = ref.trim();
        UUID uuid = parseUuid(trimmed);
        if (uuid != null) {
            Optional<OpenMetadataAssetCache> omAsset = assetRepository.findById(uuid);
            if (omAsset.isPresent()) {
                OpenMetadataAssetCache asset = omAsset.orElseThrow();
                CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
                CatalogDataset legacy = resolveLegacy(mapping);
                return Optional.of(new ResolvedAsset(asset, mapping, legacy, "om_asset_id"));
            }
            Optional<CatalogDataset> legacy = datasetRepository.findById(uuid);
            if (legacy.isPresent()) {
                CatalogDataset dataset = legacy.orElseThrow();
                CatalogAssetMapping mapping = mappingRepository.findFirstByLegacyDatasetId(dataset.getId()).orElse(null);
                OpenMetadataAssetCache asset = mapping != null && StringUtils.hasText(mapping.getFqn())
                    ? assetRepository.findFirstByFqnIgnoreCase(mapping.getFqn()).orElse(null)
                    : null;
                return Optional.of(new ResolvedAsset(asset, mapping, dataset, "legacy_dataset_id"));
            }
        }
        Optional<OpenMetadataAssetCache> byEntity = assetRepository.findFirstByOmEntityId(trimmed);
        if (byEntity.isPresent()) {
            OpenMetadataAssetCache asset = byEntity.orElseThrow();
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            return Optional.of(new ResolvedAsset(asset, mapping, resolveLegacy(mapping), "om_entity_id"));
        }
        Optional<OpenMetadataAssetCache> byFqn = assetRepository.findFirstByFqnIgnoreCase(trimmed);
        if (byFqn.isPresent()) {
            OpenMetadataAssetCache asset = byFqn.orElseThrow();
            CatalogAssetMapping mapping = mappingRepository.findFirstByFqnIgnoreCase(asset.getFqn()).orElse(null);
            return Optional.of(new ResolvedAsset(asset, mapping, resolveLegacy(mapping), "fqn"));
        }
        return Optional.empty();
    }

    private CatalogDataset resolveLegacy(CatalogAssetMapping mapping) {
        if (mapping == null || mapping.getLegacyDatasetId() == null) {
            return null;
        }
        return datasetRepository.findById(mapping.getLegacyDatasetId()).orElse(null);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record ResolvedAsset(
        OpenMetadataAssetCache omAsset,
        CatalogAssetMapping mapping,
        CatalogDataset legacyDataset,
        String resolvedBy
    ) {}
}
