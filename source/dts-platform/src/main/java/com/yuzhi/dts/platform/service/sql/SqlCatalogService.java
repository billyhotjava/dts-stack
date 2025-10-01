package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNode;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogNodeType;
import com.yuzhi.dts.platform.service.sql.dto.SqlCatalogRequest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlCatalogService.class);

    private static final Map<String, String> DATASOURCE_LABELS = Map.ofEntries(
        Map.entry("trino", "Trino"),
        Map.entry("hive", "Apache Hive"),
        Map.entry("jdbc", "JDBC"),
        Map.entry("spark", "Spark SQL")
    );

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final AccessChecker accessChecker;

    public SqlCatalogService(
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

    @Cacheable(cacheNames = "sqlCatalogTree", key = "#root.target.cacheKey(#request, #principal)")
    public SqlCatalogNode fetchTree(SqlCatalogRequest request, Principal principal) {
        SqlCatalogRequest safeRequest = request != null ? request : new SqlCatalogRequest(null, null, null, null);

        String datasourceKey = defaultDatasource(safeRequest.datasource());
        String rawDatasource = safeRequest.datasource();
        String catalogFilter = normalize(safeRequest.catalog());
        String schemaFilter = normalize(safeRequest.schema());
        String searchFilter = normalize(safeRequest.search());
        String principalName = principalName(principal);

        LOG.debug(
            "Fetching SQL catalog tree, user={}, datasource={}, catalog={}, schema={}, search={}",
            principalName,
            datasourceKey,
            catalogFilter,
            schemaFilter,
            searchFilter
        );

        List<CatalogDataset> datasets = datasetRepository
            .findAll()
            .stream()
            .filter(dataset -> matchesDatasource(dataset, datasourceKey))
            .filter(dataset -> matchesCatalog(dataset, catalogFilter))
            .filter(dataset -> matchesSchema(dataset, schemaFilter))
            .filter(this::filterByAccess)
            .sorted(
                Comparator
                    .comparing((CatalogDataset ds) -> sortKey(ds.getTrinoCatalog()))
                    .thenComparing(ds -> sortKey(ds.getHiveDatabase()))
                    .thenComparing(ds -> sortKey(ds.getName()))
            )
            .collect(Collectors.toList());

        if (datasets.isEmpty()) {
            LOG.debug("No datasets available for datasource={}, catalog={}, schema={}", datasourceKey, catalogFilter, schemaFilter);
            return new SqlCatalogNode(
                nodeId("datasource", datasourceKey),
                resolveDatasourceLabel(datasourceKey, rawDatasource),
                SqlCatalogNodeType.CATALOG,
                List.of()
            );
        }

        List<CatalogTableSchema> tables = datasets.isEmpty() ? List.of() : tableRepository.findByDatasetIn(datasets);
        Map<UUID, List<CatalogTableSchema>> tablesByDataset = indexTables(tables);

        List<CatalogColumnSchema> columns = tables.isEmpty() ? List.of() : columnRepository.findByTableIn(tables);
        Map<UUID, List<CatalogColumnSchema>> columnsByTable = indexColumns(columns);

        List<SqlCatalogNode> catalogNodes = buildCatalogNodes(datasets, tablesByDataset, columnsByTable);

        SqlCatalogNode root = new SqlCatalogNode(
            nodeId("datasource", datasourceKey),
            resolveDatasourceLabel(datasourceKey, rawDatasource),
            SqlCatalogNodeType.CATALOG,
            catalogNodes
        );

        if (searchFilter != null) {
            SqlCatalogNode filtered = filterTree(root, searchFilter);
            if (filtered != null) {
                return filtered;
            }
            LOG.debug("Search '{}' returned no matches under datasource={}", searchFilter, datasourceKey);
            return new SqlCatalogNode(root.id(), root.label(), root.type(), List.of());
        }

        return root;
    }

    public String cacheKey(SqlCatalogRequest request, Principal principal) {
        SqlCatalogRequest safeRequest = request != null ? request : new SqlCatalogRequest(null, null, null, null);
        String datasource = defaultDatasource(safeRequest.datasource());
        String catalog = normalize(safeRequest.catalog());
        String schema = normalize(safeRequest.schema());
        String search = normalize(safeRequest.search());
        String user = normalize(principalName(principal));

        return String.join(
            "|",
            keyPart(user),
            keyPart(datasource),
            keyPart(catalog),
            keyPart(schema),
            keyPart(search)
        );
    }

    private List<SqlCatalogNode> buildCatalogNodes(
        List<CatalogDataset> datasets,
        Map<UUID, List<CatalogTableSchema>> tablesByDataset,
        Map<UUID, List<CatalogColumnSchema>> columnsByTable
    ) {
        Map<String, List<CatalogDataset>> grouped = datasets
            .stream()
            .collect(Collectors.groupingBy(this::resolveCatalogLabel, Collectors.toCollection(ArrayList::new)));

        return grouped
            .entrySet()
            .stream()
            .map(entry -> buildCatalogNode(entry.getKey(), entry.getValue(), tablesByDataset, columnsByTable))
            .sorted(Comparator.comparing(SqlCatalogNode::label, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    private SqlCatalogNode buildCatalogNode(
        String catalogLabel,
        List<CatalogDataset> datasets,
        Map<UUID, List<CatalogTableSchema>> tablesByDataset,
        Map<UUID, List<CatalogColumnSchema>> columnsByTable
    ) {
        List<SqlCatalogNode> schemaNodes = datasets
            .stream()
            .map(dataset -> buildSchemaNode(dataset, tablesByDataset, columnsByTable))
            .sorted(Comparator.comparing(SqlCatalogNode::label, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        return new SqlCatalogNode(nodeId("catalog", catalogLabel), catalogLabel, SqlCatalogNodeType.CATALOG, schemaNodes);
    }

    private SqlCatalogNode buildSchemaNode(
        CatalogDataset dataset,
        Map<UUID, List<CatalogTableSchema>> tablesByDataset,
        Map<UUID, List<CatalogColumnSchema>> columnsByTable
    ) {
        List<CatalogTableSchema> tables = tablesByDataset.getOrDefault(dataset.getId(), List.of());
        List<SqlCatalogNode> tableNodes = tables
            .stream()
            .map(table -> buildTableNode(dataset, table, columnsByTable))
            .sorted(Comparator.comparing(SqlCatalogNode::label, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        return new SqlCatalogNode(
            nodeId("schema", dataset.getId()),
            resolveSchemaLabel(dataset),
            SqlCatalogNodeType.SCHEMA,
            tableNodes
        );
    }

    private SqlCatalogNode buildTableNode(
        CatalogDataset dataset,
        CatalogTableSchema table,
        Map<UUID, List<CatalogColumnSchema>> columnsByTable
    ) {
        List<CatalogColumnSchema> columns = columnsByTable.getOrDefault(table.getId(), List.of());
        List<SqlCatalogNode> columnNodes = columns
            .stream()
            .map(column -> new SqlCatalogNode(
                    nodeId("column", table.getId(), column.getId()),
                    formatColumnLabel(column),
                    SqlCatalogNodeType.COLUMN,
                    List.of()
                ))
            .sorted(Comparator.comparing(SqlCatalogNode::label, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

        return new SqlCatalogNode(
            nodeId("table", dataset.getId(), table.getId()),
            safeLabel(table.getName(), "table"),
            SqlCatalogNodeType.TABLE,
            columnNodes
        );
    }

    private SqlCatalogNode filterTree(SqlCatalogNode node, String search) {
        List<SqlCatalogNode> children = node.children() != null ? node.children() : List.of();
        List<SqlCatalogNode> filteredChildren = children
            .stream()
            .map(child -> filterTree(child, search))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (!filteredChildren.isEmpty() || matchesSearch(node.label(), search)) {
            return new SqlCatalogNode(node.id(), node.label(), node.type(), filteredChildren);
        }
        return matchesSearch(node.label(), search) ? new SqlCatalogNode(node.id(), node.label(), node.type(), List.of()) : null;
    }

    private boolean matchesDatasource(CatalogDataset dataset, String requested) {
        String datasetDatasource = resolveDatasetDatasource(dataset);
        boolean matches = Objects.equals(datasetDatasource, requested);
        if (!matches && LOG.isTraceEnabled()) {
            LOG.trace(
                "Dataset {} ({}) skipped: datasource mismatch {} != {}",
                dataset.getId(),
                dataset.getName(),
                datasetDatasource,
                requested
            );
        }
        return matches;
    }

    private boolean matchesCatalog(CatalogDataset dataset, String catalogFilter) {
        if (catalogFilter == null) {
            return true;
        }
        String datasetCatalog = normalize(dataset.getTrinoCatalog());
        boolean matches = Objects.equals(datasetCatalog, catalogFilter);
        if (!matches && LOG.isTraceEnabled()) {
            LOG.trace(
                "Dataset {} ({}) skipped: catalog mismatch {} != {}",
                dataset.getId(),
                dataset.getName(),
                datasetCatalog,
                catalogFilter
            );
        }
        return matches;
    }

    private boolean matchesSchema(CatalogDataset dataset, String schemaFilter) {
        if (schemaFilter == null) {
            return true;
        }
        String datasetSchema = normalize(dataset.getHiveDatabase());
        boolean matches = Objects.equals(datasetSchema, schemaFilter);
        if (!matches && LOG.isTraceEnabled()) {
            LOG.trace(
                "Dataset {} ({}) skipped: schema mismatch {} != {}",
                dataset.getId(),
                dataset.getName(),
                datasetSchema,
                schemaFilter
            );
        }
        return matches;
    }

    private boolean filterByAccess(CatalogDataset dataset) {
        boolean allowed = accessChecker.canRead(dataset);
        if (!allowed) {
            LOG.info("Dataset {} ({}) skipped due to access denial", dataset.getId(), dataset.getName());
        }
        return allowed;
    }

    private Map<UUID, List<CatalogTableSchema>> indexTables(List<CatalogTableSchema> tables) {
        return tables.stream().collect(Collectors.groupingBy(table -> table.getDataset().getId(), Collectors.toList()));
    }

    private Map<UUID, List<CatalogColumnSchema>> indexColumns(List<CatalogColumnSchema> columns) {
        return columns.stream().collect(Collectors.groupingBy(column -> column.getTable().getId(), Collectors.toList()));
    }

    private String resolveDatasourceLabel(String normalized, String raw) {
        String label = DATASOURCE_LABELS.getOrDefault(normalized, null);
        if (label != null) {
            return label;
        }
        if (raw != null && !raw.isBlank()) {
            return raw.trim();
        }
        return "Default Datasource";
    }

    private String resolveDatasetDatasource(CatalogDataset dataset) {
        String exposed = normalize(dataset.getExposedBy());
        if (exposed != null) {
            return exposed;
        }
        String type = normalize(dataset.getType());
        if (type != null) {
            return type;
        }
        String catalog = normalize(dataset.getTrinoCatalog());
        if (catalog != null) {
            return "trino";
        }
        return "default";
    }

    private String resolveCatalogLabel(CatalogDataset dataset) {
        if (dataset.getTrinoCatalog() != null && !dataset.getTrinoCatalog().isBlank()) {
            return dataset.getTrinoCatalog().trim();
        }
        return "default";
    }

    private String resolveSchemaLabel(CatalogDataset dataset) {
        if (dataset.getHiveDatabase() != null && !dataset.getHiveDatabase().isBlank()) {
            return dataset.getHiveDatabase().trim();
        }
        if (dataset.getName() != null && !dataset.getName().isBlank()) {
            return dataset.getName().trim();
        }
        return dataset.getId() != null ? dataset.getId().toString() : "schema";
    }

    private String formatColumnLabel(CatalogColumnSchema column) {
        String name = safeLabel(column.getName(), "column");
        String dataType = column.getDataType();
        if (dataType == null || dataType.isBlank()) {
            return name;
        }
        return name + " (" + dataType + ")";
    }

    private String nodeId(String type, Object... parts) {
        return type + ":" + Arrays.stream(parts).map(this::idPart).collect(Collectors.joining("/"));
    }

    private String idPart(Object part) {
        if (part == null) {
            return "null";
        }
        if (part instanceof UUID uuid) {
            return uuid.toString();
        }
        return String.valueOf(part);
    }

    private boolean matchesSearch(String label, String search) {
        return label != null && label.toLowerCase(Locale.ROOT).contains(search);
    }

    private String sortKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String safeLabel(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return fallback;
    }

    private String keyPart(String value) {
        return value == null ? "-" : value;
    }

    private String defaultDatasource(String datasource) {
        String normalized = normalize(datasource);
        return normalized != null ? normalized : "trino";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String principalName(Principal principal) {
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }
        return SecurityUtils.getCurrentUserLogin().orElse("anonymous");
    }
}
