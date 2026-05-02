# F2: 语义指标中心页面拆分

**优先级**: P0
**状态**: DONE
**目标**: 将语义指标中心从单页堆叠改为任务页，业务人员不写 SQL，也能完成指标定义、数据集生成和发布。

## 背景

`SemanticModelingCenterPage` 已承载主题域、DWD、业务对象、Join、字段池、指标、DWS/ADS、预览、dbt、审核、发布、血缘、运行监控等多个功能。该页面需要拆分为多个小页面，每页只承载 1-2 个主要任务。

## 页面边界

| 页面 | 主功能 | 不承载 |
|------|--------|--------|
| 指标工作台 | 流程入口、待办、状态概览 | Join 画布、SQL、血缘导入 |
| 指标字典 | 指标查询、指标详情、版本/发布状态 | DWD Join、dbt 文件 |
| 主题域映射 | 治理主题域引用、自建主题域兜底 | 指标公式 |
| 业务对象 Join | 对象选择、多表 Join 画布 | 指标发布 |
| 指标可视化配置 | 字段池、维度、指标拖拽配置 | dbt 运行 |
| DWS/ADS 数据集 | 模型组合、生成预览、dbt 产物 | 审核流 |
| 审核发布与血缘 | 审核、发布、注册 BI/API、写指标血缘 | 全局血缘导入 |
| 模型运行监控 | 运行记录、状态、重试、日志入口 | 指标配置 |

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 将 `SemanticModelingCenterPage` 概览页保留为流程入口，不再展示所有子功能 |
| T02 | DONE | 将主题域、Join、指标配置、DWS/ADS、发布、运行拆为真实页面组件 |
| T03 | DONE | 页面间通过 URL 参数或后端状态传递上下文，减少本地状态耦合 |
| T04 | DONE | 固定 DWD/DWS/ADS 口径提示: DWD 是建模输入，DWS/ADS 是消费输出 |
| T05 | DONE | 保留工程师高级模式入口查看 SQL/dbt，但不作为业务人员主流程 |
| T06 | DONE | 所有拖拽画布复用 `VisualFlowCanvas` |

## 验收标准

- 每个页面只保留 1-2 个主要功能。
- 业务人员可以从 DWD/DWS/ADS 选择输入并完成指标配置，不需要写 SQL。
- DWS/ADS 页面明确“发布给 BI/大屏/API 的消费层”。
- 发布页只处理审核、发布和指标血缘，不承载全局血缘工作台。

## 涉及文件

- `source/dts-platform-webapp/src/pages/modeling/**`
- `source/dts-platform-webapp/src/components/visual-canvas/VisualFlowCanvas.tsx`
- `source/dts-platform-webapp/src/api/semanticModelingApi.ts`
