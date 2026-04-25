package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.service.workbench.WorkbenchRoleResolver.Role;
import com.yuzhi.dts.platform.service.workbench.dto.DomainAggregateRow;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse.DomainCell;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse.Kpis;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse.TopAsset;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse.TopReport;
import com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregation service backing {@code /api/workbench/leader-overview}.
 *
 * <p>Second-wave (T03-T06) implementation: fills Kpis, topReports,
 * topAssets, and domainMatrix with real aggregated data. All outbound
 * {@code classification} values are mapped to the Sprint-15 API
 * vocabulary (S1-S4) via {@link ClassificationMapper}.
 */
@Service
@Transactional(readOnly = true)
public class WorkbenchLeaderOverviewService {

    private static final Logger log = LoggerFactory.getLogger(WorkbenchLeaderOverviewService.class);

    private static final Pageable TOP_N = PageRequest.of(0, 10);
    private static final int DOMAIN_MATRIX_TOP = 6;
    /** Defense-in-depth: reject deptCode containing LIKE wildcards or other injection chars. */
    private static final Pattern DEPT_CODE_ALLOWLIST = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    /** MINE scope look-back window for counting reports a user has visited. */
    private static final int MINE_REPORTS_LOOKBACK_DAYS = 30;

    private final WorkbenchRoleResolver roleResolver;
    private final BiReportLinkRepository reportRepo;
    private final BiReportVisitRepository visitRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDomainRepository catalogDomainRepo;

    public WorkbenchLeaderOverviewService(
        WorkbenchRoleResolver roleResolver,
        BiReportLinkRepository reportRepo,
        BiReportVisitRepository visitRepo,
        CatalogDatasetRepository datasetRepo,
        CatalogDomainRepository catalogDomainRepo
    ) {
        this.roleResolver = roleResolver;
        this.reportRepo = reportRepo;
        this.visitRepo = visitRepo;
        this.datasetRepo = datasetRepo;
        this.catalogDomainRepo = catalogDomainRepo;
    }

    public LeaderOverviewResponse build(
        String userLogin,
        List<String> userRoles,
        String userDeptCode,
        String requestedScope,
        String requestedDeptCode,
        String bizDomain,
        String timeRange
    ) {
        Role role = roleResolver.resolve(userRoles);
        String scope = resolveScope(role, userLogin, requestedScope);
        String effectiveDept = resolveEffectiveDept(scope, userDeptCode, requestedDeptCode);
        String range = normalizeTimeRange(timeRange);
        TimeWindow window = windowOf(range);

        Kpis kpis = computeKpis(scope, effectiveDept, bizDomain, window, userLogin);
        List<TopReport> topReports = computeTopReports(scope, effectiveDept, bizDomain, window, userLogin);
        List<TopAsset> topAssets = computeTopAssets(scope, effectiveDept, bizDomain, userLogin);
        List<DomainCell> domainMatrix = computeDomainMatrix(scope, effectiveDept, bizDomain, window);

        return new LeaderOverviewResponse(
            Instant.now(),
            scope,
            effectiveDept,
            range,
            kpis,
            topReports,
            topAssets,
            domainMatrix
        );
    }

    // =============================================================================================
    // T03: KPI aggregation
    // =============================================================================================

