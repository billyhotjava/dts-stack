# P1-02 血缘影响分析可用性增强

`status`: `done`
`priority`: `P1`

## 目标

提升血缘图在真实场景下的可读性和可筛选能力。

## 后端实施点

1. 血缘查询支持层级、项目、时间窗口过滤。
2. 影响分析结果增加变更范围统计。

## 前端实施点

1. 血缘图支持按层级折叠（ODS/DWD/DWS/ADS）。
2. 节点侧边栏显示来源、负责人、最近变更。
3. 影响分析结果支持导出（页面内）。

## 验收标准

- 可在页面内定位某表的上下游与影响范围。
- 大图场景下仍能快速筛选目标链路。

## 实施记录（2026-02-24）

1. 后端 `CatalogLineageResource` 新增过滤参数：
   - `layers`（ODS/DWD/DWS/ADS/DIM，多值逗号分隔）
   - `changedWithinHours`（时间窗口）
   - `sourceId`（来源数据源）
2. 影响分析结果新增：
   - `impactStats.layerNodeCounts`
   - `impactStats.relationTypeCounts`
   - `impactStats.changedNodeCount`
3. 节点/边 DTO 补充：
   - 节点 `owner/sourceId/lastModifiedAt/snapshotTime`
   - 边 `upstreamLayer/downstreamLayer/lastModifiedAt` 等侧边栏/筛选所需字段
4. 前端 `LineagePage` 可用性增强：
   - 按层级折叠展示节点（分层 Collapse）
   - 快速筛选（关键词）
   - 节点详情侧边栏（来源、负责人、最近变更）
   - 页面内导出（节点+关系 CSV）
