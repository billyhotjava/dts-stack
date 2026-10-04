package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

class QualityDatasetCatalogServiceTest {

    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000091");
    private static final UUID READABLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000091");
    private static final UUID HIDDEN_ID = UUID.fromString("30000000-0000-0000-0000-000000000091");

    @Test
    void hidesUnreadableDefaultLakeAssetsFromTheQualitySelector() {
        CatalogDatasetRepository repository = mock(CatalogDatasetRepository.class);
        DefaultLakeDatasetGuard defaultLake = mock(DefaultLakeDatasetGuard.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        CatalogDataset readable = dataset(READABLE_ID, "ods_readable");
        CatalogDataset hidden = dataset(HIDDEN_ID, "ods_hidden");
        when(defaultLake.requireDefaultLakeSourceId()).thenReturn(SOURCE_ID);
        when(repository.findBySourceIdAndEnabledTrueOrderByNameAscIdAsc(SOURCE_ID)).thenReturn(List.of(readable, hidden));
        when(readGuard.readableDatasetIds(List.of(readable, hidden), "D01")).thenReturn(Set.of(READABLE_ID));

        var service = new QualityDatasetCatalogService(repository, defaultLake, readGuard);

        assertThat(service.listReadableDefaultLakeDatasets("D01"))
            .extracting(option -> option.id().toString())
            .containsExactly(READABLE_ID.toString());
        verify(repository).findBySourceIdAndEnabledTrueOrderByNameAscIdAsc(SOURCE_ID);
    }

    @Test
    void qualitySelectorRepositoryQueryEagerlyLoadsBusinessDomain() throws NoSuchMethodException {
        EntityGraph entityGraph = CatalogDatasetRepository.class
            .getMethod("findBySourceIdAndEnabledTrueOrderByNameAscIdAsc", UUID.class)
            .getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("domain");
    }

    private CatalogDataset dataset(UUID id, String name) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setName(name);
        dataset.setSourceId(SOURCE_ID);
        dataset.setEnabled(true);
        dataset.setHiveDatabase("default");
        dataset.setHiveTable(name);
        return dataset;
    }
}
