package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovComplianceBatch;
import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovComplianceBatchRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueActionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GovernanceOpsMetricsService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 180;
    private static final Set<String> QUALITY_PASS = Set.of("SUCCESS", "PASSED", "COMPLETED");
    private static final Set<String> QUALITY_FAIL = Set.of("FAILED", "ERROR");
    private static final Set<String> COMPLIANCE_PASS = Set.of("SUCCESS", "COMPLETED", "PASSED");
    private static final Set<String> COMPLIANCE_FAIL = Set.of("FAILED", "ERROR");
    private static final Set<String> ISSUE_CLOSED = Set.of("CLOSED", "RESOLVED");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final GovQualityRunRepository qualityRunRepository;
    private final GovIssueTicketRepository issueTicketRepository;
    private final GovComplianceBatchRepository complianceBatchRepository;
    private final GovIssueActionRepository issueActionRepository;

    public GovernanceOpsMetricsService(
        GovQualityRunRepository qualityRunRepository,
        GovIssueTicketRepository issueTicketRepository,
        GovComplianceBatchRepository complianceBatchRepository,
        GovIssueActionRepository issueActionRepository
    ) {
        this.qualityRunRepository = qualityRunRepository;
        this.issueTicketRepository = issueTicketRepository;
        this.complianceBatchRepository = complianceBatchRepository;
        this.issueActionRepository = issueActionRepository;
    }

    public Map<String, Object> overview(int days) {
        int safeDays = normalizeDays(days);
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(safeDays));

        List<GovQualityRun> runs = qualityRunRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);
        List<GovIssueTicket> issues = issueTicketRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);
        List<GovComplianceBatch> batches = complianceBatchRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);
        List<GovIssueAction> actions = issueActionRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);

        long qualityTotal = runs.size();
        long qualitySuccess = runs.stream().filter(run -> QUALITY_PASS.contains(normalize(run.getStatus()))).count();
        long qualityFailed = runs.stream().filter(run -> QUALITY_FAIL.contains(normalize(run.getStatus()))).count();
        long qualityRunning = runs.stream().filter(run -> "RUNNING".equals(normalize(run.getStatus()))).count();

        long issueTotal = issues.size();
        long issueResolved = issues.stream().filter(issue -> issue.getResolvedAt() != null).count();
        long issueOpen = issues.stream().filter(issue -> !ISSUE_CLOSED.contains(normalize(issue.getStatus()))).count();
        long issueOverdue = issues
            .stream()
            .filter(issue -> !ISSUE_CLOSED.contains(normalize(issue.getStatus())))
            .filter(issue -> issue.getDueAt() != null && issue.getDueAt().isBefore(now))
            .count();
        double mttrHours = issues
            .stream()
            .filter(issue -> issue.getResolvedAt() != null && issue.getCreatedDate() != null)
            .mapToLong(issue -> Duration.between(issue.getCreatedDate(), issue.getResolvedAt()).toMinutes())
            .average()
            .orElse(0D) / 60D;

        long complianceTotal = batches.size();
        long complianceCompleted = batches.stream().filter(batch -> COMPLIANCE_PASS.contains(normalize(batch.getStatus()))).count();
        long complianceFailed = batches.stream().filter(batch -> COMPLIANCE_FAIL.contains(normalize(batch.getStatus()))).count();

        List<Map<String, Object>> failureTopN = topN(
            runs
                .stream()
                .filter(run -> QUALITY_FAIL.contains(normalize(run.getStatus())))
                .map(run -> classifyFailure(run.getStatus(), run.getMessage()))
                .toList(),
            5
        );
        List<Map<String, Object>> actionTopN = topN(actions.stream().map(action -> normalize(action.getActionType())).toList(), 5);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowDays", safeDays);
        payload.put("from", since);
        payload.put("to", now);
        payload.put(
            "kpi",
            Map.of(
                "qualitySuccessRate", percentage(qualitySuccess, qualityTotal),
                "issueMttrHours", scale(mttrHours),
                "issueOverdueRate", percentage(issueOverdue, issueOpen)
            )
        );
        payload.put(
            "quality",
            Map.of("total", qualityTotal, "success", qualitySuccess, "failed", qualityFailed, "running", qualityRunning)
        );
        payload.put(
            "issue",
            Map.of(
                "total",
                issueTotal,
                "resolved",
                issueResolved,
                "open",
                issueOpen,
                "overdue",
                issueOverdue,
                "overdueRate",
                percentage(issueOverdue, issueOpen)
            )
        );
        payload.put(
            "compliance",
            Map.of("total", complianceTotal, "completed", complianceCompleted, "failed", complianceFailed)
        );
        payload.put("failureTopN", failureTopN);
        payload.put("actionTopN", actionTopN);
        return payload;
    }

    public List<Map<String, Object>> trend(int days) {
        int safeDays = normalizeDays(days);
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(safeDays));

        List<GovQualityRun> runs = qualityRunRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);
        List<GovIssueTicket> issues = issueTicketRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);
        List<GovComplianceBatch> batches = complianceBatchRepository.findByCreatedDateAfterOrderByCreatedDateAsc(since);

        LocalDate fromDay = LocalDate.ofInstant(since, ZONE);
        LocalDate toDay = LocalDate.ofInstant(now, ZONE);
        Map<LocalDate, DayAgg> bucket = new LinkedHashMap<>();
        LocalDate cursor = fromDay;
        while (!cursor.isAfter(toDay)) {
            bucket.put(cursor, new DayAgg());
            cursor = cursor.plusDays(1);
        }

        for (GovQualityRun run : runs) {
            LocalDate day = dayOf(run.getCreatedDate());
            DayAgg agg = bucket.get(day);
            if (agg == null) continue;
            agg.runTotal++;
            if (QUALITY_PASS.contains(normalize(run.getStatus()))) {
                agg.runSuccess++;
            } else if (QUALITY_FAIL.contains(normalize(run.getStatus()))) {
                agg.runFailed++;
            }
        }
        for (GovIssueTicket issue : issues) {
            DayAgg createdAgg = bucket.get(dayOf(issue.getCreatedDate()));
            if (createdAgg != null) {
                createdAgg.issueCreated++;
            }
            if (issue.getResolvedAt() != null) {
                DayAgg resolvedAgg = bucket.get(dayOf(issue.getResolvedAt()));
                if (resolvedAgg != null) {
                    resolvedAgg.issueResolved++;
                }
            }
        }
        for (GovComplianceBatch batch : batches) {
            DayAgg agg = bucket.get(dayOf(batch.getCreatedDate()));
            if (agg == null) continue;
            agg.batchCreated++;
            if (COMPLIANCE_FAIL.contains(normalize(batch.getStatus()))) {
                agg.batchFailed++;
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, DayAgg> entry : bucket.entrySet()) {
            DayAgg agg = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", entry.getKey().toString());
            row.put("qualityTotal", agg.runTotal);
            row.put("qualitySuccess", agg.runSuccess);
            row.put("qualityFailed", agg.runFailed);
            row.put("qualitySuccessRate", percentage(agg.runSuccess, agg.runTotal));
            row.put("issueCreated", agg.issueCreated);
            row.put("issueResolved", agg.issueResolved);
            row.put("batchCreated", agg.batchCreated);
            row.put("batchFailed", agg.batchFailed);
            trend.add(row);
        }
        return trend;
    }

    private int normalizeDays(int days) {
        if (days < MIN_DAYS) return MIN_DAYS;
        return Math.min(days, MAX_DAYS);
    }

    private LocalDate dayOf(Instant value) {
        if (value == null) return null;
        return LocalDate.ofInstant(value, ZONE);
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
    }

    private String classifyFailure(String status, String message) {
        String msg = String.valueOf(message == null ? "" : message).toLowerCase(Locale.ROOT);
        if (msg.contains("timeout") || msg.contains("超时")) return "TIMEOUT";
        if (msg.contains("access denied") || msg.contains("permission") || msg.contains("无权限")) return "ACCESS";
        if (msg.contains("connect") || msg.contains("connection") || msg.contains("连接")) return "CONNECTION";
        if (msg.contains("syntax") || msg.contains("sql")) return "SQL";
        String normalizedStatus = normalize(status);
        return normalizedStatus.isBlank() ? "FAILED_UNKNOWN" : normalizedStatus;
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> topN(List<String> values, int size) {
        Map<String, Long> counter = new LinkedHashMap<>();
        for (String value : values) {
            String key = normalize(value);
            if (key.isBlank()) continue;
            counter.put(key, counter.getOrDefault(key, 0L) + 1);
        }
        return counter
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(size)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .toList();
    }

    private static final class DayAgg {

        long runTotal;
        long runSuccess;
        long runFailed;
        long issueCreated;
        long issueResolved;
        long batchCreated;
        long batchFailed;
    }
}
