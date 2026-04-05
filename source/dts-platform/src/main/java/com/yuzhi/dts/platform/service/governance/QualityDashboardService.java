package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityDashboardDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityDashboardDto.FailingDataset;
import com.yuzhi.dts.platform.service.governance.dto.QualityDashboardDto.RecentRun;
import com.yuzhi.dts.platform.service.governance.dto.QualityDashboardDto.TrendPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QualityDashboardService {

    private static final Logger log = LoggerFactory.getLogger(QualityDashboardService.class);

    private final GovRuleRepository ruleRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final GovQualityRunRepository runRepository;

    public QualityDashboardService(
        GovRuleRepository ruleRepository,
        GovRuleBindingRepository bindingRepository,
        GovQualityRunRepository runRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.bindingRepository = bindingRepository;
        this.runRepository = runRepository;
    }

    public QualityDashboardDto getDashboard() {
        // 1. ruleCount: enabled rules
        int ruleCount = ruleRepository.countByEnabledTrue();

        // 2. coveredDatasets: distinct dataset IDs from bindings (use count query, not findAll)
        long coveredDatasets = bindingRepository.countDistinctDatasetIds();

        // 3. totalDatasets: use coveredDatasets since there is no standalone dataset table
        int totalDatasets = (int) coveredDatasets;

        // 4 & 5. todayPassed / todayFailed
        ZoneId zone = ZoneId.systemDefault();
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        List<GovQualityRun> todayRuns = runRepository.findByCreatedDateAfterOrderByCreatedDateAsc(todayStart);

        int todayPassed = 0;
        int todayFailed = 0;
        for (GovQualityRun run : todayRuns) {
            if ("SUCCESS".equalsIgnoreCase(run.getStatus())) {
                todayPassed++;
            } else if ("FAILED".equalsIgnoreCase(run.getStatus())) {
                todayFailed++;
            }
        }

        // 5. pendingFixRows: count FAILED runs as a proxy (no per-row count available)
        List<GovQualityRun> failedRuns = runRepository.findTop100ByStatusOrderByCreatedDateDesc("FAILED");
        long pendingFixRows = failedRuns.size();

        // 6. trend7d: pass rate per day for the last 7 days
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<GovQualityRun> recentRuns = runRepository.findByCreatedDateAfterOrderByCreatedDateAsc(sevenDaysAgo);
        List<TrendPoint> trend7d = computeTrend(recentRuns, zone);

        // 7. topFailingDatasets: group FAILED runs by datasetId, sum failingRows, top 5
        List<FailingDataset> topFailingDatasets = computeTopFailingDatasets(failedRuns);

        // 8. recentFailedRuns: last 10 failed runs
        List<RecentRun> recentFailedRuns = failedRuns.stream()
            .limit(10)
            .map(this::toRecentRun)
            .toList();

        return new QualityDashboardDto(
            ruleCount,
            (int) coveredDatasets,
            totalDatasets,
            todayPassed,
            todayFailed,
            pendingFixRows,
            trend7d,
            topFailingDatasets,
            recentFailedRuns
        );
    }

    private List<TrendPoint> computeTrend(List<GovQualityRun> runs, ZoneId zone) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate today = LocalDate.now(zone);
        LocalDate startDate = today.minusDays(6); // 7 days including today

        Map<LocalDate, List<GovQualityRun>> byDay = runs.stream()
            .filter(r -> r.getFinishedAt() != null || r.getCreatedDate() != null)
            .collect(Collectors.groupingBy(r -> {
                Instant ts = r.getFinishedAt() != null ? r.getFinishedAt() : r.getCreatedDate();
                return ts.atZone(zone).toLocalDate();
            }));

        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(today); day = day.plusDays(1)) {
            List<GovQualityRun> dayRuns = byDay.get(day);
            if (dayRuns != null && !dayRuns.isEmpty()) {
                long total = dayRuns.size();
                long passed = dayRuns.stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .count();
                double passRate = Math.round((double) passed / total * 10000.0) / 100.0;
                trend.add(new TrendPoint(day.format(fmt), passRate));
            } else {
                trend.add(new TrendPoint(day.format(fmt), 0.0));
            }
        }
        return trend;
    }

    private List<FailingDataset> computeTopFailingDatasets(List<GovQualityRun> failedRuns) {
        Map<UUID, Long> byDataset = new LinkedHashMap<>();
        Map<UUID, String> datasetNames = new LinkedHashMap<>();

        for (GovQualityRun run : failedRuns) {
            if (run.getDatasetId() == null) {
                continue;
            }
            // Count each FAILED run as 1 (no per-row count available on entity)
            byDataset.merge(run.getDatasetId(), 1L, Long::sum);
            // Use rule name as a proxy for dataset name if binding alias isn't available
            if (!datasetNames.containsKey(run.getDatasetId()) && run.getBinding() != null) {
                String alias = run.getBinding().getDatasetAlias();
                if (alias != null && !alias.isBlank()) {
                    datasetNames.put(run.getDatasetId(), alias);
                }
            }
        }

        return byDataset.entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> new FailingDataset(
                datasetNames.getOrDefault(e.getKey(), e.getKey().toString()),
                e.getValue()
            ))
            .toList();
    }

    private RecentRun toRecentRun(GovQualityRun run) {
        String ruleName = "";
        if (run.getRule() != null && run.getRule().getName() != null) {
            ruleName = run.getRule().getName();
        }
        String dataset = "";
        if (run.getBinding() != null && run.getBinding().getDatasetAlias() != null) {
            dataset = run.getBinding().getDatasetAlias();
        } else if (run.getDatasetId() != null) {
            dataset = run.getDatasetId().toString();
        }
        String time = run.getFinishedAt() != null ? run.getFinishedAt().toString() : "";
        String status = run.getStatus() != null ? run.getStatus() : "";
        return new RecentRun(ruleName, dataset, time, status);
    }
}
