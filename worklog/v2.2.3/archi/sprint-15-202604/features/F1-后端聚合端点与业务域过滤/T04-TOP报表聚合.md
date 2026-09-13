# T04: 聚合实现 · TOP 报表

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标

在 `WorkbenchLeaderOverviewService` 中实现 TOP 报表聚合，填充 `LeaderOverviewResponse.topReports`。

## 技术设计

### 口径

| scope | 排序 | 取 N |
|---|---|---|
| MINE | 个人最近访问时间降序 | 10 |
| DEPT | 部门在 period 内访问量降序 | 10 |
| ALL | 全所在 period 内访问量降序 | 10 |

ALL 下钻到 `deptCode` 非空时等同 DEPT 逻辑，只是视角切换：排序依然是访问量降序。

### 实现

```java
private List<TopReport> computeTopReports(
    String userLogin, String scope, String deptCode, String bizDomain, TimeWindow w
) {
    if ("MINE".equals(scope)) {
        return visitRepo.findTopRecentByUser(userLogin, 10)
            .stream()
            .map(this::toTopReport)
            .toList();
    }

    // DEPT / ALL
    return visitRepo.aggregateTopReports(scope, deptCode, bizDomain, w.start(), w.end(), 10)
        .stream()
        .map(this::toTopReport)
        .toList();
}

private TopReport toTopReport(ReportVisitAggregateRow row) {
    return new TopReport(
        row.reportId().toString(),
        row.title(),
        row.visits(),
        row.bizDomain(),
        row.classification(),
        row.lastVisitedAt()
    );
}
```

### Repository / JPQL

在 `ReportVisitRepository` 新增两个投影查询（若无同等接口）：

```java
public interface ReportVisitRepository extends JpaRepository<ReportVisitEntity, UUID> {

    // MINE 场景
    @Query("""
      select new com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow(
        v.reportId, r.title, 1L, r.bizDomain, r.classification, max(v.visitedAt))
      from ReportVisitEntity v join ReportLinkEntity r on r.id = v.reportId
      where v.userLogin = :userLogin
      group by v.reportId, r.title, r.bizDomain, r.classification
      order by max(v.visitedAt) desc
    """)
    List<ReportVisitAggregateRow> findTopRecentByUser(@Param("userLogin") String userLogin, Pageable pageable);

    // DEPT / ALL 聚合
    @Query("""
      select new com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow(
        v.reportId, r.title, count(v), r.bizDomain, r.classification, max(v.visitedAt))
      from ReportVisitEntity v join ReportLinkEntity r on r.id = v.reportId
      where v.visitedAt between :start and :end
        and (:scope = 'ALL' and :deptCode is null or :deptCode member of r.deptCodes)
        and (:bizDomain is null or r.bizDomain = :bizDomain)
      group by v.reportId, r.title, r.bizDomain, r.classification
      order by count(v) desc
    """)
    List<ReportVisitAggregateRow> aggregateTopReports(
        @Param("scope") String scope,
        @Param("deptCode") String deptCode,
        @Param("bizDomain") String bizDomain,
        @Param("start") Instant start,
        @Param("end") Instant end,
        Pageable pageable
    );
}
```

Pageable 传 `PageRequest.of(0, 10)`。

### 辅助 DTO

```java
package com.yuzhi.dts.platform.service.workbench.dto;

import java.time.Instant;
import java.util.UUID;

public record ReportVisitAggregateRow(
    UUID reportId,
    String title,
    long visits,
    String bizDomain,
    String classification,
    Instant lastVisitedAt
) {}
```

### 注意

- `:deptCode member of r.deptCodes` 的 `deptCodes` 在 JPA 实体上是 `@ElementCollection` 或关联表。若实现是字符串 CSV，则改为 `locate(:deptCode, r.deptCodes) > 0` 或 native query。依据实际实体类型调整。
- ALL 且 `deptCode = null` 的分支：JPA 不支持"条件里一部分始终为 true"，必须在 Service 层分发成两个查询：`aggregateTopReportsAllDept(...)` 与 `aggregateTopReportsForDept(...)`。保持可读性而非挤进一条 JPQL。

## 影响范围

- `ReportVisitRepository.java`（新增 2 个查询）
- `ReportVisitAggregateRow`（新增 DTO）
- `WorkbenchLeaderOverviewService.java`（调用与装配）

## 验证

- [ ] 单测：
  - `topReports_scope_MINE_orders_by_last_visited()`
  - `topReports_scope_DEPT_filters_by_deptCode_and_orders_by_count()`
  - `topReports_scope_ALL_without_deptCode_aggregates_all()`
  - `topReports_applies_bizDomain_filter()`
  - `topReports_limits_to_10()`
- [ ] IT：登录 → curl → 返回前 10 条且降序正确。

## 完成标准

- [ ] 所有 scope 下 TOP 报表字段齐全（id / title / visits / bizDomain / classification / lastVisitedAt）。
- [ ] MINE 场景 `visits` 取最近 30 天去重后访问次数；DEPT/ALL 场景取 period 内聚合。
- [ ] 有空集场景（`topReports = []`）的单测覆盖。
