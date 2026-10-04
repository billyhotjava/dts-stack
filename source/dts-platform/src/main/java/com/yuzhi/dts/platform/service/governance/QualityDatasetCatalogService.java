package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityDatasetOptionDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityDatasetCatalogService {

    private final CatalogDatasetRepository datasetRepository;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final QualityDatasetReadGuard datasetReadGuard;

    public QualityDatasetCatalogService(
        CatalogDatasetRepository datasetRepository,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        QualityDatasetReadGuard datasetReadGuard
    ) {
        this.datasetRepository = datasetRepository;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.datasetReadGuard = datasetReadGuard;
    }

    @Transactional(readOnly = true)
    public List<QualityDatasetOptionDto> listReadableDefaultLakeDatasets(String activeDeptHeader) {
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.requireDefaultLakeSourceId();
        List<CatalogDataset> candidates = datasetRepository
            .findBySourceIdAndEnabledTrueOrderByNameAscIdAsc(defaultLakeSourceId);
        Set<UUID> readableIds = datasetReadGuard.readableDatasetIds(candidates, activeDeptHeader);
        return candidates
            .stream()
            .filter(dataset -> readableIds.contains(dataset.getId()))
            .map(this::toOption)
            .toList();
    }

    private QualityDatasetOptionDto toOption(CatalogDataset dataset) {
        CatalogDomain domain = dataset.getDomain();
        return new QualityDatasetOptionDto(
            dataset.getId(),
            dataset.getName(),
            dataset.getHiveDatabase(),
            dataset.getHiveTable(),
            dataset.getHiveDatabase(),
            dataset.getHiveTable(),
            dataset.getSourceId(),
            domain != null ? domain.getId() : null,
            domain != null ? domain.getName() : null
        );
    }
}
