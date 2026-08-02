package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult.DimensionScore;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult.TrendPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class QualityScoreService {

    private static final Logger log = LoggerFactory.getLogger(QualityScoreService.class);

    private static final Map<String, Integer> SEVERITY_WEIGHTS = Map.of(
        "CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1
    );

    private static final String UNKNOWN_DIMENSION = "OTHER";
    private static final int MAX_PERIOD_DAYS = 365;

    private final GovQualityRunRepository runRepository;
    private final QualityDatasetReadGuard qualityDatasetReadGuard;

    public QualityScoreService(
        GovQualityRunRepository runRepository,
        QualityDatasetReadGuard qualityDatasetReadGuard
    ) {
        this.runRepository = runRepository;
        this.qualityDatasetReadGuard = qualityDatasetReadGuard;
    }

    /**
     * Calculate quality score for a dataset over a time period.
     *
     * <p>Scoring formula:
     * <ul>
     *   <li>Weight: CRITICAL=4, HIGH=3, MEDIUM=2, LOW=1</li>
     *   <li>Single rule score = pass rate x 100 (SUCCESS status = 100, FAILED with failingRowCount uses ratio)</li>
     *   <li>Dimension score = sum(single rule score x weight) / sum(weight), grouped by rule type</li>
     *   <li>Overall = average of dimension scores (equal weight across dimensions)</li>
     *   <li>Delta = current period score - previous period score (null if no previous data)</li>
     * </ul>
     *
     * <p>Trend: one data point per day for the period, computed in-memory from a single query.
     */
    public QualityScoreResult calculate(UUID datasetId, int periodDays) {
        return calculate(datasetId, periodDays, null);
    }

    public QualityScoreResult calculate(UUID datasetId, int periodDays, String activeDeptHeader) {
        if (periodDays < 1 || periodDays > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("统计周期必须在 1 到 " + MAX_PERIOD_DAYS + " 天之间");
        }
        qualityDatasetReadGuard.requireReadable(datasetId, activeDeptHeader);
        Instant now = Instant.now();
        Instant periodStart = now.minus(java.time.Duration.ofDays(periodDays));
        Instant previousPeriodStart = periodStart.minus(java.time.Duration.ofDays(periodDays));

        // Single query for current + previous period
        List<GovQualityRun> allRuns = runRepository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(
            datasetId, previousPeriodStart
        );

        List<GovQualityRun> effectiveRuns = allRuns.stream().filter(this::isScorableRun).toList();

        List<GovQualityRun> currentRuns = effectiveRuns.stream()
            .filter(r -> r.getFinishedAt() != null && !r.getFinishedAt().isBefore(periodStart))
            .toList();
        List<GovQualityRun> previousRuns = effectiveRuns.stream()
            .filter(r -> r.getFinishedAt() != null && r.getFinishedAt().isBefore(periodStart))
            .toList();

        if (currentRuns.isEmpty()) {
            return new QualityScoreResult(0, null, List.of(), List.of());
        }

        Map<String, DimensionCalc> currentDimensions = computeDimensions(currentRuns);
        int currentOverall = computeOverall(currentDimensions);

        Integer overallDelta = null;
        Map<String, DimensionCalc> previousDimensions = Map.of();
        if (!previousRuns.isEmpty()) {
            previousDimensions = computeDimensions(previousRuns);
            int previousOverall = computeOverall(previousDimensions);
            overallDelta = currentOverall - previousOverall;
        }

        List<DimensionScore> dimensions = buildDimensionScores(currentDimensions, previousDimensions);
        List<TrendPoint> trend = computeTrend(currentRuns, periodStart, now);

        return new QualityScoreResult(currentOverall, overallDelta, dimensions, trend);
    }

    // -- internal helpers --

    private Map<String, DimensionCalc> computeDimensions(List<GovQualityRun> runs) {
        Map<String, List<GovQualityRun>> byType = runs.stream()
            .collect(Collectors.groupingBy(this::resolveType));

        Map<String, DimensionCalc> result = new LinkedHashMap<>();
        for (var entry : byType.entrySet()) {
            double weightedSum = 0;
            int totalWeight = 0;
            for (GovQualityRun run : entry.getValue()) {
                int weight = resolveWeight(run);
                int score = computeRunScore(run);
                weightedSum += (double) score * weight;
                totalWeight += weight;
            }
            int dimensionScore = totalWeight > 0 ? (int) Math.round(weightedSum / totalWeight) : 0;
            result.put(entry.getKey(), new DimensionCalc(dimensionScore));
        }
        return result;
    }

    private int computeOverall(Map<String, DimensionCalc> dimensions) {
        if (dimensions.isEmpty()) {
            return 0;
        }
        int sum = dimensions.values().stream().mapToInt(DimensionCalc::score).sum();
        return (int) Math.round((double) sum / dimensions.size());
    }

    private List<DimensionScore> buildDimensionScores(
        Map<String, DimensionCalc> current,
        Map<String, DimensionCalc> previous
    ) {
        List<DimensionScore> scores = new ArrayList<>();
        for (var entry : current.entrySet()) {
            String type = entry.getKey();
            int score = entry.getValue().score();
            Integer delta = null;
            DimensionCalc prev = previous.get(type);
            if (prev != null) {
                delta = score - prev.score();
            }
            scores.add(new DimensionScore(type, score, delta));
        }
        return scores;
    }

    private List<TrendPoint> computeTrend(List<GovQualityRun> runs, Instant periodStart, Instant periodEnd) {
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        LocalDate startDate = periodStart.atZone(zone).toLocalDate();
        LocalDate endDate = periodEnd.atZone(zone).toLocalDate();

        // Group runs by day
        Map<LocalDate, List<GovQualityRun>> byDay = runs.stream()
            .filter(r -> r.getFinishedAt() != null)
            .collect(Collectors.groupingBy(r -> r.getFinishedAt().atZone(zone).toLocalDate()));

        List<TrendPoint> trend = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            List<GovQualityRun> dayRuns = byDay.get(day);
            if (dayRuns != null && !dayRuns.isEmpty()) {
                Map<String, DimensionCalc> dims = computeDimensions(dayRuns);
                int overall = computeOverall(dims);
                trend.add(new TrendPoint(day.format(fmt), overall));
            }
        }
        return trend;
    }

    /**
     * Compute a single run's score (0-100).
     *
     * <p>When {@code rowsTotal} is available, score = pass rate =
     * (rowsTotal - failingRowCount) / rowsTotal * 100.
     *
     * <p>For legacy data without {@code rowsTotal}, fall back to status:
     * SUCCEEDED = 100, FAILED = 0.
     */
    private int computeRunScore(GovQualityRun run) {
        Integer score = QualityRunOutcomeSemantics.passRate(
            run.getStatus(),
            run.getErrorCategory(),
            run.getRowsTotal(),
            run.getFailingRowCount()
        );
        return score != null ? score : 0;
    }

    private boolean isScorableRun(GovQualityRun run) {
        String status = run != null ? run.getStatus() : null;
        return (
            "SUCCEEDED".equalsIgnoreCase(status) ||
            "SUCCESS".equalsIgnoreCase(status) ||
            "PASSED".equalsIgnoreCase(status) ||
            "COMPLETED".equalsIgnoreCase(status) ||
            "FAILED".equalsIgnoreCase(status)
        );
    }

    private String resolveType(GovQualityRun run) {
        GovRule rule = run.getRule();
        if (rule != null && rule.getType() != null) {
            return rule.getType().toUpperCase(java.util.Locale.ROOT);
        }
        return UNKNOWN_DIMENSION;
    }

    private int resolveWeight(GovQualityRun run) {
        // Prefer severity from the run itself, fall back to rule
        String severity = run.getSeverity();
        if (severity == null) {
            GovRule rule = run.getRule();
            if (rule != null) {
                severity = rule.getSeverity();
            }
        }
        if (severity == null) {
            return 1;
        }
        return SEVERITY_WEIGHTS.getOrDefault(severity.toUpperCase(java.util.Locale.ROOT), 1);
    }

    private record DimensionCalc(int score) {}
}
