package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class AddaxJobService {

    private static final Logger LOG = LoggerFactory.getLogger(AddaxJobService.class);

    private final AddaxProperties properties;
    private final IngestionSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public AddaxJobService(AddaxProperties properties, IngestionSettingsService settingsService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public record AddaxJobResult(String jobName, String jobPath, Map<String, Object> jobConfig) {}

    public AddaxJobResult createJob(
        String taskName,
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig
    ) {
        Map<String, Object> resolvedJob = resolveJobConfig(readerType, readerConfig, writerType, writerConfig, jobConfig);
        String jobDir = resolveJobDir();
        String jobName = buildJobName(taskName);
        Path dir = Paths.get(jobDir);
        try {
            Files.createDirectories(dir);
            Path jobPath = dir.resolve(jobName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), resolvedJob);
            return new AddaxJobResult(jobName, jobPath.toString(), resolvedJob);
        } catch (Exception ex) {
            LOG.warn("Failed to write Addax job {}: {}", jobName, ex.getMessage());
            throw new IllegalStateException("生成 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> resolveJobConfig(
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig
    ) {
        if (jobConfig != null && !jobConfig.isEmpty()) {
            return new LinkedHashMap<>(jobConfig);
        }
        if (!StringUtils.hasText(readerType) || !StringUtils.hasText(writerType)) {
            throw new IllegalArgumentException("缺少 Addax Reader/Writer 类型");
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reader", Map.of("name", readerType, "parameter", safeMap(readerConfig)));
        content.put("writer", Map.of("name", writerType, "parameter", safeMap(writerConfig)));

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("setting", Map.of("speed", Map.of("channel", 1)));
        job.put("content", List.of(content));
        return Map.of("job", job);
    }

    private String resolveJobDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String jobDir = settings.getString("jobDir", properties.getJobDir());
        if (!StringUtils.hasText(jobDir)) {
            throw new IllegalStateException("未配置 Addax 作业目录");
        }
        return jobDir.trim();
    }

    private String buildJobName(String taskName) {
        String base = StringUtils.hasText(taskName) ? taskName.trim().toLowerCase(Locale.ROOT) : "addax-job";
        base = base.replaceAll("[^a-z0-9-_]+", "-");
        if (!StringUtils.hasText(base)) {
            base = "addax-job";
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return base + "-" + suffix + ".json";
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    /**
     * 从IngestionTask实体创建Addax Job
     */
    public AddaxJobResult createJobFromTask(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        if (task == null) {
            throw new IllegalArgumentException("IngestionTask cannot be null");
        }

        // 将JsonNode转换为Map
        Map<String, Object> readerConfig = jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null 
            ? jsonNodeToMap(task.getDestinationConfig()) 
            : Map.of();
        Map<String, Object> jobConfig = task.getAddaxConfig() != null 
            ? jsonNodeToMap(task.getAddaxConfig()) 
            : null;

        return createJob(
            task.getName(),
            task.getSourceType(),
            readerConfig,
            task.getDestinationType() != null ? task.getDestinationType() : "postgresqlwriter",
            writerConfig,
            jobConfig
        );
    }

    /**
     * 保存Job JSON到指定路径（用于更新已有任务）
     */
    public String saveJobJson(String jobJson, Long taskId) {
        String jobDir = resolveJobDir();
        String jobName = "task_" + taskId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".json";
        Path dir = Paths.get(jobDir);
        try {
            Files.createDirectories(dir);
            Path jobPath = dir.resolve(jobName);
            Files.writeString(jobPath, jobJson);
            return jobPath.toString();
        } catch (Exception ex) {
            LOG.warn("Failed to save job JSON for task {}: {}", taskId, ex.getMessage());
            throw new IllegalStateException("保存 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将JsonNode转换为Map
     */
    private Map<String, Object> jsonNodeToMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to convert JsonNode to Map: {}", e.getMessage());
            return Map.of();
        }
    }
}
