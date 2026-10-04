# 指标工作台语义编排编辑器设计

日期：2026-07-03
范围：`source/dts-platform-webapp/src/pages/modeling/metric-workbench/`

## 目标

将指标工作台从“查看关系画布”推进为可编辑的语义编排界面。线条不是纯视觉注释，而是指标建模事实关系：用户可以在画布上新增、修改和校验业务对象与指标、指标与指标之间的编排关系。

第一期参考 Dify 工作流设计界面的交互模式，但转译为指标建模语言：

- 无限画布用于业务对象、指标和派生关系编排。
- 顶部工具栏提供选择、连线、自动布局、保存编排、预检。
- 选中节点或边后，配置区展示可编辑属性。
- 预检给出可执行的建模问题，而不是只展示图表框。

## 非目标

- 不新增后端表、迁移或专用 metric relation API。
- 不做多人协作、画布版本管理或服务端布局保存。
- 不实现通用流程引擎节点，例如 LLM、HTTP 工具、条件分支。
- 不把线条作为只影响视觉的本地注释。

## 现状

当前 `MetricCanvas` 已使用 `@xyflow/react`，支持：

- 业务对象节点和指标节点展示。
- 节点拖动和浏览器本地位置保存。
- 业务对象与指标之间的连线或拖放绑定。
- 绑定关系通过 `PUT /api/semantic/metrics/{id}` 回写 `objectId`。

主要缺口：

- 画布没有编辑模式工具栏。
- 边不能被选中、配置或表达不同语义。
- 指标与指标之间没有派生/依赖关系编辑入口。
- 属性区主要围绕指标节点，缺少边配置和编排预检视角。

## 设计方案

采用“前端先闭环关系编辑，复用现有指标 PUT”的方案。

关系边分两类：

| 类型 | 含义 | 写回方式 |
| --- | --- | --- |
| `OBJECT_METRIC` | 业务对象提供指标口径上下文 | 沿用 metric `objectId` |
| `METRIC_DERIVES` | 指标依赖另一个指标形成派生指标 | 写入目标指标 `formulaJson` 的 `dependsOnMetricIds` |

`METRIC_DERIVES` 不改变后端数据结构。前端保存时会把目标指标的公式 JSON 规范化为对象：

```json
{
  "type": "derived_metric",
  "dependsOnMetricIds": ["metric-a"],
  "expression": "",
  "note": ""
}
```

如果原 `formulaJson` 是合法对象，则保留原字段并合并 `dependsOnMetricIds`。如果原内容不是合法 JSON，则保存前提示用户先修复公式 JSON。

## 交互结构

### 画布工具栏

位置：指标关系画布标题栏右侧。

控件：

- 选择模式：默认，用于拖动节点和选择节点/边。
- 连线模式：突出节点 handle，提示“拖动连接业务对象、指标或派生指标”。
- 自动布局：将业务对象置左，指标置右，派生指标按依赖层级展开。
- 预检：打开编排问题列表。
- 保存编排：保存当前待写回的关系变更。

第一期保存策略是即时写回加显式反馈：

- `OBJECT_METRIC` 沿用现有连线即写回。
- `METRIC_DERIVES` 在边配置面板点击保存后写回目标指标。
- 工具栏“保存编排”用于保存仍处于草稿状态的边配置。

### 边配置面板

选中边后，底部属性区展示“关系配置”。

字段：

- 关系类型：`OBJECT_METRIC` 或 `METRIC_DERIVES`。
- 来源节点、目标节点。
- 关系说明。
- 派生表达式，仅 `METRIC_DERIVES` 显示。
- 保存按钮。
- 删除关系按钮。

删除规则：

- 删除 `OBJECT_METRIC`：将目标指标 `objectId` 置空。
- 删除 `METRIC_DERIVES`：从目标指标 `formulaJson.dependsOnMetricIds` 移除来源指标。

### 节点配置面板

选中指标节点时，沿用现有公式编辑能力，并补充依赖摘要：

- 当前业务对象。
- 上游依赖指标。
- 下游派生指标。
- 公式类型、公式 JSON、单位。

选中业务对象节点时，展示：

- 对象编码和名称。
- 已绑定指标数。
- 可拖入指标提示。

## 数据流

1. 页面加载 `domains / objects / metrics / models`。
2. `buildMetricCanvasNodes` 构造节点。
3. `buildMetricCanvasEdges` 同时从 `objectId` 和 `formulaJson.dependsOnMetricIds` 构造边。
4. 用户连接业务对象和指标时，调用现有 `updateSemanticMetric` 保存 `objectId`。
5. 用户连接指标和指标时，打开边配置面板，保存后调用 `updateSemanticMetric` 写回目标指标 `formulaJson`。
6. 保存成功后刷新 metrics，并重建节点和边。

## 校验

前端预检覆盖以下问题：

- 孤立指标：无业务对象且无上游指标。
- 环依赖：`METRIC_DERIVES` 形成循环。
- 草稿依赖：ACTIVE 指标依赖 DRAFT 指标。
- 公式缺失：派生指标无表达式或公式 JSON 不合法。
- 业务对象缺失：`objectId` 指向不存在的业务对象。

预检结果展示在属性区或弹层中，并允许点击定位到对应节点或边。

## 文件改动范围

预计改动：

- `MetricCanvas.tsx`
  - 增加工具栏、边选择、边删除、连线模式状态。
  - 将选中对象扩展为节点或边。
- `metricCanvas.helpers.ts`
  - 增加派生关系解析、边构造、环检测、公式 JSON 合并工具。
- `MetricDetailPanel.tsx`
  - 增加边配置和预检结果视图。
- `MetricBindingEdge.tsx`
  - 支持不同边类型、选中态、删除操作入口。
- `metricCanvas.helpers.test.ts`
  - 覆盖派生边构造、环检测、公式合并。
- `metricWorkbench.source-contract.test.ts`
  - 固化工具栏、关系边、预检和边配置能力。
- `worklog/v2.2.3/sprint-56-202607/README.md`
  - 记录本次从拖拽闭环推进到语义编排编辑器。

不改动：

- 后端 API。
- Liquibase。
- 菜单和路由。
- `dts-metrics-webapp`。

## 风险与处理

- `formulaJson` 历史内容可能不是 JSON 对象：保存派生关系前做解析保护，失败时不写回。
- 依赖关系可能形成环：保存前预检，阻止写回环依赖。
- 现有 `PUT /api/semantic/metrics/{id}` 是全量更新：继续使用 `buildSemanticMetricUpdatePayload` 保留 `code/name/formula/status` 等字段。
- 画布布局仍是个人偏好：节点位置继续保存在 localStorage，不作为业务事实。

## 验收标准

- 用户可以从指标节点拖线到指标节点，形成 `METRIC_DERIVES` 派生关系。
- 用户可以选中关系边并编辑关系说明或派生表达式。
- 用户可以删除业务对象绑定边和指标派生边。
- 预检能识别孤立指标、环依赖、草稿依赖和公式 JSON 问题。
- 原有业务对象 -> 指标绑定能力不回退。
- 通过 helper 单测、source-contract、`pnpm exec tsc --noEmit` 和目标文件 `git diff --check`。
