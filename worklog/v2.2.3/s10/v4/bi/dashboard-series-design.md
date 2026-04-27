# 领导驾驶舱大屏系列设计草案

版本：v0.1  
范围：`worklog/v2.2.3/s10/v4/bi`

## 目标

设计一套面向领导层和管理层的 BI 驾驶舱大屏系列。它不是把所有图表堆在一张屏上，而是按管理决策路径组织：

1. `L0 领导总览`: 看全局态势、关键异常、需要拍板的事项。
2. `L1 中层看板`: 看专业域或部门视角下的原因、责任和趋势。
3. `L2 明细看板`: 看项目、风险、质量问题、技术变更、任务节点等具体清单。

## 可借用内容

`../pjm/screen-instances/` 中值得复用的是指标口径和下钻关系：

- `gpmc-strategic-overview-v3.json`: 战略层总览，包含项目运营、质量、延期、风险、成本、技术状态等综合指标。
- `gpmc-overview-v3.json`: 项目综合看板，包含项目总数、完成率、质量问题、技术变更、高风险数量等首页指标。
- `gpmc-execution-board-v3.json`: 执行监控，包含里程碑、超期、未完成、高风险未完成等交付指标。
- `gpmc-quality-board-v3.json`: 质量跟进，包含质量问题总数、未闭环数、闭环率、问题分布和趋势。
- `gpmc-tech-state-board-v3.json`: 技术状态，包含变更单、签署率、整改率、科室变更和变更清单。
- `gpmc-risk-board-v3.json`: 风险预警，包含风险总数、高中风险、未释放、释放率、分类和项目汇总。
- `gpmc-drill-*.json`: 底层明细页，可作为 L2 明细看板的数据字段参考。

不建议复用旧实例的视觉样式。旧实例适合做平台组件能力验证，但领导驾驶舱需要更清晰、更克制、更稳定的表达。

## 信息架构

### L0 领导总览

建议页面名：`overview`

核心问题：

- 当前整体是否健康。
- 哪些项目、部门或风险需要领导介入。
- 未来 30/60/90 天有哪些交付压力。
- 质量、风险、变更、资源、成本分别是否拖累交付。

建议模块：

- 组合健康度：综合进度、风险、质量闭环、技术变更、资源负荷、成本偏差。
- 核心 KPI：项目完成率、里程碑达成率、高风险数、未闭环质量问题、未整改技术变更。
- 交付地图：按异常优先排序，展示重点项目的状态、进度、责任人、下一动作。
- 管理层关注：只展示需要决策或协调的 Top 5 事项。
- 趋势与原因：里程碑到期、质量闭环趋势、风险矩阵、资源负荷。

### L1 中层领导看板

建议页面：

- `delivery-board`: 交付执行看板。
- `risk-board`: 风险预警看板。
- `quality-board`: 质量闭环看板。
- `tech-change-board`: 技术状态与变更看板。
- `resource-cost-board`: 资源与成本看板。

设计要求：

- 每张 L1 页面只聚焦一个管理主题。
- 顶部保留同一套 KPI 带，方便横向比较。
- 中间区域展示趋势、结构分布和责任分布。
- 右侧或底部展示行动清单，必须包含责任人、截止日期、当前状态。
- 所有异常项必须能下钻到 L2 明细。

### L2 明细看板

建议页面：

- `project-detail`: 单项目作战室。
- `milestone-detail`: 里程碑和任务明细。
- `risk-detail`: 风险项闭环明细。
- `quality-detail`: 质量问题闭环明细。
- `change-detail`: 技术变更签署和整改明细。
- `dept-detail`: 部门资源、任务和异常明细。

设计要求：

- L2 可以更密集，但仍沿用同一视觉规范。
- 明细表必须支持状态标签、责任人、日期、关联项目和处理动作。
- 页面顶部保留面包屑、筛选条件和返回上级入口。
- 明细页不追求炫酷，核心是准确、可追溯、可落责。

