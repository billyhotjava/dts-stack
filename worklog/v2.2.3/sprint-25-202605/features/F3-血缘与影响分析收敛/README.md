# F3: 血缘与影响分析收敛

**优先级**: P0
**状态**: DONE
**目标**: 将血缘定义为平台级横向能力，拆分全局血缘工作台，并为各中心提供上下文血缘视图。

## 背景

当前 `LineagePage` 同时承载同步 Addax、导入 dbt、OpenMetadata 缓存、节点表、边表、字段血缘、血缘图、diff、导出等功能。需要拆成面向不同任务的页面。

## 血缘分层

| 层次 | 职责 |
|------|------|
| 统一血缘底座 | 存储 dataset / column / job lineage，接入 dbt、Addax、OpenLineage、OpenMetadata |
| 全局血缘工作台 | 图谱、字段血缘、影响分析、导入同步、快照对比 |
| 上下文血缘 | 资产详情、开发模型、语义指标发布、治理变更中的轻量血缘视图 |

## 页面拆分建议

| 页面 | 主功能 |
|------|--------|
| 血缘图谱 | 资产选择、方向、深度、图谱查看 |
| 字段血缘 | 字段级来源、表达式、置信度 |
| 影响分析 | 上下游影响范围、变更风险 |
| 血缘导入 | dbt manifest、Addax、OpenMetadata、OpenLineage 同步 |
| 快照对比 | 时间旅行、diff、变更边 |

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 将菜单 `血缘视图` 改为 `血缘与影响分析` |
| T02 | DONE | 拆分 `LineagePage` 为图谱、字段、影响、导入、diff 五个任务区或子页面 |
| T03 | DONE | 资产详情保留轻量血缘预览，跳转全局工作台 |
| T04 | DONE | 语义指标发布页只展示指标上下文血缘和写血缘动作 |
| T05 | DONE | 数据开发模型/任务页只提供模型上下文血缘入口 |
| T06 | DONE | 所有血缘图统一使用 `VisualFlowCanvas` |

## 验收标准

- 全局血缘页不再一个页面展示所有能力。
- 平台管理员能找到血缘导入/同步入口。
- 业务人员在语义指标中心只看到指标相关血缘。
- 数据工程师在数据开发中心只看到模型/任务相关血缘。
- 资产详情仍能快速查看当前资产上下游。

## 涉及文件

- `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/CatalogLineageResource.java`
