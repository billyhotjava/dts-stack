package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.sql.dto.CatalogColumnDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogDatasourceDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSchemaDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSearchHitDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogTableDto;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlCatalogLazyService {

    /** Static registry; expanded later when real datasource management is wired. */
    private static final List<CatalogDatasourceDto> DATASOURCES = List.of(
        new CatalogDatasourceDto("default", "默认数据源", "trino", "Trino · default"),
        new CatalogDatasourceDto("hive", "Hive 仓库", "hive", "Apache Hive"),
        new CatalogDatasourceDto("postgres", "PostgreSQL", "postgresql", "PostgreSQL")
    );

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;

    public SqlCatalogLazyService(
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    public List<CatalogDatasourceDto> listDatasources() {
        return DATASOURCES;
    }

    @Cacheable(cacheNames = "sqlIdeSchemas", key = "#datasourceId")
    public List<CatalogSchemaDto> listSchemas(String datasourceId) {
        return datasetRepository
            .findAll()
            .stream()
            .map(CatalogDataset::getName)
            .filter(Objects::nonNull)
            .distinct()
            .map(name -> new CatalogSchemaDto(name, null))
            .toList();
    }

    @Cacheable(cacheNames = "sqlIdeTables", key = "#datasourceId + ':' + #schema")
    public List<CatalogTableDto> listTables(String datasourceId, String schema) {
        return datasetRepository
            .findAll()
            .stream()
            .filter(d -> schema == null || schema.equalsIgnoreCase(d.getName()))
            .flatMap(d -> tableRepository.findByDataset(d).stream())
            .map(t -> new CatalogTableDto(t.getName(), "TABLE", null, null))
            .toList();
    }

    @Cacheable(cacheNames = "sqlIdeColumns", key = "#datasourceId + ':' + #schemaTable")
    public List<CatalogColumnDto> listColumns(String datasourceId, String schemaTable) {
        String[] parts = schemaTable == null ? new String[0] : schemaTable.split("\\.", 2);
        String schema = parts.length == 2 ? parts[0] : null;
        String table = parts.length == 2 ? parts[1] : (parts.length == 1 ? parts[0] : null);
        if (table == null) return List.of();

        return datasetRepository
            .findAll()
            .stream()
            .filter(d -> schema == null || schema.equalsIgnoreCase(d.getName()))
            .flatMap(d -> tableRepository.findByDataset(d).stream())
            .filter(t -> table.equalsIgnoreCase(t.getName()))
            .findFirst()
            .map(this::columnsOf)
            .orElse(List.of());
    }

    public List<CatalogSearchHitDto> search(String datasourceId, String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String kw = keyword.toLowerCase();
        return tableRepository
            .findAll()
            .stream()
            .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(kw))
            .limit(Math.max(1, limit))
            .map(t -> new CatalogSearchHitDto(
                t.getDataset() != null ? t.getDataset().getName() : null,
                t.getName(),
                null,
                "TABLE"
            ))
            .toList();
    }

    /* ----- helpers ----- */

    private List<CatalogColumnDto> columnsOf(CatalogTableSchema t) {
        return columnRepository
            .findByTable(t)
            .stream()
            .map(c -> new CatalogColumnDto(
                c.getName(),
                c.getDataType(),
                c.getNullable() == null || c.getNullable(),
                c.getComment(),
                0
            ))
            .toList();
    }
}
