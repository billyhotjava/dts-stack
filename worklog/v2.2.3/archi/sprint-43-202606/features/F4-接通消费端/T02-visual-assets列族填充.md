# T02: dts-metrics visual-assets 列族填充

**优先级**: P0
**状态**: READY
**依赖**: F3-T02

## 目标
修 dts-metrics `MetricVisualAssetResource` 空列族根因——从平台透出的列族填 `dimensionColumns/metricColumns/timeColumns/grain/standardCodes`，让 F3 可视化工作台在真实数据下能选字段建模。

## 技术设计
- `MetricVisualAssetResource.toVisualAsset`：把当前 5 处硬编码 `List.of()` 替换为平台 assets-v2（F3-T02）透出的列族；补 `standardCodes` 字段（`VisualAssetSummary` 需加该字段）。
- 若 assets-v2 列表不含列族（仅 schema-contract 含），评估：列表懒加载 or 详情端点补取（避免 N+1）。
- 注意 dts-metrics 当前在 v2.2.3 基线 vs sprint-35b 分支差异（见记忆 [[dts-metrics-branch-baseline]]）——以整合后落点为准。

## 影响范围
- `dts-metrics`：`MetricVisualAssetResource` + `VisualAssetSummary`（加 standardCodes）。

## 验证
- [ ] 有 meta 的资产 visual-assets 列族非空。
- [ ] 前端字段树/DWD 候选可拿到可选指标/维度（解锁 review 报告记录的 CRITICAL 数据空壳）。

## 完成标准
- [ ] 列族填充贯通、空壳根因关闭、单测覆盖。
