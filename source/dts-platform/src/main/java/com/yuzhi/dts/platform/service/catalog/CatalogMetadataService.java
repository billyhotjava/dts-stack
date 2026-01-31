package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CatalogMetadataService {

    private static final String LOCAL_FQN_PREFIX = "catalog:";

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final AccessChecker accessChecker;

    public CatalogMetadataService(
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        AccessChecker accessChecker
    ) {
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.accessChecker = accessChecker;
    }

    public OpenMetadataService.OpenMetadataTablePage listLocalTables(String keyword, int size, String activeDept) {
        int limit = Math.max(1, Math.min(size, 200));
        String needle = normalize(keyword);
        boolean searched = StringUtils.hasText(needle);

        List<CatalogDataset> datasets = datasetRepository.findAll();
        List<CatalogDataset> visible = new ArrayList<>();
        for (CatalogDataset dataset : datasets) {
            if (dataset == null) {
                continue;
            }
            if (!accessChecker.canRead(dataset)) {
                continue;
            }
            if (!accessChecker.departmentAllowed(dataset, activeDept)) {
                continue;
            }
            visible.add(dataset);
        }

        if (visible.isEmpty()) {
            return new OpenMetadataService.OpenMetadataTablePage(true, List.of(), 0, keyword, searched, "暂无元数据");
        }

        List<CatalogTableSchema> tables = tableRepository.findByDatasetIn(visible);
        Map<UUID, List<CatalogTableSchema>> tablesByDataset = new LinkedHashMap<>();
        for (CatalogTableSchema table : tables) {
            CatalogDataset dataset = table.getDataset();
            if (dataset == null || dataset.getId() == null) {
                continue;
            }
            tablesByDataset.computeIfAbsent(dataset.getId(), key -> new ArrayList<>()).add(table);
        }

        List<CatalogColumnSchema> columns = tables.isEmpty() ? List.of() : columnRepository.findByTableIn(tables);
        Map<UUID, Integer> columnCounts = new LinkedHashMap<>();
        for (CatalogColumnSchema column : columns) {
            CatalogTableSchema table = column.getTable();
            if (table == null || table.getId() == null) {
                continue;
            }
            columnCounts.merge(table.getId(), 1, Integer::sum);
        }

        List<OpenMetadataService.OpenMetadataTableSummary> summaries = new ArrayList<>();
        for (CatalogDataset dataset : visible) {
            List<CatalogTableSchema> datasetTables = tablesByDataset.getOrDefault(dataset.getId(), List.of());
            if (datasetTables.isEmpty()) {
                if (matchesKeyword(dataset, null, needle)) {
                    summaries.add(toSummary(dataset, null, 0));
                }
                continue;
            }
            for (CatalogTableSchema table : datasetTables) {
                if (!matchesKeyword(dataset, table, needle)) {
                    continue;
                }
                int count = columnCounts.getOrDefault(table.getId(), 0);
                summaries.add(toSummary(dataset, table, count));
            }
        }

        summaries.sort(Comparator.comparing(OpenMetadataService.OpenMetadataTableSummary::name, String.CASE_INSENSITIVE_ORDER));
        int total = summaries.size();
        if (summaries.size() > limit) {
            summaries = summaries.subList(0, limit);
        }

        return new OpenMetadataService.OpenMetadataTablePage(true, summaries, total, keyword, searched, null);
    }

    public OpenMetadataService.OpenMetadataResult fetchLocalTableDetail(String fqn, String activeDept) {
        UUID id = parseLocalId(fqn);
        if (id == null) {
            return OpenMetadataService.OpenMetadataResult.notFound(fqn, "未找到本地元数据");
        }
        CatalogTableSchema table = tableRepository.findById(id).orElse(null);
        CatalogDataset dataset = null;
        if (table != null) {
            dataset = table.getDataset();
        } else {
            dataset = datasetRepository.findById(id).orElse(null);
            if (dataset != null && StringUtils.hasText(dataset.getHiveTable())) {
                table = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, dataset.getHiveTable()).orElse(null);
            }
        }

        if (dataset == null) {
            return OpenMetadataService.OpenMetadataResult.notFound(fqn, "未找到本地元数据");
        }
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, activeDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }

        List<CatalogColumnSchema> columns = table != null ? columnRepository.findByTable(table) : List.of();
        Map<String, Object> entity = new LinkedHashMap<>();
        String tableName = table != null ? table.getName() : defaultIfBlank(dataset.getHiveTable(), dataset.getName());
        entity.put("name", tableName);
        entity.put("displayName", defaultIfBlank(dataset.getName(), tableName));
        entity.put("description", dataset.getDescription());
        entity.put("owner", dataset.getOwner());
        entity.put("domain", dataset.getDomain() != null ? dataset.getDomain().getName() : null);
        entity.put("database", dataset.getHiveDatabase());
        entity.put("schema", dataset.getHiveDatabase());
        entity.put("service", dataset.getType());
        entity.put("tags", dataset.getTags());
        entity.put("columns", buildColumnPayload(columns));
        return OpenMetadataService.OpenMetadataResult.found(fqn, entity, null);
    }

    public boolean isLocalFqn(String fqn) {
        return parseLocalId(fqn) != null;
    }

    private List<Map<String, Object>> buildColumnPayload(List<CatalogColumnSchema> columns) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (columns == null) {
            return items;
        }
        for (CatalogColumnSchema column : columns) {
            if (column == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", column.getName());
            item.put("dataType", column.getDataType());
            item.put("description", column.getComment());
            item.put("status", column.getStatus());
            items.add(item);
        }
        return items;
    }

    private OpenMetadataService.OpenMetadataTableSummary toSummary(
        CatalogDataset dataset,
        CatalogTableSchema table,
        int columnCount
    ) {
        String id = table != null && table.getId() != null
            ? table.getId().toString()
            : dataset.getId() != null ? dataset.getId().toString() : null;
        String name = table != null ? table.getName() : defaultIfBlank(dataset.getHiveTable(), dataset.getName());
        String fqn = id != null ? LOCAL_FQN_PREFIX + id : null;
        return new OpenMetadataService.OpenMetadataTableSummary(
            id,
            name,
            fqn,
            dataset.getType(),
            dataset.getHiveDatabase(),
            dataset.getHiveDatabase(),
            dataset.getOwner(),
            dataset.getDomain() != null ? dataset.getDomain().getName() : null,
            dataset.getTags(),
            dataset.getDescription(),
            Math.max(columnCount, 0)
        );
    }

    private boolean matchesKeyword(CatalogDataset dataset, CatalogTableSchema table, String needle) {
        if (!StringUtils.hasText(needle)) {
            return true;
        }
        String normalized = needle.toLowerCase(Locale.ROOT);
        return contains(dataset.getName(), normalized)
            || contains(dataset.getHiveDatabase(), normalized)
            || contains(dataset.getHiveTable(), normalized)
            || contains(dataset.getDescription(), normalized)
            || contains(dataset.getTags(), normalized)
            || (table != null && contains(table.getName(), normalized));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String needle) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(needle)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return StringUtils.hasText(fallback) ? fallback : "-";
    }

    private UUID parseLocalId(String fqn) {
        if (!StringUtils.hasText(fqn)) {
            return null;
        }
        String text = fqn.trim();
        if (text.startsWith(LOCAL_FQN_PREFIX)) {
            text = text.substring(LOCAL_FQN_PREFIX.length());
        }
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
