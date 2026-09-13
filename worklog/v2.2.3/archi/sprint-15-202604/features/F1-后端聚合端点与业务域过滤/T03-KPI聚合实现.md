# T03: 聚合实现 · KPI 主干

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标

在 `WorkbenchLeaderOverviewService` 里实现 KPI 聚合，填充 `LeaderOverviewResponse.Kpis` 全部字段，包括环比 `visitsMoM` 和 S1 占比 `assetsS1Ratio`。

## 技术设计

### 入参语义复盘

| scope | deptCode 语义 | 口径 |
|---|---|---|
| MINE | 忽略 | 当前用户自己 |
| DEPT | 必填（T07 会保证） | 指定部门 |
| ALL | 可空或部门 code | 空 = 全所；非空 = 所领导下钻某部门 |

### 时间窗口

```java
public record TimeWindow(Instant start, Instant end, Instant prevStart, Instant prevEnd) {}

private TimeWindow windowOf(String range) {
    ZoneId zone = ZoneId.systemDefault();
    LocalDate today = LocalDate.now(zone);
    return switch (range) {
        case "MONTH" -> {
            LocalDate s = today.withDayOfMonth(1);
            LocalDate ps = s.minusMonths(1);
            yield new TimeWindow(s.atStartOfDay(zone).toInstant(),
                                 today.plusDays(1).atStartOfDay(zone).toInstant(),
                                 ps.atStartOfDay(zone).toInstant(),
                                 s.atStartOfDay(zone).toInstant());
        }
        case "QUARTER" -> {
            int q = ((today.getMonthValue() - 1) / 3);
            LocalDate s = LocalDate.of(today.getYear(), q * 3 + 1, 1);
            LocalDate ps = s.minusMonths(3);
            yield new TimeWindow(s.atStartOfDay(zone).toInstant(),
                                 today.plusDays(1).atStartOfDay(zone).toInstant(),
                                 ps.atStartOfDay(zone).toInstant(),
                                 s.atStartOfDay(zone).toInstant());
        }
        case "YEAR" -> {
            LocalDate s = today.withDayOfYear(1);
            LocalDate ps = s.minusYears(1);
            yield new TimeWindow(s.atStartOfDay(zone).toInstant(),
                                 today.plusDays(1).atStartOfDay(zone).toInstant(),
                                 ps.atStartOfDay(zone).toInstant(),
                                 s.atStartOfDay(zone).toInstant());
        }
        default -> throw new IllegalArgumentException("unknown timeRange: " + range);
    };
}
```

### 各 KPI 计算

注入依赖：

```java
private final ReportLinkRepository reportRepo;
private final ReportVisitRepository visitRepo;      // 若无，复用现有写入端的 repo 或新增一个
private final CatalogDatasetRepository datasetRepo;
```

```java
private Kpis computeKpis(String scope, String effectiveDept, String bizDomain, TimeWindow w) {
    long reportsTotal = reportRepo.count(reportsSpec(scope, effectiveDept, bizDomain));
    long reportsNewInPeriod = reportRepo.count(reportsSpec(scope, effectiveDept, bizDomain)
        .and((root, cq, cb) -> cb.between(root.get("createdDate"), w.start(), w.end())));

    long visitsInPeriod = visitRepo.countByFilters(scope, effectiveDept, bizDomain, w.start(), w.end());
    long visitsPrev = visitRepo.countByFilters(scope, effectiveDept, bizDomain, w.prevStart(), w.prevEnd());
    BigDecimal mom = visitsPrev == 0 ? null
        : BigDecimal.valueOf(visitsInPeriod - visitsPrev)
            .divide(BigDecimal.valueOf(visitsPrev), 4, RoundingMode.HALF_UP);

    long assetsTotal = datasetRepo.count(assetsSpec(scope, effectiveDept, bizDomain));
    long assetsNewInPeriod = datasetRepo.count(assetsSpec(scope, effectiveDept, bizDomain)
        .and((root, cq, cb) -> cb.between(root.get("createdDate"), w.start(), w.end())));
    long assetsS1 = datasetRepo.count(assetsSpec(scope, effectiveDept, bizDomain)
        .and((root, cq, cb) -> cb.equal(root.get("classification"), "S1")));
    long assetsS1S2 = datasetRepo.count(assetsSpec(scope, effectiveDept, bizDomain)
        .and((root, cq, cb) -> root.get("classification").in("S1", "S2")));
    BigDecimal s1Ratio = assetsTotal == 0 ? null
        : BigDecimal.valueOf(assetsS1).divide(BigDecimal.valueOf(assetsTotal), 4, RoundingMode.HALF_UP);

    return new Kpis(reportsTotal, reportsNewInPeriod, visitsInPeriod, mom,
                    assetsTotal, assetsNewInPeriod, assetsS1, assetsS1S2, s1Ratio);
}
```

