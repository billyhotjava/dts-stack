package com.yuzhi.dts.platform.service.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import com.yuzhi.dts.platform.repository.ops.OpsBackfillRequestRepository;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final List<String> RUNNING_STATUSES = List.of("RUNNING", "SUBMITTED", "QUEUED");

    private final InfraExternalRunLogRepository runLogRepository;
    private final GovQualityRunRepository qualityRunRepository;
    private final OpsBackfillRequestRepository backfillRepository;
    private final AirflowClient airflowClient;
    private final ObjectMapper objectMapper;

    public OpsService(
        InfraExternalRunLogRepository runLogRepository,
        GovQualityRunRepository qualityRunRepository,
        OpsBackfillRequestRepository backfillRepository,
        AirflowClient airflowClient,
        ObjectMapper objectMapper
    ) {
        this.runLogRepository = runLogRepository;
        this.qualityRunRepository = qualityRunRepository;
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

        Optional<Map<String, Object>> triggered = airflowClient.triggerDag(dagId, Map.of("conf", payload));
        if (triggered.isPresent()) {
            request.setStatus("RUNNING");
            Object runId = triggered.get().get("dag_run_id");
            if (runId == null) {
                runId = triggered.get().get("run_id");
            }
            if (runId != null) {
                request.setExternalRunId(String.valueOf(runId));
            }
        } else {
            request.setStatus("PENDING");
            request.setMessage("Airflow 未启用或触发失败");
        }
        return backfillRepository.save(request);
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
        if (value == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(keyword);
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
}
