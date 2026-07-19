package com.yuzhi.dts.platform.service.sprint27;

import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventDto;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventSummaryDto;
import com.yuzhi.dts.platform.service.governance.GovernanceOpsMetricsService;
import com.yuzhi.dts.platform.service.governance.IndicatorObservabilityService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class Sprint27ConsoleService {

    private static final Map<String, Object> EMPTY_INGESTION_OBSERVABILITY = Map.ofEntries(
        Map.entry("total", 0),
        Map.entry("success", 0),
        Map.entry("failed", 0),
        Map.entry("running", 0),
        Map.entry("terminal", 0),
        Map.entry("timeout", 0),
        Map.entry("successRate", 0),
        Map.entry("timeoutRate", 0),
        Map.entry("avgDurationSeconds", 0),
        Map.entry("mttrSeconds", 0),
        Map.entry("failureTop", List.of()),
        Map.entry("trend", List.of())
    );
    private static final Map<String, Object> EMPTY_INGESTION_GOVERNANCE = Map.of(
        "running", 0,
        "preparing", 0,
        "queueLength", 0,
        "blockedByPolicy", 0,
        "avgExecutionSeconds", 0,
        "avgQueueWaitSeconds", 0,
        "maxQueueWaitSeconds", 0,
        "sourceLoads", List.of(),
        "projectLoads", List.of()
    );

    private final IngestionServiceClient ingestionClient;
    private final IndicatorObservabilityService indicatorObservabilityService;
    private final ModelSpecApplicationService modelSpecApplicationService;
    private final PlatformEventOutboxService eventOutboxService;
    private final GovernanceOpsMetricsService governanceOpsMetricsService;
    private final DbtReleaseGateService dbtReleaseGateService;
    private final String defaultTenantId;

    public Sprint27ConsoleService(
        IngestionServiceClient ingestionClient,
        IndicatorObservabilityService indicatorObservabilityService,
        ModelSpecApplicationService modelSpecApplicationService,
        PlatformEventOutboxService eventOutboxService,
        GovernanceOpsMetricsService governanceOpsMetricsService,
        DbtReleaseGateService dbtReleaseGateService,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String defaultTenantId
    ) {
        this.ingestionClient = ingestionClient;
        this.indicatorObservabilityService = indicatorObservabilityService;
        this.modelSpecApplicationService = modelSpecApplicationService;
        this.eventOutboxService = eventOutboxService;
        this.governanceOpsMetricsService = governanceOpsMetricsService;
        this.dbtReleaseGateService = dbtReleaseGateService;
        this.defaultTenantId = defaultTenantId;
    }

    public Map<String, Object> eltConsole(int days, int hours) {
        SourceResult observability = readIngestion("ingestionObservability", ingestionClient.getExecutionsObservability(Map.of("days", days)));
        SourceResult governance = readIngestion("ingestionGovernance", ingestionClient.getGovernanceOverview(Map.of("hours", hours)));
        Map<String, Object> observabilityData = mapOrDefault(observability.data(), EMPTY_INGESTION_OBSERVABILITY);
        Map<String, Object> governanceData = mapOrDefault(governance.data(), EMPTY_INGESTION_GOVERNANCE);

        List<Map<String, Object>> stages = List.of(
            stage("source", "数据接入", number(governanceData.get("running")) > 0 ? "processing" : "success", number(governanceData.get("running")), "/explore/etl/transform"),
            stage("queue", "队列调度", number(governanceData.get("queueLength")) > 0 ? "warning" : "success", number(governanceData.get("queueLength")), "/explore/etl/orchestration"),
            stage("transform", "加工转换", number(observabilityData.get("running")) > 0 ? "processing" : "success", number(observabilityData.get("running")), "/explore/etl/transform"),
            stage("quality", "质量校验", number(governanceData.get("blockedByPolicy")) > 0 ? "warning" : "success", number(governanceData.get("blockedByPolicy")), "/governance/quality"),
            stage("lineage", "血缘影响", "default", number(observabilityData.get("terminal")), "/catalog/lineage/impact")
        );

        List<Map<String, Object>> chainItems = List.of(
            chain("task", "采集任务", "接入", "dts-ingestion", number(governanceData.get("running")) > 0 ? "processing" : "success", "/explore/etl/transform"),
            chain("model", "转换模型", "加工", "dts-platform", number(observabilityData.get("failed")) > 0 ? "warning" : "success", "/modeling/dbt-files"),
            chain("metric", "指标口径", "消费", "dts-platform", "success", "/metrics/center"),
            chain("bi", "分析看板", "发布", "dts-analytics", "success", "/bi/project-cockpit")
        );

        return payload(
            "sources", Map.of("observability", observability.status(), "governance", governance.status()),
            "observability", observabilityData,
            "governance", governanceData,
            "stages", stages,
            "chainItems", chainItems
        );
    }

    public Map<String, Object> metricOperations(int hours, int bucketHours, String activeDept) {
        SourceResult overview = capture("indicatorOverview", () -> indicatorObservabilityService.overview(hours, activeDept));
        SourceResult trend = capture("indicatorTrend", () -> indicatorObservabilityService.trend(hours, bucketHours, activeDept));
        SourceResult models = capture("canonicalModelSpecs", () -> modelSpecApplicationService.list(defaultTenantId, null, null, null, null));
        List<ModelSpecView> modelList = models.data() instanceof List<?> values
            ? values.stream().filter(ModelSpecView.class::isInstance).map(ModelSpecView.class::cast).toList()
            : List.of();
        List<Map<String, Object>> domains = modelList
            .stream()
            .filter(model -> model.domainId() != null)
            .collect(java.util.stream.Collectors.toMap(
                ModelSpecView::domainId,
                model -> Map.<String, Object>of("domainId", model.domainId(), "source", "MODEL_SPEC"),
                (left, right) -> left,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
        List<Map<String, Object>> metrics = modelList
            .stream()
            .flatMap(model -> model.metricRefs().stream().map(metric -> Map.<String, Object>of(
                "metricId", metric.metricId(),
                "version", metric.version(),
                "modelSpecId", model.id(),
                "modelName", model.name(),
                "modelType", model.modelType().name(),
                "status", model.status().name()
            )))
            .toList();
        List<Object> runs = List.of();
        return payload(
            "sources",
            Map.of(
                "overview", overview.status(),
                "trend", trend.status(),
                "domains", sourceStatus(domains.isEmpty() ? "EMPTY" : "READY", "modeling_model_spec.domain_id", "canonical projection"),
                "metrics", sourceStatus(metrics.isEmpty() ? "EMPTY" : "READY", "modeling_model_spec.metric_refs", "canonical projection"),
                "models", models.status()
            ),
            "overview", overview.dataOr(Map.of()),
            "trendRows", trend.dataOr(List.of()),
            "domains", domains,
            "metrics", metrics,
            "models", modelList,
            "runs", runs
        );
    }

    public Map<String, Object> eventsConsole(String domain, String eventType, String status, String dispatchStatus, int page, int size) {
        PlatformEventSummaryDto summary = eventOutboxService.summarize();
        Page<PlatformEventDto> events = eventOutboxService.list(
            domain,
            eventType,
            null,
            null,
            status,
            dispatchStatus,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("occurredAt").descending())
        );
        return payload("summary", summary, "page", page(events));
    }

    public Map<String, Object> auditEvidence() {
        Page<PlatformEventDto> events = eventOutboxService.list(
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 100, Sort.by("occurredAt").descending())
        );
        List<PlatformEventDto> content = events.getContent();
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        long auditBound = 0;
        long failed = 0;
        Map<String, Long> domains = new LinkedHashMap<>();
        for (PlatformEventDto event : content) {
            if (hasText(event.auditActionCode())) auditBound++;
            if (SetLike.failed(event.status())) failed++;
            String domain = fallback(event.domain(), "UNKNOWN");
            domains.merge(domain, 1L, Long::sum);
            String action = fallback(event.action(), fallback(event.eventType(), "UNKNOWN"));
            String auditCode = fallback(event.auditActionCode(), "未绑定审计动作");
            String key = domain + ":" + action + ":" + auditCode;
            Map<String, Object> row = grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> next = new LinkedHashMap<>();
                next.put("key", key);
                next.put("domain", domain);
                next.put("action", action);
                next.put("auditActionCode", auditCode);
                next.put("count", 0);
                return next;
            });
            row.put("count", number(row.get("count")) + 1);
            if (!hasText(String.valueOf(row.get("lastOccurredAt"))) || compareInstant(event.occurredAt(), row.get("lastOccurredAt")) > 0) {
                row.put("lastOccurredAt", event.occurredAt());
                row.put("lastEventId", event.eventId());
                row.put("status", event.status());
                row.put("dispatchStatus", event.dispatchStatus());
            }
        }
        return payload(
            "sources", Map.of("events", sourceStatus("READY", "platform_event_outbox", content.isEmpty() ? "empty" : "ready")),
            "events", content,
            "rows", grouped.values(),
            "summary", Map.of("total", content.size(), "auditBound", auditBound, "failed", failed, "domains", domains)
        );
    }

    public Map<String, Object> releaseGovernance(String activeDept) {
        SourceResult governance = capture("governanceReleaseGate", () -> governanceReleaseGate(7, activeDept));
        SourceResult indicator = capture("indicatorOverview", () -> indicatorObservabilityService.overview(168, activeDept));
        SourceResult ingestion = readIngestion("ingestionObservability", ingestionClient.getExecutionsObservability(Map.of("days", 7)));
        SourceResult events = capture("eventSummary", eventOutboxService::summarize);
        SourceResult dbt = capture("dbtReleaseGate", () -> dbtReleaseGateService.evaluate(null, null, null, null));

        Map<String, Object> indicatorData = mapOrDefault(indicator.data(), Map.of());
        Map<String, Object> ingestionData = mapOrDefault(ingestion.data(), EMPTY_INGESTION_OBSERVABILITY);
        PlatformEventSummaryDto eventSummary = events.data() instanceof PlatformEventSummaryDto dto ? dto : null;
        Map<String, Object> governanceData = mapOrDefault(governance.data(), Map.of());

        long blockerFailed = number(governanceData.get("blockerFailed"));
        long eventFailed = eventSummary == null ? 0 : eventSummary.failed();
        long ingestionFailed = number(ingestionData.get("failed")) + number(ingestionData.get("timeout"));
        long indicatorFailed = number(readNested(indicatorData, "validation", "failed"));
        boolean dbtBlocked = dbt.data() instanceof DbtReleaseGateService.DbtReleaseGateResult result && result.blocking();
        boolean sourceError = List.of(governance, indicator, ingestion, events, dbt).stream().anyMatch(SourceResult::isError);
        boolean readyForRelease = !sourceError && blockerFailed == 0 && eventFailed == 0 && ingestionFailed == 0 && indicatorFailed == 0 && !dbtBlocked;

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.addAll(listChecks(governanceData.get("checks")));
        checks.add(check("elt-execution", "ELT", "采集/转换执行异常", ingestionFailed == 0, ingestionFailed, "=0", "BLOCKER", "/explore/etl"));
        checks.add(check("metric-validation", "指标", "指标校验失败", indicatorFailed == 0, indicatorFailed, "=0", "BLOCKER", "/metrics/operations"));
        checks.add(check("event-dispatch", "事件", "事件分发失败", eventFailed == 0, eventFailed, "=0", "WARN", "/ops/events"));
        checks.add(check("dbt-release-gate", "dbt", "dbt release gate", !dbtBlocked, dbt.data() == null ? "未返回" : "checked", "不阻断", "BLOCKER", "/modeling/dbt-files"));
        if (sourceError) {
            checks.add(check("source-status", "依赖", "依赖接口状态", false, "存在不可用依赖", "全部 READY/EMPTY", "BLOCKER", "/ops/release-governance"));
        }

        return payload(
            "sources",
            Map.of(
                "governance", governance.status(),
                "indicator", indicator.status(),
                "ingestion", ingestion.status(),
                "events", events.status(),
                "dbt", dbt.status()
            ),
            "readyForRelease", readyForRelease,
            "blockerFailed", blockerFailed,
            "eventFailed", eventFailed,
            "ingestionFailed", ingestionFailed,
            "indicatorFailed", indicatorFailed,
            "dbtBlocked", dbtBlocked,
            "checkedAt", Instant.now(),
            "governanceGate", governanceData,
            "indicatorOverview", indicatorData,
            "ingestionOverview", ingestionData,
            "eventSummary", eventSummary,
            "dbtGate", dbt.data(),
            "checks", checks
        );
    }

    private Map<String, Object> governanceReleaseGate(int days, String activeDept) {
        Map<String, Object> governance = governanceOpsMetricsService.overview(days);
        Number qualitySuccessRate = (Number) readNested(governance, "kpi", "qualitySuccessRate");
        Number issueOverdueRate = (Number) readNested(governance, "kpi", "issueOverdueRate");
        List<Map<String, Object>> checks = List.of(
            check("QUALITY_SUCCESS_RATE", "治理门禁", "质量成功率", qualitySuccessRate.doubleValue() >= 95, qualitySuccessRate, ">=95%", "BLOCKER", "/governance/quality"),
            check("ISSUE_OVERDUE_RATE", "治理门禁", "问题单逾期率", issueOverdueRate.doubleValue() <= 10, issueOverdueRate, "<=10%", "BLOCKER", "/governance")
        );
        long failed = checks.stream().filter(row -> !Boolean.TRUE.equals(row.get("passed"))).count();
        return payload("windowDays", days, "readyForRelease", failed == 0, "blockerFailed", failed, "checks", checks, "governanceOverview", governance, "checkedAt", Instant.now());
    }

    private SourceResult readIngestion(String name, ApiResponse<Object> response) {
        if (response == null) {
            return new SourceResult(sourceStatus("ERROR", name, "no-response"), null);
        }
        if (response.getStatus() == ResultStatus.SUCCESS.getCode()) {
            Object data = response.getData();
            return new SourceResult(sourceStatus(isEmpty(data) ? "EMPTY" : "READY", name, isEmpty(data) ? "empty" : "ready"), data);
        }
        return new SourceResult(sourceStatus("ERROR", name, response.getMessage()), null);
    }

    private SourceResult capture(String name, SupplierWithException<?> supplier) {
        try {
            Object data = supplier.get();
            return new SourceResult(sourceStatus(isEmpty(data) ? "EMPTY" : "READY", name, isEmpty(data) ? "empty" : "ready"), data);
        } catch (Exception ex) {
            return new SourceResult(sourceStatus("ERROR", name, ex.getMessage()), null);
        }
    }

    private Map<String, Object> sourceStatus(String status, String source, String message) {
        return payload("status", status, "source", source, "message", fallback(message, status), "checkedAt", Instant.now());
    }

    private Map<String, Object> page(Page<PlatformEventDto> result) {
        return payload("content", result.getContent(), "total", result.getTotalElements(), "page", result.getNumber(), "size", result.getSize(), "totalPages", result.getTotalPages());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listChecks(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                row.putIfAbsent("area", "治理门禁");
                row.putIfAbsent("path", "/governance");
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> stage(String key, String title, String status, long count, String path) {
        return payload("key", key, "title", title, "status", status, "count", count, "path", path);
    }

    private Map<String, Object> chain(String key, String asset, String stage, String owner, String status, String path) {
        return payload("key", key, "asset", asset, "stage", stage, "owner", owner, "status", status, "path", path);
    }

    private Map<String, Object> check(String key, String area, String name, boolean passed, Object actual, String threshold, String severity, String path) {
        return payload("key", key, "area", area, "name", name, "passed", passed, "actual", actual, "threshold", threshold, "severity", severity, "path", path);
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrDefault(Object value, Map<String, Object> fallback) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
            return result;
        }
        return fallback;
    }

    private List<?> listOrDefault(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Object readNested(Map<String, Object> map, String parent, String child) {
        Object nested = map.get(parent);
        if (nested instanceof Map<?, ?> nestedMap) {
            return nestedMap.get(child);
        }
        return 0;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private int compareInstant(Instant left, Object right) {
        if (left == null) return -1;
        if (right instanceof Instant instant) return left.compareTo(instant);
        try {
            return left.compareTo(Instant.parse(String.valueOf(right)));
        } catch (Exception ex) {
            return 1;
        }
    }

    private String readId(Object value) {
        if (value == null) return null;
        try {
            var method = value.getClass().getMethod("id");
            Object id = method.invoke(value);
            return id == null ? null : String.valueOf(id);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof List<?> list) return list.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record SourceResult(Map<String, Object> status, Object data) {
        Object dataOr(Object fallback) {
            return data == null ? fallback : data;
        }

        boolean isError() {
            return Objects.equals("ERROR", status.get("status"));
        }
    }

    private static final class SetLike {
        private static boolean failed(String value) {
            if (value == null) return false;
            String normalized = value.trim().toUpperCase();
            return normalized.equals("FAILED") || normalized.equals("ERROR") || normalized.equals("BLOCKED");
        }
    }
}
