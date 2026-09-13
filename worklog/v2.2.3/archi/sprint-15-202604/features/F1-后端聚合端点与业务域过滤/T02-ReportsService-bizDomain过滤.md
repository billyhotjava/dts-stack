# T02: ReportsService 增加 bizDomain 过滤参数

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

在 `/api/reports/published` 现有通路上补齐 `bizDomain` 过滤参数，使得 T03、T04 的聚合能复用同一套查询口径。

## 技术设计

### 后端

1. 查 `ReportsResource.java` 的 `/published` 端点现状，补 `bizDomain` 查询参数：

```java
@GetMapping("/published")
public ApiResponse<List<ReportLink>> published(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String deptCode,
    @RequestParam(required = false) String type,
    @RequestParam(required = false) String queryDatasetId,
    @RequestParam(required = false) String bizDomain
) {
    return ApiResponses.ok(reportsService.listPublished(keyword, deptCode, type, queryDatasetId, bizDomain));
}
```

2. `ReportsService.listPublished(...)` 增加 `bizDomain` 入参，直通到 Repository 方法。Repository 层按 `bizDomain` 字段（字符串）做精确匹配：

```java
List<ReportLink> findByEnabledTrueAndBizDomain(String bizDomain);
```

或使用 Specification/Criteria 动态拼：

```java
private Specification<ReportLinkEntity> withFilters(String keyword, String deptCode, String type, String queryDatasetId, String bizDomain) {
    return (root, cq, cb) -> {
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.isTrue(root.get("enabled")));
        if (StringUtils.hasText(keyword)) { ... }
        if (StringUtils.hasText(bizDomain)) {
            ps.add(cb.equal(root.get("bizDomain"), bizDomain));
        }
        // 既有 deptCode / type / queryDatasetId 条件保留
        return cb.and(ps.toArray(Predicate[]::new));
    };
}
```

> **先查现状**：读 `ReportsResource.java` + `ReportsService.java` 确认当前查询方式，沿用既有风格（Specification / Query by Example / 自定义 Repo）。本任务**不要**改变既有风格，只在同一模式下加一个字段。

3. 确认 `ReportLinkEntity`（或 JPA 实体名）上有 `bizDomain` 字段；若缺少则加 `@Column(name = "biz_domain") String bizDomain;` + Liquibase changeset 增加列。**如果已有**（设计稿提到"`bizDomain` 字段（表级 string）"），跳过。

### 前端

在 `source/dts-platform-webapp/src/api/services/reportsService.ts` 的 `getPublishedReports`、`listAll` 及 `ReportLink` / `ReportLinkUpsertRequest` 类型上加 `bizDomain?: string`：

```ts
export type ReportLink = {
  // ...既有字段
  bizDomain?: string | null;
};

function getPublishedReports(params?: {
  keyword?: string;
  deptCode?: string;
  type?: string;
  queryDatasetId?: string;
  bizDomain?: string;
}) {
  return apiClient.get<ReportLink[]>({ url: "/reports/published", params });
}
```

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ReportsResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/reports/ReportsService.java`（或同等服务类）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/reports/ReportLinkRepository.java`（若自定义方法）
- 可能新增 Liquibase changeset（仅当实体缺少 `biz_domain` 列时）
- `source/dts-platform-webapp/src/api/services/reportsService.ts`

## 验证

- [ ] `./mvnw -pl source/dts-platform test` 通过（新增 `ReportsResourceIT` 或 `ReportsServiceTest` 验证 `bizDomain` 过滤）。
- [ ] curl：`GET /api/reports/published?bizDomain=FIN` 只返回 `bizDomain=FIN` 的条目。
- [ ] 前端 `reportsService.ts` TS 编译通过；`ReportLink.bizDomain` 在编辑/查看场景不引入 any。

## 完成标准

- [ ] 端点参数、服务方法签名、前端类型三处同步新增 `bizDomain`。
- [ ] 至少一个 `@ParameterizedTest`（JUnit5）覆盖"`bizDomain` 有 / 无 / 不存在的 code"三种情况。
- [ ] 无回归：既有 `keyword / deptCode / type / queryDatasetId` 过滤仍正常。