    Kpis computeKpis(String scope, String effectiveDept, String bizDomain, TimeWindow w, String userLogin) {
        long reportsTotal;
        long reportsNewInPeriod;
        long visitsInPeriod;
        long visitsPrev;

        switch (scope) {
            case "MINE" -> {
                Instant lookback = w.end().minusSeconds(MINE_REPORTS_LOOKBACK_DAYS * 86400L);
                reportsTotal = reportRepo.countDistinctReportsVisitedByUser(userLogin, lookback, w.end());
                reportsNewInPeriod = reportRepo.countDistinctReportsVisitedByUser(userLogin, w.start(), w.end());
                visitsInPeriod = visitRepo.countVisitsForUser(userLogin, w.start(), w.end());
                visitsPrev = visitRepo.countVisitsForUser(userLogin, w.prevStart(), w.prevEnd());
            }
            case "DEPT" -> {
                reportsTotal = reportRepo.countForDept(effectiveDept, bizDomain, null, null);
                reportsNewInPeriod = reportRepo.countForDept(effectiveDept, bizDomain, w.start(), w.end());
                visitsInPeriod = visitRepo.countVisitsForDept(effectiveDept, bizDomain, w.start(), w.end());
                visitsPrev = visitRepo.countVisitsForDept(effectiveDept, bizDomain, w.prevStart(), w.prevEnd());
            }
            case "ALL" -> {
                if (hasText(effectiveDept)) {
                    reportsTotal = reportRepo.countForDept(effectiveDept, bizDomain, null, null);
                    reportsNewInPeriod = reportRepo.countForDept(effectiveDept, bizDomain, w.start(), w.end());
                } else {
                    reportsTotal = reportRepo.countForAll(bizDomain, null, null);
                    reportsNewInPeriod = reportRepo.countForAll(bizDomain, w.start(), w.end());
                }
                visitsInPeriod = visitRepo.countVisitsForAll(effectiveDept, bizDomain, w.start(), w.end());
                visitsPrev = visitRepo.countVisitsForAll(effectiveDept, bizDomain, w.prevStart(), w.prevEnd());
            }
            default -> {
                reportsTotal = 0L;
                reportsNewInPeriod = 0L;
                visitsInPeriod = 0L;
                visitsPrev = 0L;
            }
        }

        BigDecimal mom = visitsPrev == 0
            ? null
            : BigDecimal.valueOf(visitsInPeriod - visitsPrev)
                .divide(BigDecimal.valueOf(visitsPrev), 4, RoundingMode.HALF_UP);

        // Asset counts: MINE scope uses the user as owner (createdBy) proxy; DEPT/ALL use deptCode.
        String assetDept = "MINE".equals(scope) ? null : effectiveDept;
        // For MINE, we don't (yet) scope asset totals to the user — fallback to institute totals
        // so the card still shows something meaningful. A future task can wire an access-log.
        long assetsTotal = datasetRepo.countAssets(assetDept, bizDomain, null, null);
        long assetsNewInPeriod = datasetRepo.countAssets(assetDept, bizDomain, w.start(), w.end());
        long assetsS1 = datasetRepo.countAssetsByClassifications(
            assetDept,
            bizDomain,
            ClassificationMapper.toDbValues("S1")
        );
        long assetsS1S2 = datasetRepo.countAssetsByClassifications(
            assetDept,
            bizDomain,
            List.of("TOP_SECRET", "SECRET")
        );
        BigDecimal s1Ratio = assetsTotal == 0
            ? null
            : BigDecimal.valueOf(assetsS1).divide(BigDecimal.valueOf(assetsTotal), 4, RoundingMode.HALF_UP);

        return new Kpis(
            reportsTotal,
            reportsNewInPeriod,
            visitsInPeriod,
            mom,
            assetsTotal,
            assetsNewInPeriod,
            assetsS1,
            assetsS1S2,
            s1Ratio
        );
    }

    // =============================================================================================
    // T04: TOP reports
    // =============================================================================================

    List<TopReport> computeTopReports(
        String scope,
        String effectiveDept,
        String bizDomain,
        TimeWindow w,
        String userLogin
    ) {
        List<ReportVisitAggregateRow> rows = switch (scope) {
            case "MINE" -> visitRepo.findTopRecentByUser(userLogin, TOP_N);
            case "DEPT" -> hasText(effectiveDept)
                ? visitRepo.aggregateTopReportsForDept(effectiveDept, bizDomain, w.start(), w.end(), TOP_N)
                : List.<ReportVisitAggregateRow>of();
            case "ALL" -> hasText(effectiveDept)
                ? visitRepo.aggregateTopReportsForDept(effectiveDept, bizDomain, w.start(), w.end(), TOP_N)
                : visitRepo.aggregateTopReportsAll(bizDomain, w.start(), w.end(), TOP_N);
            default -> List.<ReportVisitAggregateRow>of();
        };

        return rows
            .stream()
            .map(row -> new TopReport(
                row.reportId() != null ? row.reportId().toString() : null,
                row.title(),
                row.visits(),
                row.bizDomain(),
                ClassificationMapper.toApiCode(row.classification()),
                row.lastVisitedAt()
            ))
            .toList();
    }

