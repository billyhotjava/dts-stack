package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob;
import com.yuzhi.dts.platform.domain.catalog.DatasetJobStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.SecurityViewService;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public CatalogDatasetJob submitPolicyApply(UUID datasetId, String refreshOption, Map<String, Object> requestBody, String actor) {
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
        Map<String, Object> payload = new HashMap<>(requestBody != null ? requestBody : Map.of());
        if (refreshOption != null) {
            payload.put("refresh", refreshOption);
        }
        CatalogDatasetJob job = initializeJob(dataset, "APPLY_POLICY", actor, payload);
        jobRepository.save(job);

        taskExecutor.execute(() -> worker.runPolicyApply(job.getId(), refreshOption));
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
        private final CatalogAccessPolicyRepository accessPolicyRepository;
        private final SecurityViewService securityViewService;
        private final AuditService auditService;
        private final ObjectMapper objectMapper;

        public DatasetJobWorker(
            CatalogDatasetJobRepository jobRepository,
            CatalogDatasetRepository datasetRepository,
            CatalogTableSchemaRepository tableRepository,
            CatalogColumnSchemaRepository columnRepository,
            CatalogAccessPolicyRepository accessPolicyRepository,
            SecurityViewService securityViewService,
            AuditService auditService,
            ObjectMapper objectMapper
        ) {
            this.jobRepository = jobRepository;
            this.datasetRepository = datasetRepository;
            this.tableRepository = tableRepository;
            this.columnRepository = columnRepository;
            this.accessPolicyRepository = accessPolicyRepository;
            this.securityViewService = securityViewService;
            this.auditService = auditService;
            this.objectMapper = objectMapper;
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
                var tables = tableRepository.findByDataset(dataset);
                if (!tables.isEmpty()) {
                    result.put("skipped", true);
                    job.setStatus(DatasetJobStatus.SUCCEEDED.name());
                    job.setMessage("已存在表结构，跳过同步");
                    job.setFinishedAt(Instant.now());
                    job.setDetailPayload(writeResult(result));
                    jobRepository.save(job);
                    auditService.audit("SKIP", "dataset.schema", dataset.getId() + ":existing");
                    return;
                }

                String tableName = dataset.getHiveTable() != null && !dataset.getHiveTable().isBlank() ? dataset.getHiveTable() : dataset.getName();
                var table = new com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema();
                table.setDataset(dataset);
                table.setName(tableName);
                var savedTable = tableRepository.save(table);

                List<Map<String, Object>> columns = resolveColumns(body);
                int importedColumns = 0;
                for (Map<String, Object> columnSpec : columns) {
                    var column = new com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema();
                    column.setTable(savedTable);
                    column.setName(String.valueOf(columnSpec.getOrDefault("name", "col" + importedColumns)));
                    column.setDataType(String.valueOf(columnSpec.getOrDefault("dataType", "string")));
                    Object nullable = columnSpec.get("nullable");
                    column.setNullable(nullable == null || Boolean.parseBoolean(String.valueOf(nullable)));
                    column.setTags(columnSpec.containsKey("tags") ? String.valueOf(columnSpec.get("tags")) : null);
                    column.setSensitiveTags(columnSpec.containsKey("sensitiveTags") ? String.valueOf(columnSpec.get("sensitiveTags")) : null);
                    columnRepository.save(column);
                    importedColumns++;
                }

                result.put("tablesCreated", 1);
                result.put("columnsImported", importedColumns);
                job.setStatus(DatasetJobStatus.SUCCEEDED.name());
                job.setMessage("表结构同步完成");
                job.setFinishedAt(Instant.now());
                job.setDetailPayload(writeResult(result));
                jobRepository.save(job);
                auditService.audit("SUCCESS", "dataset.schema", dataset.getId() + ":" + importedColumns);
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

        @Transactional
        public void runPolicyApply(UUID jobId, String refreshOption) {
            CatalogDatasetJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(DatasetJobStatus.RUNNING.name());
            job.setStartedAt(Instant.now());
            job.setMessage("正在生成安全视图");
            jobRepository.save(job);
            auditService.audit("START", "policy.apply", job.getDataset().getId() + ":" + job.getId());

            Map<String, Object> result = new HashMap<>();
            try {
                CatalogDataset dataset = datasetRepository.findById(job.getDataset().getId()).orElseThrow();
                var policy = accessPolicyRepository.findByDataset(dataset).orElse(null);
                var execution = securityViewService.applyViews(dataset, policy, refreshOption != null ? refreshOption : "NONE");

                result.put("statements", execution.statements());
                result.put("executionResults", execution.executionResults());
                result.put("persistedViews", execution.persistedViews());
                job.setDetailPayload(writeResult(result));
                job.setFinishedAt(Instant.now());

                boolean success = execution.success();
                job.setStatus(success ? DatasetJobStatus.SUCCEEDED.name() : DatasetJobStatus.FAILED.name());
                job.setMessage(success ? "安全视图已发布" : "安全视图执行失败");
                jobRepository.save(job);
                auditService.audit(success ? "SUCCESS" : "ERROR", "policy.apply", dataset.getId() + ":" + execution.persistedViews());
            } catch (Exception ex) {
                workerLog.error("Policy apply job failed: {}", ex.getMessage());
                job.setStatus(DatasetJobStatus.FAILED.name());
                job.setMessage("安全视图执行失败: " + truncate(ex.getMessage()));
                job.setFinishedAt(Instant.now());
                result.put("error", truncate(ex.getMessage()));
                job.setDetailPayload(writeResult(result));
                jobRepository.save(job);
                auditService.audit("ERROR", "policy.apply", job.getDataset().getId() + ":" + truncate(ex.getMessage()));
            }
        }

        private List<Map<String, Object>> resolveColumns(Map<String, Object> body) {
            Object columns = body != null ? body.get("columns") : null;
            if (columns instanceof List<?> list) {
                return list
                    .stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
            }
            return List.of(
                Map.of("name", "id", "dataType", "string", "nullable", false),
                Map.of("name", "name", "dataType", "string", "nullable", true),
                Map.of("name", "email", "dataType", "string", "nullable", true),
                Map.of("name", "created_at", "dataType", "timestamp", "nullable", true),
                Map.of("name", "level", "dataType", "string", "nullable", true)
            );
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
    }
}
