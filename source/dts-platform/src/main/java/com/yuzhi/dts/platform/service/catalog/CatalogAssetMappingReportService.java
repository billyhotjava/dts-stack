package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogAssetMappingReportService {

    private final CatalogDatasetRepository datasetRepository;
    private final OpenMetadataAssetCacheRepository openMetadataAssetRepository;
    private final CatalogAssetMappingRepository mappingRepository;
    private final QueryDatasetAssetRepository queryDatasetRepository;

    public CatalogAssetMappingReportService(
        CatalogDatasetRepository datasetRepository,
        OpenMetadataAssetCacheRepository openMetadataAssetRepository,
        CatalogAssetMappingRepository mappingRepository,
        QueryDatasetAssetRepository queryDatasetRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.openMetadataAssetRepository = openMetadataAssetRepository;
        this.mappingRepository = mappingRepository;
        this.queryDatasetRepository = queryDatasetRepository;
    }

    public MappingReport dryRun() {
        List<Candidate> candidates = new ArrayList<>();
        for (CatalogDataset dataset : datasetRepository.findAll()) {
            candidates.add(new Candidate(
                CatalogAssetType.DATASET.name(),
                safeKey(() -> CatalogAssetKey.dataset(dataset)),
                dataset.getId() == null ? null : dataset.getId().toString(),
                "dts-catalog",
                dataset.getName()
            ));
        }
        for (OpenMetadataAssetCache asset : openMetadataAssetRepository.findAll()) {
            candidates.add(new Candidate(
                CatalogAssetType.DATASET.name(),
                safeKey(() -> CatalogAssetKey.openMetadataDataset(asset)),
                asset.getId() == null ? null : asset.getId().toString(),
                "openmetadata",
                asset.getFqn()
            ));
        }
        for (QueryDatasetAsset dataset : queryDatasetRepository.findAll()) {
            candidates.add(new Candidate(
                CatalogAssetType.BI_DATASET.name(),
                safeKey(() -> CatalogAssetKey.biDataset(dataset)),
                dataset.getId() == null ? null : dataset.getId().toString(),
                "query-dataset",
                dataset.getName()
            ));
        }

        Map<String, List<Candidate>> byIdentity = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = candidate.assetType() + ":" + candidate.assetKey();
            byIdentity.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }

        List<Conflict> conflicts = byIdentity
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> new Conflict(entry.getKey(), entry.getValue()))
            .toList();

        long existingMappings = mappingRepository.count();
        return new MappingReport(candidates.size(), existingMappings, conflicts.size(), candidates, conflicts);
    }

    private static String safeKey(KeySupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return "INVALID:" + ex.getMessage();
        }
    }

    @FunctionalInterface
    private interface KeySupplier {
        String get();
    }

    public record MappingReport(
        int candidateCount,
        long existingMappingCount,
        int conflictCount,
        List<Candidate> candidates,
        List<Conflict> conflicts
    ) {}

    public record Candidate(
        String assetType,
        String assetKey,
        String assetId,
        String sourceSystem,
        String displayName
    ) {}

    public record Conflict(String identity, List<Candidate> candidates) {}
}
