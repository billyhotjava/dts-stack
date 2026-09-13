# F1: 后端聚合端点与业务域过滤

**优先级**: P0
**状态**: READY

## 目标

新增 `GET /api/workbench/leader-overview` 聚合端点，一次调用返回领导视角首屏所需的全部数据：KPI、TOP 报表、TOP 资产、业务域矩阵（仅所领导）。同时在报表查询通路上补齐 `bizDomain` 过滤参数。

## 接口契约

```
GET /api/workbench/leader-overview
  ?scope=MINE|DEPT|ALL         // 必填；服务端按用户角色强制降级
  &deptCode=<string>           // scope=DEPT 必填；scope=ALL 可选（所领导下钻某部门）
  &bizDomain=<string>          // 可选；业务域过滤
  &timeRange=MONTH|QUARTER|YEAR // 默认 MONTH
```

响应体（聚合结构）:

```json
{
  "success": true,
  "data": {
    "generatedAt": "2026-04-24T08:00:00Z",
    "scope": "ALL",
    "effectiveDeptCode": null,
    "timeRange": "MONTH",
    "kpis": {
      "reportsTotal": 248,
      "reportsNewInPeriod": 12,
      "visitsInPeriod": 14321,
      "visitsMoM": 0.08,
      "assetsTotal": 1284,
      "assetsNewInPeriod": 54,
      "assetsS1": 84,
      "assetsS1Ratio": 0.065
    },
    "topReports": [
      { "id": "...", "title": "...", "visits": 312, "bizDomain": "FIN", "classification": "S2", "lastVisitedAt": "..." }
    ],
    "topAssets": [
      { "id": "...", "name": "...", "classification": "S1", "updatedAt": "...", "bizDomain": "RES" }
    ],
    "domainMatrix": [
      { "domain": "FIN", "visits": 5210, "color": "#4f6ef7" }
    ]
  }
}
```

`scope` 语义：
- `MINE` = 普通员工视角；口径是"当前用户个人相关"。
- `DEPT` = 部门视角；口径是"`deptCode` 指定的部门"，默认用户自身部门。
- `ALL` = 全所视角；`topAssets` / `topReports` / `kpis` 均为全所范围；`deptCode` 若非空表示所领导下钻查看某部门。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 新建 LeaderOverview DTO 与端点骨架 | P0 | READY | - |
| T02 | ReportsService 增加 bizDomain 过滤参数 | P0 | READY | - |
| T03 | 聚合实现 · KPI 主干 | P0 | READY | T01, T02 |
| T04 | 聚合实现 · TOP 报表 | P0 | READY | T01, T02 |
| T05 | 聚合实现 · TOP 资产（按密级） | P0 | READY | T01 |
| T06 | 聚合实现 · 业务域矩阵（仅 ALL） | P1 | READY | T03 |
| T07 | scope 参数角色权限降级保护 | P0 | READY | T01 |

## 完成标准

- [ ] `/api/workbench/leader-overview` 返回符合契约的 JSON。
- [ ] 非所领导请求 `scope=ALL` 被服务端强制降级为 `scope=DEPT` + 自身 `deptCode`，返回日志含降级事件。
- [ ] 业务域过滤 `bizDomain=FIN` 能贯穿 KPI、TOP 报表、TOP 资产、矩阵。
- [ ] `WorkbenchLeaderOverviewService` 核心聚合逻辑有 JUnit5 + Mockito 单测覆盖主要分支（scope × role 组合、业务域有无、时间段切换）。
- [ ] 所有 `Optional` 使用 `.orElseThrow()` 而非 `.get()`（项目 modernizer 规范）。
