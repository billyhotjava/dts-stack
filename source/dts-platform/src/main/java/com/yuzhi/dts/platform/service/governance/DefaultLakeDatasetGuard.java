package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultLakeDatasetGuard {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLakeDatasetGuard.class);

    private final CatalogDatasetRepository datasetRepository;
    private final DefaultDestinationSyncService defaultDestinationSyncService;

    public DefaultLakeDatasetGuard(
        CatalogDatasetRepository datasetRepository,
        DefaultDestinationSyncService defaultDestinationSyncService
    ) {
        this.datasetRepository = datasetRepository;
        this.defaultDestinationSyncService = defaultDestinationSyncService;
    }

    public CatalogDataset requireDefaultLakeDataset(UUID datasetId) {
        if (datasetId == null) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElseThrow(() -> new IllegalArgumentException("数据集不存在"));
        assertDefaultLakeDataset(dataset, requireDefaultLakeSourceId());
        return dataset;
    }

    public boolean isDefaultLakeDataset(CatalogDataset dataset, UUID defaultLakeSourceId) {
        return dataset != null && defaultLakeSourceId != null && defaultLakeSourceId.equals(dataset.getSourceId());
    }

    public Optional<UUID> currentDefaultLakeSourceId() {
        try {
            return resolveDefaultLakeSourceId(false);
        } catch (RuntimeException ex) {
            LOG.debug("Failed to resolve default data lake source id: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public UUID requireDefaultLakeSourceId() {
        return resolveDefaultLakeSourceId(true)
            .orElseThrow(() ->
                new IllegalStateException("未识别默认数据湖数据源，请先在管理端配置默认数据湖并确认平台已映射本地数据源")
            );
    }

    public void requireDefaultLakeSource(UUID sourceId, String message) {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        UUID defaultLakeSourceId = requireDefaultLakeSourceId();
        if (!defaultLakeSourceId.equals(sourceId)) {
            throw new IllegalArgumentException(StringUtils.hasText(message) ? message : "仅允许选择默认数据湖数据源");
        }
    }

    private Optional<UUID> resolveDefaultLakeSourceId(boolean required) {
        DefaultDestinationSyncService.DefaultDestinationStatus status;
        try {
            status = defaultDestinationSyncService.checkDefaultDestinationStatus();
        } catch (RuntimeException ex) {
            if (required) {
                throw new IllegalStateException("默认数据湖状态读取失败: " + ex.getMessage(), ex);
            }
            return Optional.empty();
        }
        if (status == null || !status.available() || !StringUtils.hasText(status.dataSourceId())) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(status.dataSourceId()));
        } catch (IllegalArgumentException ex) {
            if (required) {
                throw new IllegalStateException("默认数据湖数据源标识无效: " + status.dataSourceId(), ex);
            }
            return Optional.empty();
        }
    }

    private void assertDefaultLakeDataset(CatalogDataset dataset, UUID defaultLakeSourceId) {
        if (!isDefaultLakeDataset(dataset, defaultLakeSourceId)) {
            throw new IllegalArgumentException("数据质量仅允许选择默认数据湖下的数据集，请先将外部数据源入湖到 ODS 后再配置质量规则");
        }
    }
}
