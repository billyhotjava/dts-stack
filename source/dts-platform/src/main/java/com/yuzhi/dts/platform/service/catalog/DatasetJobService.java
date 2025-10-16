package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob;
import com.yuzhi.dts.platform.domain.catalog.DatasetJobStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.HiveColumnCommentResolver;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry.InceptorDataSourceState;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetJobService {

    private static final Logger log = LoggerFactory.getLogger(DatasetJobService.class);

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetJobRepository jobRepository;
    private final Executor taskExecutor;
    private final DatasetJobWorker worker;
    private final ObjectMapper objectMapper;

    public DatasetJobService(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetJobRepository jobRepository,
        @Qualifier("taskExecutor") Executor taskExecutor,
        DatasetJobWorker worker,
        ObjectMapper objectMapper
    ) {
        this.datasetRepository = datasetRepository;
        this.jobRepository = jobRepository;
        this.taskExecutor = taskExecutor;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    public CatalogDatasetJob submitSchemaSync(UUID datasetId, Map<String, Object> requestBody, String actor) {
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
        Map<String, Object> payload = requestBody != null ? new HashMap<>(requestBody) : Map.of();
        CatalogDatasetJob job = initializeJob(dataset, "SYNC_SCHEMA", actor, payload);
        jobRepository.save(job);

        taskExecutor.execute(() -> worker.runSchemaSync(job.getId(), payload));
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<CatalogDatasetJob> findJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<CatalogDatasetJob> recentJobs(UUID datasetId) {
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
        return jobRepository.findTop10ByDatasetOrderByCreatedDateDesc(dataset);
    }

    private CatalogDatasetJob initializeJob(CatalogDataset dataset, String type, String actor, Map<String, Object> body) {
        CatalogDatasetJob job = new CatalogDatasetJob();
        job.setDataset(dataset);
        job.setJobType(type);
        job.setStatus(DatasetJobStatus.QUEUED.name());
        job.setMessage("等待执行");
        job.setSubmittedBy(actor);
        if (body != null && !body.isEmpty()) {
            try {
                job.setDetailPayload(objectMapper.writeValueAsString(body));
            } catch (JsonProcessingException e) {
                log.debug("Failed to serialize job payload: {}", e.getMessage());
            }
        }
        return job;
    }

    public Map<String, Object> toDto(CatalogDatasetJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", job.getId());
        dto.put("datasetId", job.getDataset() != null ? job.getDataset().getId() : null);
        dto.put("jobType", job.getJobType());
        dto.put("status", job.getStatus());
        dto.put("message", job.getMessage());
        dto.put("submittedBy", job.getSubmittedBy());
        dto.put("startedAt", job.getStartedAt());
        dto.put("finishedAt", job.getFinishedAt());
        dto.put("createdAt", job.getCreatedDate());
        dto.put("updatedAt", job.getLastModifiedDate());
        dto.put("detail", parseDetail(job.getDetailPayload()));
        return dto;
    }

    private Object parseDetail(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return payload;
        }
    }

    @Service
    public static class DatasetJobWorker {

        private static final Logger workerLog = LoggerFactory.getLogger(DatasetJobWorker.class);

        private final CatalogDatasetJobRepository jobRepository;
        private final CatalogDatasetRepository datasetRepository;
        private final CatalogTableSchemaRepository tableRepository;
        private final CatalogColumnSchemaRepository columnRepository;
        private final AuditService auditService;
        private final ObjectMapper objectMapper;
        private final HiveConnectionService hiveConnectionService;
        private final InceptorDataSourceRegistry dataSourceRegistry;

        public DatasetJobWorker(
            CatalogDatasetJobRepository jobRepository,
            CatalogDatasetRepository datasetRepository,
            CatalogTableSchemaRepository tableRepository,
            CatalogColumnSchemaRepository columnRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            HiveConnectionService hiveConnectionService,
            InceptorDataSourceRegistry dataSourceRegistry
        ) {
            this.jobRepository = jobRepository;
            this.datasetRepository = datasetRepository;
            this.tableRepository = tableRepository;
            this.columnRepository = columnRepository;
            this.auditService = auditService;
            this.objectMapper = objectMapper;
            this.hiveConnectionService = hiveConnectionService;
            this.dataSourceRegistry = dataSourceRegistry;
        }

        @Transactional
        public void runSchemaSync(UUID jobId, Map<String, Object> body) {
            CatalogDatasetJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(DatasetJobStatus.RUNNING.name());
            job.setStartedAt(Instant.now());
            job.setMessage("正在同步表结构");
            jobRepository.save(job);
            auditService.audit("START", "dataset.schema", job.getDataset().getId() + ":" + job.getId());

            Map<String, Object> result = new HashMap<>();
            try {
                CatalogDataset dataset = datasetRepository.findById(job.getDataset().getId()).orElseThrow();
                ColumnSyncPlan plan = resolveColumnPlan(dataset, body);
                if (plan.columns().isEmpty()) {
                    throw new IllegalStateException("未获取到列信息，请确认源表存在或手动指定列列表");
                }

                String tableName = resolveTableName(dataset);
                if (tableName == null) {
                    throw new IllegalStateException("数据集未配置 Hive 表名，无法同步");
                }

                com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema table = tableRepository
                    .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
                    .orElseGet(() -> {
                        com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema schema = new com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema();
                        schema.setDataset(dataset);
                        schema.setName(tableName);
                        return schema;
                    });
                table.setDataset(dataset);
                table.setName(tableName);
                boolean newTable = table.getId() == null;
                table = tableRepository.save(table);

                Map<String, String> legacyComments = columnRepository
                    .findByTable(table)
                    .stream()
                    .filter(existing -> existing.getName() != null && org.springframework.util.StringUtils.hasText(existing.getComment()))
                    .collect(
                        java.util.stream.Collectors.toMap(
                            existing -> existing.getName().trim().toLowerCase(java.util.Locale.ROOT),
                            existing -> existing.getComment(),
                            (left, right) -> left,
                            java.util.LinkedHashMap::new
                        )
                    );

                columnRepository.deleteByTable(table);
                List<com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema> columns = new ArrayList<>();
                for (ColumnSpec spec : plan.columns()) {
                    com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema column = new com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema();
                    column.setTable(table);
                    column.setName(spec.name());
                    column.setDataType(spec.dataType());
                    column.setNullable(spec.nullable());
                    String comment = normalizeComment(spec.comment());
                    if (!org.springframework.util.StringUtils.hasText(comment)) {
                        comment = legacyComments.getOrDefault(spec.name().trim().toLowerCase(java.util.Locale.ROOT), null);
                    }
                    column.setComment(comment);
                    columns.add(column);
                }
                columnRepository.saveAll(columns);

                result.put("source", plan.source());
                result.put("database", plan.database());
                result.put("table", tableName);
                result.put("columnsImported", columns.size());
                result.put("tablesCreated", newTable ? 1 : 0);

                job.setStatus(DatasetJobStatus.SUCCEEDED.name());
                job.setMessage("表结构同步完成（列数：" + columns.size() + "）");
                job.setFinishedAt(Instant.now());
                job.setDetailPayload(writeResult(result));
                jobRepository.save(job);
                auditService.audit("SUCCESS", "dataset.schema", dataset.getId() + ":" + columns.size());
            } catch (Exception ex) {
                workerLog.error("Schema sync job failed: {}", ex.getMessage());
                job.setStatus(DatasetJobStatus.FAILED.name());
                job.setMessage("表结构同步失败: " + truncate(ex.getMessage()));
                job.setFinishedAt(Instant.now());
                result.put("error", truncate(ex.getMessage()));
                job.setDetailPayload(writeResult(result));
                jobRepository.save(job);
                auditService.audit("ERROR", "dataset.schema", job.getDataset().getId() + ":" + truncate(ex.getMessage()));
            }
        }
        private String writeResult(Map<String, Object> result) {
            try {
                return objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                return result.toString();
            }
        }

        private String truncate(String message) {
            if (message == null) {
                return "";
            }
            return message.length() > 180 ? message.substring(0, 180) : message;
        }

        private ColumnSyncPlan resolveColumnPlan(CatalogDataset dataset, Map<String, Object> body) throws Exception {
            List<ColumnSpec> manual = parseColumns(body);
            if (!manual.isEmpty()) {
                return new ColumnSyncPlan(manual, "payload", dataset.getHiveDatabase());
            }
            List<ColumnSpec> discovered = fetchColumnsFromSource(dataset);
            return new ColumnSyncPlan(discovered, "datasource", resolveDatabase(dataset));
        }

        private List<ColumnSpec> parseColumns(Map<String, Object> body) {
            Object columns = body != null ? body.get("columns") : null;
            if (!(columns instanceof List<?> list)) {
                return List.of();
            }
            List<ColumnSpec> specs = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String name = Objects.toString(map.get("name"), "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                String type = Objects.toString(map.get("dataType"), "string");
                Object nullableObj = map.get("nullable");
                boolean nullable = nullableObj == null || Boolean.parseBoolean(String.valueOf(nullableObj));
                String comment = normalizeComment(Objects.toString(map.get("comment"), null));
                if (!org.springframework.util.StringUtils.hasText(comment)) {
                    comment = normalizeComment(Objects.toString(map.get("description"), null));
                }
                specs.add(new ColumnSpec(name, normalizeDataType(type), nullable, comment));
            }
            return specs;
        }

        private List<ColumnSpec> fetchColumnsFromSource(CatalogDataset dataset) throws Exception {
            InceptorDataSourceState state = dataSourceRegistry
                .getActive()
                .orElseThrow(() -> new IllegalStateException("未检测到可用的 Inceptor 数据源，请联系系统管理员"));
            if (!state.isAvailable()) {
                throw new IllegalStateException("Inceptor 数据源不可用: " + state.availabilityReason());
            }

            String tableName = resolveTableName(dataset);
            if (tableName == null) {
                throw new IllegalStateException("数据集未配置 Hive 表名，无法同步");
            }
            String database = resolveDatabase(dataset);

            HiveConnectionTestRequest request = buildRequest(state);
            return hiveConnectionService.executeWithConnection(request, (connection, connectStart) -> {
                if (org.springframework.util.StringUtils.hasText(database)) {
                    try (Statement schemaStmt = connection.createStatement()) {
                        schemaStmt.execute("USE `" + sanitizeIdentifier(database) + "`");
                    }
                }
                String sql = "DESCRIBE `" + sanitizeIdentifier(tableName) + "`";
                List<ColumnSpec> specs = new ArrayList<>();
                try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        String columnName = rs.getString(1);
                        String dataType = rs.getString(2);
                        String columnComment = rs.getString(3);
                        if (!org.springframework.util.StringUtils.hasText(columnName)) {
                            continue;
                        }
                        columnName = columnName.trim();
                        if (columnName.startsWith("#")) {
                            break;
                        }
                        String comment = normalizeComment(columnComment);
                        specs.add(new ColumnSpec(columnName, normalizeDataType(dataType), true, comment));
                    }
                }
                if (specs.isEmpty()) {
                    throw new IllegalStateException("源表 " + tableName + " 未返回任何列信息");
                }
                if (specs.stream().anyMatch(spec -> !org.springframework.util.StringUtils.hasText(spec.comment()))) {
                    Map<String, String> ddlComments = HiveColumnCommentResolver.fetchColumnComments(connection, tableName, 30);
                    if (!ddlComments.isEmpty()) {
                        List<ColumnSpec> enriched = new ArrayList<>(specs.size());
                        for (ColumnSpec spec : specs) {
                            String comment = spec.comment();
                            if (!org.springframework.util.StringUtils.hasText(comment)) {
                                comment = ddlComments.getOrDefault(spec.name().toLowerCase(java.util.Locale.ROOT), null);
                            }
                            enriched.add(new ColumnSpec(spec.name(), spec.dataType(), spec.nullable(), normalizeComment(comment)));
                        }
                        specs = enriched;
                    }
                }
                return specs;
            });
        }

        private HiveConnectionTestRequest buildRequest(InceptorDataSourceState state) {
            HiveConnectionTestRequest request = new HiveConnectionTestRequest();
            request.setJdbcUrl(state.jdbcUrl());
            request.setLoginPrincipal(state.loginPrincipal());
            request.setAuthMethod(state.authMethod());
            request.setKrb5Conf(state.krb5Conf());
            request.setProxyUser(state.proxyUser());
            request.setJdbcProperties(state.jdbcProperties());
            request.setTestQuery("SELECT 1");
            if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
                request.setKeytabBase64(state.keytabBase64());
                request.setKeytabFileName(state.keytabFileName());
            } else if (state.authMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
                request.setPassword(state.password());
            }
            return request;
        }

        private String resolveTableName(CatalogDataset dataset) {
            if (dataset.getHiveTable() != null && !dataset.getHiveTable().isBlank()) {
                return dataset.getHiveTable().trim();
            }
            return dataset.getName();
        }

        private String resolveDatabase(CatalogDataset dataset) {
            if (dataset.getHiveDatabase() != null && !dataset.getHiveDatabase().isBlank()) {
                return dataset.getHiveDatabase().trim();
            }
            return dataSourceRegistry.getActive().map(InceptorDataSourceState::database).orElse(null);
        }

        private String normalizeDataType(String type) {
            if (type == null || type.isBlank()) {
                return "string";
            }
            return type.trim().toLowerCase(java.util.Locale.ROOT);
        }

        private String sanitizeIdentifier(String identifier) {
            return identifier.replace("`", "``");
        }

        private String normalizeComment(String raw) {
            if (!org.springframework.util.StringUtils.hasText(raw)) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("\\n") || trimmed.equalsIgnoreCase("n/a")) {
                return null;
            }
            return trimmed;
        }

        private record ColumnSpec(String name, String dataType, boolean nullable, String comment) {}

        private record ColumnSyncPlan(List<ColumnSpec> columns, String source, String database) {}
    }
}
