# GPMC Phase 2 Design

## 背景

当前 [gpmc](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/gpmc) 模块已经具备视觉 demo、战略层/管控层/执行层的基础交互骨架，但仍完全依赖 [mockData.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/gpmc/mockData.ts)。现阶段需要从“视觉 demo”进入“正式业务建模”，并明确以下目标：

- 以 9 张业务源表作为正式数据来源
- 形成 `1 个战略层大屏 + 5 个管控层看板 + 执行层只读下钻页`
- 同时沉淀为 `analytics-webapp` 大屏设计器可继续编辑的 6 套模板

## 现状评估

### 前端现状

- [GpmcPage.tsx](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/gpmc/GpmcPage.tsx) 当前组织了 6 个页面：
  - 综合态势
  - 项目执行
  - 质量与技术状态
  - 成本
  - 风险
  - 资源
- [GpmcApp.tsx](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/gpmc/GpmcApp.tsx) 当前只提供本地状态：
  - `screen`
  - `theme`
  - `layer`
  - `drillDown`
- 当前下钻只是本地层级切换，不是有独立承载页的真实执行层页面
- “资源负载与协同”是 demo 阶段页面，后续应删除，资源信息只保留在执行层只读下钻页
- “质量与技术状态”后续要拆成两个独立看板：
  - 质量信息与跟进措施
  - 技术状态与跟进

### 后端现状

- [ProjectCockpitResource.java](/opt/prod/s10/s10-stack/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java) 和 [ProjectCockpitService.java](/opt/prod/s10/s10-stack/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java) 已经提供了“项目看板”的聚合与 screen 风格 API
- 现有后端语义主要围绕：
  - `ods_project_subject_domain`
  - `biz_dwd_project_node_enriched`
  - `pm_dim_*`
  - `biz_dws_* / biz_ads_*`
- 这套服务可复用其“按屏输出 view model”的模式，但数据源口径需要切换到新的 9 张源表体系

## 数据源与主题域

### 9 张源表

1. 项目信息表
2. 进度跟进措施表
3. 质量信息汇总表
4. 质量跟进措施表
5. 技术状态信息汇总表
6. 技术状态跟进措施表
7. 风险信息汇总表
8. 风险跟进措施表
9. 成本核算基本表

### 5 个主题域

#### 1. 项目执行域

来源表：

- 项目信息表
- 进度跟进措施表

主键：

- 主键：`项目编号`
- 次级键：`分系统/分任务`、`节点任务及目标`

承载指标：

- 项目总数 / 在建 / 延期 / 完成率
- 里程碑达成率
- 延期 TOP10
- 甘特图
- 阻塞链路
- 责任人分析

#### 2. 质量域

来源表：

- 质量信息汇总表
- 质量跟进措施表

主键：

- 主键：`项目编号`
- 次级键：质量问题唯一标识
- 若无显式问题编号，第一版允许使用组合键：`项目编号 + 问题描述 + 发生时间`

承载指标：

- 新增质量问题数
- 现存质量问题数
- 闭环率
- 分类分布
- 问题清单
- 措施清单

#### 3. 技术状态域

来源表：

- 技术状态信息汇总表
- 技术状态跟进措施表

主键：

- 主键：`项目编号`
- 次级键：`技术状态名称/代号`

承载指标：

- 技术状态变更数
- 文件签署完成率
- 未闭环数
- 变更类别分布
- 变更清单
- 措施清单

#### 4. 风险域

来源表：

- 风险信息汇总表
- 风险跟进措施表

主键：

- 主键：`项目编号`
- 次级键：`风险名称`

承载指标：

- 风险总数
- 高风险 / 中风险数
- 风险矩阵
- 风险分类分布
- 风险闭环率
- 风险清单
- 措施清单

#### 5. 成本域

来源表：

- 成本核算基本表
- 项目信息表

主键：

- 主键：`项目编号`
- 次级键：`统计周期`

承载指标：

