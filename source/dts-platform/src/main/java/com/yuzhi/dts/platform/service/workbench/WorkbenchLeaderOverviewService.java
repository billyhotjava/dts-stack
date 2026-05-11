package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.service.integration.ScreenReportLinkSyncService;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard;
import com.yuzhi.dts.platform.service.permission.DashboardCallerResolver;
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

    /** Defense-in-depth: reject deptCode containing LIKE wildcards or other injection chars. */
    private static final Pattern DEPT_CODE_ALLOWLIST = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final WorkbenchRoleResolver roleResolver;
    private final BiReportLinkRepository reportRepo;
    private final BiReportVisitRepository visitRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDomainRepository catalogDomainRepo;
    private final WorkbenchLeaderOverviewProperties props;
    private final TopReportsFallbackService fallbackService;
    private final DashboardAccessGuard dashboardAccessGuard;
    private final DashboardCallerResolver dashboardCallerResolver;

    public WorkbenchLeaderOverviewService(
        WorkbenchRoleResolver roleResolver,
        BiReportLinkRepository reportRepo,
        BiReportVisitRepository visitRepo,
        CatalogDatasetRepository datasetRepo,
        CatalogDomainRepository catalogDomainRepo,
        WorkbenchLeaderOverviewProperties props,
        TopReportsFallbackService fallbackService,
        DashboardAccessGuard dashboardAccessGuard,
        DashboardCallerResolver dashboardCallerResolver
    ) {
        this.roleResolver = roleResolver;
        this.reportRepo = reportRepo;
        this.visitRepo = visitRepo;
        this.datasetRepo = datasetRepo;
        this.catalogDomainRepo = catalogDomainRepo;
        this.props = props;
        this.fallbackService = fallbackService;
        this.dashboardAccessGuard = dashboardAccessGuard;
        this.dashboardCallerResolver = dashboardCallerResolver;
    }

    private Pageable topNPageable() {
        return PageRequest.of(0, props.getTopN());
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
                Instant lookback = w.end().minusSeconds(props.getMineReportsLookbackDays() * 86400L);
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
        // P0-5: For MINE, we don't (yet) scope asset totals to the user — fallback to
        // institute totals so the card still shows something meaningful. We surface
        // the fallback flag so the UI can disclose "机构合计" to the user instead of
        // implying the count is "我创建的".
        boolean assetScopeFallback = "MINE".equals(scope);
        long assetsTotal = datasetRepo.countAssets(assetDept, bizDomain, null, null);
        long assetsNewInPeriod = datasetRepo.countAssets(assetDept, bizDomain, w.start(), w.end());
        long assetsS1 = datasetRepo.countAssetsByClassifications(
            assetDept,
            bizDomain,
            ClassificationMapper.toDbValues("S1")
        );
        // P2-4: route both legs through ClassificationMapper so the DB vocabulary
        // ("TOP_SECRET", "SECRET") only lives in one place.
        List<String> s1s2DbValues = new ArrayList<>();
        s1s2DbValues.addAll(ClassificationMapper.toDbValues("S1"));
        s1s2DbValues.addAll(ClassificationMapper.toDbValues("S2"));
        long assetsS1S2 = datasetRepo.countAssetsByClassifications(
            assetDept,
            bizDomain,
            s1s2DbValues
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
            s1Ratio,
            assetScopeFallback
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
            case "MINE" -> visitRepo.findTopRecentByUser(
                userLogin,
                w.end().minusSeconds(props.getMineTopReportsLookbackDays() * 86400L),
                topNPageable()
            );
            case "DEPT" -> hasText(effectiveDept)
                ? visitRepo.aggregateTopReportsForDept(effectiveDept, bizDomain, w.start(), w.end(), topNPageable())
                : List.<ReportVisitAggregateRow>of();
            case "ALL" -> hasText(effectiveDept)
                ? visitRepo.aggregateTopReportsForDept(effectiveDept, bizDomain, w.start(), w.end(), topNPageable())
                : visitRepo.aggregateTopReportsAll(bizDomain, w.start(), w.end(), topNPageable());
            default -> List.<ReportVisitAggregateRow>of();
        };

        List<TopReport> mapped = rows
            .stream()
            .map(row -> new TopReport(
                row.reportId() != null ? row.reportId().toString() : null,
                row.title(),
                row.visits(),
                row.bizDomain(),
                ClassificationMapper.toApiCode(row.classification()),
                row.lastVisitedAt(),
                row.url(),
                row.engine()
            ))
            .toList();

        // Sprint-17 hotfix: when MINE-scope has no visit history yet (typical first-time user),
        // fall back to reconcile-synced screens so "我常用的大屏" is not permanently empty.
        // visits is reported as 0 to signal "not yet visited" — UI shows the relativeTime as "—".
        // The fallback runs in its own REQUIRES_NEW sub-transaction (TopReportsFallbackService),
        // so a schema mismatch (missing bi_report_link.source column when the Liquibase
        // changeset has not been applied) only rolls back the sub-tx — the outer @Transactional
        // stays usable and computeTopAssets / computeDomainMatrix below can still succeed.
        if ("MINE".equals(scope) && mapped.isEmpty()) {
            try {
                // Sprint-17.1: identify reconcile rows by code prefix instead of `source`
                // column — keeps fallback functional on older DBs without the new column.
                List<BiReportLink> fallback = fallbackService.tryFetchFallback(
                    ScreenReportLinkSyncService.CODE_PREFIX,
                    topNPageable()
                );
                return fallback
                    .stream()
                    .filter(this::canCurrentUserViewFallbackReport)
                    .map(link -> new TopReport(
                        link.getId() != null ? link.getId().toString() : null,
                        link.getTitle(),
                        0L,
                        link.getBizDomain(),
                        ClassificationMapper.toApiCode(link.getClassification()),
                        link.getLastVisitedAt(),
                        link.getUrl(),
                        link.getEngine()
                    ))
                    .toList();
            } catch (Exception e) {
                log.warn("MINE topReports fallback query failed: {}", e.getMessage());
                return List.of();
            }
        }
        return mapped;
    }

    private boolean canCurrentUserViewFallbackReport(BiReportLink link) {
        DashboardAccessGuard.Caller caller = dashboardCallerResolver.current();
        return dashboardAccessGuard.canView(link, caller).allow();
    }

    // =============================================================================================
    // T05: TOP assets
    // =============================================================================================

    List<TopAsset> computeTopAssets(String scope, String effectiveDept, String bizDomain, String userLogin) {
        List<CatalogDataset> rows;
        if ("MINE".equals(scope)) {
            rows = datasetRepo.findTopForUser(userLogin, topNPageable());
        } else {
            // DEPT and ALL share the same filter: effectiveDept already encodes the null
            // (ALL+no drill-down) vs scoped (DEPT, or ALL+deptCode) cases upstream.
            rows = datasetRepo.findTopByClassification(
                effectiveDept,
                bizDomain,
                ClassificationMapper.toDbValues("S1"),
                ClassificationMapper.toDbValues("S2"),
                ClassificationMapper.toDbValues("S3"),
                ClassificationMapper.toDbValues("S4"),
                topNPageable()
            );
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

        int matrixTop = props.getDomainMatrixTop();
        List<DomainAggregateRow> top = sorted.size() > matrixTop
            ? sorted.subList(0, matrixTop)
            : sorted;

        long otherSum = sorted
            .stream()
            .skip(matrixTop)
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
            // P1-2: drop user-identifying string from the log line. Keeping
            // a short stable hash of the login lets ops correlate repeated
            // attempts without persisting the username in plain text.
            log.info(
                "workbench.scope.downgrade userHash={} role={} requested=ALL effective=DEPT",
                userLoginHash(userLogin),
                role
            );
            normalized = "DEPT";
        }
        if ("DEPT".equals(normalized) && role == Role.EMP) {
            log.info(
                "workbench.scope.downgrade userHash={} role=EMP requested=DEPT effective=MINE",
                userLoginHash(userLogin)
            );
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
        // P2-2: timezone is configurable so MoM/quarter boundaries do not
        // depend on the JVM default (which can drift between dev / prod).
        ZoneId zone = ZoneId.of(props.getTimezone());
        LocalDate today = LocalDate.now(zone);
        // P0-4: previous window is an EQUAL-LENGTH sliding window
        // immediately preceding the current one. Using "上一个完整周期"
        // (e.g. full April vs partial May) made MoM permanently negative
        // at month start because the windows were unequal lengths.
        LocalDate currentStart = switch (range) {
            case "MONTH" -> today.withDayOfMonth(1);
            case "QUARTER" -> {
                int q = (today.getMonthValue() - 1) / 3;
                yield LocalDate.of(today.getYear(), q * 3 + 1, 1);
            }
            case "YEAR" -> today.withDayOfYear(1);
            default -> throw new IllegalArgumentException("unknown timeRange: " + range);
        };
        Instant start = currentStart.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        long durationSeconds = (end.getEpochSecond() - start.getEpochSecond());
        Instant prevEnd = start;
        Instant prevStart = prevEnd.minusSeconds(durationSeconds);
        return new TimeWindow(start, end, prevStart, prevEnd);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Stable, short, irreversible identifier for a user login — used in scope
     * downgrade logs so ops can correlate repeated attempts without leaking
     * the raw username into log files (P1-2). Returns 8 hex chars of SHA-256.
     */
    private static String userLoginHash(String userLogin) {
        if (userLogin == null || userLogin.isBlank()) return "anon";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(userLogin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return "n/a";
        }
    }
}