`reportsSpec` / `assetsSpec` 两个 helper 按 `scope` 分发：

```java
private Specification<ReportLinkEntity> reportsSpec(String scope, String deptCode, String bizDomain) {
    return (root, cq, cb) -> {
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.isTrue(root.get("enabled")));
        if ("DEPT".equals(scope) && StringUtils.hasText(deptCode)) {
            ps.add(cb.isMember(deptCode, root.get("deptCodes")));
        }
        if ("ALL".equals(scope) && StringUtils.hasText(deptCode)) {
            ps.add(cb.isMember(deptCode, root.get("deptCodes")));
        }
        if (StringUtils.hasText(bizDomain)) {
            ps.add(cb.equal(root.get("bizDomain"), bizDomain));
        }
        return cb.and(ps.toArray(Predicate[]::new));
    };
}
```

> **"MINE"** 的报表口径：取当前用户近 30 天访问过的报表 id 列表，再 JPA `root.get("id").in(ids)`。访问关系由 visit repo 承载；若性能有压力，抽成 native query。

### 访问聚合

查 `reports/visit` 写入链路（`ReportsResource.visit` 端点），定位 visit 表实体与 repository。
- 若现有 repo 已有 `countBy...` 方法，直接复用。
- 若没有，按需新增 JPQL：

```java
@Query("""
  select count(v) from ReportVisitEntity v
  where v.visitedAt between :start and :end
    and (:scope = 'ALL' or v.deptCode = :deptCode or :deptCode is null)
    and (:bizDomain is null or v.bizDomain = :bizDomain)
    and (:scope <> 'MINE' or v.userLogin = :userLogin)
""")
long countByFilters(...);
```

> **如果 visit 表没有 `deptCode` / `bizDomain` 冗余字段**：需要 join 回 `reports` 表。一旦遇到性能问题，考虑在 visit 写入时带上这两列（此动作超出本 task，留作后续优化 task）。

### MINE 场景下的特殊约束

- `reportsTotal` / `reportsNewInPeriod` 对员工意义不大，但契约一致。员工视角下 `reportsTotal` = 员工近 30 天访问过的报表去重数；`reportsNewInPeriod` = 其中在当前 period 首次访问的数量。由 T01 的 `scope=MINE` 分支进入此计算。

## 影响范围

- `WorkbenchLeaderOverviewService.java`（主实现）
- 可能的新增 JPQL 方法：`ReportVisitRepository.java`、`CatalogDatasetRepository.java`
- `ReportLinkRepository`（若改走 Specification）

## 验证

- [ ] 单测 `WorkbenchLeaderOverviewServiceTest`：
  - `kpis_scope_ALL_month_returns_totals()`
  - `kpis_scope_DEPT_with_bizDomain_filters_correctly()`
  - `kpis_scope_MINE_only_counts_user_visits()`
  - `kpis_prev_period_zero_yields_null_mom()`
  - `kpis_s1_zero_total_yields_null_ratio()`
- [ ] IT（Testcontainers Postgres）：准备 fixture → `build(...)` → 断言 KPI 数值。

## 完成标准

- [ ] `computeKpis` 所有字段均按契约正确填值。
- [ ] `visitsMoM` 和 `assetsS1Ratio` 在分母为 0 时返回 `null`，不抛 `ArithmeticException`。
- [ ] 单测覆盖率在 `WorkbenchLeaderOverviewService` 上 ≥ 80%（KPI 部分）。