- 预算总额
- 实际执行额
- 执行率
- 偏差额 / 偏差率
- 月度支出趋势
- 部门预算执行
- 项目成本明细

## 三层信息架构

### 1. 战略层

定位：

- 集团领导一屏看全局
- 关键词：态势感知

页面：

- 项目综合态势总览大屏

核心模块：

- 项目总数 / 在建 / 延期 / 高风险
- 投资 vs 执行
- 完成率趋势
- 风险热力分布
- 各事业部排名
- 异常摘要

### 2. 管控层

定位：

- PMO / 部门负责人定位问题
- 关键词：问题定位

页面：

- 项目执行监控
- 质量信息与跟进措施
- 技术状态与跟进
- 成本与预算控制
- 风险与预警中心

### 3. 执行层

定位：

- 下钻后的只读执行页
- 关键词：执行控制

约束：

- 必须有页面承载
- 全部只读
- 不做在线编辑、不做回写

通用模块：

- 对象概要信息
- 明细表
- 措施表
- 时间线 / 甘特 / 闭环状态

## 统一筛选与关联规则

### 全局筛选主轴

- 时间范围：`dateFrom` / `dateTo`
- 事业部 / 责任科室：`deptId`
- 项目：`projectNo` / `projectId`
- 风险等级：`riskLevel`

### 统一主关联键

- 统一主键：`项目编号`
- 各主题域补充次级键，不在前端页面自行拼接口径

## 指标口径来源

### 字段模板

- [project1.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.2/req/s10/project1.xlsx) 提供节点/项目类字段框架

### 枚举与状态字典

- [project2.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.2/req/s10/project2.xlsx) 提供完成情况、跟进措施类别、闭环状态、更改类别、文件签署状态、风险等级等口径

### 指标计算逻辑

- [project3.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.2/req/s10/project3.xlsx)
- [project_ref.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.2/req/s10/project_ref.xlsx)

这三份资料意味着后续指标实现不能只看字段命名，必须同步落一层“指标口径矩阵”。

## 模板化交付要求

本次最终交付不是单一业务页，而是双产物：

### 业务层

- 1 个战略层大屏
- 5 个管控层看板
- 执行层只读下钻页

### 模板层

以下 6 套都必须在 `analytics-webapp` 大屏设计器中可继续编辑：

- `gpmc-strategic-overview`
- `gpmc-execution-board`
- `gpmc-quality-board`
- `gpmc-tech-state-board`
- `gpmc-cost-board`
- `gpmc-risk-board`

## 模板层与 screens 的衔接方式

现有模板体系可直接复用：

- [screenTemplates.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts)
- [projectManagementCommandCenterTemplate.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts)
- [specV2.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/specV2.ts)
- [apiDataSourceRuntime.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/apiDataSourceRuntime.ts)

推荐方式：

- 模板优先，业务页复用模板语义
- 不做“页面一套、模板一套”的双轨实现

每个模板统一包含：

- 全局变量：`dateFrom`、`dateTo`、`deptId`、`projectNo`、`riskLevel`
- API 数据源绑定位
- 下钻跳转动作
- 设计器支持的标准组件组合

## 实施建议

### 阶段 1：语义建模

- 九张源表字段归类
- 指标口径矩阵
- 页面信息架构
- 下钻承载页模型

### 阶段 2：数据接口

- 在 `dts-analytics` 新建 GPMC 专用 facade / view model
- 按页面输出 API，而不是按源表输出 API

### 阶段 3：页面与模板并行交付

- 将 [gpmc](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/gpmc) 从 mock 升级到真实 view model
- 同时在 [screenTemplates.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts) 注册 6 套内置模板

## 结论

当前 GPMC demo 的价值主要在：

- 视觉风格
- 页面布局骨架
- 三层交互雏形

真正需要重建的是：

- 数据语义层
- 指标口径层
- 页面与模板的一致性
- 执行层只读下钻页模型
