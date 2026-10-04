# 项目看板系统设计稿

日期：2026-03-15  
Sprint：`worklog/v2.2.1/sprint-9`

## 1. 设计目标

构建一个统一入口的“项目看板系统”，面向三类使用者：

- 高层领导：看整体项目态势、趋势、重大风险与重点项目
- 中层科长：看计划执行、延期归因、责任科室与异常清单
- 信息科：看数据口径、更新时间、字段覆盖与演示支撑

这套系统不是多个离散模板，而是一个在同一上下文中切换主题视图的专题驾驶舱。

## 2. 当前基础

### 2.1 需求与测试数据

- 需求附件：[项目主体域数据说明-20260310.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.1/req/pm/项目主体域数据说明-20260310.xlsx)
- 测试数据：[项目进度测试数据-20260312.xlsx](/opt/prod/s10/s10-stack/worklog/v2.2.1/req/pm/项目进度测试数据-20260312.xlsx)

当前附件已明确：

- `29` 个原始字段
- `12` 组枚举说明
- `35` 个指标口径

### 2.2 已有建模资产

`sprint-5/6` 已落地项目主体域的基础建模：

- [biz_dwd_project_node.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/dwd/model/biz_dwd_project_node.sql)
- [biz_dws_period_node_summary.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/dws/model/biz_dws_period_node_summary.sql)
- [biz_dws_period_risk_summary.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/dws/model/biz_dws_period_risk_summary.sql)
- [biz_ads_project_kpi_overview.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/ads/model/biz_ads_project_kpi_overview.sql)
- [biz_ads_project_milestone_kpi.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/ads/model/biz_ads_project_milestone_kpi.sql)
- [biz_ads_project_incomplete_risk.sql](/opt/prod/s10/s10-stack/services/dts-dbt/models/ads/model/biz_ads_project_incomplete_risk.sql)

这些模型已能支撑“项目、节点、风险、周期趋势”的基础分析。

### 2.3 已有前端基础

`analytics` 现代前端已有大屏设计器、公共预览、模板库与项目管理模板：

- [screenTemplates.ts](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts)
- [routes.tsx](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/routes.tsx)
- [ScreensPage.tsx](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/ScreensPage.tsx)
- [PublicScreenPage.tsx](/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx)

但当前仅有单张“项目管理作战台”模板，缺少统一入口、统一筛选、树状项目进度视图与系统级主题切换。

## 3. 选定方案

采用“方案 B：统一入口 + 多主题大屏系统”。

### 3.1 不选单张超级总屏

将所有信息堆进单张大屏虽然改造快，但会让领导视角、执行视角和数据支撑视角混杂，长期不可维护。

### 3.2 不选多门户

直接拆成领导门户、科长门户、信息科门户会过早拉高复杂度。当前阶段更适合“一套系统、多主题视图”。

### 3.3 选定结构

使用一个统一壳页承载 `5` 个主题视图：

- 总览趋势
- 计划执行
- 风险归因
- 重大项目树
- 口径支撑

用户只进入一次系统，通过顶部标签切换主题，公共筛选上下文不丢失。

## 4. 系统形态

### 4.1 路由与页面结构

建议新增专题入口：

- `/analytics/project-cockpit`

页面结构：

1. 顶部全局筛选条
2. 顶部主题标签条
3. 中间主题主画布
4. 左侧或抽屉式重大项目树导航
5. 右侧局部详情或说明面板

### 4.2 公共筛选上下文

建议系统级上下文包含：

- `programId`
- `majorProjectId`
- `dateFrom`
- `dateTo`
- `deptId`
- `riskLevel`

这些上下文需要同步到 URL query，保证链接分享与分析视角可复现。

## 5. 五个主题视图

### 5.1 总览趋势

主要回答“整体是否变好或变坏”。

建议模块：

- KPI 卡：项目总数、在执行项目数、延期项目数、按期完成率、高风险项目数
- 趋势图：完成率、延期数、高风险数的周/月变化
- 项目群或科室分布
- 重点项目健康度排行
- 当前预警项目清单

### 5.2 计划执行

主要回答“计划卡在哪些节点”。

建议模块：

- 项目计划甘特图
- 本周期节点 KPI
- 里程碑推进图
- 节点类型分布
- 即将到期 / 已超期节点清单
- 责任科室负载图

### 5.3 风险归因

主要回答“为什么延期，风险如何变化”。

建议模块：

- 延期归因主图
- 风险等级结构图
- 延期趋势图
- 高风险节点趋势
- 延期申请 vs 未申请
- 重点延期项目清单

### 5.4 重大项目树

这是系统核心视图，主要回答“哪个子项目拖住了主计划”。

建议主轴：

- `重大项目 -> 子项目 -> 节点`

建议模块：

- 树状进度看板主视图
- 主项目健康卡
- 子项目进度对比
- 关键节点时间链 / 甘特
- 异常子项目清单

