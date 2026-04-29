package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtSourceService.class);

    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final InfraOdsTableMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;

    public DbtSourceService(
        DbtProperties properties,
        DbtConfigService configService,
        InfraOdsTableMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository
    ) {
        this.properties = properties;
        this.configService = configService;
        this.mappingRepository = mappingRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    public DbtSourceRefreshResult refreshOdsSources() {
        if (!properties.isEnabled()) {
            return DbtSourceRefreshResult.disabled("dbt 配置未启用");
        }
        List<InfraOdsTableMapping> mappings = mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        Path projectDir = resolveProjectDir();
        if (!Files.exists(projectDir)) {
            return DbtSourceRefreshResult.empty("dbt 项目目录不存在");
        }
        Path modelsDir = projectDir.resolve("models");
        Path output = modelsDir.resolve("ods_sources.yml");
        SourceBuildResult build = buildSources(mappings);
        try {
            Files.createDirectories(modelsDir);
            String yaml = YamlWriter.toYaml(build.root());
            Files.writeString(output, yaml, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (build.tables() == 0) {
                return DbtSourceRefreshResult.empty("未发现 ODS 映射");
            }
            return DbtSourceRefreshResult.success(output.toString(), build.tables());
        } catch (IOException ex) {
            LOG.warn("Failed to write dbt sources: {}", ex.getMessage());
            return DbtSourceRefreshResult.empty("写入 sources.yml 失败: " + ex.getMessage());
        }
    }

    private SourceBuildResult buildSources(List<InfraOdsTableMapping> mappings) {
        Map<String, List<InfraOdsTableMapping>> grouped = new LinkedHashMap<>();
        for (InfraOdsTableMapping mapping : mappings) {
            String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema() : "ods";
            grouped.computeIfAbsent(schema, key -> new ArrayList<>()).add(mapping);
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        int emittedTables = 0;
        for (Map.Entry<String, List<InfraOdsTableMapping>> entry : grouped.entrySet()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("name", entry.getKey());
            source.put("schema", entry.getKey());
            Map<String, Map<String, Object>> dedupedTables = new LinkedHashMap<>();
            List<Map<String, Object>> tables = new ArrayList<>();
            for (InfraOdsTableMapping mapping : entry.getValue()) {
                String tableName = mapping.getOdsTable();
                if (!StringUtils.hasText(tableName) || dedupedTables.containsKey(tableName.toLowerCase())) {
                    continue;
                }
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("name", tableName);
                if (StringUtils.hasText(mapping.getDescription())) {
                    table.put("description", mapping.getDescription());
                }
                List<Map<String, Object>> columns = buildColumns(mapping);
                if (!columns.isEmpty()) {
                    table.put("columns", columns);
                }
                Map<String, Object> meta = new LinkedHashMap<>();
                putIfText(meta, "system", mapping.getSystemCode());
                putIfText(meta, "biz", mapping.getBizCode());
                putIfText(meta, "entity", mapping.getEntityCode());
                putIfText(meta, "stream", mapping.getStreamName());
                putIfText(meta, "stream_namespace", mapping.getStreamNamespace());
                if (!meta.isEmpty()) {
                    table.put("meta", meta);
                }
                dedupedTables.put(tableName.toLowerCase(), table);
            }
            tables.addAll(dedupedTables.values());
            emittedTables += tables.size();
            source.put("tables", tables);
            sources.add(source);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("sources", sources);
        return new SourceBuildResult(root, emittedTables);
    }

    private List<Map<String, Object>> buildColumns(InfraOdsTableMapping mapping) {
        CatalogTableSchema table = resolveCatalogTable(mapping).orElse(null);
        if (table == null) {
            return List.of();
        }
        List<CatalogColumnSchema> catalogColumns = columnRepository.findByTable(table);
        if (catalogColumns == null || catalogColumns.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        for (CatalogColumnSchema column : catalogColumns) {
            if (column == null || !StringUtils.hasText(column.getName())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", column.getName());
            putIfText(item, "data_type", column.getDataType());
            putIfText(item, "description", column.getComment());

            Map<String, Object> meta = new LinkedHashMap<>();
            if (isDtsTechnicalColumn(column.getName())) {
                meta.put("dts_technical", true);
            }
            if (column.getNullable() != null) {
                meta.put("nullable", column.getNullable());
            }
            putIfText(meta, "status", column.getStatus());
            putIfText(meta, "tags", column.getTags());
            putIfText(meta, "sensitive_tags", column.getSensitiveTags());
            if (!meta.isEmpty()) {
                item.put("meta", meta);
            }
            columns.add(item);
        }
        return columns;
    }

    private Optional<CatalogTableSchema> resolveCatalogTable(InfraOdsTableMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.getOdsTable())) {
            return Optional.empty();
        }
        Optional<CatalogDataset> dataset = resolveDataset(mapping);
        if (dataset.isEmpty()) {
            return Optional.empty();
        }
        Optional<CatalogTableSchema> table = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset.orElseThrow(), mapping.getOdsTable());
        return table == null ? Optional.empty() : table;
    }

    private Optional<CatalogDataset> resolveDataset(InfraOdsTableMapping mapping) {
        if (mapping == null) {
            return Optional.empty();
        }
        if (mapping.getDatasetId() != null) {
            Optional<CatalogDataset> byId = datasetRepository.findById(mapping.getDatasetId());
            if (byId != null && byId.isPresent()) {
                return byId;
            }
        }
        String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema() : "ods";
        String table = mapping.getOdsTable();
        if (!StringUtils.hasText(table)) {
            return Optional.empty();
        }
        Optional<CatalogDataset> byConnection = mapping.getConnectionId() == null
            ? Optional.empty()
            : datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(mapping.getConnectionId(), schema, table);
        if (byConnection != null && byConnection.isPresent()) {
            return byConnection;
        }
        Optional<CatalogDataset> byName = datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, table);
        return byName == null ? Optional.empty() : byName;
    }

    private boolean isDtsTechnicalColumn(String name) {
        return StringUtils.hasText(name) && name.trim().toLowerCase(Locale.ROOT).startsWith("_dts_");
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private Path resolveProjectDir() {
        String dir = properties.getProjectDir();
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            dir = view.config().projectDir();
        }
        if (!StringUtils.hasText(dir)) {
            dir = "/opt/dts/dbt";
        }
        return Path.of(dir);
    }

    public record DbtSourceRefreshResult(boolean enabled, int tables, String message, String path) {
        public static DbtSourceRefreshResult disabled(String message) {
            return new DbtSourceRefreshResult(false, 0, message, null);
        }

        public static DbtSourceRefreshResult empty(String message) {
            return new DbtSourceRefreshResult(true, 0, message, null);
        }

        public static DbtSourceRefreshResult success(String path, int tables) {
            return new DbtSourceRefreshResult(true, tables, "sources.yml 已更新", path);
        }
    }

    private record SourceBuildResult(Map<String, Object> root, int tables) {}

    private static final class YamlWriter {
        private static String toYaml(Map<String, Object> root) throws IOException {
            StringBuilder sb = new StringBuilder();
            writeMap(sb, root, 0);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void writeMap(StringBuilder sb, Map<String, Object> map, int indent) {
            String prefix = " ".repeat(Math.max(0, indent));
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append(prefix).append(entry.getKey()).append(":");
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    if (nested.isEmpty()) {
                        sb.append(" {}\n");
                    } else {
                        sb.append("\n");
                        writeMap(sb, (Map<String, Object>) nested, indent + 2);
                    }
                } else if (value instanceof List<?> list) {
                    if (list.isEmpty()) {
                        sb.append(" []\n");
                    } else {
                        sb.append("\n");
                        writeList(sb, list, indent + 2);
                    }
                } else {
                    sb.append(" ").append(scalar(value)).append("\n");
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static void writeList(StringBuilder sb, List<?> list, int indent) {
            String prefix = " ".repeat(Math.max(0, indent));
            for (Object item : list) {
                sb.append(prefix).append("-");
                if (item instanceof Map<?, ?> nested) {
                    sb.append("\n");
                    writeMap(sb, (Map<String, Object>) nested, indent + 2);
                } else if (item instanceof List<?> nestedList) {
                    sb.append("\n");
                    writeList(sb, nestedList, indent + 2);
                } else {
                    sb.append(" ").append(scalar(item)).append("\n");
                }
            }
        }

        private static String scalar(Object value) {
            if (value == null) {
                return "\"\"";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            String text = String.valueOf(value);
            if (text.isEmpty()) {
                return "\"\"";
            }
            String escaped = text.replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }
    }
}
