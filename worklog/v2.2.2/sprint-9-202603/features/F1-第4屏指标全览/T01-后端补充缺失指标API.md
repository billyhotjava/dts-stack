# T01: 后端补充缺失指标 API

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
新增 `/api/project-cockpit/screen/metrics-overview` 端点，返回 project3.xlsx 全部 34 个指标

## 技术设计

### 1. 补充缺失的计算字段
在 `ProjectCockpitService` 的 `computeOverviewPeriodMetrics` 基础上，新增或扩展：
- 不正常待变更节点数（排除一般节点）
- 超期未完成未变更（排除一般节点）
- 超期未完成已变更（排除一般节点）
- 超期已完成未变更（排除一般节点）
- 异常率、超期率
- 未完成高/中/里程碑/重大/重要节点数
- 里程碑正常待完成数
- 高/中风险节点数、里程碑/重大/重要节点总数

### 2. 新增 `buildMetricsOverviewKpis` 方法
返回 ArrayNode 包含 34 个 KPI 对象，每个：
```json
{ "key": "periodNodeTotalCount", "label": "项目本周期节点总数", "value": "644", "unit": "个", "dimension": "项目（含一般节点）" }
```
`dimension` 字段标识所属维度，前端按此分组。

### 3. Resource 端点
```java
@GetMapping(path = "/screen/metrics-overview")
public ResponseEntity<?> screenMetricsOverview(/* 标准筛选参数 */)
```

## 影响范围
- `ProjectCockpitService.java` — 新增 `screenMetricsOverview()` + `buildMetricsOverviewKpis()`
- `ProjectCockpitResource.java` — 新增端点
- `PublicResource.java` — 新增公开端点（大屏分享链接需要）

## 验证
- [ ] `GET /api/project-cockpit/screen/metrics-overview` 返回 34 个指标
- [ ] 每个指标有 key, label, value, unit, dimension 字段
- [ ] 数值与手动计算一致

## 完成标准
- [ ] 34 个指标全部覆盖 project3.xlsx 的定义