### 5.5 口径支撑

主要给信息科和支撑人员使用。

建议模块：

- 数据更新时间与刷新状态
- 字段覆盖率与异常项
- 指标口径说明
- 测试数据与映射关系摘要
- 主数据接口范围与维表待补清单

## 6. 可视化组件体系

第一批建议沉淀以下项目管理组件：

- 项目健康度卡
- 趋势折线 / 面积图
- 项目计划甘特图
- 树状进度看板
- 里程碑推进图
- 延期归因矩阵
- 责任科室负载图
- 异常清单表

优先级最高的是：

- 树状进度看板
- 项目计划甘特图
- 趋势分析图
- 项目健康度卡
- 延期归因矩阵

## 7. 数据模型设计

### 7.1 当前缺口

现有源数据中没有显式的“重大项目”“子项目”字段，只有：

- `项目编号`
- `分系统/分任务`
- `节点任务及目标`

因此必须补一层项目管理语义模型，不能直接依赖当前 `project_no/subsystem/node_task` 作为最终展示结构。

### 7.2 新增映射与语义层

建议新增三张核心映射表：

#### `pm_dim_major_project`

定义重大项目主实体。

建议字段：

- `major_project_id`
- `major_project_code`
- `major_project_name`
- `program_name`
- `owner_dept`
- `owner_leader`
- `priority_level`
- `start_date`
- `plan_end_date`
- `status`

#### `pm_dim_subproject`

定义子项目并归属到重大项目。

建议字段：

- `subproject_id`
- `subproject_code`
- `subproject_name`
- `major_project_id`
- `project_no`
- `subsystem_name`
- `owner_dept`
- `owner_user`
- `project_manager`
- `plan_start_date`
- `plan_end_date`
- `actual_end_date`
- `status`

#### `pm_map_node_subject`

将节点稳定挂到子项目和重大项目上。

建议字段：

- `map_id`
- `project_no`
- `subsystem`
- `node_task`
- `subproject_id`
- `major_project_id`
- `node_category`
- `delay_reason_category`
- `is_key_node`
- `is_milestone`
- `sort_order`

### 7.3 补充语义模型

基于现有 DWD/DWS/ADS，补以下模型：

- `biz_dwd_project_node_enriched`
- `biz_dws_week_subproject_summary`
- `biz_ads_major_project_overview`
- `biz_ads_major_project_tree_snapshot`
- `biz_ads_delay_reason_trend`

## 8. 树状进度看板数据结构

树状进度看板需要后端直接输出三层树结构，而不是让前端临时拼接。

节点建议字段：

- `id`
- `parentId`
- `level`
- `name`
- `status`
- `progressRate`
- `riskLevel`
- `delayDays`
- `planStart`
- `planEnd`
- `actualStart`
- `actualEnd`
- `ownerOrg`
- `ownerUser`
- `milestoneCount`
- `incompleteCount`
- `highRiskCount`
- `children`

甘特图可直接复用同一批时间字段。

## 9. 后端接口建议

建议在 `dts-analytics` 提供项目看板专题聚合接口，而不是让前端自己拼接多个底层数据源。

建议接口：

- `GET /analytics/api/project-cockpit/summary`
- `GET /analytics/api/project-cockpit/trends`
- `GET /analytics/api/project-cockpit/execution`
- `GET /analytics/api/project-cockpit/risk-attribution`
- `GET /analytics/api/project-cockpit/major-project-tree`
- `GET /analytics/api/project-cockpit/data-support`

这样能把前端复杂度控制在可维护范围内，也便于演示和测试数据切换。

## 10. 测试数据策略

第一版测试数据建议规模：

- `6-8` 个重大项目
- 每个重大项目 `3-8` 个子项目
- 每个子项目 `8-20` 个节点
- 总节点量 `200-500`

测试数据必须覆盖：

- 正常推进项目
- 延期但可恢复项目
- 高风险项目
- 子项目拖主计划
- 多责任科室协同
- 有 / 无延期申请
- 里程碑完成 / 未完成混合状态
- 可观察的周趋势变化

## 11. 演示与客户补数清单

第一阶段目标是先做出完整可演示系统，再向客户反推缺失内容。

演示后需输出的缺失清单分四类：

- 项目层级主数据系统尚未建立，当前由数仓维表承载，并保留主数据接口占位
- 延期归因口径缺失
- 趋势分析口径缺失
- 项目健康度规则缺失

## 12. 实施原则

- 优先做系统成型，而不是等待真实主数据系统建成；当前阶段以数仓为正式来源，同时保留主数据接口占位
- 公共上下文必须稳定，避免用户在多屏间反复点击
- 优先实现树状进度看板、甘特图、趋势分析
- 先保证演示闭环，再拉客户补齐数仓维表和口径，并为后续接入主数据系统保留接口占位