    // =============================================================================================
    // T05: TOP assets
    // =============================================================================================

    List<TopAsset> computeTopAssets(String scope, String effectiveDept, String bizDomain, String userLogin) {
        List<CatalogDataset> rows;
        if ("MINE".equals(scope)) {
            rows = datasetRepo.findTopForUser(userLogin, TOP_N);
        } else {
            // DEPT and ALL share the same filter: effectiveDept already encodes the null
            // (ALL+no drill-down) vs scoped (DEPT, or ALL+deptCode) cases upstream.
            rows = datasetRepo.findTopByClassification(effectiveDept, bizDomain, TOP_N);
        }
        return rows
            .stream()
            .map(d -> new TopAsset(
                d.getId() != null ? d.getId().toString() : null,
                d.getName(),
                ClassificationMapper.toApiCode(d.getClassification()),
                d.getLastModifiedDate(),
                d.getDomain() != null ? d.getDomain().getCode() : null
            ))
            .toList();
    }

    // =============================================================================================
    // T06: Business-domain matrix (scope=ALL only)
    // =============================================================================================

    List<DomainCell> computeDomainMatrix(String scope, String effectiveDept, String bizDomain, TimeWindow w) {
        if (!"ALL".equals(scope)) {
            return List.of();
        }

        List<DomainAggregateRow> rows = visitRepo.aggregateByBizDomain(
            hasText(effectiveDept) ? effectiveDept : null,
            hasText(bizDomain) ? bizDomain : null,
            w.start(),
            w.end()
        );

        Map<String, String> domainNames = loadDomainNames();

        List<DomainAggregateRow> sorted = rows
            .stream()
            .sorted(Comparator.comparingLong(DomainAggregateRow::visits).reversed())
            .toList();

        List<DomainAggregateRow> top = sorted.size() > DOMAIN_MATRIX_TOP
            ? sorted.subList(0, DOMAIN_MATRIX_TOP)
            : sorted;

        long otherSum = sorted
            .stream()
            .skip(DOMAIN_MATRIX_TOP)
            .mapToLong(DomainAggregateRow::visits)
            .sum();

        List<DomainCell> cells = new ArrayList<>();
        for (DomainAggregateRow row : top) {
            if (row.bizDomain() == null || row.bizDomain().isBlank()) {
                cells.add(new DomainCell("__UNCATEGORIZED__", "未分类", row.visits()));
            } else {
                String name = domainNames.getOrDefault(row.bizDomain(), row.bizDomain());
                cells.add(new DomainCell(row.bizDomain(), name, row.visits()));
            }
        }
        if (otherSum > 0) {
            cells.add(new DomainCell("__OTHER__", "其他", otherSum));
        }
        return cells;
    }

    private Map<String, String> loadDomainNames() {
        try {
            return catalogDomainRepo
                .findAll()
                .stream()
                .filter(d -> d.getCode() != null && !d.getCode().isBlank())
                .collect(Collectors.toMap(
                    CatalogDomain::getCode,
                    d -> d.getName() != null && !d.getName().isBlank() ? d.getName() : d.getCode(),
                    (a, b) -> a
                ));
        } catch (Exception e) {
            log.warn("failed to load catalog domains, falling back to code-only labels: {}", e.getMessage());
            return Map.of();
        }
    }

    // =============================================================================================
    // Scope / deptCode / time-range resolution (unchanged from first wave)
    // =============================================================================================

