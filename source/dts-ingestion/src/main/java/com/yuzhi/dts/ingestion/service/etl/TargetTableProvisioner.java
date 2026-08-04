package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionSchemaSnapshot;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TargetTableProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(TargetTableProvisioner.class);
    private static final String TABLE_PLACEHOLDER = "${table}";

    private record ProvisioningPlan(
        TableMapping mapping,
        TableId target,
        List<JdbcMetadataService.ColumnMeta> sourceColumns,
        List<JdbcMetadataService.ColumnMeta> odsColumns,
        Map<String, String> columnComments
    ) {}

    private record FileLandingPolicy(
        String landingMode,
        TableId referenceTable,
        TableId targetTable,
        boolean recreateConfirmed
    ) {}

    private final JdbcMetadataService metadataService;
    private final ObjectMapper objectMapper;
    private final IngestionSchemaSnapshotService schemaSnapshotService;
    private final IngestionSourceResolver sourceResolver;

    public TargetTableProvisioner(
        JdbcMetadataService metadataService,
        ObjectMapper objectMapper,
        IngestionSchemaSnapshotService schemaSnapshotService,
        IngestionSourceResolver sourceResolver
    ) {
        this.metadataService = metadataService;
        this.objectMapper = objectMapper;
        this.schemaSnapshotService = schemaSnapshotService;
        this.sourceResolver = sourceResolver;
    }

    public void ensureTargetTables(IngestionTask task) {
        ensureTargetTables(task, null);
    }

    public void ensureTargetTables(IngestionTask task, Map<String, Object> readerConfigOverride) {
        ensureTargetTables(task, readerConfigOverride, null);
    }

    public void ensureTargetTables(IngestionTask task, Map<String, Object> readerConfigOverride, IngestionExecution execution) {
        if (task == null) {
            return;
        }
        Map<String, Object> readerConfig = readerConfigOverride != null
            ? mergeReaderConfig(readerConfigOverride, task.getSourceConfig())
            : jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null
            ? jsonNodeToMap(task.getDestinationConfig())
            : Map.of();
        boolean fullRefresh = isFullRefreshMode(task.getSyncMode());
        if (!fullRefresh && !shouldAutoCreate(writerConfig, task.getAddaxConfig())) {
            return;
        }
        List<TableMapping> mappings = resolveMappings(task.getTableMapping(), readerConfig, writerConfig);
        if (mappings.isEmpty()) {
            return;
        }
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = buildConnectionInfo(readerConfig);
        JdbcMetadataService.JdbcConnectionInfo targetInfo = resolveTargetConnectionInfo(writerConfig);
        if (!StringUtils.hasText(targetInfo.jdbcUrl())) {
            LOG.warn("Target jdbcUrl missing, skip auto-create tables for task={}", task.getId());
            return;
        }
        FileLandingPolicy fileLandingPolicy = resolveFileLandingPolicy(readerConfig, writerConfig, targetInfo.jdbcUrl());
        if (fileLandingPolicy != null && mappings.size() != 1) {
            throw new IllegalStateException("文件落地策略仅允许配置一个目标表");
        }
        try (Connection connection = metadataService.openConnection(targetInfo)) {
            List<ProvisioningPlan> plans = new ArrayList<>();
            for (TableMapping mapping : mappings) {
                if (!StringUtils.hasText(mapping.target())) {
                    continue;
                }
                TableId target = resolveTargetTable(mapping.target(), resolveSchema(writerConfig));
                target = lowercaseForPostgres(target, targetInfo.jdbcUrl());
                if (fileLandingPolicy != null) {
                    target = normalizeManagedTarget(target, targetInfo.jdbcUrl());
                    if (!target.equals(fileLandingPolicy.targetTable())) {
                        throw new IllegalStateException(
                            "文件落地目标与任务表映射不一致: " + target.qualifiedName()
                        );
                    }
                }
                List<JdbcMetadataService.ColumnMeta> sourceColumns = resolveColumns(sourceInfo, mapping.source(), readerConfig);
                if (sourceColumns.isEmpty()) {
                    // For file sources, _fileColumns may provide columns
                    LOG.warn("无法获取源表字段信息: {} — 尝试使用文件列元数据", mapping.source());
                    sourceColumns = resolveFileColumns(readerConfig);
                }
                if (sourceColumns.isEmpty()) {
                    throw new IllegalStateException("无法获取源表字段信息: " + mapping.source());
                }
                // Apply column prefix/suffix rules if configured
                List<JdbcMetadataService.ColumnMeta> columns = applyColumnPrefixSuffix(sourceColumns, writerConfig);
                if (isPostgres(targetInfo.jdbcUrl())) {
                    columns = lowercaseColumnNames(columns);
                }
                columns = DtsOdsTechnicalColumns.businessColumns(columns);
                Map<String, String> columnComments = resolveFileColumnComments(readerConfig, columns);
                columns = appendDtsTechnicalColumns(columns, task, readerConfig);
                plans.add(new ProvisioningPlan(mapping, target, sourceColumns, columns, columnComments));
            }

            boolean transactionalDdl = isPostgres(targetInfo.jdbcUrl());
            if (transactionalDdl) {
                connection.setAutoCommit(false);
            }
            try {
                for (ProvisioningPlan plan : plans) {
                    createSchemaIfNeeded(connection, plan.target().schema());
                    boolean exists = tableExists(connection, plan.target());
                    if (fileLandingPolicy == null && exists) {
                        ensureColumns(connection, plan.target(), plan.odsColumns());
                        LOG.info("Reconciled table {} for task {} without destructive DDL", plan.target().qualifiedName(), task.getId());
                    } else if (fileLandingPolicy == null) {
                        createTable(connection, plan.target(), plan.odsColumns());
                        LOG.info("Auto-created table {} for task {}", plan.target().qualifiedName(), task.getId());
                    } else if ("create_new".equals(fileLandingPolicy.landingMode())) {
                        if (exists) {
                            throw new IllegalStateException(
                                "目标表已存在，不能按新建表方式落地: " + displayName(plan.target())
                            );
                        }
                        createTable(connection, plan.target(), plan.odsColumns(), false);
                        applyColumnComments(connection, plan.target(), plan.columnComments(), targetInfo.jdbcUrl());
                        LOG.info("Created managed file landing table {} for task {}", plan.target().qualifiedName(), task.getId());
                    } else {
                        if (!exists) {
                            throw new IllegalStateException(
                                "待全量重建的原表不存在: " + displayName(plan.target())
                            );
                        }
                        dropTable(connection, plan.target());
                        createTable(connection, plan.target(), plan.odsColumns(), false);
                        applyColumnComments(connection, plan.target(), plan.columnComments(), targetInfo.jdbcUrl());
                        LOG.info("Recreated managed file landing table {} for task {}", plan.target().qualifiedName(), task.getId());
                    }
                }
                if (transactionalDdl) {
                    connection.commit();
                }
            } catch (Exception ddlFailure) {
                if (transactionalDdl) {
                    try {
                        connection.rollback();
                    } catch (Exception rollbackFailure) {
                        ddlFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw ddlFailure;
            }
            for (ProvisioningPlan plan : plans) {
                saveSchemaSnapshot(
                    task,
                    execution,
                    plan.mapping(),
                    plan.target(),
                    readerConfig,
                    sourceInfo,
                    plan.sourceColumns(),
                    plan.odsColumns()
                );
            }
        } catch (Exception ex) {
            throw new IllegalStateException("自动建表失败: " + ex.getMessage(), ex);
        }
    }

    private boolean isFullRefreshMode(String syncMode) {
        return "full_refresh".equalsIgnoreCase(normalizeText(syncMode));
    }

    private Map<String, Object> mergeReaderConfig(Map<String, Object> baseConfig, JsonNode overrideNode) {
        Map<String, Object> merged = baseConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(baseConfig);
        if (overrideNode == null || overrideNode.isNull()) {
            return merged;
        }
        Map<String, Object> overrides = jsonNodeToMap(overrideNode);
        if (overrides.isEmpty()) {
            return merged;
        }
        List<String> tables = extractTables(overrides);
        overrides.remove("table");
        overrides.remove("tables");
        overrides.remove("connection");
        overrides.remove("jdbcUrl");
        overrides.remove("url");
        overrides.remove("host");
        overrides.remove("port");
        overrides.remove("username");
        overrides.remove("password");
        overrides.remove("database");
        overrides.remove("db");
        overrides.remove("driver");
        overrides.remove("driverClass");
        overrides.remove("driverVersion");
        overrides.remove("jdbcProperties");
        merged.putAll(overrides);
        if (!tables.isEmpty()) {
            applyTables(merged, tables);
        }
        return merged;
    }

    private void applyTables(Map<String, Object> config, List<String> tables) {
        if (config == null || tables == null || tables.isEmpty()) {
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTableField(map, tables);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTableField(entryMap, tables);
                }
            }
            return;
        }
        config.put("table", tables);
    }

    private void setTableField(Map<?, ?> map, List<String> tables) {
        if (map == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutable = (Map<Object, Object>) map;
        mutable.put("table", tables);
    }

    private boolean shouldAutoCreate(Map<String, Object> writerConfig, JsonNode jobConfig) {
        Boolean value = booleanValue(writerConfig.get("autoCreateTables"));
        if (value == null) {
            value = booleanValue(writerConfig.get("autoCreate"));
        }
        if (value == null && jobConfig != null && !jobConfig.isNull()) {
            value = booleanValue(jobConfig.get("autoCreateTables"));
        }
        return value == null ? Boolean.TRUE : value;
    }

    private JdbcMetadataService.JdbcConnectionInfo buildConnectionInfo(Map<String, Object> config) {
        String jdbcUrl = appendPostgresSslDisable(resolveJdbcUrl(config));
        String username = normalizeText(config.get("username"));
        String password = normalizeText(config.get("password"));
        String driverClass = normalizeText(config.get("driver"));
        if (!StringUtils.hasText(driverClass)) {
            driverClass = normalizeText(config.get("driverClass"));
        }
        String driverVersion = normalizeText(config.get("driverVersion"));
        Map<String, String> jdbcProps = resolveJdbcProperties(config);
        return new JdbcMetadataService.JdbcConnectionInfo(jdbcUrl, username, password, driverClass, driverVersion, jdbcProps);
    }

    private JdbcMetadataService.JdbcConnectionInfo resolveTargetConnectionInfo(Map<String, Object> config) {
        String rawId = null;
        for (String key : List.of("targetDataSourceId", "destinationDataSourceId", "dataSourceId")) {
            String candidate = normalizeText(config.get(key));
            if (StringUtils.hasText(candidate)) {
                rawId = candidate;
                break;
            }
        }
        if (!StringUtils.hasText(rawId)) {
            return buildConnectionInfo(config);
        }
        UUID dataSourceId;
        try {
            dataSourceId = UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid managed destination data source id", ex);
        }
        JdbcMetadataService.JdbcConnectionInfo resolved = sourceResolver.resolveJdbcInfo(dataSourceId);
        return new JdbcMetadataService.JdbcConnectionInfo(
            appendPostgresSslDisable(resolved.jdbcUrl()),
            resolved.username(),
            resolved.password(),
            resolved.driverClass(),
            resolved.driverVersion(),
            resolved.jdbcProperties()
        );
    }

    private Map<String, String> resolveJdbcProperties(Map<String, Object> config) {
        Object props = config.get("jdbcProperties");
        if (props instanceof Map<?, ?> map) {
            Map<String, String> resolved = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    resolved.put(k.toString(), v.toString());
                }
            });
            return resolved;
        }
        return Map.of();
    }

    List<JdbcMetadataService.ColumnMeta> resolveColumns(
        JdbcMetadataService.JdbcConnectionInfo sourceInfo,
        String sourceTable,
        Map<String, Object> readerConfig
    ) {
        List<String> configured = extractColumns(readerConfig);
        if (!configured.isEmpty()) {
            List<JdbcMetadataService.ColumnMeta> discovered = StringUtils.hasText(sourceInfo.jdbcUrl())
                ? metadataService.getTableColumns(sourceInfo, sourceTable)
                : List.of();
            if (!discovered.isEmpty()) {
                Map<String, JdbcMetadataService.ColumnMeta> byName = new LinkedHashMap<>();
                for (JdbcMetadataService.ColumnMeta col : discovered) {
                    String name = normalizeText(col.name());
                    if (StringUtils.hasText(name)) {
                        byName.put(name.toLowerCase(Locale.ROOT), col);
                    }
                }
                List<JdbcMetadataService.ColumnMeta> typed = new ArrayList<>();
                for (String name : configured) {
                    String key = normalizeText(name);
                    JdbcMetadataService.ColumnMeta col = StringUtils.hasText(key)
                        ? byName.get(key.toLowerCase(Locale.ROOT))
                        : null;
                    if (col != null) {
                        typed.add(col);
                    }
                }
                if (!typed.isEmpty()) {
                    return typed;
                }
            }
            List<JdbcMetadataService.ColumnMeta> cols = new ArrayList<>();
            for (String name : configured) {
                if (StringUtils.hasText(name)) {
                    cols.add(new JdbcMetadataService.ColumnMeta(name, Types.VARCHAR, "TEXT", null, null));
                }
            }
            return cols;
        }
        // Support file upload columns metadata
        List<JdbcMetadataService.ColumnMeta> fileCols = resolveFileColumns(readerConfig);
        if (!fileCols.isEmpty()) {
            return fileCols;
        }
        if (!StringUtils.hasText(sourceInfo.jdbcUrl())) {
            return List.of();
        }
        return metadataService.getTableColumns(sourceInfo, sourceTable);
    }

    private IngestionSchemaSnapshot saveSchemaSnapshot(
        IngestionTask task,
        IngestionExecution execution,
        TableMapping mapping,
        TableId target,
        Map<String, Object> readerConfig,
        JdbcMetadataService.JdbcConnectionInfo sourceInfo,
        List<JdbcMetadataService.ColumnMeta> sourceColumns,
        List<JdbcMetadataService.ColumnMeta> odsColumns
    ) {
        TableId source = TableId.parse(mapping.source());
        String sourceSchema = StringUtils.hasText(source.schema()) ? source.schema() : resolveSchema(readerConfig);
        String sourceTable = StringUtils.hasText(source.table()) ? source.table() : mapping.source();
        List<String> warnings = new ArrayList<>();
        List<String> primaryKeyColumns = List.of();
        List<JdbcMetadataService.IndexMeta> indexes = List.of();
        if (StringUtils.hasText(sourceInfo.jdbcUrl()) && StringUtils.hasText(mapping.source())) {
            primaryKeyColumns = metadataService.getPrimaryKeyColumns(sourceInfo, mapping.source());
            indexes = metadataService.getTableIndexes(sourceInfo, mapping.source());
        } else {
            warnings.add("source_jdbc_metadata_unavailable");
        }
        return schemaSnapshotService.saveSnapshot(
            task,
            execution,
            task == null ? null : task.getSourceType(),
            resolveSourceSystem(readerConfig),
            sourceSchema,
            sourceTable,
            resolveSourceResource(readerConfig, mapping.source()),
            target.schema(),
            target.table(),
            sourceColumns,
            odsColumns,
            primaryKeyColumns,
            indexes,
            warnings
        );
    }

    @SuppressWarnings("unchecked")
    private List<JdbcMetadataService.ColumnMeta> resolveFileColumns(Map<String, Object> readerConfig) {
        if (readerConfig == null || !readerConfig.containsKey("_fileColumns")) {
            return List.of();
        }
        Object fileColumnsObj = readerConfig.get("_fileColumns");
        if (!(fileColumnsObj instanceof List<?> fileColumnsList) || fileColumnsList.isEmpty()) {
            return List.of();
        }
        List<JdbcMetadataService.ColumnMeta> cols = new ArrayList<>();
        for (Object item : fileColumnsList) {
            if (item instanceof Map<?, ?> colMap) {
                String name = normalizeText(colMap.get("safeName"));
                if (!StringUtils.hasText(name)) {
                    name = normalizeText(colMap.get("name"));
                }
                if (!StringUtils.hasText(name)) {
                    name = normalizeText(colMap.get("label"));
                }
                String type = normalizeText(colMap.get("type"));
                if (!StringUtils.hasText(name)) continue;
                int jdbcType = mapFileTypeToJdbc(type);
                String typeName = mapFileTypeToSql(type, colMap);
                Integer size = null;
                Integer scale = null;
                if (isStringFileType(type)) {
                    size = toInt(colMap.get("length"), 500);
                } else if (isNumericFileType(type)) {
                    size = toInt(colMap.get("precision"), 18);
                    scale = toInt(colMap.get("scale"), 2);
                }
                cols.add(new JdbcMetadataService.ColumnMeta(name, jdbcType, typeName, size, scale));
            }
        }
        return cols;
    }

    private Map<String, String> resolveFileColumnComments(
        Map<String, Object> readerConfig,
        List<JdbcMetadataService.ColumnMeta> businessColumns
    ) {
        if (readerConfig == null || businessColumns == null || businessColumns.isEmpty()) {
            return Map.of();
        }
        Object fileColumnsObj = readerConfig.get("_fileColumns");
        if (!(fileColumnsObj instanceof List<?> fileColumns) || fileColumns.isEmpty()) {
            return Map.of();
        }
        Map<String, String> comments = new LinkedHashMap<>();
        int size = Math.min(fileColumns.size(), businessColumns.size());
        for (int index = 0; index < size; index++) {
            Object item = fileColumns.get(index);
            JdbcMetadataService.ColumnMeta targetColumn = businessColumns.get(index);
            if (!(item instanceof Map<?, ?> column) || targetColumn == null || !StringUtils.hasText(targetColumn.name())) {
                continue;
            }
            String description = normalizeText(column.get("description"));
            if (StringUtils.hasText(description)) {
                comments.put(targetColumn.name(), description);
            }
        }
        return comments;
    }

    private FileLandingPolicy resolveFileLandingPolicy(
        Map<String, Object> readerConfig,
        Map<String, Object> writerConfig,
        String targetJdbcUrl
    ) {
        if (readerConfig == null) {
            return null;
        }
        Object contract = readerConfig.get("_fileLanding");
        if (!(contract instanceof Map<?, ?> landing)) {
            return null;
        }
        String landingMode = normalizeText(landing.get("landingMode"));
        if (!"create_new".equals(landingMode) && !"recreate_existing".equals(landingMode)) {
            throw new IllegalStateException("文件落地方式无效");
        }
        TableId target = parseManagedTarget(landing.get("targetTable"), writerConfig, targetJdbcUrl, "目标表");
        String referenceRaw = normalizeText(landing.get("referenceTable"));
        TableId reference = StringUtils.hasText(referenceRaw)
            ? parseManagedTarget(referenceRaw, writerConfig, targetJdbcUrl, "参考表")
            : null;
        boolean recreateConfirmed = Boolean.TRUE.equals(booleanValue(landing.get("recreateConfirmed")));
        if ("recreate_existing".equals(landingMode)) {
            if (reference == null || !reference.equals(target)) {
                throw new IllegalStateException("全量重建必须使用已选择的原表名");
            }
            if (!recreateConfirmed) {
                throw new IllegalStateException("未确认全量重建原表");
            }
        }
        validateReferenceDataSource(landing, writerConfig);
        return new FileLandingPolicy(landingMode, reference, target, recreateConfirmed);
    }

    private TableId parseManagedTarget(
        Object raw,
        Map<String, Object> writerConfig,
        String targetJdbcUrl,
        String label
    ) {
        String text = normalizeText(raw);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException(label + "不能为空");
        }
        String[] parts = text.split("\\.", -1);
        if (parts.length > 2) {
            throw new IllegalStateException(label + "格式不合法: " + text);
        }
        for (String part : parts) {
            if (!part.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalStateException(label + "格式不合法: " + text);
            }
        }
        TableId parsed = resolveTargetTable(text, resolveSchema(writerConfig));
        return normalizeManagedTarget(lowercaseForPostgres(parsed, targetJdbcUrl), targetJdbcUrl);
    }

    private TableId normalizeManagedTarget(TableId target, String targetJdbcUrl) {
        if (target != null && isPostgres(targetJdbcUrl) && !StringUtils.hasText(target.schema())) {
            return new TableId("public", target.table());
        }
        return target;
    }

    private void validateReferenceDataSource(Map<?, ?> landing, Map<String, Object> writerConfig) {
        String referenceId = normalizeText(landing.get("referenceDataSourceId"));
        if (!StringUtils.hasText(referenceId) || writerConfig == null) {
            return;
        }
        String targetId = null;
        for (String key : List.of("targetDataSourceId", "destinationDataSourceId", "dataSourceId")) {
            targetId = normalizeText(writerConfig.get(key));
            if (StringUtils.hasText(targetId)) {
                break;
            }
        }
        if (StringUtils.hasText(targetId) && !referenceId.equals(targetId)) {
            throw new IllegalStateException("参考表与目标数据源不一致");
        }
    }

    private int mapFileTypeToJdbc(String fileType) {
        if (!StringUtils.hasText(fileType)) return Types.VARCHAR;
        String normalized = normalizeFileType(fileType);
        if (isNumericFileType(normalized)) return Types.NUMERIC;
        return switch (normalized) {
            case "long", "bigint", "int8", "bigserial", "serial8" -> Types.BIGINT;
            case "integer", "int", "int4", "serial", "serial4" -> Types.INTEGER;
            case "smallint", "int2", "smallserial", "serial2" -> Types.SMALLINT;
            case "double", "double precision", "float8" -> Types.DOUBLE;
            case "real", "float4" -> Types.REAL;
            case "date" -> Types.DATE;
            case "timestamp", "timestamp without time zone" -> Types.TIMESTAMP;
            case "timestamptz", "timestamp with time zone" -> Types.TIMESTAMP_WITH_TIMEZONE;
            case "boolean", "bool" -> Types.BOOLEAN;
            case "text" -> Types.LONGVARCHAR;
            case "json", "jsonb", "uuid" -> Types.OTHER;
            default -> Types.VARCHAR;
        };
    }

    private String mapFileTypeToSql(String fileType, Map<?, ?> colMap) {
        if (!StringUtils.hasText(fileType)) return "TEXT";
        String normalized = normalizeFileType(fileType);
        if (isNumericFileType(normalized)) {
                int precision = toInt(colMap.get("precision"), 18);
                int scale = toInt(colMap.get("scale"), 2);
                return "NUMERIC(" + precision + "," + scale + ")";
        }
        return switch (normalized) {
            case "long", "bigint", "int8", "bigserial", "serial8" -> "BIGINT";
            case "integer", "int", "int4", "serial", "serial4" -> "INTEGER";
            case "smallint", "int2", "smallserial", "serial2" -> "SMALLINT";
            case "double", "double precision", "float8" -> "DOUBLE PRECISION";
            case "real", "float4" -> "REAL";
            case "date" -> "DATE";
            case "timestamp", "timestamp without time zone" -> "TIMESTAMP";
            case "timestamptz", "timestamp with time zone" -> "TIMESTAMP WITH TIME ZONE";
            case "boolean", "bool" -> "BOOLEAN";
            case "text" -> "TEXT";
            case "json" -> "JSON";
            case "jsonb" -> "JSONB";
            case "uuid" -> "UUID";
            default -> {
                int length = toInt(colMap.get("length"), 500);
                yield "VARCHAR(" + length + ")";
            }
        };
    }

    private String normalizeFileType(String fileType) {
        return fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean isNumericFileType(String fileType) {
        String normalized = normalizeFileType(fileType);
        return normalized.startsWith("numeric") || normalized.startsWith("decimal");
    }

    private boolean isStringFileType(String fileType) {
        String normalized = normalizeFileType(fileType);
        return normalized.equals("string")
            || normalized.equals("text")
            || normalized.contains("char");
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        try { return Integer.parseInt(value.toString().trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private List<TableMapping> resolveMappings(JsonNode mappingNode, Map<String, Object> readerConfig, Map<String, Object> writerConfig) {
        List<TableMapping> mappings = parseTableMapping(mappingNode);
        List<String> sources = extractTables(readerConfig);
        List<String> targets = extractTables(writerConfig);
        if (!mappings.isEmpty()) {
            List<String> mappingSources = mappings.stream().map(TableMapping::source).toList();
            if (mappingSources.stream().anyMatch(source -> !StringUtils.hasText(source))) {
                throw mappingValidationFailure("表映射存在空来源表");
            }
            boolean anyMappingTarget = mappings.stream().anyMatch(mapping -> StringUtils.hasText(mapping.target()));
            boolean allMappingTargets = mappings.stream().allMatch(mapping -> StringUtils.hasText(mapping.target()));
            if (anyMappingTarget && !allMappingTargets) {
                throw mappingValidationFailure("表映射目标表只能全部填写或全部由平台生成");
            }
            List<String> mappingTargets = allMappingTargets
                ? mappings.stream().map(TableMapping::target).toList()
                : List.of();
            String prefix = resolveTablePrefix(writerConfig);
            if (!StringUtils.hasText(prefix)) {
                prefix = inferTablePrefixFromTargets(mappingSources, targets);
            }
            if (mappingTargets.isEmpty()) {
                if (!targets.isEmpty()) {
                    requireSameTableCount(mappingSources, targets);
                    mappingTargets = targets;
                } else {
                    final String resolvedPrefix = prefix;
                    mappingTargets = mappingSources.stream()
                        .map(source -> buildTargetTableName(source, resolvedPrefix))
                        .toList();
                }
            } else if (isSourceAlignedTables(mappingSources, mappingTargets)) {
                if (!targets.isEmpty() && !isSourceAlignedTables(mappingSources, targets)) {
                    requireSameTableCount(mappingSources, targets);
                    mappingTargets = targets;
                } else if (StringUtils.hasText(prefix)) {
                    final String resolvedPrefix = prefix;
                    mappingTargets = mappingSources.stream()
                        .map(source -> buildTargetTableName(source, resolvedPrefix))
                        .toList();
                }
            }
            requireSameTableCount(mappingSources, mappingTargets);
            List<TableMapping> normalized = new ArrayList<>(mappingSources.size());
            for (int i = 0; i < mappingSources.size(); i++) {
                normalized.add(new TableMapping(mappingSources.get(i), mappingTargets.get(i)));
            }
            return normalized;
        }
        if (sources.isEmpty()) {
            return resolveFileLandingMapping(readerConfig);
        }
        String prefix = resolveTablePrefix(writerConfig);
        if (!StringUtils.hasText(prefix)) {
            prefix = inferTablePrefixFromTargets(sources, targets);
        }
        if (targets.isEmpty()) {
            final String resolvedPrefix = prefix;
            targets = sources.stream()
                .map(source -> buildTargetTableName(source, resolvedPrefix))
                .toList();
        } else if (targets.size() == 1 && targets.get(0).contains(TABLE_PLACEHOLDER)) {
            String template = targets.get(0);
            final String resolvedPrefix = prefix;
            targets = sources.stream()
                .map(src -> template.replace(TABLE_PLACEHOLDER, buildTargetTableName(src, resolvedPrefix)))
                .toList();
        } else if (isSourceAlignedTables(sources, targets) && StringUtils.hasText(prefix)) {
            final String resolvedPrefix = prefix;
            targets = sources.stream()
                .map(source -> buildTargetTableName(source, resolvedPrefix))
                .toList();
        }
        requireSameTableCount(sources, targets);
        List<TableMapping> resolved = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            resolved.add(new TableMapping(sources.get(i), targets.get(i)));
        }
        return resolved;
    }

    private List<TableMapping> resolveFileLandingMapping(Map<String, Object> readerConfig) {
        if (readerConfig == null) {
            return List.of();
        }
        Object contract = readerConfig.get("_fileLanding");
        if (!(contract instanceof Map<?, ?> landing)) {
            return List.of();
        }
        String source = normalizeText(readerConfig.get("_originalName"));
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("_fileId"));
        }
        if (!StringUtils.hasText(source)) {
            source = "uploaded_file";
        }
        return List.of(new TableMapping(source, normalizeText(landing.get("targetTable"))));
    }

    private void requireSameTableCount(List<String> sources, List<String> targets) {
        if (sources.size() != targets.size()) {
            throw mappingValidationFailure(
                "来源表与目标表数量不一致: source=" + sources.size() + ", target=" + targets.size()
            );
        }
    }

    private IllegalStateException mappingValidationFailure(String detail) {
        return new IllegalStateException("create table mapping validation failed: " + detail);
    }

    private List<TableMapping> parseTableMapping(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            List<TableMapping> mappings = objectMapper.convertValue(
                node,
                new com.fasterxml.jackson.core.type.TypeReference<List<TableMapping>>() {}
            );
            if (mappings == null) {
                return List.of();
            }
            return mappings.stream()
                .filter(mapping -> StringUtils.hasText(mapping.source()) || StringUtils.hasText(mapping.target()))
                .toList();
        } catch (Exception ex) {
            LOG.debug("Failed to parse table mapping: {}", ex.getMessage());
            return List.of();
        }
    }

    private TableId resolveTargetTable(String raw, String schemaFallback) {
        TableId parsed = TableId.parse(raw);
        if (StringUtils.hasText(parsed.schema())) {
            return parsed;
        }
        if (StringUtils.hasText(schemaFallback)) {
            return new TableId(schemaFallback, parsed.table());
        }
        return parsed;
    }

    private TableId lowercaseForPostgres(TableId tableId, String jdbcUrl) {
        if (tableId == null || !isPostgres(jdbcUrl)) {
            return tableId;
        }
        String schema = tableId.schema() != null ? tableId.schema().toLowerCase(Locale.ROOT) : null;
        String table = tableId.table() != null ? tableId.table().toLowerCase(Locale.ROOT) : tableId.table();
        return new TableId(schema, table);
    }

    private boolean isPostgres(String jdbcUrl) {
        return StringUtils.hasText(jdbcUrl) && jdbcUrl.toLowerCase(Locale.ROOT).contains("postgresql");
    }

    private List<JdbcMetadataService.ColumnMeta> lowercaseColumnNames(List<JdbcMetadataService.ColumnMeta> columns) {
        if (columns == null || columns.isEmpty()) {
            return columns;
        }
        return columns.stream()
            .map(col -> col.withName(col.name() != null ? col.name().toLowerCase(Locale.ROOT) : col.name()))
            .toList();
    }

    private List<JdbcMetadataService.ColumnMeta> appendDtsTechnicalColumns(
        List<JdbcMetadataService.ColumnMeta> columns,
        IngestionTask task,
        Map<String, Object> readerConfig
    ) {
        List<JdbcMetadataService.ColumnMeta> technicalColumns = new ArrayList<>(DtsOdsTechnicalColumns.commonJdbcColumns());
        if (isFileSource(task, readerConfig)) {
            technicalColumns.addAll(DtsOdsTechnicalColumns.fileJdbcColumns());
        }
        if (columns == null || columns.isEmpty()) {
            return technicalColumns;
        }
        java.util.Set<String> names = columns.stream()
            .map(JdbcMetadataService.ColumnMeta::name)
            .filter(StringUtils::hasText)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (JdbcMetadataService.ColumnMeta technical : technicalColumns) {
            String name = technical.name();
            if (names.contains(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("源字段与 DTS 技术字段冲突: " + name);
            }
        }
        List<JdbcMetadataService.ColumnMeta> merged = new ArrayList<>(columns);
        merged.addAll(technicalColumns);
        return merged;
    }

    private boolean isFileSource(IngestionTask task, Map<String, Object> readerConfig) {
        String sourceType = task == null ? null : normalizeText(task.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            String lower = sourceType.toLowerCase(Locale.ROOT);
            if ("excel".equals(lower) || "csv".equals(lower) || "excelreader".equals(lower) || "txtfilereader".equals(lower) || "file".equals(lower)) {
                return true;
            }
        }
        if (readerConfig == null || readerConfig.isEmpty()) {
            return false;
        }
        return readerConfig.containsKey("_fileColumns")
            || readerConfig.containsKey("_filePath")
            || readerConfig.containsKey("_containerPath")
            || readerConfig.containsKey("_fileType");
    }

    private List<JdbcMetadataService.ColumnMeta> applyColumnPrefixSuffix(
        List<JdbcMetadataService.ColumnMeta> columns,
        Map<String, Object> writerConfig
    ) {
        if (columns == null || columns.isEmpty() || writerConfig == null) {
            return columns;
        }
        String prefix = normalizeText(writerConfig.get("_columnPrefix"));
        String suffix = normalizeText(writerConfig.get("_columnSuffix"));
        if (!StringUtils.hasText(prefix) && !StringUtils.hasText(suffix)) {
            return columns;
        }
        String safePrefix = StringUtils.hasText(prefix) ? prefix : "";
        String safeSuffix = StringUtils.hasText(suffix) ? suffix : "";
        return columns.stream()
            .map(col -> col.withName(safePrefix + col.name() + safeSuffix))
            .toList();
    }

    private List<String> extractColumns(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> values = extractValues(config.get("column"));
        if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
            return values;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            values = extractValues(map.get("column"));
            if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
                return values;
            }
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    values = extractValues(entryMap.get("column"));
                    if (!values.isEmpty() && values.stream().noneMatch("*"::equals)) {
                        return values;
                    }
                }
            }
        }
        return List.of();
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> direct = extractValues(config.get("table"));
        if (!direct.isEmpty()) {
            return direct;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            List<String> values = extractValues(map.get("table"));
            if (!values.isEmpty()) {
                return values;
            }
        } else if (connection instanceof List<?> list) {
            List<String> collected = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    collected.addAll(extractValues(entryMap.get("table")));
                }
            }
            if (!collected.isEmpty()) {
                return collected;
            }
        }
        return List.of();
    }

    private List<String> extractValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String str) {
            String normalized = normalizeText(str);
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> collected = new ArrayList<>();
            for (Object entry : iterable) {
                String normalized = normalizeText(entry);
                if (StringUtils.hasText(normalized)) {
                    collected.add(normalized);
                }
            }
            return collected;
        }
        return List.of();
    }

    private String resolveJdbcUrl(Map<String, Object> config) {
        Object direct = config.get("jdbcUrl");
        String url = firstStringValue(direct);
        if (StringUtils.hasText(url)) {
            return url;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return firstStringValue(map.get("jdbcUrl"));
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    String candidate = firstStringValue(entryMap.get("jdbcUrl"));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private String appendPostgresSslDisable(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return url;
        }
        if (url.contains("sslmode=") || url.contains("ssl=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=disable";
    }

    private String firstStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return normalizeText(str);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                String candidate = firstStringValue(entry);
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("name", "filename", "fileName", "path", "value")) {
                if (!map.containsKey(key)) {
                    continue;
                }
                String candidate = firstStringValue(map.get(key));
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        return normalizeText(value);
    }

    private String resolveSchema(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String schema = normalizeText(config.get("schema"));
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        Object schemas = config.get("schemas");
        if (schemas instanceof List<?> list && !list.isEmpty()) {
            String candidate = normalizeText(list.get(0));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return normalizeText(map.get("schema"));
        }
        return null;
    }

    private String resolveTablePrefix(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String prefix = normalizeText(config.get("tablePrefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        prefix = normalizeText(config.get("prefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        return normalizeText(config.get("targetPrefix"));
    }

    private boolean isSourceAlignedTables(List<String> sourceTables, List<String> targetTables) {
        if (sourceTables == null || targetTables == null || sourceTables.isEmpty() || targetTables.isEmpty()) {
            return false;
        }
        if (sourceTables.size() != targetTables.size()) {
            return false;
        }
        for (int i = 0; i < sourceTables.size(); i++) {
            String source = normalizeText(sourceTables.get(i));
            String target = normalizeText(targetTables.get(i));
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                return false;
            }
            if (source.equalsIgnoreCase(target)) {
                continue;
            }
            if (!stripSchema(source).equalsIgnoreCase(stripSchema(target))) {
                return false;
            }
        }
        return true;
    }

    private String buildTargetTableName(String sourceTable, String prefix) {
        String base = stripSchema(sourceTable);
        if (!StringUtils.hasText(base)) {
            return base;
        }
        return StringUtils.hasText(prefix) ? prefix + base : base;
    }

    private String inferTablePrefixFromTargets(List<String> sourceTables, List<String> targetTables) {
        if (sourceTables == null || targetTables == null || sourceTables.isEmpty() || targetTables.isEmpty()) {
            return null;
        }
        int size = Math.min(sourceTables.size(), targetTables.size());
        String inferred = null;
        for (int i = 0; i < size; i++) {
            String sourceBase = stripSchema(sourceTables.get(i));
            String targetBase = stripSchema(targetTables.get(i));
            if (!StringUtils.hasText(sourceBase) || !StringUtils.hasText(targetBase)) {
                continue;
            }
            String sourceLower = sourceBase.toLowerCase(Locale.ROOT);
            String targetLower = targetBase.toLowerCase(Locale.ROOT);
            if (!targetLower.endsWith(sourceLower)) {
                continue;
            }
            String candidate = targetBase.substring(0, targetBase.length() - sourceBase.length());
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (inferred == null) {
                inferred = candidate;
            } else if (!inferred.equals(candidate)) {
                return null;
            }
        }
        return inferred;
    }

    private String stripSchema(String table) {
        String normalized = normalizeText(table);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        int idx = normalized.lastIndexOf('.');
        if (idx > -1 && idx < normalized.length() - 1) {
            return normalized.substring(idx + 1);
        }
        return normalized;
    }

    private boolean hasUppercaseColumns(Connection connection, TableId tableId) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, tableId.schema(), tableId.table(), null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if (colName != null && !colName.equals(colName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void dropTable(Connection connection, TableId tableId) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + tableId.qualifiedName());
        }
    }

    private void createSchemaIfNeeded(Connection connection, String schema) throws Exception {
        if (!StringUtils.hasText(schema) || "public".equalsIgnoreCase(schema)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + quoteIdentifier(schema));
        }
    }

    private boolean tableExists(Connection connection, TableId tableId) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, tableId.schema(), tableId.table(), new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private void createTable(Connection connection, TableId tableId, List<JdbcMetadataService.ColumnMeta> columns) throws Exception {
        createTable(connection, tableId, columns, true);
    }

    private void createTable(
        Connection connection,
        TableId tableId,
        List<JdbcMetadataService.ColumnMeta> columns,
        boolean ifNotExists
    ) throws Exception {
        StringBuilder ddl = new StringBuilder();
        ddl.append("create table ");
        if (ifNotExists) {
            ddl.append("if not exists ");
        }
        ddl.append(tableId.qualifiedName()).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            JdbcMetadataService.ColumnMeta col = columns.get(i);
            ddl.append(quoteIdentifier(col.name())).append(" ").append(mapType(col));
            if (i < columns.size() - 1) {
                ddl.append(", ");
            }
        }
        ddl.append(")");
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl.toString());
        }
    }

    private void applyColumnComments(
        Connection connection,
        TableId tableId,
        Map<String, String> comments,
        String targetJdbcUrl
    ) throws Exception {
        if (!isPostgres(targetJdbcUrl) || comments == null || comments.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "COMMENT ON COLUMN " + tableId.qualifiedName() + "." + quoteIdentifier(entry.getKey())
                        + " IS '" + entry.getValue().replace("'", "''").replace(String.valueOf((char) 0), "") + "'"
                );
            }
        }
    }

    private String displayName(TableId tableId) {
        if (tableId == null) {
            return "";
        }
        return StringUtils.hasText(tableId.schema())
            ? tableId.schema() + "." + tableId.table()
            : tableId.table();
    }

    private void ensureColumns(
        Connection connection,
        TableId tableId,
        List<JdbcMetadataService.ColumnMeta> expectedColumns
    ) throws Exception {
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, tableId.schema(), tableId.table(), null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (StringUtils.hasText(name)) {
                    existing.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (JdbcMetadataService.ColumnMeta column : expectedColumns) {
            String name = column == null ? null : column.name();
            if (!StringUtils.hasText(name) || existing.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "ALTER TABLE " + tableId.qualifiedName()
                        + " ADD COLUMN " + quoteIdentifier(name) + " " + mapType(column)
                );
            }
            existing.add(name.toLowerCase(Locale.ROOT));
        }
    }

    private String mapType(JdbcMetadataService.ColumnMeta column) {
        if (column == null) {
            return "text";
        }
        int jdbcType = column.jdbcType();
        String typeName = normalizeText(column.typeName());
        Integer size = column.columnSize();
        Integer scale = column.decimalDigits();
        return switch (jdbcType) {
            case Types.BOOLEAN, Types.BIT -> "boolean";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> "integer";
            case Types.BIGINT -> "bigint";
            case Types.FLOAT, Types.REAL -> "real";
            case Types.DOUBLE -> "double precision";
            case Types.NUMERIC, Types.DECIMAL -> {
                if (size != null && size > 0) {
                    int scaleVal = scale == null ? 0 : Math.max(0, scale);
                    yield "decimal(" + size + "," + scaleVal + ")";
                }
                yield "decimal";
            }
            case Types.DATE -> "date";
            case Types.TIME -> "time";
            case Types.TIMESTAMP -> "timestamp";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp with time zone";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "bytea";
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR -> {
                if (size != null && size > 0 && size <= 65535) {
                    yield "varchar(" + size + ")";
                }
                yield "text";
            }
            default -> {
                if (typeName != null && typeName.toLowerCase(Locale.ROOT).contains("clob")) {
                    yield "text";
                }
                if (typeName != null && List.of("uuid", "json", "jsonb").contains(typeName.toLowerCase(Locale.ROOT))) {
                    yield typeName.toLowerCase(Locale.ROOT);
                }
                yield "text";
            }
        };
    }

    private String quoteIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }

    private String resolveSourceSystem(Map<String, Object> readerConfig) {
        if (readerConfig == null || readerConfig.isEmpty()) {
            return "unknown";
        }
        for (String key : List.of("sourceSystem", "sourceApp", "appCode", "system", "name", "_originalName")) {
            String value = firstStringValue(readerConfig.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "unknown";
    }

    private String resolveSourceResource(Map<String, Object> readerConfig, String fallback) {
        if (readerConfig != null && !readerConfig.isEmpty()) {
            for (String key : List.of("_originalName", "_filePath", "_containerPath", "path")) {
                String value = firstStringValue(readerConfig.get(key));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return fallback;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            LOG.debug("Failed to convert JsonNode to map: {}", ex.getMessage());
            return Map.of();
        }
    }

    private record TableMapping(String source, String target) {}

    private record TableId(String schema, String table) {
        static TableId parse(String raw) {
            if (!StringUtils.hasText(raw)) {
                return new TableId(null, raw);
            }
            String trimmed = raw.trim();
            int idx = trimmed.indexOf('.');
            if (idx > 0 && idx < trimmed.length() - 1) {
                String schema = trimmed.substring(0, idx).trim();
                String table = trimmed.substring(idx + 1).trim();
                return new TableId(schema, table);
            }
            return new TableId(null, trimmed);
        }

        String qualifiedName() {
            if (StringUtils.hasText(schema)) {
                return "\"" + schema.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
            }
            return "\"" + table.replace("\"", "\"\"") + "\"";
        }
    }
}
