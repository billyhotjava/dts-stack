# Sprint-9：项目看板系统（DTS 标准链路 + 统一入口专题）

## 背景

客户在 [项目主体域数据说明-20260310.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.1/req/pm/项目主体域数据说明-20260310.xlsx) 中给出了项目主体域的字段、枚举与指标口径。`v2.2.1` 的 `sprint-5/6` 已完成 Excel → ODS → DWD → DWS → ADS 的基础建模，但当前前端只具备单张“项目管理作战台”模板，无法形成统一入口的项目分析系统。

本 Sprint 的目标是在 `source/dts-analytics-webapp/modern` 内构建一个面向领导、科长和信息科的“小型项目看板系统”，把“项目风险进度”升级为一个统一入口的专题驾驶舱，并把项目主体域接入链路纳入 DTS 标准 ETL/ELT 体系：`上传 Excel/CSV -> ODS -> DWD/DWS/ADS -> 专题看板`。

## 目标

1. 建立一个统一入口的项目看板系统，而不是多个散装模板入口
2. 内置 5 个主题视图：总览趋势、计划执行、风险归因、重大项目树、口径支撑
3. 补齐 `重大项目 -> 子项目 -> 节点` 的语义层、项目域批次结构和正式测试数据
4. 提供项目管理专用可视化组件，重点覆盖树状进度、甘特图、趋势分析和延期归因
5. 将当前演示 TSV 从正式运行链路中剥离，只保留为开发/测试夹具
6. 形成可演示版本，并输出客户补数清单

## 范围

### 数据与建模

- `services/dts-dbt/models/`
- `services/dts-dbt/seeds/`
- `services/dts-dbt/tests/`
- `services/dts-dbt/deploy/`
- `worklog/v2.2.1/req/pm/`
- `worklog/v2.2.1/sprint-9/it/`

### 后端

- `source/dts-analytics`
- `source/dts-platform`
- 必要的 `resource / service / dto / repository`

### 前端

- `source/dts-analytics-webapp/modern/src/routes.tsx`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`
- `source/dts-analytics-webapp/modern/src/pages/`
- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/`

## 设计文档

- [project-cockpit-system-design.md](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-9/project-cockpit-system-design.md)
- [project-cockpit-system-plan.md](/opt/prod/s10/s10-stack/worklog/v2.2.1/sprint-9/project-cockpit-system-plan.md)

## Task 列表

### 批次一：正式数据链路与测试数据（PMS-001 ~ PMS-004）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| PMS-001 | 项目层级映射模型、批次策略与测试数据蓝图 | 数据设计 | 1天 | WIP |
| PMS-002 | 项目域 ODS / 批次 / 问题表与映射维表 | 数据建模 | 2天 | TODO |
| PMS-003 | 节点 enrich、延期原因归类与容错清洗 | 数据建模 | 1.5天 | TODO |
| PMS-004 | 趋势与树状快照 ADS / 校验 SQL / 数据质量统计 | 数据建模 | 2天 | TODO |

### 批次二：正式 API 与统一入口系统壳页（PMS-005 ~ PMS-007）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| PMS-005 | 项目看板正式聚合 API（消费 ADS，不消费 TSV） | 后端 | 2天 | TODO |
| PMS-006 | `/analytics/project-cockpit` 路由与统一壳页 | 前端 | 1.5天 | DONE |
| PMS-007 | 全局筛选状态、URL 同步与共享上下文 | 前端 | 1天 | DONE |

### 批次三：主题视图实现（PMS-008 ~ PMS-012）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| PMS-008 | 总览趋势视图 | 前端 | 1.5天 | DONE |
| PMS-009 | 计划执行视图与甘特图增强 | 前端 | 2天 | DONE |
| PMS-010 | 风险归因视图 | 前端 | 1.5天 | DONE |
| PMS-011 | 重大项目树视图与树状进度看板 | 前端 | 2.5天 | DONE |
| PMS-012 | 口径支撑视图 | 前端 | 1天 | DONE |

### 批次四：组件沉淀、口径支撑与体验收口（PMS-013 ~ PMS-014）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| PMS-013 | 项目管理组件包沉淀 | 前端 | 2天 | DONE |
| PMS-014 | 测试数据生成、接入验证与演示 runbook | 验证 | 1.5天 | WIP |

## 本 Sprint 不做

- 不接入客户真实主数据系统，但保留主数据接口占位；第一阶段由测试数据、项目域 ODS 与映射表支撑
- 不做项目写回、审批流、任务派发或工单闭环
- 不做多角色细粒度权限分屏
- 不做移动端专项适配
- 不在本轮把全部项目管理组件完全上升为通用 screen marketplace 资产
- 不在本轮落完整 lakehouse / Iceberg / Delta 体系，第一阶段先使用 DTS 现有 Postgres + dbt 数仓链路

## 集成测试

`it/` 目录存放验证入口和执行说明，覆盖：

- 映射表 + 测试数据导入
- dbt 模型从映射维表到 ADS 的全链路验证
- 项目看板后端聚合 API
- 统一入口壳页与 5 个主题视图
- 树状进度看板与甘特图的主要交互
- 演示数据集与 runbook

## 本 Sprint 已完成

- 已完成统一入口 `/analytics/project-cockpit` 和 5 个主题视图的第一版界面
- 已完成 `重大项目 -> 子项目 -> 节点` 的第一版映射种子、dbt 语义层和 ADS 快照
- 已沉淀树状进度看板、项目甘特板、延期原因矩阵、项目健康卡、口径支撑卡
- 下一阶段重点是把演示 TSV 从正式运行链路中剥离，接通项目域 ODS / 批次 / 质量 / ADS 正式路径，并保留主数据接口占位以便后续切换

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：

- `TODO` = 未开始
- `WIP` = 进行中
- `DONE` = 已完成
- `BLOCK` = 阻塞
