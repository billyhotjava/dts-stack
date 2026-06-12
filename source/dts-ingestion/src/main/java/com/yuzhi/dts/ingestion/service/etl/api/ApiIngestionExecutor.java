package com.yuzhi.dts.ingestion.service.etl.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiIngestionExecutor {

    @FunctionalInterface
    interface PlanRunner {
        ApiIngestionResult run(ExecutionPlan plan, ExecutionContext context) throws Exception;
    }

    public record ExecutionContext(
        Long taskId,
        Long executionId,
        String taskName,
        String batchId,
        IngestionTask task,
        IngestionExecution execution
    ) {}

    private final ConcurrentMap<Long, ReentrantLock> taskLocks = new ConcurrentHashMap<>();
    private final PlanRunner planRunner;
    private final MeterRegistry meterRegistry;
    private final ApiProperties apiProperties;

    @Autowired
    public ApiIngestionExecutor(
        ApiHttpEngine httpEngine,
        ApiRawLandingService rawLandingService,
        MeterRegistry meterRegistry,
        ApiProperties apiProperties
    ) {
        this((plan, context) -> runHttpPlan(httpEngine, rawLandingService, plan, context), meterRegistry, apiProperties);
    }

    public ApiIngestionExecutor(ApiHttpEngine httpEngine, ApiRawLandingService rawLandingService) {
        this((plan, context) -> runHttpPlan(httpEngine, rawLandingService, plan, context));
    }

    ApiIngestionExecutor(ApiHttpEngine httpEngine) {
        this((plan, context) -> runHttpPlan(httpEngine, null, plan, context));
    }

    ApiIngestionExecutor(PlanRunner planRunner) {
        this(planRunner, null, new ApiProperties());
    }

    ApiIngestionExecutor(PlanRunner planRunner, MeterRegistry meterRegistry) {
        this(planRunner, meterRegistry, new ApiProperties());
    }

    ApiIngestionExecutor(PlanRunner planRunner, MeterRegistry meterRegistry, ApiProperties apiProperties) {
        this.planRunner = planRunner == null ? ApiIngestionExecutor::fallbackRun : planRunner;
        this.meterRegistry = meterRegistry;
        this.apiProperties = apiProperties == null ? new ApiProperties() : apiProperties;
    }

    public ApiIngestionResult execute(ExecutionPlan plan, IngestionTask task, IngestionExecution execution) {
        validate(plan, task, execution);
        Long taskId = task.getId();
        ReentrantLock lock = taskLocks.computeIfAbsent(taskId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new IllegalStateException("API 入湖任务正在执行中，请稍后重试");
        }
        long startNanos = System.nanoTime();
        ExecutionContext context = new ExecutionContext(taskId, execution.getId(), task.getName(), execution.getBatchId(), task, execution);
        try {
            ApiIngestionResult result = runWithTimeout(plan, context);
            ApiIngestionResult resolved = result == null ? ApiIngestionResult.success(0L, 0L, Map.of()) : result;
            applyLineageSnapshot(plan, context, resolved);
            recordMetrics(plan, context, resolved, null, startNanos);
            return resolved;
        } catch (RuntimeException ex) {
            recordMetrics(plan, context, null, ex, startNanos);
            throw ex;
        } catch (Exception ex) {
            IllegalStateException wrapped = new IllegalStateException("API 入湖执行失败: " + ex.getMessage(), ex);
            recordMetrics(plan, context, null, wrapped, startNanos);
            throw wrapped;
        } finally {
            lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                taskLocks.remove(taskId, lock);
            }
        }
    }

    private ApiIngestionResult runWithTimeout(ExecutionPlan plan, ExecutionContext context) throws Exception {
        Duration timeout = apiProperties.getExecutionTimeout();
        long timeoutMillis = timeout == null ? 0L : timeout.toMillis();
        if (timeoutMillis <= 0L) {
            return planRunner.run(plan, context);
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dts-api-ingestion-" + context.taskId());
            thread.setDaemon(true);
            return thread;
        });
        Future<ApiIngestionResult> future = executor.submit(() -> planRunner.run(plan, context));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ApiHttpException("API_RUNTIME_TIMEOUT", "API 入湖执行超时: " + timeout.toSeconds() + "s", null, 0, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("API 入湖执行失败: " + cause.getMessage(), cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void validate(ExecutionPlan plan, IngestionTask task, IngestionExecution execution) {
        if (plan == null) {
            throw new IllegalArgumentException("API 执行计划不能为空");
        }
        if (!"api-http".equalsIgnoreCase(plan.engine())) {
            throw new IllegalArgumentException("不支持的 API 执行引擎: " + plan.engine());
        }
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("API 入湖任务不能为空");
        }
        if (execution == null || execution.getId() == null) {
            throw new IllegalArgumentException("API 执行记录不能为空");
        }
    }

    private static ApiIngestionResult runHttpPlan(
        ApiHttpEngine httpEngine,
        ApiRawLandingService rawLandingService,
        ExecutionPlan plan,
        ExecutionContext context
    ) {
        if (httpEngine == null) {
            throw new IllegalStateException("API HTTP 引擎未初始化");
        }
        Map<String, String> checkpoints = Map.of();
        if (rawLandingService != null && !isBackfill(context.execution())) {
            checkpoints = rawLandingService.loadCheckpoints(plan, context.task());
        }
        java.util.List<ApiHttpEngine.ApiHttpResult> results = httpEngine.execute(plan, context.execution(), checkpoints);
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (StringUtils.hasText(plan.engine())) {
            metrics.put("engine", plan.engine());
        }
        long rowsRead = results.stream().mapToLong(result -> result.records() == null ? 0L : result.records().size()).sum();
        List<Map<String, Object>> resourceStats = resourceStats(results);
        metrics.put("taskId", context.taskId());
        metrics.put("executionId", context.executionId());
        metrics.put("resources", results.size());
        metrics.put("resourceCount", resourceStats.size());
        metrics.put("pages", results.size());
        metrics.put("rowsRead", rowsRead);
        if (!resourceStats.isEmpty()) {
            metrics.put("resourceStats", resourceStats);
        }
        metrics.put(
            "bytesRead",
            results.stream().mapToLong(result -> result.body() == null ? 0L : result.body().length).sum()
        );
        long rowsWritten = 0L;
        if (rawLandingService != null) {
            ApiRawLandingService.LandingResult landing = rawLandingService.land(plan, context.task(), context.execution(), results);
            rowsWritten = landing.rowsWritten();
            metrics.put("rowsWritten", rowsWritten);
            metrics.putAll(landing.metrics());
            if (!landing.failedResources().isEmpty()) {
                markFailedResources(resourceStats, landing.failedResources());
                metrics.put("failedResources", landing.failedResources());
                return new ApiIngestionResult(
                    false,
                    rowsRead,
                    rowsWritten,
                    "API raw landing 部分资源失败: " + String.join(",", landing.failedResources()),
                    metrics
                );
            }
        } else {
            metrics.put("rowsWritten", rowsWritten);
        }
        return ApiIngestionResult.success(rowsRead, rowsWritten, metrics);
    }

    private static void markFailedResources(List<Map<String, Object>> resourceStats, List<String> failedResources) {
        if (resourceStats == null || resourceStats.isEmpty() || failedResources == null || failedResources.isEmpty()) {
            return;
        }
        for (Map<String, Object> stat : resourceStats) {
            String resourceId = text(stat.get("resourceId"));
            if (StringUtils.hasText(resourceId) && failedResources.contains(resourceId)) {
                stat.put("status", "FAILED");
            }
        }
    }

    private static List<Map<String, Object>> resourceStats(java.util.List<ApiHttpEngine.ApiHttpResult> results) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        if (results == null) {
            return List.of();
        }
        for (ApiHttpEngine.ApiHttpResult result : results) {
            if (result == null) {
                continue;
            }
            String resourceId = text(result.resourceId());
            if (!StringUtils.hasText(resourceId)) {
                resourceId = "api_resource";
            }
            String resolvedResourceId = resourceId;
            Map<String, Object> stat = grouped.computeIfAbsent(resolvedResourceId, ignored -> {
                Map<String, Object> initial = new LinkedHashMap<>();
                initial.put("resourceId", resolvedResourceId);
                initial.put("status", "SUCCESS");
                initial.put("pageCount", 0L);
                initial.put("rowsRead", 0L);
                return initial;
            });
            long pageCount = longValue(stat.get("pageCount")) + 1L;
            long rowsRead = longValue(stat.get("rowsRead")) + (result.records() == null ? 0L : result.records().size());
            stat.put("pageCount", pageCount);
            stat.put("rowsRead", rowsRead);
            stat.put("httpStatus", result.statusCode());
            if (result.uri() != null) {
                stat.put("endpoint", result.uri().toString());
            }
            if (StringUtils.hasText(result.cursorValue())) {
                stat.put("cursorValue", result.cursorValue());
            }
        }
        return List.copyOf(grouped.values());
    }

    private static ApiIngestionResult fallbackRun(ExecutionPlan plan, ExecutionContext context) {
        return ApiIngestionResult.success(0L, 0L, Map.of());
    }

    private static boolean isBackfill(IngestionExecution execution) {
        return execution != null && (execution.getBackfillWindowStart() != null || execution.getBackfillWindowEnd() != null);
    }

    private void recordMetrics(
        ExecutionPlan plan,
        ExecutionContext context,
        ApiIngestionResult result,
        Throwable failure,
        long startNanos
    ) {
        if (meterRegistry == null) {
            return;
        }
        boolean success = failure == null && (result == null || result.success());
        String outcome = success ? "success" : "failed";
        Tags baseTags = metricTags(plan, context, outcome);
        meterRegistry.timer("dts_api_ingestion_duration", baseTags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        if (success && result != null) {
            meterRegistry.counter("dts_api_ingestion_records", baseTags).increment(Math.max(0L, valueOrZero(result.rowsRead())));
            meterRegistry.counter("dts_api_ingestion_pages", baseTags)
                .increment(Math.max(0L, metricLong(result.metrics(), "pages", "resources")));
            return;
        }
        String category = failure == null
            ? ExecutionFailureClassifier.classify(result == null ? null : result.errorMessage())
            : failureCategory(failure);
        meterRegistry.counter("dts_api_ingestion_failures", baseTags.and("category", category)).increment();
    }

    private Tags metricTags(ExecutionPlan plan, ExecutionContext context, String outcome) {
        return Tags.of(
            "taskId",
            context.taskId() == null ? "unknown" : context.taskId().toString(),
            "taskName",
            StringUtils.hasText(context.taskName()) ? context.taskName() : "unknown",
            "engine",
            StringUtils.hasText(plan == null ? null : plan.engine()) ? plan.engine() : "unknown",
            "connectorType",
            StringUtils.hasText(plan == null ? null : plan.connectorType()) ? plan.connectorType() : "unknown",
            "outcome",
            outcome
        );
    }

    private String failureCategory(Throwable failure) {
        if (failure instanceof ApiHttpException apiHttpException) {
            return ExecutionFailureClassifier.classify(apiHttpException.getCode() + ": " + apiHttpException.getMessage());
        }
        return ExecutionFailureClassifier.classify(failure == null ? null : failure.getMessage());
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private long metricLong(Map<String, Object> metrics, String... keys) {
        if (metrics == null || keys == null) {
            return 0L;
        }
        for (String key : keys) {
            Object value = metrics.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return 0L;
    }

    private void applyLineageSnapshot(ExecutionPlan plan, ExecutionContext context, ApiIngestionResult result) {
        if (context.execution() == null) {
            return;
        }
        Map<String, Object> payload = safeMap(plan == null ? null : plan.payload());
        Object rawMappings = payload.get("odsMappings");
        if (!(rawMappings instanceof Iterable<?> mappings)) {
            return;
        }
        ArrayNode sources = JsonNodeFactory.instance.arrayNode();
        ArrayNode targets = JsonNodeFactory.instance.arrayNode();
        String namespace = sourceNamespace(payload, context.task());
        for (Object item : mappings) {
            Map<String, Object> mapping = safeMap(item);
            String source = text(mapping.get("source"));
            String target = text(mapping.get("target"));
            if (StringUtils.hasText(source)) {
                sources.add(apiSourceDataset(source, namespace, payload, context.task(), result));
            }
            ObjectNode targetNode = targetDataset(target, result);
            if (targetNode != null) {
                targets.add(targetNode);
            }
        }
        if (!sources.isEmpty()) {
            context.execution().setSourceTables(sources);
        }
        if (!targets.isEmpty()) {
            context.execution().setTargetTables(targets);
        }
    }

    private ObjectNode apiSourceDataset(
        String source,
        String namespace,
        Map<String, Object> payload,
        IngestionTask task,
        ApiIngestionResult result
    ) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("role", "source");
        node.put("name", source);
        node.put("namespace", namespace);
        node.put("qualifiedName", namespace + "." + source);
        node.put("sourceType", "api");
        String dataSourceId = text(payload.get("sourceDataSourceId"));
        if (!StringUtils.hasText(dataSourceId) && task != null && task.getSourceDataSourceId() != null) {
            dataSourceId = task.getSourceDataSourceId().toString();
        }
        if (StringUtils.hasText(dataSourceId)) {
            node.put("dataSourceId", dataSourceId);
        }
        Map<String, Object> stats = resourceStats(result, source);
        if (!stats.isEmpty()) {
            putLong(node, "rowsRead", stats.get("rowsRead"));
            putLong(node, "pageCount", stats.get("pageCount"));
            putText(node, "status", stats.get("status"));
            putText(node, "cursorValue", stats.get("cursorValue"));
            putText(node, "endpoint", stats.get("endpoint"));
            putLong(node, "httpStatus", stats.get("httpStatus"));
        } else if (result != null && result.rowsRead() != null) {
            node.put("rowsRead", result.rowsRead());
        }
        return node;
    }

    private ObjectNode targetDataset(String target, ApiIngestionResult result) {
        if (!StringUtils.hasText(target)) {
            return null;
        }
        String namespace = null;
        String name = target;
        int idx = target.lastIndexOf('.');
        if (idx >= 0) {
            namespace = target.substring(0, idx);
            name = target.substring(idx + 1);
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("role", "target");
        node.put("name", name);
        node.put("qualifiedName", target);
        if (StringUtils.hasText(namespace)) {
            node.put("namespace", namespace);
        }
        if (result != null && result.rowsWritten() != null) {
            node.put("rowsWritten", result.rowsWritten());
        }
        return node;
    }

    private String sourceNamespace(Map<String, Object> payload, IngestionTask task) {
        String dataSourceId = text(payload.get("sourceDataSourceId"));
        if (!StringUtils.hasText(dataSourceId) && task != null && task.getSourceDataSourceId() != null) {
            dataSourceId = task.getSourceDataSourceId().toString();
        }
        return StringUtils.hasText(dataSourceId) ? "api:" + dataSourceId : "api";
    }

    private Map<String, Object> resourceStats(ApiIngestionResult result, String resourceId) {
        if (result == null || result.metrics() == null || !StringUtils.hasText(resourceId)) {
            return Map.of();
        }
        Object rawStats = result.metrics().get("resourceStats");
        if (!(rawStats instanceof Iterable<?> stats)) {
            return Map.of();
        }
        for (Object item : stats) {
            Map<String, Object> stat = safeMap(item);
            if (resourceId.equals(text(stat.get("resourceId")))) {
                return stat;
            }
        }
        return Map.of();
    }

    private void putLong(ObjectNode node, String field, Object value) {
        Long resolved = longObject(value);
        if (resolved != null) {
            node.put(field, resolved);
        }
    }

    private void putText(ObjectNode node, String field, Object value) {
        String resolved = text(value);
        if (StringUtils.hasText(resolved)) {
            node.put(field, resolved);
        }
    }

    private static long longValue(Object value) {
        Long resolved = longObject(value);
        return resolved == null ? 0L : resolved;
    }

    private static Long longObject(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private static String text(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : String.valueOf(value).trim();
    }
}