    /**
     * Enforces role-based downgrade:
     * <ul>
     *   <li>Non-INST_LEADER requesting ALL → DEPT</li>
     *   <li>EMP requesting DEPT → MINE</li>
     *   <li>Null request → default by role</li>
     * </ul>
     */
    String resolveScope(Role role, String userLogin, String requestedScope) {
        if (requestedScope == null || requestedScope.isBlank()) {
            return switch (role) {
                case INST_LEADER -> "ALL";
                case DEPT_LEADER -> "DEPT";
                case EMP -> "MINE";
            };
        }
        String requested = requestedScope.trim().toUpperCase(Locale.ROOT);
        String normalized = switch (requested) {
            case "DEPT", "ALL", "MINE" -> requested;
            default -> "MINE";
        };
        if ("ALL".equals(normalized) && role != Role.INST_LEADER) {
            log.warn("user={} role={} requested scope=ALL, downgraded to DEPT", userLogin, role);
            normalized = "DEPT";
        }
        if ("DEPT".equals(normalized) && role == Role.EMP) {
            log.warn("user={} role=EMP requested scope=DEPT, downgraded to MINE", userLogin);
            normalized = "MINE";
        }
        return normalized;
    }

    /**
     * DEPT scope: always uses userDeptCode (ignores requestedDeptCode to
     * prevent cross-department peeking). ALL scope: honours requestedDeptCode
     * (only INST_LEADER reaches this branch after downgrade). MINE: null.
     */
    String resolveEffectiveDept(String scope, String userDeptCode, String requestedDeptCode) {
        return switch (scope) {
            case "MINE" -> null;
            case "DEPT" -> trimToNull(userDeptCode);
            case "ALL" -> sanitizeDeptCode(requestedDeptCode);
            default -> null;
        };
    }

    /**
     * Allowlist requested deptCode before it reaches repository LIKE clauses.
     * On invalid input (wildcards, oversized, etc.) we silently treat it as
     * "no drill-down" rather than throwing, so the UX stays graceful for
     * INST_LEADERs with stale or malformed query params.
     */
    private static String sanitizeDeptCode(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        return DEPT_CODE_ALLOWLIST.matcher(trimmed).matches() ? trimmed : null;
    }

    String normalizeTimeRange(String t) {
        if (t == null || t.isBlank()) return "MONTH";
        return switch (t.trim().toUpperCase(Locale.ROOT)) {
            case "MONTH", "QUARTER", "YEAR" -> t.trim().toUpperCase(Locale.ROOT);
            default -> "MONTH";
        };
    }

    // =============================================================================================
    // Time-window helper
    // =============================================================================================

    /**
     * Time range with a previous-period mirror used for MoM calculations.
     * {@code end} is exclusive (tomorrow 00:00).
     */
    public record TimeWindow(Instant start, Instant end, Instant prevStart, Instant prevEnd) {}

    TimeWindow windowOf(String range) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        return switch (range) {
            case "MONTH" -> {
                LocalDate s = today.withDayOfMonth(1);
                LocalDate ps = s.minusMonths(1);
                yield new TimeWindow(
                    s.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                    ps.atStartOfDay(zone).toInstant(),
                    s.atStartOfDay(zone).toInstant()
                );
            }
            case "QUARTER" -> {
                int q = (today.getMonthValue() - 1) / 3;
                LocalDate s = LocalDate.of(today.getYear(), q * 3 + 1, 1);
                LocalDate ps = s.minusMonths(3);
                yield new TimeWindow(
                    s.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                    ps.atStartOfDay(zone).toInstant(),
                    s.atStartOfDay(zone).toInstant()
                );
            }
            case "YEAR" -> {
                LocalDate s = today.withDayOfYear(1);
                LocalDate ps = s.minusYears(1);
                yield new TimeWindow(
                    s.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                    ps.atStartOfDay(zone).toInstant(),
                    s.atStartOfDay(zone).toInstant()
                );
            }
            default -> throw new IllegalArgumentException("unknown timeRange: " + range);
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