## 统一视觉方向

参考 `../pjm/bi/demo/preview1.html` 的浅色企业版方向，建议确定为“浅色治理驾驶舱”：

- 画布：1920 x 1080 优先，兼容浏览器缩放。
- 背景：浅灰蓝渐变，避免纯黑科技风和高饱和霓虹风。
- 卡片：白色半透明卡片、轻阴影、统一圆角。
- 主色：蓝色用于系统主色，绿色表示正常，琥珀表示预警，红色表示风险。
- 图表：减少装饰线条，优先使用条形、折线、矩阵、表格、Gantt/时间轴。
- 字体：标题清晰、数字突出，避免全屏字号竞争。
- 动效：只保留页面加载、卡片浮现和切换反馈，不做无意义闪烁。

建议布局：

- 顶部：标题、统计周期、范围筛选、视图切换。
- 第一行：1 个综合健康卡加 4 个关键 KPI。
- 主区域：左侧或中间放核心叙事图，例如交付地图或趋势主图。
- 右侧：管理层关注、异常 TopN、待决策事项。
- 底部：原因分析图和专业域入口。

## 指标口径草案

共享筛选：

- `dateFrom`
- `dateTo`
- `deptId`
- `projectNo`
- `riskLevel`

L0 指标：

- `portfolio_health_score`: 组合健康度。
- `project_completion_rate`: 项目完成率。
- `milestone_on_time_rate`: 里程碑按期率。
- `high_risk_count`: 高风险数量。
- `unclosed_quality_issue_count`: 未闭环质量问题数。
- `unsigned_change_count`: 未签署技术变更数。
- `unrectified_change_count`: 未整改技术变更数。
- `resource_overload_dept_count`: 资源超载部门数。
- `cost_deviation_rate`: 成本偏差率。

L1 指标方向：

- 交付：未完成数、超期任务数、高风险未完成、里程碑达成率、节点超期率。
- 风险：风险总数、高风险数、中风险数、未释放风险数、风险释放率。
- 质量：质量问题总数、未闭环数、闭环率、Top 项目、Top 部门。
- 技术状态：变更单总数、未签署数、签署率、未整改数、整改完成率。
- 资源成本：部门负荷、关键资源冲突、预算消耗、成本偏差、资源瓶颈。

## 下钻路径

建议固定三层路径：

- `overview -> delivery-board -> milestone-detail`
- `overview -> risk-board -> risk-detail`
- `overview -> quality-board -> quality-detail`
- `overview -> tech-change-board -> change-detail`
- `overview -> resource-cost-board -> dept-detail`
- `overview -> project-detail`

下钻参数应显式传递：

- `dateFrom`
- `dateTo`
- `deptId`
- `projectNo`
- `riskLevel`
- `status`
- `sourcePanel`

## 文件规划

建议后续目录：

```text
bi/
  README.md
  dashboard-series-design.md
  metric-catalog.md
  page-map.md
  demo/
    README.md
    overview.html
    delivery-board.html
    risk-board.html
    quality-board.html
    tech-change-board.html
    detail-template.html
```

## 第一阶段建议

1. 确认视觉基线：以浅色企业版为主，统一 CSS token、卡片和状态标签。
2. 固化 L0 总览布局：先做 `demo/overview.html`，不要先铺满所有专业域。
3. 抽取指标目录：从 `screen-instances` 中梳理指标、SQL、参数和字段，形成 `metric-catalog.md`。
4. 设计 L1 模板：先做交付、风险、质量、技术状态 4 张看板。
5. 设计 L2 模板：统一明细页表格、状态流转、责任追踪和返回路径。

## 当前判断

`preview1.html` 的方向是对的：浅色、卡片化、突出“管理层关注”和“交付地图”。后续应把它从单页 demo 提炼成设计系统，而不是复制成多张相似页面。旧 `screen-instances` 的价值在指标和数据绑定，不在视觉。
