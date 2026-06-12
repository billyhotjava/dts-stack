package com.yuzhi.dts.ingestion.service.etl.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiRawLandingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String CHECKPOINT_TABLE = "dts_api_ingestion_checkpoint";

    private final JdbcMetadataService metadataService;
    private final IngestionSourceResolver sourceResolver;
    private final ObjectMapper objectMapper;
    private final CursorTracker cursorTracker = new CursorTracker();
    private final ApiProperties apiProperties;

    public ApiRawLandingService(JdbcMetadataService metadataService, ObjectMapper objectMapper) {
        this(metadataService, null, objectMapper, new ApiProperties());
    }

    public ApiRawLandingService(JdbcMetadataService metadataService, ObjectMapper objectMapper, ApiProperties apiProperties) {
        this(metadataService, null, objectMapper, apiProperties);
    }

    @Autowired
    public ApiRawLandingService(
        JdbcMetadataService metadataService,
        IngestionSourceResolver sourceResolver,
        ObjectMapper objectMapper,
        ApiProperties apiProperties
    ) {
        this.metadataService = metadataService;
        this.sourceResolver = sourceResolver;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.apiProperties = apiProperties == null ? new ApiProperties() : apiProperties;
    }

    public LandingResult land(
        ExecutionPlan plan,
        IngestionTask task,
        IngestionExecution execution,
        List<ApiHttpEngine.ApiHttpResult> pages
    ) {
        if (pages == null || pages.isEmpty()) {
            return new LandingResult(0L, List.of(), Map.of());
        }
        JdbcMetadataService.JdbcConnectionInfo targetInfo = targetInfo(task);
        Map<String, Object> sourceConfig = safeMap(safeMap(plan == null ? null : plan.payload()).get("sourceConfig"));
        Map<String, ResourceConfig> resources = resolveResources(sourceConfig);
        Map<String, List<ApiHttpEngine.ApiHttpResult>> byResource = groupByResource(pages);
        long rowsWritten = 0L;
        List<String> failedResources = new ArrayList<>();
        Map<String, Object> metrics = new LinkedHashMap<>();

        for (Map.Entry<String, List<ApiHttpEngine.ApiHttpResult>> entry : byResource.entrySet()) {
            String resourceId = entry.getKey();
            ResourceConfig resource = resources.getOrDefault(resourceId, ResourceConfig.fallback(resourceId, tablePrefix()));
            Connection connection = null;
            try {
                connection = metadataService.openConnection(targetInfo);
                connection.setAutoCommit(false);
                ensureCheckpointTable(connection);
                ensureLandingTable(connection, resource.targetTable());
                long resourceWritten = insertResourceRecords(connection, resource, sourceConfig, task, execution, entry.getValue());
                writeCheckpointIfNeeded(connection, plan, task, execution, resource, entry.getValue());
                connection.commit();
                rowsWritten += resourceWritten;
                metrics.put(resourceId + ".rowsWritten", resourceWritten);
            } catch (Exception ex) {
                rollbackQuietly(connection);
                failedResources.add(resourceId);
                metrics.put(resourceId + ".error", ex.getMessage());
            } finally {
                closeQuietly(connection);
            }
        }
        return new LandingResult(rowsWritten, failedResources, metrics);
    }

    public Map<String, String> loadCheckpoints(ExecutionPlan plan, IngestionTask task) {
        if (!isCursorCheckpoint(plan) || task == null || task.getId() == null) {
            return Map.of();
        }
        Connection connection = null;
        try {
            connection = metadataService.openConnection(targetInfo(task));
            ensureCheckpointTable(connection);
            Map<String, String> checkpoints = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT resource_id, cursor_value FROM " + CHECKPOINT_TABLE + " WHERE task_id = ?"
            )) {
                statement.setLong(1, task.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String resourceId = resultSet.getString("resource_id");
                        String cursorValue = resultSet.getString("cursor_value");
                        if (StringUtils.hasText(resourceId) && StringUtils.hasText(cursorValue)) {
                            checkpoints.put(resourceId, cursorValue);
                        }
                    }
                }
            }
            return Map.copyOf(checkpoints);
        } catch (Exception ex) {
            throw new IllegalStateException("API checkpoint 读取失败: " + ex.getMessage(), ex);
        } finally {
            closeQuietly(connection);
        }
    }

    private long insertResourceRecords(
        Connection connection,
        ResourceConfig resource,
        Map<String, Object> sourceConfig,
        IngestionTask task,
        IngestionExecution execution,
        List<ApiHttpEngine.ApiHttpResult> pages
    ) throws Exception {
        long rowsWritten = 0L;
        String sql = insertSql(TableId.parse(resource.targetTable()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int batchSize = Math.max(1, apiProperties.getLanding().getBatchSize());
            int pending = 0;
            for (ApiHttpEngine.ApiHttpResult page : pages) {
                List<JsonNode> records = page.records() == null ? List.of() : page.records();
                int recordNo = 1;
                for (JsonNode record : records) {
                    bindInsert(statement, resource, sourceConfig, task, execution, page, record, recordNo++);
                    if (batchSize == 1) {
                        int affected = statement.executeUpdate();
                        if (affected > 0) {
                            rowsWritten += affected;
                        }
                    } else {
                        statement.addBatch();
                        pending++;
                        if (pending >= batchSize) {
                            rowsWritten += affectedRows(statement.executeBatch());
                            pending = 0;
                        }
                    }
                }
            }
            if (pending > 0) {
                rowsWritten += affectedRows(statement.executeBatch());
            }
        }
        return rowsWritten;
    }

    private long affectedRows(int[] results) {
        if (results == null || results.length == 0) {
            return 0L;
        }
        long rows = 0L;
        for (int result : results) {
            if (result > 0) {
                rows += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                rows++;
            }
        }
        return rows;
    }

    private void bindInsert(
        PreparedStatement statement,
        ResourceConfig resource,
        Map<String, Object> sourceConfig,
        IngestionTask task,
        IngestionExecution execution,
        ApiHttpEngine.ApiHttpResult page,
        JsonNode record,
        int recordNo
    ) throws Exception {
        String cursorValue = cursorValue(record, resource.cursor());
        statement.setString(1, objectMapper.writeValueAsString(record));
        statement.setString(2, sourceSystem(sourceConfig));
        statement.setString(3, resource.resourceId());
        statement.setString(4, page.uri() == null ? null : page.uri().toString());
        statement.setTimestamp(5, Timestamp.from(Instant.now()));
        statement.setString(6, execution == null ? null : execution.getBatchId());
        statement.setString(7, executionId(execution));
        statement.setInt(8, page.pageNo());
        statement.setInt(9, recordNo);
        statement.setString(10, cursorValue);
        statement.setString(11, recordHash(record));
    }

    private String insertSql(TableId table) {
        return "INSERT INTO "
            + table.qualifiedName()
            + " (_dts_raw_record, _dts_source_system, _dts_source_resource, _dts_endpoint, _dts_import_time, "
            + "_dts_batch_id, _dts_execution_id, _dts_page_no, _dts_record_no, _dts_cursor_value, _dts_record_hash) "
            + "VALUES (?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (_dts_source_resource, _dts_record_hash) DO NOTHING";
    }

    private void ensureCheckpointTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS "
                    + CHECKPOINT_TABLE
                    + " (task_id BIGINT NOT NULL, resource_id TEXT NOT NULL, cursor_value TEXT, "
                    + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), PRIMARY KEY (task_id, resource_id))"
            );
        }
    }

    private void ensureLandingTable(Connection connection, String targetTable) throws Exception {
        TableId table = TableId.parse(targetTable);
        try (Statement statement = connection.createStatement()) {
            if (StringUtils.hasText(table.schema()) && !"public".equalsIgnoreCase(table.schema())) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(table.schema()));
            }
            statement.execute(
                "CREATE TABLE IF NOT EXISTS "
                    + table.qualifiedName()
                    + " (id BIGSERIAL PRIMARY KEY, _dts_raw_record JSONB NOT NULL, _dts_source_system TEXT, "
                    + "_dts_source_resource TEXT, _dts_endpoint TEXT, _dts_import_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "
                    + "_dts_batch_id TEXT, _dts_execution_id TEXT, _dts_page_no INTEGER, _dts_record_no INTEGER, "
                    + "_dts_cursor_value TEXT, _dts_record_hash TEXT NOT NULL)"
            );
            statement.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS "
                    + quoteIdentifier(indexName(table))
                    + " ON "
                    + table.qualifiedName()
                    + " (_dts_source_resource, _dts_record_hash)"
            );
        }
    }

    private void writeCheckpointIfNeeded(
        Connection connection,
        ExecutionPlan plan,
        IngestionTask task,
        IngestionExecution execution,
        ResourceConfig resource,
        List<ApiHttpEngine.ApiHttpResult> pages
    ) throws Exception {
        if (resource.cursor().isEmpty() || isBackfill(execution)) {
            return;
        }
        List<JsonNode> records = new ArrayList<>();
        for (ApiHttpEngine.ApiHttpResult page : pages) {
            if (page.records() != null) {
                records.addAll(page.records());
            }
        }
        String cursorValue = cursorTracker.advance(records, resource.cursor());
        if (!StringUtils.hasText(cursorValue)) {
            return;
        }
        String policyType = plan == null || plan.checkpointPolicy() == null ? null : plan.checkpointPolicy().type();
        if (StringUtils.hasText(policyType) && !"cursor".equalsIgnoreCase(policyType)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO "
                + CHECKPOINT_TABLE
                + "(task_id, resource_id, cursor_value, updated_at) VALUES (?, ?, ?, now()) "
                + "ON CONFLICT (task_id, resource_id) DO UPDATE SET cursor_value=EXCLUDED.cursor_value, updated_at=now()"
        )) {
            statement.setLong(1, task == null || task.getId() == null ? 0L : task.getId());
            statement.setString(2, resource.resourceId());
            statement.setString(3, cursorValue);
            statement.executeUpdate();
        }
    }

    private JdbcMetadataService.JdbcConnectionInfo targetInfo(IngestionTask task) {
        Map<String, Object> destination = jsonNodeToMap(task == null ? null : task.getDestinationConfig());
        UUID targetDataSourceId = uuid(firstText(destination, "targetDataSourceId", "destinationDataSourceId", "dataSourceId"));
        if (targetDataSourceId != null) {
            if (sourceResolver == null) {
                throw new IllegalArgumentException("API raw landing 目标数据源解析器未初始化");
            }
            return sourceResolver.resolveJdbcInfo(targetDataSourceId);
        }
        String jdbcUrl = firstText(destination, "jdbcUrl", "url");
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new IllegalArgumentException("API raw landing 目标库 jdbcUrl 不能为空");
        }
        return new JdbcMetadataService.JdbcConnectionInfo(
            jdbcUrl,
            firstText(destination, "username", "user"),
            firstText(destination, "password"),
            firstText(destination, "driverClass", "driver"),
            firstText(destination, "driverVersion"),
            stringMap(destination.get("jdbcProperties"))
        );
    }

    private Map<String, ResourceConfig> resolveResources(Map<String, Object> sourceConfig) {
        Map<String, ResourceConfig> resources = new LinkedHashMap<>();
        Object raw = sourceConfig.get("resources");
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                ResourceConfig config = resourceConfig(safeMap(item));
                resources.put(config.resourceId(), config);
            }
        }
        Map<String, Object> single = safeMap(sourceConfig.get("resource"));
        if (!single.isEmpty()) {
            ResourceConfig config = resourceConfig(single);
            resources.put(config.resourceId(), config);
        }
        return resources;
    }

    private ResourceConfig resourceConfig(Map<String, Object> resource) {
        String resourceId = firstText(resource, "resourceId", "id", "name");
        if (!StringUtils.hasText(resourceId)) {
            resourceId = "api_resource";
        }
        String targetTable = firstText(resource, "targetTable");
        if (!StringUtils.hasText(targetTable)) {
            targetTable = tablePrefix() + normalizeIdentifier(resourceId);
        }
        return new ResourceConfig(resourceId, targetTable, safeMap(resource.get("cursor")));
    }

    private String tablePrefix() {
        String tablePrefix = apiProperties == null ? null : apiProperties.getTablePrefix();
        return StringUtils.hasText(tablePrefix) ? tablePrefix.trim() : ApiProperties.DEFAULT_TABLE_PREFIX;
    }

    private Map<String, List<ApiHttpEngine.ApiHttpResult>> groupByResource(List<ApiHttpEngine.ApiHttpResult> pages) {
        Map<String, List<ApiHttpEngine.ApiHttpResult>> grouped = new LinkedHashMap<>();
        for (ApiHttpEngine.ApiHttpResult page : pages) {
            String resourceId = StringUtils.hasText(page.resourceId()) ? page.resourceId() : "api_resource";
            grouped.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(page);
        }
        return grouped;
    }

    private String cursorValue(JsonNode record, Map<String, Object> cursor) {
        String field = firstText(cursor, "field");
        if (!StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = JsonPathLite.read(record, field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String recordHash(JsonNode record) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(record)));
    }

    private String executionId(IngestionExecution execution) {
        if (execution == null) {
            return null;
        }
        if (StringUtils.hasText(execution.getExecutionId())) {
            return execution.getExecutionId();
        }
        return execution.getId() == null ? null : execution.getId().toString();
    }

    private String sourceSystem(Map<String, Object> sourceConfig) {
        String value = firstText(sourceConfig, "sourceSystem", "sourceApp", "appCode", "system");
        return StringUtils.hasText(value) ? value : "api";
    }

    private UUID uuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("API raw landing 目标数据源 ID 格式不合法: " + value, ex);
        }
    }

    private boolean isBackfill(IngestionExecution execution) {
        return execution != null && (execution.getBackfillWindowStart() != null || execution.getBackfillWindowEnd() != null);
    }

    private boolean isCursorCheckpoint(ExecutionPlan plan) {
        String policyType = plan == null || plan.checkpointPolicy() == null ? null : plan.checkpointPolicy().type();
        return "cursor".equalsIgnoreCase(policyType);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private Map<String, Object> safeMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, Object> raw = safeMap(value);
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (StringUtils.hasText(key) && item != null) {
                result.put(key, String.valueOf(item));
            }
        });
        return result;
    }

    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private String quoteIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "api_resource";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "api_resource";
    }

    private String indexName(TableId table) {
        String base = ("ux_" + table.table() + "_dts_hash").replaceAll("[^A-Za-z0-9_]+", "_");
        return base.length() > 60 ? base.substring(0, 60) : base;
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (Exception ignored) {
            // keep original resource failure
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception ignored) {
            // close best-effort
        }
    }

    public record LandingResult(long rowsWritten, List<String> failedResources, Map<String, Object> metrics) {
        public LandingResult {
            failedResources = failedResources == null ? List.of() : List.copyOf(failedResources);
            metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        }
    }

    private record ResourceConfig(String resourceId, String targetTable, Map<String, Object> cursor) {
        private ResourceConfig {
            cursor = cursor == null ? Map.of() : Map.copyOf(cursor);
        }

        private static ResourceConfig fallback(String resourceId, String tablePrefix) {
            String safeResourceId = StringUtils.hasText(resourceId) ? resourceId : "api_resource";
            String prefix = StringUtils.hasText(tablePrefix) ? tablePrefix.trim() : ApiProperties.DEFAULT_TABLE_PREFIX;
            return new ResourceConfig(safeResourceId, prefix + normalizeTablePart(safeResourceId), Map.of());
        }

        private static String normalizeTablePart(String value) {
            String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
            return StringUtils.hasText(normalized) ? normalized : "api_resource";
        }
    }

    private record TableId(String schema, String table) {
        private static TableId parse(String value) {
            String normalized = StringUtils.hasText(value) ? value.trim() : "ods_api_resource";
            int idx = normalized.lastIndexOf('.');
            if (idx > 0 && idx < normalized.length() - 1) {
                return new TableId(normalized.substring(0, idx), normalized.substring(idx + 1));
            }
            return new TableId(null, normalized);
        }

        private String qualifiedName() {
            if (StringUtils.hasText(schema)) {
                return quote(schema) + "." + quote(table);
            }
            return quote(table);
        }

        private static String quote(String value) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
