package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 解析数据集在目录中的结构信息（例如数据密级字段名），以便在运行时应用安全策略。
 */
@Component
public class DatasetSecurityMetadataResolver {

    private static final List<String> DATA_LEVEL_CANDIDATES = List.of(
        "data_level",
        "data_security_level",
        "data_secret_level",
        "security_level",
        "secret_level",
        "classification_level",
        "class_level",
        "protect_level",
        "data_protect_level",
        "level"
    );

    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;

    public DatasetSecurityMetadataResolver(
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository
    ) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    public Optional<String> findDataLevelColumn(CatalogDataset dataset) {
        if (dataset == null) return Optional.empty();
        // 首选 hiveTable，其次 dataset 名称，再次遍历所有已登记的表
        String preferred = resolveText(dataset.getHiveTable());
        if (preferred != null) {
            Optional<String> column = findDataLevelColumn(dataset, preferred);
            if (column.isPresent()) {
                return column;
            }
        }
        String fallback = resolveText(dataset.getName());
        if (fallback != null && !fallback.equalsIgnoreCase(preferred)) {
            Optional<String> column = findDataLevelColumn(dataset, fallback);
            if (column.isPresent()) {
                return column;
            }
        }
        List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
        if (!CollectionUtils.isEmpty(tables)) {
            for (CatalogTableSchema table : tables) {
                Map<String, String> map = buildColumnNameMap(columnRepository.findByTable(table));
                Optional<String> column = resolveFromColumns(map);
                if (column.isPresent()) {
                    return column;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> findDataLevelColumn(CatalogDataset dataset, String tableName) {
        return tableRepository
            .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
            .flatMap(table -> resolveFromColumns(buildColumnNameMap(columnRepository.findByTable(table))));
    }

    private Map<String, String> buildColumnNameMap(List<CatalogColumnSchema> columns) {
        Map<String, String> map = new HashMap<>();
        if (columns == null) {
            return map;
        }
        for (CatalogColumnSchema col : columns) {
            if (!StringUtils.hasText(col.getName())) continue;
            map.put(col.getName().toLowerCase(Locale.ROOT), col.getName());
        }
        return map;
    }

    private String resolveText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 工具方法：根据列名字典在内存中解析数据密级字段。
     */
    public static Optional<String> resolveFromColumns(Map<String, String> columnMap) {
        if (columnMap == null || columnMap.isEmpty()) {
            return Optional.empty();
        }
        for (String candidate : DATA_LEVEL_CANDIDATES) {
            String match = columnMap.get(candidate.toLowerCase(Locale.ROOT));
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }
}
