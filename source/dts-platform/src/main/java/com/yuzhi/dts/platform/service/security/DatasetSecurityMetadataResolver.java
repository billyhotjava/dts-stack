package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetSecurityMappingRepository;
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

    private static final List<String> DEPT_CODE_CANDIDATES = List.of(
        "dept_code",
        "department_code",
        "dept",
        "department",
        "dept_id",
        "department_id",
        "org_code",
        "org",
        "org_id",
        "organization_code",
        "organization_id"
    );

    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogDatasetSecurityMappingRepository mappingRepository;

    public DatasetSecurityMetadataResolver(
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogDatasetSecurityMappingRepository mappingRepository
    ) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.mappingRepository = mappingRepository;
    }

    public Optional<String> findDataLevelColumn(CatalogDataset dataset) {
        return findDataLevelColumnInfo(dataset).map(ResolvedColumn::name);
    }

    public Optional<ResolvedColumn> findDataLevelColumnInfo(CatalogDataset dataset) {
        if (dataset == null) return Optional.empty();
        String configured = mappingRepository
            .findById(dataset.getId())
            .map(mapping -> resolveText(mapping.getDataLevelField()))
            .orElse(null);
        if (configured != null) {
            return findColumnInfo(dataset, configured);
        }
        // 首选 hiveTable，其次 dataset 名称，再次遍历所有已登记的表
        String preferred = resolveText(dataset.getHiveTable());
        if (preferred != null) {
            Optional<ResolvedColumn> column = findDataLevelColumn(dataset, preferred);
            if (column.isPresent()) {
                return column;
            }
        }
        String fallback = resolveText(dataset.getName());
        if (fallback != null && !fallback.equalsIgnoreCase(preferred)) {
            Optional<ResolvedColumn> column = findDataLevelColumn(dataset, fallback);
            if (column.isPresent()) {
                return column;
            }
        }
        List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
        if (!CollectionUtils.isEmpty(tables)) {
            for (CatalogTableSchema table : tables) {
                Optional<ResolvedColumn> column = resolveFromTable(table);
                if (column.isPresent()) {
                    return column;
                }
            }
        }
        return Optional.empty();
    }

    public Optional<String> findDeptColumn(CatalogDataset dataset) {
        if (dataset == null) return Optional.empty();
        String configured = mappingRepository
            .findById(dataset.getId())
            .map(mapping -> resolveText(mapping.getDeptField()))
            .orElse(null);
        if (configured != null) {
            return Optional.of(configured);
        }
        String preferred = resolveText(dataset.getHiveTable());
        if (preferred != null) {
            Optional<String> column = findDeptColumn(dataset, preferred);
            if (column.isPresent()) {
                return column;
            }
        }
        String fallback = resolveText(dataset.getName());
        if (fallback != null && !fallback.equalsIgnoreCase(preferred)) {
            Optional<String> column = findDeptColumn(dataset, fallback);
            if (column.isPresent()) {
                return column;
            }
        }
        List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
        if (!CollectionUtils.isEmpty(tables)) {
            for (CatalogTableSchema table : tables) {
                Map<String, String> map = buildColumnNameMap(columnRepository.findByTable(table));
                Optional<String> column = resolveDeptFromColumns(map);
                if (column.isPresent()) {
                    return column;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedColumn> findDataLevelColumn(CatalogDataset dataset, String tableName) {
        return tableRepository
            .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
            .flatMap(table -> resolveFromTable(table));
    }

    private Optional<String> findDeptColumn(CatalogDataset dataset, String tableName) {
        return tableRepository
            .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
            .flatMap(table -> resolveDeptFromColumns(buildColumnNameMap(columnRepository.findByTable(table))));
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

    public static Optional<String> resolveDeptFromColumns(Map<String, String> columnMap) {
        if (columnMap == null || columnMap.isEmpty()) {
            return Optional.empty();
        }
        for (String candidate : DEPT_CODE_CANDIDATES) {
            String match = columnMap.get(candidate.toLowerCase(Locale.ROOT));
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedColumn> resolveFromTable(CatalogTableSchema table) {
        if (table == null) {
            return Optional.empty();
        }
        List<CatalogColumnSchema> columns = columnRepository.findByTable(table);
        Map<String, String> map = buildColumnNameMap(columns);
        Optional<String> name = resolveFromColumns(map);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        String resolvedName = name.orElseThrow();
        for (CatalogColumnSchema col : columns) {
            if (col == null || !StringUtils.hasText(col.getName())) continue;
            if (col.getName().trim().equalsIgnoreCase(resolvedName.trim())) {
                return Optional.of(toResolvedColumn(col));
            }
        }
        return Optional.of(new ResolvedColumn(resolvedName, null, false));
    }

    private Optional<ResolvedColumn> findColumnInfo(CatalogDataset dataset, String columnName) {
        if (dataset == null || !StringUtils.hasText(columnName)) {
            return Optional.empty();
        }
        String normalized = columnName.trim();
	        String preferred = resolveText(dataset.getHiveTable());
	        if (preferred != null) {
	            Optional<CatalogTableSchema> tableOpt = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, preferred);
	            if (tableOpt.isPresent()) {
	                Optional<ResolvedColumn> col = findColumnInfoInTable(tableOpt.orElseThrow(), normalized);
	                if (col.isPresent()) return col;
	            }
	        }
        String fallback = resolveText(dataset.getName());
	        if (fallback != null && !fallback.equalsIgnoreCase(preferred)) {
	            Optional<CatalogTableSchema> tableOpt = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, fallback);
	            if (tableOpt.isPresent()) {
	                Optional<ResolvedColumn> col = findColumnInfoInTable(tableOpt.orElseThrow(), normalized);
	                if (col.isPresent()) return col;
	            }
	        }
	        List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
	        if (!CollectionUtils.isEmpty(tables)) {
            for (CatalogTableSchema table : tables) {
                Optional<ResolvedColumn> col = findColumnInfoInTable(table, normalized);
                if (col.isPresent()) return col;
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedColumn> findColumnInfoInTable(CatalogTableSchema table, String columnName) {
        if (table == null || !StringUtils.hasText(columnName)) {
            return Optional.empty();
        }
        List<CatalogColumnSchema> columns = columnRepository.findByTable(table);
        if (columns == null || columns.isEmpty()) {
            return Optional.empty();
        }
        String lower = columnName.trim().toLowerCase(Locale.ROOT);
        for (CatalogColumnSchema col : columns) {
            if (col == null || !StringUtils.hasText(col.getName())) continue;
            if (col.getName().trim().toLowerCase(Locale.ROOT).equals(lower)) {
                return Optional.of(toResolvedColumn(col));
            }
        }
        return Optional.empty();
    }

    private static ResolvedColumn toResolvedColumn(CatalogColumnSchema column) {
        String dataType = column != null && StringUtils.hasText(column.getDataType()) ? column.getDataType().trim() : null;
        return new ResolvedColumn(column.getName(), dataType, isNumericType(dataType));
    }

    private static boolean isNumericType(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String upper = type.trim().toUpperCase(Locale.ROOT);
        return upper.contains("INT") ||
            upper.contains("DECIMAL") ||
            upper.contains("NUMERIC") ||
            upper.contains("NUMBER") ||
            upper.contains("FLOAT") ||
            upper.contains("DOUBLE") ||
            upper.contains("REAL");
    }

    public record ResolvedColumn(String name, String dataType, boolean numeric) {}
}
