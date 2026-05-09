package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultLakeDatasetGuardTest {

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private DefaultDestinationSyncService defaultDestinationSyncService;

    @InjectMocks
    private DefaultLakeDatasetGuard guard;

    @Test
    void requireDefaultLakeDataset_acceptsDatasetFromDefaultLake() {
        UUID defaultSourceId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = dataset(datasetId, defaultSourceId);

        when(defaultDestinationSyncService.checkDefaultDestinationStatus()).thenReturn(defaultStatus(defaultSourceId));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThat(guard.requireDefaultLakeDataset(datasetId)).isSameAs(dataset);
    }

    @Test
    void requireDefaultLakeDataset_rejectsExternalDataset() {
        UUID defaultSourceId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = dataset(datasetId, UUID.randomUUID());

        when(defaultDestinationSyncService.checkDefaultDestinationStatus()).thenReturn(defaultStatus(defaultSourceId));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() -> guard.requireDefaultLakeDataset(datasetId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("默认数据湖");
    }

    @Test
    void requireDefaultLakeDataset_rejectsWhenDefaultLakeMappingMissing() {
        UUID datasetId = UUID.randomUUID();
        CatalogDataset dataset = dataset(datasetId, UUID.randomUUID());

        when(defaultDestinationSyncService.checkDefaultDestinationStatus())
            .thenReturn(DefaultDestinationSyncService.DefaultDestinationStatus.missing("未配置默认数据湖"));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() -> guard.requireDefaultLakeDataset(datasetId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未识别默认数据湖数据源");
    }

    private CatalogDataset dataset(UUID datasetId, UUID sourceId) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("ods_order");
        dataset.setSourceId(sourceId);
        return dataset;
    }

    private DefaultDestinationSyncService.DefaultDestinationStatus defaultStatus(UUID sourceId) {
        return new DefaultDestinationSyncService.DefaultDestinationStatus(
            true,
            true,
            true,
            "默认数据湖",
            "postgresqlwriter",
            null,
            sourceId.toString()
        );
    }
}