## 13. DTS 标准数据链路约束

项目看板必须纳入 DTS 标准 ETL/ELT 主线，不能长期采用“接口直接读取 Excel/静态文件”的方式。

正式链路定义为：

- 客户上传 `xlsx/csv`
- 文件进入项目主体域接入批次
- 落地 `ODS`
- 经 `dbt` 生成 `DWD / DWS / ADS`
- `dts-analytics` 只消费 `ADS`

这意味着当前位于 [project-cockpit](/opt/prod/s10/s10-stack/source/dts-analytics/src/main/resources/project-cockpit) 的 `TSV` 资源只能保留为：

- 本地开发夹具
- 回归测试样本
- 演示环境兜底素材

它们不应继续作为正式运行时数据源。

## 14. 项目主体域批次与 ODS 设计

### 14.1 目标

客户输入只能是 `Excel/CSV`，因此系统必须通过“批次 + ODS + 问题伴随”的方式吸收原始文件，而不是直接要求客户提供干净数据。

### 14.2 批次表

建议新增 `pm_ods_upload_batch`：

- `batch_id`
- `source_file_name`
- `source_file_path`
- `file_checksum`
- `uploaded_by`
- `uploaded_at`
- `status`
- `total_rows`
- `valid_rows`
- `issue_rows`
- `fatal_error`
- `modeled_at`
- `latest_data_at`

状态建议：

- `RECEIVED`
- `PARSED`
- `LOADED`
- `MODELED`
- `PARTIAL_SUCCESS`
- `FAILED_FATAL`

### 14.3 原始行表

建议新增 `pm_ods_project_progress_row`，所有客户原始业务字段先按字符串落地：

- `batch_id`
- `row_no`
- `raw_payload`
- `project_no`
- `major_project_name_raw`
- `subproject_name_raw`
- `subsystem`
- `node_task`
- `node_type`
- `owner`
- `dept`
- `project_manager`
- `plan_date_raw`
- `actual_date_raw`
- `completion_status_raw`
- `risk_level_raw`
- `delay_applied_raw`
- `incomplete_reason_raw`
- `delay_impact_raw`
- `last_update_time_raw`
- `highlight_raw`

### 14.4 问题表

建议新增 `pm_ods_project_progress_issue`：

- `batch_id`
- `row_no`
- `issue_code`
- `issue_level`
- `field_name`
- `issue_detail`

这样后续“口径支撑”可以直接消费批次、覆盖率和异常统计。

## 15. 数据质量与容错原则

原则不是“脏数据必须清洗成完美结果”，而是：

- 脏数据不能阻断整条链路
- 看板必须仍能出数
- 问题数据必须带着标签进入系统
- 只有物理损坏或文件类型非法才允许硬失败

具体策略：

- 接入层不强转日期/数值，先按 `string` 吞下
- 日期、比例、枚举、层级映射后移到 `DWD`
- 无法识别的项目层级挂到 `待归类`
- 无法识别的延期原因归到 `未分类`
- 统计层使用 nullable-safe 逻辑，不因单行坏数据丢失整项目

## 16. 口径支撑升级目标

当前“口径支撑”仅是演示说明页，正式版需升级为“数据与口径支撑中心”。

至少应包含：

1. 批次与刷新状态
2. 数据质量与覆盖率
3. 指标口径字典
4. 映射与分类状态
5. 客户待补清单与闭环状态

它应回答三类核心问题：

- 这个数从哪里来
- 为什么某个项目/节点没进树里
- 某个延期原因为什么被归到当前分类

## 17. 正式 API 接线原则

当前 [ProjectCockpitService.java](/opt/prod/s10/s10-stack/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java) 读取 `TSV` 仅适合作为第一版演示。

正式版建议：

- `summary` 读 `biz_ads_major_project_overview`
- `trends` 读 `biz_dws_week_subproject_summary`
- `risk-attribution` 读 `biz_ads_delay_reason_trend`
- `major-project-tree` 读 `biz_ads_major_project_tree_snapshot`
- `data-support` 读批次表、问题表、覆盖率与指标字典

如果没有正式批次数据：

- 返回“暂无正式数据”
- 不自动回退到 `TSV`
- 前端提示去上传项目主体域批次

## 18. 测试数据策略升级

在 `worklog/v2.2.1/sprint-9/it/` 中补一份正式测试 Excel，用于验证项目主体域中台链路。

要求：

- 总行数 `2000`
- `5` 个重大项目
- `30` 个子项目
- 状态分布：
  - `50%` 延期
  - `30%` 正常
  - `20%` 提前

同时需要刻意混入可容错脏数据：

- 日期格式混杂
- 枚举脏值
- 责任科室缺失
- 延期原因自由文本
- 子项目名称别名/空格

这份文件的目标不是做“纯净演示”，而是压测“接入、清洗、建模、口径支撑、看板展示”全链路。
