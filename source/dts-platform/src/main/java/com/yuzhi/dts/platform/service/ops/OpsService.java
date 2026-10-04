package com.yuzhi.dts.platform.service.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.repository.ops.OpsBackfillRequestRepository;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort.WarehousePlanProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class OpsService {

    private static final Logger LOG = LoggerFactory.getLogger(OpsService.class);
    private static final List<String> SUCCESS_STATUSES = List.of("SUCCESS", "SUCCEEDED", "FINISHED");
    private static final List<String> FAILED_STATUSES = List.of("FAILED", "FAIL", "ERROR", "TIMEOUT", "KILLED", "CANCELLED");
    private static final List<String> RUNNING_STATUSES = List.of("RUNNING", "SUBMITTED", "QUEUED");
    private static final ZoneId METRICS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InfraExternalRunLogRepository runLogRepository;
    private final GovQualityRunRepository qualityRunRepository;
    private final WarehousePlanOperationsReadPort warehousePlanReadPort;
    private final OpsBackfillRequestRepository backfillRepository;
    private final AirflowClient airflowClient;
    private final ObjectMapper objectMapper;

    public OpsService(
        InfraExternalRunLogRepository runLogRepository,
        GovQualityRunRepository qualityRunRepository,
        WarehousePlanOperationsReadPort warehousePlanReadPort,
        OpsBackfillRequestRepository backfillRepository,
        AirflowClient airflowClient,
        ObjectMapper objectMapper
    ) {
        this.runLogRepository = runLogRepository;
        this.qualityRunRepository = qualityRunRepository;
        this.warehousePlanReadPort = warehousePlanReadPort;
        this.backfillRepository = backfillRepository;
        this.airflowClient = airflowClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> overview() {
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        long totalRuns = runLogRepository.countByFinishedAtGreaterThanEqual(since);
        long successRuns = runLogRepository.countByFinishedAtGreaterThanEqualAndStatusIn(since, SUCCESS_STATUSES);
        long runningRuns = runLogRepository.countByStatusInAndFinishedAtIsNull(RUNNING_STATUSES);

        long qualityFailures = qualityRunRepository.findTop100ByStatusOrderByCreatedDateDesc("FAILED").size();
        long alertCount = Math.max(0, qualityFailures);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("totalRuns", totalRuns);
        data.put("successRate", totalRuns > 0 ? roundRate(successRuns * 1.0 / totalRuns) : null);
        data.put("alerts", alertCount);
        data.put("running", runningRuns);
        return data;
    }

    public Map<String, Object> devCenterMetrics(
        Integer days,
        String entryKey,
        String ownerDept,
        UUID artifactId,
        String artifactName,
        UUID planId
    ) {
        int windowDays = days == null ? 7 : Math.max(1, Math.min(days, 30));
        Instant now = Instant.now();
        Instant since = now.minus(windowDays, ChronoUnit.DAYS);
        String normalizedEntry = normalize(entryKey);
        String normalizedOwnerDept = normalize(ownerDept);
        String normalizedArtifactName = normalize(artifactName);
        UUID normalizedPlanId = planId;
        List<InfraExternalRunLog> runs = runLogRepository.findForMetrics(
            since,
            now,
            normalizedEntry,
            normalizedOwnerDept,
            artifactId,
            normalizedArtifactName
        );

        List<WarehousePlanProjection> plans = warehousePlanReadPort.listPlans();
        Map<UUID, WarehousePlanProjection> planById = new HashMap<>();
        for (WarehousePlanProjection plan : plans) {
            if (plan != null && plan.id() != null) {
                planById.put(plan.id(), plan);
            }
        }

        Map<LocalDate, DailyMetric> dailyMetrics = new LinkedHashMap<>();
        Map<String, FailureAggregate> failureAggregates = new HashMap<>();
        Set<String> ownerDeptOptions = new HashSet<>();
        Map<UUID, Long> planRunCounter = new HashMap<>();
        long totalRuns = 0L;
        long successRuns = 0L;
        long failedRuns = 0L;
        long runningRuns = 0L;
        long releaseRuns = 0L;
        long releaseSuccessRuns = 0L;
        long rollbackRuns = 0L;
        long rollbackStructuredRuns = 0L;
        long rollbackKeywordRuns = 0L;

        for (InfraExternalRunLog run : runs) {
            Instant eventAt = safeInstant(run.getStartedAt(), run.getFinishedAt());
            if (eventAt == null) {
                continue;
            }
            Map<String, Object> metrics = parseMetrics(run.getMetricsJson());
            UUID resolvedPlanId = resolvePlanId(run, metrics, planById);
            if (resolvedPlanId != null) {
                planRunCounter.merge(resolvedPlanId, 1L, Long::sum);
            }
            if (normalizedPlanId != null && !normalizedPlanId.equals(resolvedPlanId)) {
                continue;
            }

            LocalDate date = LocalDate.ofInstant(eventAt, METRICS_ZONE);
            DailyMetric daily = dailyMetrics.computeIfAbsent(date, ignored -> new DailyMetric());
            daily.totalRuns++;
            totalRuns++;
            if (StringUtils.hasText(run.getOwnerDept())) {
                ownerDeptOptions.add(run.getOwnerDept());
            }

            String status = normalizeStatus(run.getStatus());
            if (isSuccess(status)) {
                daily.successRuns++;
                successRuns++;
            } else if (isFailed(status)) {
                daily.failedRuns++;
                failedRuns++;
            } else if (isRunning(status)) {
                daily.runningRuns++;
                runningRuns++;
            }

            if (run.getDurationMs() != null && run.getDurationMs() >= 0) {
                daily.durationCount++;
                daily.durationMsTotal += run.getDurationMs();
            }

            WarehousePlanProjection resolvedPlan = resolvedPlanId == null ? null : planById.get(resolvedPlanId);
            String aggregateKey =
                normalize(run.getEntryKey()) + "|" + safeText(run.getArtifactName()) + "|" + safeText(resolvedPlanId);
            FailureAggregate aggregate = failureAggregates.computeIfAbsent(
                aggregateKey,
                ignored -> new FailureAggregate(run, resolvedPlanId, resolvedPlan == null ? null : resolvedPlan.name())
            );
            aggregate.totalRuns++;
            if (isFailed(status)) {
                aggregate.failedRuns++;
            }

            if (isReleaseRun(run, metrics)) {
                releaseRuns++;
                if (isSuccess(status)) {
                    releaseSuccessRuns++;
                }
                RollbackMark rollbackMark = detectRollback(run, metrics);
                if (rollbackMark.rollback()) {
                    rollbackRuns++;
                    if (rollbackMark.isStructured()) {
                        rollbackStructuredRuns++;
                    } else {
                        rollbackKeywordRuns++;
                    }
                }
            }
        }

        List<InfraExternalRunLog> effectiveRuns = filterRunsByPlan(runs, normalizedPlanId, planById);
        double mttrMinutes = computeMttrMinutes(effectiveRuns);
        long retryRuns = computeRetryRuns(effectiveRuns);
        List<Map<String, Object>> trend = buildTrend(dailyMetrics);
        List<Map<String, Object>> topFailures = buildTopFailures(failureAggregates);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", totalRuns);
        summary.put("successRuns", successRuns);
        summary.put("failedRuns", failedRuns);
        summary.put("runningRuns", runningRuns);
        summary.put("successRate", totalRuns > 0 ? roundRate(successRuns * 1.0 / totalRuns) : null);
        summary.put("failureRate", totalRuns > 0 ? roundRate(failedRuns * 1.0 / totalRuns) : null);
        summary.put("retryRuns", retryRuns);
        summary.put("retryRate", totalRuns > 0 ? roundRate(retryRuns * 1.0 / totalRuns) : null);
        summary.put("mttrMinutes", mttrMinutes > 0 ? roundOne(mttrMinutes) : null);
        summary.put("releaseRuns", releaseRuns);
        summary.put("releaseSuccessRate", releaseRuns > 0 ? roundRate(releaseSuccessRuns * 1.0 / releaseRuns) : null);
        summary.put("rollbackRate", releaseRuns > 0 ? roundRate(rollbackRuns * 1.0 / releaseRuns) : null);
        summary.put("rollbackStructuredRuns", rollbackStructuredRuns);
        summary.put("rollbackKeywordRuns", rollbackKeywordRuns);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("entryKey", normalizedEntry);
        filters.put("ownerDept", normalizedOwnerDept);
        filters.put("artifactId", artifactId);
        filters.put("artifactName", normalizedArtifactName);
        filters.put("planId", normalizedPlanId);
        filters.put("windowDays", windowDays);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", now.toString());
        data.put("summary", summary);
        data.put("trend", trend);
        data.put("topFailures", topFailures);
        data.put("availableOwnerDepts", ownerDeptOptions.stream().sorted(String::compareToIgnoreCase).toList());
        data.put("availablePlans", buildPlanOptions(plans, planRunCounter, normalizedPlanId));
        data.put("filters", filters);
        return data;
    }

    public List<Map<String, Object>> listInstances(String entryKey, String status, String keyword, int limit) {
        List<InfraExternalRunLog> runs = runLogRepository.findTop200ByOrderByStartedAtDesc();
        List<Map<String, Object>> results = new ArrayList<>();
        String normalizedEntry = normalize(entryKey);
        String normalizedStatus = normalize(status);
        String normalizedKeyword = normalize(keyword);
        int cap = Math.max(1, Math.min(limit, 200));
        for (InfraExternalRunLog run : runs) {
            if (normalizedEntry != null && !normalizedEntry.equalsIgnoreCase(normalize(run.getEntryKey()))) {
                continue;
            }
            if (normalizedStatus != null && !normalizedStatus.equalsIgnoreCase(normalize(run.getStatus()))) {
                continue;
            }
            if (normalizedKeyword != null && !matches(run, normalizedKeyword)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", run.getId());
            row.put("entryKey", run.getEntryKey());
            row.put("artifactId", run.getArtifactId());
            row.put("artifactType", run.getArtifactType());
            row.put("artifactName", run.getArtifactName());
            row.put("externalRunId", run.getExternalRunId());
            row.put("externalUrl", run.getExternalUrl());
            row.put("status", run.getStatus());
            row.put("startedAt", run.getStartedAt());
            row.put("finishedAt", run.getFinishedAt());
            row.put("durationMs", run.getDurationMs());
            row.put("message", run.getMessage());
            Map<String, Object> metrics = parseMetrics(run.getMetricsJson());
            row.put("logPath", metrics.get("logPath"));
            row.put("dagId", metrics.get("dagId"));
            results.add(row);
            if (results.size() >= cap) break;
        }
        return results;
    }

    public List<Map<String, Object>> listAlerts(int limit) {
        int cap = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> alerts = new ArrayList<>();

        List<GovQualityRun> failedRuns = qualityRunRepository.findTop100ByStatusOrderByCreatedDateDesc("FAILED");
        for (GovQualityRun run : failedRuns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "QUALITY");
            row.put("status", run.getStatus());
            row.put("severity", run.getSeverity());
            row.put("datasetId", run.getDatasetId());
            row.put("ruleId", run.getRule() != null ? run.getRule().getId() : null);
            row.put("ruleName", run.getRule() != null ? run.getRule().getName() : null);
            row.put("message", run.getMessage());
            row.put("createdAt", run.getCreatedDate());
            alerts.add(row);
            if (alerts.size() >= cap) break;
        }
        return alerts;
    }

    public List<OpsBackfillRequest> listBackfills() {
        return backfillRepository.findTop200ByOrderByCreatedDateDesc();
    }

    @Transactional
    public OpsBackfillRequest createBackfill(String user, String dagId, LocalDate dateFrom, LocalDate dateTo, String note) {
        OpsBackfillRequest request = new OpsBackfillRequest();
        request.setDagId(dagId);
        request.setName(note);
        request.setDateFrom(dateFrom);
        request.setDateTo(dateTo);
        request.setStatus("SUBMITTED");
        request.setTriggeredAt(Instant.now());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dateFrom", dateFrom != null ? dateFrom.toString() : null);
        payload.put("dateTo", dateTo != null ? dateTo.toString() : null);
        payload.put("requestedBy", user);
        try {
            request.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            LOG.warn("Failed to serialize backfill payload: {}", ex.getMessage());
        }

        try {
            Optional<Map<String, Object>> triggered = airflowClient.triggerDag(dagId, Map.of("conf", payload));
            if (triggered.isPresent()) {
                request.setStatus("RUNNING");
                Object runId = triggered.orElseThrow().get("dag_run_id");
                if (runId == null) {
                    runId = triggered.orElseThrow().get("run_id");
                }
                if (runId != null) {
                    request.setExternalRunId(String.valueOf(runId));
                }
            } else {
                request.setStatus("PENDING");
                request.setMessage("Airflow 未启用或触发失败");
            }
        } catch (RuntimeException ex) {
            LOG.warn("Backfill trigger failed for dagId={}: {}", dagId, ex.getMessage());
            request.setStatus("FAILED");
            request.setMessage("Airflow 触发失败: " + ex.getMessage());
        }
        return backfillRepository.save(request);
    }

    private List<Map<String, Object>> buildPlanOptions(
        List<WarehousePlanProjection> plans,
        Map<UUID, Long> planRunCounter,
        UUID selectedPlanId
    ) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        return plans
            .stream()
            .filter(plan -> plan != null && plan.id() != null)
            .sorted(Comparator.comparing(plan -> safeText(plan.name()), String.CASE_INSENSITIVE_ORDER))
            .filter(
                plan ->
                    planRunCounter.getOrDefault(plan.id(), 0L) > 0 ||
                    (selectedPlanId != null && selectedPlanId.equals(plan.id()))
            )
            .map(plan -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", plan.id());
                row.put("name", plan.name());
                row.put("ownerDept", plan.ownerDepartmentId());
                row.put("status", plan.lifecycleStatus());
                row.put("runCount", planRunCounter.getOrDefault(plan.id(), 0L));
                return row;
            })
            .toList();
    }

    private List<InfraExternalRunLog> filterRunsByPlan(
        List<InfraExternalRunLog> runs,
        UUID planId,
        Map<UUID, WarehousePlanProjection> planById
    ) {
        if (planId == null) {
            return runs;
        }
        List<InfraExternalRunLog> filtered = new ArrayList<>();
        for (InfraExternalRunLog run : runs) {
            UUID resolvedPlanId = resolvePlanId(run, parseMetrics(run.getMetricsJson()), planById);
            if (planId.equals(resolvedPlanId)) {
                filtered.add(run);
            }
        }
        return filtered;
    }

    private UUID resolvePlanId(
        InfraExternalRunLog run,
        Map<String, Object> metrics,
        Map<UUID, WarehousePlanProjection> planById
    ) {
        if (run != null && run.getArtifactId() != null && planById.containsKey(run.getArtifactId())) {
            return run.getArtifactId();
        }
        Map<String, Object> conf = toMap(metrics == null ? null : metrics.get("conf"));
        UUID fromConf = toUuid(conf.get("planId"));
        if (fromConf == null) {
            fromConf = toUuid(conf.get("projectId"));
        }
        if (fromConf != null && planById.containsKey(fromConf)) {
            return fromConf;
        }
        Map<String, Object> runInfo = toMap(metrics == null ? null : metrics.get("run"));
        UUID fromRun = toUuid(runInfo.get("planId"));
        if (fromRun == null) {
            fromRun = toUuid(runInfo.get("projectId"));
        }
        if (fromRun != null && planById.containsKey(fromRun)) {
            return fromRun;
        }
        return null;
    }

    private RollbackMark detectRollback(InfraExternalRunLog run, Map<String, Object> metrics) {
        Map<String, Object> conf = toMap(metrics == null ? null : metrics.get("conf"));
        if (!conf.isEmpty()) {
            if (toBoolean(conf.get("rollback")) || toBoolean(conf.get("isRollback"))) {
                return RollbackMark.structured();
            }
            String operation = safeText(conf.get("operation"));
            if ("rollback".equalsIgnoreCase(operation) || "revert".equalsIgnoreCase(operation) || "restore".equalsIgnoreCase(operation)) {
                return RollbackMark.structured();
            }
            String command = safeText(conf.get("command"));
            if (contains(command, "rollback") || contains(command, "revert")) {
                return RollbackMark.structured();
            }
        }
        Map<String, Object> result = toMap(metrics == null ? null : metrics.get("result"));
        if (!result.isEmpty()) {
            if (toBoolean(result.get("rollback")) || toBoolean(result.get("isRollback"))) {
                return RollbackMark.structured();
            }
        }
        if (run != null && (contains(run.getMessage(), "rollback") || contains(run.getMetricsJson(), "rollback"))) {
            return RollbackMark.keyword();
        }
        return RollbackMark.none();
    }

    private List<Map<String, Object>> buildTrend(Map<LocalDate, DailyMetric> dailyMetrics) {
        List<Map<String, Object>> rows = new ArrayList<>();
        dailyMetrics
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                LocalDate date = entry.getKey();
                DailyMetric metric = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", date.toString());
                row.put("totalRuns", metric.totalRuns);
                row.put("successRuns", metric.successRuns);
                row.put("failedRuns", metric.failedRuns);
                row.put("runningRuns", metric.runningRuns);
                row.put("successRate", metric.totalRuns > 0 ? roundRate(metric.successRuns * 1.0 / metric.totalRuns) : null);
                row.put("avgDurationMs", metric.durationCount > 0 ? Math.round(metric.durationMsTotal * 1.0 / metric.durationCount) : null);
                rows.add(row);
            });
        return rows;
    }

    private List<Map<String, Object>> buildTopFailures(Map<String, FailureAggregate> failureAggregates) {
        return failureAggregates
            .values()
            .stream()
            .filter(item -> item.failedRuns > 0)
            .sorted(
                Comparator.comparingLong(FailureAggregate::failedRuns)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(FailureAggregate::totalRuns).reversed())
            )
            .limit(10)
            .map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("entryKey", item.entryKey);
                row.put("artifactName", item.artifactName);
                row.put("artifactId", item.artifactId);
                row.put("planId", item.planId);
                row.put("planName", item.planName);
                row.put("totalRuns", item.totalRuns);
                row.put("failedRuns", item.failedRuns);
                row.put("failureRate", item.totalRuns > 0 ? roundRate(item.failedRuns * 1.0 / item.totalRuns) : null);
                return row;
            })
            .toList();
    }

    private long computeRetryRuns(List<InfraExternalRunLog> runs) {
        Map<String, Long> attempts = new HashMap<>();
        for (InfraExternalRunLog run : runs) {
            Instant eventAt = safeInstant(run.getStartedAt(), run.getFinishedAt());
            if (eventAt == null) {
                continue;
            }
            LocalDate date = LocalDate.ofInstant(eventAt, METRICS_ZONE);
            String key =
                date + "|" + normalize(run.getEntryKey()) + "|" + safeText(run.getArtifactName()) + "|" + safeText(run.getArtifactId());
            attempts.merge(key, 1L, Long::sum);
        }
        long retries = 0L;
        for (Long count : attempts.values()) {
            if (count != null && count > 1) {
                retries += count - 1;
            }
        }
        return retries;
    }

    private double computeMttrMinutes(List<InfraExternalRunLog> runs) {
        Map<String, List<InfraExternalRunLog>> groups = new HashMap<>();
        for (InfraExternalRunLog run : runs) {
            String key = normalize(run.getEntryKey()) + "|" + safeText(run.getArtifactName()) + "|" + safeText(run.getArtifactId());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(run);
        }
        long recoveredCount = 0L;
        double totalMinutes = 0D;
        for (List<InfraExternalRunLog> groupRuns : groups.values()) {
            groupRuns.sort(
                Comparator.comparing(run -> safeInstant(run.getStartedAt(), run.getFinishedAt()), Comparator.nullsLast(Comparator.naturalOrder()))
            );
            ArrayDeque<Instant> failedQueue = new ArrayDeque<>();
            for (InfraExternalRunLog run : groupRuns) {
                Instant eventAt = safeInstant(run.getFinishedAt(), run.getStartedAt());
                if (eventAt == null) {
                    continue;
                }
                String status = normalizeStatus(run.getStatus());
                if (isFailed(status)) {
                    failedQueue.addLast(eventAt);
                    continue;
                }
                if (!isSuccess(status) || failedQueue.isEmpty()) {
                    continue;
                }
                Instant failedAt = failedQueue.pollFirst();
                if (failedAt != null && !eventAt.isBefore(failedAt)) {
                    totalMinutes += (eventAt.toEpochMilli() - failedAt.toEpochMilli()) / 60000.0;
                    recoveredCount++;
                }
            }
        }
        if (recoveredCount <= 0) {
            return 0D;
        }
        return totalMinutes / recoveredCount;
    }

    private Instant safeInstant(Instant first, Instant second) {
        if (first != null) {
            return first;
        }
        return second;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSuccess(String status) {
        return SUCCESS_STATUSES.contains(status);
    }

    private boolean isFailed(String status) {
        return FAILED_STATUSES.contains(status);
    }

    private boolean isRunning(String status) {
        return RUNNING_STATUSES.contains(status);
    }

    private boolean isReleaseRun(InfraExternalRunLog run, Map<String, Object> metrics) {
        if (run != null && ExternalRunLogService.ENTRY_DBT.equalsIgnoreCase(safeText(run.getEntryKey()))) {
            return true;
        }
        Map<String, Object> conf = toMap(metrics == null ? null : metrics.get("conf"));
        String operation = safeText(conf.get("operation"));
        return "run".equalsIgnoreCase(operation) || "build".equalsIgnoreCase(operation);
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(InfraExternalRunLog run, String keyword) {
        if (run == null || keyword == null) return false;
        String kw = keyword.toLowerCase(Locale.ROOT);
        return contains(run.getArtifactName(), kw)
            || contains(run.getExternalRunId(), kw)
            || contains(run.getMessage(), kw)
            || contains(run.getEntryKey(), kw);
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return map;
        }
        return Map.of();
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private Map<String, Object> parseMetrics(String metricsJson) {
        if (!StringUtils.hasText(metricsJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metricsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private double roundRate(double rate) {
        return Math.round(rate * 1000.0) / 10.0;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class DailyMetric {

        private long totalRuns;
        private long successRuns;
        private long failedRuns;
        private long runningRuns;
        private long durationCount;
        private long durationMsTotal;
    }

    private static final class FailureAggregate {

        private final String entryKey;
        private final String artifactName;
        private final UUID artifactId;
        private final UUID planId;
        private final String planName;
        private long totalRuns;
        private long failedRuns;

        private FailureAggregate(InfraExternalRunLog run, UUID planId, String planName) {
            this.entryKey = run == null ? null : run.getEntryKey();
            this.artifactName = run == null ? null : run.getArtifactName();
            this.artifactId = run == null ? null : run.getArtifactId();
            this.planId = planId;
            this.planName = planName;
        }

        private long totalRuns() {
            return totalRuns;
        }

        private long failedRuns() {
            return failedRuns;
        }
    }

    private static final class RollbackMark {

        private final boolean rollback;
        private final boolean structured;

        private RollbackMark(boolean rollback, boolean structured) {
            this.rollback = rollback;
            this.structured = structured;
        }

        private static RollbackMark structured() {
            return new RollbackMark(true, true);
        }

        private static RollbackMark keyword() {
            return new RollbackMark(true, false);
        }

        private static RollbackMark none() {
            return new RollbackMark(false, false);
        }

        private boolean rollback() {
            return rollback;
        }

        private boolean isStructured() {
            return structured;
        }
    }
}
